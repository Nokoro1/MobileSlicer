package com.mobileslicer.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.mobileslicer.appBodyColor
import com.mobileslicer.appCardColorMuted
import com.mobileslicer.appOutlineColor
import com.mobileslicer.appTitleColor
import com.mobileslicer.viewer.ViewerPaintAction
import com.mobileslicer.viewer.ViewerPaintBrushShape
import com.mobileslicer.viewer.ViewerPaintMode

@Composable
internal fun WorkspacePaintLauncher(
    enabled: Boolean,
    onModeSelected: (ViewerPaintMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        color = appCardColorMuted().copy(alpha = 0.88f),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Paint",
                color = appTitleColor(),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 6.dp)
            )
            PaintModeButton("Color", enabled, onClick = { onModeSelected(ViewerPaintMode.Color) })
            PaintModeButton("Seam", enabled, onClick = { onModeSelected(ViewerPaintMode.Seam) })
            PaintModeButton("Support", enabled, onClick = { onModeSelected(ViewerPaintMode.Support) })
            PaintModeButton("Fuzzy", enabled, onClick = { onModeSelected(ViewerPaintMode.FuzzySkin) })
        }
    }
}

@Composable
internal fun WorkspacePaintTopBar(
    mode: ViewerPaintMode,
    objectLabel: String,
    undoEnabled: Boolean,
    redoEnabled: Boolean,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = appCardColorMuted().copy(alpha = 0.9f),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = appTitleColor()
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${mode.label()} paint",
                    color = appTitleColor(),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = objectLabel,
                    color = appBodyColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            FilledTonalButton(onClick = onUndo, contentPadding = PaddingValues(horizontal = 12.dp), enabled = undoEnabled) {
                Text("Undo")
            }
            FilledTonalButton(onClick = onRedo, contentPadding = PaddingValues(horizontal = 12.dp), enabled = redoEnabled) {
                Text("Redo")
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun WorkspacePaintToolbar(
    mode: ViewerPaintMode,
    brushShape: ViewerPaintBrushShape,
    brushRadiusMm: Float,
    smartFillAngleDeg: Float,
    overhangAngleDeg: Float,
    clippingEnabled: Boolean,
    sectionViewPosition: Float,
    action: ViewerPaintAction,
    filamentSlots: List<PlateFilamentSlot>,
    activeColorSlotIndex: Int?,
    onBrushShapeChanged: (ViewerPaintBrushShape) -> Unit,
    onBrushRadiusChanged: (Float) -> Unit,
    onSmartFillAngleChanged: (Float) -> Unit,
    onOverhangAngleChanged: (Float) -> Unit,
    onClippingEnabledChanged: (Boolean) -> Unit,
    onSectionViewPositionChanged: (Float) -> Unit,
    onActionChanged: (ViewerPaintAction) -> Unit,
    onColorSlotSelected: (Int) -> Unit,
    onUnsupportedOption: (String) -> Unit,
    onCollapse: () -> Unit,
    clearEnabled: Boolean,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val availableBrushShapes = mode.availableBrushShapes()
    val availableActions = mode.availableActions()
    var editingNumber by remember { mutableStateOf<PaintNumericField?>(null) }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = appCardColorMuted().copy(alpha = 0.9f),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PaintSectionLabel("Brush", modifier = Modifier.weight(1f))
                TextButton(
                    onClick = onCollapse,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                ) {
                    Text("Hide")
                }
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                availableBrushShapes.forEach { candidate ->
                    PaintChoice(candidate.label(), brushShape == candidate) {
                        onBrushShapeChanged(candidate)
                    }
                }
            }
            PaintSectionLabel("Action")
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                availableActions.forEach { candidate ->
                    PaintChoice(candidate.label(mode), action == candidate) { onActionChanged(candidate) }
                }
                when (mode) {
                    ViewerPaintMode.Support -> {
                        PaintChoice("Overhangs only", selected = overhangAngleDeg > 0f) {
                            onOverhangAngleChanged(if (overhangAngleDeg > 0f) 0f else 45f)
                        }
                    }
                    ViewerPaintMode.Seam,
                    ViewerPaintMode.Color,
                    ViewerPaintMode.FuzzySkin -> Unit
                }
            }
            val displayBrushRadiusMm = effectivePaintBrushRadiusMm(brushRadiusMm)
            PaintSliderRow(
                label = mode.sizeLabel(),
                valueField = {
                    PaintNumberField(
                        value = displayBrushRadiusMm,
                        suffix = "mm",
                        decimals = 1,
                        onEditRequested = { editingNumber = PaintNumericField.BrushSize }
                    )
                }
            ) {
                PaintBrushRadiusSlider(
                    radiusMm = displayBrushRadiusMm,
                    onRadiusChanged = onBrushRadiusChanged,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (brushShape.usesSmartFillAngle(mode)) {
                PaintSliderRow(
                    label = "Smart fill angle",
                    valueField = {
                        PaintNumberField(
                            value = smartFillAngleDeg,
                            suffix = "deg",
                            decimals = 0,
                            onEditRequested = { editingNumber = PaintNumericField.SmartFillAngle }
                        )
                    }
                ) {
                    PaintLinearSlider(
                        value = smartFillAngleDeg,
                        onValueChange = onSmartFillAngleChanged,
                        valueRange = 0f..180f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            if (mode == ViewerPaintMode.Support && overhangAngleDeg > 0f) {
                PaintSliderRow(
                    label = "Highlight overhangs",
                    valueField = {
                        PaintNumberField(
                            value = overhangAngleDeg,
                            suffix = "deg",
                            decimals = 0,
                            onEditRequested = { editingNumber = PaintNumericField.OverhangAngle }
                        )
                    }
                ) {
                    PaintLinearSlider(
                        value = overhangAngleDeg,
                        onValueChange = onOverhangAngleChanged,
                        valueRange = 1f..90f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            if (mode == ViewerPaintMode.Color && filamentSlots.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PaintSectionLabel("Filaments")
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        filamentSlots.forEach { slot ->
                            val color = slotColor(slot.colorHex).toArgb()
                            FilamentPaintSwatch(
                                colorInt = color,
                                selected = activeColorSlotIndex == slot.index,
                                label = slot.index.toString(),
                                onClick = { onColorSlotSelected(slot.index) }
                            )
                        }
                    }
                }
            }
            if (mode == ViewerPaintMode.Color && clippingEnabled) {
                PaintSliderRow(
                    label = "Section view",
                    valueField = {
                        Text(
                            text = "${(sectionViewPosition.coerceIn(0f, 1f) * 100f).toInt()}%",
                            color = appBodyColor(),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .width(92.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(appCardColorMuted().copy(alpha = 0.76f))
                                .border(1.dp, appOutlineColor().copy(alpha = 0.46f), RoundedCornerShape(9.dp))
                                .padding(horizontal = 10.dp, vertical = 9.dp)
                        )
                    }
                ) {
                    PaintLinearSlider(
                        value = sectionViewPosition,
                        onValueChange = onSectionViewPositionChanged,
                        valueRange = 0f..1f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            FilledTonalButton(
                onClick = onClear,
                enabled = clearEnabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Erase all painting")
            }
        }
    }
    editingNumber?.let { field ->
        PaintNumericDialog(
            field = field,
            brushRadiusMm = brushRadiusMm,
            smartFillAngleDeg = smartFillAngleDeg,
            overhangAngleDeg = overhangAngleDeg,
            onDismiss = { editingNumber = null },
            onApply = { value ->
                when (field) {
                    PaintNumericField.BrushSize -> onBrushRadiusChanged(value.coerceIn(PaintBrushMinRadiusMm, PaintBrushMaxRadiusMm))
                    PaintNumericField.SmartFillAngle -> onSmartFillAngleChanged(value.coerceIn(0f, 180f))
                    PaintNumericField.OverhangAngle -> onOverhangAngleChanged(value.coerceIn(1f, 90f))
                }
                editingNumber = null
            }
        )
    }
}

internal fun ViewerPaintMode.availableBrushShapes(): List<ViewerPaintBrushShape> = when (this) {
    ViewerPaintMode.Support -> listOf(
        ViewerPaintBrushShape.Circle,
        ViewerPaintBrushShape.Sphere,
        ViewerPaintBrushShape.Fill,
        ViewerPaintBrushShape.GapFill
    )
    ViewerPaintMode.Seam -> listOf(
        ViewerPaintBrushShape.Circle,
        ViewerPaintBrushShape.Sphere
    )
    ViewerPaintMode.Color -> listOf(
        ViewerPaintBrushShape.Circle,
        ViewerPaintBrushShape.Sphere,
        ViewerPaintBrushShape.Triangle,
        ViewerPaintBrushShape.HeightRange,
        ViewerPaintBrushShape.Fill,
        ViewerPaintBrushShape.GapFill
    )
    ViewerPaintMode.FuzzySkin -> listOf(
        ViewerPaintBrushShape.Circle,
        ViewerPaintBrushShape.Sphere,
        ViewerPaintBrushShape.Triangle,
        ViewerPaintBrushShape.Fill,
    )
}

internal fun ViewerPaintMode.availableActions(): List<ViewerPaintAction> = when (this) {
    ViewerPaintMode.Support,
    ViewerPaintMode.Seam -> listOf(ViewerPaintAction.Enforce, ViewerPaintAction.Block, ViewerPaintAction.Erase)
    ViewerPaintMode.Color,
    ViewerPaintMode.FuzzySkin -> listOf(ViewerPaintAction.Paint, ViewerPaintAction.Erase)
}

@Composable
private fun PaintSectionLabel(label: String, modifier: Modifier = Modifier) {
    Text(
        text = label,
        color = appTitleColor(),
        fontWeight = FontWeight.SemiBold,
        style = MaterialTheme.typography.bodySmall,
        modifier = modifier
    )
}

@Composable
private fun PaintSliderRow(
    label: String,
    valueField: @Composable () -> Unit,
    slider: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = appBodyColor(),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            valueField()
        }
        slider()
    }
}

@Composable
private fun PaintModeButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(label)
    }
}

@Composable
private fun PaintChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = if (selected) {
        ButtonDefaults.buttonColors()
    } else {
        ButtonDefaults.filledTonalButtonColors()
    }
    Button(
        onClick = onClick,
        colors = colors,
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(label)
    }
}

@Composable
private fun PaintNumberField(
    value: Float,
    suffix: String,
    decimals: Int,
    onEditRequested: () -> Unit
) {
    fun format(next: Float): String =
        if (decimals == 0) {
            next.toInt().toString()
        } else {
            java.lang.String.format(java.util.Locale.US, "%.${decimals}f", next)
        }

    Text(
        text = "${format(value)} $suffix",
        color = appBodyColor(),
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
        overflow = TextOverflow.Clip,
        modifier = Modifier
            .width(if (suffix == "mm") 96.dp else 92.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(appCardColorMuted().copy(alpha = 0.76f))
            .border(1.dp, appOutlineColor().copy(alpha = 0.46f), RoundedCornerShape(9.dp))
            .clickable(onClick = onEditRequested)
            .padding(horizontal = 10.dp, vertical = 9.dp)
    )
}

@Composable
private fun PaintLinearSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val rangeSize = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.0001f)
    val fraction = ((value.coerceIn(valueRange.start, valueRange.endInclusive) - valueRange.start) / rangeSize)
        .coerceIn(0f, 1f)
    val activeColor = MaterialTheme.colorScheme.primary
    val trackColor = appOutlineColor().copy(alpha = 0.28f)
    val thumbOutlineColor = appOutlineColor().copy(alpha = 0.58f)

    fun updateFromX(x: Float) {
        val width = size.width.toFloat()
        if (width <= 0f) return
        val inset = minOf(width * 0.08f, 22f)
        val usableWidth = (width - inset * 2f).coerceAtLeast(1f)
        val nextFraction = ((x - inset) / usableWidth).coerceIn(0f, 1f)
        onValueChange(valueRange.start + rangeSize * nextFraction)
    }

    Canvas(
        modifier = modifier
            .height(48.dp)
            .onSizeChanged { size = it }
            .pointerInput(valueRange) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    updateFromX(down.position.x)
                    drag(down.id) { change ->
                        updateFromX(change.position.x)
                        change.consume()
                    }
                }
            }
    ) {
        val inset = minOf(size.width * 0.08f, 22f)
        val startX = inset
        val endX = size.width - inset
        val centerY = size.height / 2f
        val thumbX = startX + (endX - startX) * fraction
        val trackStroke = 8.dp.toPx()
        val thumbRadius = 8.dp.toPx()
        drawLine(
            color = trackColor,
            start = androidx.compose.ui.geometry.Offset(startX, centerY),
            end = androidx.compose.ui.geometry.Offset(endX, centerY),
            strokeWidth = trackStroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = activeColor,
            start = androidx.compose.ui.geometry.Offset(startX, centerY),
            end = androidx.compose.ui.geometry.Offset(thumbX, centerY),
            strokeWidth = trackStroke,
            cap = StrokeCap.Round
        )
        drawCircle(
            color = activeColor,
            radius = thumbRadius,
            center = androidx.compose.ui.geometry.Offset(thumbX, centerY)
        )
        drawCircle(
            color = thumbOutlineColor,
            radius = thumbRadius,
            center = androidx.compose.ui.geometry.Offset(thumbX, centerY),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
        )
    }
}

@Composable
private fun PaintBrushRadiusSlider(
    radiusMm: Float,
    onRadiusChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val fraction = paintBrushSliderValue(radiusMm)
    val trackColor = appCardColorMuted().copy(alpha = 0.92f)
    val activeColor = MaterialTheme.colorScheme.primary
    val thumbOutlineColor = appOutlineColor().copy(alpha = 0.58f)

    fun updateFromX(x: Float) {
        val width = size.width.toFloat()
        if (width <= 0f) return
        val inset = minOf(width * 0.08f, 22f)
        val usableWidth = (width - inset * 2f).coerceAtLeast(1f)
        val nextFraction = ((x - inset) / usableWidth).coerceIn(0f, 1f)
        onRadiusChanged(paintBrushRadiusFromSliderValue(nextFraction))
    }

    Canvas(
        modifier = modifier
            .height(48.dp)
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    updateFromX(down.position.x)
                    drag(down.id) { change ->
                        updateFromX(change.position.x)
                        change.consume()
                    }
                }
            }
    ) {
        val inset = minOf(size.width * 0.08f, 22f)
        val startX = inset
        val endX = size.width - inset
        val centerY = size.height / 2f
        val thumbX = startX + (endX - startX) * fraction
        val trackStroke = 10.dp.toPx()
        val thumbRadius = 9.dp.toPx()
        drawLine(
            color = trackColor,
            start = androidx.compose.ui.geometry.Offset(startX, centerY),
            end = androidx.compose.ui.geometry.Offset(endX, centerY),
            strokeWidth = trackStroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = activeColor,
            start = androidx.compose.ui.geometry.Offset(startX, centerY),
            end = androidx.compose.ui.geometry.Offset(thumbX, centerY),
            strokeWidth = trackStroke,
            cap = StrokeCap.Round
        )
        drawCircle(
            color = activeColor,
            radius = thumbRadius,
            center = androidx.compose.ui.geometry.Offset(thumbX, centerY)
        )
        drawCircle(
            color = thumbOutlineColor,
            radius = thumbRadius,
            center = androidx.compose.ui.geometry.Offset(thumbX, centerY),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
        )
    }
}

@Composable
private fun PaintNumericDialog(
    field: PaintNumericField,
    brushRadiusMm: Float,
    smartFillAngleDeg: Float,
    overhangAngleDeg: Float,
    onDismiss: () -> Unit,
    onApply: (Float) -> Unit
) {
    val (title, initialValue, suffix, range, decimals) = when (field) {
        PaintNumericField.BrushSize -> PaintNumericSpec(
            "Cursor radius",
            effectivePaintBrushRadiusMm(brushRadiusMm),
            "mm",
            PaintBrushMinRadiusMm..PaintBrushMaxRadiusMm,
            1
        )
        PaintNumericField.SmartFillAngle -> PaintNumericSpec("Smart fill angle", smartFillAngleDeg, "deg", 0f..180f, 0)
        PaintNumericField.OverhangAngle -> PaintNumericSpec("Highlight overhangs", overhangAngleDeg, "deg", 1f..90f, 0)
    }
    fun format(next: Float): String =
        if (decimals == 0) {
            next.toInt().toString()
        } else {
            java.lang.String.format(java.util.Locale.US, "%.${decimals}f", next)
        }
    var text by remember(field, initialValue) {
        mutableStateOf(format(initialValue.coerceIn(range.start, range.endInclusive)))
    }
    val parsedValue = text.toFloatOrNull()?.coerceIn(range.start, range.endInclusive)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { raw -> text = cleanDecimalInput(raw) },
                singleLine = true,
                suffix = {
                    Text(
                        text = suffix,
                        color = appBodyColor(),
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
        },
        confirmButton = {
            TextButton(
                onClick = { parsedValue?.let(onApply) },
                enabled = parsedValue != null
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

private enum class PaintNumericField {
    BrushSize,
    SmartFillAngle,
    OverhangAngle
}

private data class PaintNumericSpec(
    val title: String,
    val initialValue: Float,
    val suffix: String,
    val range: ClosedFloatingPointRange<Float>,
    val decimals: Int
)

private fun cleanDecimalInput(raw: String): String {
    val trimmed = raw.trim()
    val builder = StringBuilder()
    var sawDecimal = false
    for (ch in trimmed) {
        when {
            ch.isDigit() -> builder.append(ch)
            ch == '.' && !sawDecimal -> {
                sawDecimal = true
                if (builder.isEmpty()) {
                    builder.append('0')
                }
                builder.append(ch)
            }
        }
    }
    return builder.toString()
}

@Composable
private fun FilamentPaintSwatch(colorInt: Int, selected: Boolean, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color(colorInt))
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else appOutlineColor(),
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
    }
}

private fun ViewerPaintMode.label(): String = when (this) {
    ViewerPaintMode.Color -> "Color"
    ViewerPaintMode.Seam -> "Seam"
    ViewerPaintMode.Support -> "Support"
    ViewerPaintMode.FuzzySkin -> "Fuzzy skin"
}

private fun ViewerPaintMode.sizeLabel(): String = when (this) {
    ViewerPaintMode.Color,
    ViewerPaintMode.Support -> "Cursor radius"
    ViewerPaintMode.Seam,
    ViewerPaintMode.FuzzySkin -> "Cursor radius"
}

private fun ViewerPaintAction.label(mode: ViewerPaintMode): String = when (this) {
    ViewerPaintAction.Paint -> when (mode) {
        ViewerPaintMode.Color -> "Paint"
        ViewerPaintMode.FuzzySkin -> "Paint"
        ViewerPaintMode.Support,
        ViewerPaintMode.Seam -> "Paint"
    }
    ViewerPaintAction.Erase -> when (mode) {
        ViewerPaintMode.Color -> "Erase"
        else -> "Erase"
    }
    ViewerPaintAction.Enforce -> "Enforce"
    ViewerPaintAction.Block -> "Block"
}

private fun ViewerPaintBrushShape.label(): String = when (this) {
    ViewerPaintBrushShape.Circle -> "Circle"
    ViewerPaintBrushShape.Sphere -> "Sphere"
    ViewerPaintBrushShape.Triangle -> "Triangle"
    ViewerPaintBrushShape.HeightRange -> "Height range"
    ViewerPaintBrushShape.Fill -> "Fill"
    ViewerPaintBrushShape.GapFill -> "Gap fill"
}

private fun ViewerPaintBrushShape.usesSmartFillAngle(mode: ViewerPaintMode): Boolean =
    this == ViewerPaintBrushShape.Fill && mode != ViewerPaintMode.Seam
