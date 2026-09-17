package com.technoral.servis.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.technoral.servis.data.Customer
import com.technoral.servis.data.Machine
import com.technoral.servis.data.PartStatus
import com.technoral.servis.data.SparePart

@Composable
fun CustomerEditorDialog(
    initial: Customer,
    onSave: (Customer) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var contact by remember { mutableStateOf(initial.contactName) }
    var phone by remember { mutableStateOf(initial.phone) }
    var email by remember { mutableStateOf(initial.email) }
    var address by remember { mutableStateOf(initial.address) }
    var city by remember { mutableStateOf(initial.city) }
    var notes by remember { mutableStateOf(initial.notes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.name.isBlank()) "Yeni Müşteri" else "Müşteriyi Düzenle") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AppTextField(name, { name = it }, "Firma adı *")
                AppTextField(contact, { contact = it }, "Yetkili kişi")
                AppTextField(phone, { phone = it }, "Telefon", keyboardType = KeyboardType.Phone)
                AppTextField(
                    email, { email = it }, "E-posta",
                    keyboardType = KeyboardType.Email,
                    supportingText = "Servis raporu maili öncelikle bu adrese gider",
                )
                AppTextField(address, { address = it }, "Adres", singleLine = false, minLines = 2)
                AppTextField(city, { city = it }, "Şehir")
                AppTextField(notes, { notes = it }, "Not", singleLine = false, minLines = 2)
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        initial.copy(
                            name = name.trim(),
                            contactName = contact.trim(),
                            phone = phone.trim(),
                            email = email.trim(),
                            address = address.trim(),
                            city = city.trim(),
                            notes = notes.trim(),
                        )
                    )
                },
            ) { Text("Kaydet") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}

@Composable
fun MachineEditorDialog(
    initial: Machine,
    customers: List<Customer>,
    onSave: (Machine) -> Unit,
    onDismiss: () -> Unit,
) {
    var customerId by remember { mutableStateOf(initial.customerId) }
    var name by remember { mutableStateOf(initial.name) }
    var brand by remember { mutableStateOf(initial.brand) }
    var model by remember { mutableStateOf(initial.model) }
    var serial by remember { mutableStateOf(initial.serialNo) }
    var year by remember { mutableStateOf(initial.year) }
    var location by remember { mutableStateOf(initial.location) }
    var installDate by remember { mutableStateOf(initial.installDate) }
    var warrantyEnd by remember { mutableStateOf(initial.warrantyEnd) }
    var notes by remember { mutableStateOf(initial.notes) }
    var pickCustomer by remember { mutableStateOf(false) }

    if (pickCustomer) {
        SelectDialog(
            title = "Müşteri seç",
            items = customers,
            itemTitle = { it.name },
            itemSubtitle = { listOf(it.city, it.contactName).filter { v -> v.isNotBlank() }.joinToString(" • ") },
            onPick = { customerId = it.id; pickCustomer = false },
            onDismiss = { pickCustomer = false },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.serialNo.isBlank() && initial.name.isBlank()) "Yeni Makine" else "Makineyi Düzenle") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PickerField(
                    label = "Müşteri *",
                    value = customers.firstOrNull { it.id == customerId }?.name.orEmpty(),
                    placeholder = "Müşteri seçin",
                    onClick = { pickCustomer = true },
                )
                AppTextField(name, { name = it }, "Makine adı / tipi *")
                AppTextField(brand, { brand = it }, "Marka")
                AppTextField(model, { model = it }, "Model")
                AppTextField(serial, { serial = it }, "Seri no")
                AppTextField(year, { year = it }, "Üretim yılı", keyboardType = KeyboardType.Number)
                AppTextField(location, { location = it }, "Konum / hat")
                DateField("Kurulum tarihi", installDate, { installDate = it }, clearable = true)
                DateField("Garanti bitişi", warrantyEnd, { warrantyEnd = it }, clearable = true)
                AppTextField(notes, { notes = it }, "Not", singleLine = false, minLines = 2)
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && customerId.isNotBlank(),
                onClick = {
                    onSave(
                        initial.copy(
                            customerId = customerId,
                            name = name.trim(),
                            brand = brand.trim(),
                            model = model.trim(),
                            serialNo = serial.trim(),
                            year = year.trim(),
                            location = location.trim(),
                            installDate = installDate,
                            warrantyEnd = warrantyEnd,
                            notes = notes.trim(),
                        )
                    )
                },
            ) { Text("Kaydet") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}

@Composable
fun SparePartDialog(
    initial: SparePart,
    onSave: (SparePart) -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var name by remember { mutableStateOf(initial.name) }
    var code by remember { mutableStateOf(initial.code) }
    var quantity by remember { mutableStateOf(if (initial.quantity == 0.0) "" else initial.quantity.toString()) }
    var unit by remember { mutableStateOf(initial.unit) }
    var status by remember { mutableStateOf(initial.status) }
    var note by remember { mutableStateOf(initial.note) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Yedek Parça") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AppTextField(name, { name = it }, "Parça adı *")
                AppTextField(code, { code = it }, "Parça kodu")
                AppTextField(quantity, { quantity = it }, "Miktar", keyboardType = KeyboardType.Decimal)
                AppTextField(unit, { unit = it }, "Birim", placeholder = "adet, metre, litre…")
                ChoiceChipRow(
                    label = "Durum",
                    options = PartStatus.entries.toList(),
                    selected = status,
                    optionLabel = { it.label },
                    onSelect = { status = it },
                )
                AppTextField(note, { note = it }, "Not", singleLine = false, minLines = 2)
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        initial.copy(
                            name = name.trim(),
                            code = code.trim(),
                            quantity = quantity.replace(',', '.').toDoubleOrNull() ?: 1.0,
                            unit = unit.trim().ifBlank { "adet" },
                            status = status,
                            note = note.trim(),
                        )
                    )
                },
            ) { Text("Kaydet") }
        },
        dismissButton = {
            if (onDelete != null) {
                TextButton(onClick = onDelete) { Text("Sil") }
            } else {
                TextButton(onClick = onDismiss) { Text("Vazgeç") }
            }
        },
    )
}
