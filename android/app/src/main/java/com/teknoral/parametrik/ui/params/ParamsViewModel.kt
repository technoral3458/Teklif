package com.teknoral.parametrik.ui.params

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teknoral.parametrik.core.AuthRequiredException
import com.teknoral.parametrik.core.userMessage
import com.teknoral.parametrik.data.repository.JobRepository
import com.teknoral.parametrik.domain.logic.ParamValidation
import com.teknoral.parametrik.domain.logic.ValidationResult
import com.teknoral.parametrik.domain.model.JobDetail
import com.teknoral.parametrik.domain.model.SourceKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ParamsUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val detail: JobDetail? = null,
    val meshForm: MeshForm = MeshForm(),
    val imageForm: ImageForm = ImageForm(),
    val slicing: Boolean = false,
    val imageUrl: String? = null
) {
    val sourceKind: SourceKind get() = detail?.job?.sourceKind ?: SourceKind.MESH

    val validation: ValidationResult
        get() = if (sourceKind == SourceKind.IMAGE) ParamValidation.validate(imageForm.toParams())
        else ParamValidation.validate(meshForm.toParams())
}

sealed interface ParamsEvent {
    data class Sliced(val jobId: Long, val message: String?) : ParamsEvent
    data object AuthRequired : ParamsEvent
}

@HiltViewModel
class ParamsViewModel @Inject constructor(
    private val repository: JobRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val jobId: Long = savedStateHandle.get<String>("jobId")?.toLongOrNull()
        ?: savedStateHandle.get<Long>("jobId")
        ?: 0L

    private val _state = MutableStateFlow(ParamsUiState())
    val state: StateFlow<ParamsUiState> = _state.asStateFlow()

    private val _events = Channel<ParamsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var sliceJob: Job? = null

    init {
        load()
    }

    fun load() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            repository.jobDetail(jobId)
                .onSuccess { detail ->
                    _state.value = _state.value.copy(
                        loading = false,
                        detail = detail,
                        meshForm = MeshForm.from(detail),
                        imageForm = ImageForm.from(detail),
                        imageUrl = if (detail.job.sourceKind == SourceKind.IMAGE) {
                            repository.imageUrl(detail.job.id)
                        } else {
                            null
                        }
                    )
                }
                .onFailure { error ->
                    if (error is AuthRequiredException) _events.send(ParamsEvent.AuthRequired)
                    _state.value = _state.value.copy(loading = false, error = error.userMessage())
                }
        }
    }

    fun updateMesh(transform: (MeshForm) -> MeshForm) {
        _state.value = _state.value.copy(meshForm = transform(_state.value.meshForm))
    }

    fun updateImage(transform: (ImageForm) -> ImageForm) {
        _state.value = _state.value.copy(imageForm = transform(_state.value.imageForm))
    }

    fun slice() {
        val current = _state.value
        if (current.slicing || !current.validation.isValid) return
        _state.value = current.copy(slicing = true, error = null)
        sliceJob = viewModelScope.launch {
            val result = if (current.sourceKind == SourceKind.IMAGE) {
                repository.sliceImage(jobId, current.imageForm.toParams())
            } else {
                repository.slice(jobId, current.meshForm.toParams())
            }
            result
                .onSuccess { message ->
                    _state.value = _state.value.copy(slicing = false)
                    _events.send(ParamsEvent.Sliced(jobId, message))
                }
                .onFailure { error ->
                    if (error is AuthRequiredException) _events.send(ParamsEvent.AuthRequired)
                    _state.value = _state.value.copy(slicing = false, error = error.userMessage())
                }
        }
    }

    fun cancelSlice() {
        sliceJob?.cancel()
        sliceJob = null
        _state.value = _state.value.copy(slicing = false)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}
