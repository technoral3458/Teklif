package com.teknoral.parametrik.ui.result

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teknoral.parametrik.core.AuthRequiredException
import com.teknoral.parametrik.core.userMessage
import com.teknoral.parametrik.data.media.SavedFile
import com.teknoral.parametrik.data.repository.DownloadKind
import com.teknoral.parametrik.data.repository.JobRepository
import com.teknoral.parametrik.domain.model.Geometry
import com.teknoral.parametrik.domain.model.JobDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ResultUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val detail: JobDetail? = null,
    val geometry: Geometry = Geometry.EMPTY,
    val downloading: DownloadKind? = null,
    val lastSaved: SavedFile? = null,
    val message: String? = null
)

sealed interface ResultEvent {
    data class Downloaded(val file: SavedFile) : ResultEvent
    data object AuthRequired : ResultEvent
}

@HiltViewModel
class ResultViewModel @Inject constructor(
    private val repository: JobRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val jobId: Long = savedStateHandle.get<String>("jobId")?.toLongOrNull()
        ?: savedStateHandle.get<Long>("jobId")
        ?: 0L

    private val _state = MutableStateFlow(ResultUiState())
    val state: StateFlow<ResultUiState> = _state.asStateFlow()

    private val _events = Channel<ResultEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var downloadJob: Job? = null

    init {
        load()
    }

    fun load() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val detailResult = repository.jobDetail(jobId)
            val geometryResult = repository.geometry(jobId)

            val failure = detailResult.exceptionOrNull() ?: geometryResult.exceptionOrNull()
            if (failure is AuthRequiredException) _events.send(ResultEvent.AuthRequired)

            _state.value = _state.value.copy(
                loading = false,
                detail = detailResult.getOrNull() ?: _state.value.detail,
                geometry = geometryResult.getOrNull() ?: Geometry.EMPTY,
                error = failure?.userMessage()
            )
        }
    }

    fun download(kind: DownloadKind) {
        if (_state.value.downloading != null) return
        val name = _state.value.detail?.job?.name ?: "is"
        _state.value = _state.value.copy(downloading = kind, error = null)
        downloadJob = viewModelScope.launch {
            repository.download(jobId, name, kind)
                .onSuccess { file ->
                    _state.value = _state.value.copy(
                        downloading = null,
                        lastSaved = file,
                        message = "${file.displayName} indirildi."
                    )
                    _events.send(ResultEvent.Downloaded(file))
                }
                .onFailure { error ->
                    if (error is AuthRequiredException) _events.send(ResultEvent.AuthRequired)
                    _state.value = _state.value.copy(downloading = null, error = error.userMessage())
                }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        _state.value = _state.value.copy(downloading = null, message = "İndirme iptal edildi.")
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }
}
