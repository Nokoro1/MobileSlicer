package com.mobileslicer.printerconnection

import com.mobileslicer.profiles.PrintHostType
import com.mobileslicer.profiles.PrinterProfile
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URL
import java.net.UnknownHostException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val ELEGOO_UPLOAD_CHUNK_BYTES = 1_048_576L

internal class PrinterConnectionRepository {
    private val webSocketClient = PrinterWebSocketClient(runNetwork = ::runNetwork)
    private val simplyPrintClient = SimplyPrintConnectionClient(
        requestTextBody = { url, method, headers -> requestTextBody(url, method, headers) },
        requestFormBody = { url, fields, headers -> requestFormBody(url, fields, headers) },
        uploadMultipartBody = { url, headers, fields, file, fileFieldName, remoteFileName, offset, length, totalProgressBytes, progressOffsetBytes, onProgress ->
            uploadMultipartBody(
                url = url,
                headers = headers,
                fields = fields,
                file = file,
                fileFieldName = fileFieldName,
                remoteFileName = remoteFileName,
                offset = offset,
                length = length,
                totalProgressBytes = totalProgressBytes,
                progressOffsetBytes = progressOffsetBytes,
                onProgress = onProgress
            )
        },
        uploadMultipartFieldsBody = { url, headers, fields -> uploadMultipartFieldsBody(url, headers, fields) }
    )
    private val mksClient = MksConnectionClient(
        printerHostName = { baseUrl -> printerHostName(baseUrl) },
        safeDisplayUrl = { url -> safeDisplayUrl(url) },
        urlEncode = { value -> urlEncode(value) },
        sendTcpConsoleCommands = { host, port, commands, delayBeforeMs ->
            sendTcpConsoleCommands(host = host, port = port, commands = commands, delayBeforeMs = delayBeforeMs)
        },
        uploadRawFileBody = { url, method, headers, file, onProgress, successCodes ->
            uploadRawFileBody(
                url = url,
                method = method,
                headers = headers,
                file = file,
                onProgress = onProgress,
                successCodes = successCodes
            )
        }
    )
    private val esp3dClient = Esp3dConnectionClient(
        requestText = { url, method, headers -> requestText(url, method, headers) },
        requestTextBody = { url, method, headers -> requestTextBody(url, method, headers) },
        uploadMultipart = { url, headers, fields, file, fileFieldName, remoteFileName, onProgress ->
            uploadMultipart(
                url = url,
                headers = headers,
                fields = fields,
                file = file,
                fileFieldName = fileFieldName,
                remoteFileName = remoteFileName,
                onProgress = onProgress
            )
        }
    )
    private val flashAirClient = FlashAirConnectionClient(
        requestTextBody = { url, method, headers -> requestTextBody(url, method, headers) },
        uploadMultipartBody = { url, headers, fields, file, fileFieldName, remoteFileName, offset, length, totalProgressBytes, progressOffsetBytes, onProgress ->
            uploadMultipartBody(
                url = url,
                headers = headers,
                fields = fields,
                file = file,
                fileFieldName = fileFieldName,
                remoteFileName = remoteFileName,
                offset = offset,
                length = length,
                totalProgressBytes = totalProgressBytes,
                progressOffsetBytes = progressOffsetBytes,
                onProgress = onProgress
            )
        }
    )
    private val repetierClient = RepetierConnectionClient(
        requestTextBody = { url, method, headers -> requestTextBody(url, method, headers) },
        uploadMultipart = { url, headers, fields, file, fileFieldName, remoteFileName, onProgress ->
            uploadMultipart(
                url = url,
                headers = headers,
                fields = fields,
                file = file,
                fileFieldName = fileFieldName,
                remoteFileName = remoteFileName,
                onProgress = onProgress
            )
        }
    )
    private val obicoClient = ObicoConnectionClient(
        requestText = { url, method, headers -> requestText(url, method, headers) },
        requestTextBody = { url, method, headers -> requestTextBody(url, method, headers) },
        uploadMultipart = { url, headers, fields, file, fileFieldName, remoteFileName, onProgress ->
            uploadMultipart(
                url = url,
                headers = headers,
                fields = fields,
                file = file,
                fileFieldName = fileFieldName,
                remoteFileName = remoteFileName,
                onProgress = onProgress
            )
        }
    )
    private val astroBoxClient = AstroBoxConnectionClient(
        requestTextBody = { url, method, headers -> requestTextBody(url, method, headers) },
        uploadMultipart = { url, headers, fields, file, fileFieldName, remoteFileName, onProgress ->
            uploadMultipart(
                url = url,
                headers = headers,
                fields = fields,
                file = file,
                fileFieldName = fileFieldName,
                remoteFileName = remoteFileName,
                onProgress = onProgress
            )
        }
    )
    private val crealityPrintClient = CrealityPrintConnectionClient(
        requestTextBody = { url, method, headers -> requestTextBody(url, method, headers) },
        uploadMultipart = { url, headers, fields, file, fileFieldName, remoteFileName, onProgress ->
            uploadMultipart(
                url = url,
                headers = headers,
                fields = fields,
                file = file,
                fileFieldName = fileFieldName,
                remoteFileName = remoteFileName,
                onProgress = onProgress
            )
        },
        startPrint = { baseUrl, fileName -> startCrealityPrint(baseUrl, fileName) }
    )
    private val duetClient = DuetConnectionClient(
        requestText = { url, method, headers -> requestText(url, method, headers) },
        requestTextBody = { url, method, headers -> requestTextBody(url, method, headers) },
        sendRawBody = { url, method, headers, body -> sendRawBody(url, method, headers, body) },
        uploadRawFile = { url, method, headers, file, onProgress, successCodes, contentType ->
            uploadRawFile(
                url = url,
                method = method,
                headers = headers,
                file = file,
                onProgress = onProgress,
                successCodes = successCodes,
                contentType = contentType
            )
        }
    )
    private val flashforgeClient = FlashforgeConnectionClient(
        sendTcpConsoleCommands = { host, port, commands, delayBeforeMs, delimiter ->
            sendTcpConsoleCommands(
                host = host,
                port = port,
                commands = commands,
                delayBeforeMs = delayBeforeMs,
                delimiter = delimiter
            )
        },
        uploadFlashforgeFile = { host, port, file, remoteFileName, onProgress ->
            uploadFlashforgeFile(host, port, file, remoteFileName, onProgress)
        }
    )
    private val elegooLinkClient = ElegooLinkConnectionClient(
        requestText = { url, method, headers -> requestText(url, method, headers) },
        requestTextBody = { url, method, headers -> requestTextBody(url, method, headers) },
        uploadMultipart = { url, headers, fields, file, fileFieldName, remoteFileName, onProgress ->
            uploadMultipart(
                url = url,
                headers = headers,
                fields = fields,
                file = file,
                fileFieldName = fileFieldName,
                remoteFileName = remoteFileName,
                onProgress = onProgress
            )
        },
        uploadElegooLinkChunks = { profile, baseUrl, file, fileName, onProgress ->
            uploadElegooLinkChunks(profile, baseUrl, file, fileName, onProgress)
        },
        startElegooLinkPrint = { baseUrl, fileName -> startElegooLinkPrint(baseUrl, fileName) },
        octoPrintHeaders = { profile -> profile.authHeaders(PrinterProtocol.OctoPrint) }
    )
    private val bambuLanClient = BambuLanConnectionClient(
        canOpenTcp = { host, port, timeoutMs -> canOpenTcp(host, port, timeoutMs) }
    )
    private val bambuLanAgent = BambuLanPrinterAgent(
        transferClient = BambuLanFtpsTransferClient(),
        mqttClient = BambuLanMqttTransportClient()
    )
    private val prusaClient = PrusaConnectionClient(
        requestTextBody = { url, method, headers -> requestTextBody(url, method, headers) },
        uploadMultipart = { url, headers, fields, file, fileFieldName, remoteFileName, onProgress ->
            uploadMultipart(
                url = url,
                headers = headers,
                fields = fields,
                file = file,
                fileFieldName = fileFieldName,
                remoteFileName = remoteFileName,
                onProgress = onProgress
            )
        },
        uploadRawFile = { url, method, headers, file, onProgress, successCodes, contentType ->
            uploadRawFile(
                url = url,
                method = method,
                headers = headers,
                file = file,
                onProgress = onProgress,
                successCodes = successCodes,
                contentType = contentType
            )
        },
        fetchDigestChallenge = { targetUrl -> fetchDigestChallenge(targetUrl) }
    )
    private val octoKlipperClient = OctoKlipperConnectionClient(
        requestText = { url, method, headers -> requestText(url, method, headers) },
        requestTextBody = { url, method, headers -> requestTextBody(url, method, headers) },
        sendRawBody = { url, method, headers, body -> sendRawBody(url, method, headers, body) },
        uploadMultipart = { url, headers, fields, file, fileFieldName, remoteFileName, onProgress ->
            uploadMultipart(
                url = url,
                headers = headers,
                fields = fields,
                file = file,
                fileFieldName = fileFieldName,
                remoteFileName = remoteFileName,
                onProgress = onProgress
            )
        }
    )

    suspend fun browseConnectionTargets(profile: PrinterProfile): PrinterConnectionChoicesResult = withContext(Dispatchers.IO) {
        val baseUrl = normalizeBaseUrl(profile.effectivePrintHost())
            ?: return@withContext PrinterConnectionChoicesResult(false, "Picker unavailable", "Enter a printer host or IP address.")
        requireAllowedPrinterBaseUrl(baseUrl)?.let {
            return@withContext PrinterConnectionChoicesResult(false, it.title, it.detail)
        }

        when (profile.printHostType) {
            PrintHostType.Obico -> obicoClient.browsePrinters(profile, baseUrl).withTargetType(PrinterBrowseTargetType.PrinterTarget)
            PrintHostType.Repetier -> repetierClient.browsePrinters(profile, baseUrl).withTargetType(PrinterBrowseTargetType.PrinterTarget)
            PrintHostType.PrusaLink -> prusaClient.browseLinkStorage(profile, baseUrl).withTargetType(PrinterBrowseTargetType.StoragePath)
            else -> PrinterConnectionChoicesResult(
                false,
                "Picker unavailable",
                "${profile.printHostType.displayLabel} does not expose a connection picker yet."
            )
        }
    }

    suspend fun browseConnectionGroups(profile: PrinterProfile): PrinterConnectionChoicesResult = withContext(Dispatchers.IO) {
        val baseUrl = normalizeBaseUrl(profile.effectivePrintHost())
            ?: return@withContext PrinterConnectionChoicesResult(false, "Picker unavailable", "Enter a printer host or IP address.")
        requireAllowedPrinterBaseUrl(baseUrl)?.let {
            return@withContext PrinterConnectionChoicesResult(false, it.title, it.detail)
        }
        if (profile.printHostType != PrintHostType.Repetier) {
            return@withContext PrinterConnectionChoicesResult(false, "Picker unavailable", "${profile.printHostType.displayLabel} does not expose model groups.")
        }
        repetierClient.browseGroups(profile, baseUrl).withTargetType(PrinterBrowseTargetType.Group)
    }

    suspend fun testConnection(profile: PrinterProfile): PrinterConnectionResult = withContext(Dispatchers.IO) {
        val baseUrl = normalizeBaseUrl(profile.effectivePrintHost())
            ?: return@withContext PrinterConnectionResult(false, "Connection failed", "Enter a printer host or IP address.")
        requireAllowedPrinterBaseUrl(baseUrl)?.let { return@withContext it }

        when (profile.printHostType) {
            PrintHostType.Duet -> return@withContext duetClient.testConnection(profile, baseUrl)
            PrintHostType.Repetier -> return@withContext repetierClient.testConnection(profile, baseUrl)
            PrintHostType.Esp3d -> return@withContext esp3dClient.testConnection(baseUrl)
            PrintHostType.CrealityPrint -> return@withContext crealityPrintClient.testConnection(profile, baseUrl)
            PrintHostType.ElegooLink -> return@withContext elegooLinkClient.testConnection(profile, baseUrl)
            PrintHostType.FlashAir -> return@withContext flashAirClient.testConnection(baseUrl)
            PrintHostType.AstroBox -> return@withContext astroBoxClient.testConnection(profile, baseUrl)
            PrintHostType.Mks -> return@withContext mksClient.testConnection(profile, baseUrl)
            PrintHostType.Flashforge -> return@withContext flashforgeClient.testConnection(profile, baseUrl)
            PrintHostType.PrusaLink -> return@withContext prusaClient.testLinkConnection(profile, baseUrl)
            PrintHostType.PrusaConnect -> return@withContext prusaClient.testConnectConnection(profile, baseUrl)
            PrintHostType.Obico -> return@withContext obicoClient.testConnection(profile, baseUrl)
            PrintHostType.SimplyPrint -> return@withContext simplyPrintClient.testConnection(profile)
            PrintHostType.BambuLan -> return@withContext bambuLanClient.testConnection(profile, baseUrl)
            PrintHostType.OctoPrint -> Unit
            else -> return@withContext unsupportedHost(profile)
        }

        octoKlipperClient.testConnection(profile, baseUrl)
    }

    suspend fun uploadGcode(
        profile: PrinterProfile,
        file: File,
        remoteFileName: String,
        action: PrinterUploadAction,
        bambuOptions: BambuLanPrintOptions? = null,
        onProgress: (Int) -> Unit = {}
    ): PrinterConnectionResult = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() <= 0L) {
            return@withContext PrinterConnectionResult(false, "Send failed", "Generated G-code file was unavailable.")
        }
        val baseUrl = normalizeBaseUrl(profile.effectivePrintHost())
            ?: return@withContext PrinterConnectionResult(false, "Send failed", "Enter a printer host or IP address.")
        requireAllowedPrinterBaseUrl(baseUrl)?.let { return@withContext it.copy(title = "Send failed") }
        if (
            action == PrinterUploadAction.Queue &&
            profile.printHostType != PrintHostType.PrusaConnect &&
            profile.printHostType != PrintHostType.SimplyPrint
        ) {
            return@withContext PrinterConnectionResult(false, "Send failed", "Queue upload is currently supported only for PrusaConnect and SimplyPrint.")
        }

        when (profile.printHostType) {
            PrintHostType.Duet -> return@withContext duetClient.uploadGcode(profile, baseUrl, file, remoteFileName, action, onProgress)
            PrintHostType.Repetier -> return@withContext repetierClient.uploadGcode(profile, baseUrl, file, remoteFileName, action, onProgress)
            PrintHostType.Esp3d -> return@withContext esp3dClient.uploadGcode(baseUrl, file, remoteFileName, action, onProgress)
            PrintHostType.CrealityPrint -> return@withContext crealityPrintClient.uploadGcode(profile, baseUrl, file, remoteFileName, action, onProgress)
            PrintHostType.ElegooLink -> return@withContext elegooLinkClient.uploadGcode(profile, baseUrl, file, remoteFileName, action, onProgress)
            PrintHostType.FlashAir -> return@withContext flashAirClient.uploadGcode(profile, baseUrl, file, remoteFileName, action, onProgress)
            PrintHostType.AstroBox -> return@withContext astroBoxClient.uploadGcode(profile, baseUrl, file, remoteFileName, action, onProgress)
            PrintHostType.Mks -> return@withContext mksClient.uploadGcode(profile, baseUrl, file, remoteFileName, action, onProgress)
            PrintHostType.Flashforge -> return@withContext flashforgeClient.uploadGcode(profile, baseUrl, file, remoteFileName, action, onProgress)
            PrintHostType.PrusaLink -> return@withContext prusaClient.uploadLinkGcode(profile, baseUrl, file, remoteFileName, action, onProgress)
            PrintHostType.PrusaConnect -> return@withContext prusaClient.uploadConnectGcode(profile, baseUrl, file, remoteFileName, action, onProgress)
            PrintHostType.Obico -> return@withContext obicoClient.uploadGcode(profile, baseUrl, file, remoteFileName, action, onProgress)
            PrintHostType.SimplyPrint -> return@withContext simplyPrintClient.uploadGcode(profile, file, remoteFileName, action, onProgress)
            PrintHostType.BambuLan -> return@withContext uploadBambuLanPackage(profile, baseUrl, file, remoteFileName, action, bambuOptions, onProgress)
            PrintHostType.OctoPrint -> Unit
            else -> return@withContext unsupportedHost(profile)
        }

        octoKlipperClient.uploadGcode(profile, baseUrl, file, remoteFileName, action, onProgress)
    }

    suspend fun fetchStatus(profile: PrinterProfile): PrinterConnectionResult = withContext(Dispatchers.IO) {
        val baseUrl = normalizeBaseUrl(profile.effectivePrintHost())
            ?: return@withContext PrinterConnectionResult(false, "Status unavailable", "Enter a printer host or IP address.")
        requireAllowedPrinterBaseUrl(baseUrl)?.let { return@withContext it.copy(title = "Status unavailable") }

        val result = when (profile.printHostType) {
            PrintHostType.Duet -> duetClient.fetchStatus(profile, baseUrl)
            PrintHostType.Repetier -> repetierClient.fetchStatus(profile, baseUrl)
            PrintHostType.Esp3d -> esp3dClient.fetchStatus(baseUrl)
            PrintHostType.CrealityPrint -> crealityPrintClient.fetchStatus(profile, baseUrl)
            PrintHostType.ElegooLink -> elegooLinkClient.fetchStatus(profile, baseUrl)
            PrintHostType.FlashAir -> flashAirClient.fetchStatus(baseUrl)
            PrintHostType.AstroBox -> astroBoxClient.fetchStatus(profile, baseUrl)
            PrintHostType.Mks -> mksClient.fetchStatus(profile, baseUrl)
            PrintHostType.Flashforge -> flashforgeClient.fetchStatus(profile, baseUrl)
            PrintHostType.PrusaLink -> prusaClient.fetchLinkStatus(profile, baseUrl)
            PrintHostType.PrusaConnect -> prusaClient.fetchConnectStatus(profile, baseUrl)
            PrintHostType.Obico -> obicoClient.fetchStatus(profile, baseUrl)
            PrintHostType.SimplyPrint -> simplyPrintClient.fetchStatus(profile)
            PrintHostType.BambuLan -> bambuLanClient.testConnection(profile, baseUrl).copy(title = "Printer status")
            PrintHostType.OctoPrint -> octoKlipperClient.fetchStatus(profile, baseUrl)
            else -> unsupportedHost(profile)
        }
        result.withInferredRuntimeStatus(profile.printHostType)
    }

    private fun unsupportedHost(profile: PrinterProfile): PrinterConnectionResult =
        PrinterConnectionResult(
            false,
            "Connection not implemented",
            "${profile.printHostType.displayLabel} is saved in the profile, but runtime support currently covers Octo/Klipper, PrusaLink, PrusaConnect, Duet, Repetier, ESP3D, CrealityPrint, Elegoo Link, FlashAir, AstroBox, MKS, Flashforge, Obico, SimplyPrint, and Bambu LAN."
        )

    private fun uploadBambuLanPackage(
        profile: PrinterProfile,
        baseUrl: String,
        file: File,
        remoteFileName: String,
        action: PrinterUploadAction,
        bambuOptions: BambuLanPrintOptions?,
        onProgress: (Int) -> Unit
    ): PrinterConnectionResult {
        if (action == PrinterUploadAction.Queue) {
            return PrinterConnectionResult(false, "Send failed", "Bambu LAN does not support queue upload from Mobile Slicer.")
        }
        val packageFile = if (file.name.endsWith(".gcode.3mf", ignoreCase = true) || file.name.endsWith(".3mf", ignoreCase = true)) {
            file
        } else {
            return PrinterConnectionResult(
                false,
                "Bambu package required",
                "Bambu LAN printing requires a sliced .gcode.3mf package. Slice the plate before sending so MobileSlicer can package it."
            )
        }
        val device = bambuLanAgent.deviceConfig(profile, baseUrl)
            ?: return PrinterConnectionResult(false, "Send failed", profile.bambuLanProfileProblem(baseUrl))
        val bambuRemoteName = remoteFileName.toBambuPackageFileName()
        val job = BambuLanPrintJob(
            taskName = bambuRemoteName.substringBeforeLast(".gcode.3mf", bambuRemoteName),
            projectName = bambuRemoteName,
            localFile = packageFile,
            remoteFileName = bambuRemoteName,
            options = bambuOptions ?: profile.bambuLanPrintOptions()
        )
        return when (action) {
            PrinterUploadAction.UploadAndStart -> bambuLanAgent.uploadAndStart(device, job, onProgress)
            PrinterUploadAction.UploadOnly -> bambuLanAgent.uploadOnly(device, job, onProgress)
            PrinterUploadAction.Queue -> PrinterConnectionResult(false, "Send failed", "Bambu LAN does not support queue upload from Mobile Slicer.")
        }
    }

    private fun fetchDigestChallenge(targetUrl: String): Map<String, String>? {
        val target = runCatching { URI(targetUrl) }.getOrNull() ?: return null
        val challengeUrl = "${target.scheme}://${target.host}${if (target.port >= 0) ":${target.port}" else ""}/api/version"
        return runCatching {
            val connection = openConnection(challengeUrl, "GET", emptyMap(), connectTimeoutMs = 3_000, readTimeoutMs = 6_000)
            val code = connection.responseCode
            if (code != 401) return@runCatching null
            val header = connection.getHeaderField("WWW-Authenticate") ?: return@runCatching null
            parseDigestChallenge(header)
        }.getOrNull()
    }

    private fun parseDigestChallenge(header: String): Map<String, String>? {
        val trimmed = header.trim()
        if (!trimmed.startsWith("Digest", ignoreCase = true)) return null
        val input = trimmed.substringAfter(' ', missingDelimiterValue = "").trim()
        val values = mutableMapOf<String, String>()
        var index = 0
        while (index < input.length) {
            while (index < input.length && (input[index] == ',' || input[index].isWhitespace())) index++
            val keyStart = index
            while (index < input.length && input[index] != '=') index++
            if (index >= input.length) break
            val key = input.substring(keyStart, index).trim().lowercase()
            index++
            val value = if (index < input.length && input[index] == '"') {
                index++
                buildString {
                    while (index < input.length) {
                        val char = input[index++]
                        if (char == '"') break
                        if (char == '\\' && index < input.length) {
                            append(input[index++])
                        } else {
                            append(char)
                        }
                    }
                }
            } else {
                val valueStart = index
                while (index < input.length && input[index] != ',') index++
                input.substring(valueStart, index).trim()
            }
            values[key] = value
        }
        return values.takeIf { "realm" in it && "nonce" in it }
    }

    private fun PrinterProfile.effectivePrintHost(): String =
        printHost.ifBlank {
            when (printHostType) {
                PrintHostType.PrusaConnect -> "https://connect.prusa3d.com"
                PrintHostType.Obico -> "https://app.obico.io"
                PrintHostType.SimplyPrint -> SIMPLYPRINT_PANEL_URL
                else -> ""
            }
        }

    private fun requestText(
        url: String,
        method: String,
        headers: Map<String, String>
    ): NetworkResult {
        return runNetwork {
            val connection = openConnection(url, method, headers, connectTimeoutMs = 3_000, readTimeoutMs = 6_000)
            val code = connection.responseCode
            if (code in 200..299) {
                NetworkResult.Success
            } else {
                NetworkResult.Failure(httpFailureMessage(code, url))
            }
        }
    }

    private fun requestTextBody(
        url: String,
        method: String,
        headers: Map<String, String>
    ): TextNetworkResult {
        return runTextNetwork {
            val connection = openConnection(url, method, headers, connectTimeoutMs = 3_000, readTimeoutMs = 6_000)
            val code = connection.responseCode
            if (code in 200..299) {
                TextNetworkResult.Success(connection.inputStream.bufferedReader().use { it.readText() })
            } else {
                TextNetworkResult.Failure(httpFailureMessage(code, url))
            }
        }
    }

    private fun requestFormBody(
        url: String,
        fields: Map<String, String>,
        headers: Map<String, String>
    ): TextNetworkResult {
        return runTextNetwork {
            val connection = openConnection(url, "POST", headers, connectTimeoutMs = 7_000, readTimeoutMs = 20_000).apply {
                doOutput = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            }
            val body = fields.entries.joinToString("&") { (key, value) ->
                "${urlEncode(key)}=${urlEncode(value)}"
            }
            connection.outputStream.use { output ->
                output.write(body.toByteArray(StandardCharsets.UTF_8))
            }
            val code = connection.responseCode
            if (code in 200..299) {
                TextNetworkResult.Success(connection.inputStream.bufferedReader().use { it.readText() })
            } else {
                TextNetworkResult.Failure(httpFailureMessage(code, url))
            }
        }
    }

    private suspend fun uploadMultipart(
        url: String,
        headers: Map<String, String>,
        fields: Map<String, String>,
        file: File,
        fileFieldName: String,
        remoteFileName: String,
        onProgress: (Int) -> Unit
    ): NetworkResult {
        val uploadContext = currentCoroutineContext()
        return runNetwork {
            val boundary = "MobileSlicerBoundary${System.currentTimeMillis()}"
            val connection = openConnection(url, "POST", headers, connectTimeoutMs = 7_000, readTimeoutMs = 600_000).apply {
                doOutput = true
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            }
            BufferedOutputStream(connection.outputStream).use { output ->
                fields.forEach { (name, value) ->
                    output.write("--$boundary\r\n".toByteArray())
                    output.write("Content-Disposition: form-data; name=\"$name\"\r\n\r\n".toByteArray())
                    output.write(value.toByteArray(StandardCharsets.UTF_8))
                    output.write("\r\n".toByteArray())
                }
                output.write("--$boundary\r\n".toByteArray())
                output.write(
                    "Content-Disposition: form-data; name=\"$fileFieldName\"; filename=\"$remoteFileName\"\r\n"
                        .toByteArray(StandardCharsets.UTF_8)
                )
                output.write("Content-Type: application/octet-stream\r\n\r\n".toByteArray())
                file.inputStream().use { input ->
                    val totalBytes = file.length().coerceAtLeast(1L)
                    val buffer = ByteArray(1 shl 16)
                    var sentBytes = 0L
                    while (true) {
                        uploadContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        sentBytes += read
                        onProgress(((sentBytes * 100L) / totalBytes).toInt().coerceIn(0, 100))
                    }
                }
                output.write("\r\n--$boundary--\r\n".toByteArray())
            }
            val code = connection.responseCode
            if (code in 200..299) {
                NetworkResult.Success
            } else {
                NetworkResult.Failure("${httpFailureMessage(code, url)} while uploading G-code.")
            }
        }
    }

    private suspend fun uploadMultipartBody(
        url: String,
        headers: Map<String, String>,
        fields: Map<String, String>,
        file: File,
        fileFieldName: String,
        remoteFileName: String,
        offset: Long = 0L,
        length: Long = file.length() - offset,
        totalProgressBytes: Long = file.length().coerceAtLeast(1L),
        progressOffsetBytes: Long = 0L,
        onProgress: (Int) -> Unit
    ): TextNetworkResult {
        val uploadContext = currentCoroutineContext()
        return runTextNetwork {
            val boundary = "MobileSlicerBoundary${System.currentTimeMillis()}"
            val connection = openConnection(url, "POST", headers, connectTimeoutMs = 7_000, readTimeoutMs = 120_000).apply {
                doOutput = true
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            }
            BufferedOutputStream(connection.outputStream).use { output ->
                fields.forEach { (name, value) ->
                    output.write("--$boundary\r\n".toByteArray())
                    output.write("Content-Disposition: form-data; name=\"$name\"\r\n\r\n".toByteArray())
                    output.write(value.toByteArray(StandardCharsets.UTF_8))
                    output.write("\r\n".toByteArray())
                }
                output.write("--$boundary\r\n".toByteArray())
                output.write(
                    "Content-Disposition: form-data; name=\"$fileFieldName\"; filename=\"$remoteFileName\"\r\n"
                        .toByteArray(StandardCharsets.UTF_8)
                )
                output.write("Content-Type: application/octet-stream\r\n\r\n".toByteArray())
                file.inputStream().use { input ->
                    if (offset > 0L) input.skipFully(offset)
                    val boundedLength = length.coerceAtLeast(0L)
                    val buffer = ByteArray(1 shl 16)
                    var sentChunkBytes = 0L
                    while (sentChunkBytes < boundedLength) {
                        uploadContext.ensureActive()
                        val toRead = minOf(buffer.size.toLong(), boundedLength - sentChunkBytes).toInt()
                        val read = input.read(buffer, 0, toRead)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        sentChunkBytes += read
                        val sentTotalBytes = progressOffsetBytes + sentChunkBytes
                        onProgress(((sentTotalBytes * 100L) / totalProgressBytes.coerceAtLeast(1L)).toInt().coerceIn(0, 100))
                    }
                }
                output.write("\r\n--$boundary--\r\n".toByteArray())
            }
            val code = connection.responseCode
            if (code in 200..299) {
                TextNetworkResult.Success(connection.inputStream.bufferedReader().use { it.readText() })
            } else {
                TextNetworkResult.Failure("${httpFailureMessage(code, url)} while uploading G-code.")
            }
        }
    }

    private fun uploadMultipartFieldsBody(
        url: String,
        headers: Map<String, String>,
        fields: Map<String, String>
    ): TextNetworkResult {
        return runTextNetwork {
            val boundary = "MobileSlicerBoundary${System.currentTimeMillis()}"
            val connection = openConnection(url, "POST", headers, connectTimeoutMs = 7_000, readTimeoutMs = 120_000).apply {
                doOutput = true
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            }
            BufferedOutputStream(connection.outputStream).use { output ->
                fields.forEach { (name, value) ->
                    output.write("--$boundary\r\n".toByteArray())
                    output.write("Content-Disposition: form-data; name=\"$name\"\r\n\r\n".toByteArray())
                    output.write(value.toByteArray(StandardCharsets.UTF_8))
                    output.write("\r\n".toByteArray())
                }
                output.write("--$boundary--\r\n".toByteArray())
            }
            val code = connection.responseCode
            if (code in 200..299) {
                TextNetworkResult.Success(connection.inputStream.bufferedReader().use { it.readText() })
            } else {
                TextNetworkResult.Failure("${httpFailureMessage(code, url)} while uploading G-code.")
            }
        }
    }

    private suspend fun uploadRawFile(
        url: String,
        method: String,
        headers: Map<String, String>,
        file: File,
        onProgress: (Int) -> Unit,
        successCodes: IntRange,
        contentType: String = "application/octet-stream"
    ): NetworkResult {
        val uploadContext = currentCoroutineContext()
        return runNetwork {
            val connection = openConnection(url, method, headers, connectTimeoutMs = 7_000, readTimeoutMs = 120_000).apply {
                doOutput = true
                setRequestProperty("Content-Type", contentType)
            }
            BufferedOutputStream(connection.outputStream).use { output ->
                file.inputStream().use { input ->
                    val totalBytes = file.length().coerceAtLeast(1L)
                    val buffer = ByteArray(1 shl 16)
                    var sentBytes = 0L
                    while (true) {
                        uploadContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        sentBytes += read
                        onProgress(((sentBytes * 100L) / totalBytes).toInt().coerceIn(0, 100))
                    }
                }
            }
            val code = connection.responseCode
            if (code in successCodes) {
                NetworkResult.Success
            } else {
                NetworkResult.Failure("${httpFailureMessage(code, url)} while uploading G-code.")
            }
        }
    }

    private suspend fun uploadRawFileBody(
        url: String,
        method: String,
        headers: Map<String, String>,
        file: File,
        onProgress: (Int) -> Unit,
        successCodes: IntRange
    ): TextNetworkResult {
        val uploadContext = currentCoroutineContext()
        return runTextNetwork {
            val connection = openConnection(url, method, headers, connectTimeoutMs = 7_000, readTimeoutMs = 120_000).apply {
                doOutput = true
                setRequestProperty("Content-Type", "application/octet-stream")
            }
            BufferedOutputStream(connection.outputStream).use { output ->
                file.inputStream().use { input ->
                    val totalBytes = file.length().coerceAtLeast(1L)
                    val buffer = ByteArray(1 shl 16)
                    var sentBytes = 0L
                    while (true) {
                        uploadContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        sentBytes += read
                        onProgress(((sentBytes * 100L) / totalBytes).toInt().coerceIn(0, 100))
                    }
                }
            }
            val code = connection.responseCode
            if (code in successCodes) {
                TextNetworkResult.Success(connection.inputStream.bufferedReader().use { it.readText() })
            } else {
                TextNetworkResult.Failure("${httpFailureMessage(code, url)} while uploading G-code.")
            }
        }
    }

    private fun sendRawBody(
        url: String,
        method: String,
        headers: Map<String, String>,
        body: String
    ): NetworkResult {
        return runNetwork {
            val connection = openConnection(url, method, headers, connectTimeoutMs = 5_000, readTimeoutMs = 15_000).apply {
                doOutput = true
                if (headers.keys.none { it.equals("Content-Type", ignoreCase = true) }) {
                    setRequestProperty("Content-Type", "text/plain")
                }
            }
            connection.outputStream.use { output ->
                output.write(body.toByteArray(StandardCharsets.UTF_8))
            }
            val code = connection.responseCode
            if (code in 200..299) {
                NetworkResult.Success
            } else {
                NetworkResult.Failure(httpFailureMessage(code, url))
            }
        }
    }

    private suspend fun uploadFlashforgeFile(
        host: String,
        port: Int,
        file: File,
        remoteFileName: String,
        onProgress: (Int) -> Unit
    ): NetworkResult {
        val uploadContext = currentCoroutineContext()
        return runNetwork {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 5_000)
                socket.soTimeout = 10_000
                val input = socket.getInputStream().bufferedReader(StandardCharsets.UTF_8)
                val output = socket.getOutputStream()
                output.write("~M28 ${file.length()} 0:/user/$remoteFileName\r\n".toByteArray(StandardCharsets.UTF_8))
                output.flush()
                var acknowledged = false
                while (true) {
                    val line = input.readLine() ?: break
                    if (line.trim().equals("ok", ignoreCase = true)) {
                        acknowledged = true
                        break
                    }
                }
                if (!acknowledged) {
                    return@runNetwork NetworkResult.Failure("Printer console did not acknowledge upload start.")
                }

                file.inputStream().use { source ->
                    val totalBytes = file.length().coerceAtLeast(1L)
                    val buffer = ByteArray(4096)
                    var sentBytes = 0L
                    while (true) {
                        uploadContext.ensureActive()
                        val read = source.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        sentBytes += read
                        onProgress(((sentBytes * 100L) / totalBytes).toInt().coerceIn(0, 100))
                    }
                    output.flush()
                }
            }
            NetworkResult.Success
        }
    }

    private fun sendTcpConsoleCommands(
        host: String,
        port: Int,
        commands: List<String>,
        delayBeforeMs: Long = 0L,
        delimiter: String = "\n"
    ): NetworkResult {
        return runNetwork {
            if (delayBeforeMs > 0L) Thread.sleep(delayBeforeMs)
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 5_000)
                socket.soTimeout = 10_000
                val input = socket.getInputStream().bufferedReader(StandardCharsets.UTF_8)
                val output = socket.getOutputStream()
                for (command in commands) {
                    output.write((command.trimEnd('\r', '\n') + delimiter).toByteArray(StandardCharsets.UTF_8))
                    output.flush()
                    var acknowledged = false
                    while (true) {
                        val line = input.readLine() ?: break
                        if (line.trim().equals("ok", ignoreCase = true)) {
                            acknowledged = true
                            break
                        }
                    }
                    if (!acknowledged) {
                        return@runNetwork NetworkResult.Failure("Printer console did not acknowledge $command.")
                    }
                }
            }
            NetworkResult.Success
        }
    }

    private fun canOpenTcp(host: String, port: Int, timeoutMs: Int): Boolean =
        runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
            }
        }.isSuccess

    private fun startCrealityPrint(baseUrl: String, fileName: String): NetworkResult {
        val uri = runCatching { URI(baseUrl) }.getOrNull()
            ?: return NetworkResult.Failure("CrealityPrint URL could not be parsed.")
        val host = uri.host ?: return NetworkResult.Failure("CrealityPrint host could not be parsed.")
        val payload = """{"method":"set","params":{"opGcodeFile":${"printprt:/usr/data/printer_data/gcodes/$fileName".jsonString()}}}"""
        return webSocketClient.sendText(host = host, port = 9999, path = "/", payload = payload)
    }

    private suspend fun uploadElegooLinkChunks(
        profile: PrinterProfile,
        baseUrl: String,
        file: File,
        fileName: String,
        onProgress: (Int) -> Unit
    ): NetworkResult {
        val fileSize = file.length()
        val fileMd5 = md5Hex(file)
        val uuid = UUID.randomUUID().toString()
        val chunkCount = ((fileSize + ELEGOO_UPLOAD_CHUNK_BYTES - 1L) / ELEGOO_UPLOAD_CHUNK_BYTES)
            .toInt()
            .coerceAtLeast(1)
        for (index in 0 until chunkCount) {
            val offset = index * ELEGOO_UPLOAD_CHUNK_BYTES
            val length = minOf(ELEGOO_UPLOAD_CHUNK_BYTES, fileSize - offset).coerceAtLeast(0L)
            val upload = uploadMultipartBody(
                url = "${baseUrl.trimEnd('/')}/uploadFile/upload",
                headers = profile.authHeaders(PrinterProtocol.OctoPrint),
                fields = mapOf(
                    "Check" to "1",
                    "S-File-MD5" to fileMd5,
                    "Offset" to offset.toString(),
                    "Uuid" to uuid,
                    "TotalSize" to fileSize.toString()
                ),
                file = file,
                fileFieldName = "File",
                remoteFileName = fileName,
                offset = offset,
                length = length,
                totalProgressBytes = fileSize.coerceAtLeast(1L),
                progressOffsetBytes = offset,
                onProgress = onProgress
            )
            val body = upload.body.orEmpty()
            if (!upload.isSuccess) {
                return NetworkResult.Failure(upload.errorMessage ?: "Elegoo Link chunk upload failed.")
            }
            if (!elegooUploadAccepted(body)) {
                return NetworkResult.Failure(elegooUploadError(body))
            }
        }
        return NetworkResult.Success
    }

    private fun elegooUploadAccepted(body: String): Boolean =
        runCatching { JSONObject(body).optString("code") == "000000" }.getOrDefault(false)

    private fun elegooUploadError(body: String): String =
        runCatching {
            val json = JSONObject(body)
            val code = json.optString("code", "unknown")
            val messages = json.optJSONArray("messages")
            if (messages == null || messages.length() == 0) {
                "Elegoo Link upload failed with code $code."
            } else {
                buildString {
                    append("Elegoo Link upload failed with code ")
                    append(code)
                    append(".")
                    for (i in 0 until messages.length()) {
                        val item = messages.optJSONObject(i) ?: continue
                        append('\n')
                        append(item.optString("field", "field"))
                        append(": ")
                        append(item.optString("message", "error"))
                    }
                }
            }
        }.getOrElse { "Elegoo Link upload returned an unexpected response." }

    private fun startElegooLinkPrint(baseUrl: String, fileName: String): NetworkResult {
        val uri = runCatching { URI(baseUrl) }.getOrNull()
            ?: return NetworkResult.Failure("Elegoo Link URL could not be parsed.")
        val host = uri.host ?: return NetworkResult.Failure("Elegoo Link host could not be parsed.")
        return webSocketClient.withSession(host = host, port = 3030, path = "/websocket", userAgent = "ElegooSlicer") { session ->
            val statusResult = waitForElegooFileCheck(session)
            if (!statusResult.isSuccess) return@withSession statusResult
            sendElegooStartPrint(session, fileName)
        }
    }

    private fun waitForElegooFileCheck(session: PrinterWebSocketSession): NetworkResult {
        val startedAt = System.currentTimeMillis()
        while (System.currentTimeMillis() - startedAt < 60_000L) {
            session.sendText(elegooCommandJson(cmd = 0, data = "{}"))
            val response = session.readText(timeoutMs = 10_000) ?: return NetworkResult.Success
            val statuses = runCatching {
                val current = JSONObject(response)
                    .optJSONObject("Status")
                    ?.optJSONArray("CurrentStatus")
                    ?: return@runCatching emptyList<Int>()
                List(current.length()) { index -> current.optInt(index) }
            }.getOrDefault(emptyList())
            if (8 !in statuses) return NetworkResult.Success
            Thread.sleep(1_000)
        }
        return NetworkResult.Failure("Elegoo Link file check timed out.")
    }

    private fun sendElegooStartPrint(session: PrinterWebSocketSession, fileName: String): NetworkResult {
        val data = JSONObject()
            .put("Filename", "/local/$fileName")
            .put("StartLayer", 0)
            .put("Calibration_switch", 0)
            .put("PrintPlatformType", 0)
            .put("Tlp_Switch", 0)
            .toString()
        session.sendText(elegooCommandJson(cmd = 128, data = data))
        val startedAt = System.currentTimeMillis()
        while (System.currentTimeMillis() - startedAt < 30_000L) {
            val response = session.readText(timeoutMs = 10_000) ?: continue
            val dataObject = runCatching { JSONObject(response).optJSONObject("Data") }.getOrNull() ?: continue
            if (dataObject.optInt("Cmd", -1) != 128) continue
            val ack = dataObject.optJSONObject("Data")?.optInt("Ack", -1) ?: -1
            return if (ack == 0) {
                NetworkResult.Success
            } else {
                NetworkResult.Failure(elegooStartAckMessage(ack))
            }
        }
        return NetworkResult.Failure("Elegoo Link start print timed out.")
    }

    private fun elegooCommandJson(cmd: Int, data: String): String =
        """{"Id":"","Data":{"Cmd":$cmd,"Data":$data,"RequestID":${UUID.randomUUID().toString().jsonString()},"MainboardID":"","TimeStamp":${System.currentTimeMillis()},"From":1}}"""

    private fun elegooStartAckMessage(ack: Int): String =
        when (ack) {
            1 -> "The printer is busy. Check the device page and try starting the file again. Error code: $ack"
            2 -> "The file is lost. Check the device page and try again. Error code: $ack"
            3 -> "The file is corrupted. Check the file and try again. Error code: $ack"
            4, 5, 6 -> "Transmission abnormality. Check the device and try again. Error code: $ack"
            7 -> "The file does not match the printer. Check the file and try again. Error code: $ack"
            else -> "Elegoo Link start failed. Error code: $ack"
        }

    private fun md5Hex(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun String.jsonString(): String =
        buildString {
            append('"')
            this@jsonString.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(char)
                }
            }
            append('"')
        }

    private fun openConnection(
        url: String,
        method: String,
        headers: Map<String, String>,
        connectTimeoutMs: Int,
        readTimeoutMs: Int
    ): HttpURLConnection {
        requireAllowedPrinterNetworkUrl(url)
        return (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            useCaches = false
            headers.forEach { (name, value) -> setRequestProperty(name, value) }
        }
    }

    private fun runNetwork(block: () -> NetworkResult): NetworkResult =
        try {
            block()
        } catch (_: ConnectException) {
            NetworkResult.Failure("Connection refused. Check the printer IP address and API port.")
        } catch (_: NoRouteToHostException) {
            NetworkResult.Failure("No route to host. Check that the phone is on the same network as the printer.")
        } catch (_: UnknownHostException) {
            NetworkResult.Failure("Host was not found.")
        } catch (_: SocketTimeoutException) {
            NetworkResult.Failure("Connection timed out.")
        } catch (_: SSLHandshakeException) {
            NetworkResult.Failure("TLS certificate validation failed.")
        } catch (exception: IOException) {
            NetworkResult.Failure(exception.message ?: "Network I/O failed.")
        } catch (exception: Exception) {
            NetworkResult.Failure(exception.message ?: "Network request failed.")
        }

    private fun runTextNetwork(block: () -> TextNetworkResult): TextNetworkResult =
        try {
            block()
        } catch (_: ConnectException) {
            TextNetworkResult.Failure("Connection refused. Check the printer IP address and API port.")
        } catch (_: NoRouteToHostException) {
            TextNetworkResult.Failure("No route to host. Check that the phone is on the same network as the printer.")
        } catch (_: UnknownHostException) {
            TextNetworkResult.Failure("Host was not found.")
        } catch (_: SocketTimeoutException) {
            TextNetworkResult.Failure("Connection timed out.")
        } catch (_: SSLHandshakeException) {
            TextNetworkResult.Failure("TLS certificate validation failed.")
        } catch (exception: IOException) {
            TextNetworkResult.Failure(exception.message ?: "Network I/O failed.")
        } catch (exception: Exception) {
            TextNetworkResult.Failure(exception.message ?: "Network request failed.")
        }

}
