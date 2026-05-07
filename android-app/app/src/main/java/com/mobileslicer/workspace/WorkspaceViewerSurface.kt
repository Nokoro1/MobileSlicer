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
import androidx.compose.runtime.key
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
import com.mobileslicer.viewer.GcodePreviewDisplayMode
import com.mobileslicer.viewer.PrinterBedSpec
import com.mobileslicer.viewer.TouchModelViewerView
import com.mobileslicer.viewer.ViewerCameraState
import com.mobileslicer.viewer.ViewerModelTransform
import com.mobileslicer.viewer.ViewerPaintSession
import com.mobileslicer.viewer.ViewerPaintStrokePoint
import com.mobileslicer.viewer.ViewerPlateObject
import com.mobileslicer.viewer.ViewerCutPlaneAxis
import com.mobileslicer.viewer.ViewerCutPlaneSession
import com.mobileslicer.viewer.ViewerCutConnectorPoint
import com.mobileslicer.viewer.ViewerCutConnectorKind
import com.mobileslicer.viewer.ViewerCutConnectorShape
import com.mobileslicer.viewer.ViewerCutConnectorStyle
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
internal fun WorkspaceViewerSurface(
    modelFormat: ImportedModelFormat?,
    preparedMesh: StlMesh?,
    printerBed: PrinterBedSpec,
    viewerState: WorkspaceViewerState,
    darkTheme: Boolean,
    accentColor: Int,
    worldColor: Int,
    modelTransform: ViewerModelTransform?,
    plateObjects: List<ViewerPlateObject>,
    previewEngineHandle: Long,
    previewSliceKey: Long,
    gcodePreviewVertexBudget: Long,
    gcodeLayerMin: Long,
    gcodeLayerMax: Long,
    gcodeLayerReloadToken: Long,
    gcodeDisplayMode: GcodePreviewDisplayMode?,
    paintSession: ViewerPaintSession? = null,
    activeStylusPaintOnly: Boolean = false,
    onRuntimeFailureChanged: (com.mobileslicer.viewer.ViewerFailure?) -> Unit,
    onViewerReadyChanged: (Boolean) -> Unit,
    onPreviewRuntimeMetrics: (com.mobileslicer.viewer.GcodePreviewRuntimeMetrics) -> Unit,
    onObjectSelected: (Long?) -> Unit,
    onObjectHitSelected: (com.mobileslicer.viewer.ViewerPickHit?) -> Boolean = { false },
    onObjectDrag: (Long, Float, Float, Boolean) -> Unit = { _, _, _, _ -> },
    onPaintHitTest: (Float, Float) -> Boolean? = { _, _ -> null },
    onPaintStrokeBegin: (ViewerPaintStrokePoint) -> Unit = {},
    onPaintStrokeMove: (ViewerPaintStrokePoint) -> Unit = {},
    onPaintStrokeEnd: (Boolean) -> Unit = {},
    cutSession: WorkspaceCutSession? = null,
    onCutOffsetChanged: (Float) -> Unit = {},
    onCutConnectorPointAdded: (WorkspaceCutConnectorPoint) -> Unit = {},
    onViewerViewChanged: (TouchModelViewerView?) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val configuration = LocalConfiguration.current
    val surfaceLayoutKey = "${configuration.screenWidthDp}x${configuration.screenHeightDp}"
    var viewerView by remember { mutableStateOf<TouchModelViewerView?>(null) }
    var savedCameraState by remember { mutableStateOf<ViewerCameraState?>(null) }
    var paintCursor by remember { mutableStateOf<Offset?>(null) }
    LaunchedEffect(paintSession?.selectedObjectId, paintSession?.mode) {
        paintCursor = null
    }
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        if (modelFormat != ImportedModelFormat.ThreeMf) {
            key(surfaceLayoutKey) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        TouchModelViewerView(context).apply {
                            viewerView = this
                            onViewerViewChanged(this)
                            setPrinterBed(printerBed)
                            setViewerAppearance(darkTheme = darkTheme, accentColor = accentColor, worldColor = worldColor)
                            setFailureListener(onRuntimeFailureChanged)
                            setRenderReadyListener(onViewerReadyChanged)
                            setPreviewRuntimeMetricsListener(onPreviewRuntimeMetrics)
                            setObjectSelectionListener(onObjectSelected)
                            setCutConnectorPointListener { point ->
                                onCutConnectorPointAdded(point.toWorkspaceCutConnectorPoint())
                            }
                            setObjectHitSelectionListener(onObjectHitSelected)
                            setObjectDragListener(
                                movableIds = plateObjects.filter { it.movable }.mapTo(mutableSetOf()) { it.id },
                                listener = onObjectDrag
                            )
                            setPaintSession(paintSession)
                            setActiveStylusPaintOnly(activeStylusPaintOnly)
                            setPaintHitTestListener(onPaintHitTest)
                            setPaintStrokeListener(
                                onBegin = { point ->
                                    paintCursor = Offset(point.x, point.y)
                                    onPaintStrokeBegin(point)
                                },
                                onMove = { point ->
                                    paintCursor = Offset(point.x, point.y)
                                    onPaintStrokeMove(point)
                                },
                                onEnd = { committed ->
                                    paintCursor = null
                                    onPaintStrokeEnd(committed)
                                }
                            )
                            setModelTransform(modelTransform)
                            setPlateObjects(plateObjects)
                            setCutPlaneSession(cutSession?.toViewerCutPlaneSession())
                            setMesh(preparedMesh)
                            setGcodePreviewSourceAndLayerRange(
                                previewEngineHandle,
                                previewSliceKey,
                                gcodePreviewVertexBudget,
                                gcodeLayerMin,
                                gcodeLayerMax,
                                gcodeLayerReloadToken
                            )
                            gcodeDisplayMode?.let(::setGcodeDisplayMode)
                            savedCameraState?.let(::restoreCameraState)
                        }
                    },
                    update = { view ->
                        viewerView = view
                        onViewerViewChanged(view)
                        view.setPrinterBed(printerBed)
                        view.setViewerAppearance(darkTheme = darkTheme, accentColor = accentColor, worldColor = worldColor)
                        view.setFailureListener(onRuntimeFailureChanged)
                        view.setRenderReadyListener(onViewerReadyChanged)
                        view.setPreviewRuntimeMetricsListener(onPreviewRuntimeMetrics)
                        view.setObjectSelectionListener(onObjectSelected)
                        view.setCutConnectorPointListener { point ->
                            onCutConnectorPointAdded(point.toWorkspaceCutConnectorPoint())
                        }
                        view.setObjectHitSelectionListener(onObjectHitSelected)
                        view.setObjectDragListener(
                            movableIds = plateObjects.filter { it.movable }.mapTo(mutableSetOf()) { it.id },
                            listener = onObjectDrag
                        )
                        view.setPaintSession(paintSession)
                        view.setActiveStylusPaintOnly(activeStylusPaintOnly)
                        view.setPaintHitTestListener(onPaintHitTest)
                        view.setPaintStrokeListener(
                            onBegin = { point ->
                                paintCursor = Offset(point.x, point.y)
                                onPaintStrokeBegin(point)
                            },
                            onMove = { point ->
                                paintCursor = Offset(point.x, point.y)
                                onPaintStrokeMove(point)
                            },
                            onEnd = { committed ->
                                paintCursor = null
                                onPaintStrokeEnd(committed)
                            }
                        )
                        view.setModelTransform(modelTransform)
                        view.setPlateObjects(plateObjects)
                        view.setCutPlaneSession(cutSession?.toViewerCutPlaneSession())
                        view.setMesh(preparedMesh)
                        view.setGcodePreviewSourceAndLayerRange(
                            previewEngineHandle,
                            previewSliceKey,
                            gcodePreviewVertexBudget,
                            gcodeLayerMin,
                            gcodeLayerMax,
                            gcodeLayerReloadToken
                        )
                        gcodeDisplayMode?.let(view::setGcodeDisplayMode)
                    }
                )
            }
        }
        if (viewerState !is WorkspaceViewerState.Loaded && viewerState !is WorkspaceViewerState.Preparing) {
            ViewerStatePanel(state = viewerState)
        }
        DisposableEffect(lifecycleOwner, viewerView) {
            val activeViewer = viewerView
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> activeViewer?.onResume()
                    Lifecycle.Event.ON_PAUSE -> activeViewer?.onPause()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                savedCameraState = activeViewer?.captureCameraState() ?: savedCameraState
                activeViewer?.onPause()
                if (activeViewer != null) {
                    onViewerViewChanged(null)
                }
            }
        }
    }
}

private fun WorkspaceCutSession.toViewerCutPlaneSession(): ViewerCutPlaneSession =
    ViewerCutPlaneSession(
        selectedObjectId = selectedObjectId,
        axis = when (axis) {
            WorkspaceCutAxis.X -> ViewerCutPlaneAxis.X
            WorkspaceCutAxis.Y -> ViewerCutPlaneAxis.Y
            WorkspaceCutAxis.Z -> ViewerCutPlaneAxis.Z
            WorkspaceCutAxis.Custom -> ViewerCutPlaneAxis.Custom
        },
        offsetMm = offsetMm,
        rotationXDegrees = rotationXDegrees,
        rotationYDegrees = rotationYDegrees,
        keepUpper = keepUpper,
        keepLower = keepLower,
        connectorKind = when (connectorKind) {
            WorkspaceCutConnectorKind.None -> ViewerCutConnectorKind.None
            WorkspaceCutConnectorKind.Plug -> ViewerCutConnectorKind.Plug
            WorkspaceCutConnectorKind.Dowel -> ViewerCutConnectorKind.Dowel
            WorkspaceCutConnectorKind.Snap -> ViewerCutConnectorKind.Snap
        },
        connectorStyle = when (connectorStyle) {
            WorkspaceCutConnectorStyle.Prism -> ViewerCutConnectorStyle.Prism
            WorkspaceCutConnectorStyle.Frustum -> ViewerCutConnectorStyle.Frustum
        },
        connectorShape = when (connectorShape) {
            WorkspaceCutConnectorShape.Triangle -> ViewerCutConnectorShape.Triangle
            WorkspaceCutConnectorShape.Square -> ViewerCutConnectorShape.Square
            WorkspaceCutConnectorShape.Hexagon -> ViewerCutConnectorShape.Hexagon
            WorkspaceCutConnectorShape.Circle -> ViewerCutConnectorShape.Circle
        },
        connectorDepthMm = connectorDepthMm,
        connectorDepthToleranceMm = connectorDepthToleranceMm,
        connectorSizeMm = connectorSizeMm,
        connectorSizeToleranceMm = connectorSizeToleranceMm,
        connectorRotationDegrees = connectorRotationDegrees,
        connectorSnapBulgePercent = connectorSnapBulgePercent,
        connectorSnapSpacePercent = connectorSnapSpacePercent,
        connectorsEditing = connectorsEditing,
        connectorPoints = connectorPositions.map {
            ViewerCutConnectorPoint(it.xMm, it.yMm, it.zMm)
        }
    )

private fun ViewerCutConnectorPoint.toWorkspaceCutConnectorPoint(): WorkspaceCutConnectorPoint =
    WorkspaceCutConnectorPoint(xMm = xMm, yMm = yMm, zMm = zMm)

private fun ViewerPaintSession.previewPaintColor(): Color =
    when (mode) {
        com.mobileslicer.viewer.ViewerPaintMode.Color ->
            activeColorInt?.let(::Color) ?: Color(0xFFFF7043)
        com.mobileslicer.viewer.ViewerPaintMode.Support ->
            if (action == com.mobileslicer.viewer.ViewerPaintAction.Block) {
                Color(0xFFFF7043)
            } else {
                Color(0xFF88A2FF)
            }
        com.mobileslicer.viewer.ViewerPaintMode.Seam ->
            if (action == com.mobileslicer.viewer.ViewerPaintAction.Block) {
                Color(0xFFFF7043)
            } else {
                Color(0xFFFFAA00)
            }
        com.mobileslicer.viewer.ViewerPaintMode.FuzzySkin ->
            Color(0xFF9C6ADE)
    }
