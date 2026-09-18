package com.teknoral.parametrik.ui.params

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.teknoral.parametrik.core.Numbers
import com.teknoral.parametrik.domain.logic.PanelEstimator
import com.teknoral.parametrik.domain.model.SourceKind
import com.teknoral.parametrik.ui.common.BlockingProgress
import com.teknoral.parametrik.ui.common.CollapsibleSection
import com.teknoral.parametrik.ui.common.CounterField
import com.teknoral.parametrik.ui.common.ErrorBox
import com.teknoral.parametrik.ui.common.MmField
import com.teknoral.parametrik.ui.common.SegmentedChoice
import com.teknoral.parametrik.ui.common.SwitchRow
import com.teknoral.parametrik.ui.common.WarningBox

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParamsScreen(
    onBack: () -> Unit,
    onSliced: (jobId: Long, message: String?) -> Unit,
    onAuthRequired: () -> Unit,
    viewModel: ParamsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ParamsEvent.Sliced -> onSliced(event.jobId, event.message)
                ParamsEvent.AuthRequired -> onAuthRequired()
            }
        }
    }

    val validation = state.validation

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.detail?.job?.name ?: "Parametreler") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    EstimateRow(state)
                    Button(
                        onClick = viewModel::slice,
                        enabled = !state.loading && !state.slicing && validation.isValid,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                    ) { Text("Dilimle") }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                state.detail == null -> Column(modifier = Modifier.padding(16.dp)) {
                    ErrorBox(state.error ?: "İş bilgisi alınamadı.", onRetry = viewModel::load)
                }

                else -> Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    state.error?.let {
                        Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                            ErrorBox(it)
                        }
                    }
                    validation.errors.forEach { message ->
                        Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                            ErrorBox(message)
                        }
                    }
                    validation.warnings.forEach { message ->
                        Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                            WarningBox(message)
                        }
                    }

                    if (state.sourceKind == SourceKind.IMAGE) {
                        state.imageUrl?.let { url ->
                            AsyncImage(
                                model = url,
                                contentDescription = "Kaynak resim",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .padding(12.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                        }
                        ImageParamsForm(state, viewModel)
                    } else {
                        MeshParamsForm(state, viewModel)
                    }

                    Box(modifier = Modifier.height(24.dp))
                }
            }

            if (state.slicing) {
                BlockingProgress(
                    message = "Dilimleniyor… Bu işlem birkaç saniye sürebilir.",
                    onCancel = viewModel::cancelSlice
                )
            }
        }
    }
}

/** Ağ isteği olmadan, yerel hesapla anlık geri bildirim. */
@Composable
private fun EstimateRow(state: ParamsUiState) {
    val text = if (state.sourceKind == SourceKind.IMAGE) {
        val params = state.imageForm.toParams()
        val count = PanelEstimator.panelCount(params.stackSpan, params.thickness, params.gap)
        val pitch = PanelEstimator.pitch(params.thickness, params.gap)
        "≈ $count panel · adım ${Numbers.format(pitch, 1)} mm"
    } else {
        val params = state.meshForm.toParams()
        if (params.targetLen > 0.0) {
            val count = PanelEstimator.panelCount(params.targetLen, params.thickness, params.gap)
            val pitch = PanelEstimator.pitch(params.thickness, params.gap)
            "≈ $count panel · adım ${Numbers.format(pitch, 1)} mm"
        } else {
            "Adım ${Numbers.format(PanelEstimator.pitch(params.thickness, params.gap), 1)} mm · " +
                "panel sayısı için hedef boy girin"
        }
    }
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun ImageParamsForm(state: ParamsUiState, viewModel: ParamsViewModel) {
    val form = state.imageForm

    CollapsibleSection(title = "İşin Ölçüleri") {
        SwitchRow(
            label = "En-boy oranını koru",
            description = "En girilince yükseklik otomatik hesaplansın.",
            checked = form.keepAspect,
            onCheckedChange = { checked -> viewModel.updateImage { it.copy(keepAspect = checked) } }
        )
        MmField(
            label = "En",
            value = form.imgW,
            onValueChange = { value -> viewModel.updateImage { it.withWidth(value) } }
        )
        MmField(
            label = "Yükseklik",
            value = form.imgH,
            onValueChange = { value -> viewModel.updateImage { it.withHeight(value) } }
        )
        MmField(
            label = "En derin nokta",
            value = form.imgDepth,
            onValueChange = { value -> viewModel.updateImage { it.copy(imgDepth = value) } }
        )
        MmField(
            label = "Taban derinliği",
            value = form.minDepth,
            helper = "En derin noktadan küçük olmalı.",
            isError = form.toParams().minDepth >= form.toParams().imgDepth,
            onValueChange = { value -> viewModel.updateImage { it.copy(minDepth = value) } }
        )
    }

    CollapsibleSection(title = "Paneller") {
        MmField(
            label = "Panel kalınlığı",
            value = form.thickness,
            onValueChange = { value -> viewModel.updateImage { it.copy(thickness = value) } }
        )
        MmField(
            label = "Panel arası boşluk",
            value = form.gap,
            onValueChange = { value -> viewModel.updateImage { it.copy(gap = value) } }
        )
        SegmentedChoice(
            label = "Panel yönü",
            options = listOf("v" to "Dikey", "h" to "Yatay"),
            selected = form.orient,
            onSelected = { value -> viewModel.updateImage { it.copy(orient = value) } }
        )
        SegmentedChoice(
            label = "Biçim",
            options = listOf("single" to "Tek taraflı", "double" to "Çift taraflı"),
            selected = form.shapeMode,
            onSelected = { value -> viewModel.updateImage { it.copy(shapeMode = value) } }
        )
    }

    CollapsibleSection(title = "Görüntü İşleme", initiallyExpanded = false) {
        SwitchRow(
            label = "Koyu bölgeler derin olsun",
            description = "invert",
            checked = form.invert,
            onCheckedChange = { checked -> viewModel.updateImage { it.copy(invert = checked) } }
        )
        SwitchRow(
            label = "Kontrastı otomatik ger",
            description = "normalize",
            checked = form.normalize,
            onCheckedChange = { checked -> viewModel.updateImage { it.copy(normalize = checked) } }
        )
        MmField(
            label = "Yumuşatma (0 – 8)",
            value = form.smooth,
            suffix = "",
            helper = "0 = keskin detay",
            onValueChange = { value -> viewModel.updateImage { it.copy(smooth = value) } }
        )
        MmField(
            label = "Kontur sadeleştirme",
            value = form.simplifyTol,
            onValueChange = { value -> viewModel.updateImage { it.copy(simplifyTol = value) } }
        )
    }

    CollapsibleSection(title = "Montaj Delikleri", initiallyExpanded = false) {
        CounterField(
            label = "Delik adedi",
            value = form.holeCount,
            onValueChange = { value -> viewModel.updateImage { it.copy(holeCount = value) } }
        )
        MmField(
            label = "Delik çapı",
            value = form.holeDia,
            onValueChange = { value -> viewModel.updateImage { it.copy(holeDia = value) } }
        )
    }

    CollapsibleSection(title = "Geçme Çerçeve", initiallyExpanded = false) {
        CounterField(
            label = "Kayıt adedi",
            value = form.frameCount,
            onValueChange = { value -> viewModel.updateImage { it.copy(frameCount = value) } }
        )
        MmField(
            label = "Kayıt kalınlığı",
            value = form.frameT,
            onValueChange = { value -> viewModel.updateImage { it.copy(frameT = value) } }
        )
        MmField(
            label = "Kayıt yüksekliği",
            value = form.frameH,
            onValueChange = { value -> viewModel.updateImage { it.copy(frameH = value) } }
        )
        MmField(
            label = "Geçme payı",
            value = form.frameFit,
            helper = "0,1 – 0,3 mm önerilir.",
            onValueChange = { value -> viewModel.updateImage { it.copy(frameFit = value) } }
        )
    }

    CollapsibleSection(title = "Plaka ve Yerleşim", initiallyExpanded = false) {
        MmField(
            label = "Plaka eni",
            value = form.sheetW,
            onValueChange = { value -> viewModel.updateImage { it.copy(sheetW = value) } }
        )
        MmField(
            label = "Plaka boyu",
            value = form.sheetH,
            onValueChange = { value -> viewModel.updateImage { it.copy(sheetH = value) } }
        )
        MmField(
            label = "Parça arası",
            value = form.partGap,
            imeAction = ImeAction.Done,
            onValueChange = { value -> viewModel.updateImage { it.copy(partGap = value) } }
        )
    }
}

@Composable
private fun MeshParamsForm(state: ParamsUiState, viewModel: ParamsViewModel) {
    val form = state.meshForm

    CollapsibleSection(
        title = "Dilimleme Ekseni",
        subtitle = state.detail?.job?.triCount?.takeIf { it > 0 }?.let { "$it üçgen" }
    ) {
        SegmentedChoice(
            label = "Eksen",
            options = listOf("x" to "X", "y" to "Y", "z" to "Z"),
            selected = form.axis,
            onSelected = { value -> viewModel.updateMesh { it.copy(axis = value) } }
        )
    }

    CollapsibleSection(title = "Paneller") {
        MmField(
            label = "Panel kalınlığı",
            value = form.thickness,
            onValueChange = { value -> viewModel.updateMesh { it.copy(thickness = value) } }
        )
        MmField(
            label = "Panel arası boşluk",
            value = form.gap,
            onValueChange = { value -> viewModel.updateMesh { it.copy(gap = value) } }
        )
        MmField(
            label = "Kontur sadeleştirme",
            value = form.simplifyTol,
            onValueChange = { value -> viewModel.updateMesh { it.copy(simplifyTol = value) } }
        )
    }

    CollapsibleSection(title = "Ölçek") {
        MmField(
            label = "Ölçek çarpanı",
            value = form.scale,
            suffix = "×",
            helper = "Hedef boy 0'dan büyükse ölçek yok sayılır.",
            onValueChange = { value -> viewModel.updateMesh { it.copy(scale = value) } }
        )
        MmField(
            label = "Hedef boy (dilimleme ekseni)",
            value = form.targetLen,
            helper = "0 = kullanma",
            onValueChange = { value -> viewModel.updateMesh { it.copy(targetLen = value) } }
        )
    }

    CollapsibleSection(title = "Montaj Delikleri", initiallyExpanded = false) {
        CounterField(
            label = "Delik adedi",
            value = form.holeCount,
            onValueChange = { value -> viewModel.updateMesh { it.copy(holeCount = value) } }
        )
        MmField(
            label = "Delik çapı",
            value = form.holeDia,
            onValueChange = { value -> viewModel.updateMesh { it.copy(holeDia = value) } }
        )
    }

    CollapsibleSection(title = "Geçme Çerçeve", initiallyExpanded = false) {
        CounterField(
            label = "Kayıt adedi",
            value = form.frameCount,
            onValueChange = { value -> viewModel.updateMesh { it.copy(frameCount = value) } }
        )
        MmField(
            label = "Kayıt kalınlığı",
            value = form.frameT,
            onValueChange = { value -> viewModel.updateMesh { it.copy(frameT = value) } }
        )
        MmField(
            label = "Kayıt yüksekliği",
            value = form.frameH,
            onValueChange = { value -> viewModel.updateMesh { it.copy(frameH = value) } }
        )
        MmField(
            label = "Geçme payı",
            value = form.frameFit,
            helper = "0,1 – 0,3 mm önerilir.",
            onValueChange = { value -> viewModel.updateMesh { it.copy(frameFit = value) } }
        )
    }

    CollapsibleSection(title = "Plaka ve Yerleşim", initiallyExpanded = false) {
        MmField(
            label = "Plaka eni",
            value = form.sheetW,
            onValueChange = { value -> viewModel.updateMesh { it.copy(sheetW = value) } }
        )
        MmField(
            label = "Plaka boyu",
            value = form.sheetH,
            onValueChange = { value -> viewModel.updateMesh { it.copy(sheetH = value) } }
        )
        MmField(
            label = "Parça arası",
            value = form.partGap,
            imeAction = ImeAction.Done,
            onValueChange = { value -> viewModel.updateMesh { it.copy(partGap = value) } }
        )
    }
}
