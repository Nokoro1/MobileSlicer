package com.mobileslicer.profiles

import android.content.Context
import android.net.Uri
import java.nio.charset.StandardCharsets

internal fun importProfilePayloadFromDeviceUri(context: Context, uri: Uri, currentStore: ProfileStore): ProfileImportPayload {
    val displayName = queryDisplayName(context, uri) ?: "imported-profiles"
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?: error("Unable to read selected profile file.")
    val zipPayload = parseProfilePayloadZip(context, bytes, currentStore)
    if (zipPayload != null) return zipPayload
    return parseProfilePayloadText(context, bytes.toString(StandardCharsets.UTF_8), displayName, currentStore)
}
