package com.jtr.app.data.remote

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

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
            .header("User-Agent", "JTR-App/4.0 (contact-manager Android)")
            .build()
        chain.proceed(request)
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(userAgentInterceptor)
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
