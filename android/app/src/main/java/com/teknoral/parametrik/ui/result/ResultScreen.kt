package com.teknoral.parametrik.ui.result

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.teknoral.parametrik.core.Numbers
import com.teknoral.parametrik.data.media.SavedFile
import com.teknoral.parametrik.data.repository.DownloadKind
import com.teknoral.parametrik.domain.model.JobSummary
import com.teknoral.parametrik.ui.common.BlockingProgress
import com.teknoral.parametrik.ui.common.ErrorBox
import com.teknoral.parametrik.ui.common.WarningBox
import com.teknoral.parametrik.ui.viewer.Viewer3d

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    onBack: () -> Unit,
    onEditParams: (Long) -> Unit,
    onAuthRequired: () -> Unit,
    viewModel: ResultViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    fun share(file: SavedFile) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = file.mimeType
            putExtra(Intent.EXTRA_STREAM, file.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Dosyayı paylaş"))
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ResultEvent.Downloaded -> share(event.file)
                ResultEvent.AuthRequired -> onAuthRequired()
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
                title = { Text(state.detail?.job?.name ?: "Sonuç") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                actions = {
                    state.lastSaved?.let { file ->
                        IconButton(onClick = { share(file) }) {
                            Icon(Icons.Default.Share, contentDescription = "Paylaş")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                ) {
                    Viewer3d(
                        geometry = state.geometry,
                        modifier = Modifier.fillMaxWidth().height(360.dp)
                    )

                    state.error?.let {
                        Box(modifier = Modifier.padding(12.dp)) {
                            ErrorBox(it, onRetry = viewModel::load)
                        }
                    }

                    val summary = state.detail?.summary
                    if (summary != null) {
                        SummaryGrid(summary)
                        summary.warnings.forEach { warning ->
                            Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                                WarningBox(warning)
                            }
                        }
                    } else if (state.geometry.isEmpty) {
                        Box(modifier = Modifier.padding(12.dp)) {
                            WarningBox("Bu iş henüz dilimlenmedi. Parametreleri girip 'Dilimle' deyin.")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { viewModel.download(DownloadKind.DXF) },
                            enabled = state.downloading == null,
                            modifier = Modifier.weight(1f)
                        ) { Text("DXF İndir") }
                        OutlinedButton(
                            onClick = { viewModel.download(DownloadKind.ZIP) },
                            enabled = state.downloading == null,
                            modifier = Modifier.weight(1f)
                        ) { Text("ZIP İndir") }
                    }

                    TextButton(
                        onClick = { onEditParams(viewModel.jobId) },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    ) { Text("Parametreleri Düzenle") }

                    Box(modifier = Modifier.height(24.dp))
                }
            }

            state.downloading?.let { kind ->
                BlockingProgress(
                    message = "${kind.label} indiriliyor…",
                    onCancel = viewModel::cancelDownload
                )
            }
        }
    }
}

@Composable
private fun SummaryGrid(summary: JobSummary) {
    val items = buildList {
        add("Panel sayısı" to "${summary.panelCount}")
        add("Kalınlık" to "${Numbers.format(summary.thickness, 1)} mm")
        add("Aralık" to "${Numbers.format(summary.gap, 1)} mm")
        add("Adım" to "${Numbers.format(summary.pitch, 1)} mm")
        add("Toplam boy" to "${Numbers.format(summary.stackLen, 1)} mm")
        add("Panel ölçüsü" to "${Numbers.format(summary.profileW, 1)} × ${Numbers.format(summary.profileH, 1)} mm")
        add("Toplam kesim" to "${Numbers.format(summary.cutLenM, 1)} m")
        add("Plaka adedi" to "${summary.sheetCount}")
        if (summary.holeCount > 0) {
            add("Montaj deliği" to "${summary.holeCount} × Ø${Numbers.format(summary.holeDia, 1)} mm")
        }
        if (summary.frameCount > 0) {
            add(
                "Geçme kayıt" to "${summary.frameCount} × ${Numbers.format(summary.frameT, 1)}×" +
                    "${Numbers.format(summary.frameH, 1)} mm · ${Numbers.format(summary.frameLen, 1)} mm"
            )
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        items.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { (label, value) ->
                    SummaryTile(label = label, value = value, modifier = Modifier.weight(1f))
                }
                if (row.size == 1) Box(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SummaryTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
