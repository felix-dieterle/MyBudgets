package de.mybudgets.app.data.api

import de.mybudgets.app.util.AppLogger
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private var retrofit: Retrofit? = null
    private var currentBaseUrl: String = ""
    private var currentApiKey: String = ""

    fun getService(baseUrl: String, apiKey: String): ApiService {
        if (retrofit == null || baseUrl != currentBaseUrl || apiKey != currentApiKey) {
            currentBaseUrl = baseUrl
            currentApiKey = apiKey
            retrofit = buildRetrofit(baseUrl, apiKey)
        }
        return retrofit!!.create(ApiService::class.java)
    }

    private fun buildRetrofit(baseUrl: String, apiKey: String): Retrofit {
        val logging = HttpLoggingInterceptor { message ->
            AppLogger.d("HTTP", message)
        }.apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val authInterceptor = Interceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("X-API-Key", apiKey)
                .addHeader("Content-Type", "application/json")
                .build()
            chain.proceed(request)
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val url = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return Retrofit.Builder()
            .baseUrl(url)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}
