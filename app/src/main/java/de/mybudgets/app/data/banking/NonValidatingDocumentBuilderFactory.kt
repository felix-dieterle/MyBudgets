package de.mybudgets.app.data.banking

import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Custom [DocumentBuilderFactory] that wraps Android's built-in XML parser while
 * silently ignoring [setValidating].
 *
 * Android's default XML parser does not support DTD validation, but hbci4java's
 * [org.kapott.hbci.manager.MsgGen] unconditionally enables it via
 * `factory.setValidating(true)`, which causes a
 * [javax.xml.parsers.ParserConfigurationException] at [org.kapott.hbci.manager.HBCIHandler]
 * initialisation time.
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

    override fun newDocumentBuilder(): DocumentBuilder = delegate.newDocumentBuilder()

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
}
