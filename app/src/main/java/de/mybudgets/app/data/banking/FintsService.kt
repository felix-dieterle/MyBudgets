package de.mybudgets.app.data.banking

import android.content.Context
import de.mybudgets.app.data.model.Account
import de.mybudgets.app.data.model.StandingOrder
import de.mybudgets.app.data.model.Transaction
import de.mybudgets.app.data.model.TransactionType
import de.mybudgets.app.util.AppLogger
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.kapott.hbci.GV_Result.GVRKUms
import org.kapott.hbci.callback.AbstractHBCICallback
import org.kapott.hbci.manager.HBCIHandler
import org.kapott.hbci.manager.HBCIUtils
import org.kapott.hbci.passport.AbstractHBCIPassport
import org.kapott.hbci.passport.HBCIPassport
import org.kapott.hbci.structures.Konto
import org.kapott.hbci.structures.Value
import java.io.File
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

private const val TAG = "FintsService"

/**
 * Wraps HBCI4Java for direct FinTS/HBCI bank communication.
 * Supports single SEPA transfers, SEPA standing orders, and account statement fetch.
 *
 * PIN and TAN input are bridged asynchronously via suspend lambdas set by the UI layer.
 */
@Singleton
class FintsService @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /** Set by the active UI fragment before banking operations. Cleared on fragment destroy. */
    @Volatile var pinProvider: (suspend (bankName: String) -> String)? = null
    @Volatile var tanProvider: (suspend (tanChallenge: String) -> String)? = null
    /**
     * Called for decoupled TAN methods (e.g. BBBank Secure Go / BestSign / pushTAN).
     * The UI should show a message asking the user to approve in their Secure Go (or banking)
     * app and wait for the user to tap OK before returning.
     */
    @Volatile var decoupledConfirmProvider: (suspend (challenge: String) -> Unit)? = null

    /** BLZ of the account currently being connected. Set in openSession, read by HbciCallback. */
    private val currentBlz = ThreadLocal<String>()

    /** Nutzerkennung (HBCI/FinTS login name) of the account currently being connected. */
    private val currentUserId = ThreadLocal<String>()

    /**
     * TAN security mechanism code (Sicherheitsfunktion) of the account being connected.
     * Returned when hbci4j asks which TAN method to use (NEED_PT_SECMECH).
     * Empty string means hbci4j auto-selects the first available method.
     */
    private val currentTanMethod = ThreadLocal<String>()

    private val passportDir: File by lazy {
        File(context.filesDir, "hbci_passports").also { it.mkdirs() }
    }

    /**
     * A randomly generated passphrase stored in app-private SharedPreferences.
     * Used to encrypt the HBCI passport file. Generated once per install.
     */
    private val passportPassphrase: String by lazy {
        val prefs = context.getSharedPreferences("hbci_secure", Context.MODE_PRIVATE)
        prefs.getString("passport_pp", null) ?: run {
            val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
            val pp = bytes.joinToString("") { "%02x".format(it) }
            prefs.edit().putString("passport_pp", pp).apply()
            pp
        }
    }

    // ─── Public API ──────────────────────────────────────────────────────────────

    /**
     * Executes a single SEPA credit transfer from [fromAccount] to the given recipient.
     * Requires [fromAccount.bankCode] (BLZ) and [fromAccount.iban] to be set.
     */
    suspend fun executeTransfer(
        fromAccount: Account,
        toName: String,
        toIban: String,
        toBic: String,
        amount: Double,
        purpose: String
    ): Result<String> = withContext(Dispatchers.IO) {
        AppLogger.i(TAG, "executeTransfer: start – von ${fromAccount.iban} an $toName ($toIban) Betrag=$amount")
        runCatching {
            val (handler, passport) = openSession(fromAccount)
            try {
                val job = handler.newJob("UebSEPA")
                job.setParam("src", buildKonto(fromAccount))
                job.setParam("dst", buildKontoRecipient(toName, toIban, toBic))
                job.setParam("btg", Value(amount, "EUR"))
                job.setParam("usage", purpose)
                handler.addJob(job)

                val status = handler.execute()
                AppLogger.d(TAG, "execute() status: isOK=${status.isOK} – $status")
                if (!status.isOK) {
                    error("Überweisung fehlgeschlagen: $status")
                }
                val result = job.jobResult
                AppLogger.d(TAG, "jobResult: isOK=${result.isOK} – $result")
                if (!result.isOK) error("Bankfehler: $result")
                AppLogger.i(TAG, "executeTransfer: erfolgreich")
                "Überweisung erfolgreich ausgeführt"
            } finally {
                safeClose(handler)
            }
        }.onFailure { e ->
            if (e is CancellationException) throw e
            AppLogger.e(TAG, "executeTransfer fehlgeschlagen: ${e.message}", e)
        }
    }

    /**
     * Creates a SEPA standing order at the bank for the given [order].
     */
    suspend fun createStandingOrder(
        fromAccount: Account,
        order: StandingOrder
    ): Result<String> = withContext(Dispatchers.IO) {
        AppLogger.i(TAG, "createStandingOrder: ${order.recipientName} Betrag=${order.amount}")
        runCatching {
            val (handler, _) = openSession(fromAccount)
            try {
                val job = handler.newJob("DauerNewSEPA")
                job.setParam("src", buildKonto(fromAccount))
                job.setParam("dst", buildKontoRecipient(order.recipientName, order.recipientIban, order.recipientBic))
                job.setParam("btg", Value(order.amount, "EUR"))
                job.setParam("usage", order.purpose)
                job.setParam("firstdate", SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).format(Date(order.firstExecutionDate)))
                job.setParam("timeunit", "M")
                job.setParam("turnus", "1")
                job.setParam("execday", "1")
                if (order.lastExecutionDate != null) {
                    job.setParam("lastdate", SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).format(Date(order.lastExecutionDate)))
                }
                handler.addJob(job)

                val status = handler.execute()
                if (!status.isOK) error("Dauerauftrag fehlgeschlagen: $status")
                val result = job.jobResult
                if (!result.isOK) error("Bankfehler: $result")
                AppLogger.i(TAG, "createStandingOrder: erfolgreich")
                "Dauerauftrag erfolgreich angelegt"
            } finally {
                safeClose(handler)
            }
        }.onFailure { e ->
            if (e is CancellationException) throw e
            AppLogger.e(TAG, "createStandingOrder fehlgeschlagen: ${e.message}", e)
        }
    }

    /**
     * Fetches account statement (Kontoauszug) for the given [account].
     * Returns a list of [Transaction] objects populated from bank data.
     */
    suspend fun fetchAccountStatement(
        account: Account,
        fromDate: Date? = null
    ): Result<List<Transaction>> = withContext(Dispatchers.IO) {
        AppLogger.i(TAG, "fetchAccountStatement: ${account.iban} ab $fromDate")
        runCatching {
            val (handler, _) = openSession(account)
            try {
                val jobName = if (fromDate != null) "KUmsZeit" else "KUmsAll"
                val job = handler.newJob(jobName)
                job.setParam("my", buildKonto(account))
                if (fromDate != null) {
                    job.setParam("startdate", SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).format(fromDate))
                }
                handler.addJob(job)

                val status = handler.execute()
                if (!status.isOK) error("Kontoauszug fehlgeschlagen: $status")

                val result = job.jobResult as? GVRKUms
                    ?: error("Unerwartetes Ergebnis vom Kontoauszug-Job")

                val transactions = result.flatData.map { entry ->
                    val isIncome = entry.value.longValue >= 0
                    Transaction(
                        accountId   = account.id,
                        amount      = abs(entry.value.doubleValue),
                        description = entry.usage.joinToString(" ").trim().ifBlank { entry.other?.name ?: "" },
                        date        = entry.valuta?.time ?: entry.bdate?.time ?: System.currentTimeMillis(),
                        type        = if (isIncome) TransactionType.INCOME else TransactionType.EXPENSE,
                        note        = entry.other?.name ?: "",
                        remoteId    = entry.id
                    )
                }
                AppLogger.i(TAG, "fetchAccountStatement: ${transactions.size} Buchungen empfangen")
                transactions
            } finally {
                safeClose(handler)
            }
        }.onFailure { e ->
            if (e is CancellationException) throw e
            AppLogger.e(TAG, "fetchAccountStatement fehlgeschlagen: ${e.message}", e)
        }
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────────

    private fun openSession(account: Account): Pair<HBCIHandler, HBCIPassport> {
        val blz = account.bankCode.ifBlank {
            // Derive BLZ from German IBAN (DE + 2 check digits + 8-digit BLZ + account number)
            blzFromIban(account.iban)
                ?: error("BLZ fehlt und kann nicht aus der IBAN ermittelt werden für Konto '${account.name}'")
        }
        AppLogger.d(TAG, "openSession: BLZ=$blz accountId=${account.id}")
        currentBlz.set(blz)
        currentUserId.set(account.userId)
        currentTanMethod.set(account.tanMethod)
        initHbciOnce()

        val passportFile = File(passportDir, "passport_${blz}_${account.id}.dat")
        HBCIUtils.setParam("client.passport.PinTan.filename", passportFile.absolutePath)
        HBCIUtils.setParam("client.passport.PinTan.init", "1")
        HBCIUtils.setParam("client.passport.default", "PinTan")

        val passport = AbstractHBCIPassport.getInstance("PinTan") as AbstractHBCIPassport
        // Set country and BLZ on the passport so that HBCI4Java can resolve the bank server URL.
        // Without this, a fresh passport triggers "bankleitzahl darf nicht leer sein".
        passport.country = "DE"
        passport.blz = blz
        val handler = HBCIHandler("300", passport)
        return Pair(handler, passport)
    }

    @Synchronized
    private fun initHbciOnce() {
        if (hbciInitialized) return
        // Android's default DocumentBuilderFactory does not support DTD validation, but
        // hbci4java's MsgGen unconditionally calls setValidating(true). Replace the factory
        // with our wrapper that silently ignores the validation flag.
        System.setProperty(
            "javax.xml.parsers.DocumentBuilderFactory",
            NonValidatingDocumentBuilderFactory::class.java.name
        )
        val props = Properties().apply {
            setProperty("client.product.id", "MyBudgets")
            setProperty("client.product.version", "1.0")
            setProperty("log.loglevel.default", "2")
        }
        HBCIUtils.init(props, HbciCallback())
        hbciInitialized = true
    }

    private fun safeClose(handler: HBCIHandler) {
        try { handler.close() } catch (e: Exception) { AppLogger.w(TAG, "Handler close error: ${e.message}", e) }
        currentBlz.remove()
        currentUserId.remove()
        currentTanMethod.remove()
    }

    /**
     * Extracts the 8-digit BLZ from a German IBAN.
     * German IBAN format: DE + 2 check digits + 8-digit BLZ + 10-digit account number.
     * Returns null for non-German or malformed IBANs.
     */
    private fun blzFromIban(iban: String): String? {
        val normalized = iban.replace(" ", "").uppercase()
        return if (normalized.length == 22 && normalized.startsWith("DE")) {
            normalized.substring(4, 12)
        } else null
    }

    private fun buildKonto(account: Account): Konto {
        val k = Konto()
        k.blz  = account.bankCode.ifBlank { blzFromIban(account.iban) ?: "" }
        k.iban = account.iban
        k.curr = account.currency.ifBlank { "EUR" }
        return k
    }

    private fun buildKontoRecipient(name: String, iban: String, bic: String): Konto {
        val k = Konto()
        k.name = name
        k.iban = iban
        if (bic.isNotBlank()) k.bic = bic
        return k
    }

    // ─── HBCI Callback ────────────────────────────────────────────────────────────

    inner class HbciCallback : AbstractHBCICallback() {

        override fun log(msg: String?, level: Int, date: Date?, trace: StackTraceElement?) {
            AppLogger.d(TAG, "HBCI log level=$level msg=$msg")
        }

        override fun callback(
            passport: HBCIPassport?,
            reason: Int,
            msg: String?,
            datatype: Int,
            retData: StringBuffer?
        ) {
            when (reason) {
                NEED_PT_PIN -> {
                    val bankName = passport?.blz ?: "Bank"
                    AppLogger.i(TAG, "PIN-Anfrage für BLZ $bankName")
                    val pin = requestFromUi { pinProvider?.invoke(bankName) ?: "" }
                    retData?.replace(0, retData.length, pin)
                }
                NEED_PT_TAN -> {
                    val challenge = msg ?: "TAN erforderlich"
                    AppLogger.i(TAG, "TAN-Anfrage: $challenge")
                    val tan = requestFromUi { tanProvider?.invoke(challenge) ?: "" }
                    retData?.replace(0, retData.length, tan)
                }
                NEED_PASSPHRASE_LOAD, NEED_PASSPHRASE_SAVE -> {
                    retData?.replace(0, retData.length, passportPassphrase)
                }
                NEED_BLZ -> {
                    retData?.replace(0, retData.length, currentBlz.get() ?: "")
                }
                NEED_USERID, NEED_CUSTOMERID -> {
                    retData?.replace(0, retData.length, currentUserId.get() ?: "")
                }
                NEED_PT_SECMECH -> {
                    // Return the user-configured TAN security mechanism code (e.g. "900" for
                    // BBBank Secure Go / BestSign / pushTAN). Empty string lets hbci4j auto-select.
                    val method = currentTanMethod.get() ?: ""
                    AppLogger.i(TAG, "TAN-Verfahren-Auswahl: '${method.ifBlank { "auto" }}'")
                    retData?.replace(0, retData.length, method)
                }
                NEED_PT_DECOUPLED, NEED_PT_DECOUPLED_RETRY -> {
                    // Decoupled TAN (BBBank Secure Go / BestSign / pushTAN): user confirms in banking app.
                    AppLogger.i(TAG, "Decoupled TAN-Bestätigung erforderlich (Secure Go / BestSign): $msg")
                    if (decoupledConfirmProvider != null) {
                        requestFromUi {
                            decoupledConfirmProvider?.invoke(msg ?: "")
                            ""
                        }
                    } else {
                        AppLogger.w(TAG, "Kein decoupledConfirmProvider gesetzt – Bestätigung übersprungen")
                    }
                    retData?.replace(0, retData.length, "")
                }
                NEED_NEW_INST_KEYS_ACK -> {
                    retData?.replace(0, retData.length, "")
                }
                HAVE_INST_MSG -> AppLogger.i(TAG, "Bank-Nachricht: $msg")
                else -> AppLogger.d(TAG, "HBCI callback reason=$reason msg=$msg")
            }
        }

        override fun status(passport: HBCIPassport?, statusTag: Int, o: Array<out Any>?) {
            AppLogger.d(TAG, "HBCI status tag=$statusTag")
        }

        /**
         * Bridges a suspend UI call to the synchronous HBCI callback thread.
         * Blocks the HBCI thread until the UI responds.
         *
         * A [CoroutineExceptionHandler] is installed so that any exception thrown
         * by [block] (e.g. BadTokenException when the Activity is finishing) is
         * caught, logged, and resolved as an empty string instead of crashing the app.
         */
        private fun requestFromUi(block: suspend () -> String): String {
            val result = AtomicReference("")
            val latch  = CountDownLatch(1)
            val handler = CoroutineExceptionHandler { _, throwable ->
                AppLogger.e(TAG, "requestFromUi: Fehler im UI-Coroutine: ${throwable.message}", throwable)
                latch.countDown()
            }
            CoroutineScope(Dispatchers.Main).launch(handler) {
                try {
                    result.set(block())
                } catch (e: Exception) {
                    AppLogger.e(TAG, "requestFromUi: block() fehlgeschlagen: ${e.message}", e)
                } finally {
                    latch.countDown()
                }
            }
            latch.await()
            return result.get()
        }
    }

    companion object {
        @Volatile private var hbciInitialized = false
    }
}

