package com.mobileslicer.printerconnection

import com.mobileslicer.profiles.PrinterProfile

internal class BambuLanConnectionClient(
    private val canOpenTcp: (host: String, port: Int, timeoutMs: Int) -> Boolean
) {
    fun testConnection(profile: PrinterProfile, baseUrl: String): PrinterConnectionResult {
        val config = profile.bambuLanDeviceConfig(baseUrl)
            ?: return PrinterConnectionResult(false, "Connection failed", profile.bambuLanProfileProblem(baseUrl))
        val mqtt = canOpenTcp(config.host, config.mqttPort, 2_000)
        val transfer = canOpenTcp(config.host, config.transferPort, 2_000)
        return if (mqtt && transfer) {
            PrinterConnectionResult(
                true,
                "Connection successful",
                buildList {
                    add("Bambu LAN host reachable at ${config.host}.")
                    add("Device ID: ${config.deviceId}")
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
                    add("Bambu LAN host was found at ${config.host}, but required local services were not both reachable.")
                    add(if (mqtt) "MQTT port reachable" else "MQTT port not reachable")
                    add(if (transfer) "FTP/FTPS port reachable" else "FTP/FTPS port not reachable")
                    add("Upload requires FTP/FTPS; upload-and-start also requires MQTT. Confirm LAN mode, IP address, and access code.")
                }.joinToString(" • ")
            )
        }
    }
}

internal fun PrinterProfile.bambuLanDeviceConfig(baseUrl: String): BambuLanDeviceConfig? {
    val host = printerHostName(baseUrl) ?: return null
    val accessCode = printHostApiKey.trim().takeIf { it.length == BAMBU_LAN_ACCESS_CODE_LENGTH } ?: return null
    val deviceId = printHostPort.trim().takeIf { it.isNotBlank() } ?: return null
    return BambuLanDeviceConfig(
        host = host,
        deviceId = deviceId,
        accessCode = accessCode
    )
}

internal fun PrinterProfile.bambuLanProfileProblem(baseUrl: String): String {
    if (printerHostName(baseUrl) == null) {
        return "Bambu LAN host could not be parsed."
    }
    val accessCode = printHostApiKey.trim()
    if (accessCode.isBlank()) {
        return "Enter the Bambu LAN access code in Access code."
    }
    if (accessCode.length != BAMBU_LAN_ACCESS_CODE_LENGTH) {
        return "Bambu LAN access codes are expected to be $BAMBU_LAN_ACCESS_CODE_LENGTH characters."
    }
    if (printHostPort.trim().isBlank()) {
        return "Enter the Bambu device serial in Device serial. This is used as the LAN device ID."
    }
    return "Enter Bambu LAN IP, an $BAMBU_LAN_ACCESS_CODE_LENGTH-character access code, and the device serial/dev id."
}

private const val BAMBU_LAN_ACCESS_CODE_LENGTH = 8
