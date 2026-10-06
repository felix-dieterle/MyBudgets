package de.mybudgets.app.viewmodel

import android.app.Application
import de.mybudgets.app.data.repository.AccountRepository
import de.mybudgets.app.data.repository.CategoryRepository
import de.mybudgets.app.data.repository.TransactionRepository
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class DashboardViewModelTest {

    @Test
    fun `huberRegression with flat data returns avg and zero trend`() {
        val (avg, trend) = huberRegression(listOf(10f, 10f, 10f))
        assertEquals(10f, avg, 0.001f)
        assertEquals(0f, trend, 0.001f)
    }

    @Test
    fun `huberRegression with increasing data returns positive trend`() {
        val (avg, trend) = huberRegression(listOf(10f, 20f, 30f))
        assertEquals(20f, avg, 0.001f)
        assertEquals(10f, trend, 0.001f)
    }

    @Test
    fun `huberRegression with decreasing data returns negative trend`() {
        val (avg, trend) = huberRegression(listOf(30f, 20f, 10f))
        assertEquals(20f, avg, 0.001f)
        assertEquals(-10f, trend, 0.001f)
    }

    @Test
    fun `huberRegression with single value returns avg and zero trend`() {
        val (avg, trend) = huberRegression(listOf(42f))
        assertEquals(42f, avg, 0.001f)
        assertEquals(0f, trend, 0.001f)
    }

    @Test
    fun `huberRegression with two values computes correct trend`() {
        val (avg, trend) = huberRegression(listOf(5f, 15f))
        assertEquals(10f, avg, 0.001f)
        assertEquals(10f, trend, 0.001f)
    }

    @Test
    fun `huberRegression with empty list returns zero avg and zero trend`() {
        val (avg, trend) = huberRegression(emptyList())
        assertEquals(0f, avg, 0.001f)
        assertEquals(0f, trend, 0.001f)
    }

    @Test
    fun `huberRegression with steep decline computes correctly`() {
        val (avg, trend) = huberRegression(listOf(100f, 0f, 0f))
        assertEquals(33.333f, avg, 0.001f)
        assertEquals(-50f, trend, 0.001f)
    }
}
