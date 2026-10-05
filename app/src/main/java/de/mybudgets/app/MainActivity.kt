package de.mybudgets.app

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import de.mybudgets.app.data.api.BackendStatus
import de.mybudgets.app.data.api.BackendStatusChecker
import de.mybudgets.app.data.api.PREF_REPUSH_ORPHANS
import de.mybudgets.app.data.api.SyncIndicatorState
import de.mybudgets.app.data.api.SyncStatusInfo
import de.mybudgets.app.data.api.SyncStatusMonitor
import de.mybudgets.app.databinding.ActivityMainBinding
import de.mybudgets.app.util.AppLogger
import de.mybudgets.app.worker.BackendSyncScheduler
import javax.inject.Inject
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "MainActivity"
private const val BLINK_ALPHA = 0.15f
private const val BLINK_STEP_MS = 400L

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    @Inject lateinit var statusChecker: BackendStatusChecker
    @Inject lateinit var syncMonitor: SyncStatusMonitor

    /** Laufende Blink-Animationen je View - ohne das bleibt ein Indicator nach onStop stehen. */
    private val blinkJobs = mutableMapOf<View, Job>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching {
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)

            val navHost = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
            if (navHost == null) {
                AppLogger.e(TAG, "Navigation Host Fragment konnte beim Start nicht gefunden werden.")
                showStartupErrorDialog()
                return
            }
            val navController = navHost.navController
            binding.bottomNavigation.setupWithNavController(navController)

            val prefs = getSharedPreferences("mybudgets_prefs", MODE_PRIVATE)
            val legalAccepted = prefs.getBoolean("legal_accepted", false)

            if (!legalAccepted) {
                showLegalAcceptDialog(prefs)
            } else {
                val onboardingShown = prefs.getBoolean("onboarding_shown", false)
                if (!onboardingShown) {
                    showOnboardingDialog(prefs)
                }
            }

            BackendSyncScheduler.enqueue(this)

            binding.vStatusLamp.setOnClickListener { showBackendDiagnosis() }
            binding.vStatusLamp.setOnLongClickListener {
                openLogConsole()
                true
            }
            binding.ivSyncStatus.setOnClickListener { showSyncDetails() }
            binding.ivSyncStatus.setOnLongClickListener {
                openLogConsole()
                true
            }
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    launch {
                        statusChecker.status.collect { bindLamp(it) }
                    }
                    launch {
                        statusChecker.diagnosis.collect { d ->
                            binding.vStatusLamp.contentDescription =
                                getString(R.string.status_lamp_desc, getString(R.string.status_lamp_state, d.status, d.headline))
                        }
                    }
                    launch {
                        statusChecker.checking.collect { setBlinking(binding.vStatusLamp, it) }
                    }
                    launch {
                        syncMonitor.status.collect { bindSyncIcon(it) }
                    }
                }
            }
        }.onFailure { e ->
            AppLogger.e(TAG, "MainActivity konnte beim Start nicht vollständig initialisiert werden: ${e.message}", e)
            showStartupErrorDialog()
        }
    }

    override fun onStart() {
        super.onStart()
        statusChecker.start()
    }

    override fun onStop() {
        super.onStop()
        statusChecker.stop()
        stopAllBlinking()
    }

    private fun bindLamp(status: BackendStatus) {
        val (color, descRes) = when (status) {
            BackendStatus.GRAY  -> R.color.status_lamp_gray  to R.string.status_lamp_disabled
            BackendStatus.GREEN -> R.color.status_lamp_green to R.string.status_lamp_ok
            BackendStatus.RED   -> R.color.status_lamp_red   to R.string.status_lamp_unreachable
        }
        binding.vStatusLamp.backgroundTintList = ColorStateList.valueOf(getColor(color))
        binding.vStatusLamp.contentDescription = getString(R.string.status_lamp_desc, getString(descRes))
    }

    private fun bindSyncIcon(info: SyncStatusInfo) {
        val (icon, color, descRes) = when (info.state) {
            SyncIndicatorState.DISABLED -> Triple(R.drawable.ic_sync_disabled, R.color.sync_status_disabled, R.string.sync_state_disabled)
            SyncIndicatorState.SYNCING -> Triple(R.drawable.ic_sync_syncing, R.color.sync_status_syncing, R.string.sync_state_syncing)
            SyncIndicatorState.ERROR -> Triple(R.drawable.ic_sync_error, R.color.sync_status_error, R.string.sync_state_error)
            SyncIndicatorState.PENDING -> Triple(R.drawable.ic_sync_pending, R.color.sync_status_pending, R.string.sync_state_pending)
            SyncIndicatorState.NEVER_SYNCED -> Triple(R.drawable.ic_sync_pending, R.color.sync_status_pending, R.string.sync_state_never_synced)
            SyncIndicatorState.MISMATCH -> Triple(R.drawable.ic_sync_mismatch, R.color.sync_status_pending, R.string.sync_state_mismatch)
            SyncIndicatorState.UP_TO_DATE -> Triple(R.drawable.ic_sync_up_to_date, R.color.sync_status_up_to_date, R.string.sync_state_up_to_date)
        }
        binding.ivSyncStatus.setImageResource(icon)
        binding.ivSyncStatus.imageTintList = ColorStateList.valueOf(getColor(color))
        binding.ivSyncStatus.contentDescription = getString(R.string.sync_indicator_desc, getString(descRes))
        setBlinking(binding.ivSyncStatus, info.state == SyncIndicatorState.SYNCING)
        bindInlineStatus(info, color)
    }

    /**
     * Kurzer Text direkt neben dem Icon. Das Icon allein sagt nur "etwas stimmt nicht" - der
     * Grund bleibt im Dialog versteckt, den man erst finden muss. Fehler und abweichende
     * Zaehler stehen deshalb sichtbar in der Kopfzeile; alles andere bleibt unauffaellig.
     */
    private fun bindInlineStatus(info: SyncStatusInfo, colorRes: Int) {
        val text = when (info.state) {
            SyncIndicatorState.ERROR -> if (info.lastError.isNotBlank()) {
                getString(R.string.sync_inline_error_reason, info.lastError)
            } else {
                getString(R.string.sync_inline_error)
            }
            SyncIndicatorState.MISMATCH -> getString(
                R.string.sync_inline_mismatch,
                info.localTransactionCount,
                info.remoteTransactionCount
            )
            else -> null
        }
        binding.tvSyncStatusInline.apply {
            if (text == null) {
                visibility = View.GONE
            } else {
                this.text = text
                setTextColor(getColor(colorRes))
                visibility = View.VISIBLE
            }
        }
    }

    /**
     * Laesst [view] weich pulsieren, solange [blinking] true ist. Health-Laempchen = laufende
     * Pruefung, Sync-Icon = laufender Abgleich. Das Pulsieren macht einen mehrere Sekunden
     * dauernden Vorgang von einem haengenden Indicator unterscheidbar.
     */
    private fun setBlinking(view: View, blinking: Boolean) {
        blinkJobs.remove(view)?.cancel()
        view.animate().cancel()
        view.alpha = 1f
        if (!blinking) return
        blinkJobs[view] = lifecycleScope.launch {
            while (isActive) {
                view.animate().alpha(BLINK_ALPHA).setDuration(BLINK_STEP_MS).withEndAction(null).start()
                delay(BLINK_STEP_MS)
                view.animate().alpha(1f).setDuration(BLINK_STEP_MS).withEndAction(null).start()
                delay(BLINK_STEP_MS)
            }
        }
    }

    private fun stopAllBlinking() {
        blinkJobs.keys.toList().forEach { setBlinking(it, false) }
        blinkJobs.clear()
    }

    /** Klick auf das Sync-Icon: was genau ist los - und wie wird es wieder gut? */
    private fun showSyncDetails() {
        if (isFinishing || isDestroyed) return
        val info = syncMonitor.status.value
        val builder = MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.sync_detail_title, info.state.name))
            .setMessage(buildSyncReport(info))
            .setPositiveButton(R.string.sync_detail_sync_now) { _, _ ->
                BackendSyncScheduler.enqueue(this)
            }
            .setNeutralButton(R.string.status_diagnose_open_log) { _, _ -> openLogConsole() }
            .setNegativeButton(android.R.string.cancel, null)

// "Jetzt synchronisieren" loest den Fall nicht: die Buchungen haben eine remoteId und
        // werden deshalb uebersprungen. Erst wenn sie zuruecksetzt sind, sendet der naechste
        // Lauf sie wirklich - das ist der einzige Weg aus diesem Zustand heraus.
        // Der neutrale Knopf wechselt dabei vom Log-Knopf zum Repush-Knopf; das Log bleibt
        // ueber den Long-Press auf dem Icon erreichbar.
        if (info.orphanCount > 0) {
            builder.setNeutralButton(
                getString(R.string.sync_detail_repush_orphans, info.orphanCount)
            ) { _, _ -> confirmRepushOrphans(info.orphanCount) }
        }
        builder.show()
    }

    /** Rueckfrage vor dem Zuruecksetzen: koennte im Backend doch noch eine gleiche Buchung geben. */
    private fun confirmRepushOrphans(count: Int) {
        if (isFinishing || isDestroyed) return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.sync_repush_title)
            .setMessage(getString(R.string.sync_repush_message, count))
            .setPositiveButton(R.string.sync_repush_confirm) { _, _ ->
                getSharedPreferences("mybudgets_prefs", MODE_PRIVATE)
                    .edit()
                    .putBoolean(PREF_REPUSH_ORPHANS, true)
                    .apply()
                BackendSyncScheduler.enqueue(this)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun buildSyncReport(info: SyncStatusInfo): String = buildString {
        appendLine(getString(R.string.sync_report_state, getString(stateLabelRes(info.state))))
        appendLine(getString(R.string.sync_report_meaning, getString(stateMeaningRes(info.state))))
        if (info.state != SyncIndicatorState.DISABLED) {
            appendLine(getString(R.string.sync_report_local_count, info.localTransactionCount, info.localAccountCount))
            if (info.remoteKnown) {
                appendLine(
                    getString(
                        R.string.sync_report_remote_count,
                        info.remoteTransactionCount,
                        info.remoteAccountCount,
                        formatSyncMoment(info.lastSyncTime)
                    )
                )
                if (info.mismatched) {
                    appendLine(
                        getString(
                            R.string.sync_report_mismatch,
                            info.localTransactionCount,
                            info.remoteTransactionCount,
                            info.localAccountCount,
                            info.remoteAccountCount
                        )
                    )
                }
                if (info.orphanCount > 0) {
                    appendLine(getString(R.string.sync_report_orphans, info.orphanCount))
                }
            } else {
                appendLine(getString(R.string.sync_report_remote_unknown))
            }
            appendLine(getString(R.string.sync_report_pending, info.pendingCount))
        }
        appendLine(
            getString(
                R.string.sync_report_last_ok,
                formatSyncMoment(info.lastSyncTime)
            )
        )
        if (info.lastError.isNotEmpty()) {
            appendLine(getString(R.string.sync_report_failed, info.lastError))
        } else if (info.state == SyncIndicatorState.ERROR) {
            appendLine(getString(R.string.sync_report_error_empty))
        }
        if (info.queued) appendLine(getString(R.string.sync_report_queued))
        appendLine(getString(R.string.sync_report_url, info.backendUrl.ifEmpty { "nicht gesetzt" }))
    }

    /** Zeitpunkt eines gemessenen Werts - "nie", solange keiner vorliegt. */
    private fun formatSyncMoment(ts: Long): String =
        if (ts <= 0L) getString(R.string.sync_report_never)
        else DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(ts))

    private fun stateLabelRes(state: SyncIndicatorState): Int = when (state) {
        SyncIndicatorState.DISABLED -> R.string.sync_state_disabled
        SyncIndicatorState.SYNCING -> R.string.sync_state_syncing
        SyncIndicatorState.ERROR -> R.string.sync_state_error
        SyncIndicatorState.PENDING -> R.string.sync_state_pending
        SyncIndicatorState.NEVER_SYNCED -> R.string.sync_state_never_synced
        SyncIndicatorState.MISMATCH -> R.string.sync_state_mismatch
        SyncIndicatorState.UP_TO_DATE -> R.string.sync_state_up_to_date
    }

    private fun stateMeaningRes(state: SyncIndicatorState): Int = when (state) {
        SyncIndicatorState.DISABLED -> R.string.sync_meaning_disabled
        SyncIndicatorState.SYNCING -> R.string.sync_meaning_syncing
        SyncIndicatorState.ERROR -> R.string.sync_meaning_error
        SyncIndicatorState.PENDING -> R.string.sync_meaning_pending
        SyncIndicatorState.NEVER_SYNCED -> R.string.sync_meaning_never_synced
        SyncIndicatorState.MISMATCH -> R.string.sync_meaning_mismatch
        SyncIndicatorState.UP_TO_DATE -> R.string.sync_meaning_up_to_date
    }

    /**
     * Tapping the lamp answers the only question that matters when it is red:
     * "what exactly failed?". Runs a fresh check and shows the full diagnosis.
     */
    private fun showBackendDiagnosis() {
        if (isFinishing || isDestroyed) return
        lifecycleScope.launch {
            val diagnosis = runCatching { statusChecker.checkNow() }
                .getOrElse { e ->
                    AppLogger.e(TAG, "Diagnose-Abfrage fehlgeschlagen: ${e.message}", e)
                    null
                }
            if (isFinishing || isDestroyed) return@launch
            if (diagnosis == null) {
                Toast.makeText(this@MainActivity, R.string.status_diagnose_failed, Toast.LENGTH_LONG).show()
                return@launch
            }
            AlertDialog.Builder(this@MainActivity)
                .setTitle(getString(R.string.status_diagnose_title, diagnosis.status.name))
                .setMessage(diagnosis.report())
                .setPositiveButton(R.string.status_diagnose_retry) { _, _ -> showBackendDiagnosis() }
                .setNeutralButton(R.string.status_diagnose_open_log) { _, _ -> openLogConsole() }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun openLogConsole() {
        if (isFinishing || isDestroyed) return
        runCatching {
            val navHost = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
            navHost.navController.navigate(R.id.logConsoleFragment)
        }.onFailure {
            AppLogger.e(TAG, "Fehlerprotokoll konnte nicht geöffnet werden: ${it.message}", it)
        }
    }

    private fun showStartupErrorDialog() {
        if (isFinishing || isDestroyed) return
        Toast.makeText(this, R.string.error_startup_failed, Toast.LENGTH_LONG).show()
        window?.decorView?.postDelayed({
            if (!isFinishing) finish()
        }, 1800L)
    }

    private fun showLegalAcceptDialog(prefs: android.content.SharedPreferences) {
        AlertDialog.Builder(this)
            .setTitle(R.string.legal_accept_title)
            .setMessage(R.string.legal_accept_message)
            .setCancelable(false)
            .setPositiveButton(R.string.legal_accept_button) { _, _ ->
                prefs.edit().putBoolean("legal_accepted", true).apply()
                showOnboardingDialog(prefs)
            }
            .setNegativeButton(R.string.legal_decline_button) { _, _ ->
                finish()
            }
            .show()
    }

    private fun showOnboardingDialog(prefs: android.content.SharedPreferences) {
        AlertDialog.Builder(this)
            .setTitle(R.string.onboarding_title)
            .setMessage(R.string.onboarding_message)
            .setPositiveButton(R.string.onboarding_ok) { _, _ ->
                prefs.edit().putBoolean("onboarding_shown", true).apply()
            }
            .show()
    }
}
