package com.teknoral.parametrik.ui.jobs

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teknoral.parametrik.core.AuthRequiredException
import com.teknoral.parametrik.core.userMessage
import com.teknoral.parametrik.data.media.CropRect
import com.teknoral.parametrik.data.repository.JobRepository
import com.teknoral.parametrik.data.repository.UploadState
import com.teknoral.parametrik.domain.model.Job
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job as CoroutineJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UploadUiState(
    val label: String,
    val progress: Float? = null
)

data class JobListUiState(
    val jobs: List<Job> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val upload: UploadUiState? = null
)

sealed interface JobListEvent {
    data class OpenJob(val jobId: Long) : JobListEvent
    data object AuthRequired : JobListEvent
}

@HiltViewModel
class JobListViewModel @Inject constructor(
    private val repository: JobRepository
) : ViewModel() {

    private val _state = MutableStateFlow(JobListUiState())
    val state: StateFlow<JobListUiState> = _state.asStateFlow()

    private val _events = Channel<JobListEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var uploadJob: CoroutineJob? = null

    init {
        load(initial = true)
    }

    fun load(initial: Boolean = false) {
        _state.value = _state.value.copy(
            loading = initial,
            refreshing = !initial,
            error = null
        )
        viewModelScope.launch {
            repository.jobs()
                .onSuccess { jobs ->
                    _state.value = _state.value.copy(
                        jobs = jobs,
                        loading = false,
                        refreshing = false,
                        error = null
                    )
                }
                .onFailure { error -> fail(error) }
        }
    }

    fun delete(job: Job) {
        viewModelScope.launch {
            repository.delete(job.id)
                .onSuccess { message ->
                    _state.value = _state.value.copy(message = message ?: "${job.name} silindi.")
                    load()
                }
                .onFailure { error -> fail(error) }
        }
    }

    fun uploadImage(name: String, uri: Uri, crop: CropRect) {
        startUpload { repository.uploadImage(name, uri, crop) }
    }

    fun uploadMesh(name: String, uri: Uri) {
        startUpload { repository.uploadMesh(name, uri) }
    }

    private fun startUpload(source: () -> kotlinx.coroutines.flow.Flow<UploadState>) {
        uploadJob?.cancel()
        _state.value = _state.value.copy(upload = UploadUiState("Yükleniyor…", null), error = null)
        uploadJob = viewModelScope.launch {
            source()
                .catch { error ->
                    _state.value = _state.value.copy(upload = null)
                    fail(error)
                }
                .collect { update ->
                    when (update) {
                        is UploadState.Preparing ->
                            _state.value = _state.value.copy(upload = UploadUiState(update.message, null))

                        is UploadState.Progress -> _state.value = _state.value.copy(
                            upload = UploadUiState("Yükleniyor…", update.fraction)
                        )

                        is UploadState.Completed -> {
                            _state.value = _state.value.copy(upload = null)
                            _events.send(JobListEvent.OpenJob(update.jobId))
                            load()
                        }
                    }
                }
        }
    }

    fun cancelUpload() {
        uploadJob?.cancel()
        uploadJob = null
        _state.value = _state.value.copy(upload = null, message = "Yükleme iptal edildi.")
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }

    private suspend fun fail(error: Throwable) {
        if (error is AuthRequiredException) {
            _events.send(JobListEvent.AuthRequired)
        }
        _state.value = _state.value.copy(
            loading = false,
            refreshing = false,
            error = error.userMessage()
        )
    }
}
