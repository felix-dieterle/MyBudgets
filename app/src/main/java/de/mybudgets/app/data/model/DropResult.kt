package de.mybudgets.app.data.model

sealed class DropResult {
    data class Valid(val newLevel: Int, val targetName: String?) : DropResult()
    data class Warning(val message: String, val newLevel: Int, val targetName: String?) : DropResult()
    data class Invalid(val message: String) : DropResult()
}
