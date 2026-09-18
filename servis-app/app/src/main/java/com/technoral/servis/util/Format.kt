package com.technoral.servis.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private val TR = Locale("tr", "TR")

private val dateFmt = SimpleDateFormat("dd.MM.yyyy", TR)
private val dateTimeFmt = SimpleDateFormat("dd.MM.yyyy HH:mm", TR)
private val timeFmt = SimpleDateFormat("HH:mm", TR)
private val longDateFmt = SimpleDateFormat("d MMMM yyyy EEEE", TR)
private val fileStampFmt = SimpleDateFormat("yyyyMMdd_HHmmss", TR)

fun Long?.asDate(): String = if (this == null) "-" else dateFmt.format(this)
fun Long?.asDateTime(): String = if (this == null) "-" else dateTimeFmt.format(this)
fun Long?.asTime(): String = if (this == null) "-" else timeFmt.format(this)
fun Long?.asLongDate(): String = if (this == null) "-" else longDateFmt.format(this)
fun fileStamp(millis: Long = System.currentTimeMillis()): String = fileStampFmt.format(millis)

fun minutesAsDuration(minutes: Long?): String {
    if (minutes == null || minutes <= 0) return "-"
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h > 0 && m > 0 -> "$h sa $m dk"
        h > 0 -> "$h sa"
        else -> "$m dk"
    }
}

fun Double.asNumber(): String =
    if (this == this.toLong().toDouble()) this.toLong().toString()
    else String.format(TR, "%.2f", this)

/** Gün başlangıcına yuvarlar; tarih karşılaştırmalarında kullanılıyor. */
fun Long.startOfDay(): Long {
    val c = Calendar.getInstance()
    c.timeInMillis = this
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

fun startOfMonth(): Long {
    val c = Calendar.getInstance()
    c.set(Calendar.DAY_OF_MONTH, 1)
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

fun daysBetween(from: Long, to: Long): Long = (to.startOfDay() - from.startOfDay()) / 86_400_000L

/** Saat/dakikayı verilen güne yerleştirir. */
fun combineDateTime(dayMillis: Long, hour: Int, minute: Int): Long {
    val c = Calendar.getInstance()
    c.timeInMillis = dayMillis
    c.set(Calendar.HOUR_OF_DAY, hour)
    c.set(Calendar.MINUTE, minute)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

fun hourOf(millis: Long): Int {
    val c = Calendar.getInstance(); c.timeInMillis = millis
    return c.get(Calendar.HOUR_OF_DAY)
}

fun minuteOf(millis: Long): Int {
    val c = Calendar.getInstance(); c.timeInMillis = millis
    return c.get(Calendar.MINUTE)
}

/** 12.345,67 ₺ biçiminde para gösterimi. */
fun money(amount: Double, symbol: String = "₺", decimals: Int = 2): String {
    val text = String.format(TR, "%,.${decimals}f", amount)
    return "$text $symbol"
}

/** Büyük tutarları özet kartlarında kısaltır: 1,2 mn ₺ */
fun moneyShort(amount: Double, symbol: String = "₺"): String = when {
    kotlin.math.abs(amount) >= 1_000_000 -> String.format(TR, "%.1f mn %s", amount / 1_000_000, symbol)
    kotlin.math.abs(amount) >= 10_000 -> String.format(TR, "%.0f bin %s", amount / 1_000, symbol)
    else -> money(amount, symbol, 0)
}
