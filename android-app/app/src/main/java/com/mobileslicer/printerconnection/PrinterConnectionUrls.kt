package com.mobileslicer.printerconnection

import java.io.InputStream
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal fun normalizeBaseUrl(input: String): String? {
    val trimmed = input.trim()
    if (trimmed.isBlank()) return null
    val withScheme = if ("://" in trimmed) trimmed else "http://$trimmed"
    return runCatching {
        val uri = URI(withScheme)
        val scheme = uri.scheme ?: "http"
        val host = uri.host ?: return@runCatching null
        val port = if (uri.port >= 0) ":${uri.port}" else ""
        "$scheme://$host$port"
    }.getOrNull()
}

internal fun printerHostCandidates(baseUrl: String): List<String> {
    val candidates = mutableListOf<String>()
    val uri = runCatching { URI(baseUrl) }.getOrNull() ?: return listOf(baseUrl)
    val host = uri.host ?: return listOf(baseUrl)
    if (uri.port < 0) {
        candidates += "${uri.scheme}://$host:10088"
        candidates += "${uri.scheme}://$host:7125"
    }
    candidates += baseUrl
    return candidates.distinct()
}

internal fun requireAllowedPrinterBaseUrl(baseUrl: String): PrinterConnectionResult? {
    val uri = runCatching { URI(baseUrl) }.getOrNull()
        ?: return PrinterConnectionResult(false, "Connection failed", "Printer URL could not be parsed.")
    val scheme = uri.scheme?.lowercase()
    return when {
        scheme == "https" -> null
        scheme != "http" -> PrinterConnectionResult(false, "Connection failed", "Unsupported printer URL scheme. Use HTTP for local printers or HTTPS.")
        isLocalPrinterHost(uri.host) -> null
        else -> PrinterConnectionResult(
            false,
            "Connection refused",
            "Cleartext HTTP is only allowed for local printer addresses. Use HTTPS for non-local hosts."
        )
    }
}

internal fun requireAllowedPrinterNetworkUrl(url: String) {
    val uri = runCatching { URI(url) }.getOrNull()
        ?: throw IOException("Printer URL could not be parsed.")
    val scheme = uri.scheme?.lowercase()
    when {
        scheme == "https" -> return
        scheme == "http" && isLocalPrinterHost(uri.host) -> return
        scheme == "http" -> throw IOException("Cleartext HTTP is only allowed for local printer addresses.")
        else -> throw IOException("Unsupported printer URL scheme. Use HTTP for local printers or HTTPS.")
    }
}

internal fun isLocalPrinterHost(host: String?): Boolean {
    val normalized = host?.trim()?.trim('[', ']')?.lowercase().orEmpty()
    if (normalized.isBlank()) return false
    if (
        normalized == "localhost" ||
        normalized.endsWith(".local") ||
        normalized.endsWith(".ts.net") ||
        "." !in normalized && ":" !in normalized
    ) return true
    val octets = normalized.split('.').mapNotNull { it.toIntOrNull() }
    if (octets.size == 4 && octets.all { it in 0..255 }) {
        return octets[0] == 10 ||
            octets[0] == 127 ||
            octets[0] == 192 && octets[1] == 168 ||
            octets[0] == 172 && octets[1] in 16..31 ||
            octets[0] == 169 && octets[1] == 254 ||
            octets[0] == 100 && octets[1] in 64..127
    }
    return normalized == "::1" ||
        normalized.startsWith("fe80:") ||
        normalized.startsWith("fc") ||
        normalized.startsWith("fd")
}

internal fun safeDisplayUrl(url: String): String =
    runCatching {
        val uri = URI(url)
        val scheme = uri.scheme ?: "http"
        val host = uri.host ?: return@runCatching url.substringBefore('?').substringBefore('#')
        val port = if (uri.port >= 0) ":${uri.port}" else ""
        val path = uri.rawPath.orEmpty()
        "$scheme://$host$port$path"
    }.getOrElse { url.substringBefore('?').substringBefore('#') }

internal fun printerHostName(baseUrl: String): String? =
    runCatching { URI(baseUrl).host?.trim()?.takeIf { it.isNotBlank() } }.getOrNull()

internal fun httpFailureMessage(code: Int, url: String): String {
    val displayUrl = safeDisplayUrl(url)
    return when (code) {
        401 -> "HTTP 401 unauthorized from $displayUrl. Check the printer API key, username, or password."
        403 -> "HTTP 403 forbidden from $displayUrl. The printer rejected this app's credentials or permissions."
        404 -> "HTTP 404 not found from $displayUrl. This may be the wrong printer service or port."
        405 -> "HTTP 405 method not allowed from $displayUrl. This usually means the app reached the web UI instead of the printer API."
        409 -> "HTTP 409 conflict from $displayUrl. The printer may be busy or not ready for that action."
        in 500..599 -> "HTTP $code from $displayUrl. The printer service returned a server error."
        else -> "HTTP $code from $displayUrl."
    }
}

internal fun urlEncode(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

internal fun Map<String, String>.toQueryString(): String =
    entries.joinToString("&") { (key, value) -> "${urlEncode(key)}=${urlEncode(value)}" }

internal fun InputStream.skipFully(byteCount: Long) {
    var remaining = byteCount
    while (remaining > 0L) {
        val skipped = skip(remaining)
        if (skipped <= 0L) {
            if (read() == -1) break
            remaining -= 1L
        } else {
            remaining -= skipped
        }
    }
}
