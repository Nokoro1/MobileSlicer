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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.unit.Dp
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
import com.mobileslicer.viewer.TouchModelViewerView
import com.mobileslicer.viewer.ViewerModelTransform
import com.mobileslicer.viewer.ViewerPaintMode
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
internal fun TransformPopoverContent(
    printerBed: PrinterBedSpec,
    transform: ViewerModelTransform,
    selectedTab: TransformToolTab,
    onSelectedTabChanged: (TransformToolTab) -> Unit,
    onTransformChanged: (ViewerModelTransform?) -> Unit,
    onAutoOrientObjects: () -> Unit,
    onAutoArrangeObjects: (Boolean) -> Unit,
    onLayFaceRequested: () -> Unit,
    onPaintModeSelected: (ViewerPaintMode) -> Unit,
    modifier: Modifier = Modifier,
    onSplitToObjectsRequested: () -> Unit = {},
    onSplitToPartsRequested: () -> Unit = {},
    cutBounds: MeshBounds? = null,
    cutOffsetOverride: Float? = null,
    onCutPreviewChanged: (WorkspaceCutRequest?) -> Unit = {},
    onCutRequested: (WorkspaceCutRequest) -> Unit = {},
    showTabRow: Boolean = true,
    showControls: Boolean = true,
    compactLayout: Boolean = false
) {
    val titleColor = appTitleColor()
    val outlineColor = appOutlineColor()
    var editingField by remember { mutableStateOf<TransformNumericField?>(null) }
    var allowArrangeRotation by rememberSaveable { mutableStateOf(false) }
    val initialCutHeight = cutBounds?.let { (it.minZ + it.maxZ) * 0.5f } ?: 0f
    var cutHeightMm by rememberSaveable(cutBounds?.minZ, cutBounds?.maxZ) { mutableFloatStateOf(initialCutHeight) }
    var cutRotationXDegrees by rememberSaveable { mutableFloatStateOf(0f) }
    var cutRotationYDegrees by rememberSaveable { mutableFloatStateOf(0f) }
    var editingCutField by remember { mutableStateOf<CutNumericField?>(null) }
    var cutMode by rememberSaveable { mutableStateOf(WorkspaceCutMode.Plane) }
    var cutKeepUpper by rememberSaveable { mutableStateOf(true) }
    var cutKeepLower by rememberSaveable { mutableStateOf(true) }
    var cutKeepAsParts by rememberSaveable { mutableStateOf(false) }
    var cutFlipUpper by rememberSaveable { mutableStateOf(false) }
    var cutFlipLower by rememberSaveable { mutableStateOf(false) }
    var cutPlaceOnCutUpper by rememberSaveable { mutableStateOf(true) }
    var cutPlaceOnCutLower by rememberSaveable { mutableStateOf(false) }
    var cutConnectorKind by rememberSaveable { mutableStateOf(WorkspaceCutConnectorKind.None) }
    var cutConnectorsEditing by rememberSaveable { mutableStateOf(false) }
    var cutConnectorStyle by rememberSaveable { mutableStateOf(WorkspaceCutConnectorStyle.Prism) }
    var cutConnectorShape by rememberSaveable { mutableStateOf(WorkspaceCutConnectorShape.Circle) }
    var cutConnectorDepthMm by rememberSaveable { mutableFloatStateOf(3f) }
    var cutConnectorDepthToleranceMm by rememberSaveable { mutableFloatStateOf(0.1f) }
    var cutConnectorSizeMm by rememberSaveable { mutableFloatStateOf(2.5f) }
    var cutConnectorSizeToleranceMm by rememberSaveable { mutableFloatStateOf(0f) }
    var cutConnectorRotationDegrees by rememberSaveable { mutableFloatStateOf(0f) }
    var cutConnectorSnapBulgePercent by rememberSaveable { mutableFloatStateOf(15f) }
    var cutConnectorSnapSpacePercent by rememberSaveable { mutableFloatStateOf(30f) }
    var grooveDepthMm by rememberSaveable { mutableFloatStateOf(2f) }
    var grooveWidthMm by rememberSaveable { mutableFloatStateOf(8f) }
    var grooveFlapAngleDegrees by rememberSaveable { mutableFloatStateOf(60f) }
    var grooveAngleDegrees by rememberSaveable { mutableFloatStateOf(0f) }
    var grooveDepthToleranceMm by rememberSaveable { mutableFloatStateOf(0.1f) }
    var grooveWidthToleranceMm by rememberSaveable { mutableFloatStateOf(0.1f) }
    LaunchedEffect(cutOffsetOverride) {
        cutOffsetOverride?.let { cutHeightMm = it }
    }
    LaunchedEffect(
        selectedTab,
        cutHeightMm,
        cutRotationXDegrees,
        cutRotationYDegrees,
        cutMode,
        cutKeepUpper,
        cutKeepLower,
        cutKeepAsParts,
        cutFlipUpper,
        cutFlipLower,
        cutPlaceOnCutUpper,
        cutPlaceOnCutLower,
        cutConnectorKind,
        cutConnectorStyle,
        cutConnectorShape,
        cutConnectorDepthMm,
        cutConnectorDepthToleranceMm,
        cutConnectorSizeMm,
        cutConnectorSizeToleranceMm,
        cutConnectorRotationDegrees,
        cutConnectorSnapBulgePercent,
        cutConnectorSnapSpacePercent,
        cutConnectorsEditing,
        grooveDepthMm,
        grooveWidthMm,
        grooveFlapAngleDegrees,
        grooveAngleDegrees,
        grooveDepthToleranceMm,
        grooveWidthToleranceMm,
        cutBounds
    ) {
        if (showControls && selectedTab == TransformToolTab.Cut && cutBounds != null) {
            onCutPreviewChanged(
                WorkspaceCutRequest(
                    axis = WorkspaceCutAxis.Z,
                    heightMm = cutHeightMm,
                    rotationXDegrees = cutRotationXDegrees,
                    rotationYDegrees = cutRotationYDegrees,
                    mode = cutMode,
                    keepUpper = cutKeepUpper,
                    keepLower = cutKeepLower,
                    keepAsParts = cutKeepAsParts,
                    flipUpper = cutFlipUpper,
                    flipLower = cutFlipLower,
                    placeOnCutUpper = cutPlaceOnCutUpper,
                    placeOnCutLower = cutPlaceOnCutLower,
                    connectorKind = cutConnectorKind,
                    connectorStyle = cutConnectorStyle,
                    connectorShape = cutConnectorShape,
                    connectorDepthMm = if (cutConnectorKind == WorkspaceCutConnectorKind.Snap) {
                        cutConnectorDepthMm.coerceAtLeast(cutConnectorSizeMm)
                    } else {
                        cutConnectorDepthMm
                    },
                    connectorDepthToleranceMm = cutConnectorDepthToleranceMm,
                    connectorSizeMm = cutConnectorSizeMm,
                    connectorSizeToleranceMm = cutConnectorSizeToleranceMm,
                    connectorRotationDegrees = cutConnectorRotationDegrees,
                    connectorSnapBulgePercent = cutConnectorSnapBulgePercent,
                    connectorSnapSpacePercent = cutConnectorSnapSpacePercent,
                    connectorsEditing = cutConnectorsEditing,
                    grooveDepthMm = grooveDepthMm,
                    grooveWidthMm = grooveWidthMm,
                    grooveFlapAngleDegrees = grooveFlapAngleDegrees,
                    grooveAngleDegrees = grooveAngleDegrees,
                    grooveDepthToleranceMm = grooveDepthToleranceMm,
                    grooveWidthToleranceMm = grooveWidthToleranceMm
                )
            )
        } else {
            onCutPreviewChanged(null)
        }
    }
    fun update(next: ViewerModelTransform) {
        onTransformChanged(
            next.copy(
                centerXmm = next.centerXmm.coerceIn(0f, printerBed.widthMm),
                centerYmm = next.centerYmm.coerceIn(0f, printerBed.depthMm),
                rotationXDegrees = normalizeDegrees(next.rotationXDegrees),
                rotationYDegrees = normalizeDegrees(next.rotationYDegrees),
                rotationZDegrees = normalizeDegrees(next.rotationZDegrees),
                uniformScale = next.uniformScale.coerceIn(0.05f, 20f)
            )
        )
    }
    fun runAutoOrient() {
        onAutoOrientObjects()
    }
    fun runAutoArrange() {
        onAutoArrangeObjects(allowArrangeRotation)
    }
    fun rotateBy(
        xDegrees: Float = 0f,
        yDegrees: Float = 0f,
        zDegrees: Float = 0f
    ) {
        update(
            transform.copy(
                rotationXDegrees = transform.rotationXDegrees + xDegrees,
                rotationYDegrees = transform.rotationYDegrees + yDegrees,
                rotationZDegrees = transform.rotationZDegrees + zDegrees,
                orientationMatrix = null
            )
        )
    }

    val panelMaxHeight: Dp = when {
        !showControls -> Dp.Unspecified
        selectedTab == TransformToolTab.Cut -> if (compactLayout) 220.dp else 320.dp
        else -> Dp.Unspecified
    }
    val panelModifier = modifier
        .fillMaxWidth()
        .then(if (panelMaxHeight != Dp.Unspecified) Modifier.heightIn(max = panelMaxHeight) else Modifier)
    Surface(
        modifier = panelModifier,
        shape = RoundedCornerShape(17.dp),
        color = appCardColor().copy(alpha = if (selectedTab == TransformToolTab.Cut) 0.80f else 0.88f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, outlineColor.copy(alpha = 0.58f))
    ) {
        Column(
            modifier = Modifier
                .padding(
                    start = if (compactLayout) 10.dp else 16.dp,
                    top = if (compactLayout) 6.dp else 9.dp,
                    end = if (compactLayout) 10.dp else 16.dp,
                    bottom = if (compactLayout) 8.dp else 13.dp
                ),
            verticalArrangement = Arrangement.spacedBy(if (compactLayout) 5.dp else 8.dp)
        ) {
            if (showTabRow) {
                                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(if (compactLayout) 5.dp else 7.dp)
                ) {
                    TransformToolTab.values().forEach { tab ->
                        val selected = selectedTab == tab
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(if (compactLayout) 31.dp else 35.dp)
                                .clip(RoundedCornerShape(if (compactLayout) 9.dp else 10.dp))
                                .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.92f) else appCardColorMuted().copy(alpha = 0.72f))
                                .border(1.dp, outlineColor.copy(alpha = if (selected) 0.18f else 0.52f), RoundedCornerShape(if (compactLayout) 9.dp else 10.dp))
                                .clickable { onSelectedTabChanged(tab) },
                            contentAlignment = Alignment.Center
                        ) {
                            TransformToolIcon(
                                type = tab,
                                tint = if (selected) MaterialTheme.colorScheme.onPrimary else titleColor,
                                modifier = Modifier.size(if (compactLayout) 18.dp else 21.dp)
                            )
                        }
                    }
                }
            }
            if (showControls) {
                TransformToolPanel(
                    selectedTab = selectedTab,
                    printerBed = printerBed,
                    transform = transform,
                    compactLayout = compactLayout,
                    allowArrangeRotation = allowArrangeRotation,
                    onAllowArrangeRotationChanged = { allowArrangeRotation = it },
                    cutBounds = cutBounds,
                    cutHeightMm = cutHeightMm,
                    onCutHeightChanged = { cutHeightMm = it },
                    cutRotationXDegrees = cutRotationXDegrees,
                    onCutRotationXChanged = { cutRotationXDegrees = it },
                    cutRotationYDegrees = cutRotationYDegrees,
                    onCutRotationYChanged = { cutRotationYDegrees = it },
                    cutMode = cutMode,
                    onCutModeChanged = { cutMode = it },
                    cutKeepUpper = cutKeepUpper,
                    onCutKeepUpperChanged = { cutKeepUpper = it },
                    cutKeepLower = cutKeepLower,
                    onCutKeepLowerChanged = { cutKeepLower = it },
                    cutKeepAsParts = cutKeepAsParts,
                    onCutKeepAsPartsChanged = { cutKeepAsParts = it },
                    cutFlipUpper = cutFlipUpper,
                    onCutFlipUpperChanged = { cutFlipUpper = it },
                    cutFlipLower = cutFlipLower,
                    onCutFlipLowerChanged = { cutFlipLower = it },
                    cutPlaceOnCutUpper = cutPlaceOnCutUpper,
                    onCutPlaceOnCutUpperChanged = { cutPlaceOnCutUpper = it },
                    cutPlaceOnCutLower = cutPlaceOnCutLower,
                    onCutPlaceOnCutLowerChanged = { cutPlaceOnCutLower = it },
                    cutConnectorKind = cutConnectorKind,
                    onCutConnectorKindChanged = { cutConnectorKind = it },
                    cutConnectorsEditing = cutConnectorsEditing,
                    onCutConnectorsEditingChanged = { cutConnectorsEditing = it },
                    cutConnectorStyle = cutConnectorStyle,
                    onCutConnectorStyleChange = { cutConnectorStyle = it },
                    cutConnectorShape = cutConnectorShape,
                    onCutConnectorShapeChange = { cutConnectorShape = it },
                    cutConnectorDepthMm = cutConnectorDepthMm,
                    onCutConnectorDepthChange = { cutConnectorDepthMm = it },
                    cutConnectorDepthToleranceMm = cutConnectorDepthToleranceMm,
                    onCutConnectorDepthToleranceChange = { cutConnectorDepthToleranceMm = it },
                    cutConnectorSizeMm = cutConnectorSizeMm,
                    onCutConnectorSizeChange = { cutConnectorSizeMm = it },
                    cutConnectorSizeToleranceMm = cutConnectorSizeToleranceMm,
                    onCutConnectorSizeToleranceChange = { cutConnectorSizeToleranceMm = it },
                    cutConnectorRotationDegrees = cutConnectorRotationDegrees,
                    onCutConnectorRotationChange = { cutConnectorRotationDegrees = it },
                    cutConnectorSnapBulgePercent = cutConnectorSnapBulgePercent,
                    onCutConnectorSnapBulgeChange = { cutConnectorSnapBulgePercent = it },
                    cutConnectorSnapSpacePercent = cutConnectorSnapSpacePercent,
                    onCutConnectorSnapSpaceChange = { cutConnectorSnapSpacePercent = it },
                    grooveDepthMm = grooveDepthMm,
                    onGrooveDepthChange = { grooveDepthMm = it },
                    grooveWidthMm = grooveWidthMm,
                    onGrooveWidthChange = { grooveWidthMm = it },
                    grooveFlapAngleDegrees = grooveFlapAngleDegrees,
                    onGrooveFlapAngleChange = { grooveFlapAngleDegrees = it },
                    grooveAngleDegrees = grooveAngleDegrees,
                    onGrooveAngleChange = { grooveAngleDegrees = it },
                    grooveDepthToleranceMm = grooveDepthToleranceMm,
                    onGrooveDepthToleranceChange = { grooveDepthToleranceMm = it },
                    grooveWidthToleranceMm = grooveWidthToleranceMm,
                    onGrooveWidthToleranceChange = { grooveWidthToleranceMm = it },
                    onEditTransformField = { editingField = it },
                    onEditCutField = { editingCutField = it },
                    onTransformUpdate = ::update,
                    onRotateBy = ::rotateBy,
                    onAutoOrientObjects = ::runAutoOrient,
                    onAutoArrangeObjects = ::runAutoArrange,
                    onLayFaceRequested = onLayFaceRequested,
                    onPaintModeSelected = onPaintModeSelected,
                    onSplitToObjectsRequested = onSplitToObjectsRequested,
                    onSplitToPartsRequested = onSplitToPartsRequested,
                    onCutRequested = onCutRequested
                )
            }
        }
    }
    editingField?.let { field ->
        TransformValueDialog(
            field = field,
            transform = transform,
            printerBed = printerBed,
            onDismiss = { editingField = null },
            onApply = { value ->
                when (field) {
                    TransformNumericField.MoveX -> update(transform.copy(centerXmm = value))
                    TransformNumericField.MoveY -> update(transform.copy(centerYmm = value))
                    TransformNumericField.RotateX -> update(transform.copy(rotationXDegrees = value, orientationMatrix = null))
                    TransformNumericField.RotateY -> update(transform.copy(rotationYDegrees = value, orientationMatrix = null))
                    TransformNumericField.RotateZ -> update(transform.copy(rotationZDegrees = value, orientationMatrix = null))
                    TransformNumericField.Scale -> update(transform.copy(uniformScale = value / 100f))
                }
                editingField = null
            }
        )
    }
    editingCutField?.let { field ->
        CutValueDialog(
            field = field,
            cutHeightMm = cutHeightMm,
            cutRotationXDegrees = cutRotationXDegrees,
            cutRotationYDegrees = cutRotationYDegrees,
            cutRange = (cutBounds?.minZ ?: 0f)..(cutBounds?.maxZ ?: 1f),
            onDismiss = { editingCutField = null },
            onApply = { value ->
                when (field) {
                    CutNumericField.PositionZ -> cutHeightMm = value
                    CutNumericField.RotationX -> cutRotationXDegrees = normalizeDegrees(value)
                    CutNumericField.RotationY -> cutRotationYDegrees = normalizeDegrees(value)
                }
                editingCutField = null
            }
        )
    }
}

@Composable
private fun TransformToolPanel(
    selectedTab: TransformToolTab,
    printerBed: PrinterBedSpec,
    transform: ViewerModelTransform,
    compactLayout: Boolean,
    allowArrangeRotation: Boolean,
    onAllowArrangeRotationChanged: (Boolean) -> Unit,
    cutBounds: MeshBounds?,
    cutHeightMm: Float,
    onCutHeightChanged: (Float) -> Unit,
    cutRotationXDegrees: Float,
    onCutRotationXChanged: (Float) -> Unit,
    cutRotationYDegrees: Float,
    onCutRotationYChanged: (Float) -> Unit,
    cutMode: WorkspaceCutMode,
    onCutModeChanged: (WorkspaceCutMode) -> Unit,
    cutKeepUpper: Boolean,
    onCutKeepUpperChanged: (Boolean) -> Unit,
    cutKeepLower: Boolean,
    onCutKeepLowerChanged: (Boolean) -> Unit,
    cutKeepAsParts: Boolean,
    onCutKeepAsPartsChanged: (Boolean) -> Unit,
    cutFlipUpper: Boolean,
    onCutFlipUpperChanged: (Boolean) -> Unit,
    cutFlipLower: Boolean,
    onCutFlipLowerChanged: (Boolean) -> Unit,
    cutPlaceOnCutUpper: Boolean,
    onCutPlaceOnCutUpperChanged: (Boolean) -> Unit,
    cutPlaceOnCutLower: Boolean,
    onCutPlaceOnCutLowerChanged: (Boolean) -> Unit,
    cutConnectorKind: WorkspaceCutConnectorKind,
    onCutConnectorKindChanged: (WorkspaceCutConnectorKind) -> Unit,
    cutConnectorsEditing: Boolean,
    onCutConnectorsEditingChanged: (Boolean) -> Unit,
    cutConnectorStyle: WorkspaceCutConnectorStyle,
    onCutConnectorStyleChange: (WorkspaceCutConnectorStyle) -> Unit,
    cutConnectorShape: WorkspaceCutConnectorShape,
    onCutConnectorShapeChange: (WorkspaceCutConnectorShape) -> Unit,
    cutConnectorDepthMm: Float,
    onCutConnectorDepthChange: (Float) -> Unit,
    cutConnectorDepthToleranceMm: Float,
    onCutConnectorDepthToleranceChange: (Float) -> Unit,
    cutConnectorSizeMm: Float,
    onCutConnectorSizeChange: (Float) -> Unit,
    cutConnectorSizeToleranceMm: Float,
    onCutConnectorSizeToleranceChange: (Float) -> Unit,
    cutConnectorRotationDegrees: Float,
    onCutConnectorRotationChange: (Float) -> Unit,
    cutConnectorSnapBulgePercent: Float,
    onCutConnectorSnapBulgeChange: (Float) -> Unit,
    cutConnectorSnapSpacePercent: Float,
    onCutConnectorSnapSpaceChange: (Float) -> Unit,
    grooveDepthMm: Float,
    onGrooveDepthChange: (Float) -> Unit,
    grooveWidthMm: Float,
    onGrooveWidthChange: (Float) -> Unit,
    grooveFlapAngleDegrees: Float,
    onGrooveFlapAngleChange: (Float) -> Unit,
    grooveAngleDegrees: Float,
    onGrooveAngleChange: (Float) -> Unit,
    grooveDepthToleranceMm: Float,
    onGrooveDepthToleranceChange: (Float) -> Unit,
    grooveWidthToleranceMm: Float,
    onGrooveWidthToleranceChange: (Float) -> Unit,
    onEditTransformField: (TransformNumericField) -> Unit,
    onEditCutField: (CutNumericField) -> Unit,
    onTransformUpdate: (ViewerModelTransform) -> Unit,
    onRotateBy: (Float, Float, Float) -> Unit,
    onAutoOrientObjects: () -> Unit,
    onAutoArrangeObjects: () -> Unit,
    onLayFaceRequested: () -> Unit,
    onPaintModeSelected: (ViewerPaintMode) -> Unit,
    onSplitToObjectsRequested: () -> Unit,
    onSplitToPartsRequested: () -> Unit,
    onCutRequested: (WorkspaceCutRequest) -> Unit
) {
    when (selectedTab) {
        TransformToolTab.Move -> TransformMoveToolPanel(
            printerBed = printerBed,
            transform = transform,
            onTransformUpdate = onTransformUpdate,
            onEditTransformField = onEditTransformField
        )
        TransformToolTab.Rotate -> TransformRotateToolPanel(
            transform = transform,
            onTransformUpdate = onTransformUpdate,
            onRotateBy = onRotateBy,
            onEditTransformField = onEditTransformField
        )
        TransformToolTab.Scale -> TransformScaleToolPanel(
            transform = transform,
            onTransformUpdate = onTransformUpdate,
            onEditTransformField = onEditTransformField
        )
        TransformToolTab.Split -> TransformSplitToolPanel(
            compactLayout = compactLayout,
            onSplitToObjectsRequested = onSplitToObjectsRequested,
            onSplitToPartsRequested = onSplitToPartsRequested
        )
        TransformToolTab.Cut -> TransformCutToolPanel(
            compactLayout = compactLayout,
            cutBounds = cutBounds,
            cutHeightMm = cutHeightMm,
            onCutHeightChanged = onCutHeightChanged,
            cutRotationXDegrees = cutRotationXDegrees,
            onCutRotationXChanged = onCutRotationXChanged,
            cutRotationYDegrees = cutRotationYDegrees,
            onCutRotationYChanged = onCutRotationYChanged,
            cutMode = cutMode,
            onCutModeChanged = onCutModeChanged,
            cutKeepUpper = cutKeepUpper,
            onCutKeepUpperChanged = onCutKeepUpperChanged,
            cutKeepLower = cutKeepLower,
            onCutKeepLowerChanged = onCutKeepLowerChanged,
            cutKeepAsParts = cutKeepAsParts,
            onCutKeepAsPartsChanged = onCutKeepAsPartsChanged,
            cutFlipUpper = cutFlipUpper,
            onCutFlipUpperChanged = onCutFlipUpperChanged,
            cutFlipLower = cutFlipLower,
            onCutFlipLowerChanged = onCutFlipLowerChanged,
            cutPlaceOnCutUpper = cutPlaceOnCutUpper,
            onCutPlaceOnCutUpperChanged = onCutPlaceOnCutUpperChanged,
            cutPlaceOnCutLower = cutPlaceOnCutLower,
            onCutPlaceOnCutLowerChanged = onCutPlaceOnCutLowerChanged,
            cutConnectorKind = cutConnectorKind,
            onCutConnectorKindChanged = onCutConnectorKindChanged,
            cutConnectorsEditing = cutConnectorsEditing,
            onCutConnectorsEditingChanged = onCutConnectorsEditingChanged,
            cutConnectorStyle = cutConnectorStyle,
            onCutConnectorStyleChange = onCutConnectorStyleChange,
            cutConnectorShape = cutConnectorShape,
            onCutConnectorShapeChange = onCutConnectorShapeChange,
            cutConnectorDepthMm = cutConnectorDepthMm,
            onCutConnectorDepthChange = onCutConnectorDepthChange,
            cutConnectorDepthToleranceMm = cutConnectorDepthToleranceMm,
            onCutConnectorDepthToleranceChange = onCutConnectorDepthToleranceChange,
            cutConnectorSizeMm = cutConnectorSizeMm,
            onCutConnectorSizeChange = onCutConnectorSizeChange,
            cutConnectorSizeToleranceMm = cutConnectorSizeToleranceMm,
            onCutConnectorSizeToleranceChange = onCutConnectorSizeToleranceChange,
            cutConnectorRotationDegrees = cutConnectorRotationDegrees,
            onCutConnectorRotationChange = onCutConnectorRotationChange,
            cutConnectorSnapBulgePercent = cutConnectorSnapBulgePercent,
            onCutConnectorSnapBulgeChange = onCutConnectorSnapBulgeChange,
            cutConnectorSnapSpacePercent = cutConnectorSnapSpacePercent,
            onCutConnectorSnapSpaceChange = onCutConnectorSnapSpaceChange,
            grooveDepthMm = grooveDepthMm,
            onGrooveDepthChange = onGrooveDepthChange,
            grooveWidthMm = grooveWidthMm,
            onGrooveWidthChange = onGrooveWidthChange,
            grooveFlapAngleDegrees = grooveFlapAngleDegrees,
            onGrooveFlapAngleChange = onGrooveFlapAngleChange,
            grooveAngleDegrees = grooveAngleDegrees,
            onGrooveAngleChange = onGrooveAngleChange,
            grooveDepthToleranceMm = grooveDepthToleranceMm,
            onGrooveDepthToleranceChange = onGrooveDepthToleranceChange,
            grooveWidthToleranceMm = grooveWidthToleranceMm,
            onGrooveWidthToleranceChange = onGrooveWidthToleranceChange,
            onEditCutField = onEditCutField,
            onCutRequested = onCutRequested
        )
        TransformToolTab.AutoOrient -> TransformAutoOrientToolPanel(
            compactLayout = compactLayout,
            onAutoOrientObjects = onAutoOrientObjects,
            onLayFaceRequested = onLayFaceRequested
        )
        TransformToolTab.Paint -> TransformPaintToolPanel(
            compactLayout = compactLayout,
            onPaintModeSelected = onPaintModeSelected
        )
        TransformToolTab.AutoArrange -> TransformAutoArrangeToolPanel(
            compactLayout = compactLayout,
            allowArrangeRotation = allowArrangeRotation,
            onAllowArrangeRotationChanged = onAllowArrangeRotationChanged,
            onAutoArrangeObjects = onAutoArrangeObjects
        )
    }
}

@Composable
private fun TransformMoveToolPanel(
    printerBed: PrinterBedSpec,
    transform: ViewerModelTransform,
    onTransformUpdate: (ViewerModelTransform) -> Unit,
    onEditTransformField: (TransformNumericField) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TransformSliderRow(
            label = "X",
            valueText = String.format(Locale.US, "%.1f mm", transform.centerXmm),
            value = transform.centerXmm,
            range = 0f..printerBed.widthMm,
            onValueChange = { onTransformUpdate(transform.copy(centerXmm = it)) },
            onValueClick = { onEditTransformField(TransformNumericField.MoveX) },
            modifier = Modifier.weight(1f)
        )
        TransformSliderRow(
            label = "Y",
            valueText = String.format(Locale.US, "%.1f mm", transform.centerYmm),
            value = transform.centerYmm,
            range = 0f..printerBed.depthMm,
            onValueChange = { onTransformUpdate(transform.copy(centerYmm = it)) },
            onValueClick = { onEditTransformField(TransformNumericField.MoveY) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun TransformRotateToolPanel(
    transform: ViewerModelTransform,
    onTransformUpdate: (ViewerModelTransform) -> Unit,
    onRotateBy: (Float, Float, Float) -> Unit,
    onEditTransformField: (TransformNumericField) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TransformSliderRow(
                label = "X",
                valueText = String.format(Locale.US, "%.0f°", transform.rotationXDegrees),
                value = normalizeDegrees(transform.rotationXDegrees),
                range = -180f..180f,
                onValueChange = { onTransformUpdate(transform.copy(rotationXDegrees = it, orientationMatrix = null)) },
                onValueClick = { onEditTransformField(TransformNumericField.RotateX) },
                modifier = Modifier.weight(1f)
            )
            TransformSliderRow(
                label = "Y",
                valueText = String.format(Locale.US, "%.0f°", transform.rotationYDegrees),
                value = normalizeDegrees(transform.rotationYDegrees),
                range = -180f..180f,
                onValueChange = { onTransformUpdate(transform.copy(rotationYDegrees = it, orientationMatrix = null)) },
                onValueClick = { onEditTransformField(TransformNumericField.RotateY) },
                modifier = Modifier.weight(1f)
            )
            TransformSliderRow(
                label = "Z",
                valueText = String.format(Locale.US, "%.0f°", transform.rotationZDegrees),
                value = normalizeDegrees(transform.rotationZDegrees),
                range = -180f..180f,
                onValueChange = { onTransformUpdate(transform.copy(rotationZDegrees = it, orientationMatrix = null)) },
                onValueClick = { onEditTransformField(TransformNumericField.RotateZ) },
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TransformQuickButton("X -90") { onRotateBy(-90f, 0f, 0f) }
            TransformQuickButton("X +90") { onRotateBy(90f, 0f, 0f) }
            TransformQuickButton("Y -90") { onRotateBy(0f, -90f, 0f) }
            TransformQuickButton("Y +90") { onRotateBy(0f, 90f, 0f) }
            TransformQuickButton("Z -90") { onRotateBy(0f, 0f, -90f) }
            TransformQuickButton("Z +90") { onRotateBy(0f, 0f, 90f) }
            TransformQuickButton("Snap 45") { onTransformUpdate(transform.snapRotationTo(45f)) }
        }
    }
}

@Composable
private fun TransformScaleToolPanel(
    transform: ViewerModelTransform,
    onTransformUpdate: (ViewerModelTransform) -> Unit,
    onEditTransformField: (TransformNumericField) -> Unit
) {
    TransformSliderRow(
        label = "Scale",
        valueText = String.format(Locale.US, "%.0f%%", transform.uniformScale * 100f),
        value = transform.uniformScale,
        range = 0.05f..3f,
        onValueChange = { onTransformUpdate(transform.copy(uniformScale = it)) },
        onValueClick = { onEditTransformField(TransformNumericField.Scale) },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun TransformSplitToolPanel(
    compactLayout: Boolean,
    onSplitToObjectsRequested: () -> Unit,
    onSplitToPartsRequested: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilledTonalButton(
            onClick = onSplitToObjectsRequested,
            modifier = Modifier
                .weight(1f)
                .height(if (compactLayout) 36.dp else 42.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp)
        ) {
            Text("To objects", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        FilledTonalButton(
            onClick = onSplitToPartsRequested,
            modifier = Modifier
                .weight(1f)
                .height(if (compactLayout) 36.dp else 42.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp)
        ) {
            Text("To parts", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TransformCutToolPanel(
    compactLayout: Boolean,
    cutBounds: MeshBounds?,
    cutHeightMm: Float,
    onCutHeightChanged: (Float) -> Unit,
    cutRotationXDegrees: Float,
    onCutRotationXChanged: (Float) -> Unit,
    cutRotationYDegrees: Float,
    onCutRotationYChanged: (Float) -> Unit,
    cutMode: WorkspaceCutMode,
    onCutModeChanged: (WorkspaceCutMode) -> Unit,
    cutKeepUpper: Boolean,
    onCutKeepUpperChanged: (Boolean) -> Unit,
    cutKeepLower: Boolean,
    onCutKeepLowerChanged: (Boolean) -> Unit,
    cutKeepAsParts: Boolean,
    onCutKeepAsPartsChanged: (Boolean) -> Unit,
    cutFlipUpper: Boolean,
    onCutFlipUpperChanged: (Boolean) -> Unit,
    cutFlipLower: Boolean,
    onCutFlipLowerChanged: (Boolean) -> Unit,
    cutPlaceOnCutUpper: Boolean,
    onCutPlaceOnCutUpperChanged: (Boolean) -> Unit,
    cutPlaceOnCutLower: Boolean,
    onCutPlaceOnCutLowerChanged: (Boolean) -> Unit,
    cutConnectorKind: WorkspaceCutConnectorKind,
    onCutConnectorKindChanged: (WorkspaceCutConnectorKind) -> Unit,
    cutConnectorsEditing: Boolean,
    onCutConnectorsEditingChanged: (Boolean) -> Unit,
    cutConnectorStyle: WorkspaceCutConnectorStyle,
    onCutConnectorStyleChange: (WorkspaceCutConnectorStyle) -> Unit,
    cutConnectorShape: WorkspaceCutConnectorShape,
    onCutConnectorShapeChange: (WorkspaceCutConnectorShape) -> Unit,
    cutConnectorDepthMm: Float,
    onCutConnectorDepthChange: (Float) -> Unit,
    cutConnectorDepthToleranceMm: Float,
    onCutConnectorDepthToleranceChange: (Float) -> Unit,
    cutConnectorSizeMm: Float,
    onCutConnectorSizeChange: (Float) -> Unit,
    cutConnectorSizeToleranceMm: Float,
    onCutConnectorSizeToleranceChange: (Float) -> Unit,
    cutConnectorRotationDegrees: Float,
    onCutConnectorRotationChange: (Float) -> Unit,
    cutConnectorSnapBulgePercent: Float,
    onCutConnectorSnapBulgeChange: (Float) -> Unit,
    cutConnectorSnapSpacePercent: Float,
    onCutConnectorSnapSpaceChange: (Float) -> Unit,
    grooveDepthMm: Float,
    onGrooveDepthChange: (Float) -> Unit,
    grooveWidthMm: Float,
    onGrooveWidthChange: (Float) -> Unit,
    grooveFlapAngleDegrees: Float,
    onGrooveFlapAngleChange: (Float) -> Unit,
    grooveAngleDegrees: Float,
    onGrooveAngleChange: (Float) -> Unit,
    grooveDepthToleranceMm: Float,
    onGrooveDepthToleranceChange: (Float) -> Unit,
    grooveWidthToleranceMm: Float,
    onGrooveWidthToleranceChange: (Float) -> Unit,
    onEditCutField: (CutNumericField) -> Unit,
    onCutRequested: (WorkspaceCutRequest) -> Unit
) {
    val minZ = cutBounds?.minZ ?: 0f
    val maxZ = cutBounds?.maxZ ?: 1f
    val cutRange = minZ..maxZ.coerceAtLeast(minZ + 0.1f)
    val cutContentScrollState = rememberScrollState()
    fun currentCutConnectorDepth(): Float =
        if (cutConnectorKind == WorkspaceCutConnectorKind.Snap) {
            cutConnectorDepthMm.coerceAtLeast(cutConnectorSizeMm)
        } else {
            cutConnectorDepthMm
        }
    fun currentCutRequest() = WorkspaceCutRequest(
        axis = WorkspaceCutAxis.Z,
        heightMm = cutHeightMm.coerceIn(cutRange.start, cutRange.endInclusive),
        rotationXDegrees = normalizeDegrees(cutRotationXDegrees),
        rotationYDegrees = normalizeDegrees(cutRotationYDegrees),
        mode = cutMode,
        keepUpper = cutKeepUpper,
        keepLower = cutKeepLower,
        keepAsParts = cutKeepAsParts,
        flipUpper = cutFlipUpper,
        flipLower = cutFlipLower,
        placeOnCutUpper = cutPlaceOnCutUpper,
        placeOnCutLower = cutPlaceOnCutLower,
        connectorKind = cutConnectorKind,
        connectorStyle = cutConnectorStyle,
        connectorShape = cutConnectorShape,
        connectorDepthMm = currentCutConnectorDepth(),
        connectorDepthToleranceMm = cutConnectorDepthToleranceMm,
        connectorSizeMm = cutConnectorSizeMm,
        connectorSizeToleranceMm = cutConnectorSizeToleranceMm,
        connectorRotationDegrees = cutConnectorRotationDegrees,
        connectorSnapBulgePercent = cutConnectorSnapBulgePercent,
        connectorSnapSpacePercent = cutConnectorSnapSpacePercent,
        connectorsEditing = cutConnectorsEditing,
        grooveDepthMm = grooveDepthMm,
        grooveWidthMm = grooveWidthMm,
        grooveFlapAngleDegrees = grooveFlapAngleDegrees,
        grooveAngleDegrees = grooveAngleDegrees,
        grooveDepthToleranceMm = grooveDepthToleranceMm,
        grooveWidthToleranceMm = grooveWidthToleranceMm
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (compactLayout) 7.dp else 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CutSectionLabel(if (cutConnectorsEditing) "Connectors" else "Mode")
            Spacer(modifier = Modifier.weight(1f))
            if (cutConnectorsEditing) {
                TransformQuickButton("Cancel") {
                    onCutConnectorKindChanged(WorkspaceCutConnectorKind.None)
                    onCutConnectorsEditingChanged(false)
                }
                FilledTonalButton(
                    onClick = { onCutConnectorsEditingChanged(false) },
                    modifier = Modifier.height(if (compactLayout) 34.dp else 38.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 5.dp)
                ) {
                    Text("Confirm")
                }
            } else {
                FilledTonalButton(
                    onClick = { onCutRequested(currentCutRequest()) },
                    enabled = cutBounds != null && (cutKeepUpper || cutKeepLower),
                    modifier = Modifier.height(if (compactLayout) 34.dp else 38.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 5.dp)
                ) {
                    Text("Cut")
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = if (compactLayout) 126.dp else 212.dp)
                .verticalScroll(cutContentScrollState),
            verticalArrangement = Arrangement.spacedBy(if (compactLayout) 7.dp else 10.dp)
        ) {
            if (cutConnectorsEditing) {
                CutConnectorEditor(
                    connectorKind = cutConnectorKind,
                    onConnectorKindChange = { kind ->
                        onCutConnectorKindChanged(kind)
                        if (kind != WorkspaceCutConnectorKind.None) onCutKeepAsPartsChanged(false)
                        if (kind == WorkspaceCutConnectorKind.Snap) onCutConnectorShapeChange(WorkspaceCutConnectorShape.Circle)
                        if (kind == WorkspaceCutConnectorKind.Dowel && cutConnectorStyle == WorkspaceCutConnectorStyle.Frustum) {
                            onCutConnectorStyleChange(WorkspaceCutConnectorStyle.Prism)
                        }
                    },
                    connectorStyle = cutConnectorStyle,
                    onConnectorStyleChange = onCutConnectorStyleChange,
                    connectorShape = cutConnectorShape,
                    onConnectorShapeChange = onCutConnectorShapeChange,
                    depthMm = cutConnectorDepthMm,
                    onDepthChange = onCutConnectorDepthChange,
                    depthToleranceMm = cutConnectorDepthToleranceMm,
                    onDepthToleranceChange = onCutConnectorDepthToleranceChange,
                    sizeMm = cutConnectorSizeMm,
                    onSizeChange = onCutConnectorSizeChange,
                    sizeToleranceMm = cutConnectorSizeToleranceMm,
                    onSizeToleranceChange = onCutConnectorSizeToleranceChange,
                    rotationDegrees = cutConnectorRotationDegrees,
                    onRotationChange = { onCutConnectorRotationChange(normalizeDegrees(it)) },
                    snapBulgePercent = cutConnectorSnapBulgePercent,
                    onSnapBulgeChange = onCutConnectorSnapBulgeChange,
                    snapSpacePercent = cutConnectorSnapSpacePercent,
                    onSnapSpaceChange = onCutConnectorSnapSpaceChange
                )
            } else {
                CutPlanarControls(
                    cutRange = cutRange,
                    cutHeightMm = cutHeightMm,
                    onCutHeightChanged = onCutHeightChanged,
                    cutRotationXDegrees = cutRotationXDegrees,
                    onCutRotationXChanged = onCutRotationXChanged,
                    cutRotationYDegrees = cutRotationYDegrees,
                    onCutRotationYChanged = onCutRotationYChanged,
                    cutMode = cutMode,
                    onCutModeChanged = onCutModeChanged,
                    cutKeepUpper = cutKeepUpper,
                    onCutKeepUpperChanged = onCutKeepUpperChanged,
                    cutKeepLower = cutKeepLower,
                    onCutKeepLowerChanged = onCutKeepLowerChanged,
                    cutKeepAsParts = cutKeepAsParts,
                    onCutKeepAsPartsChanged = onCutKeepAsPartsChanged,
                    cutFlipUpper = cutFlipUpper,
                    onCutFlipUpperChanged = onCutFlipUpperChanged,
                    cutFlipLower = cutFlipLower,
                    onCutFlipLowerChanged = onCutFlipLowerChanged,
                    cutPlaceOnCutUpper = cutPlaceOnCutUpper,
                    onCutPlaceOnCutUpperChanged = onCutPlaceOnCutUpperChanged,
                    cutPlaceOnCutLower = cutPlaceOnCutLower,
                    onCutPlaceOnCutLowerChanged = onCutPlaceOnCutLowerChanged,
                    cutConnectorKind = cutConnectorKind,
                    onCutConnectorKindChanged = onCutConnectorKindChanged,
                    onCutConnectorsEditingChanged = onCutConnectorsEditingChanged,
                    onEditCutField = onEditCutField
                )
                if (cutMode == WorkspaceCutMode.Groove) {
                    CutGrooveControls(
                        grooveDepthMm = grooveDepthMm,
                        onGrooveDepthChange = onGrooveDepthChange,
                        grooveWidthMm = grooveWidthMm,
                        onGrooveWidthChange = onGrooveWidthChange,
                        grooveFlapAngleDegrees = grooveFlapAngleDegrees,
                        onGrooveFlapAngleChange = onGrooveFlapAngleChange,
                        grooveAngleDegrees = grooveAngleDegrees,
                        onGrooveAngleChange = onGrooveAngleChange,
                        grooveDepthToleranceMm = grooveDepthToleranceMm,
                        onGrooveDepthToleranceChange = onGrooveDepthToleranceChange,
                        grooveWidthToleranceMm = grooveWidthToleranceMm,
                        onGrooveWidthToleranceChange = onGrooveWidthToleranceChange
                    )
                }
            }
        }
    }
}

@Composable
private fun CutPlanarControls(
    cutRange: ClosedFloatingPointRange<Float>,
    cutHeightMm: Float,
    onCutHeightChanged: (Float) -> Unit,
    cutRotationXDegrees: Float,
    onCutRotationXChanged: (Float) -> Unit,
    cutRotationYDegrees: Float,
    onCutRotationYChanged: (Float) -> Unit,
    cutMode: WorkspaceCutMode,
    onCutModeChanged: (WorkspaceCutMode) -> Unit,
    cutKeepUpper: Boolean,
    onCutKeepUpperChanged: (Boolean) -> Unit,
    cutKeepLower: Boolean,
    onCutKeepLowerChanged: (Boolean) -> Unit,
    cutKeepAsParts: Boolean,
    onCutKeepAsPartsChanged: (Boolean) -> Unit,
    cutFlipUpper: Boolean,
    onCutFlipUpperChanged: (Boolean) -> Unit,
    cutFlipLower: Boolean,
    onCutFlipLowerChanged: (Boolean) -> Unit,
    cutPlaceOnCutUpper: Boolean,
    onCutPlaceOnCutUpperChanged: (Boolean) -> Unit,
    cutPlaceOnCutLower: Boolean,
    onCutPlaceOnCutLowerChanged: (Boolean) -> Unit,
    cutConnectorKind: WorkspaceCutConnectorKind,
    onCutConnectorKindChanged: (WorkspaceCutConnectorKind) -> Unit,
    onCutConnectorsEditingChanged: (Boolean) -> Unit,
    onEditCutField: (CutNumericField) -> Unit
) {
    val outlineColor = appOutlineColor()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TransformQuickButton("Planar", selected = cutMode == WorkspaceCutMode.Plane) {
            onCutModeChanged(WorkspaceCutMode.Plane)
        }
        TransformQuickButton("Dovetail", selected = cutMode == WorkspaceCutMode.Groove) {
            onCutModeChanged(WorkspaceCutMode.Groove)
        }
    }
    CutSectionLabel("Cut position")
    TransformSliderRow(
        label = "Z:",
        valueText = String.format(Locale.US, "%.1f mm", cutHeightMm.coerceIn(cutRange.start, cutRange.endInclusive)),
        value = cutHeightMm.coerceIn(cutRange.start, cutRange.endInclusive),
        range = cutRange,
        onValueChange = onCutHeightChanged,
        onValueClick = { onEditCutField(CutNumericField.PositionZ) },
        modifier = Modifier.fillMaxWidth()
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        TransformSliderRow(
            label = "X:",
            valueText = String.format(Locale.US, "%.0f°", normalizeDegrees(cutRotationXDegrees)),
            value = normalizeDegrees(cutRotationXDegrees),
            range = -180f..180f,
            onValueChange = onCutRotationXChanged,
            onValueClick = { onEditCutField(CutNumericField.RotationX) },
            beforeValueContent = {
                CutStepButton("-90") { onCutRotationXChanged(normalizeDegrees(cutRotationXDegrees - 90f)) }
                CutStepButton("+90") { onCutRotationXChanged(normalizeDegrees(cutRotationXDegrees + 90f)) }
            },
            modifier = Modifier.fillMaxWidth()
        )
        TransformSliderRow(
            label = "Y:",
            valueText = String.format(Locale.US, "%.0f°", normalizeDegrees(cutRotationYDegrees)),
            value = normalizeDegrees(cutRotationYDegrees),
            range = -180f..180f,
            onValueChange = onCutRotationYChanged,
            onValueClick = { onEditCutField(CutNumericField.RotationY) },
            beforeValueContent = {
                CutStepButton("-90") { onCutRotationYChanged(normalizeDegrees(cutRotationYDegrees - 90f)) }
                CutStepButton("+90") { onCutRotationYChanged(normalizeDegrees(cutRotationYDegrees + 90f)) }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
    HorizontalDivider(color = outlineColor.copy(alpha = 0.32f))
    CutSectionLabel("After cut")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TransformQuickButton("Keep upper", selected = cutKeepUpper) {
            onCutKeepUpperChanged(!cutKeepUpper || !cutKeepLower)
        }
        TransformQuickButton("Keep lower", selected = cutKeepLower) {
            onCutKeepLowerChanged(!cutKeepLower || !cutKeepUpper)
        }
        TransformQuickButton(
            "Cut to parts",
            selected = cutKeepAsParts && cutConnectorKind == WorkspaceCutConnectorKind.None
        ) {
            if (cutConnectorKind == WorkspaceCutConnectorKind.None) {
                onCutKeepAsPartsChanged(!cutKeepAsParts)
            }
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TransformQuickButton("Place upper", selected = cutPlaceOnCutUpper) {
            onCutPlaceOnCutUpperChanged(!cutPlaceOnCutUpper)
        }
        TransformQuickButton("Place lower", selected = cutPlaceOnCutLower) {
            onCutPlaceOnCutLowerChanged(!cutPlaceOnCutLower)
        }
        TransformQuickButton("Flip upper", selected = cutFlipUpper) {
            onCutFlipUpperChanged(!cutFlipUpper)
        }
        TransformQuickButton("Flip lower", selected = cutFlipLower) {
            onCutFlipLowerChanged(!cutFlipLower)
        }
    }
    HorizontalDivider(color = outlineColor.copy(alpha = 0.32f))
    CutSectionLabel("Connectors")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TransformQuickButton("Add connectors", selected = cutConnectorKind != WorkspaceCutConnectorKind.None) {
            if (cutConnectorKind == WorkspaceCutConnectorKind.None) {
                onCutConnectorKindChanged(WorkspaceCutConnectorKind.Plug)
            }
            onCutKeepAsPartsChanged(false)
            onCutConnectorsEditingChanged(true)
        }
        if (cutConnectorKind != WorkspaceCutConnectorKind.None) {
            TransformQuickButton("Reset connectors") {
                onCutConnectorKindChanged(WorkspaceCutConnectorKind.None)
                onCutConnectorsEditingChanged(false)
            }
        }
    }
}

@Composable
private fun CutGrooveControls(
    grooveDepthMm: Float,
    onGrooveDepthChange: (Float) -> Unit,
    grooveWidthMm: Float,
    onGrooveWidthChange: (Float) -> Unit,
    grooveFlapAngleDegrees: Float,
    onGrooveFlapAngleChange: (Float) -> Unit,
    grooveAngleDegrees: Float,
    onGrooveAngleChange: (Float) -> Unit,
    grooveDepthToleranceMm: Float,
    onGrooveDepthToleranceChange: (Float) -> Unit,
    grooveWidthToleranceMm: Float,
    onGrooveWidthToleranceChange: (Float) -> Unit
) {
    HorizontalDivider(color = appOutlineColor().copy(alpha = 0.32f))
    CutSectionLabel("Groove")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TransformSliderRow(
            label = "Depth",
            valueText = String.format(Locale.US, "%.1f mm", grooveDepthMm),
            value = grooveDepthMm,
            range = 0.5f..12f,
            onValueChange = onGrooveDepthChange,
            onValueClick = {},
            modifier = Modifier.weight(1f)
        )
        TransformSliderRow(
            label = "Width",
            valueText = String.format(Locale.US, "%.1f mm", grooveWidthMm),
            value = grooveWidthMm,
            range = 0.5f..20f,
            onValueChange = onGrooveWidthChange,
            onValueClick = {},
            modifier = Modifier.weight(1f)
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TransformSliderRow(
            label = "Flap",
            valueText = String.format(Locale.US, "%.0f°", grooveFlapAngleDegrees),
            value = grooveFlapAngleDegrees,
            range = 30f..120f,
            onValueChange = onGrooveFlapAngleChange,
            onValueClick = {},
            modifier = Modifier.weight(1f)
        )
        TransformSliderRow(
            label = "Groove angle",
            valueText = String.format(Locale.US, "%.0f°", grooveAngleDegrees),
            value = grooveAngleDegrees,
            range = 0f..15f,
            onValueChange = onGrooveAngleChange,
            onValueClick = {},
            modifier = Modifier.weight(1f)
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TransformSliderRow(
            label = "Depth tol",
            valueText = String.format(Locale.US, "%.2f mm", grooveDepthToleranceMm),
            value = grooveDepthToleranceMm,
            range = 0f..1f,
            onValueChange = onGrooveDepthToleranceChange,
            onValueClick = {},
            modifier = Modifier.weight(1f)
        )
        TransformSliderRow(
            label = "Width tol",
            valueText = String.format(Locale.US, "%.2f mm", grooveWidthToleranceMm),
            value = grooveWidthToleranceMm,
            range = 0f..1f,
            onValueChange = onGrooveWidthToleranceChange,
            onValueClick = {},
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun TransformAutoOrientToolPanel(
    compactLayout: Boolean,
    onAutoOrientObjects: () -> Unit,
    onLayFaceRequested: () -> Unit
) {
    val actionHeight = if (compactLayout) 38.dp else 44.dp
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilledTonalButton(
            onClick = onAutoOrientObjects,
            modifier = Modifier
                .weight(1f)
                .height(actionHeight),
            contentPadding = PaddingValues(horizontal = if (compactLayout) 9.dp else 12.dp, vertical = 7.dp)
        ) {
            Text("Auto orient", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        FilledTonalButton(
            onClick = onLayFaceRequested,
            modifier = Modifier
                .weight(1f)
                .height(actionHeight),
            contentPadding = PaddingValues(horizontal = if (compactLayout) 9.dp else 12.dp, vertical = 7.dp)
        ) {
            Text("Lay face", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TransformPaintToolPanel(
    compactLayout: Boolean,
    onPaintModeSelected: (ViewerPaintMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PaintModeToolButton("Color", compactLayout) { onPaintModeSelected(ViewerPaintMode.Color) }
        PaintModeToolButton("Seam", compactLayout) { onPaintModeSelected(ViewerPaintMode.Seam) }
        PaintModeToolButton("Support", compactLayout) { onPaintModeSelected(ViewerPaintMode.Support) }
        PaintModeToolButton("Fuzzy", compactLayout) { onPaintModeSelected(ViewerPaintMode.FuzzySkin) }
    }
}

@Composable
private fun TransformAutoArrangeToolPanel(
    compactLayout: Boolean,
    allowArrangeRotation: Boolean,
    onAllowArrangeRotationChanged: (Boolean) -> Unit,
    onAutoArrangeObjects: () -> Unit
) {
    val actionHeight = if (compactLayout) 38.dp else 44.dp
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (compactLayout) 6.dp else 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TransformQuickButton(
                label = "Keep angle",
                selected = !allowArrangeRotation,
                compact = compactLayout,
                modifier = Modifier.weight(1f)
            ) { onAllowArrangeRotationChanged(false) }
            TransformQuickButton(
                label = "Rotate to fit",
                selected = allowArrangeRotation,
                compact = compactLayout,
                modifier = Modifier.weight(1f)
            ) { onAllowArrangeRotationChanged(true) }
        }
        FilledTonalButton(
            onClick = onAutoArrangeObjects,
            modifier = Modifier
                .fillMaxWidth()
                .height(actionHeight),
            contentPadding = PaddingValues(horizontal = if (compactLayout) 9.dp else 12.dp, vertical = 7.dp)
        ) {
            Text("Auto arrange", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun TransformToolIcon(
    type: TransformToolTab,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Icon(
        painter = painterResource(type.iconRes()),
        contentDescription = type.contentDescription(),
        tint = tint,
        modifier = modifier
    )
}

private fun TransformToolTab.iconRes(): Int = when (this) {
    TransformToolTab.Move -> R.drawable.ic_tool_move
    TransformToolTab.Rotate -> R.drawable.ic_tool_rotate
    TransformToolTab.Scale -> R.drawable.ic_tool_scale
    TransformToolTab.Split -> R.drawable.ic_tool_split
    TransformToolTab.Cut -> R.drawable.ic_tool_cut
    TransformToolTab.Paint -> R.drawable.ic_tool_paint
    TransformToolTab.AutoOrient -> R.drawable.ic_tool_orient
    TransformToolTab.AutoArrange -> R.drawable.ic_tool_arrange
}

private fun TransformToolTab.contentDescription(): String = when (this) {
    TransformToolTab.Move -> "Move"
    TransformToolTab.Rotate -> "Rotate"
    TransformToolTab.Scale -> "Scale"
    TransformToolTab.Split -> "Split"
    TransformToolTab.Cut -> "Cut"
    TransformToolTab.Paint -> "Paint"
    TransformToolTab.AutoOrient -> "Auto orient"
    TransformToolTab.AutoArrange -> "Auto arrange"
}

private fun ViewerModelTransform.snapRotationTo(stepDegrees: Float): ViewerModelTransform =
    copy(
        rotationXDegrees = snapDegrees(rotationXDegrees, stepDegrees),
        rotationYDegrees = snapDegrees(rotationYDegrees, stepDegrees),
        rotationZDegrees = snapDegrees(rotationZDegrees, stepDegrees),
        orientationMatrix = null
    )

private fun snapDegrees(value: Float, stepDegrees: Float): Float {
    val step = stepDegrees.coerceAtLeast(1f)
    return normalizeDegrees(kotlin.math.round(value / step) * step)
}

@Composable
private fun CutSectionLabel(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = appTitleColor(),
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun TransformQuickButton(
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.height(38.dp),
        colors = if (selected) {
            ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            ButtonDefaults.filledTonalButtonColors()
        },
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TransformQuickButton(
    label: String,
    compact: Boolean,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.height(if (compact) 32.dp else 38.dp),
        colors = if (selected) {
            ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            ButtonDefaults.filledTonalButtonColors()
        },
        contentPadding = PaddingValues(horizontal = if (compact) 8.dp else 10.dp, vertical = 5.dp)
    ) {
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PaintModeToolButton(label: String, compact: Boolean, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.height(if (compact) 34.dp else 40.dp),
        contentPadding = PaddingValues(horizontal = if (compact) 9.dp else 12.dp, vertical = 7.dp)
    ) {
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CutStepButton(label: String, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.height(28.dp),
        contentPadding = PaddingValues(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
        )
    }
}

@Composable
private fun CutConnectorEditor(
    connectorKind: WorkspaceCutConnectorKind,
    onConnectorKindChange: (WorkspaceCutConnectorKind) -> Unit,
    connectorStyle: WorkspaceCutConnectorStyle,
    onConnectorStyleChange: (WorkspaceCutConnectorStyle) -> Unit,
    connectorShape: WorkspaceCutConnectorShape,
    onConnectorShapeChange: (WorkspaceCutConnectorShape) -> Unit,
    depthMm: Float,
    onDepthChange: (Float) -> Unit,
    depthToleranceMm: Float,
    onDepthToleranceChange: (Float) -> Unit,
    sizeMm: Float,
    onSizeChange: (Float) -> Unit,
    sizeToleranceMm: Float,
    onSizeToleranceChange: (Float) -> Unit,
    rotationDegrees: Float,
    onRotationChange: (Float) -> Unit,
    snapBulgePercent: Float,
    onSnapBulgeChange: (Float) -> Unit,
    snapSpacePercent: Float,
    onSnapSpaceChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        CutSectionLabel("Type")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(WorkspaceCutConnectorKind.Plug, WorkspaceCutConnectorKind.Dowel, WorkspaceCutConnectorKind.Snap).forEach { kind ->
                TransformQuickButton(
                    label = when (kind) {
                        WorkspaceCutConnectorKind.Plug -> "Plug"
                        WorkspaceCutConnectorKind.Dowel -> "Dowel"
                        WorkspaceCutConnectorKind.Snap -> "Snap"
                        WorkspaceCutConnectorKind.None -> "None"
                    },
                    selected = connectorKind == kind
                ) { onConnectorKindChange(kind) }
            }
        }
        CutSectionLabel("Style")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            WorkspaceCutConnectorStyle.values().forEach { style ->
                val enabled = connectorKind == WorkspaceCutConnectorKind.Plug || style == WorkspaceCutConnectorStyle.Prism
                TransformQuickButton(
                    label = when (style) {
                        WorkspaceCutConnectorStyle.Prism -> "Prism"
                        WorkspaceCutConnectorStyle.Frustum -> "Frustum"
                    },
                    selected = connectorStyle == style && enabled
                ) {
                    if (enabled) onConnectorStyleChange(style)
                }
            }
        }
        CutSectionLabel("Shape")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            WorkspaceCutConnectorShape.values().forEach { shape ->
                val enabled = connectorKind != WorkspaceCutConnectorKind.Snap || shape == WorkspaceCutConnectorShape.Circle
                TransformQuickButton(
                    label = when (shape) {
                        WorkspaceCutConnectorShape.Triangle -> "Triangle"
                        WorkspaceCutConnectorShape.Square -> "Square"
                        WorkspaceCutConnectorShape.Hexagon -> "Hexagon"
                        WorkspaceCutConnectorShape.Circle -> "Circle"
                    },
                    selected = connectorShape == shape && enabled
                ) {
                    if (enabled) onConnectorShapeChange(shape)
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TransformSliderRow(
                label = "Depth",
                valueText = String.format(Locale.US, "%.2f mm", depthMm),
                                        value = depthMm.coerceAtLeast(if (connectorKind == WorkspaceCutConnectorKind.Snap) sizeMm else 0.5f),
                                        range = (if (connectorKind == WorkspaceCutConnectorKind.Snap) sizeMm else 0.5f)..20f,
                onValueChange = onDepthChange,
                onValueClick = {},
                modifier = Modifier.weight(1f)
            )
            TransformSliderRow(
                label = "Tol",
                valueText = String.format(Locale.US, "%.2f mm", depthToleranceMm),
                value = depthToleranceMm,
                range = 0f..10f,
                onValueChange = onDepthToleranceChange,
                onValueClick = {},
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TransformSliderRow(
                label = "Size",
                valueText = String.format(Locale.US, "%.2f mm", sizeMm),
                value = sizeMm,
                range = 0.5f..20f,
                onValueChange = onSizeChange,
                onValueClick = {},
                modifier = Modifier.weight(1f)
            )
            TransformSliderRow(
                label = "Tol",
                valueText = String.format(Locale.US, "%.2f mm", sizeToleranceMm),
                value = sizeToleranceMm,
                range = 0f..10f,
                onValueChange = onSizeToleranceChange,
                onValueClick = {},
                modifier = Modifier.weight(1f)
            )
        }
        TransformSliderRow(
            label = "Rotation",
            valueText = String.format(Locale.US, "%.0f°", normalizeDegrees(rotationDegrees)),
            value = normalizeDegrees(rotationDegrees),
            range = 0f..180f,
            onValueChange = onRotationChange,
            onValueClick = {},
            modifier = Modifier.fillMaxWidth()
        )
        if (connectorKind == WorkspaceCutConnectorKind.Snap) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TransformSliderRow(
                    label = "Bulge",
                    valueText = String.format(Locale.US, "%.0f%%", snapBulgePercent),
                    value = snapBulgePercent,
                    range = 5f..100f,
                    onValueChange = onSnapBulgeChange,
                    onValueClick = {},
                    modifier = Modifier.weight(1f)
                )
                TransformSliderRow(
                    label = "Space",
                    valueText = String.format(Locale.US, "%.0f%%", snapSpacePercent),
                    value = snapSpacePercent,
                    range = 10f..50f,
                    onValueChange = onSnapSpaceChange,
                    onValueClick = {},
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private enum class CutNumericField {
    PositionZ,
    RotationX,
    RotationY
}

@Composable
private fun CutValueDialog(
    field: CutNumericField,
    cutHeightMm: Float,
    cutRotationXDegrees: Float,
    cutRotationYDegrees: Float,
    cutRange: ClosedFloatingPointRange<Float>,
    onDismiss: () -> Unit,
    onApply: (Float) -> Unit
) {
    val (title, initialValue, suffix) = when (field) {
        CutNumericField.PositionZ -> Triple("Z position", cutHeightMm.coerceIn(cutRange.start, cutRange.endInclusive), "mm")
        CutNumericField.RotationX -> Triple("X rotation", normalizeDegrees(cutRotationXDegrees), "deg")
        CutNumericField.RotationY -> Triple("Y rotation", normalizeDegrees(cutRotationYDegrees), "deg")
    }
    var text by remember(field, initialValue) {
        mutableStateOf(String.format(Locale.US, "%.1f", initialValue))
    }
    val parsedValue = text.toFloatOrNull()
    val validValue = parsedValue?.let { value ->
        when (field) {
            CutNumericField.PositionZ -> value.coerceIn(cutRange.start, cutRange.endInclusive)
            CutNumericField.RotationX,
            CutNumericField.RotationY -> normalizeDegrees(value)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                suffix = { Text(suffix) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )
        },
        confirmButton = {
            TextButton(
                onClick = { validValue?.let(onApply) },
                enabled = validValue != null
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
internal fun TransformValueDialog(
    field: TransformNumericField,
    transform: ViewerModelTransform,
    printerBed: PrinterBedSpec,
    onDismiss: () -> Unit,
    onApply: (Float) -> Unit
) {
    val (title, initialValue, suffix) = when (field) {
        TransformNumericField.MoveX -> Triple("X position", transform.centerXmm, "mm")
        TransformNumericField.MoveY -> Triple("Y position", transform.centerYmm, "mm")
        TransformNumericField.RotateX -> Triple("X rotation", transform.rotationXDegrees, "deg")
        TransformNumericField.RotateY -> Triple("Y rotation", transform.rotationYDegrees, "deg")
        TransformNumericField.RotateZ -> Triple("Z rotation", transform.rotationZDegrees, "deg")
        TransformNumericField.Scale -> Triple("Scale", transform.uniformScale * 100f, "%")
    }
    var text by remember(field, initialValue) {
        mutableStateOf(String.format(Locale.US, "%.1f", initialValue))
    }
    val parsedValue = text.toFloatOrNull()
    val validValue = parsedValue?.let { value ->
        when (field) {
            TransformNumericField.MoveX -> value.coerceIn(0f, printerBed.widthMm)
            TransformNumericField.MoveY -> value.coerceIn(0f, printerBed.depthMm)
            TransformNumericField.RotateX,
            TransformNumericField.RotateY,
            TransformNumericField.RotateZ -> normalizeDegrees(value)
            TransformNumericField.Scale -> value.coerceIn(5f, 2_000f)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                suffix = { Text(suffix) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )
        },
        confirmButton = {
            TextButton(
                onClick = { validValue?.let(onApply) },
                enabled = validValue != null
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
internal fun TransformSliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueClick: () -> Unit,
    modifier: Modifier = Modifier,
    beforeValueContent: @Composable (() -> Unit)? = null
) {
    val titleColor = appTitleColor()
    val bodyColor = appBodyColor()
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = titleColor,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.weight(1f))
            beforeValueContent?.invoke()
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelMedium,
                color = bodyColor,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(appCardColorMuted().copy(alpha = 0.76f))
                    .border(1.dp, appOutlineColor().copy(alpha = 0.36f), RoundedCornerShape(9.dp))
                    .clickable(onClick = onValueClick)
                    .widthIn(min = 58.dp)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            colors = workspaceSliderColors(),
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
        )
    }
}

@Composable
internal fun workspaceSliderColors() = SliderDefaults.colors(
    thumbColor = MaterialTheme.colorScheme.primary,
    activeTrackColor = MaterialTheme.colorScheme.primary,
    activeTickColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.38f),
    inactiveTrackColor = if (LocalAppDarkTheme.current) {
        Color(0xFF465160)
    } else {
        Color(0xFFD7DCE3)
    },
    inactiveTickColor = if (LocalAppDarkTheme.current) {
        Color(0xFF6D7784)
    } else {
        Color(0xFFAEB6C0)
    },
    disabledThumbColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.62f),
    disabledActiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.46f),
    disabledActiveTickColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.24f),
    disabledInactiveTrackColor = if (LocalAppDarkTheme.current) {
        Color(0xFF465160).copy(alpha = 0.72f)
    } else {
        Color(0xFFD7DCE3)
    },
    disabledInactiveTickColor = if (LocalAppDarkTheme.current) {
        Color(0xFF6D7784).copy(alpha = 0.72f)
    } else {
        Color(0xFFAEB6C0)
    }
)
