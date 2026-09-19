package com.technoral.servis.mail

import com.technoral.servis.data.AppSettings
import com.technoral.servis.data.Customer
import com.technoral.servis.data.Machine
import com.technoral.servis.data.MonthlySummary
import com.technoral.servis.data.PartStatus
import com.technoral.servis.data.ServiceReport
import com.technoral.servis.util.asDate
import com.technoral.servis.util.asNumber
import com.technoral.servis.util.asTime
import com.technoral.servis.util.minutesAsDuration
import com.technoral.servis.util.money

/** Mail gövdesi ve konusu — rapor özetini PDF'i açmadan da okunur kılar. */
object MailTemplates {

    fun subject(settings: AppSettings, report: ServiceReport, customer: Customer?, machine: Machine?): String =
        settings.mail.subjectTemplate
            .replace("{rapor_no}", report.reportNo)
            .replace("{musteri}", customer?.name ?: "-")
            .replace("{makine}", machine?.displayName ?: "-")
            .replace("{tarih}", report.serviceDate.asDate())
            .replace("{durum}", report.status.label)
            .ifBlank { "Servis Raporu ${report.reportNo}" }

    fun body(settings: AppSettings, report: ServiceReport, customer: Customer?, machine: Machine?): String {
        val company = settings.company
        val rows = buildString {
            row("Rapor No", report.reportNo)
            row("Servis Tarihi", report.serviceDate.asDate())
            row("Servis Tipi", report.type.label)
            row("Durum", report.status.label)
            row("Öncelik", report.priority.label)
            row("Müşteri", customer?.name ?: "-")
            row("Makine", machine?.displayName ?: "-")
            row("Seri No", machine?.serialNo?.ifBlank { "-" } ?: "-")
            row("Çalışma Saatleri", "${report.startTime.asTime()} - ${report.endTime.asTime()}")
            row("Çalışma Süresi", minutesAsDuration(report.durationMinutes))
            row("Teknisyen", report.technician.ifBlank { settings.technicianName })
            if (report.nextMaintenance != null) row("Sonraki Bakım", report.nextMaintenance.asDate())
        }

        val departments = report.departments.joinToString("") { dw ->
            "<li><b>${esc(dw.department.label)}</b>" +
                (if (dw.work.isBlank()) "" else ": ${esc(dw.work)}") + "</li>"
        }

        val pendingParts = report.parts.filter { it.status != PartStatus.TAKILDI }
        val partsHtml = if (report.parts.isEmpty()) "" else buildString {
            append("<h3 style=\"$H3\">Yedek Parçalar</h3><ul style=\"$UL\">")
            report.parts.forEach {
                append(
                    "<li>${esc(it.name)}" +
                        (if (it.code.isBlank()) "" else " <span style=\"color:#64748b\">(${esc(it.code)})</span>") +
                        " — ${it.quantity.asNumber()} ${esc(it.unit)} • <b>${esc(it.status.label)}</b></li>"
                )
            }
            append("</ul>")
            if (pendingParts.isNotEmpty()) {
                append(
                    "<p style=\"$WARN\">⚠ ${pendingParts.size} kalem parça temin edilmeyi bekliyor.</p>"
                )
            }
        }

        fun block(title: String, text: String): String =
            if (text.isBlank()) "" else
                "<h3 style=\"$H3\">${esc(title)}</h3><p style=\"$P\">${esc(text).replace("\n", "<br>")}</p>"

        return """
<!DOCTYPE html>
<html lang="tr"><head><meta charset="utf-8"></head>
<body style="margin:0;padding:24px;background:#f1f5f9;font-family:Segoe UI,Roboto,Helvetica,Arial,sans-serif;color:#0f172a">
  <div style="max-width:640px;margin:0 auto;background:#ffffff;border-radius:14px;overflow:hidden;border:1px solid #e2e8f0">
    <div style="background:#0f4c75;padding:20px 24px;color:#ffffff">
      <div style="font-size:18px;font-weight:700">${esc(company.name.ifBlank { "Servis Raporu" })}</div>
      <div style="font-size:13px;opacity:.85;margin-top:4px">Servis Raporu • ${esc(report.reportNo)}</div>
    </div>
    <div style="padding:24px">
      <p style="$P">Merhaba,</p>
      <p style="$P">
        ${esc(customer?.name ?: "")} için gerçekleştirilen servis çalışmasının raporu aşağıdadır.
        Ayrıntılı rapor, fotoğraflar ve imzalar ekteki PDF dosyasındadır.
      </p>
      <table style="width:100%;border-collapse:collapse;margin:18px 0;font-size:14px">$rows</table>
      ${if (departments.isBlank()) "" else "<h3 style=\"$H3\">Çalışılan Bölümler</h3><ul style=\"$UL\">$departments</ul>"}
      ${block("Arıza / Talep Tanımı", report.faultDescription)}
      ${block("Arıza Nedeni", report.faultCause)}
      ${block("Yapılan İşlem / Çözüm", report.workDone)}
      ${block("Öneriler", report.recommendations)}
      $partsHtml
      <p style="$P;margin-top:24px">İyi çalışmalar dileriz.</p>
      <div style="border-top:1px solid #e2e8f0;margin-top:20px;padding-top:14px;font-size:12px;color:#64748b">
        ${esc(company.name)}<br>
        ${esc(listOf(company.address, company.phone, company.email, company.web).filter { it.isNotBlank() }.joinToString(" • "))}
      </div>
    </div>
  </div>
</body></html>
        """.trimIndent()
    }

    fun testBody(settings: AppSettings): String = """
<!DOCTYPE html><html lang="tr"><head><meta charset="utf-8"></head>
<body style="font-family:Segoe UI,Roboto,Arial,sans-serif;padding:24px;color:#0f172a">
  <h2 style="color:#0f4c75;margin:0 0 12px">Mail ayarları çalışıyor ✔</h2>
  <p style="$P">Bu bir test mesajıdır. Deli Kadir App uygulamasındaki SMTP ayarlarınız doğru yapılandırılmıştır.</p>
  <p style="$P"><b>Sunucu:</b> ${esc(settings.mail.host)}:${settings.mail.port} (${esc(settings.mail.security)})<br>
  <b>Gönderen:</b> ${esc(settings.mail.fromAddress)}</p>
</body></html>
    """.trimIndent()

    /** Aylık finans raporu maili. */
    fun financeBody(settings: AppSettings, summary: MonthlySummary, note: String): String {
        val company = settings.company
        val rows = buildString {
            row("Dönem", summary.label)
            row("Hakediş", money(summary.incomeTry))
            row("Tahsilat", money(summary.collectedTry))
            row("Masraf", money(summary.expenseTry))
            row("Net kâr (hakediş - masraf)", money(summary.netTry))
            row("Kasa akışı (tahsilat - masraf)", money(summary.cashFlowTry))
            row("Servis sayısı", summary.serviceCount.toString())
            if (summary.fuelLiters > 0) row("Yakıt", "${summary.fuelLiters.asNumber()} litre")
        }
        val expenses = summary.expenseByCategory.joinToString("") { (category, amount) ->
            "<li>${esc(category.label)}: <b>${esc(money(amount))}</b></li>"
        }
        val noteHtml = if (note.isBlank()) "" else
            "<div style=\"margin:0 0 16px;padding:12px 14px;background:#f1f5f9;border-left:3px solid #0f4c75;" +
                "font-size:14px;line-height:1.6;color:#334155\">${esc(note).replace("\n", "<br>")}</div>"

        return """
<!DOCTYPE html>
<html lang="tr"><head><meta charset="utf-8"></head>
<body style="margin:0;padding:24px;background:#f1f5f9;font-family:Segoe UI,Roboto,Helvetica,Arial,sans-serif;color:#0f172a">
  <div style="max-width:640px;margin:0 auto;background:#ffffff;border-radius:14px;overflow:hidden;border:1px solid #e2e8f0">
    <div style="background:#0f4c75;padding:20px 24px;color:#ffffff">
      <div style="font-size:18px;font-weight:700">${esc(company.name.ifBlank { "Finans Raporu" })}</div>
      <div style="font-size:13px;opacity:.85;margin-top:4px">${esc(summary.label)} • Aylık Finans Raporu</div>
    </div>
    <div style="padding:24px">
      $noteHtml
      <table style="width:100%;border-collapse:collapse;margin:0 0 18px;font-size:14px">$rows</table>
      ${if (expenses.isBlank()) "" else "<h3 style=\"$H3\">Masraf Dağılımı</h3><ul style=\"$UL\">$expenses</ul>"}
      <p style="$P">Ayrıntılı döküm ve açık alacak listesi ekteki PDF dosyasındadır.</p>
    </div>
  </div>
</body></html>
        """.trimIndent()
    }

    private const val P = "margin:0 0 12px;font-size:14px;line-height:1.6;color:#334155"
    private const val H3 = "margin:20px 0 8px;font-size:14px;color:#0f4c75;text-transform:uppercase;letter-spacing:.4px"
    private const val UL = "margin:0 0 12px;padding-left:20px;font-size:14px;line-height:1.7;color:#334155"
    private const val WARN = "margin:8px 0;padding:10px 12px;background:#fff7ed;border-left:3px solid #f59e0b;font-size:13px;color:#92400e"

    private fun StringBuilder.row(label: String, value: String) {
        if (value.isBlank()) return
        append(
            "<tr>" +
                "<td style=\"padding:7px 10px;background:#f8fafc;border:1px solid #e2e8f0;color:#64748b;width:42%\">${esc(label)}</td>" +
                "<td style=\"padding:7px 10px;border:1px solid #e2e8f0;font-weight:600\">${esc(value)}</td>" +
                "</tr>"
        )
    }

    private fun esc(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
