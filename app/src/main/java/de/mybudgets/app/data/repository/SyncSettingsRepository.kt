package de.mybudgets.app.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("sync_settings", Context.MODE_PRIVATE)
    
    var secureGoWaitSeconds: Int
        get() = prefs.getInt("secureGoWaitSeconds", 30)
        set(value) = prefs.edit().putInt("secureGoWaitSeconds", value).apply()
    
    var bulkSyncDelaySeconds: Int
        get() = prefs.getInt("bulkSyncDelaySeconds", 5)
        set(value) = prefs.edit().putInt("bulkSyncDelaySeconds", value).apply()
    
    var dnsRetryCount: Int
        get() = prefs.getInt("dnsRetryCount", 2)
        set(value) = prefs.edit().putInt("dnsRetryCount", value).apply()
    
    var dnsRetryDelaySeconds: Int
        get() = prefs.getInt("dnsRetryDelaySeconds", 3)
        set(value) = prefs.edit().putInt("dnsRetryDelaySeconds", value).apply()
    
    fun resetToDefaults() {
        secureGoWaitSeconds = 30
        bulkSyncDelaySeconds = 5
        dnsRetryCount = 2
        dnsRetryDelaySeconds = 3
    }
}
