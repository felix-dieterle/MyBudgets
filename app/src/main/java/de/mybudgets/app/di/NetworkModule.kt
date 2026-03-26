package de.mybudgets.app.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.mybudgets.app.data.api.ApiClient
import de.mybudgets.app.data.api.ApiService
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideApiService(@ApplicationContext context: Context): ApiService {
        val prefs = context.getSharedPreferences("mybudgets_prefs", Context.MODE_PRIVATE)
        val baseUrl = prefs.getString("backend_url", "https://example.com/apps/finn/api.php") ?: ""
        val apiKey  = prefs.getString("api_key", "") ?: ""
        return ApiClient.getService(baseUrl, apiKey)
    }
}
