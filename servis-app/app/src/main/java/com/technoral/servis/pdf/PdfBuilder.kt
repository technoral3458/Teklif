package com.technoral.servis.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream

/**
 * A4 sayfalar üzerine yukarıdan aşağı akan basit bir yerleşim motoru.
 * Sayfa sonuna gelindiğinde otomatik olarak yeni sayfa açar.
 */
class PdfBuilder(
    private val pageWidth: Int = 595,
    private val pageHeight: Int = 842,
    private val margin: Float = 36f,
) {
    val document = PdfDocument()
    private var page: PdfDocument.Page? = null
    private var pageNumber = 0
    private var cursor = margin

    /** Her yeni sayfada çalışır (üst bant / sayfa numarası gibi tekrar eden ögeler için). */
    var onNewPage: ((Canvas, Int) -> Unit)? = null

    /** onNewPage'in çizdiği üst bandın yüksekliği; içerik bu kadar aşağıdan başlar. */
    var pageTopInset: Float = 0f

    val contentWidth: Float get() = pageWidth - margin * 2
    val left: Float get() = margin
    val right: Float get() = pageWidth - margin
    val y: Float get() = cursor
    val canvas: Canvas get() = requirePage().canvas
    val pages: Int get() = pageNumber

    private val bottomLimit: Float get() = pageHeight - margin - 18f

    private fun requirePage(): PdfDocument.Page {
        page?.let { return it }
        pageNumber += 1
        val info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        val p = document.startPage(info)
        page = p
        cursor = margin
        onNewPage?.invoke(p.canvas, pageNumber)
        cursor = margin + pageTopInset
        return p
    }

    fun newPage() {
        page?.let { document.finishPage(it) }
        page = null
        requirePage()
    }

    /** İstenen yükseklik sayfaya sığmıyorsa yeni sayfaya geçer. */
    fun ensure(height: Float) {
        requirePage()
        if (cursor + height > bottomLimit) newPage()
    }

    fun advance(dy: Float) {
        cursor += dy
    }

    fun moveTo(value: Float) {
        cursor = value
    }

    fun finish(file: File) {
        page?.let { document.finishPage(it) }
        page = null
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
    }

    // ------------------------------------------------------------- çizim

    fun text(
        value: String,
        size: Float = 9f,
        bold: Boolean = false,
        color: Int = Color.BLACK,
        x: Float = left,
        width: Float = contentWidth,
        lineGap: Float = 3f,
        align: Paint.Align = Paint.Align.LEFT,
    ): Float {
        if (value.isBlank()) return 0f
        val paint = textPaint(size, bold, color, align)
        val lines = wrap(value, paint, width)
        val lineHeight = size + lineGap
        var drawn = 0f
        lines.forEach { line ->
            ensure(lineHeight)
            val drawX = when (align) {
                Paint.Align.CENTER -> x + width / 2
                Paint.Align.RIGHT -> x + width
                else -> x
            }
            canvas.drawText(line, drawX, cursor + size, paint)
            advance(lineHeight)
            drawn += lineHeight
        }
        return drawn
    }

    /** İmleci ilerletmeden, verilen noktaya tek satır yazar. */
    fun textAt(
        value: String,
        x: Float,
        baselineY: Float,
        size: Float = 9f,
        bold: Boolean = false,
        color: Int = Color.BLACK,
        align: Paint.Align = Paint.Align.LEFT,
    ) {
        canvas.drawText(value, x, baselineY, textPaint(size, bold, color, align))
    }

    fun rect(rectF: RectF, color: Int, radius: Float = 0f, stroke: Boolean = false, strokeWidth: Float = 1f) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = if (stroke) Paint.Style.STROKE else Paint.Style.FILL
            this.strokeWidth = strokeWidth
        }
        if (radius > 0f) canvas.drawRoundRect(rectF, radius, radius, paint)
        else canvas.drawRect(rectF, paint)
    }

    fun line(color: Int = Color.parseColor("#D8DEE5"), thickness: Float = 0.8f) {
        ensure(thickness + 4f)
        val paint = Paint().apply {
            this.color = color
            strokeWidth = thickness
        }
        canvas.drawLine(left, cursor, right, cursor, paint)
        advance(thickness + 4f)
    }

    fun image(bitmap: Bitmap, x: Float, top: Float, width: Float, height: Float) {
        val dst = RectF(x, top, x + width, top + height)
        canvas.drawBitmap(bitmap, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
    }

    fun measure(value: String, size: Float, bold: Boolean = false, width: Float = contentWidth): Float {
        if (value.isBlank()) return 0f
        return wrap(value, textPaint(size, bold, Color.BLACK, Paint.Align.LEFT), width).size * (size + 3f)
    }

    fun textWidth(value: String, size: Float, bold: Boolean = false): Float =
        textPaint(size, bold, Color.BLACK, Paint.Align.LEFT).measureText(value)

    companion object {
        fun textPaint(size: Float, bold: Boolean, color: Int, align: Paint.Align = Paint.Align.LEFT): Paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                textSize = size
                textAlign = align
                typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
            }

        /** Kelime bazlı satır kırma; tek kelime sığmazsa harf bazına düşer. */
        fun wrap(value: String, paint: Paint, width: Float): List<String> {
            val result = mutableListOf<String>()
            value.split("\n").forEach { paragraph ->
                if (paragraph.isBlank()) {
                    result.add("")
                    return@forEach
                }
                var current = StringBuilder()
                paragraph.split(" ").forEach { word ->
                    val candidate = if (current.isEmpty()) word else "$current $word"
                    if (paint.measureText(candidate) <= width) {
                        current = StringBuilder(candidate)
                    } else {
                        if (current.isNotEmpty()) result.add(current.toString())
                        if (paint.measureText(word) <= width) {
                            current = StringBuilder(word)
                        } else {
                            var chunk = StringBuilder()
                            word.forEach { ch ->
                                if (paint.measureText(chunk.toString() + ch) <= width) {
                                    chunk.append(ch)
                                } else {
                                    result.add(chunk.toString())
                                    chunk = StringBuilder().append(ch)
                                }
                            }
                            current = chunk
                        }
                    }
                }
                if (current.isNotEmpty()) result.add(current.toString())
            }
            return result.ifEmpty { listOf("") }
        }

        fun textHeight(paint: Paint): Float {
            val bounds = Rect()
            paint.getTextBounds("Ağ", 0, 2, bounds)
            return bounds.height().toFloat()
        }
    }
}
