package com.mobileslicer.modelsearch.thingiverse

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

class ThingiverseApiClient(
    private val appToken: String,
    private val baseUrl: String = "https://api.thingiverse.com"
) {
    val isConfigured: Boolean
        get() = appToken.isNotBlank()

    fun searchThings(
        query: String,
        page: Int = 1,
        perPage: Int = DEFAULT_SEARCH_PAGE_SIZE
    ): List<ThingiverseSearchResult> {
        requireConfigured()
        val encodedQuery = URLEncoder.encode(query.trim(), Charsets.UTF_8.name())
            .replace("+", "%20")
        val json = getJsonObject("/search/$encodedQuery/?type=things&page=$page&per_page=$perPage&sort=relevant")
        val hits = json.optJSONArray("hits") ?: JSONArray()
        return List(hits.length()) { index -> hits.optJSONObject(index) }
            .mapNotNull { item -> item?.toSearchResult() }
    }

    fun filesForThing(thingId: Long): List<ThingiverseFileResult> {
        requireConfigured()
        val json = getJsonArray("/things/$thingId/files")
        return List(json.length()) { index -> json.optJSONObject(index) }
            .mapNotNull { item -> item?.toFileResult() }
    }

    fun downloadFile(
        context: Context,
        file: ThingiverseFileResult
    ): File {
        requireConfigured()
        if (!file.isSupportedModelFile) {
            throw ThingiverseDownloadException("Only STL, 3MF, STEP, and STP files can be imported from Thingiverse.")
        }
        val url = "$baseUrl/files/${file.fileId}/download"
        val targetDir = File(context.cacheDir, "thingiverse-imports").apply { mkdirs() }
        val target = File(targetDir, file.name.sanitizeFileName()).also { existing ->
            if (existing.exists() && !existing.delete()) {
                throw ThingiverseDownloadException("Could not replace the previous Thingiverse download.")
            }
        }
        targetDir.deleteOtherDownloadedFiles(target)
        val connection = openConnection(url, includeAuthorization = true, accept = "*/*")
        try {
            val status = connection.responseCode
            if (status in 300..399) {
                val location = connection.getHeaderField("Location")
                    ?: throw ThingiverseDownloadException("Thingiverse did not return a file download location.")
                connection.disconnect()
                return downloadRedirectedFile(location, target)
            }
            if (status !in 200..299) {
                throw ThingiverseApiException(status, connection.errorStream?.bufferedReader()?.readText().orEmpty())
            }
            enforceDownloadLength(connection)
            streamToFileWithLimit(connection, target)
            return target
        } catch (error: Exception) {
            target.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadRedirectedFile(location: String, target: File, redirectCount: Int = 0): File {
        if (redirectCount >= MAX_DOWNLOAD_REDIRECTS) {
            throw ThingiverseDownloadException("Thingiverse download redirected too many times.")
        }
        val redirectUrl = validateDownloadRedirect(location)
        val connection = openConnection(redirectUrl, includeAuthorization = false, accept = "*/*")
        try {
            val status = connection.responseCode
            if (status in 300..399) {
                val nextLocation = connection.getHeaderField("Location")
                    ?: throw ThingiverseDownloadException("Thingiverse download redirect was invalid.")
                connection.disconnect()
                return downloadRedirectedFile(nextLocation, target, redirectCount + 1)
            }
            if (status !in 200..299) {
                throw ThingiverseDownloadException("Thingiverse download failed after redirect.")
            }
            enforceDownloadLength(connection)
            streamToFileWithLimit(connection, target)
            return target
        } catch (error: Exception) {
            target.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    private fun enforceDownloadLength(connection: HttpURLConnection) {
        val length = connection.getHeaderFieldLong("Content-Length", -1L)
        if (length > MAX_DOWNLOAD_BYTES) {
            throw ThingiverseDownloadException("Thingiverse file is too large to import.")
        }
    }

    private fun streamToFileWithLimit(connection: HttpURLConnection, target: File) {
        var totalBytes = 0L
        connection.inputStream.use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    totalBytes += read.toLong()
                    if (totalBytes > MAX_DOWNLOAD_BYTES) {
                        throw ThingiverseDownloadException("Thingiverse file is too large to import.")
                    }
                    output.write(buffer, 0, read)
                }
            }
        }
    }

    private fun getJsonObject(pathAndQuery: String): JSONObject {
        val body = getText(pathAndQuery)
        return JSONObject(body)
    }

    private fun getJsonArray(pathAndQuery: String): JSONArray {
        val body = getText(pathAndQuery)
        return JSONArray(body)
    }

    private fun getText(pathAndQuery: String): String {
        val connection = openConnection("$baseUrl$pathAndQuery", includeAuthorization = true, accept = "application/json")
        return try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                throw ThingiverseApiException(status, body)
            }
            body
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(url: String, includeAuthorization: Boolean, accept: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = 15_000
            readTimeout = 45_000
            requestMethod = "GET"
            setRequestProperty("Accept", accept)
            if (includeAuthorization) {
                setRequestProperty("Authorization", "Bearer $appToken")
            }
            setRequestProperty("User-Agent", THINGIVERSE_API_USER_AGENT)
        }

    private fun requireConfigured() {
        check(isConfigured) { "Thingiverse API token is not configured." }
    }

    private fun JSONObject.toSearchResult(): ThingiverseSearchResult? {
        val id = optLong("id", -1L).takeIf { it > 0L } ?: return null
        return ThingiverseSearchResult(
            thingId = id,
            name = optString("name", "Untitled").ifBlank { "Untitled" },
            creatorName = optJSONObject("creator")?.optString("name")?.ifBlank { null }
                ?: optJSONObject("creator")?.optString("username")?.ifBlank { null },
            publicUrl = optString("public_url").ifBlank { "https://www.thingiverse.com/thing:$id" },
            thumbnailUrl = optString("preview_image").ifBlank { null }
                ?: optString("thumbnail").ifBlank { null }
                ?: optString("thumbnail_url").ifBlank { null },
            license = optString("license").ifBlank { null },
            fileCount = optIntOrNull("file_count"),
            likeCount = optIntOrNull("like_count"),
            downloadCount = optIntOrNull("download_count")
        )
    }

    private fun JSONObject.toFileResult(): ThingiverseFileResult? {
        val id = optLong("id", -1L).takeIf { it > 0L } ?: return null
        return ThingiverseFileResult(
            fileId = id,
            name = optString("name", "thingiverse-file-$id").ifBlank { "thingiverse-file-$id" },
            sizeBytes = optLongOrNull("size"),
            formattedSize = optString("formatted_size").ifBlank { null },
            thumbnailUrl = optFileThumbnailUrl(),
            downloadUrl = optString("download_url").ifBlank { null },
            publicUrl = optString("public_url").ifBlank { null },
            directUrl = optString("direct_url").ifBlank { null }
        )
    }

    private fun JSONObject.optFileThumbnailUrl(): String? =
        optJSONObject("default_image")
            ?.optJSONArray("sizes")
            ?.let { sizes ->
                sequence {
                    for (index in 0 until sizes.length()) {
                        yield(sizes.optJSONObject(index))
                    }
                }
                    .filterNotNull()
                    .firstOrNull { item ->
                        item.optString("type") == "preview" && item.optString("size") in listOf("small", "medium", "card")
                    }
                    ?.optString("url")
                    ?.ifBlank { null }
            }
            ?: optString("thumbnail").ifBlank { null }

    private fun JSONObject.optIntOrNull(name: String): Int? =
        if (has(name) && !isNull(name)) optInt(name) else null

    private fun JSONObject.optLongOrNull(name: String): Long? =
        if (has(name) && !isNull(name)) optLong(name) else null

    private fun String.sanitizeFileName(): String =
        trim()
            .ifBlank { "thingiverse-model" }
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .lowercase(Locale.US)

    private fun File.deleteOtherDownloadedFiles(retainedFile: File) {
        val retainedPath = retainedFile.absolutePath
        listFiles()?.forEach { file ->
            if (file.isFile && file.absolutePath != retainedPath) {
                file.delete()
            }
        }
    }

    private fun validateDownloadRedirect(location: String): String {
        val uri = URI(location)
        if (uri.scheme != "https") {
            throw ThingiverseDownloadException("Thingiverse download redirect must use HTTPS.")
        }
        val host = uri.host?.lowercase(Locale.US)
            ?: throw ThingiverseDownloadException("Thingiverse download redirect is invalid.")
        val allowed = host == "thingiverse.com" ||
            host.endsWith(".thingiverse.com") ||
            host == "makerbot.com" ||
            host.endsWith(".makerbot.com") ||
            host == "s3.amazonaws.com" ||
            host.endsWith(".amazonaws.com") ||
            host.endsWith(".cloudfront.net")
        if (!allowed) {
            throw ThingiverseDownloadException("Thingiverse download redirected to an unexpected host.")
        }
        return uri.toString()
    }

    companion object {
        const val THINGIVERSE_API_USER_AGENT = "MobileSlicer Android ThingiverseImport"
        const val DEFAULT_SEARCH_PAGE_SIZE = 20
        const val MAX_DOWNLOAD_BYTES = 250L * 1024L * 1024L
        const val MAX_DOWNLOAD_REDIRECTS = 5
    }
}

class ThingiverseApiException(
    val statusCode: Int,
    body: String
) : RuntimeException(
    when (statusCode) {
        401 -> "Thingiverse authentication failed. Check the app token."
        403 -> "Thingiverse denied this request."
        404 -> "Thingiverse could not find that model or file."
        429 -> "Thingiverse rate limit reached. Try again later."
        else -> "Thingiverse API error $statusCode${body.takeIf { it.isNotBlank() }?.let { ": ${it.take(160)}" }.orEmpty()}"
    }
)

class ThingiverseDownloadException(message: String) : IOException(message)

fun File.toLocalUri(): Uri = Uri.fromFile(this)
