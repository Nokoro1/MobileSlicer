package com.mobileslicer

import android.Manifest
import android.annotation.SuppressLint
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
import com.mobileslicer.printerconnection.requireAllowedPrinterNetworkUrl
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
import androidx.compose.runtime.mutableIntStateOf
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
@SuppressLint("SetJavaScriptEnabled")
internal fun PrinterBrowserScreen(
    url: String,
    printerName: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var browserStatus by remember(url) { mutableStateOf("Loading printer UI...") }
    var currentUrl by remember(url) { mutableStateOf(url) }
    var showingCamera by remember(url) { mutableStateOf(false) }
    var cameraReloadKey by remember { mutableIntStateOf(0) }
    val fallbackCameraUrl = remember(url) { defaultPrinterCameraUrl(url) }
    val detectedCamera by produceState<PrinterCameraStream?>(initialValue = null, url) {
        value = withContext(Dispatchers.IO) { discoverMoonrakerCameraStream(url) }
    }
    val cameraUrl = detectedCamera?.streamUrl ?: fallbackCameraUrl
    var pendingCameraRequest by remember { mutableStateOf<PermissionRequest?>(null) }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val request = pendingCameraRequest
        pendingCameraRequest = null
        if (request != null) {
            if (granted) {
                request.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
            } else {
                request.deny()
            }
        }
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(appCardColor())
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = appTitleColor()
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = printerName,
                    style = MaterialTheme.typography.titleMedium,
                    color = appTitleColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = url,
                    style = MaterialTheme.typography.bodySmall,
                    color = appBodyColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(onClick = {
                showingCamera = true
                currentUrl = cameraUrl
                browserStatus = detectedCamera?.let { "Camera: ${it.name}" } ?: "Camera: default stream"
            }) {
                Text("Camera")
            }
            TextButton(onClick = {
                showingCamera = false
                currentUrl = url
                browserStatus = "Loading printer UI..."
            }) {
                Text("Printer UI")
            }
            if (showingCamera) {
                TextButton(onClick = {
                    cameraReloadKey += 1
                    browserStatus = "Refreshing camera..."
                }) {
                    Text("Refresh")
                }
            }
        }
        HorizontalDivider(color = appOutlineColor().copy(alpha = 0.45f))
        if (browserStatus.isNotBlank()) {
            Text(
                text = browserStatus,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(appCardColorMuted().copy(alpha = 0.72f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = appBodyColor(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (url.isBlank()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Printer UI is not configured.",
                    color = appBodyColor()
                )
            }
        } else {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, pageUrl: String?) {
                                browserStatus = if (showingCamera) "Camera stream loaded" else "Printer UI loaded"
                            }

                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val target = request?.url?.toString().orEmpty()
                                if (target.isBlank() || isTrustedPrinterWebUrl(url, target)) {
                                    return false
                                }
                                browserStatus = "Blocked external page: ${safePrinterDisplayUrl(target)}"
                                return true
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?
                            ) {
                                if (request?.isForMainFrame == true) {
                                    browserStatus = "Page error: ${error?.description ?: "Unable to load printer UI"}"
                                }
                            }

                            override fun onReceivedSslError(
                                view: WebView?,
                                handler: android.webkit.SslErrorHandler?,
                                error: android.net.http.SslError?
                            ) {
                                val target = error?.url ?: view?.url.orEmpty()
                                browserStatus = "Blocked SSL certificate warning from ${safePrinterDisplayUrl(target)}"
                                handler?.cancel()
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                val message = consoleMessage?.message().orEmpty()
                                if (message.contains("camera", ignoreCase = true) ||
                                    message.contains("media", ignoreCase = true) ||
                                    message.contains("stream", ignoreCase = true) ||
                                    message.contains("permission", ignoreCase = true)
                                ) {
                                    browserStatus = message
                                }
                                return true
                            }

                            override fun onPermissionRequest(request: PermissionRequest) {
                                val needsCamera = request.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
                                val origin = request.origin?.toString().orEmpty()
                                if (!needsCamera || !isTrustedPrinterWebUrl(url, origin)) {
                                    request.deny()
                                    return
                                }
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                    browserStatus = "Camera permission granted to printer UI"
                                    request.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
                                } else {
                                    browserStatus = "Printer UI requested camera permission"
                                    pendingCameraRequest = request
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            }
                        }
                        // Local printer dashboards commonly require JavaScript; keep it scoped to trusted printer origins.
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        settings.allowContentAccess = false
                        settings.allowFileAccess = false
                        if (showingCamera) {
                            tag = "camera:$currentUrl:$cameraReloadKey"
                            loadUrl(cacheBustedCameraUrl(currentUrl, cameraReloadKey))
                        } else {
                            tag = "ui:$currentUrl"
                            loadUrl(currentUrl)
                        }
                    }
                },
                update = { webView ->
                    if (showingCamera) {
                        val key = "camera:$currentUrl:$cameraReloadKey"
                        if (webView.tag != key) {
                            val nextCameraUrl = cacheBustedCameraUrl(currentUrl, cameraReloadKey)
                            webView.tag = key
                            webView.loadUrl(nextCameraUrl)
                        }
                    } else {
                        if (webView.url != currentUrl) {
                            webView.tag = "ui:$currentUrl"
                            webView.loadUrl(currentUrl)
                        }
                    }
                }
            )
        }
    }
}

private data class PrinterCameraStream(
    val name: String,
    val streamUrl: String
)

private fun discoverMoonrakerCameraStream(printerUiUrl: String): PrinterCameraStream? {
    val origins = moonrakerOrigins(printerUiUrl)
    for (origin in origins) {
        val result = runCatching {
            val webcamListUrl = "$origin/server/webcams/list"
            requireAllowedPrinterNetworkUrl(webcamListUrl)
            val connection = (URL(webcamListUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 2_500
                readTimeout = 4_000
                useCaches = false
            }
            val code = connection.responseCode
            if (code !in 200..299) return@runCatching null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val webcams = JSONObject(body).optJSONArray("webcams") ?: return@runCatching null
            for (index in 0 until webcams.length()) {
                val webcam = webcams.optJSONObject(index) ?: continue
                if (!webcam.optBoolean("enabled", true)) continue
                val streamUrl = webcam.optString("stream_url").trim()
                if (streamUrl.isBlank()) continue
                if (!isWebViewMjpegLikeStream(streamUrl)) continue
                return@runCatching PrinterCameraStream(
                    name = webcam.optString("name", "Camera"),
                    streamUrl = resolvePrinterUrl(origin, streamUrl)
                )
            }
            null
        }.getOrNull()
        if (result != null) return result
    }
    return null
}

internal fun normalizePrinterWebUiUrl(input: String): String? {
    val trimmed = input.trim()
    if (trimmed.isBlank()) return null
    val withScheme = if ("://" in trimmed) trimmed else "http://$trimmed"
    return runCatching {
        val uri = URI(withScheme)
        val scheme = uri.scheme ?: "http"
        val host = uri.host ?: return@runCatching null
        if (scheme != "http" && scheme != "https") return@runCatching null
        if (scheme == "http" && !isLocalPrinterHost(host)) return@runCatching null
        val port = if (uri.port >= 0) ":${uri.port}" else ""
        val path = uri.rawPath.orEmpty().ifBlank { "/" }
        val query = uri.rawQuery?.let { "?$it" }.orEmpty()
        val fragment = uri.rawFragment?.let { "#$it" }.orEmpty()
        "$scheme://$host$port$path$query$fragment"
    }.getOrNull()
}

private fun moonrakerOrigins(printerUiUrl: String): List<String> {
    val uri = runCatching { URI(printerUiUrl) }.getOrNull() ?: return emptyList()
    val scheme = uri.scheme ?: "http"
    val host = uri.host ?: return emptyList()
    val basePort = if (uri.port >= 0) ":${uri.port}" else ""
    val origins = mutableListOf("$scheme://$host$basePort")
    if (uri.port < 0) {
        origins += "$scheme://$host:10088"
        origins += "$scheme://$host:7125"
    }
    return origins.distinct()
}

private fun isTrustedPrinterWebUrl(rootUrl: String, targetUrl: String): Boolean {
    val root = runCatching { URI(rootUrl) }.getOrNull() ?: return false
    val target = runCatching { URI(targetUrl) }.getOrNull() ?: return false
    val targetScheme = target.scheme?.lowercase(Locale.US)
    if (targetScheme == "about" || targetScheme == "data") return true
    if (targetScheme != "http" && targetScheme != "https") return false
    val rootHost = root.host?.lowercase(Locale.US)
    val targetHost = target.host?.lowercase(Locale.US)
    if (rootHost != null && rootHost == targetHost) {
        return targetScheme == "https" || isLocalPrinterHost(target.host)
    }
    return isLocalPrinterUrl(targetUrl)
}

private fun isLocalPrinterUrl(url: String): Boolean {
    val uri = runCatching { URI(url) }.getOrNull() ?: return false
    val scheme = uri.scheme?.lowercase(Locale.US)
    return scheme == "http" && isLocalPrinterHost(uri.host) ||
        scheme == "https" && isLocalPrinterHost(uri.host)
}

private fun isLocalPrinterHost(host: String?): Boolean {
    val normalized = host?.trim()?.trim('[', ']')?.lowercase(Locale.US).orEmpty()
    if (normalized.isBlank()) return false
    if (normalized == "localhost" || normalized.endsWith(".local") || "." !in normalized && ":" !in normalized) return true
    val octets = normalized.split('.').mapNotNull { it.toIntOrNull() }
    if (octets.size == 4 && octets.all { it in 0..255 }) {
        return octets[0] == 10 ||
            octets[0] == 127 ||
            octets[0] == 192 && octets[1] == 168 ||
            octets[0] == 172 && octets[1] in 16..31 ||
            octets[0] == 169 && octets[1] == 254
    }
    return normalized == "::1" ||
        normalized.startsWith("fe80:") ||
        normalized.startsWith("fc") ||
        normalized.startsWith("fd")
}

private fun safePrinterDisplayUrl(url: String): String =
    runCatching {
        val uri = URI(url)
        val scheme = uri.scheme ?: "http"
        val host = uri.host ?: return@runCatching url.substringBefore('?').substringBefore('#')
        val port = if (uri.port >= 0) ":${uri.port}" else ""
        val path = uri.rawPath.orEmpty()
        "$scheme://$host$port$path"
    }.getOrElse { url.substringBefore('?').substringBefore('#') }

private fun resolvePrinterUrl(origin: String, url: String): String =
    if ("://" in url) {
        url
    } else {
        val path = if (url.startsWith("/")) url else "/$url"
        "$origin$path"
    }

private fun defaultPrinterCameraUrl(printerUiUrl: String): String {
    val uri = runCatching { URI(printerUiUrl) }.getOrNull() ?: return printerUiUrl
    val scheme = uri.scheme ?: "http"
    val host = uri.host ?: return printerUiUrl
    val port = if (uri.port >= 0) ":${uri.port}" else ""
    return "$scheme://$host$port/webcam/?action=stream"
}

private fun cacheBustedCameraUrl(cameraUrl: String, reloadKey: Int): String {
    val separator = if ("?" in cameraUrl) "&" else "?"
    return "$cameraUrl${separator}mobile_slicer_reload=$reloadKey"
}

private fun isWebViewMjpegLikeStream(url: String): Boolean {
    val lower = url.lowercase(Locale.US)
    return "action=stream" in lower ||
        "mjpg" in lower ||
        "mjpeg" in lower ||
        "/webcam" in lower
}
