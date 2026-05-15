package com.mobileslicer.scanner

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.json.JSONObject

internal const val SCANNER_MANIFEST_PATH = "manifest.json"
internal const val SCANNER_HASHES_PATH = "package_hashes.json"

internal data class ScannerPackageValidationLimits(
    val maxCompressedBytes: Long = 300L * 1024L * 1024L,
    val maxDecompressedBytes: Long = 900L * 1024L * 1024L,
    val maxFileCount: Int = 750
)

internal data class ScannerPackageValidationResult(
    val valid: Boolean,
    val manifest: ScanPackageManifest?,
    val errors: List<String>,
    val warnings: List<String>,
    val fileCount: Int,
    val decompressedBytes: Long
)

internal fun sha256Hex(file: File): String =
    file.inputStream().use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (read > 0) digest.update(buffer, 0, read)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

internal fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

internal fun writeScannerPackageDirectory(
    directory: File,
    manifest: ScanPackageManifest,
    writeDiagnostics: (() -> Unit)? = null
) {
    require(directory.mkdirs() || directory.isDirectory) {
        "Unable to create scanner package directory: ${directory.absolutePath}"
    }
    val manifestFile = directory.resolve(SCANNER_MANIFEST_PATH)
    manifestFile.writeText(manifest.toJson().toString(2))
    writeDiagnostics?.invoke()
    val hashes = buildPackageHashes(directory)
    directory.resolve(SCANNER_HASHES_PATH).writeText(hashes.toString(2))
}

internal fun buildPackageHashes(directory: File): JSONObject {
    val files = JSONObject()
    directory.walkTopDown()
        .filter { it.isFile }
        .map { it.relativeTo(directory).invariantSeparatorsPath }
        .filter { it != SCANNER_HASHES_PATH }
        .sorted()
        .forEach { relativePath ->
            require(isSafeScannerPackagePath(relativePath)) {
                "Unsafe scanner package path: $relativePath"
            }
            files.put(relativePath, sha256Hex(directory.resolve(relativePath)))
        }
    return JSONObject()
        .put("schema_version", SCANNER_PACKAGE_SCHEMA_VERSION)
        .put("files", files)
}

internal fun writeScannerPackageZip(directory: File, zipFile: File) {
    require(directory.isDirectory) { "Scanner package directory does not exist: ${directory.absolutePath}" }
    zipFile.parentFile?.mkdirs()
    ZipOutputStream(FileOutputStream(zipFile)).use { zip ->
        directory.walkTopDown()
            .filter { it.isFile }
            .map { it.relativeTo(directory).invariantSeparatorsPath }
            .sorted()
            .forEach { relativePath ->
                require(isSafeScannerPackagePath(relativePath)) {
                    "Unsafe scanner package path: $relativePath"
                }
                zip.putNextEntry(ZipEntry(relativePath))
                FileInputStream(directory.resolve(relativePath)).use { input ->
                    input.copyTo(zip)
                }
                zip.closeEntry()
            }
    }
}

internal fun validateScannerPackageDirectory(directory: File): ScannerPackageValidationResult {
    val errors = mutableListOf<String>()
    val warnings = mutableListOf<String>()
    if (!directory.isDirectory) {
        return ScannerPackageValidationResult(
            valid = false,
            manifest = null,
            errors = listOf("Package directory does not exist"),
            warnings = emptyList(),
            fileCount = 0,
            decompressedBytes = 0L
        )
    }
    val packageFiles = directory.walkTopDown()
        .filter { it.isFile }
        .map { it.relativeTo(directory).invariantSeparatorsPath }
        .toList()
    packageFiles.filterNot(::isSafeScannerPackagePath).forEach {
        errors += "Unsafe package path: $it"
    }
    val fileBytes = packageFiles.sumOf { directory.resolve(it).length() }
    val manifest = parseManifestCatching(
        readText = { directory.resolve(SCANNER_MANIFEST_PATH).readText() },
        errors = errors
    )
    validateHashes(
        hashText = runCatching { directory.resolve(SCANNER_HASHES_PATH).readText() }.getOrNull(),
        readBytes = { relativePath -> directory.resolve(relativePath).readBytes() },
        availablePaths = packageFiles.toSet(),
        errors = errors
    )
    validateManifestFileReferences(manifest, packageFiles.toSet(), errors, warnings)
    return ScannerPackageValidationResult(
        valid = errors.isEmpty(),
        manifest = manifest,
        errors = errors,
        warnings = warnings,
        fileCount = packageFiles.size,
        decompressedBytes = fileBytes
    )
}

internal fun validateScannerPackageZip(
    zipFile: File,
    limits: ScannerPackageValidationLimits = ScannerPackageValidationLimits()
): ScannerPackageValidationResult {
    val errors = mutableListOf<String>()
    val warnings = mutableListOf<String>()
    if (!zipFile.isFile) {
        return ScannerPackageValidationResult(
            valid = false,
            manifest = null,
            errors = listOf("Package zip does not exist"),
            warnings = emptyList(),
            fileCount = 0,
            decompressedBytes = 0L
        )
    }
    if (zipFile.length() > limits.maxCompressedBytes) {
        errors += "Package zip exceeds compressed size limit"
    }

    val entries = linkedMapOf<String, ByteArray>()
    ZipFile(zipFile).use { zip ->
        val enumeration = zip.entries()
        while (enumeration.hasMoreElements()) {
            val entry = enumeration.nextElement()
            if (entry.isDirectory) continue
            val name = entry.name
            if (!isSafeScannerPackagePath(name)) {
                errors += "Unsafe package path: $name"
                continue
            }
            if (entries.size >= limits.maxFileCount) {
                errors += "Package exceeds file count limit"
                break
            }
            val bytes = zip.getInputStream(entry).use { it.readBytes() }
            entries[name] = bytes
            val totalBytes = entries.values.sumOf { it.size.toLong() }
            if (totalBytes > limits.maxDecompressedBytes) {
                errors += "Package exceeds decompressed size limit"
                break
            }
        }
    }

    val manifest = parseManifestCatching(
        readText = { entries[SCANNER_MANIFEST_PATH]?.toString(Charsets.UTF_8) ?: error("Missing manifest.json") },
        errors = errors
    )
    validateHashes(
        hashText = entries[SCANNER_HASHES_PATH]?.toString(Charsets.UTF_8),
        readBytes = { relativePath -> entries[relativePath] ?: error("Missing file: $relativePath") },
        availablePaths = entries.keys,
        errors = errors
    )
    validateManifestFileReferences(manifest, entries.keys, errors, warnings)
    return ScannerPackageValidationResult(
        valid = errors.isEmpty(),
        manifest = manifest,
        errors = errors.distinct(),
        warnings = warnings.distinct(),
        fileCount = entries.size,
        decompressedBytes = entries.values.sumOf { it.size.toLong() }
    )
}

internal fun isSafeScannerPackagePath(path: String): Boolean =
    path.isNotBlank() &&
        !path.startsWith("/") &&
        !path.startsWith("\\") &&
        !path.contains('\\') &&
        path.split('/').none { it.isBlank() || it == "." || it == ".." }

private fun parseManifestCatching(
    readText: () -> String,
    errors: MutableList<String>
): ScanPackageManifest? =
    runCatching { parseScanPackageManifest(JSONObject(readText())) }
        .onFailure { errors += "Invalid manifest: ${it.message}" }
        .getOrNull()

private fun validateHashes(
    hashText: String?,
    readBytes: (String) -> ByteArray,
    availablePaths: Set<String>,
    errors: MutableList<String>
) {
    if (hashText == null) {
        errors += "Missing package_hashes.json"
        return
    }
    val hashJson = runCatching { JSONObject(hashText) }
        .onFailure { errors += "Invalid package_hashes.json: ${it.message}" }
        .getOrNull() ?: return
    val schema = hashJson.optInt("schema_version", -1)
    if (schema != SCANNER_PACKAGE_SCHEMA_VERSION) {
        errors += "Unsupported package hash schema: $schema"
    }
    val files = hashJson.optJSONObject("files")
    if (files == null) {
        errors += "Missing package hash file map"
        return
    }
    files.keys().forEach { relativePath ->
        if (!isSafeScannerPackagePath(relativePath)) {
            errors += "Unsafe hashed path: $relativePath"
            return@forEach
        }
        if (relativePath !in availablePaths) {
            errors += "Hashed file is missing: $relativePath"
            return@forEach
        }
        val expected = files.getString(relativePath)
        val actual = runCatching { sha256Hex(readBytes(relativePath)) }
            .getOrElse {
                errors += "Unable to hash file $relativePath: ${it.message}"
                return@forEach
            }
        if (!expected.equals(actual, ignoreCase = true)) {
            errors += "Hash mismatch: $relativePath"
        }
    }
    availablePaths.filter { it != SCANNER_HASHES_PATH && !files.has(it) }.forEach {
        errors += "Package file missing hash: $it"
    }
}

private fun validateManifestFileReferences(
    manifest: ScanPackageManifest?,
    availablePaths: Set<String>,
    errors: MutableList<String>,
    warnings: MutableList<String>
) {
    if (manifest == null) return
    if (manifest.frameCount != manifest.frames.size) {
        errors += "Manifest frame_count does not match frames array"
    }
    val accepted = manifest.frames.count { it.quality.accepted }
    val forced = manifest.frames.count { it.forcedCapture }
    val rejected = manifest.frames.count { !it.quality.accepted }
    if (manifest.acceptedFrameCount != accepted) {
        errors += "Manifest accepted_frame_count does not match frame quality"
    }
    if (manifest.forcedFrameCount != forced) {
        errors += "Manifest forced_frame_count does not match frames"
    }
    if (manifest.rejectedFrameCount != rejected) {
        errors += "Manifest rejected_frame_count does not match frame quality"
    }
    val hasArCorePoses = manifest.frames.any { it.poseWorldFromCamera?.size == 16 }
    val hasDepth = manifest.frames.any { it.depth16Path != null }
    val hasMasks = manifest.frames.any { it.maskPath != null }
    if (manifest.hasArCorePoses != hasArCorePoses) {
        errors += "Manifest has_arcore_poses does not match frame pose metadata"
    }
    if (manifest.hasDepth != hasDepth) {
        errors += "Manifest has_depth does not match frame depth metadata"
    }
    if (manifest.hasMasks != hasMasks) {
        errors += "Manifest has_masks does not match frame mask metadata"
    }
    manifest.frames.forEach { frame ->
        validateReferencedPath(frame.rgbPath, "Frame ${frame.id} image", availablePaths, errors)
        validateOptionalReferencedPath(frame.maskPath, "Frame ${frame.id} mask", availablePaths, errors)
        validateOptionalReferencedPath(frame.depth16Path, "Frame ${frame.id} depth", availablePaths, errors)
        validateOptionalReferencedPath(
            frame.depthConfidencePath,
            "Frame ${frame.id} depth confidence",
            availablePaths,
            errors
        )
        if (frame.forcedCapture) {
            warnings += "Forced frame present: ${frame.id}"
        }
    }
}

private fun validateReferencedPath(
    path: String,
    label: String,
    availablePaths: Set<String>,
    errors: MutableList<String>
) {
    if (!isSafeScannerPackagePath(path)) {
        errors += "$label path is unsafe: $path"
    } else if (path !in availablePaths) {
        errors += "$label file is missing: $path"
    }
}

private fun validateOptionalReferencedPath(
    path: String?,
    label: String,
    availablePaths: Set<String>,
    errors: MutableList<String>
) {
    if (path != null) {
        validateReferencedPath(path, label, availablePaths, errors)
    }
}
