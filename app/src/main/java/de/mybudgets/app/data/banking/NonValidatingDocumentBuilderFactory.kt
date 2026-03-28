package de.mybudgets.app.data.banking

import org.w3c.dom.DOMImplementation
import org.w3c.dom.Document
import org.xml.sax.EntityResolver
import org.xml.sax.ErrorHandler
import org.xml.sax.InputSource
import java.io.InputStreamReader
import java.io.StringReader
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Custom [DocumentBuilderFactory] that wraps Android's built-in XML parser while
 * silently ignoring [setValidating] and processing `<!DOCTYPE …>` declarations in
 * XML input before parsing.
 *
 * Android's default XML parser (Expat-based) has two problems with hbci4java:
 *  1. It does not support DTD validation, but hbci4java's [org.kapott.hbci.manager.MsgGen]
 *     unconditionally calls `factory.setValidating(true)`.
 *  2. Android's Expat parser neither processes the internal DTD subset (entity definitions)
 *     nor expands general entity references (`&name;`), which causes critical XML structures
 *     to be silently replaced with empty strings.  hbci4java's HBCI syntax files
 *     (hbci-300.xml, hbci-220.xml, …) rely heavily on DTD entities to define reusable
 *     XML fragments (e.g. `&MsgSigHeadUser;` for the `MsgHead`/`SigHead` segments in every
 *     message definition).  Without expansion those segments are missing, which ultimately
 *     causes `NoSuchPathException: kein Syntax-Element mit dem Pfad Synch.MsgHead.msgsize
 *     vorhanden` at runtime.
 *
 * This factory addresses both issues:
 *  - [setValidating] with `true` is silently ignored.
 *  - [newDocumentBuilder] returns a [DocumentBuilder] that, before handing the input to
 *    Android's parser:
 *     1. Extracts `<!ENTITY name 'value'>` definitions from the DOCTYPE's internal subset.
 *     2. Expands every `&name;` reference in the document body with the literal entity text.
 *     3. Strips the `<!DOCTYPE …>` declaration so the parser never sees it.
 *
 * Register this factory before HBCI initialisation:
 * ```
 * System.setProperty(
 *     "javax.xml.parsers.DocumentBuilderFactory",
 *     NonValidatingDocumentBuilderFactory::class.java.name
 * )
 * ```
 */
class NonValidatingDocumentBuilderFactory : DocumentBuilderFactory() {

    // Instantiate Android's concrete factory directly (bypasses system-property lookup,
    // so there is no recursion when this class is itself registered as the factory).
    private val delegate: DocumentBuilderFactory = try {
        DocumentBuilderFactory.newInstance(
            "org.apache.harmony.xml.parsers.DocumentBuilderFactoryImpl",
            NonValidatingDocumentBuilderFactory::class.java.classLoader
        )
    } catch (e: Exception) {
        throw IllegalStateException(
            "Unable to create non-validating XML DocumentBuilderFactory — " +
                "org.apache.harmony.xml.parsers.DocumentBuilderFactoryImpl not found",
            e
        )
    }

    /**
     * Returns a [DocumentBuilder] that pre-processes every XML input to expand
     * DTD entity references and strip `<!DOCTYPE …>` declarations before parsing,
     * so Android's Expat-based parser never encounters DTD syntax it cannot handle.
     */
    override fun newDocumentBuilder(): DocumentBuilder =
        DtdIgnoringDocumentBuilder(delegate.newDocumentBuilder())

    /**
     * Setting validating=true is intentionally ignored — Android's XML parser does not
     * support DTD validation.  Setting validating=false is forwarded normally.
     */
    override fun setValidating(validating: Boolean) {
        if (!validating) delegate.setValidating(false)
    }

    override fun setAttribute(name: String?, value: Any?) = delegate.setAttribute(name, value)

    override fun getAttribute(name: String?): Any? = delegate.getAttribute(name)

    override fun setFeature(name: String?, value: Boolean) = delegate.setFeature(name, value)

    override fun getFeature(name: String?): Boolean = delegate.getFeature(name)

    /**
     * [DocumentBuilder] wrapper that expands DTD entity references and strips
     * `<!DOCTYPE …>` declarations from the XML source before delegating to
     * the underlying Android parser.
     *
     * All concrete `parse` overloads in [DocumentBuilder] ultimately call
     * `parse(InputSource)`, so a single override is sufficient.
     */
    private class DtdIgnoringDocumentBuilder(private val inner: DocumentBuilder) : DocumentBuilder() {

        init {
            // Return an empty InputSource for any external entity/DTD reference so the
            // underlying parser does not attempt network or filesystem access.
            inner.setEntityResolver { _, _ -> InputSource(StringReader("")) }
        }

        override fun parse(inputSource: InputSource): Document {
            val charStream = inputSource.characterStream
            val xmlText = if (charStream != null) {
                charStream.readText()
            } else {
                val byteStream = inputSource.byteStream
                    ?: return inner.parse(inputSource) // no content to pre-process
                val encoding = inputSource.encoding ?: "UTF-8"
                InputStreamReader(byteStream, encoding).readText()
            }
            val processed = expandEntitiesAndStripDoctype(xmlText)
            val filtered = InputSource(StringReader(processed)).also {
                it.systemId = inputSource.systemId
                it.publicId = inputSource.publicId
            }
            return inner.parse(filtered)
        }

        override fun isNamespaceAware(): Boolean = inner.isNamespaceAware

        override fun isValidating(): Boolean = false

        /**
         * Compose [er] with the empty-InputSource fallback so that any external entity
         * or DTD reference that [er] does not handle explicitly still returns empty
         * content rather than triggering network or filesystem access.
         */
        override fun setEntityResolver(er: EntityResolver?) {
            if (er == null) {
                inner.setEntityResolver { _, _ -> InputSource(StringReader("")) }
            } else {
                inner.setEntityResolver { publicId, systemId ->
                    er.resolveEntity(publicId, systemId) ?: InputSource(StringReader(""))
                }
            }
        }

        override fun setErrorHandler(eh: ErrorHandler?) = inner.setErrorHandler(eh)

        override fun newDocument(): Document = inner.newDocument()

        override fun getDOMImplementation(): DOMImplementation = inner.domImplementation

        /**
         * Expands DTD general-entity references (`&name;`) and then removes the
         * `<!DOCTYPE …>` declaration.
         *
         * hbci4java's HBCI syntax XML files (hbci-300.xml, hbci-220.xml, …) define
         * XML-fragment entities in the DOCTYPE's internal DTD subset, for example:
         *
         * ```xml
         * <!ENTITY MsgSigHeadUser
         *   '<SEG type="MsgHeadUser" name="MsgHead"/>
         *    <SEG type="SigHeadUser" name="SigHead" minnum="0" maxnum="3"/>'
         * >
         * ```
         *
         * The document body then uses `&MsgSigHeadUser;` etc. to include these
         * fragments in message definitions (e.g. the `Synch` MSGdef).  After the
         * DOCTYPE is stripped, Android's XML parser cannot resolve those entity
         * references (it returns empty content via the EntityResolver), so entire
         * segments such as `MsgHead` vanish from the parsed message tree.  This
         * causes `NoSuchPathException: kein Syntax-Element mit dem Pfad
         * Synch.MsgHead.msgsize vorhanden` when hbci4java tries to build the sync
         * message.
         *
         * This method therefore:
         *  1. Extracts `<!ENTITY name 'value'>` definitions from the internal subset.
         *  2. Replaces every `&name;` occurrence in the rest of the document with the
         *     corresponding literal text.
         *  3. Strips the `<!DOCTYPE …>` declaration so the parser never sees it.
         */
        private fun expandEntitiesAndStripDoctype(xml: String): String {
            val entities = extractEntities(xml)
            val stripped = stripDoctype(xml)
            if (entities.isEmpty()) return stripped
            return expandEntityRefs(stripped, entities)
        }

        /**
         * Parses `<!ENTITY name '…'>` definitions from the DOCTYPE's internal DTD
         * subset and returns them as a name→value map.  Both single-quoted and
         * double-quoted entity values are supported.  Brackets (`[`, `]`) and quote
         * characters inside quoted attribute values are handled to avoid confusing
         * the subset-boundary detection.
         */
        private fun extractEntities(xml: String): Map<String, String> {
            val doctypeStart = xml.indexOf("<!DOCTYPE")
            if (doctypeStart < 0) return emptyMap()

            // Locate the internal DTD subset '[' … ']'
            var depth = 0
            var quoteChar: Char? = null
            var dtdSubsetStart = -1
            var dtdSubsetEnd   = -1
            var i = doctypeStart
            while (i < xml.length) {
                val c = xml[i]
                when {
                    quoteChar != null      -> { if (c == quoteChar) quoteChar = null }
                    c == '\'' || c == '"'  -> quoteChar = c
                    c == '['               -> { depth++; if (depth == 1) dtdSubsetStart = i }
                    c == ']'               -> { depth--; if (depth == 0) { dtdSubsetEnd = i; break } }
                    c == '>' && depth == 0 -> break  // DOCTYPE without internal subset
                }
                i++
            }
            if (dtdSubsetStart < 0 || dtdSubsetEnd < 0) return emptyMap()

            val dtd = xml.substring(dtdSubsetStart + 1, dtdSubsetEnd)
            val entities = mutableMapOf<String, String>()
            // Match <!ENTITY name 'value'> or <!ENTITY name "value">
            // [\w:.-]+ covers all valid XML name chars; [^']* / [^"]* match anything
            // (incl. newlines) that is not the closing delimiter.
            val entityRegex = Regex(
                """<!ENTITY\s+([\w:.\-]+)\s+(?:'([^']*)'|"([^"]*)")\s*>""",
                setOf(RegexOption.DOT_MATCHES_ALL)
            )
            for (match in entityRegex.findAll(dtd)) {
                val name  = match.groupValues[1]
                // group 2 = single-quoted value, group 3 = double-quoted value
                val value = match.groupValues[2].ifEmpty { match.groupValues[3] }
                entities[name] = value
            }
            return entities
        }

        /**
         * Replaces every `&name;` in [xml] that appears in [entities] with the
         * corresponding literal text.  XML pre-defined entities (`amp`, `lt`, `gt`,
         * `apos`, `quot`) and character references (`&#…;`) are left untouched so
         * the XML parser can still handle them normally.
         */
        private fun expandEntityRefs(xml: String, entities: Map<String, String>): String {
            val predefined = setOf("amp", "lt", "gt", "apos", "quot")
            val sb = StringBuilder(xml.length + xml.length / 4)
            var i = 0
            while (i < xml.length) {
                if (xml[i] == '&') {
                    val semicolon = xml.indexOf(';', i + 1)
                    if (semicolon > i + 1) {
                        val ref = xml.substring(i + 1, semicolon)
                        if (!ref.startsWith('#') && ref !in predefined) {
                            val expansion = entities[ref]
                            if (expansion != null) {
                                sb.append(expansion)
                                i = semicolon + 1
                                continue
                            }
                        }
                    }
                }
                sb.append(xml[i])
                i++
            }
            return sb.toString()
        }

        /**
         * Removes all `<!DOCTYPE …>` declarations from [xml], correctly handling:
         *  - external DTD references: `<!DOCTYPE foo SYSTEM "foo.dtd">`
         *  - inline DTD subsets:      `<!DOCTYPE foo [ <!ELEMENT …> ]>`
         *  - combined forms:          `<!DOCTYPE foo SYSTEM "…" [ … ]>`
         *  - brackets inside quotes:  `<!DOCTYPE foo SYSTEM "file[1].dtd">` (not treated as depth change)
         */
        private fun stripDoctype(xml: String): String {
            val sb = StringBuilder(xml.length)
            var i = 0
            while (i < xml.length) {
                if (xml.startsWith("<!DOCTYPE", i)) {
                    i += 9 // skip "<!DOCTYPE"
                    var depth = 0
                    var quoteChar: Char? = null
                    while (i < xml.length) {
                        val c = xml[i]
                        when {
                            quoteChar != null      -> {
                                // Inside a quoted string — only watch for the closing quote
                                if (c == quoteChar) quoteChar = null
                            }
                            c == '"' || c == '\'' -> quoteChar = c
                            c == '[' -> depth++
                            c == ']' -> depth--
                            c == '>' && depth == 0 -> { i++; break }
                        }
                        i++
                    }
                } else {
                    sb.append(xml[i])
                    i++
                }
            }
            return sb.toString()
        }
    }
}
