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

    var offlineMode: Boolean
        get()      = prefs.getBoolean("offline_mode", true)
        set(value) = prefs.edit().putBoolean("offline_mode", value).apply()
}
