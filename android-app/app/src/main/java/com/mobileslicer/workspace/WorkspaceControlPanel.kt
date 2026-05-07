package com.mobileslicer.workspace

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.OpenableColumns
import android.util.Log
import com.mobileslicer.CompactWorkspaceBadge
import com.mobileslicer.R
import com.mobileslicer.appBackgroundGradient
import com.mobileslicer.appBodyColor
import com.mobileslicer.appCardColor
import com.mobileslicer.appCardColorMuted
import com.mobileslicer.appMutedColor
import com.mobileslicer.appOutlineColor
import com.mobileslicer.appTitleColor
import com.mobileslicer.profiles.ActiveSlicerConfiguration
import com.mobileslicer.profiles.PrintHostType
import com.mobileslicer.profiles.PrinterProfile
import com.mobileslicer.printerconnection.PrinterUploadAction
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.mobileslicer.viewer.MeshBounds
import com.mobileslicer.viewer.StlMesh
import com.mobileslicer.nativebridge.NativeEngineBridge
import com.mobileslicer.ui.theme.AccentPaletteOption
import com.mobileslicer.viewer.PrinterBedSpec
import com.mobileslicer.viewer.PreviewRangeSuggestion
import com.mobileslicer.viewer.TouchModelViewerView
import com.mobileslicer.viewer.ViewerModelTransform
import com.mobileslicer.viewer.ViewerPlateObject
import com.mobileslicer.ui.theme.MobileSlicerTheme
import com.mobileslicer.ui.theme.PanelAmber
import com.mobileslicer.ui.theme.PanelBlue
import com.mobileslicer.ui.theme.PanelGreen
import com.mobileslicer.ui.theme.PanelLavender
import com.mobileslicer.ui.theme.PanelSlate
import com.mobileslicer.ui.theme.LocalAppDarkTheme
import com.mobileslicer.ui.theme.ThemeModeOption
import com.mobileslicer.ui.theme.WorldViewColorOption
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sqrt
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkspaceControlPanel(
    modelLabel: String,
    printerTitle: String,
    printerBed: PrinterBedSpec,
    modelFormat: ImportedModelFormat?,
    viewerState: WorkspaceViewerState,
    meshSummary: String?,
    importTiming: ModelImportTiming?,
    workspacePreparationTiming: WorkspacePreparationTiming?,
    firstVisibleWorkspaceFrameMs: Long?,
    firstVisiblePreviewFrameMs: Long?,
    activeConfiguration: ActiveSlicerConfiguration,
    workspaceStatus: String,
    workspaceMode: WorkspaceMode,
    sliceInProgress: Boolean,
    sendToPrinterInProgress: Boolean,
    sliceReady: Boolean,
    hasGeneratedGcode: Boolean,
    canSendToPrinter: Boolean,
    sliceSummary: SliceResultSummary?,
    sliceTiming: SlicePipelineTiming?,
    previewLayerCount: Int,
    previewLayerSelection: PreviewLayerSelection,
    maxRangeLayerSpan: Int,
    previewRangeSliderBounds: IntRange?,
    previewRangeChunks: List<PreviewRangeSuggestion>,
    previewRangeChunkIndex: Int,
    printerStatusLabel: String?,
    controlsExpanded: Boolean,
    compactControlsEnabled: Boolean,
    modelTransform: ViewerModelTransform,
    objectCount: Int,
    activePlateLabel: String,
    selectedObjectLabel: String?,
    onPreviewLayerSelectionChanged: (PreviewLayerSelection) -> Unit,
    onPreviewLayerSelectionCommitted: (PreviewLayerSelection) -> Unit,
    onPreviousPreviewRangeChunk: () -> Unit,
    onNextPreviewRangeChunk: () -> Unit,
    onControlsExpandedChange: (Boolean) -> Unit,
    onModelTransformChanged: (ViewerModelTransform?) -> Unit,
    onOpenObjectList: () -> Unit,
    onOpenPlateSettings: () -> Unit,
    onOpenProfiles: () -> Unit,
    onSavePlate: () -> Unit,
    onSlice: () -> Unit,
    onExport: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bodyColor = appBodyColor()
    val outlineColor = appOutlineColor()
    val loadedState = viewerState is WorkspaceViewerState.Loaded
    val compactDock = compactControlsEnabled && !controlsExpanded
    val compactPreview = workspaceMode == WorkspaceMode.Preview && sliceSummary != null && compactDock
    val compactPrepare = workspaceMode == WorkspaceMode.Prepare && compactDock
    val primaryStatus = when {
        viewerState is WorkspaceViewerState.Error -> viewerState.message
        sliceInProgress -> "Slicing model..."
        hasGeneratedGcode -> "G-code ready"
        workspaceStatus.startsWith("Paint updated") -> "Ready"
        else -> workspaceStatus.lineSequence().firstOrNull().orEmpty()
    }
    val performanceSummary = workspacePerformanceSnapshot(
        importTiming = importTiming,
        workspacePreparationTiming = workspacePreparationTiming,
        firstVisibleWorkspaceFrameMs = firstVisibleWorkspaceFrameMs,
        sliceTiming = sliceTiming
    ).compactSummary()
    val bottomDock = compactControlsEnabled && !controlsExpanded
    val modelMetadataLabel = when (viewerState) {
        is WorkspaceViewerState.Loaded -> modelLabel.shortWorkspaceMetadataLabel()
        WorkspaceViewerState.Preparing -> "Preparing"
        WorkspaceViewerState.Unsupported -> "Unsupported"
        WorkspaceViewerState.Empty -> "No model"
        is WorkspaceViewerState.Error -> "Viewer issue"
    }
    Surface(
        modifier = modifier
            .fillMaxWidth(if (bottomDock) 0.70f else 1f)
            .padding(
                horizontal = if (bottomDock) 8.dp else 10.dp,
                vertical = if (bottomDock) 4.dp else 8.dp
            ),
        shape = if (bottomDock) {
            RoundedCornerShape(18.dp)
        } else {
            RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
        },
        color = appCardColor().copy(alpha = 0.72f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, outlineColor.copy(alpha = 0.64f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            appCardColor().copy(alpha = 0.08f),
                            appCardColor().copy(alpha = 0.18f)
                        )
                    )
                )
                .padding(
                    horizontal = 12.dp,
                    vertical = if (bottomDock) 5.dp else 9.dp
                ),
            verticalArrangement = Arrangement.spacedBy(if (bottomDock) 2.dp else 6.dp)
        ) {
            if (compactPreview) {
                if (!printerStatusLabel.isNullOrBlank()) {
                    CompactWorkspaceBadge(label = printerStatusLabel)
                }
                PreviewRangeChunkNavigator(
                    chunks = previewRangeChunks,
                    currentIndex = previewRangeChunkIndex,
                    onPrevious = onPreviousPreviewRangeChunk,
                    onNext = onNextPreviewRangeChunk,
                    compact = true
                )
                CompactPreviewRangeSlider(
                    layerCount = previewLayerCount,
                    selection = previewLayerSelection,
                    maxRangeLayerSpan = maxRangeLayerSpan,
                    rangeSliderBounds = previewRangeSliderBounds,
                    onSelectionChanged = onPreviewLayerSelectionChanged,
                    onSelectionCommitted = onPreviewLayerSelectionCommitted,
                    onExpand = { onControlsExpandedChange(true) }
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxWidth(0.30f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (loadedState) {
                            CompactWorkspaceBadge(
                                label = printerTitle.shortWorkspaceMetadataLabel(),
                                emphasized = true
                            )
                        }
                    }
                    TextButton(
                        onClick = onOpenPlateSettings,
                        modifier = Modifier.align(Alignment.Center),
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
                    ) {
                        CompactWorkspaceBadge(
                            label = activePlateLabel,
                            emphasized = true
                        )
                    }
                    Row(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxWidth(0.34f),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = onOpenObjectList,
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
                        ) {
                            CompactWorkspaceBadge(
                                label = modelMetadataLabel,
                                emphasized = true
                            )
                        }
                    }
                    if (compactControlsEnabled) {
                        TextButton(
                            onClick = { onControlsExpandedChange(!controlsExpanded) },
                            modifier = Modifier.align(Alignment.CenterEnd),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                        ) {
                            Text(if (controlsExpanded) "Hide" else "Menu")
                        }
                    }
                }
            }
            if (!compactPreview && !compactPrepare && primaryStatus.isNotBlank() && (viewerState !is WorkspaceViewerState.Loaded || sliceInProgress)) {
                Text(
                    text = primaryStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = bodyColor,
                    maxLines = if (loadedState) 1 else 2
                )
            }
            if (!compactPreview && performanceSummary.isNotBlank()) {
                Text(
                    text = performanceSummary,
                    style = MaterialTheme.typography.labelSmall,
                    color = bodyColor
                )
            }
            if (workspaceMode == WorkspaceMode.Preview && sliceSummary != null) {
                if (!compactPreview) {
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = outlineColor.copy(alpha = 0.42f)
                    )
                    PreviewRangeChunkNavigator(
                        chunks = previewRangeChunks,
                        currentIndex = previewRangeChunkIndex,
                        onPrevious = onPreviousPreviewRangeChunk,
                        onNext = onNextPreviewRangeChunk,
                        compact = false
                    )
                    PreviewLayerControls(
                        layerCount = previewLayerCount,
                        selection = previewLayerSelection,
                        sliceSummary = sliceSummary,
                        maxRangeLayerSpan = maxRangeLayerSpan,
                        rangeSliderBounds = previewRangeSliderBounds,
                        onSelectionChanged = onPreviewLayerSelectionChanged,
                        onSelectionCommitted = onPreviewLayerSelectionCommitted
                    )
                }
            } else if (hasGeneratedGcode && sliceSummary != null) {
                HorizontalDivider(
                    thickness = 1.dp,
                    color = outlineColor.copy(alpha = 0.42f)
                )
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = sliceSummary.prepareMetricsLine(),
                        style = MaterialTheme.typography.labelSmall,
                        color = bodyColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (!compactPreview && !compactPrepare && sliceInProgress) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(999.dp)),
                        trackColor = appCardColorMuted().copy(alpha = 0.46f)
                    )
                    Text(
                        text = "Slicing model...",
                        style = MaterialTheme.typography.labelSmall,
                        color = bodyColor
                    )
                }
            }
            if (!compactPreview && !compactPrepare && workspaceMode == WorkspaceMode.Prepare) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onOpenProfiles,
                        enabled = !sliceInProgress,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Open profiles")
                    }
                    Button(
                        onClick = onSavePlate,
                        enabled = objectCount > 0 && !sliceInProgress,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Save plate")
                    }
                }
            }
            if (!compactPreview && !compactPrepare) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onSlice,
                        enabled = sliceReady && !sliceInProgress && !sendToPrinterInProgress,
                        modifier = Modifier.weight(1.1f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(if (sliceInProgress) "Slicing..." else "Slice")
                    }
                    Button(
                        onClick = onExport,
                        enabled = hasGeneratedGcode && !sliceInProgress && !sendToPrinterInProgress,
                        modifier = Modifier.weight(0.95f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Export")
                    }
                    Button(
                        onClick = onShare,
                        enabled = hasGeneratedGcode && !sliceInProgress && !sendToPrinterInProgress,
                        modifier = Modifier.weight(0.95f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Share")
                    }
                }
            }
            if (!loadedState && viewerState !is WorkspaceViewerState.Error) {
                HorizontalDivider(
                    thickness = 1.dp,
                    color = outlineColor.copy(alpha = 0.6f)
                )
            }
        }
    }
}

private fun String.shortWorkspaceMetadataLabel(): String =
    if (length <= 12) this else take(12) + "..."

private fun String.shortWorkspaceObjectLabel(): String = shortWorkspaceMetadataLabel()

@Composable
private fun PreviewRangeChunkNavigator(
    chunks: List<PreviewRangeSuggestion>,
    currentIndex: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    compact: Boolean
) {
    if (chunks.size <= 1) {
        return
    }
    val current = chunks.getOrNull(currentIndex.coerceIn(0, chunks.lastIndex)) ?: return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = onPrevious,
            modifier = Modifier.size(if (compact) 42.dp else 44.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            contentPadding = PaddingValues(0.dp)
        ) {
            Text("<")
        }
        Text(
            text = "Range ${currentIndex + 1}/${chunks.size} • ${previewChunkRangeLabel(current)}",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelSmall,
            color = appBodyColor(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Button(
            onClick = onNext,
            modifier = Modifier.size(if (compact) 42.dp else 44.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            contentPadding = PaddingValues(0.dp)
        ) {
            Text(">")
        }
    }
}

private fun previewChunkRangeLabel(chunk: PreviewRangeSuggestion): String =
    if (chunk.startLayer == chunk.endLayer) {
        "L${chunk.startLayer}"
    } else {
        "L${chunk.startLayer}-${chunk.endLayer}"
    }
