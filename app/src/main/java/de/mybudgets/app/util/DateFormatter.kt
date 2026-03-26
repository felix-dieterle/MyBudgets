package de.mybudgets.app.util

import java.text.SimpleDateFormat
import java.util.*

object DateFormatter {
    private val dateFormat   = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY)
    private val monthFormat  = SimpleDateFormat("MMMM yyyy", Locale.GERMANY)

    fun formatDate(ms: Long): String = dateFormat.format(Date(ms))
    fun formatMonth(ms: Long): String = monthFormat.format(Date(ms))
}
