package com.mobileslicer

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import com.mobileslicer.profiles.ProfileStore
import com.mobileslicer.profiles.PrintHostType
import com.mobileslicer.profiles.PrinterProfile
import com.mobileslicer.profiles.ProfilesLandingSection
import com.mobileslicer.profiles.ProfilesScreen
import com.mobileslicer.profiles.activeConfiguration
import com.mobileslicer.profiles.toNativeSliceConfigJson
import com.mobileslicer.printerconnection.PrinterConnectionChoicesResult
import com.mobileslicer.printerconnection.PrinterConnectionResult
import com.mobileslicer.printerconnection.PrinterUploadAction
import com.mobileslicer.printerconnection.SimplyPrintOAuthResult
import com.mobileslicer.calibration.CalibrationJob
import com.mobileslicer.calibration.PrinterCalibrationsScreen
import com.mobileslicer.calibration.writeOrcaCalibrationModels
import com.mobileslicer.storage.GCODE_MIME_TYPE
import com.mobileslicer.storage.SavedProject
import com.mobileslicer.storage.SavedProjectPlateObject
import com.mobileslicer.workspace.AppScreen
import com.mobileslicer.workspace.ImportedModelFormat
import com.mobileslicer.workspace.ModelLoadResult
import com.mobileslicer.workspace.PlateObject
import com.mobileslicer.workspace.SliceResult
import com.mobileslicer.workspace.WorkspaceMode
import com.mobileslicer.workspace.WorkspacePreparationResult
import com.mobileslicer.workspace.WorkspaceScreen
import com.mobileslicer.workspace.WorkspaceSessionViewModel
import com.mobileslicer.workspace.defaultViewerModelTransform
import com.mobileslicer.workspace.formatDurationMs
import com.mobileslicer.workspace.modelLoadStatusMessage
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import com.mobileslicer.viewer.MeshBounds
import com.mobileslicer.viewer.StlMesh
import com.mobileslicer.viewer.StlMeshParser
import com.mobileslicer.nativebridge.NativeEngineBridge
import com.mobileslicer.ui.theme.AccentPaletteOption
import com.mobileslicer.viewer.PrinterBedSpec
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
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sqrt
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
internal fun HomeTopBar(
    onOpenSettings: () -> Unit
) {
    val titleColor = appTitleColor()
    val bodyColor = appBodyColor()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_logo),
                contentDescription = "Mobile Slicer logo",
                tint = Color.Unspecified,
                modifier = Modifier.size(29.dp)
            )
            Column {
                Text(
                    text = "Mobile Slicer",
                    style = MaterialTheme.typography.titleLarge,
                    color = titleColor,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Local touch-first 3D slicer",
                    style = MaterialTheme.typography.bodySmall,
                    color = bodyColor
                )
            }
        }
        TopBarSettingsButton(
            onClick = onOpenSettings
        )
    }
}

@Composable
internal fun TopBarSettingsButton(
    onClick: () -> Unit
) {
    val surfaceColor = appCardColorMuted()
    val outlineColor = appOutlineColor()
    val iconColor = appTitleColor()
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(surfaceColor)
            .border(1.dp, outlineColor, RoundedCornerShape(18.dp))
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(52.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = "Settings",
                tint = iconColor,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
internal fun HeroImportCard(
    importedModel: String,
    importInProgress: Boolean,
    onSelectModel: () -> Unit
) {
    LandingIsland(
        title = "Import an STL or 3MF",
        body = "Choose a model from your device, then prepare it on the bed before slicing.",
        footer = "Current model: $importedModel",
        compact = true
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SoftPill(label = "File Manager")
            SoftPill(label = "STL / 3MF")
        }
        Button(
            onClick = onSelectModel,
            enabled = !importInProgress,
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
            shape = RoundedCornerShape(18.dp)
        ) {
            Text(
                when {
                    importInProgress -> "Importing..."
                    importedModel != "No model imported" -> "Import another model"
                    else -> "Open model"
                }
            )
        }
    }
}

@Composable
internal fun PrinterCalibrationsLandingSection(
    importInProgress: Boolean,
    onCalibrationsClick: () -> Unit
) {
    LandingIsland(
        title = "Printer Calibrations",
        body = "Create calibration prints for tuning flow, temperature, pressure advance, and other printer settings.",
        footer = "Uses your selected printer, filament, and process.",
        compact = true
    ) {
        Button(
            onClick = onCalibrationsClick,
            enabled = !importInProgress,
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
            shape = RoundedCornerShape(18.dp)
        ) {
            Text("Printer calibrations")
        }
    }
}

@Composable
internal fun CompactWorkspaceBadge(
    label: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false
) {
    val outlineColor = appOutlineColor()
    val textColor = if (emphasized) appTitleColor() else appBodyColor()
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(appCardColor().copy(alpha = if (emphasized) 0.48f else 0.28f))
            .border(1.dp, outlineColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 9.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

internal fun ProfileStore.profileRequirementMessage(): String? {
    val missingPrinter = printers.isEmpty()
    val missingFilament = filaments.isEmpty()
    val missingProcess = selectedProcessId.isBlank() ||
        processes.none { it.id == selectedProcessId && it.printerProfileId == selectedPrinterId }
    return when {
        missingPrinter && missingFilament -> "Choose a printer and filament in Profiles before slicing."
        missingPrinter -> "Choose a printer in Profiles before slicing."
        missingFilament -> "Choose a filament in Profiles before slicing."
        missingProcess -> "Choose a process in Profiles before slicing."
        else -> null
    }
}

@Composable
internal fun SoftPill(
    label: String,
    modifier: Modifier = Modifier
) {
    CompactWorkspaceBadge(label = label, modifier = modifier)
}

@Composable
internal fun SavedProjectsLandingSection(
    projects: List<SavedProject>,
    importInProgress: Boolean,
    onOpenProject: (SavedProject) -> Unit,
    onDeleteProject: (SavedProject) -> Unit
) {
    val titleColor = appTitleColor()
    val bodyColor = appBodyColor()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = appCardColor().copy(alpha = 0.7f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, appOutlineColor().copy(alpha = 0.64f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Saved Projects",
                style = MaterialTheme.typography.titleMedium,
                color = titleColor,
                fontWeight = FontWeight.SemiBold
            )
            if (projects.isEmpty()) {
                Text(
                    text = "Saved plates keep model files, transforms, and profile settings together.",
                    style = MaterialTheme.typography.bodySmall,
                    color = bodyColor
                )
                SoftPill(label = "0 saved")
            } else {
                projects.forEachIndexed { index, project ->
                    if (index > 0) {
                        HorizontalDivider(color = appOutlineColor().copy(alpha = 0.28f))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SavedProjectThumbnail(project = project)
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = project.name,
                                style = MaterialTheme.typography.labelLarge,
                                color = titleColor,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${project.plateObjects.size} object${if (project.plateObjects.size == 1) "" else "s"} • ${project.profileStore.activeConfiguration().nativeSliceTitle()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = bodyColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = formatSavedProjectTimestamp(project.updatedAtEpochMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = bodyColor.copy(alpha = 0.82f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        TextButton(
                            onClick = { onOpenProject(project) },
                            enabled = !importInProgress
                        ) {
                            Text("Open")
                        }
                        TextButton(
                            onClick = { onDeleteProject(project) },
                            enabled = !importInProgress
                        ) {
                            Text("Delete")
                        }
                    }
                }
                SoftPill(label = "${projects.size} saved")
            }
        }
    }
}

@Composable
internal fun SavedProjectThumbnail(project: SavedProject) {
    val thumbnailBitmap = remember(project.thumbnailPath) {
        project.thumbnailPath
            ?.let(::File)
            ?.takeIf { it.exists() }
            ?.let { BitmapFactory.decodeFile(it.absolutePath) }
            ?.asImageBitmap()
    }
    Box(
        modifier = Modifier
            .size(54.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(appCardColorMuted().copy(alpha = 0.58f))
            .border(1.dp, appOutlineColor().copy(alpha = 0.54f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (thumbnailBitmap != null) {
            Image(
                bitmap = thumbnailBitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = project.name.firstOrNull()?.uppercaseChar()?.toString() ?: "P",
                style = MaterialTheme.typography.titleMedium,
                color = appBodyColor()
            )
        }
    }
}

@Composable
internal fun LandingIsland(
    title: String,
    body: String,
    footer: String,
    compact: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val darkTheme = LocalAppDarkTheme.current
    val titleColor = appTitleColor()
    val bodyColor = appBodyColor()
    val shape = RoundedCornerShape(if (compact) 24.dp else 30.dp)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = if (compact) 4.dp else 6.dp)
    ) {
        Box(
            modifier = Modifier
                .clip(shape)
                .background(
                    brush = Brush.linearGradient(
                        colors = if (darkTheme) {
                            listOf(Color(0xFF1E2736), Color(0xFF171F2B), Color(0xFF131A25))
                        } else {
                            listOf(Color(0xFFFFFFFF), Color(0xFFF4F7FB), Color(0xFFE9EFF7))
                        }
                    )
                )
                .border(1.dp, appOutlineColor(), shape)
                .padding(if (compact) 16.dp else 20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp), content = {
                Text(
                    text = title,
                    style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                    color = titleColor,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = body,
                    style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                    color = bodyColor
                )
                content()
                Text(
                    text = footer,
                    style = MaterialTheme.typography.bodySmall,
                    color = bodyColor
                )
            })
        }
    }
}
