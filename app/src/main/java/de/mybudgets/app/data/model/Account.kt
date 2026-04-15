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
    /**
     * HBCI/FinTS TAN security mechanism code (Sicherheitsfunktion).
     * Leave empty to let hbci4j auto-select the first available method.
     * For BBBank Secure Go / BestSign use the code assigned by your bank (e.g. "900").
     * Both apps use the decoupled TAN flow: the user confirms directly in the Secure Go app.
     */
    val tanMethod: String = "",
    /** Regex used to auto-map synced transactions to this virtual account. */
    val autoAssignPattern: String = "",
    /** Optional savings target for this virtual account. */
    val targetAmount: Double? = null,
    /** Optional due date (epoch millis) for [targetAmount]. */
    val targetDueDate: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
