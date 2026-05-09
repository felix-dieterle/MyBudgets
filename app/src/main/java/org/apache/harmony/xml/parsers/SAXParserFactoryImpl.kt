package org.apache.harmony.xml.parsers

import org.xml.sax.SAXException
import org.xml.sax.SAXNotRecognizedException
import org.xml.sax.SAXNotSupportedException
import org.xml.sax.XMLReader
import org.xml.sax.helpers.XMLReaderFactory
import javax.xml.parsers.ParserConfigurationException
import javax.xml.parsers.SAXParser
import javax.xml.parsers.SAXParserFactory

/**
 * Drop-in replacement for Android's built-in [SAXParserFactoryImpl] that silently ignores
 * the `http://javax.xml.XMLConstants/feature/secure-processing` feature (and other
 * unsupported features) instead of throwing [SAXNotRecognizedException].
 *
 * **Problem:**
 * - JAXB (used by hbci4java for CAMT XML parsing) calls [setFeature] with `secure-processing`
 * - Android's native [SAXParserFactoryImpl] throws [SAXNotRecognizedException]
 * - JAXB wraps this as [IllegalStateException], causing CAMT parsing to fail
 *
 * **Solution:**
 * - This class has the **exact same package name** as Android's native implementation
 * - Due to classloader precedence, this class will be loaded **instead** of the system one
 * - We use [XMLReaderFactory.createXMLReader] which uses Android's Expat-based parser
 * - We silently ignore [SAXNotRecognizedException] in [setFeature]
 *
 * **Why not use System.setProperty?**
 * - JAXB's [com.sun.xml.bind.v2.util.XmlFactory] calls [SAXParserFactory.newInstance] directly
 * - On Android, the classloader may not respect the javax.xml.parsers.SAXParserFactory property
 * - By replacing the native class directly, we guarantee JAXB uses our wrapper
 *
 * **Classloader precedence:**
 * - App classes are loaded **before** system classes (parent-last delegation on Android)
 * - Our `org.apache.harmony.xml.parsers.SAXParserFactoryImpl` in the app classpath will
 *   "shadow" the system class of the same name
 *
 * @see <a href="https://issuetracker.google.com/issues/37009951">Android Issue #37009951</a>
 */
class SAXParserFactoryImpl : SAXParserFactory() {

    private val features = mutableMapOf<String, Boolean>()

    @Throws(ParserConfigurationException::class)
    override fun newSAXParser(): SAXParser {
        return AndroidSAXParser(isNamespaceAware, isValidating, features)
    }

    override fun setFeature(name: String?, value: Boolean) {
        // Store feature requests, but don't fail if unsupported
        // (JAXB's secure-processing feature is not available on Android)
        if (name != null) {
            features[name] = value
        }
    }

    override fun getFeature(name: String?): Boolean {
        // Return stored value or false if unknown
        return features[name] ?: false
    }

    /**
     * [SAXParser] implementation that uses [XMLReaderFactory.createXMLReader] to create
     * an Android Expat-based XMLReader.
     */
    private class AndroidSAXParser(
        private val namespaceAware: Boolean,
        private val validating: Boolean,
        private val features: Map<String, Boolean>
    ) : SAXParser() {

        @Throws(SAXException::class)
        override fun getXMLReader(): XMLReader {
            // Use Android's default XMLReader (Expat-based via org.apache.harmony.xml.ExpatReader)
            val reader = XMLReaderFactory.createXMLReader()

            // Apply namespace awareness
            try {
                reader.setFeature("http://xml.org/sax/features/namespaces", namespaceAware)
            } catch (e: SAXNotRecognizedException) {
                // Ignore - parser doesn't support this feature
            } catch (e: SAXNotSupportedException) {
                // Ignore
            }

            // Apply stored features (silently ignore unsupported ones)
            for ((name, value) in features) {
                try {
                    reader.setFeature(name, value)
                } catch (e: SAXNotRecognizedException) {
                    // Silently ignore (e.g. secure-processing on Android)
                } catch (e: SAXNotSupportedException) {
                    // Also ignore
                }
            }

            return reader
        }

        override fun isNamespaceAware(): Boolean = namespaceAware
        override fun isValidating(): Boolean = validating

        override fun getParser(): org.xml.sax.Parser {
            // Legacy SAX1 Parser interface - not supported on Android
            throw UnsupportedOperationException("SAX1 Parser interface is not supported - use getXMLReader() instead")
        }

        override fun getProperty(name: String?): Any? {
            return try {
                xmlReader.getProperty(name)
            } catch (e: SAXNotRecognizedException) {
                null
            } catch (e: SAXNotSupportedException) {
                null
            }
        }

        override fun setProperty(name: String?, value: Any?) {
            try {
                xmlReader.setProperty(name, value)
            } catch (e: SAXNotRecognizedException) {
                // Ignore
            } catch (e: SAXNotSupportedException) {
                // Ignore
            }
        }
    }
}
