package de.mybudgets.app.util

import de.mybudgets.app.data.model.Account
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VirtualAccountMatcherTest {

    @Test
    fun `matches virtual account by regex pattern`() {
        val vacation = Account(id = 1, name = "Urlaub", isVirtual = true, autoAssignPattern = "urlaub|hotel")
        val emergency = Account(id = 2, name = "Notgroschen", isVirtual = true, autoAssignPattern = "reserve")

        val result = VirtualAccountMatcher.match("HOTEL Booking", listOf(emergency, vacation))

        assertEquals(vacation.id, result?.id)
    }

    @Test
    fun `returns null for invalid or non-matching patterns`() {
        val invalid = Account(id = 1, name = "Broken", isVirtual = true, autoAssignPattern = "[")
        val result = VirtualAccountMatcher.match("grocery market", listOf(invalid))
        assertNull(result)
    }
}
