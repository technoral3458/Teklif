package com.technoral.ucusbul.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.technoral.ucusbul.data.Airport
import com.technoral.ucusbul.data.GroundTransport
import com.technoral.ucusbul.data.OfferResult
import com.technoral.ucusbul.data.SortMode
import com.technoral.ucusbul.data.formatMinutes
import com.technoral.ucusbul.domain.Airlines
import com.technoral.ucusbul.domain.BookingLinks
import com.technoral.ucusbul.domain.badge
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(vm: MainViewModel, state: UiState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var showFilters by remember { mutableStateOf(false) }
    var bookingFor by remember { mutableStateOf<OfferResult?>(null) }
    var showWatchDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AirportField(
                        label = "Nereden",
                        value = state.originText,
                        suggestions = state.originSuggestions,
                        selected = state.origin,
                        onValueChange = vm::onOriginTextChange,
                        onPick = vm::pickOrigin
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(onClick = vm::swap) {
                            Icon(Icons.Default.SwapVert, contentDescription = "Yönü değiştir")
                        }
                    }
                    AirportField(
                        label = "Nereye",
                        value = state.destinationText,
                        suggestions = state.destinationSuggestions,
                        selected = state.destination,
                        onValueChange = vm::onDestinationTextChange,
                        onPick = vm::pickDestination
                    )

                    DateRow(state, vm)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Yolcu", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.width(10.dp))
                        OutlinedButton(onClick = { vm.setAdults(state.adults - 1) }) { Text("−") }
                        Text(
                            "  ${state.adults}  ",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        OutlinedButton(onClick = { vm.setAdults(state.adults + 1) }) { Text("+") }
                        Spacer(Modifier.width(12.dp))
                        TextButton(onClick = { showFilters = !showFilters }) {
                            Icon(Icons.Default.Tune, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (showFilters) "Gizle" else "Seçenekler")
                        }
                    }

                    if (showFilters) {
                        Divider()
                        FilterSection(state, vm)
                    }

                    Button(
                        onClick = { if (state.searching) vm.cancelSearch() else vm.search() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (state.searching) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(18.dp).width(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("Durdur")
                        } else {
                            Icon(Icons.Default.Search, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Çevredeki havalimanlarıyla birlikte ara")
                        }
                    }

                    if (state.searching && state.progress.isNotBlank()) {
                        Text(
                            state.progress,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        state.error?.let { err ->
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        Modifier.padding(12.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            err,
                            Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        TextButton(onClick = vm::clearError) { Text("Tamam") }
                    }
                }
            }
        }

        val outcome = state.outcome
        if (outcome != null) {
            item { ScanSummary(state) }

            if (outcome.notes.isNotEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Notlar", style = MaterialTheme.typography.titleSmall)
                            outcome.notes.forEach {
                                Text(
                                    "• $it",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            if (outcome.results.isEmpty()) {
                item {
                    Text(
                        "Sonuç bulunamadı. Tarihi değiştirmeyi, mesafe sınırını artırmayı veya " +
                            "Ayarlar'dan bir sağlayıcı anahtarı eklemeyi deneyin.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                item { SortRow(state, vm) { showWatchDialog = true } }
                items(outcome.results) { result ->
                    ResultCard(result, state) { bookingFor = result }
                }
            }
        }
    }

    bookingFor?.let { result ->
        BookingSheet(
            result = result,
            state = state,
            onDismiss = { bookingFor = null },
            onOpen = { url ->
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        )
    }

    if (showWatchDialog) {
        WatchDialog(
            state = state,
            onDismiss = { showWatchDialog = false },
            onConfirm = { target ->
                vm.addWatchFromCurrentSearch(target)
                showWatchDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AirportField(
    label: String,
    value: String,
    suggestions: List<Airport>,
    selected: Airport?,
    onValueChange: (String) -> Unit,
    onPick: (Airport) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            placeholder = { Text("Şehir veya havalimanı kodu") },
            leadingIcon = { Icon(Icons.Default.FlightTakeoff, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (selected == null && suggestions.isNotEmpty()) {
            Card(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                    suggestions.forEach { a ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPick(a) }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                "${a.city} • ${a.iata}",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "${a.name} · ${a.country}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Divider()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRow(state: UiState, vm: MainViewModel) {
    var pickDeparture by remember { mutableStateOf(false) }
    var pickReturn by remember { mutableStateOf(false) }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { pickDeparture = true }, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.CalendarMonth, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(formatDateShort(state.departureDate))
        }
        OutlinedButton(onClick = { pickReturn = true }, modifier = Modifier.weight(1f)) {
            Text(state.returnDate?.let { "Dönüş ${formatDateShort(it)}" } ?: "Tek yön")
        }
    }

    if (pickDeparture) {
        DatePickerSheet(
            initial = state.departureDate,
            onDismiss = { pickDeparture = false },
            onPick = { vm.setDepartureDate(it); pickDeparture = false }
        )
    }
    if (pickReturn) {
        DatePickerSheet(
            initial = state.returnDate ?: state.departureDate,
            onDismiss = { pickReturn = false },
            onPick = { vm.setReturnDate(it); pickReturn = false },
            onClear = { vm.setReturnDate(null); pickReturn = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerSheet(
    initial: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    onClear: (() -> Unit)? = null
) {
    val initialMillis = runCatching {
        LocalDate.parse(initial).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }.getOrNull()
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = pickerState.selectedDateMillis
                if (millis != null) {
                    val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    onPick(date.toString())
                } else onDismiss()
            }) { Text("Seç") }
        },
        dismissButton = {
            Row {
                if (onClear != null) TextButton(onClick = onClear) { Text("Temizle") }
                TextButton(onClick = onDismiss) { Text("Vazgeç") }
            }
        }
    ) {
        DatePicker(state = pickerState)
    }
}

@Composable
private fun FilterSection(state: UiState, vm: MainViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Hedef şehre en fazla ${formatMinutes(state.maxGroundMinutes)} mesafedeki " +
                "havalimanları da taransın",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        Slider(
            value = state.maxGroundMinutes.toFloat(),
            onValueChange = { vm.setMaxGroundMinutes((it / 15f).toInt() * 15) },
            valueRange = 0f..360f,
            steps = 23
        )

        Text("Ulaşım varsayımı", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GroundTransport.entries.forEach { t ->
                FilterChip(
                    selected = state.transport == t,
                    onClick = { vm.setTransport(t) },
                    label = { Text(t.title, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        Text(
            "Taranacak varış havalimanı sayısı: ${state.maxDestinations}",
            style = MaterialTheme.typography.bodyMedium
        )
        Slider(
            value = state.maxDestinations.toFloat(),
            onValueChange = { vm.setMaxDestinations(it.toInt().coerceIn(1, 12)) },
            valueRange = 1f..12f,
            steps = 10
        )

        SwitchRow("Küçük havalimanlarını da tara", state.includeSmallAirports, vm::setIncludeSmall)
        SwitchRow("Kalkışta da yakın havalimanlarına bak", state.searchNearbyOrigins, vm::setNearbyOrigins)
        SwitchRow("Sadece aktarmasız uçuşlar", state.nonStopOnly, vm::setNonStop)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ScanSummary(state: UiState) {
    val outcome = state.outcome ?: return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "${outcome.scanned.size} havalimanı tarandı • ${outcome.results.size} teklif",
                style = MaterialTheme.typography.titleSmall
            )
            if (outcome.usedDemo) {
                Text(
                    "DEMO verisi gösteriliyor — gerçek fiyatlar için Ayarlar'dan bir sağlayıcı anahtarı ekleyin.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            outcome.scanned.forEach { n ->
                Text(
                    "· ${n.airport.city} (${n.airport.iata}) — ${n.badge()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SortRow(state: UiState, vm: MainViewModel, onWatch: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SortMode.entries.forEach { mode ->
                FilterChip(
                    selected = state.sortMode == mode,
                    onClick = { vm.setSortMode(mode) },
                    label = { Text(mode.title, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
        OutlinedButton(onClick = onWatch, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.NotificationsActive, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Bu aramayı takibe al, ucuzlayınca haber ver")
        }
    }
}

@Composable
private fun ResultCard(result: OfferResult, state: UiState, onClick: () -> Unit) {
    val offer = result.offer
    ElevatedCard(Modifier.fillMaxWidth().clickable { onClick() }) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${offer.origin} → ${result.destination.airport.iata}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        result.destination.airport.city,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        formatPrice(offer.price, offer.currency),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (offer.isDemo) {
                        Text(
                            "DEMO",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Text(Airlines.label(offer.airlineCodes), style = MaterialTheme.typography.bodyMedium)

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AssistChip(onClick = onClick, label = { Text(stopsLabel(offer.stops)) })
                if (offer.durationMinutes > 0) {
                    AssistChip(
                        onClick = onClick,
                        label = { Text("Uçuş ${formatMinutes(offer.durationMinutes)}") }
                    )
                }
                if (timeOf(offer.departure).isNotBlank()) {
                    AssistChip(onClick = onClick, label = { Text("Kalkış ${timeOf(offer.departure)}") })
                }
            }

            if (!result.destination.isAnchor) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.DirectionsCar,
                        contentDescription = null,
                        modifier = Modifier.height(16.dp).width(16.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "${state.destination?.city ?: "hedef"} şehrine " +
                            "${result.destination.distanceKm} km · " +
                            "${formatMinutes(result.destination.groundMinutes)} kara yolu " +
                            "(toplam ${formatMinutes(result.totalMinutes)})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            Text(
                "Kaynak: ${offer.provider}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookingSheet(
    result: OfferResult,
    state: UiState,
    onDismiss: () -> Unit,
    onOpen: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val options = BookingLinks.optionsFor(
        offer = result.offer,
        departureDate = state.departureDate,
        returnDate = state.returnDate,
        adults = state.adults
    )

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "${result.offer.origin} → ${result.destination.airport.city} " +
                    "(${result.destination.airport.iata})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "${formatPrice(result.offer.price, result.offer.currency)} · " +
                    Airlines.label(result.offer.airlineCodes) + " · " + stopsLabel(result.offer.stops),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                formatDateLong(state.departureDate),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (result.offer.isDemo) {
                Text(
                    "Bu bir demo fiyatıdır. Aşağıdaki bağlantılar gerçek arama sonuçlarını açar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Divider()
            options.forEach { option ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(option.url) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(option.title, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            option.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.Default.OpenInNew, contentDescription = null)
                }
                Divider()
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun WatchDialog(
    state: UiState,
    onDismiss: () -> Unit,
    onConfirm: (Double?) -> Unit
) {
    val best = state.outcome?.results?.minByOrNull { it.offer.price }?.offer
    var text by remember {
        mutableStateOf(best?.let { (it.price * 0.9).toInt().toString() } ?: "")
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Fiyat takibi") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Bu arama arka planda düzenli tekrarlanır. Fiyat hedefin altına inince " +
                        "veya belirgin şekilde düşünce bildirim gelir.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter { c -> c.isDigit() } },
                    label = { Text("Hedef fiyat (boş bırakılabilir)") },
                    singleLine = true
                )
                best?.let {
                    Text(
                        "Şu anki en iyi fiyat: ${formatPrice(it.price, it.currency)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.toDoubleOrNull()) }) { Text("Takibe al") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } }
    )
}
