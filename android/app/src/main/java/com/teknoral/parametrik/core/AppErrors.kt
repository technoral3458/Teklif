package com.teknoral.parametrik.core

import java.io.IOException

/** Sunucunun `err=` parametresiyle döndürdüğü mesaj — kullanıcıya aynen gösterilir. */
class ServerMessageException(val serverMessage: String) : IOException(serverMessage)

/** Oturum kapalı ya da düşmüş; giriş ekranına dönülmeli. */
class AuthRequiredException(
    message: String = "Oturumunuz sona ermiş. Lütfen tekrar giriş yapın."
) : IOException(message)

/** Sunucuya hiç ulaşılamadı (DNS, timeout, kapalı port...). */
class NetworkUnavailableException(
    message: String = "Sunucuya ulaşılamıyor. Bağlantınızı ve sunucu adresini kontrol edin.",
    cause: Throwable? = null
) : IOException(message, cause)

/** Sunucu adresi henüz ayarlanmamış. */
class ServerNotConfiguredException(
    message: String = "Önce Ayarlar ekranından sunucu adresini girin."
) : IOException(message)

/** Sunucuda beklenen JSON uç noktası yok (404). */
class MissingEndpointException(val endpoint: String) : IOException(
    "Sunucuda $endpoint uç noktası bulunamadı. Sunucu sürümünü güncelleyin."
)

/** İstemci tarafı doğrulama hatası. */
class ValidationException(message: String) : IllegalArgumentException(message)

/** Beklenmeyen HTTP kodu. */
class UnexpectedResponseException(val code: Int) : IOException(
    "Sunucu beklenmeyen bir yanıt döndürdü (HTTP $code)."
)

fun Throwable.userMessage(): String = when (this) {
    is ServerMessageException -> serverMessage
    is java.util.concurrent.CancellationException -> "İşlem iptal edildi."
    is java.net.UnknownHostException,
    is java.net.ConnectException,
    is java.net.SocketTimeoutException,
    is javax.net.ssl.SSLException ->
        "Sunucuya ulaşılamıyor. Bağlantınızı ve sunucu adresini kontrol edin."
    else -> message ?: "Bilinmeyen bir hata oluştu."
}
