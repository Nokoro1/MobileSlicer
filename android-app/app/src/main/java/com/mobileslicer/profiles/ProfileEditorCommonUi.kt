package com.mobileslicer.profiles

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import com.mobileslicer.AppSettingOption
import com.mobileslicer.SoftPill
import com.mobileslicer.appBackgroundGradient
import com.mobileslicer.appBodyColor
import com.mobileslicer.appCardColor
import com.mobileslicer.appCardColorMuted
import com.mobileslicer.appMutedColor
import com.mobileslicer.appOutlineColor
import com.mobileslicer.appTitleColor
import com.mobileslicer.printerconnection.PrinterConnectionChoicesResult
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import com.mobileslicer.viewer.StlMesh
import com.mobileslicer.nativebridge.NativeEngineBridge
import com.mobileslicer.ui.theme.AccentPaletteOption
import com.mobileslicer.viewer.PrinterBedSpec
import com.mobileslicer.printerconnection.SimplyPrintOAuthResult
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
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlin.math.cos
import kotlin.math.sqrt
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@Composable
internal fun ProfileEditorScreenScaffold(
    title: String? = null,
    subtitle: String? = "Edit and save this profile on its own page.",
    saveLabel: String,
    onBack: () -> Unit,
    onSave: () -> Unit,
    headerContent: (@Composable ColumnScope.() -> Unit)? = null,
    topContent: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val titleColor = appTitleColor()
    val bodyColor = appBodyColor()
    val outlineColor = appOutlineColor()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = Brush.verticalGradient(colors = appBackgroundGradient()))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(appCardColorMuted())
                    .border(1.dp, outlineColor, RoundedCornerShape(18.dp))
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = titleColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (headerContent != null) {
                    headerContent()
                } else {
                    if (title != null) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = titleColor
                        )
                    }
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = bodyColor
                        )
                    }
                }
            }
            Button(
                onClick = onSave,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                )
            ) {
                Text(saveLabel)
            }
        }
        if (topContent != null) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = topContent
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

internal enum class ProcessEditorTab(val label: String) {
    Quality("Quality"),
    Strength("Strength"),
    Speed("Speed"),
    Support("Support"),
    Multimaterial("Multimaterial"),
    Others("Others")
}

internal enum class PrinterEditorTab(val label: String) {
    BasicInformation("Basic information"),
    Connection("Connection"),
    MachineGcode("Machine G-code"),
    Multimaterial("Multimaterial"),
    Extruder("Extruder"),
    MotionAbility("Motion ability"),
    Notes("Notes")
}

internal enum class FilamentEditorTab(val label: String) {
    Filament("Filament"),
    Cooling("Cooling"),
    SettingOverrides("Setting Overrides"),
    Advanced("Advanced"),
    Multimaterial("Multimaterial"),
    Dependencies("Dependencies"),
    Notes("Notes")
}

internal fun processEditorTabLabelsForParityTest(): List<String> = ProcessEditorTab.entries.map { it.label }
internal fun filamentEditorTabLabelsForParityTest(): List<String> = FilamentEditorTab.entries.map { it.label }
internal fun printerEditorTabLabelsForParityTest(): List<String> = PrinterEditorTab.entries.map { it.label }

@Composable
internal fun ConnectionStatusDialog(
    status: String?,
    refreshing: Boolean = false,
    onDismiss: () -> Unit
) {
    if (status == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        },
        title = { Text("Printer connection") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(status)
                if (refreshing) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    )
}

@Composable
internal fun <T> ProfileEditorTabRow(
    tabs: List<T>,
    selectedTab: T,
    labelFor: (T) -> String,
    onSelected: (T) -> Unit
) {
    val titleColor = appTitleColor()
    val bodyColor = appBodyColor()
    val outlineColor = appOutlineColor()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEach { tab ->
            TextButton(
                onClick = { onSelected(tab) },
                modifier = Modifier.border(
                    width = if (tab == selectedTab) 1.dp else 0.dp,
                    color = if (tab == selectedTab) outlineColor else Color.Transparent,
                    shape = RoundedCornerShape(14.dp)
                ),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = labelFor(tab),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (tab == selectedTab) titleColor else bodyColor
                )
            }
        }
    }
}

@Composable
internal fun ProfileGroupHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = appTitleColor(),
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
internal fun ProfileEditorSection(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    val titleColor = appTitleColor()
    val bodyColor = appBodyColor()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = appCardColorMuted())
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = titleColor,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = bodyColor
                )
                content()
            }
        )
    }
}

@Composable
internal fun ProfileTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
    )
}

@Composable
internal fun ProfileMultilineTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        minLines = 4,
        maxLines = 8,
        shape = RoundedCornerShape(16.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
    )
}

internal fun String.parseFloatAtLeast(min: Float): Float? =
    toFloatOrNull()?.takeIf { it >= min }

internal fun String.parseFloatGreaterThanZero(): Float? =
    toFloatOrNull()?.takeIf { it > 0f }

internal fun String.parseFloatIn(min: Float, max: Float): Float? =
    toFloatOrNull()?.takeIf { it in min..max }

internal fun String.parseOptionalFloatAtLeast(min: Float, fallback: Float?): Float? {
    val trimmed = trim()
    if (trimmed.isEmpty()) return null
    return trimmed.toFloatOrNull()?.takeIf { it >= min } ?: fallback
}

internal fun String.parseIntAtLeast(min: Int): Int? =
    toIntOrNull()?.takeIf { it >= min }

internal fun String.parseIntIn(min: Int, max: Int): Int? =
    toIntOrNull()?.takeIf { it in min..max }

internal fun String.parseOptionalIntIn(min: Int, max: Int, fallback: Int?): Int? {
    val trimmed = trim()
    if (trimmed.isEmpty()) return null
    return trimmed.toIntOrNull()?.takeIf { it in min..max } ?: fallback
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> ProfileDropdownField(
    label: String,
    selectedLabel: String,
    options: List<AppSettingOption<T>>,
    onSelected: (T) -> Unit
) {
    var pickerOpen by remember { mutableStateOf(false) }
    val titleColor = appTitleColor()
    val bodyColor = appBodyColor()
    val outlineColor = appOutlineColor()

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
            label = { Text(label) },
            singleLine = true,
            readOnly = true,
            shape = RoundedCornerShape(16.dp),
            trailingIcon = {
                Text(
                    text = "▼",
                    style = MaterialTheme.typography.labelMedium,
                    color = bodyColor
                )
            }
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { pickerOpen = true }
        )
    }

    if (pickerOpen) {
        ModalBottomSheet(
            onDismissRequest = { pickerOpen = false },
            containerColor = appCardColor().copy(alpha = 0.98f),
            contentColor = titleColor,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(top = 10.dp, bottom = 4.dp)
                        .size(width = 44.dp, height = 4.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(outlineColor.copy(alpha = 0.75f))
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        color = titleColor,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleLarge
                    )
                    TextButton(onClick = { pickerOpen = false }) {
                        Text("Close")
                    }
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(options, key = { it.title }) { option ->
                        val selected = option.title == selectedLabel
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    onSelected(option.value)
                                    pickerOpen = false
                                },
                            color = if (selected) appCardColorMuted() else Color.Transparent,
                            shape = RoundedCornerShape(16.dp),
                            border = if (selected) {
                                androidx.compose.foundation.BorderStroke(1.dp, outlineColor)
                            } else {
                                null
                            }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = option.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = titleColor,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (selected) {
                                    Text(
                                        text = "Selected",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
