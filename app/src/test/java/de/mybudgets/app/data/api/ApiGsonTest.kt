package de.mybudgets.app.data.api

import com.google.gson.reflect.TypeToken
import de.mybudgets.app.data.api.dto.AccountDto
import de.mybudgets.app.data.api.dto.CategoryDto
import de.mybudgets.app.data.api.dto.TransactionDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gegenprobe fuer [apiGson]: Die Endpoints antworten mit den Spaltennamen der
 * Datenbank (snake_case), die DTOs sind camelCase. Vor dem Fix galt das nur fuer
 * die Requests - die Antworten gingen still verloren.
 */
class ApiGsonTest {

    /** Zeilenaufbau exakt wie `SELECT * FROM transactions`. */
    private val serverAnswer = """
        {
          "id": 4711,
          "account_id": 7,
          "virtual_account_id": 12,
          "amount": 12.99,
          "description": "Spotify Premium",
          "date": 1759708800000,
          "type": "EXPENSE",
          "category_id": 42,
          "note": "DE89 3704 0044 0532 0130 00",
          "is_recurring": true,
          "recurring_interval_days": 30,
          "remote_id": "1234",
          "created_at": 1759708800000
        }
    """.trimIndent()

    @Test
    fun `antwort der datenbank erreicht alle camelCase felder`() {
        val dto = apiGson.fromJson(serverAnswer, TransactionDto::class.java)

        assertEquals(4711L, dto.id)
        assertEquals(7L, dto.accountId)
        assertEquals(12L, dto.virtualAccountId)
        assertEquals(42L, dto.categoryId)
        assertTrue(dto.isRecurring)
        assertEquals(30, dto.recurringIntervalDays)
        assertEquals("1234", dto.remoteId)
        assertEquals(1759708800000L, dto.createdAt)
        assertEquals(12.99, dto.amount, 0.0)
        assertEquals("Spotify Premium", dto.description)
    }

    @Test
    fun `ohne die policy bleiben dieselben felder leer - deshalb war der fix noetig`() {
        val legacy = com.google.gson.GsonBuilder().create()
        val dto = legacy.fromJson(serverAnswer, TransactionDto::class.java)

        // Bis 10.2026 war das der Produktionszustand. Nichts davon fiel auf,
        // weil die Transaction-Entitaet keinen Room-Fremdschluessel hat.
        assertEquals(0L, dto.accountId)
        assertNull(dto.virtualAccountId)
        assertNull(dto.categoryId)
        assertFalse(dto.isRecurring)
        assertEquals(0, dto.recurringIntervalDays)
        assertNull(dto.remoteId)
        assertEquals(0L, dto.createdAt)
        // Was unveraendert bleibt, darf sich nicht mitaendern - sonst testet
        // die Regression nicht die Ursache.
        assertEquals(4711L, dto.id)
        assertEquals(12.99, dto.amount, 0.0)
        assertEquals("Spotify Premium", dto.description)
    }

    @Test
    fun `request verlaesst die app als snake_case`() {
        val dto = TransactionDto(
            accountId = 7,
            virtualAccountId = 12,
            categoryId = 42,
            isRecurring = true,
            recurringIntervalDays = 30,
            remoteId = "1234",
            createdAt = 1L
        )

        val json = apiGson.toJson(dto)

        assertTrue(json, json.contains("\"account_id\":7"))
        assertTrue(json, json.contains("\"virtual_account_id\":12"))
        assertTrue(json, json.contains("\"category_id\":42"))
        assertTrue(json, json.contains("\"is_recurring\":true"))
        assertTrue(json, json.contains("\"recurring_interval_days\":30"))
        assertTrue(json, json.contains("\"remote_id\":\"1234\""))
        assertTrue(json, json.contains("\"created_at\":1"))
        assertFalse(json, json.contains("accountId"))
    }

    @Test
    fun `konto- und kategorien-antworten lesen sich ebenfalls`() {
        val acc = apiGson.fromJson(
            """{"id":3,"name":"Girokonto","parent_account_id":null,"is_virtual":true,
               "bank_code":"sparkasse","iban":"DE89370400440532013000",
               "created_at":99,"updated_at":100}""".trimIndent().replace("\n", ""),
            AccountDto::class.java
        )
        assertEquals(3L, acc.id)
        assertTrue(acc.isVirtual)
        assertEquals("sparkasse", acc.bankCode)
        assertEquals(99L, acc.createdAt)
        assertEquals(100L, acc.updatedAt)

        val cat = apiGson.fromJson(
            """{"id":5,"name":"Miete","parent_category_id":1,"is_default":true,"level":2}""",
            CategoryDto::class.java
        )
        assertEquals(1L, cat.parentCategoryId)
        assertTrue(cat.isDefault)
        assertEquals(2, cat.level)
    }

    @Test
    fun `map-antworten wie die id bleiben lesbar`() {
        // Die Policy benennt Felder um, keine Map-Schluessel - sonst waere
        // resp.body()?.get("id") in BackendSyncWorker nach dem Fix tot.
        val type = object : TypeToken<Map<String, Long>>() {}.type
        val body: Map<String, Long> = apiGson.fromJson("""{"id":4711}""", type)

        assertEquals(4711L, body["id"])
    }
}
