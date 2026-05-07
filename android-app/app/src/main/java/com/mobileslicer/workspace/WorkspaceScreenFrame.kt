package com.mobileslicer.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mobileslicer.appBackgroundGradient
import com.mobileslicer.appCardColorMuted
import com.mobileslicer.appTitleColor

@Composable
internal fun WorkspaceScreenFrame(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = appBackgroundGradient()
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding(),
        content = content
    )
}

@Composable
internal fun LayFacePickHint(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.widthIn(min = 132.dp),
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 8.dp,
        color = appCardColorMuted().copy(alpha = 0.96f)
    ) {
        Text(
            text = "Tap a face",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 10.dp),
            style = MaterialTheme.typography.titleSmall,
            color = appTitleColor(),
            textAlign = TextAlign.Center
        )
    }
}
