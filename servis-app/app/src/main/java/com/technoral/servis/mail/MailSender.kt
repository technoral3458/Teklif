package com.technoral.servis.mail

import com.technoral.servis.data.MailSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Properties
import javax.activation.DataHandler
import javax.activation.FileDataSource
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart

data class MailResult(val success: Boolean, val message: String)

/**
 * Kullanıcının Ayarlar > Mail ekranında girdiği SMTP sunucusu üzerinden mail gönderir.
 * Gönderim tamamen cihazdan yapılır; araya bir sunucu girmez.
 */
object MailSender {

    private fun session(settings: MailSettings): Session {
        val props = Properties().apply {
            put("mail.smtp.host", settings.host)
            put("mail.smtp.port", settings.port.toString())
            put("mail.smtp.connectiontimeout", "20000")
            put("mail.smtp.timeout", "30000")
            put("mail.smtp.writetimeout", "30000")

            when (settings.security.uppercase()) {
                "SSL" -> {
                    put("mail.smtp.socketFactory.port", settings.port.toString())
                    put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
                    put("mail.smtp.socketFactory.fallback", "false")
                    put("mail.smtp.ssl.enable", "true")
                }
                "STARTTLS" -> {
                    put("mail.smtp.starttls.enable", "true")
                    put("mail.smtp.starttls.required", "true")
                }
                else -> {
                    put("mail.smtp.ssl.enable", "false")
                }
            }

            val useAuth = settings.username.isNotBlank()
            put("mail.smtp.auth", useAuth.toString())
        }

        return if (settings.username.isNotBlank()) {
            Session.getInstance(props, object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication =
                    PasswordAuthentication(settings.username, settings.password)
            })
        } else {
            Session.getInstance(props)
        }
    }

    private fun addresses(raw: String): Array<InternetAddress> =
        raw.split(",", ";", "\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { InternetAddress(it) }
            .toTypedArray()

    suspend fun send(
        settings: MailSettings,
        to: String,
        cc: String = "",
        subject: String,
        bodyHtml: String,
        attachments: List<File> = emptyList(),
    ): MailResult = withContext(Dispatchers.IO) {
        if (settings.host.isBlank()) {
            return@withContext MailResult(false, "SMTP sunucusu tanımlı değil. Ayarlar > Mail Ayarları bölümünü doldurun.")
        }
        val recipients = addresses(to)
        if (recipients.isEmpty()) {
            return@withContext MailResult(false, "Alıcı e-posta adresi girilmedi.")
        }

        try {
            val session = session(settings)
            val message = MimeMessage(session)
            val from = settings.fromAddress.ifBlank { settings.username }
            message.setFrom(
                if (settings.fromName.isBlank()) InternetAddress(from)
                else InternetAddress(from, settings.fromName, "UTF-8")
            )
            message.setRecipients(Message.RecipientType.TO, recipients)
            val ccList = addresses(cc)
            if (ccList.isNotEmpty()) message.setRecipients(Message.RecipientType.CC, ccList)
            message.setSubject(subject, "UTF-8")
            message.sentDate = java.util.Date()

            val multipart = MimeMultipart("mixed")

            val htmlPart = MimeBodyPart()
            htmlPart.setContent(bodyHtml, "text/html; charset=UTF-8")
            multipart.addBodyPart(htmlPart)

            attachments.filter { it.exists() }.forEach { file ->
                val part = MimeBodyPart()
                val source = FileDataSource(file)
                part.dataHandler = DataHandler(source)
                part.fileName = file.name
                multipart.addBodyPart(part)
            }

            message.setContent(multipart)
            Transport.send(message)
            MailResult(true, "Mail gönderildi: ${recipients.joinToString(", ") { it.address }}")
        } catch (e: Exception) {
            MailResult(false, readableError(e))
        }
    }

    /** JavaMail hatalarını teknisyenin anlayacağı dile çevirir. */
    private fun readableError(e: Exception): String {
        val raw = (e.message ?: e.toString())
        val lower = raw.lowercase()
        val hint = when {
            lower.contains("authenticationfailed") || lower.contains("535") || lower.contains("username and password") ->
                "Kullanıcı adı veya parola kabul edilmedi. Gmail/Outlook kullanıyorsanız normal hesap parolanız yerine " +
                    "\"uygulama parolası\" üretip onu girmelisiniz."
            lower.contains("could not connect") || lower.contains("connection timed out") || lower.contains("timeout") ->
                "Sunucuya bağlanılamadı. Sunucu adı, port ve şifreleme (SSL/STARTTLS) ayarını kontrol edin."
            lower.contains("unknownhost") ->
                "SMTP sunucu adresi bulunamadı. Yazımını kontrol edin."
            lower.contains("ssl") || lower.contains("handshake") ->
                "Güvenli bağlantı kurulamadı. 465 için SSL, 587 için STARTTLS seçili olmalıdır."
            lower.contains("relay") || lower.contains("not permitted") ->
                "Sunucu bu adresten gönderime izin vermiyor. Gönderen adresi hesabınızla aynı olmalıdır."
            else -> "Gönderim başarısız."
        }
        return "$hint\n\nTeknik ayrıntı: $raw"
    }
}
