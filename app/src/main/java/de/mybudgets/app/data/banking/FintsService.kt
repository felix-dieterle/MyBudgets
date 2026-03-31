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
import org.kapott.hbci.GV.HBCIJob
import org.kapott.hbci.GV_Result.GVRKUms
import org.kapott.hbci.callback.AbstractHBCICallback
import org.kapott.hbci.exceptions.InvalidUserDataException
import org.kapott.hbci.exceptions.JobNotSupportedException
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

/** Prefix used by hbci4java when a job class is missing from the classpath or spec. */
private const val HBCI_NO_HIGHLEVEL_JOB_MSG = "there is no highlevel job named"

/** Fragment of the hbci4java error message when a required BIC property was not set. */
private const val HBCI_MISSING_BIC_MSG = "my.bic"

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

    /**
     * Set to `true` on the current thread by [HbciCallback] when the bank signals an invalid PIN
     * (callback reason [org.kapott.hbci.callback.HBCICallback.WRONG_PIN] = 40).
     * Checked after each FinTS operation to produce a user-friendly error message.
     */
    private val wrongPinOnThread = ThreadLocal<Boolean>()

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
        // Clear any stale wrong-PIN flag from a previous operation on this thread.
        wrongPinOnThread.remove()
        val operationResult = runCatching {
            val (handler, passport) = openSession(fromAccount)
            try {
                val bic = bicFromPassport(passport, fromAccount.iban)
                val job = handler.newJob("UebSEPA")
                job.setParam("src", buildKonto(fromAccount, bic))
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
        wrapWrongPinResult(operationResult)
    }

    /**
     * Creates a SEPA standing order at the bank for the given [order].
     */
    suspend fun createStandingOrder(
        fromAccount: Account,
        order: StandingOrder
    ): Result<String> = withContext(Dispatchers.IO) {
        AppLogger.i(TAG, "createStandingOrder: ${order.recipientName} Betrag=${order.amount}")
        // Clear any stale wrong-PIN flag from a previous operation on this thread.
        wrongPinOnThread.remove()
        val operationResult = runCatching {
            val (handler, passport) = openSession(fromAccount)
            try {
                val bic = bicFromPassport(passport, fromAccount.iban)
                val job = handler.newJob("DauerNewSEPA")
                job.setParam("src", buildKonto(fromAccount, bic))
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
        wrapWrongPinResult(operationResult)
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
        // Clear any stale wrong-PIN flag from a previous operation on this thread.
        wrongPinOnThread.remove()
        val operationResult = runCatching {
            val (handler, passport) = openSession(account)
            try {
                // Look up the BIC for this account from the passport's UPD accounts.
                // SEPA/CAMT jobs (KUmsAllCamt, KUmsZeitSEPA) require my.bic to be set;
                // omitting it causes InvalidUserDataException: "Property my.bic wurde nicht gesetzt".
                val bic = bicFromPassport(passport, account.iban)
                // Fetch account statement with an ordered fallback strategy:
                //
                // Priority order (first success wins):
                //  1. KUmsAllCamt (HKCAZ CAMT/ISO 20022, FinTS 3.0+) — most modern banks
                //     (e.g. BBBank / VR cooperative banks) advertise HKCAZ only in CAMT format
                //     (camt.052) in their BPD. GVKUmsAllCamt handles date filtering natively.
                //  2. KUmsZeitSEPA (HKCAZ SEPA MT940-style, FinTS 3.0) — banks that advertise
                //     the SEPA HKCAZ variant (not CAMT). Supports date filtering.
                //  3. KUmsAll   (HKKAZ, FinTS 3.0) — tried first when fromDate == null; also
                //     tried as a fallback (returns more transactions than requested when
                //     fromDate was set, which is acceptable).
                //  4. KUmsNew   (HKKAZ, HBCI 2.x)  — legacy fallback for older bank servers.
                //
                // Note: KUmsAll and KUmsNew do not support date filtering; if fromDate was set
                // and only these succeed, more transactions than requested will be returned.
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY)

                // Build the ordered list of job names + optional startdate to try.
                // fromDate != null  → KUmsAllCamt (with date), KUmsZeitSEPA (with date),
                //                     KUmsAll, KUmsNew
                // fromDate == null  → KUmsAllCamt (no explicit date, uses BPD timerange),
                //                     KUmsAll, KUmsZeitSEPA (epoch startdate to cover banks
                //                     that only support HKCAZ SEPA), KUmsNew
                data class JobAttempt(val name: String, val startDate: Date? = null)
                val jobAttempts = if (fromDate != null) listOf(
                    JobAttempt("KUmsAllCamt", fromDate),
                    JobAttempt("KUmsZeitSEPA", fromDate),
                    JobAttempt("KUmsAll"),
                    JobAttempt("KUmsNew"),
                ) else listOf(
                    JobAttempt("KUmsAllCamt"),
                    JobAttempt("KUmsAll"),
                    JobAttempt("KUmsZeitSEPA", Date(0)),
                    JobAttempt("KUmsNew"),
                )

                var lastJobException: Exception? = null
                val job: HBCIJob = jobAttempts.firstNotNullOfOrNull { attempt ->
                    val r = runCatching {
                        val j = handler.newJob(attempt.name)
                        j.setParam("my", buildKonto(account, bic))
                        attempt.startDate?.let { j.setParam("startdate", sdf.format(it)) }
                        // addJob is called here (inside the runCatching) so that constraint
                        // validation errors (e.g. "Property my.bic wurde nicht gesetzt" when
                        // the BIC could not be resolved from the passport UPD yet) are caught
                        // and trigger a fallback to the next job instead of aborting entirely.
                        handler.addJob(j)
                        j
                    }
                    if (r.isSuccess) {
                        r.getOrThrow()
                    } else {
                        val ex = r.exceptionOrNull() as? Exception ?: throw r.exceptionOrNull()!!
                        if (ex.hasCause<JobNotSupportedException> { true } ||
                            ex.hasCause<InvalidUserDataException> { msg ->
                                msg.message?.let {
                                    it.contains(HBCI_NO_HIGHLEVEL_JOB_MSG) ||
                                    it.contains(HBCI_MISSING_BIC_MSG)
                                } == true
                            }) {
                            AppLogger.w(TAG, "Job nicht unterstützt, nächsten Fallback versuchen: ${ex.message}")
                            lastJobException = ex
                            null  // try next
                        } else {
                            throw ex
                        }
                    }
                } ?: run {
                    AppLogger.w(TAG, "Kein Kontoauszug-Job unterstützt – Abruf nicht möglich: ${lastJobException?.message}")
                    throw UnsupportedOperationException(
                        "Diese Bank unterstützt keinen HBCI-Kontoauszug-Abruf " +
                        "(weder KUmsAllCamt/KUmsZeitSEPA noch KUmsAll/KUmsNew werden in der BPD angeboten).",
                        lastJobException
                    )
                }

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
            if (e is UnsupportedOperationException)
                AppLogger.w(TAG, "fetchAccountStatement nicht unterstützt: ${e.message}")
            else
                AppLogger.e(TAG, "fetchAccountStatement fehlgeschlagen: ${e.message}", e)
        }
        wrapWrongPinResult(operationResult)
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────────

    private fun openSession(account: Account): Pair<HBCIHandler, HBCIPassport> {
        val blz = account.bankCode.ifBlank {
            // Derive BLZ from German IBAN (DE + 2 check digits + 8-digit BLZ + account number)
            blzFromIban(account.iban)
                ?: error("BLZ fehlt und kann nicht aus der IBAN ermittelt werden für Konto '${account.name}'")
        }
        AppLogger.d(TAG, "openSession: BLZ=$blz userId='${account.userId.ifBlank { "(leer)" }}' accountId=${account.id}")
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
        // Always override userId/customerId from the account's current settings.
        // If the passport file already existed (from a previous session), it may contain a
        // stale/empty userId – explicitly setting it here ensures the currently configured
        // Nutzerkennung is always used, regardless of what was persisted in the passport file.
        //
        // For standard German retail banking (PIN/TAN), customerId equals userId (Nutzerkennung).
        // This is consistent with the NEED_USERID/NEED_CUSTOMERID callback below, which also
        // returns the same account.userId for both.  If a bank ever requires a distinct
        // Kundenkennung, a dedicated customerId field can be added to Account.
        if (account.userId.isNotBlank()) {
            passport.userId = account.userId
            passport.customerId = account.userId
        }
        return try {
            val handler = HBCIHandler("300", passport)
            Pair(handler, passport)
        } catch (e: Exception) {
            // If the TAN method stored in the passport is no longer supported by the bank
            // (e.g. the bank updated their TAN method list), delete the stale passport file
            // and retry once with a fresh passport so hbci4j can re-negotiate.
            if (e.hasCause<InvalidUserDataException> { it.message?.contains("selected pintan method not supported") == true }
                && passportFile.exists()
            ) {
                AppLogger.w(TAG, "openSession: gespeicherte TAN-Methode ungültig – lösche Passport-Datei und versuche erneut")
                passportFile.delete()
                HBCIUtils.setParam("client.passport.PinTan.filename", passportFile.absolutePath)
                HBCIUtils.setParam("client.passport.PinTan.init", "1")
                val freshPassport = AbstractHBCIPassport.getInstance("PinTan") as AbstractHBCIPassport
                freshPassport.country = "DE"
                freshPassport.blz = blz
                if (account.userId.isNotBlank()) {
                    freshPassport.userId = account.userId
                    freshPassport.customerId = account.userId
                }
                val freshHandler = HBCIHandler("300", freshPassport)
                Pair(freshHandler, freshPassport)
            } else {
                throw e
            }
        }
    }

    /**
     * Returns true if any exception in the cause chain matches the given [predicate].
     * Limits traversal depth to avoid infinite loops on pathological circular chains.
     */
    private inline fun <reified T : Throwable> Throwable.hasCause(predicate: (T) -> Boolean): Boolean {
        var cause: Throwable? = this
        var depth = 0
        while (cause != null && depth < 20) {
            if (cause is T && predicate(cause)) return true
            cause = cause.cause
            depth++
        }
        return false
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

    private fun buildKonto(account: Account, bic: String = ""): Konto {
        val k = Konto()
        k.blz  = account.bankCode.ifBlank { blzFromIban(account.iban) ?: "" }
        k.iban = account.iban
        if (bic.isNotBlank()) k.bic = bic
        k.curr = account.currency.ifBlank { "EUR" }
        return k
    }

    /**
     * Looks up the BIC for [iban] from the UPD accounts stored in the given [passport].
     * Returns an empty string if the account is not found or the passport has no accounts yet
     * (e.g. on the very first connect before the UPD has been downloaded).
     */
    private fun bicFromPassport(passport: HBCIPassport, iban: String): String {
        val normalized = iban.replace(" ", "").uppercase()
        return passport.accounts
            ?.firstOrNull { it.iban?.replace(" ", "")?.uppercase() == normalized }
            ?.bic.orEmpty()
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
                    val uid = currentUserId.get() ?: ""
                    AppLogger.d(TAG, "HBCI Nutzerkennung-Anfrage (reason=$reason): userId='${uid.ifBlank { "(leer)" }}'")
                    retData?.replace(0, retData.length, uid)
                }
                NEED_PT_SECMECH -> {
                    val method = currentTanMethod.get() ?: ""
                    if (method.isNotBlank()) {
                        // User has explicitly configured a TAN security mechanism code
                        // (e.g. "900" for BBBank Secure Go / BestSign / pushTAN) → override.
                        AppLogger.i(TAG, "TAN-Verfahren-Auswahl: '$method'")
                        retData?.replace(0, retData.length, method)
                    } else {
                        // Auto mode: hbci4j passes the available methods as a pipe-delimited
                        // list in retData (format: "code:name|code:name|…").  If left unchanged
                        // the full list string is used as the selected method code, which always
                        // fails with "selected pintan method not supported".  Extract the first
                        // method code and set it so hbci4j receives a valid single code.
                        val stored = retData?.toString() ?: ""
                        AppLogger.i(TAG, "TAN-Verfahren-Auswahl: 'auto' (gespeichert: '${stored.ifBlank { "(leer)" }}')")
                        if (stored.contains("|")) {
                            val firstCode = stored.split("|").firstOrNull()?.substringBefore(":")?.trim() ?: ""
                            if (firstCode.isNotBlank()) {
                                AppLogger.i(TAG, "TAN-Verfahren-Auswahl: Auto-Auswahl erste Methode '$firstCode'")
                                retData?.replace(0, retData.length, firstCode)
                            }
                        }
                    }
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
                WRONG_PIN -> {
                    AppLogger.w(TAG, "HBCI: PIN ungültig (callback reason=$reason)")
                    wrongPinOnThread.set(true)
                }
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

    private fun wrongPinException(cause: Throwable?): Exception =
        Exception("Anmeldung fehlgeschlagen: PIN oder Nutzerkennung ungültig. Bitte überprüfe deine Online-Banking-Zugangsdaten (Nutzerkennung = dein Online-Banking-Login; PIN = deine Online-Banking-PIN).", cause)

    /**
     * Checks whether [HbciCallback] signalled a wrong PIN on the current thread.
     * If so, rewraps the failure with a user-friendly [wrongPinException].
     * Clears the [wrongPinOnThread] flag regardless.
     */
    private fun <T> wrapWrongPinResult(operationResult: Result<T>): Result<T> {
        val wrongPin = wrongPinOnThread.get() == true
        wrongPinOnThread.remove()
        return if (operationResult.isFailure && wrongPin)
            Result.failure(wrongPinException(operationResult.exceptionOrNull()))
        else
            operationResult
    }

    companion object {
        @Volatile private var hbciInitialized = false
    }
}

