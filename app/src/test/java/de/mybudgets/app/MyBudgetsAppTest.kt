package de.mybudgets.app

import android.os.Looper
import de.mybudgets.app.data.banking.FeatureIgnoringSAXParserFactory
import de.mybudgets.app.data.banking.NonValidatingDocumentBuilderFactory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MyBudgetsAppTest {
    private var originalSaxFactory: String? = null
    private var originalDocumentFactory: String? = null

    @Before
    fun resetStartupCrashGuard() {
        startupProtectionUntilElapsedRealtime = 0L
        originalSaxFactory = System.getProperty("javax.xml.parsers.SAXParserFactory")
        originalDocumentFactory = System.getProperty("javax.xml.parsers.DocumentBuilderFactory")
    }

    @After
    fun restoreXmlFactoryOverrides() {
        if (originalSaxFactory == null) {
            System.clearProperty("javax.xml.parsers.SAXParserFactory")
        } else {
            System.setProperty("javax.xml.parsers.SAXParserFactory", originalSaxFactory!!)
        }
        if (originalDocumentFactory == null) {
            System.clearProperty("javax.xml.parsers.DocumentBuilderFactory")
        } else {
            System.setProperty("javax.xml.parsers.DocumentBuilderFactory", originalDocumentFactory!!)
        }
    }

    @Test
    fun `does not delegate uncaught exceptions from main thread`() {
        startupProtectionUntilElapsedRealtime = Long.MAX_VALUE
        assertFalse(shouldDelegateToDefaultUncaughtHandler(Looper.getMainLooper().thread))
    }

    @Test
    fun `delegates uncaught exceptions from main thread after startup grace period`() {
        startupProtectionUntilElapsedRealtime = 0L
        assertTrue(shouldDelegateToDefaultUncaughtHandler(Looper.getMainLooper().thread))
    }

    @Test
    fun `delegates uncaught exceptions from background threads`() {
        startupProtectionUntilElapsedRealtime = Long.MAX_VALUE
        assertTrue(shouldDelegateToDefaultUncaughtHandler(Thread("background-test")))
    }

    @Test
    fun `installXmlParserFactoryOverrides registers custom XML parser factories`() {
        System.clearProperty("javax.xml.parsers.SAXParserFactory")
        System.clearProperty("javax.xml.parsers.DocumentBuilderFactory")

        installXmlParserFactoryOverrides()

        assertEquals(
            FeatureIgnoringSAXParserFactory::class.java.name,
            System.getProperty("javax.xml.parsers.SAXParserFactory")
        )
        assertEquals(
            NonValidatingDocumentBuilderFactory::class.java.name,
            System.getProperty("javax.xml.parsers.DocumentBuilderFactory")
        )
    }
}
