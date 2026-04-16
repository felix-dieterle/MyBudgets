package de.mybudgets.app

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import dagger.hilt.android.AndroidEntryPoint
import de.mybudgets.app.databinding.ActivityMainBinding
import de.mybudgets.app.util.AppLogger

private const val TAG = "MainActivity"

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching {
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)

            val navHost = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
            if (navHost == null) {
                AppLogger.e(TAG, "Navigation Host Fragment konnte beim Start nicht gefunden werden.")
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
        }.onFailure { e ->
            AppLogger.e(TAG, "MainActivity konnte beim Start nicht vollständig initialisiert werden: ${e.message}", e)
        }
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
