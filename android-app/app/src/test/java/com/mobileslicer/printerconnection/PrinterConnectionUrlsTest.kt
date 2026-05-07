package com.mobileslicer.printerconnection

import java.io.IOException
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PrinterConnectionUrlsTest {
    @Test
    fun requireAllowedPrinterBaseUrlAllowsLocalCleartext() {
        assertNull(requireAllowedPrinterBaseUrl("http://192.168.1.42"))
        assertNull(requireAllowedPrinterBaseUrl("http://10.0.0.5:7125"))
        assertNull(requireAllowedPrinterBaseUrl("http://172.16.0.5:7125"))
        assertNull(requireAllowedPrinterBaseUrl("http://169.254.20.5"))
        assertNull(requireAllowedPrinterBaseUrl("http://printer.local"))
        assertNull(requireAllowedPrinterBaseUrl("http://printer-lan"))
        assertNull(requireAllowedPrinterBaseUrl("http://localhost:5000"))
    }

    @Test
    fun requireAllowedPrinterBaseUrlRejectsPublicCleartext() {
        val result = requireAllowedPrinterBaseUrl("http://example.com")

        check(result != null)
        assertTrue(result.detail.contains("Cleartext HTTP"))
    }

    @Test
    fun requireAllowedPrinterBaseUrlRejectsPublicIpCleartext() {
        val result = requireAllowedPrinterBaseUrl("http://8.8.8.8")

        check(result != null)
        assertTrue(result.detail.contains("Cleartext HTTP"))
    }

    @Test
    fun requireAllowedPrinterNetworkUrlAllowsHttpsAndLocalCleartext() {
        requireAllowedPrinterNetworkUrl("https://example.com/api/files")
        requireAllowedPrinterNetworkUrl("http://192.168.1.42/api/files")
        requireAllowedPrinterNetworkUrl("http://printer.local/api/files")
        requireAllowedPrinterNetworkUrl("http://printer-lan/api/files")
    }

    @Test
    fun requireAllowedPrinterNetworkUrlRejectsPublicCleartextBeforeNetworkIo() {
        assertThrows(IOException::class.java) {
            requireAllowedPrinterNetworkUrl("http://example.com/api/files")
        }
    }
}
