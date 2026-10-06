package com.example.data.network.api

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object MovieBoxApiClient {

    private const val BASE_URL = "https://h5-api.aoneroom.com/"

    private val okHttpClient: OkHttpClient by lazy {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor { chain ->
                val original = chain.request()
                val requestBuilder = original.newBuilder()
                    .header("User-Agent", "StreamFlix/2.0 (Android; Mobile)")
                    .header("Accept", "application/json")
                    .header("callerSource", "node-frontend")
                    .header("Referer", "https://h5.inmoviebox.com/")
                    .header("Origin", "https://h5.inmoviebox.com")
                    .method(original.method, original.body)
                chain.proceed(requestBuilder.build())
            }
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    val service: MovieBoxApiService by lazy {
        retrofit.create(MovieBoxApiService::class.java)
    }

    /**
     * Resolves poster or image URLs with TMDB and CDN path support.
     */
    fun resolveImageUrl(url: String?): String {
        if (url.isNullOrBlank()) return ""
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url
        }
        if (url.startsWith("/")) {
            // TMDB relative poster path fallback
            return "https://image.tmdb.org/t/p/w780$url"
        }
        return url
    }
}
