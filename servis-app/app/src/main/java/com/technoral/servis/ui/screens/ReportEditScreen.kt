package com.technoral.servis.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.technoral.servis.data.Customer
import com.technoral.servis.data.Department
import com.technoral.servis.data.DepartmentWork
import com.technoral.servis.data.Machine
import com.technoral.servis.data.PhotoTag
import com.technoral.servis.data.Priority
import com.technoral.servis.data.ServicePhoto
import com.technoral.servis.data.ServiceStatus
import com.technoral.servis.data.ServiceType
import com.technoral.servis.data.SparePart
import com.technoral.servis.ui.AppViewModel
import com.technoral.servis.ui.Navigator
import com.technoral.servis.ui.Screen
import com.technoral.servis.ui.components.AppTextField
import com.technoral.servis.ui.components.AppTopBar
import com.technoral.servis.ui.components.ChoiceChipRow
import com.technoral.servis.ui.components.ConfirmDialog
import com.technoral.servis.ui.components.CustomerEditorDialog
import com.technoral.servis.ui.components.DateField
import com.technoral.servis.ui.components.FlowRowCompat
import com.technoral.servis.ui.components.MachineEditorDialog
import com.technoral.servis.ui.components.PickerField
import com.technoral.servis.ui.components.SectionCard
import com.technoral.servis.ui.components.SelectDialog
import com.technoral.servis.ui.components.SignaturePad
import com.technoral.servis.ui.components.SparePartDialog
import com.technoral.servis.ui.components.TimeField
import com.technoral.servis.util.asNumber
import com.technoral.servis.util.createCameraTarget
import com.technoral.servis.util.minutesAsDuration
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportEditScreen(vm: AppViewModel, nav: Navigator) {
    val draft by vm.draft.collectAsState()
    val customers by vm.customers.collectAsState()
    val machines by vm.machines.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(draft) {
        if (draft == null) nav.back()
    }
    val report = draft ?: return

    var pickCustomer by remember { mutableStateOf(false) }
    var pickMachine by remember { mutableStateOf(false) }
    var editingCustomer by remember { mutableStateOf<Customer?>(null) }
    var editingMachine by remember { mutableStateOf<Machine?>(null) }
    var editingPhoto by remember { mutableStateOf<ServicePhoto?>(null) }
    var editingPart by remember { mutableStateOf<SparePart?>(null) }
    var newPart by remember { mutableStateOf(false) }
    var showSignature by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var cameraFile by remember { mutableStateOf<File?>(null) }

    val selectedCustomer = customers.firstOrNull { it.id == report.customerId }
    val selectedMachine = machines.firstOrNull { it.id == report.machineId }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = cameraFile
        if (ok && file != null) vm.addPhotoFromCamera(file) else file?.delete()
        cameraFile = null
    }

    fun launchCamera() {
        val (file, uri) = createCameraTarget(context, vm.cameraDir())
        cameraFile = file
        cameraLauncher.launch(uri)
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera() else vm.notify("Fotoğraf çekmek için kamera izni gerekiyor.")
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        uris.forEach { vm.addPhotoFromGallery(it) }
    }

    // ---------------------------------------------------------------- pencereler

    if (pickCustomer) {
        SelectDialog(
            title = "Müşteri seç",
            items = customers,
            itemTitle = { it.name },
            itemSubtitle = { listOf(it.city, it.contactName, it.phone).filter { v -> v.isNotBlank() }.joinToString(" • ") },
            onPick = { picked ->
                vm.updateDraft {
                    val keepMachine = vm.machine(it.machineId)?.customerId == picked.id
                    it.copy(customerId = picked.id, machineId = if (keepMachine) it.machineId else "")
                }
                pickCustomer = false
            },
            onDismiss = { pickCustomer = false },
            createLabel = "Yeni müşteri",
            onCreate = { pickCustomer = false; editingCustomer = Customer() },
        )
    }

    if (pickMachine) {
        val list = vm.machinesOf(report.customerId.ifBlank { null })
        SelectDialog(
            title = "Makine seç",
            items = list,
            itemTitle = { it.displayName },
            itemSubtitle = {
                listOf(it.serialNo, it.location, vm.customer(it.customerId)?.name.orEmpty())
                    .filter { v -> v.isNotBlank() }.joinToString(" • ")
            },
            onPick = { picked ->
                vm.updateDraft {
                    it.copy(
                        machineId = picked.id,
                        customerId = it.customerId.ifBlank { picked.customerId },
                    )
                }
                pickMachine = false
            },
            onDismiss = { pickMachine = false },
            createLabel = "Yeni makine",
            onCreate = {
                pickMachine = false
                editingMachine = Machine(customerId = report.customerId)
            },
        )
    }

    editingCustomer?.let { customer ->
        CustomerEditorDialog(
            initial = customer,
            onSave = { saved ->
                vm.saveCustomer(saved)
                vm.updateDraft { it.copy(customerId = saved.id) }
                editingCustomer = null
            },
            onDismiss = { editingCustomer = null },
        )
    }

    editingMachine?.let { machine ->
        MachineEditorDialog(
            initial = machine,
            customers = customers,
            onSave = { saved ->
                vm.saveMachine(saved)
                vm.updateDraft { it.copy(machineId = saved.id, customerId = saved.customerId) }
                editingMachine = null
            },
            onDismiss = { editingMachine = null },
        )
    }

    editingPhoto?.let { photo ->
        PhotoEditDialog(
            photo = photo,
            onSave = { vm.updatePhoto(it); editingPhoto = null },
            onDelete = { vm.removePhoto(photo); editingPhoto = null },
            onDismiss = { editingPhoto = null },
        )
    }

    if (newPart) {
        SparePartDialog(
            initial = SparePart(),
            onSave = { part ->
                vm.updateDraft { it.copy(parts = it.parts + part) }
                newPart = false
            },
            onDismiss = { newPart = false },
        )
    }

    editingPart?.let { part ->
        SparePartDialog(
            initial = part,
            onSave = { updated ->
                vm.updateDraft { r -> r.copy(parts = r.parts.map { if (it.id == updated.id) updated else it }) }
                editingPart = null
            },
            onDelete = {
                vm.updateDraft { r -> r.copy(parts = r.parts.filterNot { it.id == part.id }) }
                editingPart = null
            },
            onDismiss = { editingPart = null },
        )
    }

    if (showSignature) {
        AlertDialog(
            onDismissRequest = { showSignature = false },
            title = { Text("İmza") },
            text = {
                SignaturePad(
                    onSave = { bitmap -> vm.saveSignature(bitmap); showSignature = false },
                    onCancel = { showSignature = false },
                )
            },
            confirmButton = {},
        )
    }

    if (confirmDiscard) {
        ConfirmDialog(
            title = "Rapordan çıkılsın mı?",
            message = "Kaydedilmemiş değişiklikler silinecek.",
            confirmLabel = "Çık",
            onConfirm = { vm.discardDraft() },
            onDismiss = { confirmDiscard = false },
        )
    }

    // -------------------------------------------------------------------- form

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AppTopBar(
                title = report.reportNo.ifBlank { "Yeni Servis" },
                subtitle = selectedCustomer?.name ?: "Servis raporu",
                onBack = { confirmDiscard = true },
                actions = {
                    TextButton(onClick = {
                        vm.saveDraftSilently()
                        vm.notify("Taslak kaydedildi.")
                    }) { Text("Taslak") }
                },
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = { confirmDiscard = true },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) { Text("Vazgeç") }
                    Button(
                        onClick = {
                            val id = vm.commitDraft()
                            if (id != null) nav.replace(Screen.ReportDetail(id))
                        },
                        modifier = Modifier.weight(1.4f).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        enabled = report.customerId.isNotBlank(),
                    ) {
                        Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Raporu Kaydet")
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                SectionCard(title = "Müşteri ve Makine") {
                    PickerField(
                        label = "Müşteri *",
                        value = selectedCustomer?.name.orEmpty(),
                        placeholder = "Müşteri seçin",
                        onClick = { pickCustomer = true },
                    )
                    PickerField(
                        label = "Makine",
                        value = selectedMachine?.displayName.orEmpty(),
                        placeholder = if (report.customerId.isBlank()) "Önce müşteri seçin" else "Makine seçin",
                        onClick = { pickMachine = true },
                    )
                    selectedMachine?.let { machine ->
                        val info = listOf(
                            machine.serialNo.takeIf { it.isNotBlank() }?.let { "Seri: $it" },
                            machine.location.takeIf { it.isNotBlank() },
                        ).filterNotNull().joinToString(" • ")
                        if (info.isNotBlank()) {
                            Text(
                                info,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            item {
                SectionCard(title = "Servis Bilgileri") {
                    ChoiceChipRow(
                        label = "Servis tipi",
                        options = ServiceType.entries.toList(),
                        selected = report.type,
                        optionLabel = { it.label },
                        onSelect = { type -> vm.updateDraft { it.copy(type = type) } },
                    )
                    ChoiceChipRow(
                        label = "Öncelik",
                        options = Priority.entries.toList(),
                        selected = report.priority,
                        optionLabel = { it.label },
                        onSelect = { p -> vm.updateDraft { it.copy(priority = p) } },
                    )
                    ChoiceChipRow(
                        label = "Sonuç durumu",
                        options = ServiceStatus.entries.toList(),
                        selected = report.status,
                        optionLabel = { it.label },
                        onSelect = { s -> vm.updateDraft { it.copy(status = s) } },
                    )
                    DateField("Servis tarihi", report.serviceDate, { value ->
                        value?.let { day -> vm.updateDraft { it.copy(serviceDate = day) } }
                    })
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.weight(1f)) {
                            TimeField("Başlangıç", report.startTime, report.serviceDate, { t ->
                                vm.updateDraft { it.copy(startTime = t) }
                            })
                        }
                        Box(Modifier.weight(1f)) {
                            TimeField("Bitiş", report.endTime, report.serviceDate, { t ->
                                vm.updateDraft { it.copy(endTime = t) }
                            })
                        }
                    }
                    report.durationMinutes?.let {
                        Text(
                            "Çalışma süresi: ${minutesAsDuration(it)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    AppTextField(
                        value = if (report.travelKm == 0.0) "" else report.travelKm.asNumber(),
                        onValueChange = { value ->
                            val km = value.replace(',', '.').toDoubleOrNull() ?: 0.0
                            vm.updateDraft { it.copy(travelKm = km) }
                        },
                        label = "Yol (km)",
                        keyboardType = KeyboardType.Decimal,
                    )
                    AppTextField(
                        value = report.technician,
                        onValueChange = { value -> vm.updateDraft { it.copy(technician = value) } },
                        label = "Teknisyen",
                    )
                }
            }

            item {
                SectionCard(
                    title = "Çalışılan Bölümler",
                    subtitle = "Hangi alanlarda işlem yapıldıysa seçin",
                ) {
                    FlowRowCompat {
                        Department.entries.forEach { dept ->
                            val active = report.departments.any { it.department == dept }
                            DepartmentChip(
                                label = dept.label,
                                selected = active,
                                onClick = {
                                    vm.updateDraft { r ->
                                        if (active) r.copy(departments = r.departments.filterNot { it.department == dept })
                                        else r.copy(departments = r.departments + DepartmentWork(dept))
                                    }
                                },
                            )
                        }
                    }
                    report.departments.forEach { dw ->
                        AppTextField(
                            value = dw.work,
                            onValueChange = { value ->
                                vm.updateDraft { r ->
                                    r.copy(
                                        departments = r.departments.map {
                                            if (it.department == dw.department) it.copy(work = value) else it
                                        }
                                    )
                                }
                            },
                            label = "${dw.department.label} — yapılan işlem",
                            singleLine = false,
                            minLines = 2,
                        )
                    }
                }
            }

            item {
                SectionCard(title = "Arıza ve Çözüm") {
                    AppTextField(
                        value = report.faultDescription,
                        onValueChange = { v -> vm.updateDraft { it.copy(faultDescription = v) } },
                        label = "Arıza / talep tanımı",
                        placeholder = "Müşterinin bildirdiği durum",
                        singleLine = false,
                        minLines = 3,
                    )
                    AppTextField(
                        value = report.faultCause,
                        onValueChange = { v -> vm.updateDraft { it.copy(faultCause = v) } },
                        label = "Arıza nedeni (kök neden)",
                        placeholder = "Arızaya ne sebep oldu?",
                        singleLine = false,
                        minLines = 3,
                    )
                    AppTextField(
                        value = report.workDone,
                        onValueChange = { v -> vm.updateDraft { it.copy(workDone = v) } },
                        label = "Yapılan işlem / çözüm",
                        singleLine = false,
                        minLines = 4,
                    )
                    AppTextField(
                        value = report.recommendations,
                        onValueChange = { v -> vm.updateDraft { it.copy(recommendations = v) } },
                        label = "Öneriler",
                        placeholder = "Müşteriye iletilecek tavsiyeler",
                        singleLine = false,
                        minLines = 2,
                    )
                }
            }

            item {
                SectionCard(
                    title = "Fotoğraflar",
                    subtitle = if (report.photos.isEmpty()) "Arızayı ve yapılan işi belgeleyin"
                    else "${report.photos.size} fotoğraf",
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = {
                                val granted = ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.CAMERA,
                                ) == PackageManager.PERMISSION_GRANTED
                                if (granted) launchCamera()
                                else permissionLauncher.launch(Manifest.permission.CAMERA)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(Icons.Default.PhotoCamera, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Çek")
                        }
                        OutlinedButton(
                            onClick = { galleryLauncher.launch("image/*") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(Icons.Default.PhotoLibrary, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Galeri")
                        }
                    }
                    if (report.photos.isNotEmpty()) {
                        report.photos.chunked(3).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { photo ->
                                    PhotoThumb(
                                        photo = photo,
                                        modifier = Modifier.weight(1f),
                                        onClick = { editingPhoto = photo },
                                    )
                                }
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }

            item {
                SectionCard(
                    title = "Yedek Parçalar",
                    trailing = {
                        IconButton(onClick = { newPart = true }) {
                            Icon(Icons.Default.AddCircleOutline, "Parça ekle")
                        }
                    },
                ) {
                    if (report.parts.isEmpty()) {
                        Text(
                            "Kullanılan veya temin edilmesi gereken parçaları ekleyin.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        report.parts.forEachIndexed { index, part ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(part.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        listOf(
                                            part.code.takeIf { it.isNotBlank() },
                                            "${part.quantity.asNumber()} ${part.unit}",
                                            part.status.label,
                                        ).filterNotNull().joinToString(" • "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = { editingPart = part }) {
                                    Icon(Icons.Default.Edit, "Düzenle", Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }

            item {
                SectionCard(title = "Teslim ve İmza") {
                    AppTextField(
                        value = report.customerRep,
                        onValueChange = { v -> vm.updateDraft { it.copy(customerRep = v) } },
                        label = "Müşteri yetkilisi",
                    )
                    DateField(
                        "Sonraki bakım tarihi",
                        report.nextMaintenance,
                        { value -> vm.updateDraft { it.copy(nextMaintenance = value) } },
                        clearable = true,
                    )
                    if (report.signaturePath != null) {
                        Card(
                            Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
                        ) {
                            AsyncImage(
                                model = File(report.signaturePath),
                                contentDescription = "İmza",
                                modifier = Modifier.fillMaxWidth().height(110.dp).padding(8.dp),
                                contentScale = ContentScale.Fit,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { showSignature = true }) { Text("Yeniden al") }
                            TextButton(onClick = { vm.clearSignature() }) {
                                Icon(Icons.Default.Delete, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Sil")
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = { showSignature = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(Icons.Default.Edit, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("İmza al")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DepartmentChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(10.dp),
        onClick = onClick,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected) {
                Icon(Icons.Default.Check, null, Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun PhotoThumb(photo: ServicePhoto, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() },
    ) {
        AsyncImage(
            model = File(photo.path),
            contentDescription = photo.caption,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Surface(
            color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f),
            contentColor = androidx.compose.ui.graphics.Color.White,
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(),
        ) {
            Text(
                photo.tag.label,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun PhotoEditDialog(
    photo: ServicePhoto,
    onSave: (ServicePhoto) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var caption by remember { mutableStateOf(photo.caption) }
    var tag by remember { mutableStateOf(photo.tag) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Fotoğraf") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AsyncImage(
                    model = File(photo.path),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop,
                )
                ChoiceChipRow(
                    label = "Etiket",
                    options = PhotoTag.entries.toList(),
                    selected = tag,
                    optionLabel = { it.label },
                    onSelect = { tag = it },
                )
                AppTextField(caption, { caption = it }, "Açıklama", singleLine = false, minLines = 2)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(photo.copy(caption = caption.trim(), tag = tag)) }) { Text("Kaydet") }
        },
        dismissButton = {
            TextButton(onClick = onDelete) {
                Text("Sil", color = MaterialTheme.colorScheme.error)
            }
        },
    )
}
