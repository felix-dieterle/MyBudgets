package de.mybudgets.app

import android.os.Looper
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MyBudgetsAppTest {

    @Before
    fun resetStartupCrashGuard() {
        startupProtectionUntilElapsedRealtime = 0L
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
}
