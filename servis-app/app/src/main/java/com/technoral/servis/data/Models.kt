package com.technoral.servis.data

import java.util.UUID

/** Uygulamadaki tüm kayıtlar bu arayüzü taşır; kimlik üretimi tek yerden yapılır. */
fun newId(): String = UUID.randomUUID().toString()

enum class ServiceType(val label: String) {
    ARIZA("Arıza"),
    PERIYODIK_BAKIM("Periyodik Bakım"),
    KURULUM("Kurulum"),
    DEVREYE_ALMA("Devreye Alma"),
    REVIZYON("Revizyon"),
    KESIF("Keşif"),
    EGITIM("Eğitim"),
    GARANTI("Garanti"),
}

enum class ServiceStatus(val label: String) {
    TASLAK("Taslak"),
    ACIK("Açık"),
    COZULDU("Çözüldü"),
    GECICI_COZUM("Geçici Çözüm"),
    PARCA_BEKLIYOR("Parça Bekliyor"),
    TEKRAR_ZIYARET("Tekrar Ziyaret Gerekli"),
}

enum class Priority(val label: String) {
    DUSUK("Düşük"),
    NORMAL("Normal"),
    YUKSEK("Yüksek"),
    KRITIK("Kritik / Duruş"),
}

/** Servis raporunun bölümleri — teknisyen hangi alanlarda çalıştıysa onları işaretler. */
enum class Department(val label: String) {
    MEKANIK("Mekanik"),
    ELEKTRIK("Elektrik"),
    ELEKTRONIK("Elektronik"),
    PNOMATIK("Pnömatik"),
    HIDROLIK("Hidrolik"),
    YAZILIM("Yazılım / PLC"),
    KALIBRASYON("Kalibrasyon"),
    OTOMASYON("Otomasyon"),
    TEMIZLIK("Temizlik / Yağlama"),
    DIGER("Diğer"),
}

enum class PhotoTag(val label: String) {
    ARIZA("Arıza"),
    ONCESI("İşlem Öncesi"),
    SONRASI("İşlem Sonrası"),
    PARCA("Parça"),
    ETIKET("Makine Etiketi"),
    DIGER("Diğer"),
}

enum class PartStatus(val label: String) {
    TAKILDI("Takıldı"),
    GEREKLI("Gerekli"),
    SIPARIS("Sipariş Edilecek"),
    TEKLIF("Teklif Verilecek"),
}

data class Customer(
    val id: String = newId(),
    val name: String = "",
    val contactName: String = "",
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val city: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

data class Machine(
    val id: String = newId(),
    val customerId: String = "",
    val name: String = "",
    val brand: String = "",
    val model: String = "",
    val serialNo: String = "",
    val year: String = "",
    val location: String = "",
    val installDate: Long? = null,
    val warrantyEnd: Long? = null,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
) {
    val displayName: String
        get() = listOf(brand, name.ifBlank { model }).filter { it.isNotBlank() }.joinToString(" ")
            .ifBlank { serialNo.ifBlank { "Makine" } }
}

data class DepartmentWork(
    val department: Department = Department.MEKANIK,
    val work: String = "",
)

data class ServicePhoto(
    val id: String = newId(),
    val path: String = "",
    val caption: String = "",
    val tag: PhotoTag = PhotoTag.ARIZA,
    val createdAt: Long = System.currentTimeMillis(),
)

data class SparePart(
    val id: String = newId(),
    val name: String = "",
    val code: String = "",
    val quantity: Double = 1.0,
    val unit: String = "adet",
    val status: PartStatus = PartStatus.TAKILDI,
    val note: String = "",
)

data class ServiceReport(
    val id: String = newId(),
    val reportNo: String = "",
    val customerId: String = "",
    val machineId: String = "",
    val type: ServiceType = ServiceType.ARIZA,
    val status: ServiceStatus = ServiceStatus.TASLAK,
    val priority: Priority = Priority.NORMAL,
    val serviceDate: Long = System.currentTimeMillis(),
    val startTime: Long? = null,
    val endTime: Long? = null,
    val travelKm: Double = 0.0,
    val departments: List<DepartmentWork> = emptyList(),
    val faultDescription: String = "",
    val faultCause: String = "",
    val workDone: String = "",
    val recommendations: String = "",
    val photos: List<ServicePhoto> = emptyList(),
    val parts: List<SparePart> = emptyList(),
    val technician: String = "",
    val customerRep: String = "",
    val signaturePath: String? = null,
    val nextMaintenance: Long? = null,
    val mailedTo: String = "",
    val mailedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    /** Çalışma süresi (dakika). Başlangıç/bitiş girilmemişse null. */
    val durationMinutes: Long?
        get() {
            val s = startTime ?: return null
            val e = endTime ?: return null
            if (e <= s) return null
            return (e - s) / 60000L
        }

    val isClosed: Boolean
        get() = status == ServiceStatus.COZULDU

    val needsFollowUp: Boolean
        get() = status == ServiceStatus.PARCA_BEKLIYOR ||
            status == ServiceStatus.TEKRAR_ZIYARET ||
            status == ServiceStatus.GECICI_COZUM
}

data class CompanyInfo(
    val name: String = "",
    val address: String = "",
    val phone: String = "",
    val email: String = "",
    val web: String = "",
    val taxInfo: String = "",
    val logoPath: String? = null,
)

data class MailSettings(
    val host: String = "",
    val port: Int = 587,
    val security: String = "STARTTLS", // NONE | STARTTLS | SSL
    val username: String = "",
    val password: String = "",
    val fromAddress: String = "",
    val fromName: String = "",
    val defaultTo: String = "",
    val defaultCc: String = "",
    val attachPhotos: Boolean = true,
    val subjectTemplate: String = "Servis Raporu {rapor_no} - {musteri}",
) {
    val isConfigured: Boolean
        get() = host.isNotBlank() && fromAddress.isNotBlank()
}

data class AppSettings(
    val company: CompanyInfo = CompanyInfo(),
    val mail: MailSettings = MailSettings(),
    val technicianName: String = "",
    val technicianPhone: String = "",
    val darkTheme: Boolean? = null, // null = sistem
    val reportPrefix: String = "SRV",
    val serverUrl: String = "",
    val setupDone: Boolean = false,
)
