package de.mybudgets.app.util

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import de.mybudgets.app.data.model.*

data class ExportData(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val accounts: List<Account> = emptyList(),
    val transactions: List<Transaction> = emptyList(),
    val categories: List<Category> = emptyList(),
    val labels: List<Label> = emptyList()
)

object JsonImportExport {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    fun toJson(data: ExportData): String = gson.toJson(data)

    fun fromJson(json: String): ExportData? = try {
        gson.fromJson(json, ExportData::class.java)
    } catch (_: Exception) {
        null
    }
}
