package de.mybudgets.app

import android.content.res.ColorStateList
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import dagger.hilt.android.AndroidEntryPoint
import de.mybudgets.app.data.api.BackendStatus
import de.mybudgets.app.data.api.BackendStatusChecker
import de.mybudgets.app.databinding.ActivityMainBinding
import de.mybudgets.app.util.AppLogger
import de.mybudgets.app.worker.BackendSyncScheduler
import javax.inject.Inject
import kotlinx.coroutines.launch

private const val TAG = "MainActivity"

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    @Inject lateinit var statusChecker: BackendStatusChecker

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

            binding.vStatusLamp.setOnClickListener { statusChecker.triggerCheck() }
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    statusChecker.status.collect { bindLamp(it) }
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
