package com.teknoral.parametrik.ui.jobs

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.teknoral.parametrik.data.media.CropRect
import com.teknoral.parametrik.domain.model.Job
import com.teknoral.parametrik.ui.common.BlockingProgress
import com.teknoral.parametrik.ui.common.ErrorBox

/** Yükleme başlatılmadan önce kullanıcıdan alınan iş adı. */
private data class PendingUpload(
    val uri: Uri,
    val isImage: Boolean,
    val crop: CropRect = CropRect.FULL,
    val suggestedName: String = ""
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun JobListScreen(
    onOpenJob: (Long) -> Unit,
    onOpenCamera: () -> Unit,
    onOpenSettings: () -> Unit,
    onAuthRequired: () -> Unit,
    pendingFromCamera: Pair<Uri, CropRect>?,
    onPendingConsumed: () -> Unit,
    viewModel: JobListViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showNewJobSheet by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<PendingUpload?>(null) }
    var jobToDelete by remember { mutableStateOf<Job?>(null) }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) pending = PendingUpload(uri = uri, isImage = true)
    }

    val documentPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) pending = PendingUpload(uri = uri, isImage = false)
    }

    LaunchedEffect(pendingFromCamera) {
        val incoming = pendingFromCamera ?: return@LaunchedEffect
        pending = PendingUpload(uri = incoming.first, isImage = true, crop = incoming.second)
        onPendingConsumed()
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is JobListEvent.OpenJob -> onOpenJob(event.jobId)
                JobListEvent.AuthRequired -> onAuthRequired()
            }
        }
    }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeMessage()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("İşler") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Ayarlar")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showNewJobSheet = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Yeni iş") }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = { viewModel.load() },
                modifier = Modifier.fillMaxSize()
            ) {
                when {
                    state.loading -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }

                    state.error != null && state.jobs.isEmpty() -> Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        ErrorBox(state.error!!, onRetry = { viewModel.load(initial = true) })
                    }

                    state.jobs.isEmpty() -> Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Henüz iş yok.", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Sağ alttaki düğmeyle fotoğraf ya da 3B model yükleyin.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp, 8.dp, 12.dp, 96.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        state.error?.let { message ->
                            item { ErrorBox(message, onRetry = { viewModel.load(initial = true) }) }
                        }
                        items(state.jobs, key = { it.id }) { job ->
                            JobCard(
                                job = job,
                                onClick = { onOpenJob(job.id) },
                                onLongClick = { jobToDelete = job }
                            )
                        }
                    }
                }
            }

            state.upload?.let { upload ->
                BlockingProgress(
                    message = upload.label,
                    progress = upload.progress,
                    onCancel = viewModel::cancelUpload
                )
            }
        }
    }

    if (showNewJobSheet) {
        NewJobSheet(
            onDismiss = { showNewJobSheet = false },
            onTakePhoto = {
                showNewJobSheet = false
                onOpenCamera()
            },
            onPickImage = {
                showNewJobSheet = false
                photoPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onPickModel = {
                showNewJobSheet = false
                documentPicker.launch(arrayOf("*/*"))
            }
        )
    }

    pending?.let { upload ->
        JobNameDialog(
            isImage = upload.isImage,
            onDismiss = { pending = null },
            onConfirm = { name ->
                pending = null
                if (upload.isImage) viewModel.uploadImage(name, upload.uri, upload.crop)
                else viewModel.uploadMesh(name, upload.uri)
            }
        )
    }

    jobToDelete?.let { job ->
        AlertDialog(
            onDismissRequest = { jobToDelete = null },
            title = { Text("İş silinsin mi?") },
            text = { Text("\"${job.name}\" ve ona ait tüm paneller silinecek. Bu işlem geri alınamaz.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(job)
                    jobToDelete = null
                }) { Text("Sil") }
            },
            dismissButton = {
                TextButton(onClick = { jobToDelete = null }) { Text("Vazgeç") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun JobCard(job: Job, onClick: () -> Unit, onLongClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    job.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                AssistChip(
                    onClick = onClick,
                    label = { Text(job.sourceKind.label) },
                    colors = AssistChipDefaults.assistChipColors(
                        labelColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
            Text(
                buildString {
                    append(if (job.isSliced) "${job.panelCount} panel" else "Bekliyor")
                    if (job.sheetCount > 0) append(" · ${job.sheetCount} plaka")
                    if (job.status.isNotBlank()) append(" · ${job.status}")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
            if (job.createdAt.isNotBlank()) {
                Text(
                    job.createdAt,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun JobNameDialog(
    isImage: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isImage) "Yeni resim işi" else "Yeni 3B model işi") },
        text = {
            Column {
                Text(
                    "İş adı boş bırakılırsa dosya adı kullanılır.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("İş adı") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(name) }) { Text("Yükle") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } }
    )
}
