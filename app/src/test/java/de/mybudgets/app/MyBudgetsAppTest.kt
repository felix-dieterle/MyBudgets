package de.mybudgets.app

import android.os.Looper
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MyBudgetsAppTest {

    @Test
    fun `does not delegate uncaught exceptions from main thread`() {
        assertFalse(shouldDelegateToDefaultUncaughtHandler(Looper.getMainLooper().thread))
    }

    @Test
    fun `delegates uncaught exceptions from background threads`() {
        assertTrue(shouldDelegateToDefaultUncaughtHandler(Thread("background-test")))
    }
}

