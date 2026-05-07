package com.mobileslicer.workspace

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.mobileslicer.R
import com.mobileslicer.appBodyColor
import com.mobileslicer.appCardColorMuted

@Composable
internal fun DirectMoveLockButton(
    unlocked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(17.dp)
    val lockedContentColor = appBodyColor().copy(alpha = 0.58f)
    val containerColor = when {
        !enabled -> MaterialTheme.colorScheme.surface.copy(alpha = 0.48f)
        unlocked -> MaterialTheme.colorScheme.primary.copy(alpha = 0.94f)
        else -> appCardColorMuted().copy(alpha = 0.88f)
    }
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f)
        unlocked -> MaterialTheme.colorScheme.onPrimary
        else -> lockedContentColor
    }
    Surface(
        modifier = modifier
            .size(width = 46.dp, height = 44.dp)
            .clickable(enabled = enabled, onClick = onToggle),
        shape = shape,
        color = containerColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = if (unlocked) 0.18f else 0.58f)
        )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(R.drawable.ic_tool_move),
                contentDescription = if (unlocked) "Move unlocked" else "Move locked",
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
            if (!unlocked) {
                Canvas(modifier = Modifier.size(26.dp)) {
                    drawLine(
                        color = contentColor,
                        start = Offset(size.width * 0.18f, size.height * 0.82f),
                        end = Offset(size.width * 0.82f, size.height * 0.18f),
                        strokeWidth = 2.8.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}
