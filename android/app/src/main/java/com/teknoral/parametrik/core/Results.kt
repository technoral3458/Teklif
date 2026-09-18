package com.teknoral.parametrik.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Ağ çağrılarını IO dispatcher'ında çalıştırır ve bağlantı hatalarını
 * kullanıcıya gösterilebilir tek bir türe indirger. İptal yukarı taşınır.
 */
suspend fun <T> apiCall(block: suspend () -> T): Result<T> = withContext(Dispatchers.IO) {
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: UnknownHostException) {
        Result.failure(NetworkUnavailableException(cause = e))
    } catch (e: ConnectException) {
        Result.failure(NetworkUnavailableException(cause = e))
    } catch (e: SocketTimeoutException) {
        Result.failure(NetworkUnavailableException("Sunucu yanıt vermedi. Tekrar deneyin.", e))
    } catch (e: SSLException) {
        Result.failure(NetworkUnavailableException("Güvenli bağlantı kurulamadı.", e))
    } catch (e: Throwable) {
        Result.failure(e)
    }
}
