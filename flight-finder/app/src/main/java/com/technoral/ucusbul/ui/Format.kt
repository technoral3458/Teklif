package com.technoral.ucusbul.ui

import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val trLocale = Locale("tr", "TR")

fun currencySymbol(code: String): String = when (code.uppercase()) {
    "TRY" -> "₺"
    "USD" -> "$"
    "EUR" -> "€"
    "GBP" -> "£"
    "CNY" -> "¥"
    else -> code.uppercase()
}

fun formatPrice(value: Double, currency: String): String {
    val nf = NumberFormat.getNumberInstance(trLocale).apply {
        maximumFractionDigits = if (value >= 100) 0 else 2
        minimumFractionDigits = 0
    }
    return "${nf.format(value)} ${currencySymbol(currency)}"
}

private val dayFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy, EEEE", trLocale)
private val shortFormatter = DateTimeFormatter.ofPattern("d MMM", trLocale)

fun formatDateLong(iso: String): String = runCatching {
    LocalDate.parse(iso).format(dayFormatter)
}.getOrDefault(iso)

fun formatDateShort(iso: String): String = runCatching {
    LocalDate.parse(iso).format(shortFormatter)
}.getOrDefault(iso)

/** "2026-09-01T21:30:00" -> "21:30" */
fun timeOf(isoDateTime: String): String {
    val t = isoDateTime.indexOf('T')
    if (t < 0 || isoDateTime.length < t + 6) return ""
    return isoDateTime.substring(t + 1, t + 6)
}

fun stopsLabel(stops: Int): String = when (stops) {
    0 -> "Aktarmasız"
    1 -> "1 aktarma"
    else -> "$stops aktarma"
}
