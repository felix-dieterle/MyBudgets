package de.mybudgets.app.data.banking

import org.xml.sax.SAXNotRecognizedException
import javax.xml.parsers.SAXParser
import javax.xml.parsers.SAXParserFactory

/**
 * Custom [SAXParserFactory] that wraps Android's built-in SAX parser factory while
 * silently ignoring features it does not support (such as
 * `http://javax.xml.XMLConstants/feature/secure-processing`).
 *
 * Android's [org.apache.harmony.xml.parsers.SAXParserFactoryImpl] does not support the
 * `secure-processing` feature required by JAXB's [com.sun.xml.bind.v2.util.XmlFactory].
 * When JAXB calls [setFeature] with that feature name, Android throws
 * [SAXNotRecognizedException], which JAXB re-wraps as [IllegalStateException], causing
 * CAMT XML parsing to fail entirely.
 *
 * This factory delegates all calls to Android's [SAXParserFactoryImpl], but overrides
 * [setFeature] to silently ignore [SAXNotRecognizedException] instead of propagating it.
 *
 * Register this factory before HBCI initialisation:
 * ```
 * System.setProperty(
 *     "javax.xml.parsers.SAXParserFactory",
 *     FeatureIgnoringSAXParserFactory::class.java.name
 * )
 * ```
 */
class FeatureIgnoringSAXParserFactory : SAXParserFactory() {

    // Instantiate Android's concrete factory directly (bypasses system-property lookup,
    // so there is no recursion when this class is itself registered as the factory).
    private val delegate: SAXParserFactory = try {
        SAXParserFactory.newInstance(
            "org.apache.harmony.xml.parsers.SAXParserFactoryImpl",
            FeatureIgnoringSAXParserFactory::class.java.classLoader
        )
    } catch (e: Exception) {
        throw IllegalStateException(
            "Unable to create FeatureIgnoringSAXParserFactory — " +
                "org.apache.harmony.xml.parsers.SAXParserFactoryImpl not found",
            e
        )
    }

    /**
     * Syncs namespace-awareness and validating settings from the base-class fields (which
     * are set by [setNamespaceAware] and [setValidating]) to the delegate before creating
     * a new parser, so callers only need to configure this factory instance.
     */
    override fun newSAXParser(): SAXParser {
        delegate.isNamespaceAware = isNamespaceAware
        delegate.isValidating = isValidating
        return delegate.newSAXParser()
    }

    /**
     * Attempts to set [name] on the delegate.  Any [SAXNotRecognizedException] thrown by
     * Android's parser (e.g. for `http://javax.xml.XMLConstants/feature/secure-processing`)
     * is silently swallowed — these are security hardening features that are not needed on
     * Android and whose absence does not affect correctness.
     */
    override fun setFeature(name: String?, value: Boolean) {
        try {
            delegate.setFeature(name, value)
        } catch (e: SAXNotRecognizedException) {
            // Silently ignore — Android's SAX parser does not support all JAXP features.
            // In particular, http://javax.xml.XMLConstants/feature/secure-processing is
            // not recognised by org.apache.harmony.xml.parsers.SAXParserFactoryImpl but
            // is unconditionally requested by com.sun.xml.bind.v2.util.XmlFactory when
            // creating the SAX parser for CAMT XML unmarshalling.
        }
    }

    override fun getFeature(name: String?): Boolean = delegate.getFeature(name)
}
