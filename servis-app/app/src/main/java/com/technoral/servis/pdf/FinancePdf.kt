package com.technoral.servis.pdf

import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.technoral.servis.data.AppSettings
import com.technoral.servis.data.Customer
import com.technoral.servis.data.MonthlySummary
import com.technoral.servis.data.OpenDebt
import com.technoral.servis.util.asDate
import com.technoral.servis.util.asNumber
import com.technoral.servis.util.money
import java.io.File

private val INK = Color.parseColor("#12212E")
private val MUTED = Color.parseColor("#5C6B7A")
private val ACCENT = Color.parseColor("#0F4C75")
private val SOFT = Color.parseColor("#EDF2F7")
private val BORDER = Color.parseColor("#D3DCE5")
private val GREEN = Color.parseColor("#1B7F4B")
private val RED = Color.parseColor("#B3261E")
private val AMBER = Color.parseColor("#B26A00")

/** Aylık gelir-gider ve alacak raporunun A4 PDF çıktısı. */
object FinancePdf {

    fun build(
        target: File,
        summary: MonthlySummary,
        settings: AppSettings,
        customerName: (String) -> String,
        overdue: List<Pair<String, OpenDebt>>,
        openBalances: List<Pair<Customer, Double>>,
    ): File {
        val b = PdfBuilder()
        val company = settings.company

        b.onNewPage = { canvas, pageNo ->
            canvas.drawRect(
                RectF(0f, 0f, 595f, 4f),
                PdfBuilder.textPaint(1f, false, ACCENT).apply { style = Paint.Style.FILL },
            )
            val footer = PdfBuilder.textPaint(7.5f, false, MUTED, Paint.Align.LEFT)
            canvas.drawText(
                listOf(company.name, company.phone).filter { it.isNotBlank() }.joinToString("  •  "),
                36f, 812f, footer,
            )
            canvas.drawText(
                "Sayfa $pageNo",
                559f, 812f,
                PdfBuilder.textPaint(7.5f, false, MUTED, Paint.Align.RIGHT),
            )
        }
        b.pageTopInset = 8f

        // ------------------------------------------------------------- başlık
        val top = b.y
        b.textAt(company.name.ifBlank { "Servis" }, b.left, top + 13f, 14f, bold = true, color = INK)
        b.textAt("Aylık Finans Raporu", b.left, top + 27f, 9f, color = MUTED)
        b.textAt(summary.label, b.right, top + 13f, 14f, bold = true, color = ACCENT, align = Paint.Align.RIGHT)
        b.textAt(
            "Düzenleme: ${System.currentTimeMillis().asDate()}",
            b.right, top + 27f, 8f, color = MUTED, align = Paint.Align.RIGHT,
        )
        b.moveTo(top + 40f)
        b.line(BORDER, 1f)
        b.advance(8f)

        // -------------------------------------------------------- özet kutular
        val cells = listOf(
            Triple("HAKEDİŞ", money(summary.incomeTry), ACCENT),
            Triple("TAHSİLAT", money(summary.collectedTry), GREEN),
            Triple("MASRAF", money(summary.expenseTry), AMBER),
            Triple("NET KÂR", money(summary.netTry), if (summary.netTry >= 0) GREEN else RED),
        )
        val gap = 8f
        val cellW = (b.contentWidth - gap * 3) / 4
        b.ensure(54f)
        var cellTop = b.y
        cells.forEachIndexed { i, (label, value, color) ->
            val x = b.left + i * (cellW + gap)
            b.rect(RectF(x, cellTop, x + cellW, cellTop + 46f), SOFT, radius = 6f)
            b.textAt(label, x + 10f, cellTop + 15f, 6.5f, color = MUTED)
            b.textAt(value, x + 10f, cellTop + 33f, 11f, bold = true, color = color)
        }
        b.moveTo(cellTop + 54f)

        val second = listOf(
            Triple("KASA AKIŞI", money(summary.cashFlowTry), if (summary.cashFlowTry >= 0) GREEN else RED),
            Triple("SERVİS SAYISI", summary.serviceCount.toString(), INK),
            Triple("YAKIT", if (summary.fuelLiters > 0) "${summary.fuelLiters.asNumber()} lt" else "-", INK),
            Triple("YANSITILACAK MASRAF", money(summary.billableExpenseTry), MUTED),
        )
        b.ensure(54f)
        cellTop = b.y
        second.forEachIndexed { i, (label, value, color) ->
            val x = b.left + i * (cellW + gap)
            b.rect(RectF(x, cellTop, x + cellW, cellTop + 46f), SOFT, radius = 6f)
            b.textAt(label, x + 10f, cellTop + 15f, 6.5f, color = MUTED)
            b.textAt(value, x + 10f, cellTop + 33f, 11f, bold = true, color = color)
        }
        b.moveTo(cellTop + 60f)

        // ------------------------------------------------------ masraf dağılımı
        if (summary.expenseByCategory.isNotEmpty()) {
            sectionTitle(b, "MASRAF DAĞILIMI")
            val maxValue = summary.expenseByCategory.maxOf { it.second }.coerceAtLeast(0.01)
            summary.expenseByCategory.forEach { (category, amount) ->
                b.ensure(20f)
                val rowTop = b.y
                b.textAt(category.label, b.left, rowTop + 9f, 8.5f, color = INK)
                val barLeft = b.left + 150f
                val barWidth = b.contentWidth - 150f - 80f
                b.rect(RectF(barLeft, rowTop + 2f, barLeft + barWidth, rowTop + 11f), SOFT, radius = 4.5f)
                val filled = (amount / maxValue * barWidth).toFloat().coerceAtLeast(2f)
                b.rect(RectF(barLeft, rowTop + 2f, barLeft + filled, rowTop + 11f), ACCENT, radius = 4.5f)
                b.textAt(money(amount), b.right, rowTop + 9f, 8.5f, bold = true, color = INK, align = Paint.Align.RIGHT)
                b.moveTo(rowTop + 17f)
            }
            b.advance(6f)
        }

        // ----------------------------------------------------- para birimleri
        if (summary.incomeByCurrency.size > 1) {
            sectionTitle(b, "PARA BİRİMİ BAZINDA HAKEDİŞ")
            summary.incomeByCurrency.forEach { (currency, amount) ->
                b.ensure(14f)
                val rowTop = b.y
                b.textAt(currency.label, b.left, rowTop + 9f, 8.5f, color = INK)
                b.textAt(
                    money(amount, currency.symbol),
                    b.right, rowTop + 9f, 8.5f, bold = true, color = INK, align = Paint.Align.RIGHT,
                )
                b.moveTo(rowTop + 14f)
            }
            b.advance(6f)
        }

        // --------------------------------------------------- müşteri kırılımı
        if (summary.topCustomers.isNotEmpty()) {
            sectionTitle(b, "MÜŞTERİ BAZINDA")
            tableHeader(b, listOf("MÜŞTERİ" to 0f, "HAKEDİŞ" to 0.55f, "TAHSİLAT" to 0.78f))
            summary.topCustomers.forEach { (customerId, income, collected) ->
                b.ensure(17f)
                val rowTop = b.y
                b.textAt(customerName(customerId), b.left + 6f, rowTop + 11f, 8.5f, bold = true, color = INK)
                b.textAt(
                    money(income), b.left + 0.55f * b.contentWidth + 60f, rowTop + 11f, 8.5f,
                    color = INK, align = Paint.Align.RIGHT,
                )
                b.textAt(
                    money(collected), b.right, rowTop + 11f, 8.5f,
                    color = GREEN, align = Paint.Align.RIGHT,
                )
                b.moveTo(rowTop + 17f)
                b.line(BORDER, 0.5f)
            }
            b.advance(6f)
        }

        // -------------------------------------------------------- açık bakiye
        val positives = openBalances.filter { it.second > 0.005 }
        if (positives.isNotEmpty()) {
            sectionTitle(b, "AÇIK ALACAKLAR (TÜM DÖNEMLER)")
            positives.sortedByDescending { it.second }.forEach { (customer, balance) ->
                b.ensure(16f)
                val rowTop = b.y
                b.textAt(customer.name, b.left + 6f, rowTop + 10f, 8.5f, color = INK)
                b.textAt(
                    money(balance), b.right, rowTop + 10f, 8.5f,
                    bold = true, color = AMBER, align = Paint.Align.RIGHT,
                )
                b.moveTo(rowTop + 15f)
                b.line(BORDER, 0.5f)
            }
            b.ensure(18f)
            val totalTop = b.y
            b.textAt("TOPLAM", b.left + 6f, totalTop + 11f, 9f, bold = true, color = INK)
            b.textAt(
                money(positives.sumOf { it.second }), b.right, totalTop + 11f, 9f,
                bold = true, color = INK, align = Paint.Align.RIGHT,
            )
            b.moveTo(totalTop + 18f)
            b.advance(6f)
        }

        // ------------------------------------------------------ vadesi geçen
        if (overdue.isNotEmpty()) {
            sectionTitle(b, "ÖDEMESİ GECİKEN ALACAKLAR")
            overdue.forEach { (customerId, debt) ->
                b.ensure(24f)
                val rowTop = b.y
                b.rect(RectF(b.left, rowTop, b.left + 3f, rowTop + 20f), RED, radius = 1.5f)
                b.textAt(customerName(customerId), b.left + 10f, rowTop + 9f, 8.5f, bold = true, color = INK)
                val deadline = debt.entry.promisedDate ?: debt.entry.dueDate
                b.textAt(
                    (if (debt.brokenPromise) "Söz verilen tarih: " else "Vade: ") +
                        deadline.asDate() + "  •  ${debt.daysLate} gün gecikme",
                    b.left + 10f, rowTop + 19f, 7.5f, color = RED,
                )
                b.textAt(
                    money(debt.openTry), b.right, rowTop + 12f, 9f,
                    bold = true, color = RED, align = Paint.Align.RIGHT,
                )
                b.moveTo(rowTop + 24f)
                b.line(BORDER, 0.5f)
            }
            b.ensure(18f)
            val totalTop = b.y
            b.textAt("GECİKEN TOPLAM", b.left + 6f, totalTop + 11f, 9f, bold = true, color = RED)
            b.textAt(
                money(overdue.sumOf { it.second.openTry }), b.right, totalTop + 11f, 9f,
                bold = true, color = RED, align = Paint.Align.RIGHT,
            )
            b.moveTo(totalTop + 18f)
        }

        b.advance(10f)
        b.text(
            "Tutarlar, hareketin yapıldığı günkü kur ile TL'ye çevrilerek toplanmıştır. " +
                "Bu rapor TeknoServis mobil uygulaması ile oluşturulmuştur.",
            size = 7f, color = MUTED, align = Paint.Align.CENTER,
        )

        b.finish(target)
        return target
    }

    private fun tableHeader(b: PdfBuilder, columns: List<Pair<String, Float>>) {
        b.ensure(20f)
        val top = b.y
        b.rect(RectF(b.left, top, b.right, top + 16f), SOFT)
        columns.forEach { (label, fraction) ->
            b.textAt(label, b.left + fraction * b.contentWidth + 6f, top + 11f, 7.5f, bold = true, color = MUTED)
        }
        b.moveTo(top + 16f)
    }

    private fun sectionTitle(b: PdfBuilder, title: String) {
        b.ensure(22f)
        val top = b.y
        b.rect(RectF(b.left, top + 2f, b.left + 3f, top + 13f), ACCENT, radius = 1.5f)
        b.textAt(title, b.left + 9f, top + 11f, 8.5f, bold = true, color = ACCENT)
        b.moveTo(top + 18f)
    }
}
