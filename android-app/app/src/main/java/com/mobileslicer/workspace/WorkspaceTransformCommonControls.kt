package com.mobileslicer.workspace

import com.mobileslicer.R
import com.mobileslicer.appBodyColor
import com.mobileslicer.appCardColorMuted
import com.mobileslicer.appOutlineColor
import com.mobileslicer.appTitleColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mobileslicer.ui.theme.LocalAppDarkTheme
import com.mobileslicer.viewer.PrinterBedSpec
import com.mobileslicer.viewer.ViewerModelTransform
import java.util.Locale

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

@Composable
internal fun CutSectionLabel(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = appTitleColor(),
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
internal fun TransformQuickButton(
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
internal fun TransformQuickButton(
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
internal fun PaintModeToolButton(label: String, compact: Boolean, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.height(if (compact) 34.dp else 40.dp),
        contentPadding = PaddingValues(horizontal = if (compact) 9.dp else 12.dp, vertical = 7.dp)
    ) {
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun CutStepButton(label: String, onClick: () -> Unit) {
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
