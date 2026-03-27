package de.mybudgets.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class AccountType { CHECKING, SAVINGS, CASH, VIRTUAL }

@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: AccountType = AccountType.CHECKING,
    val balance: Double = 0.0,
    val currency: String = "EUR",
    val color: Int = 0xFF2196F3.toInt(),
    val icon: String = "ic_account",
    val parentAccountId: Long? = null,
    val isVirtual: Boolean = false,
    val bankCode: String = "",
    val iban: String = "",
    /** HBCI/FinTS login name (Nutzerkennung). Required for bank sync. */
    val userId: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
