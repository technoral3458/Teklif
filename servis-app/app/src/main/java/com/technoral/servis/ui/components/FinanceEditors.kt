package com.technoral.servis.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.technoral.servis.data.AppSettings
import com.technoral.servis.data.Currency
import com.technoral.servis.data.Expense
import com.technoral.servis.data.ExpenseCategory
import com.technoral.servis.data.LedgerEntry
import com.technoral.servis.data.LedgerType
import com.technoral.servis.data.PaymentMethod
import com.technoral.servis.util.asNumber
import com.technoral.servis.util.money
import java.io.File

/**
 * Tutar + para birimi + kur girişi. TL seçiliyken kur alanı gizlenir; döviz
 * seçildiğinde kur otomatik çekilir, alanın yanındaki düğmeyle yenilenebilir.
 */
@Composable
fun MoneyInput(
    amount: String,
    currency: Currency,
    rate: String,
    onAmountChange: (String) -> Unit,
    onCurrencyChange: (Currency) -> Unit,
    onRateChange: (String) -> Unit,
    label: String = "Tutar",
    rateLoading: Boolean = false,
    rateHint: String = "",
    onRefreshRate: (() -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AppTextField(amount, onAmountChange, label, keyboardType = KeyboardType.Decimal)
        ChoiceChipRow(
            label = "Para birimi",
            options = Currency.entries.toList(),
            selected = currency,
            optionLabel = { "${it.symbol} ${it.code}" },
            onSelect = onCurrencyChange,
        )
        if (currency != Currency.TRY) {
            AppTextField(
                value = rate,
                onValueChange = onRateChange,
                label = "Kur (1 " + currency.code + " = ? ₺)",
                keyboardType = KeyboardType.Decimal,
                supportingText = rateSupportText(amount, rate, rateLoading, rateHint),
                trailingIcon = if (onRefreshRate != null) {
                    {
                        if (rateLoading) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            IconButton(onClick = onRefreshRate) {
                                Icon(Icons.Default.Refresh, "Kuru güncelle")
                            }
                        }
                    }
                } else null,
            )
            if (!rateLoading && rate.replace(',', '.').toDoubleOrNull().let { it == null || it <= 0 }) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(
                        "Kur alınamadı. Yenile düğmesine basın ya da kuru elle yazın — " +
                            "kursuz kayıtta TL karşılığı yanlış hesaplanır.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }
    }
}

private fun rateSupportText(amount: String, rate: String, loading: Boolean, hint: String): String {
    if (loading) return "Güncel kur alınıyor…"
    val r = rate.replace(',', '.').toDoubleOrNull()
    if (r == null || r <= 0) return "Kur zorunlu"
    val a = amount.replace(',', '.').toDoubleOrNull()
    val equivalent = if (a != null && a > 0) "TL karşılığı: ${money(a * r)}" else ""
    return listOf(equivalent, hint).filter { it.isNotBlank() }.joinToString("  •  ")
}

@Composable
fun LedgerEntryDialog(
    initial: LedgerEntry,
    settings: AppSettings,
    customerName: String,
    onSave: (LedgerEntry) -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    rateLoading: Boolean = false,
    onEnsureRate: ((Currency, (Double?) -> Unit) -> Unit)? = null,
) {
    var type by remember { mutableStateOf(initial.type) }
    var amount by remember { mutableStateOf(if (initial.amount == 0.0) "" else initial.amount.asNumber()) }
    var currency by remember { mutableStateOf(initial.currency) }
    var rate by remember {
        mutableStateOf(
            if (initial.rate != 1.0) initial.rate.asNumber()
            else settings.rateFor(initial.currency).takeIf { it > 0 }?.asNumber() ?: ""
        )
    }
    var description by remember { mutableStateOf(initial.description) }
    var documentNo by remember { mutableStateOf(initial.documentNo) }
    var date by remember { mutableStateOf(initial.date) }
    var dueDate by remember { mutableStateOf(initial.dueDate) }
    var promisedDate by remember { mutableStateOf(initial.promisedDate) }
    var method by remember { mutableStateOf(initial.paymentMethod) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (type == LedgerType.TAHSILAT) "Tahsilat" else "Cari Hareket") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (customerName.isNotBlank()) {
                    Text(
                        customerName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                ChoiceChipRow(
                    label = "Hareket tipi",
                    options = LedgerType.entries.toList(),
                    selected = type,
                    optionLabel = { it.label },
                    onSelect = { value ->
                        type = value
                        if (value == LedgerType.BORC) promisedDate = null
                    },
                )
                MoneyInput(
                    amount = amount,
                    currency = currency,
                    rate = rate,
                    onAmountChange = { amount = it },
                    onCurrencyChange = { value ->
                        currency = value
                        if (value == Currency.TRY) {
                            rate = "1"
                        } else {
                            val suggested = settings.rateFor(value)
                            rate = suggested.takeIf { it > 0 }?.asNumber() ?: ""
                            onEnsureRate?.invoke(value) { fetched ->
                                if (fetched != null && fetched > 0) rate = fetched.asNumber()
                            }
                        }
                    },
                    onRateChange = { rate = it },
                    rateLoading = rateLoading,
                    rateHint = rateHintOf(settings),
                    onRefreshRate = onEnsureRate?.let { ensure ->
                        {
                            ensure(currency) { fetched ->
                                if (fetched != null && fetched > 0) rate = fetched.asNumber()
                            }
                        }
                    },
                )
                DateField("Tarih", date, { value -> value?.let { date = it } })
                AppTextField(description, { description = it }, "Açıklama", singleLine = false, minLines = 2)
                AppTextField(documentNo, { documentNo = it }, "Belge / fatura no")

                if (type == LedgerType.BORC) {
                    DateField("Vade tarihi", dueDate, { dueDate = it }, clearable = true)
                    DateField(
                        "Müşterinin söz verdiği tarih", promisedDate, { promisedDate = it },
                        clearable = true,
                    )
                } else {
                    ChoiceChipRow(
                        label = "Ödeme şekli",
                        options = PaymentMethod.entries.toList(),
                        selected = method,
                        optionLabel = { it.label },
                        onSelect = { method = it },
                    )
                }
            }
        },
        confirmButton = {
            val parsedAmount = amount.replace(',', '.').toDoubleOrNull() ?: 0.0
            val parsedRate = if (currency == Currency.TRY) 1.0
            else rate.replace(',', '.').toDoubleOrNull() ?: 0.0
            TextButton(
                enabled = parsedAmount > 0 && parsedRate > 0,
                onClick = {
                    onSave(
                        initial.copy(
                            type = type,
                            amount = parsedAmount,
                            currency = currency,
                            rate = parsedRate,
                            description = description.trim(),
                            documentNo = documentNo.trim(),
                            date = date,
                            dueDate = if (type == LedgerType.BORC) dueDate else null,
                            promisedDate = if (type == LedgerType.BORC) promisedDate else null,
                            paymentMethod = method,
                        )
                    )
                },
            ) { Text("Kaydet") }
        },
        dismissButton = {
            if (onDelete != null) {
                TextButton(onClick = onDelete) {
                    Text("Sil", color = MaterialTheme.colorScheme.error)
                }
            } else {
                TextButton(onClick = onDismiss) { Text("Vazgeç") }
            }
        },
    )
}

@Composable
fun ExpenseDialog(
    initial: Expense,
    settings: AppSettings,
    onPickReceipt: ((onDone: (String?) -> Unit) -> Unit)? = null,
    onSave: (Expense) -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    rateLoading: Boolean = false,
    onEnsureRate: ((Currency, (Double?) -> Unit) -> Unit)? = null,
) {
    var category by remember { mutableStateOf(initial.category) }
    var amount by remember { mutableStateOf(if (initial.amount == 0.0) "" else initial.amount.asNumber()) }
    var currency by remember { mutableStateOf(initial.currency) }
    var rate by remember {
        mutableStateOf(
            if (initial.rate != 1.0) initial.rate.asNumber()
            else settings.rateFor(initial.currency).takeIf { it > 0 }?.asNumber() ?: ""
        )
    }
    var description by remember { mutableStateOf(initial.description) }
    var quantity by remember { mutableStateOf(if (initial.quantity == 0.0) "" else initial.quantity.asNumber()) }
    var date by remember { mutableStateOf(initial.date) }
    var billable by remember { mutableStateOf(initial.billable) }
    var receipt by remember { mutableStateOf(initial.receiptPath) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Masraf") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ChoiceChipRow(
                    label = "Kategori",
                    options = ExpenseCategory.entries.toList(),
                    selected = category,
                    optionLabel = { it.label },
                    onSelect = { category = it },
                )
                MoneyInput(
                    amount = amount,
                    currency = currency,
                    rate = rate,
                    onAmountChange = { amount = it },
                    onCurrencyChange = { value ->
                        currency = value
                        if (value == Currency.TRY) {
                            rate = "1"
                        } else {
                            val suggested = settings.rateFor(value)
                            rate = suggested.takeIf { it > 0 }?.asNumber() ?: ""
                            onEnsureRate?.invoke(value) { fetched ->
                                if (fetched != null && fetched > 0) rate = fetched.asNumber()
                            }
                        }
                    },
                    onRateChange = { rate = it },
                    rateLoading = rateLoading,
                    rateHint = rateHintOf(settings),
                    onRefreshRate = onEnsureRate?.let { ensure ->
                        {
                            ensure(currency) { fetched ->
                                if (fetched != null && fetched > 0) rate = fetched.asNumber()
                            }
                        }
                    },
                )
                if (category == ExpenseCategory.YAKIT) {
                    AppTextField(
                        quantity, { quantity = it }, "Litre",
                        keyboardType = KeyboardType.Decimal,
                        supportingText = "Aylık raporda toplam yakıt tüketimi olarak görünür",
                    )
                } else if (category == ExpenseCategory.KONAKLAMA) {
                    AppTextField(
                        quantity, { quantity = it }, "Gece sayısı",
                        keyboardType = KeyboardType.Decimal,
                    )
                }
                DateField("Tarih", date, { value -> value?.let { date = it } })
                AppTextField(description, { description = it }, "Açıklama", singleLine = false, minLines = 2)

                Row(
                    Modifier.fillMaxWidth().clickable { billable = !billable },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = billable, onCheckedChange = { billable = it })
                    Column {
                        Text("Müşteriye yansıtılacak", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Aylık raporda ayrıca gösterilir",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (onPickReceipt != null) {
                    receipt?.let { path ->
                        AsyncImage(
                            model = File(path),
                            contentDescription = "Fiş",
                            modifier = Modifier.fillMaxWidth().height(160.dp),
                            contentScale = ContentScale.Fit,
                        )
                    }
                    OutlinedButton(
                        onClick = { onPickReceipt { path -> if (path != null) receipt = path } },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Default.PhotoCamera, null, Modifier.size(18.dp))
                        Box(Modifier.width(8.dp))
                        Text(if (receipt == null) "Fiş fotoğrafı ekle" else "Fişi değiştir")
                    }
                }
            }
        },
        confirmButton = {
            val parsedAmount = amount.replace(',', '.').toDoubleOrNull() ?: 0.0
            val parsedRate = if (currency == Currency.TRY) 1.0
            else rate.replace(',', '.').toDoubleOrNull() ?: 0.0
            TextButton(
                enabled = parsedAmount > 0 && parsedRate > 0,
                onClick = {
                    onSave(
                        initial.copy(
                            category = category,
                            amount = parsedAmount,
                            currency = currency,
                            rate = parsedRate,
                            description = description.trim(),
                            quantity = quantity.replace(',', '.').toDoubleOrNull() ?: 0.0,
                            date = date,
                            billable = billable,
                            receiptPath = receipt,
                        )
                    )
                },
            ) { Text("Kaydet") }
        },
        dismissButton = {
            if (onDelete != null) {
                TextButton(onClick = onDelete) {
                    Text("Sil", color = MaterialTheme.colorScheme.error)
                }
            } else {
                TextButton(onClick = onDismiss) { Text("Vazgeç") }
            }
        },
    )
}

/** "TCMB döviz satış • 18.09.2026" biçiminde kur kaynağı bilgisi. */
fun rateHintOf(settings: AppSettings): String =
    listOf(settings.rateSource, settings.rateDateLabel)
        .filter { it.isNotBlank() }
        .joinToString(" • ")

/** Tutarı, işaretine göre renklendirerek gösterir. */
@Composable
fun MoneyText(
    amount: Double,
    symbol: String = "₺",
    positiveIsGood: Boolean = true,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleMedium,
) {
    val good = if (positiveIsGood) amount >= 0 else amount <= 0
    Text(
        money(amount, symbol),
        style = style,
        color = if (good) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
    )
}
