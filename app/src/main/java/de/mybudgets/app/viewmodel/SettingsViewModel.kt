package de.mybudgets.app.viewmodel

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("mybudgets_prefs", Context.MODE_PRIVATE)

    var backendUrl: String
        get()      = prefs.getString("backend_url", "") ?: ""
        set(value) = prefs.edit().putString("backend_url", value).apply()

    var apiKey: String
        get()      = prefs.getString("api_key", "") ?: ""
        set(value) = prefs.edit().putString("api_key", value).apply()

    var openrouterApiKey: String
        get()      = prefs.getString("openrouter_key", "") ?: ""
        set(value) = prefs.edit().putString("openrouter_key", value).apply()

    var githubCopilotToken: String
        get()      = prefs.getString("github_copilot_token", "") ?: ""
        set(value) = prefs.edit().putString("github_copilot_token", value).apply()

    var aiProvider: String
        get()      = prefs.getString("ai_provider", "github_copilot") ?: "github_copilot"
        set(value) = prefs.edit().putString("ai_provider", value).apply()

    var aiModel: String
        get()      = prefs.getString("ai_model", "gpt-4.1") ?: "gpt-4.1"
        set(value) = prefs.edit().putString("ai_model", value).apply()

    var offlineMode: Boolean
        get()      = prefs.getBoolean("offline_mode", true)
        set(value) = prefs.edit().putBoolean("offline_mode", value).apply()

    val lastSyncTime: Long
        get() = prefs.getLong("last_sync_time", 0L)

    var fintsBankCode: String
        get()      = prefs.getString("fints_bank_code", "") ?: ""
        set(value) = prefs.edit().putString("fints_bank_code", value).apply()

    var fintsUserId: String
        get()      = prefs.getString("fints_user_id", "") ?: ""
        set(value) = prefs.edit().putString("fints_user_id", value).apply()
}
