package de.mybudgets.app.data.api.dto

data class AccountDto(
    val id: Long = 0,
    val name: String = "",
    val type: String = "CHECKING",
    val balance: Double = 0.0,
    val currency: String = "EUR",
    val color: Int = 0,
    val icon: String = "ic_account",
    val parentAccountId: Long? = null,
    val isVirtual: Boolean = false,
    val bankCode: String = "",
    val iban: String = "",
    val createdAt: Long = 0,
    val updatedAt: Long = 0
)
