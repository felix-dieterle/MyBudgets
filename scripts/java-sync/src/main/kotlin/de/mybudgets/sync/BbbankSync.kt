package de.mybudgets.sync

import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import org.kapott.hbci.GV.HBCIJob
import org.kapott.hbci.GV_Result.GVRKUms
import org.kapott.hbci.callback.AbstractHBCICallback
import org.kapott.hbci.exceptions.HBCI_Exception
import org.kapott.hbci.manager.HBCIHandler
import org.kapott.hbci.manager.HBCIUtils
import org.kapott.hbci.passport.AbstractHBCIPassport
import org.kapott.hbci.passport.HBCIPassport
import org.kapott.hbci.structures.Konto

// Load config
fun loadConfig(): Properties {
    val props = Properties()
    val configFile = File("config.properties")
    if (!configFile.exists()) {
        val fallbackConfig = File("scripts/java-sync/config.properties")
        if (fallbackConfig.exists()) {
            props.load(fallbackConfig.inputStream())
        } else {
            throw Exception("config.properties nicht gefunden")
        }
    } else {
        props.load(configFile.inputStream())
    }
    return props
}

fun maskUser(s: String): String {
    if (s.length <= 4) return "****"
    return s.substring(0, 2) + "***" + s.substring(s.length - 2)
}

// Simple callback
class SimpleCallback(
    private val userId: String,
    private val pin: String,
    private val blz: String,
    private val tanMethod: String
) : AbstractHBCICallback() {

    override fun callback(passport: HBCIPassport?, reason: Int, msg: String?, datatype: Int, retData: StringBuffer?) {
        when (reason) {
            NEED_COUNTRY -> retData?.replace(0, retData.length, "DE")
            NEED_BLZ -> retData?.replace(0, retData.length, blz)
            NEED_HOST -> retData?.replace(0, retData.length, "fints2.atruvia.de")
            NEED_PORT -> retData?.replace(0, retData.length, "443")
            NEED_FILTER -> retData?.replace(0, retData.length, "Base64")
            NEED_USERID, NEED_CUSTOMERID -> retData?.replace(0, retData.length, userId)
            NEED_PT_SECMECH -> {
                // Return configured TAN method if set, otherwise empty string for auto-select
                val method = if (tanMethod.isNotBlank()) tanMethod else ""
                retData?.replace(0, retData.length, method)
                println("TAN-Methode: ${if (method.isNotBlank()) method else "Auto-Select"}")
            }
            NEED_PT_PIN -> retData?.replace(0, retData.length, pin)
            NEED_PT_TAN -> {
                print("TAN eingeben: ")
                val tan = readlnOrNull() ?: ""
                retData?.replace(0, retData.length, tan)
            }
            NEED_PASSPHRASE_LOAD, NEED_PASSPHRASE_SAVE -> retData?.replace(0, retData.length, "default-passphrase")
            else -> println("Callback reason=$reason msg=$msg datatype=$datatype")
        }
    }

    override fun log(msg: String?, level: Int, date: Date?, trace: StackTraceElement?) {
        // Output all logs for debugging
        println("[HBCI L$level] $msg")
    }

    override fun status(passport: HBCIPassport?, statusTag: Int, o: Array<out Any?>?) {
        // Status callback - not used in this simple implementation
    }
}

fun main() {
    try {
        // Load config
        val config = loadConfig()
        val iban = config.getProperty("iban") ?: throw Exception("iban nicht gesetzt")
        val userId = config.getProperty("userId") ?: throw Exception("userId nicht gesetzt")
        val pin = config.getProperty("pin") ?: throw Exception("pin nicht gesetzt")
        var blz = config.getProperty("blz", "")
        val tanMethod = config.getProperty("tanMethod", "")
        val daysBack = config.getProperty("daysBack", "30").toInt()
        val debug = config.getProperty("debug", "false").toBoolean()

        println("=== BBBank Sync (Kotlin) ===")
        println("IBAN: $iban")
        println("User: ${maskUser(userId)}")
        println("BLZ: $blz")
        println("TAN-Methode: $tanMethod")
        println("Tage zurück: $daysBack")
        println("Debug: $debug")
        println()

        // Derive BLZ from IBAN if not set
        if (blz.isEmpty()) {
            if (iban.startsWith("DE") && iban.length >= 14) {
                blz = iban.substring(4, 12)
                println("BLZ aus IBAN abgeleitet: $blz")
            } else {
                throw Exception("BLZ nicht gesetzt und kann nicht aus IBAN abgeleitet werden")
            }
        }

        // Initialize HBCI
        val props = Properties()
        props.setProperty("client.product.id", "MyBudgets")
        props.setProperty("client.product.version", "1.0")
        props.setProperty("log.loglevel.default", "5") // Maximum log level for debugging
        HBCIUtils.init(props, SimpleCallback(userId, pin, blz, tanMethod))

        // Setup passport directory
        val passportDir = File("scripts/java-sync/passports")
        passportDir.mkdirs()
        val passportFile = File(passportDir, "passport_$blz.dat")

        HBCIUtils.setParam("client.passport.PinTan.filename", passportFile.absolutePath)
        HBCIUtils.setParam("client.passport.PinTan.init", "1")
        HBCIUtils.setParam("client.passport.default", "PinTan")

        // Create passport
        val passport = AbstractHBCIPassport.getInstance("PinTan") as AbstractHBCIPassport

        println("Passport class: ${passport.javaClass.name}")
        println("Passport superclass: ${passport.javaClass.superclass.name}")

        // Set fields directly (Kotlin allows access to public fields)
        passport.country = "DE"
        println("Set country to DE")

        passport.blz = blz
        println("Set BLZ to $blz")

        passport.userId = userId
        println("Set userId to ${maskUser(userId)}")

        passport.customerId = userId
        println("Set customerId to ${maskUser(userId)}")

        if (tanMethod.isNotEmpty()) {
            HBCIUtils.setParam("client.passport.PinTan.tanmethod", tanMethod)
        }

        // Create handler
        val handler = HBCIHandler("300", passport)

        // BIC Lookup - extract BIC from passport UPD (like the app does)
        println("[1/4] BIC aus Passport UPD abfragen...")
        val accounts = passport.accounts
        if (accounts.isEmpty()) {
            throw Exception("Keine Konten im Passport UPD gefunden")
        }
        val account = accounts[0]
        val bic = account.bic
        println("BIC: $bic")

        // Fetch account statement
        println("[2/4] Kontoauszug abrufen...")
        val sdf = SimpleDateFormat("yyyy-MM-dd")
        val startDate = Date(System.currentTimeMillis() - (daysBack * 24L * 60 * 60 * 1000))
        val endDate = Date()

        // Try different job types
        val jobTypes = listOf("KUmsAllCamt", "KUmsZeitSEPA", "KUmsAll", "KUmsNew")
        var job: HBCIJob? = null
        var result: Any? = null

        for (jobType in jobTypes) {
            try {
                println("Versuche Job-Typ: $jobType")
                job = handler.newJob(jobType)
                // Set parameters directly
                job.setParam("my", account)
                job.setParam("my.bic", bic)
                job.setParam("startdate", sdf.format(startDate))
                job.setParam("enddate", sdf.format(endDate))
                job.addToQueue()
                handler.execute()
                result = job.jobResult
                println("Job-Typ $jobType erfolgreich")
                break
            } catch (e: HBCI_Exception) {
                println("Job-Typ $jobType fehlgeschlagen: ${e.message}")
                job = null
            }
        }

        if (job == null) {
            throw Exception("Kein Job-Typ erfolgreich")
        }

        // Parse result using GVRKUms exactly like the app does
        println("[3/4] Transaktionen parsen...")
        val transactions = mutableListOf<Map<String, Any>>()

        try {
            val gvrKUms = result as? GVRKUms
                ?: error("Unerwartetes Ergebnis vom Kontoauszug-Job")

            val flatData = gvrKUms.flatData
            println("flatData Größe: ${flatData.size}")
            for (entry in flatData) {
                val isIncome = entry.value.longValue >= 0
                val transaction = mutableMapOf<String, Any>()
                transaction["account_id"] = "default" // In der App: account.id
                transaction["amount"] = kotlin.math.abs(entry.value.doubleValue)
                transaction["description"] = entry.usage.joinToString(" ").trim().ifBlank { entry.other?.name ?: "" }
                transaction["date"] = entry.valuta?.time ?: entry.bdate?.time ?: System.currentTimeMillis()
                transaction["type"] = if (isIncome) "INCOME" else "EXPENSE"
                transaction["note"] = entry.other?.name ?: ""
                transaction["remote_id"] = entry.id ?: ""
                transactions.add(transaction)
            }
        } catch (e: Exception) {
            println("Konnte Ergebnis nicht parsen: ${e.message}")
            e.printStackTrace()
        }

        // Output transactions
        println("[4/4] Transaktionen ausgeben...")
        println("Gefundene Transaktionen: ${transactions.size}")
        for (tx in transactions) {
            println("  Datum: ${tx["date"]}, Betrag: ${tx["amount"]}, Typ: ${tx["type"]}, Beschreibung: ${tx["description"]}, Note: ${tx["note"]}, Remote ID: ${tx["remote_id"]}")
        }

        // Close
        handler.close()

        println("Sync erfolgreich abgeschlossen")

    } catch (e: Exception) {
        println("Fehler: ${e.message}")
        e.printStackTrace()
    }
}
