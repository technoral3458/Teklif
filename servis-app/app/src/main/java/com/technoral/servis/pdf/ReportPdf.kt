package com.technoral.servis.pdf

import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.technoral.servis.data.AppSettings
import com.technoral.servis.data.Customer
import com.technoral.servis.data.Machine
import com.technoral.servis.data.ServiceReport
import com.technoral.servis.data.ServiceStatus
import com.technoral.servis.util.asDate
import com.technoral.servis.util.asNumber
import com.technoral.servis.util.asTime
import com.technoral.servis.util.loadBitmap
import com.technoral.servis.util.minutesAsDuration
import java.io.File

private val INK = Color.parseColor("#12212E")
private val MUTED = Color.parseColor("#5C6B7A")
private val ACCENT = Color.parseColor("#0F4C75")
private val SOFT = Color.parseColor("#EDF2F7")
private val BORDER = Color.parseColor("#D3DCE5")

private fun statusColor(status: ServiceStatus): Int = when (status) {
    ServiceStatus.COZULDU -> Color.parseColor("#1B7F4B")
    ServiceStatus.PARCA_BEKLIYOR -> Color.parseColor("#B26A00")
    ServiceStatus.TEKRAR_ZIYARET -> Color.parseColor("#B3261E")
    ServiceStatus.GECICI_COZUM -> Color.parseColor("#8A6D00")
    ServiceStatus.ACIK -> Color.parseColor("#0F4C75")
    ServiceStatus.TASLAK -> MUTED
}

/**
 * Servis raporunu A4 PDF'e döker. Çıktı; firma başlığı, müşteri/makine künyesi,
 * yapılan işlemler, fotoğraflar, yedek parça listesi ve imzalardan oluşur.
 */
object ReportPdf {

    fun build(
        target: File,
        report: ServiceReport,
        customer: Customer?,
        machine: Machine?,
        settings: AppSettings,
        chargeText: String? = null,
    ): File {
        val b = PdfBuilder()
        val company = settings.company

        b.onNewPage = { canvas, pageNo ->
            // Üst renk şeridi
            canvas.drawRect(
                RectF(0f, 0f, 595f, 4f),
                PdfBuilder.textPaint(1f, false, ACCENT).apply { style = Paint.Style.FILL },
            )
            // Alt bilgi
            val footer = PdfBuilder.textPaint(7.5f, false, MUTED, Paint.Align.LEFT)
            canvas.drawText(
                listOf(company.name, company.phone, company.email)
                    .filter { it.isNotBlank() }.joinToString("  •  "),
                36f, 812f, footer,
            )
            canvas.drawText(
                "Sayfa $pageNo",
                559f, 812f,
                PdfBuilder.textPaint(7.5f, false, MUTED, Paint.Align.RIGHT),
            )
        }
        b.pageTopInset = 8f

        drawHeader(b, report, company)
        drawSummaryStrip(b, report, machine)
        drawParties(b, customer, machine)
        drawServiceInfo(b, report, settings, chargeText)
        drawDepartments(b, report)
        drawNarrative(b, report)
        drawParts(b, report)
        drawPhotos(b, report)
        drawSignatures(b, report, settings)

        b.finish(target)
        return target
    }

    // ------------------------------------------------------------------ başlık

    private fun drawHeader(b: PdfBuilder, report: ServiceReport, company: com.technoral.servis.data.CompanyInfo) {
        val top = b.y
        val logo = company.logoPath?.let { loadBitmap(it, 320) }
        var textLeft = b.left

        if (logo != null) {
            val h = 42f
            val w = (logo.width.toFloat() / logo.height.toFloat()) * h
            val capped = w.coerceAtMost(130f)
            b.image(logo, b.left, top, capped, h)
            textLeft = b.left + capped + 12f
        }

        b.textAt(company.name.ifBlank { "Servis Raporu" }, textLeft, top + 13f, 14f, bold = true, color = INK)
        val sub = listOf(company.address, company.phone, company.email, company.web)
            .filter { it.isNotBlank() }
        sub.forEachIndexed { i, line ->
            b.textAt(line, textLeft, top + 26f + i * 10f, 8f, color = MUTED)
        }

        b.textAt("SERVİS RAPORU", b.right, top + 13f, 14f, bold = true, color = ACCENT, align = Paint.Align.RIGHT)
        b.textAt(report.reportNo, b.right, top + 27f, 10f, bold = true, color = INK, align = Paint.Align.RIGHT)
        b.textAt(report.serviceDate.asDate(), b.right, top + 39f, 9f, color = MUTED, align = Paint.Align.RIGHT)

        val headerHeight = maxOf(52f, 26f + sub.size * 10f)
        b.moveTo(top + headerHeight)
        b.line(BORDER, 1f)
        b.advance(6f)
    }

    /** Durum / tip / öncelik rozetleri. */
    private fun drawSummaryStrip(b: PdfBuilder, report: ServiceReport, machine: Machine?) {
        b.ensure(26f)
        val top = b.y
        var x = b.left
        val chips = listOf(
            Triple(report.type.label, ACCENT, true),
            Triple(report.status.label, statusColor(report.status), true),
            Triple("Öncelik: ${report.priority.label}", MUTED, false),
        ) + listOfNotNull(
            machine?.serialNo?.takeIf { it.isNotBlank() }?.let { Triple("Seri No: $it", MUTED, false) },
        )

        chips.forEach { (label, color, filled) ->
            val w = b.textWidth(label, 8.5f, true) + 16f
            val rect = RectF(x, top, x + w, top + 18f)
            if (filled) {
                b.rect(rect, color, radius = 9f)
                b.textAt(label, x + 8f, top + 12.5f, 8.5f, bold = true, color = Color.WHITE)
            } else {
                b.rect(rect, BORDER, radius = 9f, stroke = true)
                b.textAt(label, x + 8f, top + 12.5f, 8.5f, bold = false, color = color)
            }
            x += w + 6f
        }
        b.moveTo(top + 26f)
    }

    // ----------------------------------------------------------------- künye

    private fun drawParties(b: PdfBuilder, customer: Customer?, machine: Machine?) {
        val colGap = 12f
        val colWidth = (b.contentWidth - colGap) / 2

        val customerLines = listOf(
            "Firma" to (customer?.name ?: "-"),
            "Yetkili" to (customer?.contactName ?: "-"),
            "Telefon" to (customer?.phone ?: "-"),
            "E-posta" to (customer?.email ?: "-"),
            "Adres" to listOf(customer?.address.orEmpty(), customer?.city.orEmpty())
                .filter { it.isNotBlank() }.joinToString(" / ").ifBlank { "-" },
        )
        val machineLines = listOf(
            "Makine" to (machine?.displayName ?: "-"),
            "Marka / Model" to listOf(machine?.brand.orEmpty(), machine?.model.orEmpty())
                .filter { it.isNotBlank() }.joinToString(" / ").ifBlank { "-" },
            "Seri No" to (machine?.serialNo?.ifBlank { "-" } ?: "-"),
            "Konum / Hat" to (machine?.location?.ifBlank { "-" } ?: "-"),
            "Garanti Bitiş" to (machine?.warrantyEnd.asDate()),
        )

        val height = 18f + maxOf(customerLines.size, machineLines.size) * 12f + 8f
        b.ensure(height)
        val top = b.y

        drawCard(b, "MÜŞTERİ", customerLines, b.left, top, colWidth, height)
        drawCard(b, "MAKİNE", machineLines, b.left + colWidth + colGap, top, colWidth, height)

        b.moveTo(top + height + 10f)
    }

    private fun drawCard(
        b: PdfBuilder,
        title: String,
        lines: List<Pair<String, String>>,
        x: Float,
        top: Float,
        width: Float,
        height: Float,
    ) {
        b.rect(RectF(x, top, x + width, top + height), SOFT, radius = 6f)
        b.textAt(title, x + 10f, top + 13f, 8f, bold = true, color = ACCENT)
        lines.forEachIndexed { i, (label, value) ->
            val lineY = top + 27f + i * 12f
            b.textAt("$label:", x + 10f, lineY, 8f, color = MUTED)
            val labelWidth = 62f
            val available = width - 20f - labelWidth
            val shown = PdfBuilder.wrap(value, PdfBuilder.textPaint(8f, true, INK), available).firstOrNull() ?: value
            val suffix = if (PdfBuilder.wrap(value, PdfBuilder.textPaint(8f, true, INK), available).size > 1) "…" else ""
            b.textAt(shown + suffix, x + 10f + labelWidth, lineY, 8f, bold = true, color = INK)
        }
    }

    // ----------------------------------------------------------- servis bilgisi

    private fun drawServiceInfo(
        b: PdfBuilder,
        report: ServiceReport,
        settings: AppSettings,
        chargeText: String?,
    ) {
        sectionTitle(b, "SERVİS BİLGİLERİ")
        val items = listOf(
            "Servis Tarihi" to report.serviceDate.asDate(),
            "Başlangıç" to report.startTime.asTime(),
            "Bitiş" to report.endTime.asTime(),
            "Çalışma Süresi" to minutesAsDuration(report.durationMinutes),
            "Yol (km)" to report.travelKm.asNumber(),
            "Teknisyen" to report.technician.ifBlank { settings.technicianName.ifBlank { "-" } },
            "Sonraki Bakım" to report.nextMaintenance.asDate(),
            "Durum" to report.status.label,
        ) + listOfNotNull(chargeText?.let { "Servis Bedeli" to it })
        val cols = 4
        val cellW = b.contentWidth / cols
        val rows = (items.size + cols - 1) / cols
        val height = rows * 26f
        b.ensure(height + 4f)
        val top = b.y
        items.forEachIndexed { i, (label, value) ->
            val cx = b.left + (i % cols) * cellW
            val cy = top + (i / cols) * 26f
            b.textAt(label.uppercase(java.util.Locale("tr", "TR")), cx, cy + 8f, 6.5f, color = MUTED)
            b.textAt(value, cx, cy + 20f, 9f, bold = true, color = INK)
        }
        b.moveTo(top + height + 6f)
    }

    private fun drawDepartments(b: PdfBuilder, report: ServiceReport) {
        if (report.departments.isEmpty()) return
        sectionTitle(b, "ÇALIŞILAN BÖLÜMLER")
        report.departments.forEach { dw ->
            val bodyHeight = if (dw.work.isBlank()) 0f else b.measure(dw.work, 8.5f, width = b.contentWidth - 100f)
            b.ensure(maxOf(18f, bodyHeight + 6f))
            val top = b.y
            val label = dw.department.label
            val w = b.textWidth(label, 8f, true) + 14f
            b.rect(RectF(b.left, top, b.left + w, top + 15f), ACCENT, radius = 7.5f)
            b.textAt(label, b.left + 7f, top + 10.5f, 8f, bold = true, color = Color.WHITE)
            if (dw.work.isNotBlank()) {
                b.moveTo(top + 2f)
                b.text(dw.work, size = 8.5f, color = INK, x = b.left + w + 10f, width = b.contentWidth - w - 10f)
                if (b.y < top + 15f) b.moveTo(top + 15f)
            } else {
                b.moveTo(top + 15f)
            }
            b.advance(5f)
        }
        b.advance(2f)
    }

    private fun drawNarrative(b: PdfBuilder, report: ServiceReport) {
        val blocks = listOf(
            "ARIZA / TALEP TANIMI" to report.faultDescription,
            "ARIZA NEDENİ (KÖK NEDEN)" to report.faultCause,
            "YAPILAN İŞLEM / ÇÖZÜM" to report.workDone,
            "ÖNERİLER" to report.recommendations,
        ).filter { it.second.isNotBlank() }
        blocks.forEach { (title, body) ->
            sectionTitle(b, title)
            b.text(body, size = 9f, color = INK, lineGap = 4f)
            b.advance(8f)
        }
    }

    private fun drawParts(b: PdfBuilder, report: ServiceReport) {
        if (report.parts.isEmpty()) return
        sectionTitle(b, "KULLANILAN / GEREKEN YEDEK PARÇALAR")

        val colX = listOf(0f, 0.42f, 0.60f, 0.74f).map { b.left + it * b.contentWidth }
        b.ensure(20f)
        var top = b.y
        b.rect(RectF(b.left, top, b.right, top + 16f), SOFT)
        b.textAt("PARÇA", colX[0] + 6f, top + 11f, 7.5f, bold = true, color = MUTED)
        b.textAt("KOD", colX[1], top + 11f, 7.5f, bold = true, color = MUTED)
        b.textAt("MİKTAR", colX[2], top + 11f, 7.5f, bold = true, color = MUTED)
        b.textAt("DURUM", colX[3], top + 11f, 7.5f, bold = true, color = MUTED)
        b.moveTo(top + 16f)

        report.parts.forEach { part ->
            val nameH = b.measure(part.name, 8.5f, width = colX[1] - colX[0] - 10f)
            val rowH = maxOf(17f, nameH + 6f)
            b.ensure(rowH)
            top = b.y
            b.textAt(part.name, colX[0] + 6f, top + 11f, 8.5f, bold = true, color = INK)
            b.textAt(part.code.ifBlank { "-" }, colX[1], top + 11f, 8.5f, color = INK)
            b.textAt("${part.quantity.asNumber()} ${part.unit}", colX[2], top + 11f, 8.5f, color = INK)
            b.textAt(part.status.label, colX[3], top + 11f, 8.5f, color = INK)
            b.moveTo(top + rowH)
            if (part.note.isNotBlank()) {
                b.text("Not: ${part.note}", size = 7.5f, color = MUTED, x = b.left + 6f, width = b.contentWidth - 12f)
            }
            b.line(BORDER, 0.5f)
        }
        b.advance(6f)
    }

    private fun drawPhotos(b: PdfBuilder, report: ServiceReport) {
        val photos = report.photos.filter { File(it.path).exists() }
        if (photos.isEmpty()) return
        sectionTitle(b, "FOTOĞRAFLAR (${photos.size})")

        val gap = 10f
        val cellW = (b.contentWidth - gap) / 2
        val imgH = 118f

        photos.chunked(2).forEach { row ->
            val captionH = row.maxOf { p ->
                val text = listOf(p.tag.label, p.caption).filter { it.isNotBlank() }.joinToString(" — ")
                maxOf(11f, b.measure(text, 7.5f, width = cellW))
            }
            val rowH = imgH + captionH + 10f
            b.ensure(rowH)
            val top = b.y
            row.forEachIndexed { i, photo ->
                val x = b.left + i * (cellW + gap)
                val bmp = loadBitmap(photo.path, 900)
                b.rect(RectF(x, top, x + cellW, top + imgH), SOFT, radius = 4f)
                if (bmp != null) {
                    // Oranı koruyarak hücreye sığdır
                    val scale = minOf(cellW / bmp.width, imgH / bmp.height)
                    val w = bmp.width * scale
                    val h = bmp.height * scale
                    b.image(bmp, x + (cellW - w) / 2, top + (imgH - h) / 2, w, h)
                }
                val text = listOf(photo.tag.label, photo.caption).filter { it.isNotBlank() }.joinToString(" — ")
                PdfBuilder.wrap(text, PdfBuilder.textPaint(7.5f, false, MUTED), cellW)
                    .forEachIndexed { li, line ->
                        b.textAt(line, x, top + imgH + 9f + li * 9f, 7.5f, color = MUTED)
                    }
            }
            b.moveTo(top + rowH)
        }
        b.advance(4f)
    }

    private fun drawSignatures(b: PdfBuilder, report: ServiceReport, settings: AppSettings) {
        b.ensure(96f)
        b.advance(6f)
        val top = b.y
        val colW = (b.contentWidth - 20f) / 2

        b.rect(RectF(b.left, top, b.left + colW, top + 82f), BORDER, radius = 6f, stroke = true)
        b.textAt("SERVİS TEKNİSYENİ", b.left + 10f, top + 14f, 7f, bold = true, color = MUTED)
        b.textAt(
            report.technician.ifBlank { settings.technicianName.ifBlank { "-" } },
            b.left + 10f, top + 74f, 9f, bold = true, color = INK,
        )

        val rx = b.left + colW + 20f
        b.rect(RectF(rx, top, rx + colW, top + 82f), BORDER, radius = 6f, stroke = true)
        b.textAt("MÜŞTERİ YETKİLİSİ", rx + 10f, top + 14f, 7f, bold = true, color = MUTED)
        b.textAt(report.customerRep.ifBlank { "-" }, rx + 10f, top + 74f, 9f, bold = true, color = INK)

        report.signaturePath?.let { path ->
            loadBitmap(path, 700)?.let { bmp ->
                val maxW = colW - 20f
                val maxH = 44f
                val scale = minOf(maxW / bmp.width, maxH / bmp.height)
                b.image(bmp, rx + 10f, top + 20f, bmp.width * scale, bmp.height * scale)
            }
        }

        b.moveTo(top + 88f)
        b.text(
            "Bu rapor TeknoServis mobil uygulaması ile oluşturulmuştur. " +
                "Rapor No: ${report.reportNo} • Düzenleme: ${report.updatedAt.asDate()}",
            size = 7f, color = MUTED, align = Paint.Align.CENTER,
        )
    }

    private fun sectionTitle(b: PdfBuilder, title: String) {
        b.ensure(22f)
        val top = b.y
        b.rect(RectF(b.left, top + 2f, b.left + 3f, top + 13f), ACCENT, radius = 1.5f)
        b.textAt(title, b.left + 9f, top + 11f, 8.5f, bold = true, color = ACCENT)
        b.moveTo(top + 18f)
    }
}
