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

internal fun safeProjectFileName(name: String): String {
    val cleaned = name.map { char ->
        when {
            char.isLetterOrDigit() -> char
            char == '.' || char == '-' || char == '_' -> char
            else -> '_'
        }
    }.joinToString("").trim('_')
    return cleaned.ifBlank { "model" }
}

internal fun detectCalibrationGcodeFileName(gcodeFilePath: String): String? {
    val file = File(gcodeFilePath)
    if (!file.exists() || !file.isFile) return null
    val sample = runCatching {
        val sampleLength = file.length().coerceAtMost(4_194_304L).toInt()
        if (sampleLength <= 0) {
            ""
        } else {
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(sampleLength)
                val read = input.read(buffer)
                if (read <= 0) "" else buffer.decodeToString(endIndex = read)
            }
        }
    }.getOrDefault("")
    return when {
        "; start pressure advance pattern for layer" in sample || "pressure advance pattern" in sample ->
            calibrationGcodeFileNameForType("PressureAdvance")
        "Calib_Retraction_tower" in sample ->
            calibrationGcodeFileNameForType("Retraction")
        Regex("""flowrate_(?:m)?\d+\.stl""").containsMatchIn(sample) || "flowrate-test" in sample ->
            calibrationGcodeFileNameForType("FlowRate")
        else -> null
    }
}

internal data class PlateBounds(
    val minX: Float,
    val maxX: Float,
    val minY: Float,
    val maxY: Float,
    val minZ: Float,
    val maxZ: Float
)

internal data class RotatedPoint(
    val x: Float,
    val y: Float,
    val z: Float
)

internal fun transformedObjectBoundsOnPlate(
    bounds: MeshBounds,
    transform: ViewerModelTransform
): PlateBounds {
    val scale = transform.uniformScale.coerceIn(0.05f, 20f)
    val rotationXRadians = Math.toRadians(transform.rotationXDegrees.toDouble())
    val rotationYRadians = Math.toRadians(transform.rotationYDegrees.toDouble())
    val rotationZRadians = Math.toRadians(transform.rotationZDegrees.toDouble())
    val transformedCenter = rotateScaledPoint(
        x = bounds.centerX,
        y = bounds.centerY,
        z = bounds.centerZ,
        scale = scale,
        rotationXRadians = rotationXRadians,
        rotationYRadians = rotationYRadians,
        rotationZRadians = rotationZRadians,
        orientationMatrix = transform.orientationMatrix
    )

    var rotatedMinX = Float.POSITIVE_INFINITY
    var rotatedMaxX = Float.NEGATIVE_INFINITY
    var rotatedMinY = Float.POSITIVE_INFINITY
    var rotatedMaxY = Float.NEGATIVE_INFINITY
    var rotatedMinZ = Float.POSITIVE_INFINITY
    var rotatedMaxZ = Float.NEGATIVE_INFINITY
    val xs = floatArrayOf(bounds.minX, bounds.maxX)
    val ys = floatArrayOf(bounds.minY, bounds.maxY)
    val zs = floatArrayOf(bounds.minZ, bounds.maxZ)
    for (x in xs) {
        for (y in ys) {
            for (z in zs) {
                val point = rotateScaledPoint(
                    x = x,
                    y = y,
                    z = z,
                    scale = scale,
                    rotationXRadians = rotationXRadians,
                    rotationYRadians = rotationYRadians,
                    rotationZRadians = rotationZRadians,
                    orientationMatrix = transform.orientationMatrix
                )
                rotatedMinX = minOf(rotatedMinX, point.x)
                rotatedMaxX = maxOf(rotatedMaxX, point.x)
                rotatedMinY = minOf(rotatedMinY, point.y)
                rotatedMaxY = maxOf(rotatedMaxY, point.y)
                rotatedMinZ = minOf(rotatedMinZ, point.z)
                rotatedMaxZ = maxOf(rotatedMaxZ, point.z)
            }
        }
    }

    val offsetX = transform.centerXmm - transformedCenter.x
    val offsetY = transform.centerYmm - transformedCenter.y
    val offsetZ = -rotatedMinZ
    return PlateBounds(
        minX = rotatedMinX + offsetX,
        maxX = rotatedMaxX + offsetX,
        minY = rotatedMinY + offsetY,
        maxY = rotatedMaxY + offsetY,
        minZ = rotatedMinZ + offsetZ,
        maxZ = rotatedMaxZ + offsetZ
    )
}

internal fun rotateScaledPoint(
    x: Float,
    y: Float,
    z: Float,
    scale: Float,
    rotationXRadians: Double,
    rotationYRadians: Double,
    rotationZRadians: Double,
    orientationMatrix: List<Float>? = null
): RotatedPoint {
    var tx = x.toDouble() * scale
    var ty = y.toDouble() * scale
    var tz = z.toDouble() * scale
    if (orientationMatrix != null && orientationMatrix.size == 9) {
        return RotatedPoint(
            x = (orientationMatrix[0] * tx + orientationMatrix[1] * ty + orientationMatrix[2] * tz).toFloat(),
            y = (orientationMatrix[3] * tx + orientationMatrix[4] * ty + orientationMatrix[5] * tz).toFloat(),
            z = (orientationMatrix[6] * tx + orientationMatrix[7] * ty + orientationMatrix[8] * tz).toFloat()
        )
    }

    val cosX = cos(rotationXRadians)
    val sinX = sin(rotationXRadians)
    val yAfterX = ty * cosX - tz * sinX
    val zAfterX = ty * sinX + tz * cosX
    ty = yAfterX
    tz = zAfterX

    val cosY = cos(rotationYRadians)
    val sinY = sin(rotationYRadians)
    val xAfterY = tx * cosY + tz * sinY
    val zAfterY = -tx * sinY + tz * cosY
    tx = xAfterY
    tz = zAfterY

    val cosZ = cos(rotationZRadians)
    val sinZ = sin(rotationZRadians)
    return RotatedPoint(
        x = (tx * cosZ - ty * sinZ).toFloat(),
        y = (tx * sinZ + ty * cosZ).toFloat(),
        z = tz.toFloat()
    )
}

internal fun formatSavedProjectTimestamp(updatedAtEpochMs: Long): String {
    if (updatedAtEpochMs <= 0L) return "Saved date unknown"
    val formatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    return "Saved ${formatter.format(Date(updatedAtEpochMs))}"
}

internal fun writeCapturedProjectThumbnail(
    projectDir: File,
    bitmap: Bitmap?
): String? {
    if (bitmap == null || bitmap.isRecycled) return null
    return runCatching {
        val width = 320
        val height = 220
        val thumbnail = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(thumbnail)
        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.rgb(18, 25, 35)
            style = Paint.Style.FILL
        }
        val sourceAspect = bitmap.width.toFloat() / bitmap.height.toFloat()
        val targetAspect = width.toFloat() / height.toFloat()
        val sourceRect = if (sourceAspect > targetAspect) {
            val cropWidth = (bitmap.height * targetAspect).toInt()
            val left = (bitmap.width - cropWidth) / 2
            android.graphics.Rect(left, 0, left + cropWidth, bitmap.height)
        } else {
            val cropHeight = (bitmap.width / targetAspect).toInt()
            val top = (bitmap.height - cropHeight) / 2
            android.graphics.Rect(0, top, bitmap.width, top + cropHeight)
        }
        val targetRect = android.graphics.Rect(0, 0, width, height)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        canvas.drawBitmap(bitmap, sourceRect, targetRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))

        val thumbnailFile = File(projectDir, "thumbnail.png")
        FileOutputStream(thumbnailFile).use { output ->
            thumbnail.compress(Bitmap.CompressFormat.PNG, 92, output)
        }
        thumbnail.recycle()
        thumbnailFile.absolutePath
    }.getOrNull()
}
