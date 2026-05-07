package com.mobileslicer.printerconnection

import com.mobileslicer.profiles.PrinterProfile

internal class BambuLanConnectionClient(
    private val canOpenTcp: (host: String, port: Int, timeoutMs: Int) -> Boolean
) {
    fun testConnection(profile: PrinterProfile, baseUrl: String): PrinterConnectionResult {
        val host = printerHostName(baseUrl)
            ?: return PrinterConnectionResult(false, "Connection failed", "Bambu LAN host could not be parsed.")
        val accessCode = profile.printHostApiKey.trim()
        val serial = profile.printHostPort.trim()
        if (accessCode.isBlank()) {
            return PrinterConnectionResult(false, "Connection failed", "Enter the Bambu LAN access code in Access code.")
        }
        if (accessCode.length != 8) {
            return PrinterConnectionResult(false, "Connection failed", "Bambu LAN access codes are expected to be 8 characters.")
        }
        if (serial.isBlank()) {
            return PrinterConnectionResult(false, "Connection failed", "Enter the Bambu device serial in Device serial. This is used as the LAN device ID.")
        }
        val mqtt = canOpenTcp(host, 8883, 2_000)
        val transfer = canOpenTcp(host, 990, 2_000)
        return if (mqtt && transfer) {
            PrinterConnectionResult(
                true,
                "Connection successful",
                buildList {
                    add("Bambu LAN host reachable at $host.")
                    if (serial.isNotBlank()) add("Device ID: $serial")
                    add("MQTT port reachable")
                    add("FTP/FTPS port reachable")
                    add("Upload-only and upload-and-start can use the Bambu LAN device agent.")
                }.joinToString(" • ")
            )
        } else {
            PrinterConnectionResult(
                false,
                "Connection incomplete",
                buildList {
                    add("Bambu LAN host was found at $host, but required local services were not both reachable.")
                    add(if (mqtt) "MQTT port reachable" else "MQTT port not reachable")
                    add(if (transfer) "FTP/FTPS port reachable" else "FTP/FTPS port not reachable")
                    add("Upload requires FTP/FTPS; upload-and-start also requires MQTT. Confirm LAN mode, IP address, and access code.")
                }.joinToString(" • ")
            )
        }
    }
}
