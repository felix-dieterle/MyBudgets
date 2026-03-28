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
 * silently ignoring [setValidating] and stripping `<!DOCTYPE …>` declarations from
 * XML input before parsing.
 *
 * Android's default XML parser (Expat-based) has two problems with hbci4java:
 *  1. It does not support DTD validation, but hbci4java's [org.kapott.hbci.manager.MsgGen]
 *     unconditionally calls `factory.setValidating(true)`.
 *  2. Even with validation disabled, Android's Expat parser throws
 *     [org.xml.sax.SAXParseException] ("name expected") when it encounters the
 *     `<!DOCTYPE …>` declarations present in hbci4java's HBCI syntax XML files.
 *
 * This factory addresses both issues:
 *  - [setValidating] with `true` is silently ignored.
 *  - [newDocumentBuilder] returns a [DocumentBuilder] that strips DOCTYPE declarations
 *    from the XML input stream before handing it to Android's parser.
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
     * Returns a [DocumentBuilder] that pre-processes every XML input to strip
     * `<!DOCTYPE …>` declarations before parsing, so Android's Expat-based parser
     * never encounters DTD syntax it cannot handle.
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
     * [DocumentBuilder] wrapper that strips `<!DOCTYPE …>` declarations (including
     * inline DTD subsets enclosed in `[…]`) from the XML source before delegating to
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
            val stripped = stripDoctype(xmlText)
            val filtered = InputSource(StringReader(stripped)).also {
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
                    var quoteChar = 0.toChar()
                    while (i < xml.length) {
                        val c = xml[i]
                        when {
                            quoteChar != 0.toChar() -> {
                                // Inside a quoted string — only watch for the closing quote
                                if (c == quoteChar) quoteChar = 0.toChar()
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
