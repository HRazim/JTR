package com.jtr.app.data.remote

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.Locale

/**
 * ApiClient — Configuration Retrofit pour l'API Nominatim.
 *
 * Inclut un User-Agent obligatoire (règles d'utilisation Nominatim) et
 * un intercepteur de journalisation HTTP en mode debug.
 */
object ApiClient {

    private const val BASE_URL = "https://nominatim.openstreetmap.org/"

    private val userAgentInterceptor = okhttp3.Interceptor { chain ->
        val request = chain.request().newBuilder()
            .header("User-Agent", "JTR-App/4.1 (contact-manager Android)")
            .build()
        chain.proceed(request)
    }

    /**
     * Accept-Language (v7.1.31) — pilote la langue des noms de lieux renvoyés par Nominatim
     * (`display_name`). Recalculé À CHAQUE requête depuis [Locale.getDefault] → toute nouvelle
     * recherche suit IMMÉDIATEMENT la langue active. Depuis v7.1.30, `Locale.getDefault()`
     * reflète fidèlement la langue choisie dans JTR (explicite) OU la locale de l'appareil
     * (« Langue du système »). Chaîne de fallback JTR → système → anglais : `<tag>,en;q=0.8`
     * (si la traduction du lieu manque, Nominatim retombe sur le nom local → jamais vide).
     */
    private val acceptLanguageInterceptor = okhttp3.Interceptor { chain ->
        val tag = Locale.getDefault().toLanguageTag()
        val value = if (tag.equals("en", ignoreCase = true) || tag.startsWith("en-", ignoreCase = true))
            tag else "$tag,en;q=0.8"
        val request = chain.request().newBuilder()
            .header("Accept-Language", value)
            .build()
        chain.proceed(request)
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(userAgentInterceptor)
        .addInterceptor(acceptLanguageInterceptor)
        .addInterceptor(loggingInterceptor)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    val nominatimApi: NominatimApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(NominatimApi::class.java)
    }
}
