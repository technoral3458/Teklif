package com.technoral.servis.pdf

import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.technoral.servis.data.AppSettings
import com.technoral.servis.data.Customer
import com.technoral.servis.data.Expense
import com.technoral.servis.data.LedgerEntry
import com.technoral.servis.data.Machine
import com.technoral.servis.data.ServiceReport
import com.technoral.servis.util.asDate
import com.technoral.servis.util.asNumber
import com.technoral.servis.util.loadBitmap
import com.technoral.servis.util.money
import java.io.File

private val INK = Color.parseColor("#12212E")
private val MUTED = Color.parseColor("#5C6B7A")
private val ACCENT = Color.parseColor("#0F4C75")
private val SOFT = Color.parseColor("#EDF2F7")
private val BORDER = Color.parseColor("#D3DCE5")
private val GREEN = Color.parseColor("#1B7F4B")

/**
 * Servis masraflarının müşteriye verilecek dökümü: kalem listesi, toplamlar ve
 * eklenen fiş fotoğraflarının tamamı tek PDF'te birleştirilir.
 */
object ExpensePdf {

    fun build(
        target: File,
        report: ServiceReport,
        customer: Customer?,
        machine: Machine?,
        expenses: List<Expense>,
        charge: LedgerEntry?,
        settings: AppSettings,
    ): File {
        val b = PdfBuilder()
        val company = settings.company

        b.onNewPage = { canvas, pageNo ->
            canvas.drawRect(
                RectF(0f, 0f, 595f, 4f),
                PdfBuilder.textPaint(1f, false, ACCENT).apply { style = Paint.Style.FILL },
            )
            canvas.drawText(
                listOf(company.name, company.phone, company.email)
                    .filter { it.isNotBlank() }.joinToString("  •  "),
                36f, 812f, PdfBuilder.textPaint(7.5f, false, MUTED),
            )
            canvas.drawText(
                "Sayfa $pageNo",
                559f, 812f,
                PdfBuilder.textPaint(7.5f, false, MUTED, Paint.Align.RIGHT),
            )
        }
        b.pageTopInset = 8f

        drawHeader(b, report, customer, machine, company)
        drawTable(b, expenses)
        drawTotals(b, expenses, charge)
        drawReceipts(b, expenses)

        b.advance(10f)
        b.text(
            "Bu döküm, servis sırasında yapılan ve müşteriye yansıtılan harcamaların " +
                "belgeleriyle birlikte sunulması için hazırlanmıştır.",
            size = 7f, color = MUTED, align = Paint.Align.CENTER,
        )

        b.finish(target)
        return target
    }

    private fun drawHeader(
        b: PdfBuilder,
        report: ServiceReport,
        customer: Customer?,
        machine: Machine?,
        company: com.technoral.servis.data.CompanyInfo,
    ) {
        val top = b.y
        b.textAt(company.name.ifBlank { "Masraf Dökümü" }, b.left, top + 13f, 14f, bold = true, color = INK)
        val sub = listOf(company.address, company.phone, company.email).filter { it.isNotBlank() }
        sub.forEachIndexed { i, line ->
            b.textAt(line, b.left, top + 26f + i * 10f, 8f, color = MUTED)
        }

        b.textAt("MASRAF DÖKÜMÜ", b.right, top + 13f, 14f, bold = true, color = ACCENT, align = Paint.Align.RIGHT)
        b.textAt(report.reportNo, b.right, top + 27f, 10f, bold = true, color = INK, align = Paint.Align.RIGHT)
        b.textAt(report.serviceDate.asDate(), b.right, top + 39f, 9f, color = MUTED, align = Paint.Align.RIGHT)

        b.moveTo(top + maxOf(52f, 26f + sub.size * 10f))
        b.line(BORDER, 1f)
        b.advance(6f)

        val rows = listOfNotNull(
            customer?.name?.takeIf { it.isNotBlank() }?.let { "Müşteri" to it },
            machine?.displayName?.takeIf { it.isNotBlank() }?.let { "Makine" to it },
            machine?.serialNo?.takeIf { it.isNotBlank() }?.let { "Seri No" to it },
        )
        if (rows.isNotEmpty()) {
            val height = 12f + rows.size * 12f + 8f
            b.ensure(height + 8f)
            val cardTop = b.y
            b.rect(RectF(b.left, cardTop, b.right, cardTop + height), SOFT, radius = 6f)
            rows.forEachIndexed { i, (label, value) ->
                val lineY = cardTop + 20f + i * 12f
                b.textAt("$label:", b.left + 10f, lineY, 8f, color = MUTED)
                b.textAt(value, b.left + 78f, lineY, 8f, bold = true, color = INK)
            }
            b.moveTo(cardTop + height + 10f)
        }
    }

    private fun drawTable(b: PdfBuilder, expenses: List<Expense>) {
        sectionTitle(b, "MASRAF KALEMLERİ (${expenses.size})")

        val colNo = b.left + 6f
        val colCategory = b.left + 26f
        val colDate = b.left + 0.42f * b.contentWidth
        val colBillable = b.left + 0.58f * b.contentWidth
        val colAmount = b.right

        b.ensure(20f)
        var top = b.y
        b.rect(RectF(b.left, top, b.right, top + 16f), SOFT)
        b.textAt("#", colNo, top + 11f, 7.5f, bold = true, color = MUTED)
        b.textAt("KALEM", colCategory, top + 11f, 7.5f, bold = true, color = MUTED)
        b.textAt("TARİH", colDate, top + 11f, 7.5f, bold = true, color = MUTED)
        b.textAt("DURUM", colBillable, top + 11f, 7.5f, bold = true, color = MUTED)
        b.textAt("TUTAR", colAmount, top + 11f, 7.5f, bold = true, color = MUTED, align = Paint.Align.RIGHT)
        b.moveTo(top + 16f)

        expenses.forEachIndexed { index, expense ->
            val detail = listOfNotNull(
                expense.description.takeIf { it.isNotBlank() },
                expense.quantity.takeIf { it > 0 && expense.category.name == "YAKIT" }
                    ?.let { "${it.asNumber()} lt" },
            ).joinToString(" • ")
            val rowHeight = if (detail.isBlank()) 18f else 28f

            b.ensure(rowHeight)
            top = b.y
            b.textAt("${index + 1}", colNo, top + 12f, 8.5f, color = MUTED)
            b.textAt(expense.category.label, colCategory, top + 12f, 8.5f, bold = true, color = INK)
            b.textAt(expense.date.asDate(), colDate, top + 12f, 8.5f, color = INK)
            b.textAt(
                if (expense.billable) "Yansıtıldı" else "Yansıtılmadı",
                colBillable, top + 12f, 8f,
                color = if (expense.billable) GREEN else MUTED,
            )
            b.textAt(
                money(expense.amount, expense.currency.symbol),
                colAmount, top + 12f, 8.5f, bold = true, color = INK, align = Paint.Align.RIGHT,
            )
            if (detail.isNotBlank()) {
                b.textAt(detail, colCategory, top + 23f, 7.5f, color = MUTED)
            }
            if (expense.receiptPath != null) {
                b.textAt("fiş ekli", colNo, top + 23f, 6.5f, color = ACCENT)
            }
            b.moveTo(top + rowHeight)
            b.line(BORDER, 0.5f)
        }
        b.advance(4f)
    }

    private fun drawTotals(b: PdfBuilder, expenses: List<Expense>, charge: LedgerEntry?) {
        val total = expenses.sumOf { it.tryAmount }
        val billable = expenses.filter { it.billable }.sumOf { it.tryAmount }
        val serviceFee = charge?.tryAmount ?: 0.0

        val rows = buildList {
            add(Triple("Toplam masraf", money(total), INK))
            if (billable != total) {
                add(Triple("Müşteriye yansıtılan", money(billable), GREEN))
            }
            if (serviceFee > 0) {
                val feeLabel = charge?.let {
                    if (it.currency.code == "TRY") money(it.amount)
                    else "${money(it.amount, it.currency.symbol)} = ${money(it.tryAmount)}"
                } ?: money(serviceFee)
                add(Triple("Servis bedeli", feeLabel, INK))
            }
        }

        b.ensure(rows.size * 16f + 34f)
        var top = b.y
        rows.forEach { (label, value, color) ->
            b.textAt(label, b.left + 6f, top + 11f, 9f, color = MUTED)
            b.textAt(value, b.right, top + 11f, 9f, bold = true, color = color, align = Paint.Align.RIGHT)
            top += 16f
        }

        b.rect(RectF(b.left, top + 2f, b.right, top + 28f), ACCENT, radius = 6f)
        b.textAt("MÜŞTERİYE TOPLAM", b.left + 12f, top + 19f, 9f, bold = true, color = Color.WHITE)
        b.textAt(
            money(serviceFee + billable),
            b.right - 12f, top + 19f, 12f, bold = true, color = Color.WHITE, align = Paint.Align.RIGHT,
        )
        b.moveTo(top + 36f)
    }

    private fun drawReceipts(b: PdfBuilder, expenses: List<Expense>) {
        val withReceipt = expenses.filter { it.receiptPath != null && File(it.receiptPath).exists() }
        if (withReceipt.isEmpty()) {
            b.advance(6f)
            b.text(
                "Bu servise ait masraf fişi fotoğrafı eklenmemiştir.",
                size = 8f, color = MUTED,
            )
            return
        }

        b.newPage()
        sectionTitle(b, "MASRAF FİŞLERİ (${withReceipt.size})")

        val imageHeight = 320f
        withReceipt.forEach { expense ->
            val index = expenses.indexOf(expense) + 1
            b.ensure(imageHeight + 34f)
            val top = b.y

            b.rect(RectF(b.left, top, b.right, top + imageHeight), SOFT, radius = 6f)
            loadBitmap(expense.receiptPath!!, 1400)?.let { bitmap ->
                // Oranı koruyarak kutuya sığdır
                val scale = minOf(b.contentWidth / bitmap.width, imageHeight / bitmap.height)
                val width = bitmap.width * scale
                val height = bitmap.height * scale
                b.image(
                    bitmap,
                    b.left + (b.contentWidth - width) / 2,
                    top + (imageHeight - height) / 2,
                    width, height,
                )
            }

            val caption = listOfNotNull(
                "$index — ${expense.category.label}",
                expense.date.asDate(),
                expense.description.takeIf { it.isNotBlank() },
            ).joinToString("  •  ")
            b.textAt(caption, b.left, top + imageHeight + 13f, 8.5f, bold = true, color = INK)
            b.textAt(
                money(expense.amount, expense.currency.symbol) +
                    if (expense.currency.code != "TRY") "  (${money(expense.tryAmount)})" else "",
                b.right, top + imageHeight + 13f, 8.5f, bold = true, color = ACCENT, align = Paint.Align.RIGHT,
            )
            b.moveTo(top + imageHeight + 24f)
        }

        val missing = expenses.count { it.receiptPath == null }
        if (missing > 0) {
            b.advance(6f)
            b.text("$missing kalem için fiş fotoğrafı eklenmemiştir.", size = 7.5f, color = MUTED)
        }
    }

    private fun sectionTitle(b: PdfBuilder, title: String) {
        b.ensure(22f)
        val top = b.y
        b.rect(RectF(b.left, top + 2f, b.left + 3f, top + 13f), ACCENT, radius = 1.5f)
        b.textAt(title, b.left + 9f, top + 11f, 8.5f, bold = true, color = ACCENT)
        b.moveTo(top + 18f)
    }
}
