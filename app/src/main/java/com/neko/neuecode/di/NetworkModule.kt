package com.neko.neuecode.di

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.neko.neuecode.data.local.cookie.PersistentCookieJar
import com.neko.neuecode.data.remote.api.PersonalApi
import com.neko.neuecode.data.remote.model.AppLoginResponse
import com.neko.neuecode.data.remote.model.AppLoginResponseDeserializer
import com.neko.neuecode.data.remote.model.LoginCheckData
import com.neko.neuecode.data.remote.model.LoginCommitData
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val PERSONAL_BASE_URL = "https://personal.neu.edu.cn/"

    private val SENSITIVE_HEADER_NAMES = setOf(
        "authorization-str",
        "authorization",
        "cookie",
        "set-cookie"
    )

    @Provides
    @Singleton
    fun provideOkHttpClient(
        cookieJar: PersistentCookieJar
    ): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor { message ->
            Timber.tag("OkHttp").d(sanitizeNetworkLog(message))
        }.apply {
            // Keep logs useful for debugging while avoiding encrypted bodies,
            // tickets, cookies, credentials and personal data in local files/logcat.
            level = HttpLoggingInterceptor.Level.HEADERS
        }

        val metadataInterceptor = okhttp3.Interceptor { chain ->
            val request = chain.request()
            Timber.tag("OkHttp").i(
                buildString {
                    append("=== REQUEST ===\n")
                    append("URL: ${request.url}\n")
                    append("Method: ${request.method}\n")
                    append("Headers: ${sanitizeHeaders(request.headers)}")
                }
            )

            val response = chain.proceed(request)

            Timber.tag("OkHttp").i(
                buildString {
                    append("=== RESPONSE ===\n")
                    append("Status: ${response.code} ${response.message}\n")
                    append("Headers: ${sanitizeHeaders(response.headers)}")
                }
            )

            response
        }

        return OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .addInterceptor(metadataInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        val gson = GsonBuilder()
            .setLenient()
            .registerTypeAdapter(
                object : TypeToken<AppLoginResponse<LoginCheckData>>() {}.type,
                AppLoginResponseDeserializer<LoginCheckData>()
            )
            .registerTypeAdapter(
                object : TypeToken<AppLoginResponse<LoginCommitData>>() {}.type,
                AppLoginResponseDeserializer<LoginCommitData>()
            )
            .create()

        return Retrofit.Builder()
            .baseUrl(PERSONAL_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    fun providePersonalApi(retrofit: Retrofit): PersonalApi {
        return retrofit.create(PersonalApi::class.java)
    }

    private fun sanitizeHeaders(headers: Headers): String {
        return headers.names().joinToString(prefix = "[", postfix = "]") { name ->
            if (SENSITIVE_HEADER_NAMES.contains(name.lowercase())) {
                "$name=<redacted>"
            } else {
                "$name=${headers[name]}"
            }
        }
    }

    private fun sanitizeNetworkLog(message: String): String {
        return message
            .replace(Regex("(?i)(authorization-str: )\\S+"), "$1<redacted>")
            .replace(Regex("(?i)(Cookie: ).*"), "$1<redacted>")
            .replace(Regex("(?i)(Set-Cookie: ).*"), "$1<redacted>")
            .replace(Regex("(?i)(content=)[^&\\s]+"), "$1<redacted>")
            .replace(Regex("(?i)(password=)[^&\\s]+"), "$1<redacted>")
            .replace(Regex("TGT-[A-Za-z0-9._:-]+"), "TGT-<redacted>")
            .replace(Regex("ST-[A-Za-z0-9._:-]+"), "ST-<redacted>")
    }
}
