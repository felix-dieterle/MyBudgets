package de.mybudgets.app.data.api

import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import de.mybudgets.app.util.AppLogger
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Einzige Gson-Konfiguration fuer Requests UND Antworten.
 *
 * Das PHP-Backend antwortet mit snake_case - die Endpoints geben `SELECT *`
 * zurueck, also die Spaltennamen. Die DTOs sind camelCase. Ohne diese Policy
 * erreichte `account_id` das Feld `accountId` nie: der Abruf importierte
 * Buchungen mit accountId = 0, categoryId = null, isRecurring = false,
 * createdAt = 0. Dass nichts davon auffiel, liegt daran, dass die
 * Transaction-Entitaet keinen Room-Fremdschluessel hat.
 *
 * Requests waren bis 10.2026 ebenfalls camelCase; dort fing
 * `snakeCaseKeys()` in api.php die Abweichung ab. Mit der Policy laufen beide
 * Richtungen auf dieselben Feldnamen zu, dort ist es nur noch ein No-Op.
 *
 * Die Policy wirkt auf Felder, NICHT auf Map-Schluessel - Antworten wie
 * `{"id": 4711}` lesen sich also unveraendert.
 */
internal val apiGson: Gson = GsonBuilder()
    .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
    .create()

/** Stellt sicher, dass eine Backend-URL ein Schema enthaelt (`http://`/`https://`). */
fun normalizeBaseUrl(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty() || trimmed.contains("://")) return trimmed
    return "http://$trimmed"
}

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
        // Redaction Pflicht: der Export des Fehlerprotokolls wird regelmaessig weitergegeben.
        // Ohne redactHeader landet der vollstaendige API-Key im Klartext in der Datei
        // (X-API-Key: AIRR… - so stand es am 05.10.2026 im exportierten Log).
        val logging = HttpLoggingInterceptor { message ->
            AppLogger.d("HTTP", message)
        }.apply {
            level = HttpLoggingInterceptor.Level.BODY
            redactHeader("X-API-Key")
            redactHeader("Authorization")
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

        val url = normalizeBaseUrl(baseUrl)
        val retrofitBase = if (url.endsWith("/")) url else "$url/"
        return Retrofit.Builder()
            .baseUrl(retrofitBase)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(apiGson))
            .build()
    }
}
