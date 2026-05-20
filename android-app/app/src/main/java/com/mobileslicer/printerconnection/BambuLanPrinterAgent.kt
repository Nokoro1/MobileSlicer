package com.mobileslicer.printerconnection

import android.annotation.SuppressLint
import com.mobileslicer.profiles.PrinterProfile
import java.io.File
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.time.Duration
import java.util.UUID
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPReply
import org.apache.commons.net.ftp.FTPSClient
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONObject

internal data class BambuLanDeviceConfig(
    val host: String,
    val deviceId: String,
    val accessCode: String,
    val username: String = "bblp",
    val mqttPort: Int = 8883,
    val transferPort: Int = 990,
    val useSslForMqtt: Boolean = true,
    val useSslForTransfer: Boolean = true
)

internal data class BambuLanPrintJob(
    val taskName: String,
    val projectName: String,
    val localFile: File,
    val remoteFolder: String = "/",
    val remoteFileName: String,
    val options: BambuLanPrintOptions = BambuLanPrintOptions()
)

internal data class BambuLanPrintOptions(
    val plateIndex: Int = 0,
    val bedType: String = "",
    val useAms: Boolean = false,
    val amsMapping: String = "",
    val nozzleMapping: String = "",
    val bedLeveling: Boolean = true,
    val flowCalibration: Boolean = false,
    val vibrationCalibration: Boolean = false,
    val timelapse: Boolean = false
)

internal data class BambuLanUploadedFile(
    val folder: String,
    val fileName: String,
    val md5: String
)

internal data class BambuLanTransferResult(
    val isSuccess: Boolean,
    val md5: String = "",
    val errorMessage: String? = null
) {
    companion object {
        fun success(md5: String): BambuLanTransferResult = BambuLanTransferResult(isSuccess = true, md5 = md5)
        fun failure(message: String): BambuLanTransferResult = BambuLanTransferResult(isSuccess = false, errorMessage = message)
    }
}

internal interface BambuLanTransferClient {
    fun upload(
        device: BambuLanDeviceConfig,
        job: BambuLanPrintJob,
        onProgress: (Int) -> Unit
    ): BambuLanTransferResult
}

internal interface BambuLanMqttClient {
    fun connect(device: BambuLanDeviceConfig): NetworkResult
    fun startPrint(device: BambuLanDeviceConfig, job: BambuLanPrintJob, uploadedFile: BambuLanUploadedFile): NetworkResult
    fun disconnect()
}

internal class BambuLanPrinterAgent(
    private val transferClient: BambuLanTransferClient,
    private val mqttClient: BambuLanMqttClient
) {
    fun deviceConfig(profile: PrinterProfile, baseUrl: String): BambuLanDeviceConfig? {
        return profile.bambuLanDeviceConfig(baseUrl)
    }

    fun uploadOnly(device: BambuLanDeviceConfig, job: BambuLanPrintJob, onProgress: (Int) -> Unit): PrinterConnectionResult {
        if (!job.localFile.isFile || job.localFile.length() <= 0L) {
            return PrinterConnectionResult(false, "Send failed", "Generated Bambu upload file was unavailable.")
        }
        val upload = transferClient.upload(device, job, onProgress)
        return if (upload.isSuccess) {
            PrinterConnectionResult(true, "Uploaded", "Uploaded ${job.remoteFileName}.")
        } else {
            PrinterConnectionResult(false, "Send failed", upload.errorMessage ?: "Bambu LAN file transfer failed.")
        }
    }

    fun uploadAndStart(device: BambuLanDeviceConfig, job: BambuLanPrintJob, onProgress: (Int) -> Unit): PrinterConnectionResult {
        if (!job.localFile.isFile || job.localFile.length() <= 0L) {
            return PrinterConnectionResult(false, "Send failed", "Generated Bambu upload file was unavailable.")
        }
        val connect = mqttClient.connect(device)
        if (!connect.isSuccess) {
            mqttClient.disconnect()
            return PrinterConnectionResult(false, "Connection failed", connect.errorMessage ?: "Bambu LAN MQTT connection failed.")
        }
        val upload = transferClient.upload(device, job, onProgress)
        if (!upload.isSuccess) {
            mqttClient.disconnect()
            return PrinterConnectionResult(false, "Send failed", upload.errorMessage ?: "Bambu LAN file transfer failed.")
        }
        val uploadedFile = BambuLanUploadedFile(
            folder = job.remoteFolder,
            fileName = job.remoteFileName,
            md5 = upload.md5
        )
        val start = mqttClient.startPrint(device, job, uploadedFile)
        mqttClient.disconnect()
        return if (start.isSuccess) {
            PrinterConnectionResult(true, "Print started", "Uploaded and started ${job.remoteFileName}.")
        } else {
            PrinterConnectionResult(false, "Start failed", start.errorMessage ?: "Bambu LAN uploaded the file, but print did not start.")
        }
    }

}

internal class BambuLanFtpsTransferClient : BambuLanTransferClient {
    override fun upload(
        device: BambuLanDeviceConfig,
        job: BambuLanPrintJob,
        onProgress: (Int) -> Unit
    ): BambuLanTransferResult {
        val client = FTPSClient("TLS", true).apply {
            connectTimeout = 10_000
            defaultTimeout = 10_000
            dataTimeout = Duration.ofMillis(30_000)
            trustManager = trustAllManager()
        }
        return try {
            client.connect(device.host, device.transferPort)
            if (!FTPReply.isPositiveCompletion(client.replyCode)) {
                return BambuLanTransferResult.failure("Bambu FTPS refused the connection: ${client.replyString.orEmpty().trim()}")
            }
            if (!client.login(device.username, device.accessCode)) {
                return BambuLanTransferResult.failure("Bambu FTPS login failed. Check the LAN access code.")
            }
            client.execPBSZ(0)
            client.execPROT("P")
            client.enterLocalPassiveMode()
            client.setFileType(FTP.BINARY_FILE_TYPE)
            client.setBufferSize(DEFAULT_BUFFER_SIZE)
            val remotePath = job.remoteTransferPath()
            ensureRemoteDirectory(client, remotePath.substringBeforeLast('/', ""))
            val totalBytes = job.localFile.length().coerceAtLeast(1L)
            val digest = MessageDigest.getInstance("MD5")
            DigestCountingInputStream(job.localFile.inputStream(), digest) { sentBytes ->
                onProgress(((sentBytes.toDouble() / totalBytes.toDouble()) * 100.0).toInt().coerceIn(0, 100))
            }.use { input ->
                if (!client.storeFile(remotePath, input)) {
                    return BambuLanTransferResult.failure("Bambu FTPS upload failed: ${client.replyString.orEmpty().trim()}")
                }
            }
            onProgress(100)
            BambuLanTransferResult.success(digest.digest().joinToString("") { byte -> "%02x".format(byte) })
        } catch (_: SocketTimeoutException) {
            BambuLanTransferResult.failure("Bambu FTPS connection timed out.")
        } catch (exception: Exception) {
            BambuLanTransferResult.failure(exception.message ?: "Bambu FTPS upload failed.")
        } finally {
            runCatching { if (client.isConnected) client.logout() }
            runCatching { if (client.isConnected) client.disconnect() }
        }
    }

    private fun ensureRemoteDirectory(client: FTPSClient, directory: String) {
        if (directory.isBlank() || directory == "/") return
        var current = ""
        directory.split('/').filter { it.isNotBlank() }.forEach { part ->
            current += "/$part"
            if (!client.changeWorkingDirectory(current)) {
                client.makeDirectory(current)
            }
        }
    }
}

internal class BambuLanMqttTransportClient : BambuLanMqttClient {
    private var client: MqttClient? = null

    override fun connect(device: BambuLanDeviceConfig): NetworkResult =
        try {
            val mqttClient = MqttClient(
                "ssl://${device.host}:${device.mqttPort}",
                "MobileSlicer-${UUID.randomUUID()}",
                MemoryPersistence()
            )
            val options = MqttConnectOptions().apply {
                isCleanSession = true
                connectionTimeout = 10
                keepAliveInterval = 30
                userName = device.username
                password = device.accessCode.toCharArray()
                socketFactory = insecureLocalBambuSslContext().socketFactory
            }
            mqttClient.connect(options)
            client = mqttClient
            NetworkResult.Success
        } catch (exception: MqttException) {
            NetworkResult.Failure(exception.message ?: "Bambu MQTT connection failed.")
        } catch (exception: Exception) {
            NetworkResult.Failure(exception.message ?: "Bambu MQTT connection failed.")
        }

    override fun startPrint(device: BambuLanDeviceConfig, job: BambuLanPrintJob, uploadedFile: BambuLanUploadedFile): NetworkResult {
        val mqttClient = client ?: return NetworkResult.Failure("Bambu MQTT is not connected.")
        return try {
            val message = MqttMessage(bambuProjectFileCommand(job, uploadedFile).toByteArray(Charsets.UTF_8)).apply {
                qos = 1
                isRetained = false
            }
            mqttClient.publish("device/${device.deviceId}/request", message)
            NetworkResult.Success
        } catch (exception: MqttException) {
            NetworkResult.Failure(exception.message ?: "Bambu MQTT start command failed.")
        }
    }

    override fun disconnect() {
        val mqttClient = client ?: return
        runCatching { if (mqttClient.isConnected) mqttClient.disconnect() }
        runCatching { mqttClient.close() }
        client = null
    }
}

internal fun bambuProjectFileCommand(job: BambuLanPrintJob, uploadedFile: BambuLanUploadedFile): String {
    val remotePath = uploadedFile.sdcardUrlPath()
    return JSONObject()
        .put(
            "print",
            JSONObject()
                .put("sequence_id", System.currentTimeMillis().toString())
                .put("command", "project_file")
                .put("url", remotePath)
                .put("param", "Metadata/plate_${job.options.plateIndex + 1}.gcode")
                .put("subtask_id", "0")
                .put("profile_id", "0")
                .put("project_id", "0")
                .put("task_id", "0")
                .put("subtask_name", job.taskName)
                .put("md5", uploadedFile.md5)
                .put("timelapse", job.options.timelapse)
                .put("bed_leveling", job.options.bedLeveling)
                .put("flow_cali", job.options.flowCalibration)
                .put("vibration_cali", job.options.vibrationCalibration)
                .put("use_ams", job.options.useAms)
                .put("ams_mapping", job.options.amsMapping)
                .put("nozzle_mapping", job.options.nozzleMapping)
                .put("bed_type", job.options.bedType)
        )
        .toString()
}

internal fun PrinterProfile.bambuLanPrintOptions(): BambuLanPrintOptions =
    BambuLanPrintOptions(
        bedType = bambuBedType.trim(),
        useAms = bambuUseAms,
        amsMapping = bambuAmsMapping.trim(),
        nozzleMapping = bambuNozzleMapping.trim(),
        bedLeveling = bambuBedLeveling,
        flowCalibration = bambuFlowCalibration,
        vibrationCalibration = bambuVibrationCalibration,
        timelapse = bambuTimelapse
    )

private fun BambuLanPrintJob.remoteTransferPath(): String =
    "/" + listOf(remoteFolder.trim('/'), remoteFileName.substringAfterLast('/').substringAfterLast('\\'))
        .filter { it.isNotBlank() }
        .joinToString("/")

private fun BambuLanUploadedFile.sdcardUrlPath(): String =
    (listOf(BAMBU_SDCARD_URL_ROOT) + listOf(folder.trim('/'), fileName.substringAfterLast('/').substringAfterLast('\\')))
        .filter { it.isNotBlank() }
        .joinToString("/")

private class DigestCountingInputStream(
    private val delegate: java.io.InputStream,
    private val digest: MessageDigest,
    private val onBytesRead: (Long) -> Unit
) : java.io.InputStream() {
    private var totalBytes: Long = 0L

    override fun read(): Int {
        val value = delegate.read()
        if (value >= 0) {
            digest.update(value.toByte())
            report(1)
        }
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val read = delegate.read(buffer, offset, length)
        if (read > 0) {
            digest.update(buffer, offset, read)
            report(read)
        }
        return read
    }

    override fun close() {
        delegate.close()
    }

    private fun report(bytes: Int) {
        totalBytes += bytes.toLong()
        onBytesRead(totalBytes)
    }
}

private fun insecureLocalBambuSslContext(): SSLContext =
    SSLContext.getInstance("TLS").apply {
        // Bambu LAN printers use local certificates that do not chain to public
        // roots. Keep this trust policy scoped to Bambu LAN MQTT/FTPS clients.
        init(null, arrayOf<TrustManager>(trustAllManager()), SecureRandom())
    }

@SuppressLint("CustomX509TrustManager", "TrustAllX509TrustManager")
private fun trustAllManager(): X509TrustManager =
    object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

private const val BAMBU_SDCARD_URL_ROOT = "file://" + "/" + "sdcard"
