package com.technoral.ucusbul.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.technoral.ucusbul.core.Prefs
import com.technoral.ucusbul.data.Airport
import com.technoral.ucusbul.data.AirportRepository
import com.technoral.ucusbul.data.GroundTransport
import com.technoral.ucusbul.data.SearchOutcome
import com.technoral.ucusbul.data.SearchQuery
import com.technoral.ucusbul.data.SortMode
import com.technoral.ucusbul.data.provider.Providers
import com.technoral.ucusbul.domain.SearchEngine
import com.technoral.ucusbul.work.PriceWatchWorker
import com.technoral.ucusbul.work.Watch
import com.technoral.ucusbul.work.WatchStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID

data class UiState(
    val origin: Airport? = null,
    val destination: Airport? = null,
    val originText: String = "",
    val destinationText: String = "",
    val originSuggestions: List<Airport> = emptyList(),
    val destinationSuggestions: List<Airport> = emptyList(),
    val departureDate: String = LocalDate.now().plusDays(30).toString(),
    val returnDate: String? = null,
    val adults: Int = 1,
    val maxGroundMinutes: Int = 120,
    val transport: GroundTransport = GroundTransport.MIXED,
    val includeSmallAirports: Boolean = false,
    val maxDestinations: Int = 6,
    val searchNearbyOrigins: Boolean = false,
    val nonStopOnly: Boolean = false,
    val searching: Boolean = false,
    val progress: String = "",
    val outcome: SearchOutcome? = null,
    val sortMode: SortMode = SortMode.PRICE,
    val error: String? = null,
    val watches: List<Watch> = emptyList(),
    val settingsVersion: Int = 0
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = AirportRepository.get(app)
    private val prefs = Prefs.get(app)
    private val engine = SearchEngine(app)
    private val watchStore = WatchStore(app)

    private val _state = MutableStateFlow(
        UiState(
            maxGroundMinutes = prefs.maxGroundMinutes,
            transport = prefs.transport,
            includeSmallAirports = prefs.includeSmallAirports,
            maxDestinations = prefs.maxDestinations,
            searchNearbyOrigins = prefs.searchNearbyOrigins
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        reloadWatches()
        viewModelScope.launch { repo.all() }
    }

    // ---------- Havalimanı seçimi ----------

    fun onOriginTextChange(text: String) {
        _state.update { it.copy(originText = text, origin = if (text.isBlank()) null else it.origin) }
        viewModelScope.launch {
            val list = withContext(Dispatchers.Default) { repo.search(text, 12) }
            _state.update { it.copy(originSuggestions = list) }
        }
    }

    fun onDestinationTextChange(text: String) {
        _state.update { it.copy(destinationText = text, destination = if (text.isBlank()) null else it.destination) }
        viewModelScope.launch {
            val list = withContext(Dispatchers.Default) { repo.search(text, 12) }
            _state.update { it.copy(destinationSuggestions = list) }
        }
    }

    fun pickOrigin(a: Airport) = _state.update {
        it.copy(origin = a, originText = a.label, originSuggestions = emptyList())
    }

    fun pickDestination(a: Airport) = _state.update {
        it.copy(destination = a, destinationText = a.label, destinationSuggestions = emptyList())
    }

    fun swap() = _state.update {
        it.copy(
            origin = it.destination,
            destination = it.origin,
            originText = it.destinationText,
            destinationText = it.originText,
            originSuggestions = emptyList(),
            destinationSuggestions = emptyList()
        )
    }

    // ---------- Arama parametreleri ----------

    fun setDepartureDate(iso: String) = _state.update { it.copy(departureDate = iso) }
    fun setReturnDate(iso: String?) = _state.update { it.copy(returnDate = iso) }
    fun setAdults(n: Int) = _state.update { it.copy(adults = n.coerceIn(1, 9)) }
    fun setNonStop(v: Boolean) = _state.update { it.copy(nonStopOnly = v) }
    fun setSortMode(mode: SortMode) = _state.update { s ->
        s.copy(
            sortMode = mode,
            outcome = s.outcome?.let { o -> o.copy(results = SearchEngine.sort(o.results, mode)) }
        )
    }

    fun setMaxGroundMinutes(minutes: Int) {
        prefs.maxGroundMinutes = minutes
        _state.update { it.copy(maxGroundMinutes = minutes) }
    }

    fun setTransport(t: GroundTransport) {
        prefs.transport = t
        _state.update { it.copy(transport = t) }
    }

    fun setIncludeSmall(v: Boolean) {
        prefs.includeSmallAirports = v
        _state.update { it.copy(includeSmallAirports = v) }
    }

    fun setMaxDestinations(n: Int) {
        prefs.maxDestinations = n
        _state.update { it.copy(maxDestinations = n) }
    }

    fun setNearbyOrigins(v: Boolean) {
        prefs.searchNearbyOrigins = v
        _state.update { it.copy(searchNearbyOrigins = v) }
    }

    fun onSettingsChanged() {
        Providers.reset()
        _state.update { it.copy(settingsVersion = it.settingsVersion + 1) }
    }

    // ---------- Arama ----------

    fun currentQuery(): SearchQuery? {
        val s = _state.value
        val o = s.origin ?: return null
        val d = s.destination ?: return null
        return SearchQuery(
            originIata = o.iata,
            originCity = o.city,
            destinationIata = d.iata,
            destinationCity = d.city,
            departureDate = s.departureDate,
            returnDate = s.returnDate,
            adults = s.adults,
            maxGroundMinutes = s.maxGroundMinutes,
            transport = s.transport,
            includeSmallAirports = s.includeSmallAirports,
            maxDestinations = s.maxDestinations,
            searchNearbyOrigins = s.searchNearbyOrigins,
            nonStopOnly = s.nonStopOnly
        )
    }

    fun search() {
        val query = currentQuery() ?: run {
            _state.update { it.copy(error = "Lütfen kalkış ve varış şehirlerini listeden seçin.") }
            return
        }
        searchJob?.cancel()
        _state.update { it.copy(searching = true, error = null, progress = "Havalimanları belirleniyor…") }
        searchJob = viewModelScope.launch {
            val outcome = runCatching {
                engine.run(query) { msg -> _state.update { it.copy(progress = msg) } }
            }.getOrElse { e ->
                _state.update {
                    it.copy(searching = false, progress = "", error = e.message ?: "Arama başarısız oldu")
                }
                return@launch
            }
            _state.update {
                it.copy(
                    searching = false,
                    progress = "",
                    outcome = outcome.copy(results = SearchEngine.sort(outcome.results, it.sortMode))
                )
            }
        }
    }

    fun cancelSearch() {
        searchJob?.cancel()
        _state.update { it.copy(searching = false, progress = "") }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    // ---------- Fiyat takibi ----------

    fun reloadWatches() {
        _state.update { it.copy(watches = watchStore.all()) }
    }

    fun addWatchFromCurrentSearch(targetPrice: Double?) {
        val query = currentQuery() ?: return
        val best = _state.value.outcome?.results?.minByOrNull { it.offer.price }?.offer?.price
        val watch = Watch(
            id = UUID.randomUUID().toString(),
            label = "${query.originCity} → ${query.destinationCity} • ${formatDateShort(query.departureDate)}",
            query = query,
            targetPrice = targetPrice,
            lastBestPrice = best
        )
        watchStore.add(watch)
        PriceWatchWorker.schedule(getApplication<Application>())
        reloadWatches()
    }

    fun removeWatch(id: String) {
        watchStore.remove(id)
        val remaining = watchStore.all()
        if (remaining.isEmpty()) PriceWatchWorker.cancel(getApplication<Application>())
        reloadWatches()
    }

    fun checkWatchesNow() {
        PriceWatchWorker.checkNow(getApplication<Application>())
    }
}
