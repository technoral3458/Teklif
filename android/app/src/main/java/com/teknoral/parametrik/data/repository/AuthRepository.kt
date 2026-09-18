package com.teknoral.parametrik.data.repository

import com.teknoral.parametrik.core.ServerMessageException
import com.teknoral.parametrik.core.UnexpectedResponseException
import com.teknoral.parametrik.core.ValidationException
import com.teknoral.parametrik.core.apiCall
import com.teknoral.parametrik.data.local.PersistentCookieJar
import com.teknoral.parametrik.data.local.SettingsStore
import com.teknoral.parametrik.data.remote.Http
import com.teknoral.parametrik.data.remote.ParametricApi
import com.teknoral.parametrik.data.remote.ServerConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val api: ParametricApi,
    private val cookieJar: PersistentCookieJar,
    private val settings: SettingsStore,
    private val serverConfig: ServerConfig
) {

    /**
     * 303 + Set-Cookie → başarılı. 200 (HTML gövde) → hatalı giriş.
     */
    suspend fun login(email: String, password: String): Result<Unit> = apiCall {
        val response = api.login(email.trim(), password)
        when {
            Http.isRedirect(response.code()) -> {
                Http.queryValue(Http.location(response), "err")?.let { throw ServerMessageException(it) }
                settings.setLastEmail(email.trim())
            }
            response.code() == 200 ->
                throw ServerMessageException("E-posta veya şifre hatalı ya da hesabınız onaylı değil.")
            else -> throw UnexpectedResponseException(response.code())
        }
    }

    suspend fun logout(): Result<Unit> = apiCall {
        runCatching { api.logout() }
        cookieJar.clear()
    }

    /** GET /parametric → 200 ise oturum açık. */
    suspend fun hasValidSession(): Result<Boolean> = apiCall {
        if (serverConfig.baseUrl == null) return@apiCall false
        if (!cookieJar.hasSessionCookie()) return@apiCall false
        val response = api.sessionProbe()
        response.code() == 200 && !Http.isLoginRedirect(Http.location(response))
    }

    /**
     * Girilen adresi geçici olarak devreye alıp bağlantıyı dener.
     * @return oturum açık mı
     */
    suspend fun probeServer(rawUrl: String): Result<Boolean> {
        val normalized = SettingsStore.normalizeBaseUrl(rawUrl)
        val previous = serverConfig.baseUrl?.toString()
        serverConfig.update(normalized)
        if (serverConfig.baseUrl == null) {
            serverConfig.update(previous)
            return Result.failure(ValidationException("Sunucu adresi geçersiz. Örnek: https://panel.firma.com"))
        }
        val result = apiCall {
            val response = api.sessionProbe()
            response.code() == 200 && !Http.isLoginRedirect(Http.location(response))
        }
        if (result.isFailure) serverConfig.update(previous)
        return result
    }

    /** Adres değişirse eski sunucunun oturumu geçersizdir. */
    suspend fun saveServer(rawUrl: String): Result<Unit> {
        val normalized = SettingsStore.normalizeBaseUrl(rawUrl)
        if (normalized.isBlank()) {
            return Result.failure(ValidationException("Sunucu adresi boş olamaz."))
        }
        val previousHost = serverConfig.baseUrl?.host
        settings.setBaseUrl(normalized)
        serverConfig.update(normalized)
        if (previousHost != null && previousHost != serverConfig.baseUrl?.host) cookieJar.clear()
        return Result.success(Unit)
    }

    /** Sunucu adresi değiştiğinde eski oturum geçersizdir. */
    fun clearSession() = cookieJar.clear()
}
