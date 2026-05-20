package com.mobileslicer.printerconnection

import com.mobileslicer.profiles.ProfileStoreRepository
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

class BambuLanPrinterAgentTest {
    @Test
    fun deviceConfigMapsProfileFieldsToBambuAgentFields() {
        val agent = BambuLanPrinterAgent(NoopTransferClient(), RecordingMqttClient())
        val profile = ProfileStoreRepository.fallbackPrinterProfile().copy(
            printHostApiKey = "12345678",
            printHostPort = "SERIAL123"
        )

        val device = agent.deviceConfig(profile, "http://192.168.1.55")

        check(device != null)
        assertEquals("192.168.1.55", device.host)
        assertEquals("SERIAL123", device.deviceId)
        assertEquals("12345678", device.accessCode)
        assertEquals("bblp", device.username)
        assertEquals(8883, device.mqttPort)
        assertEquals(990, device.transferPort)
    }

    @Test
    fun deviceConfigRejectsInvalidAccessCode() {
        val agent = BambuLanPrinterAgent(NoopTransferClient(), RecordingMqttClient())
        val profile = ProfileStoreRepository.fallbackPrinterProfile().copy(
            printHostApiKey = "1234",
            printHostPort = "SERIAL123"
        )

        val device = agent.deviceConfig(profile, "http://192.168.1.55")

        assertEquals(null, device)
    }

    @Test
    fun profileMapsToBambuLanPrintOptions() {
        val profile = ProfileStoreRepository.fallbackPrinterProfile().copy(
            bambuBedType = "Textured PEI Plate",
            bambuUseAms = true,
            bambuAmsMapping = "0,1",
            bambuNozzleMapping = "0",
            bambuBedLeveling = false,
            bambuFlowCalibration = true,
            bambuVibrationCalibration = true,
            bambuTimelapse = true
        )

        val options = profile.bambuLanPrintOptions()

        assertEquals("Textured PEI Plate", options.bedType)
        assertTrue(options.useAms)
        assertEquals("0,1", options.amsMapping)
        assertEquals("0", options.nozzleMapping)
        assertEquals(false, options.bedLeveling)
        assertTrue(options.flowCalibration)
        assertTrue(options.vibrationCalibration)
        assertTrue(options.timelapse)
    }

    @Test
    fun uploadAndStartTransfersFileThenStartsPrintOverMqtt() {
        val transfer = RecordingTransferClient()
        val mqtt = RecordingMqttClient()
        val agent = BambuLanPrinterAgent(transfer, mqtt)
        val file = File.createTempFile("mobile-slicer-bambu", ".gcode.3mf").apply {
            writeText("bambu")
            deleteOnExit()
        }
        val device = BambuLanDeviceConfig(
            host = "192.168.1.55",
            deviceId = "SERIAL123",
            accessCode = "12345678"
        )
        val job = BambuLanPrintJob(
            taskName = "Benchy",
            projectName = "Benchy",
            localFile = file,
            remoteFileName = "Benchy.gcode.3mf",
            options = BambuLanPrintOptions(bedType = "Textured PEI Plate")
        )

        val result = agent.uploadAndStart(device, job, onProgress = {})

        assertTrue(result.success)
        assertEquals(device, transfer.device)
        assertEquals(job, transfer.job)
        assertEquals(device, mqtt.startedDevice)
        assertEquals(job, mqtt.startedJob)
        assertEquals(
            BambuLanUploadedFile("/", "Benchy.gcode.3mf", "7eeae359c9908bb89e8ade41be5e89bc"),
            mqtt.uploadedFile
        )
        assertTrue(mqtt.disconnected)
    }

    @Test
    fun uploadOnlyTransfersFileWithoutMqttStart() {
        val transfer = RecordingTransferClient()
        val mqtt = RecordingMqttClient()
        val agent = BambuLanPrinterAgent(transfer, mqtt)
        val file = File.createTempFile("mobile-slicer-bambu", ".gcode.3mf").apply {
            writeText("bambu")
            deleteOnExit()
        }
        val device = BambuLanDeviceConfig(
            host = "192.168.1.55",
            deviceId = "SERIAL123",
            accessCode = "12345678"
        )
        val job = BambuLanPrintJob(
            taskName = "Benchy",
            projectName = "Benchy",
            localFile = file,
            remoteFileName = "Benchy.gcode.3mf"
        )

        val result = agent.uploadOnly(device, job, onProgress = {})

        assertTrue(result.success)
        assertEquals(device, transfer.device)
        assertEquals(job, transfer.job)
        assertEquals(null, mqtt.startedDevice)
        assertEquals(false, mqtt.disconnected)
    }

    @Test
    fun projectFileCommandMatchesBambuLanMqttShape() {
        val command = bambuProjectFileCommand(
            job = BambuLanPrintJob(
                taskName = "Benchy",
                projectName = "Benchy",
                localFile = File("Benchy.gcode.3mf"),
                remoteFileName = "Benchy.gcode.3mf",
                options = BambuLanPrintOptions(
                    plateIndex = 0,
                    bedType = "Textured PEI Plate",
                    useAms = true,
                    bedLeveling = true,
                    flowCalibration = true,
                    vibrationCalibration = false,
                    timelapse = true
                )
            ),
            uploadedFile = BambuLanUploadedFile("/", "Benchy.gcode.3mf", "abc123")
        )

        val print = JSONObject(command).getJSONObject("print")
        assertEquals("project_file", print.getString("command"))
        assertEquals("file:///sdcard/Benchy.gcode.3mf", print.getString("url"))
        assertEquals("Metadata/plate_1.gcode", print.getString("param"))
        assertEquals("abc123", print.getString("md5"))
        assertTrue(print.getBoolean("use_ams"))
        assertTrue(print.getBoolean("bed_leveling"))
        assertTrue(print.getBoolean("flow_cali"))
        assertTrue(print.getBoolean("timelapse"))
    }

    private class NoopTransferClient : BambuLanTransferClient {
        override fun upload(
            device: BambuLanDeviceConfig,
            job: BambuLanPrintJob,
            onProgress: (Int) -> Unit
        ): BambuLanTransferResult = BambuLanTransferResult.success("7eeae359c9908bb89e8ade41be5e89bc")
    }

    private class RecordingTransferClient : BambuLanTransferClient {
        var device: BambuLanDeviceConfig? = null
        var job: BambuLanPrintJob? = null

        override fun upload(
            device: BambuLanDeviceConfig,
            job: BambuLanPrintJob,
            onProgress: (Int) -> Unit
        ): BambuLanTransferResult {
            this.device = device
            this.job = job
            return BambuLanTransferResult.success("7eeae359c9908bb89e8ade41be5e89bc")
        }
    }

    private class RecordingMqttClient : BambuLanMqttClient {
        var startedDevice: BambuLanDeviceConfig? = null
        var startedJob: BambuLanPrintJob? = null
        var uploadedFile: BambuLanUploadedFile? = null
        var disconnected: Boolean = false

        override fun connect(device: BambuLanDeviceConfig): NetworkResult = NetworkResult.Success

        override fun startPrint(
            device: BambuLanDeviceConfig,
            job: BambuLanPrintJob,
            uploadedFile: BambuLanUploadedFile
        ): NetworkResult {
            startedDevice = device
            startedJob = job
            this.uploadedFile = uploadedFile
            return NetworkResult.Success
        }

        override fun disconnect() {
            disconnected = true
        }
    }
}
