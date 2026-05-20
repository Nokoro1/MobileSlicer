package com.mobileslicer.printerconnection

import com.mobileslicer.profiles.ProfileStoreRepository
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BambuLanConnectionClientTest {
    @Test
    fun testConnectionRequiresLanAccessCode() {
        val client = BambuLanConnectionClient(canOpenTcp = { _, _, _ -> true })
        val profile = ProfileStoreRepository.fallbackPrinterProfile().copy(
            printHostApiKey = "",
            printHostPort = "SERIAL123"
        )

        val result = client.testConnection(profile, "http://192.168.1.55")

        assertFalse(result.success)
        assertTrue(result.detail.contains("Bambu LAN access code"))
    }

    @Test
    fun testConnectionRequiresEightCharacterAccessCode() {
        val client = BambuLanConnectionClient(canOpenTcp = { _, _, _ -> true })
        val profile = ProfileStoreRepository.fallbackPrinterProfile().copy(
            printHostApiKey = "1234",
            printHostPort = "SERIAL123"
        )

        val result = client.testConnection(profile, "http://192.168.1.55")

        assertFalse(result.success)
        assertTrue(result.detail.contains("8 characters"))
    }

    @Test
    fun testConnectionRequiresDeviceSerial() {
        val client = BambuLanConnectionClient(canOpenTcp = { _, _, _ -> true })
        val profile = ProfileStoreRepository.fallbackPrinterProfile().copy(
            printHostApiKey = "12345678",
            printHostPort = ""
        )

        val result = client.testConnection(profile, "http://192.168.1.55")

        assertFalse(result.success)
        assertTrue(result.detail.contains("Bambu device serial"))
    }

    @Test
    fun testConnectionRequiresMqttAndTransferReachability() {
        val openedPorts = mutableListOf<Int>()
        val client = BambuLanConnectionClient(
            canOpenTcp = { _, port, _ ->
                openedPorts += port
                port == 8883
            }
        )
        val profile = ProfileStoreRepository.fallbackPrinterProfile().copy(
            printHostApiKey = "12345678",
            printHostPort = "SERIAL123"
        )

        val result = client.testConnection(profile, "http://192.168.1.55")

        assertFalse(result.success)
        assertTrue(result.detail.contains("MQTT port reachable"))
        assertTrue(result.detail.contains("FTP/FTPS port not reachable"))
        assertTrue(openedPorts.contains(8883))
        assertTrue(openedPorts.contains(990))
    }

    @Test
    fun testConnectionSucceedsWhenBambuAgentPortsAreReachable() {
        val client = BambuLanConnectionClient(canOpenTcp = { _, port, _ -> port == 8883 || port == 990 })
        val profile = ProfileStoreRepository.fallbackPrinterProfile().copy(
            printHostApiKey = "12345678",
            printHostPort = "SERIAL123"
        )

        val result = client.testConnection(profile, "http://192.168.1.55")

        assertTrue(result.success)
        assertTrue(result.detail.contains("Upload-only and upload-and-start can use the Bambu LAN device agent."))
    }
}
