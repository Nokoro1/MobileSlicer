package com.mobileslicer.workspace

import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal object GcodeSummaryParser {
    private val numberRegex = Regex("""[-+]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][-+]?\d+)?""")

    fun fromNativeSummary(summaryText: String?): SliceResultSummary? {
        if (summaryText.isNullOrBlank()) {
            return null
        }
        val fields = summaryText
            .split('|')
            .mapNotNull { part ->
                val separator = part.indexOf('=')
                if (separator <= 0) {
                    null
                } else {
                    part.substring(0, separator) to part.substring(separator + 1)
                }
            }
            .toMap()
        val byteCount = fields["bytes"]?.toIntOrNull() ?: return null
        val lineCount = fields["lines"]?.toIntOrNull() ?: return null
        val layerChangeCount = fields["layers"]?.toIntOrNull() ?: return null
        return SliceResultSummary(
            byteCount = byteCount,
            lineCount = lineCount,
            layerChangeCount = layerChangeCount,
            observedTypes = fields["types"].orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.take(8),
            wallShellTypes = fields["walls"].orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.take(6),
            estimatedPrintTimeText = fields["time"]?.let(::normalizeEstimatedPrintTimeText)?.takeIf { it.isNotBlank() },
            filamentUsedGrams = fields["grams"]?.toDoubleOrNull()?.takeIf { it >= 0.005 },
            previewInfo = parsePreviewInfo(fields)
        )
    }

    fun fromGcode(gcode: String): SliceResultSummary {
        val defaultFilamentDensityGPerCm3 = 1.24
        var lineCount = 0
        var layerChangeCount = 0
        val observedTypes = linkedSetOf<String>()
        val wallShellTypes = linkedSetOf<String>()
        var estimatedPrintTimeText: String? = null
        var totalFilamentUsedGrams: Double? = null
        var perToolFilamentUsedGrams: Double? = null
        var wipeTowerFilamentUsedGrams: Double? = null
        var filamentUsedMm: Double? = null
        var wipeTowerFilamentUsedMm: Double? = null
        var filamentUsedCm3: Double? = null
        var wipeTowerFilamentUsedCm3: Double? = null
        var filamentDiameterMm = 1.75
        var filamentDensityGPerCm3 = defaultFilamentDensityGPerCm3
        val moveWords = GcodeMoveWords()
        var x = 0.0
        var y = 0.0
        var z = 0.0
        var e = 0.0
        var a = 0.0
        var feedrateMmPerMin = 0.0
        var absoluteXyz = true
        var absoluteE = true
        var positiveExtrusionMm = 0.0
        var estimatedSecondsFromMoves = 0.0
        var accelerationMmPerS2 = 10000.0
        var firstExtrusionZ: Double? = null
        val firstLayerExtrusionBounds = GcodeBoundsAccumulator()
        val nozzleTemperaturesC = linkedSetOf<Int>()
        val bedTemperaturesC = linkedSetOf<Int>()
        val fanSpeeds = linkedSetOf<Int>()
        val extrusionFeedratesMmPerMin = linkedSetOf<Double>()
        val accelerationsMmPerSec2 = linkedSetOf<Double>()

        gcode.lineSequence().forEach { line ->
            lineCount += 1
            if (!line.startsWith(';')) {
                if (estimatedPrintTimeText != null && totalFilamentUsedGrams != null) {
                    return@forEach
                }
                val command = line.substringBefore(';').trimStart()
                if (command.length < 2) {
                    return@forEach
                }
                when {
                    commandMatches(command, "G90") -> absoluteXyz = true
                    commandMatches(command, "G91") -> absoluteXyz = false
                    commandMatches(command, "M82") -> absoluteE = true
                    commandMatches(command, "M83") -> absoluteE = false
                    commandMatches(command, "M204") -> {
                        val acceleration =
                            parseWordValue(command, 'S')
                                ?: parseWordValue(command, 'P')
                                ?: parseWordValue(command, 'T')
                        if (acceleration != null && acceleration > 0.0) {
                            accelerationMmPerS2 = acceleration
                            accelerationsMmPerSec2.addCapped(acceleration)
                        }
                    }
                    commandMatches(command, "M104") || commandMatches(command, "M109") -> {
                        parseWordValue(command, 'S')?.roundGcodeScalar()?.let { nozzleTemperaturesC.addCapped(it) }
                    }
                    commandMatches(command, "M140") || commandMatches(command, "M190") -> {
                        parseWordValue(command, 'S')?.roundGcodeScalar()?.let { bedTemperaturesC.addCapped(it) }
                    }
                    commandMatches(command, "M106") -> {
                        parseWordValue(command, 'S')?.roundGcodeScalar()?.let { fanSpeeds.addCapped(it) }
                    }
                    commandMatches(command, "G92") -> {
                        if (parseMoveWords(command, moveWords)) {
                            if (moveWords.hasX) x = moveWords.x
                            if (moveWords.hasY) y = moveWords.y
                            if (moveWords.hasZ) z = moveWords.z
                            if (moveWords.hasE) e = moveWords.e
                            if (moveWords.hasA) a = moveWords.a
                        }
                    }
                    commandMatches(command, "G0") || commandMatches(command, "G1") ||
                        commandMatches(command, "G2") || commandMatches(command, "G3") -> {
                        if (parseMoveWords(command, moveWords)) {
                            val isArc = commandMatches(command, "G2") || commandMatches(command, "G3")
                            val previousX = x
                            val previousY = y
                            val previousZ = z
                            val previousE = e
                            val previousA = a
                            if (moveWords.hasX) x = if (absoluteXyz) moveWords.x else x + moveWords.x
                            if (moveWords.hasY) y = if (absoluteXyz) moveWords.y else y + moveWords.y
                            if (moveWords.hasZ) z = if (absoluteXyz) moveWords.z else z + moveWords.z
                            if (moveWords.hasE) e = if (absoluteE) moveWords.e else e + moveWords.e
                            if (moveWords.hasA) a = if (absoluteE) moveWords.a else a + moveWords.a
                            if (moveWords.hasF) feedrateMmPerMin = moveWords.f
                            val de = when {
                                moveWords.hasE -> e - previousE
                                moveWords.hasA -> a - previousA
                                else -> 0.0
                            }
                            if (de > 0.0) {
                                positiveExtrusionMm += de
                                if (feedrateMmPerMin > 0.0) extrusionFeedratesMmPerMin.addCapped(feedrateMmPerMin)
                                val extrusionZ = z
                                val firstZ = firstExtrusionZ ?: extrusionZ.also { firstExtrusionZ = it }
                                if (kotlin.math.abs(extrusionZ - firstZ) <= 0.001) {
                                    firstLayerExtrusionBounds.include(previousX, previousY)
                                    firstLayerExtrusionBounds.include(x, y)
                                }
                            }
                            if (estimatedPrintTimeText == null) {
                                val dx = x - previousX
                                val dy = y - previousY
                                val dz = z - previousZ
                                val distanceMm = if (isArc) {
                                    estimateArcDistanceMm(
                                        clockwise = commandMatches(command, "G2"),
                                        words = moveWords,
                                        previousX = previousX,
                                        previousY = previousY,
                                        previousZ = previousZ,
                                        x = x,
                                        y = y,
                                        z = z
                                    )
                                } else {
                                    sqrt(dx * dx + dy * dy + dz * dz)
                                }
                                val timedDistanceMm = if (distanceMm > 0.0) distanceMm else kotlin.math.abs(de)
                                if (feedrateMmPerMin > 0.0 && timedDistanceMm > 0.0) {
                                    estimatedSecondsFromMoves += if (isArc) {
                                        timedDistanceMm / (feedrateMmPerMin / 60.0)
                                    } else {
                                        estimateTrapezoidMoveSeconds(
                                            distanceMm = timedDistanceMm,
                                            feedrateMmPerMin = feedrateMmPerMin,
                                            accelerationMmPerS2 = accelerationMmPerS2
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                return@forEach
            }

            when {
                line.startsWith(";LAYER_CHANGE") || line.startsWith("; CHANGE_LAYER") -> layerChangeCount += 1
                line.startsWith(";TYPE:") -> {
                    val label = line.removePrefix(";TYPE:").trim()
                    if (label.isNotEmpty()) {
                        observedTypes += label
                        val lowercase = label.lowercase(Locale.US)
                        if ("wall" in lowercase || "surface" in lowercase || "shell" in lowercase) {
                            wallShellTypes += label
                        }
                    }
                }
            }

            val needsTime = estimatedPrintTimeText == null
            val needsFilament =
                totalFilamentUsedGrams == null ||
                    perToolFilamentUsedGrams == null ||
                    filamentUsedMm == null ||
                    filamentUsedCm3 == null
            val needsFilamentConversion = totalFilamentUsedGrams == null && perToolFilamentUsedGrams == null
            if (!needsTime && !needsFilament && !needsFilamentConversion) {
                return@forEach
            }

            val trimmedLine = line.trim()
            val lowerLine = trimmedLine.lowercase(Locale.US)
            if (needsTime) {
                estimatedPrintTimeText = parseEstimatedPrintTime(trimmedLine)
            }
            if (needsFilament) {
                parseFilamentGrams(trimmedLine)?.let { parsedGrams ->
                    if ("total filament used" in lowerLine) {
                        totalFilamentUsedGrams = parsedGrams
                    } else if ("for wipe tower" in lowerLine) {
                        wipeTowerFilamentUsedGrams = parsedGrams
                    } else if (perToolFilamentUsedGrams == null) {
                        perToolFilamentUsedGrams = parsedGrams
                    }
                }
                parseFilamentUsage(trimmedLine, "mm")?.let { parsedMm ->
                    if ("for wipe tower" in lowerLine) {
                        if (wipeTowerFilamentUsedMm == null) wipeTowerFilamentUsedMm = parsedMm
                    } else if (filamentUsedMm == null) {
                        filamentUsedMm = parsedMm
                    }
                }
                parseFilamentUsage(trimmedLine, "cm3")?.let { parsedCm3 ->
                    if ("for wipe tower" in lowerLine) {
                        if (wipeTowerFilamentUsedCm3 == null) wipeTowerFilamentUsedCm3 = parsedCm3
                    } else if (filamentUsedCm3 == null) {
                        filamentUsedCm3 = parsedCm3
                    }
                }
            }
            if (needsFilamentConversion && lowerLine.startsWith("; filament_diameter:")) {
                parseFirstNumber(trimmedLine)?.let { filamentDiameterMm = it }
            }
            if (needsFilamentConversion && lowerLine.startsWith("; filament_density:")) {
                parseFirstNumber(trimmedLine)?.takeIf { it > 0.0 }?.let { filamentDensityGPerCm3 = it }
            }
        }

        val effectiveFilamentDensityGPerCm3 =
            filamentDensityGPerCm3.takeIf { it > 0.0 } ?: defaultFilamentDensityGPerCm3
        if (estimatedPrintTimeText == null && estimatedSecondsFromMoves > 0.0) {
            estimatedPrintTimeText = formatDurationSeconds(estimatedSecondsFromMoves.toLong())
        }
        var filamentUsedGrams: Double? = null
        if (filamentUsedGrams == null && totalFilamentUsedGrams != null) {
            filamentUsedGrams = totalFilamentUsedGrams
        }
        if (filamentUsedGrams == null && perToolFilamentUsedGrams != null) {
            filamentUsedGrams = perToolFilamentUsedGrams + (wipeTowerFilamentUsedGrams ?: 0.0)
        }
        filamentUsedCm3?.let { volumeCm3 ->
            if (filamentUsedGrams == null) {
                filamentUsedGrams = (volumeCm3 + (wipeTowerFilamentUsedCm3 ?: 0.0)) * effectiveFilamentDensityGPerCm3
            }
        }
        filamentUsedMm?.let { lengthMm ->
            if (filamentUsedGrams == null) {
                val filamentRadiusMm = filamentDiameterMm / 2.0
                val filamentVolumeMm3 = (lengthMm + (wipeTowerFilamentUsedMm ?: 0.0)) * PI * filamentRadiusMm * filamentRadiusMm
                filamentUsedGrams = filamentVolumeMm3 * effectiveFilamentDensityGPerCm3 / 1000.0
            }
        }
        if (filamentUsedGrams == null && positiveExtrusionMm > 0.0) {
            val filamentRadiusMm = filamentDiameterMm / 2.0
            val filamentVolumeMm3 = positiveExtrusionMm * PI * filamentRadiusMm * filamentRadiusMm
            filamentUsedGrams = filamentVolumeMm3 * effectiveFilamentDensityGPerCm3 / 1000.0
        }
        if ((filamentUsedGrams ?: Double.MAX_VALUE) < 0.005) {
            filamentUsedGrams = null
        }

        return SliceResultSummary(
            byteCount = gcode.length,
            lineCount = lineCount,
            layerChangeCount = layerChangeCount,
            observedTypes = observedTypes.take(8),
            wallShellTypes = wallShellTypes.take(6),
            estimatedPrintTimeText = estimatedPrintTimeText,
            filamentUsedGrams = filamentUsedGrams,
            regressionMetrics = GcodeRegressionMetrics(
                firstLayerExtrusionBounds = firstLayerExtrusionBounds.toBoundsOrNull(),
                nozzleTemperaturesC = nozzleTemperaturesC.toList(),
                bedTemperaturesC = bedTemperaturesC.toList(),
                fanSpeeds = fanSpeeds.toList(),
                extrusionFeedratesMmPerMin = extrusionFeedratesMmPerMin.toList(),
                accelerationsMmPerSec2 = accelerationsMmPerSec2.toList()
            )
        )
    }

    private fun parsePreviewInfo(fields: Map<String, String>): PreviewInfoSummary {
        val totals = parsePreviewTotals(fields["previewTotals"].orEmpty())
        return PreviewInfoSummary(
            lineTypes = parsePreviewLineTypes(fields["previewLineTypes"].orEmpty()),
            filaments = parsePreviewFilaments(fields["previewFilaments"].orEmpty()),
            totalSeconds = totals["totalSeconds"]?.toDoubleOrNull(),
            prepareSeconds = totals["prepareSeconds"]?.toDoubleOrNull(),
            modelSeconds = totals["modelSeconds"]?.toDoubleOrNull(),
            totalCost = totals["cost"]?.toDoubleOrNull()?.takeIf { it > 0.0 },
            filamentChanges = totals["filamentChanges"]?.toIntOrNull() ?: 0,
            extruderChanges = totals["extruderChanges"]?.toIntOrNull() ?: 0
        )
    }

    private fun parsePreviewLineTypes(rawValue: String): List<PreviewLineTypeRow> =
        rawValue
            .split(';')
            .mapNotNull { row ->
                val parts = row.split(',')
                if (parts.size < 8) return@mapNotNull null
                val kind = when (parts[0]) {
                    "role" -> PreviewPathKind.Role
                    "option" -> PreviewPathKind.Option
                    else -> return@mapNotNull null
                }
                PreviewLineTypeRow(
                    kind = kind,
                    nativeId = parts[1].toIntOrNull() ?: return@mapNotNull null,
                    label = unescapeSummaryValue(parts[2]),
                    colorHex = unescapeSummaryValue(parts[3]).takeIf { it.startsWith("#") } ?: "#8FC1FF",
                    timeSeconds = parts[4].toDoubleOrNull() ?: 0.0,
                    percent = parts[5].toDoubleOrNull() ?: 0.0,
                    usageMeters = parts[6].toDoubleOrNull() ?: 0.0,
                    usageGrams = parts[7].toDoubleOrNull() ?: 0.0,
                    defaultVisible = parts.getOrNull(8)?.toIntOrNull()?.let { it != 0 } ?: (kind == PreviewPathKind.Role)
                )
            }

    private fun parsePreviewFilaments(rawValue: String): List<PreviewFilamentUsageRow> =
        rawValue
            .split(';')
            .mapNotNull { row ->
                val parts = row.split(',')
                if (parts.size < 14) return@mapNotNull null
                PreviewFilamentUsageRow(
                    slotIndex = parts[0].toIntOrNull() ?: return@mapNotNull null,
                    label = unescapeSummaryValue(parts[1]).ifBlank { "Filament ${parts[0]}" },
                    colorHex = unescapeSummaryValue(parts[2]).takeIf { it.startsWith("#") } ?: "#8FC1FF",
                    modelMeters = parts[3].toDoubleOrNull() ?: 0.0,
                    modelGrams = parts[4].toDoubleOrNull() ?: 0.0,
                    supportMeters = parts[5].toDoubleOrNull() ?: 0.0,
                    supportGrams = parts[6].toDoubleOrNull() ?: 0.0,
                    flushedMeters = parts[7].toDoubleOrNull() ?: 0.0,
                    flushedGrams = parts[8].toDoubleOrNull() ?: 0.0,
                    towerMeters = parts[9].toDoubleOrNull() ?: 0.0,
                    towerGrams = parts[10].toDoubleOrNull() ?: 0.0,
                    totalMeters = parts[11].toDoubleOrNull() ?: 0.0,
                    totalGrams = parts[12].toDoubleOrNull() ?: 0.0,
                    cost = parts[13].toDoubleOrNull() ?: 0.0
                )
            }

    private fun parsePreviewTotals(rawValue: String): Map<String, String> =
        rawValue
            .split(',')
            .mapNotNull { field ->
                val separator = field.indexOf('=')
                if (separator <= 0) null else field.substring(0, separator) to field.substring(separator + 1)
            }
            .toMap()

    private fun unescapeSummaryValue(value: String): String {
        val result = StringBuilder(value.length)
        val bytes = ArrayList<Byte>()
        fun flushBytes() {
            if (bytes.isNotEmpty()) {
                result.append(bytes.toByteArray().toString(Charsets.UTF_8))
                bytes.clear()
            }
        }
        var index = 0
        while (index < value.length) {
            val ch = value[index]
            if (ch == '%' && index + 2 < value.length) {
                val decoded = value.substring(index + 1, index + 3).toIntOrNull(16)
                if (decoded != null) {
                    bytes += decoded.toByte()
                    index += 3
                    continue
                }
            }
            flushBytes()
            result.append(ch)
            index += 1
        }
        flushBytes()
        return result.toString()
    }

    private fun parseFilamentGrams(line: String): Double? {
        val lower = line.lowercase(Locale.US)
        if ("filament" !in lower || "[g]" !in lower) return null
        val rawValue = line.substringAfter('=', missingDelimiterValue = "")
            .ifBlank { line.substringAfter(':', missingDelimiterValue = "") }
            .trim()
        val values = numberRegex
            .findAll(rawValue)
            .mapNotNull { it.value.toDoubleOrNull() }
            .toList()
        val total = values.sum()
        return total.takeIf { it >= 0.005 }
    }

    private fun parseFilamentUsage(line: String, unit: String): Double? {
        val lower = line.lowercase(Locale.US)
        if ("filament" !in lower || "[$unit]" !in lower) return null
        val rawValue = line.substringAfter('=', missingDelimiterValue = "")
            .ifBlank { line.substringAfter(':', missingDelimiterValue = "") }
            .trim()
        val total = numberRegex
            .findAll(rawValue)
            .mapNotNull { it.value.toDoubleOrNull() }
            .sum()
        return total.takeIf { it > 0.0 }
    }

    private fun parseEstimatedPrintTime(line: String): String? {
        val trimmed = line.trim()
        val lower = trimmed.lowercase(Locale.US)
        if (lower.startsWith(";time:")) {
            val seconds = trimmed.substringAfter(':').trim().toLongOrNull()
            return seconds?.let(::formatDurationSeconds)
        }
        parseLabeledTimeSegment(trimmed, "total estimated time")?.let { return it }
        parseLabeledTimeSegment(trimmed, "estimated printing time")?.let { return it }
        if ("estimated" !in lower || "time" !in lower) return null
        val value = trimmed.substringAfter('=', missingDelimiterValue = "")
            .ifBlank { trimmed.substringAfter(':', missingDelimiterValue = "") }
            .trim()
            .removeSuffix(";")
            .trim()
        return value.ifBlank { null }
    }

    private fun normalizeEstimatedPrintTimeText(value: String): String =
        parseLabeledTimeSegment(value, "total estimated time")
            ?: parseLabeledTimeSegment(value, "estimated printing time")
            ?: value.substringBefore(';').trim()

    private fun parseLabeledTimeSegment(line: String, label: String): String? {
        val lower = line.lowercase(Locale.US)
        val start = lower.indexOf(label)
        if (start < 0) return null
        val valueStart = line.indexOf(':', start).takeIf { it >= 0 } ?: line.indexOf('=', start).takeIf { it >= 0 } ?: return null
        return line
            .substring(valueStart + 1)
            .substringBefore(';')
            .trim()
            .takeIf { it.isNotEmpty() }
    }

    private fun parseFirstNumber(line: String): Double? =
        numberRegex.find(line)?.value?.toDoubleOrNull()

    private fun parseMoveWords(command: String, words: GcodeMoveWords): Boolean {
        words.clear()
        var found = false
        var index = 0
        val limit = command.length
        while (index < limit) {
            when (command[index].uppercaseChar()) {
                'X', 'Y', 'Z', 'E', 'A', 'F', 'I', 'J' -> {
                    val axis = command[index].uppercaseChar()
                    index += 1
                    while (index < limit && command[index].isWhitespace()) index += 1
                    val start = index
                    if (index < limit && (command[index] == '+' || command[index] == '-')) index += 1
                    var hasDigit = false
                    while (index < limit && command[index].isDigit()) {
                        hasDigit = true
                        index += 1
                    }
                    if (index < limit && command[index] == '.') {
                        index += 1
                        while (index < limit && command[index].isDigit()) {
                            hasDigit = true
                            index += 1
                        }
                    }
                    if (hasDigit && index < limit && (command[index] == 'e' || command[index] == 'E')) {
                        val exponentStart = index
                        index += 1
                        if (index < limit && (command[index] == '+' || command[index] == '-')) index += 1
                        var hasExponentDigit = false
                        while (index < limit && command[index].isDigit()) {
                            hasExponentDigit = true
                            index += 1
                        }
                        if (!hasExponentDigit) {
                            index = exponentStart
                        }
                    }
                    if (hasDigit) {
                        command.substring(start, index).toDoubleOrNull()?.let { value ->
                            found = true
                            when (axis) {
                                'X' -> { words.hasX = true; words.x = value }
                                'Y' -> { words.hasY = true; words.y = value }
                                'Z' -> { words.hasZ = true; words.z = value }
                                'E' -> { words.hasE = true; words.e = value }
                                'A' -> { words.hasA = true; words.a = value }
                                'F' -> { words.hasF = true; words.f = value }
                                'I' -> { words.hasI = true; words.i = value }
                                'J' -> { words.hasJ = true; words.j = value }
                            }
                        }
                    }
                }
                ';' -> return found
                else -> index += 1
            }
        }
        return found
    }

    private fun commandMatches(command: String, expected: String): Boolean {
        val trimmed = command.trimStart()
        if (!trimmed.startsWith(expected, ignoreCase = true)) return false
        return trimmed.length == expected.length || trimmed[expected.length].isWhitespace()
    }

    private fun parseWordValue(command: String, target: Char): Double? {
        val normalizedTarget = target.uppercaseChar()
        var index = 0
        while (index < command.length) {
            if (command[index].uppercaseChar() != normalizedTarget) {
                index += 1
                continue
            }
            index += 1
            while (index < command.length && command[index].isWhitespace()) index += 1
            val start = index
            if (index < command.length && (command[index] == '+' || command[index] == '-')) index += 1
            var hasDigit = false
            while (index < command.length && command[index].isDigit()) {
                hasDigit = true
                index += 1
            }
            if (index < command.length && command[index] == '.') {
                index += 1
                while (index < command.length && command[index].isDigit()) {
                    hasDigit = true
                    index += 1
                }
            }
            if (hasDigit) {
                return command.substring(start, index).toDoubleOrNull()
            }
        }
        return null
    }

    private fun Double.roundGcodeScalar(): Int =
        roundToInt()

    private fun <T> LinkedHashSet<T>.addCapped(value: T, maxSize: Int = 32) {
        if (size < maxSize || contains(value)) {
            add(value)
        }
    }

    private fun estimateTrapezoidMoveSeconds(
        distanceMm: Double,
        feedrateMmPerMin: Double,
        accelerationMmPerS2: Double
    ): Double {
        if (distanceMm <= 0.0 || feedrateMmPerMin <= 0.0) return 0.0
        val speedMmPerS = feedrateMmPerMin / 60.0
        val acceleration = max(1.0, accelerationMmPerS2)
        val accelerateAndDecelerateDistance = speedMmPerS * speedMmPerS / acceleration
        return if (distanceMm >= accelerateAndDecelerateDistance) {
            distanceMm / speedMmPerS + speedMmPerS / acceleration
        } else {
            2.0 * sqrt(distanceMm / acceleration)
        }
    }

    private fun estimateArcDistanceMm(
        clockwise: Boolean,
        words: GcodeMoveWords,
        previousX: Double,
        previousY: Double,
        previousZ: Double,
        x: Double,
        y: Double,
        z: Double
    ): Double {
        if (!words.hasI && !words.hasJ) {
            val dx = x - previousX
            val dy = y - previousY
            val dz = z - previousZ
            return sqrt(dx * dx + dy * dy + dz * dz)
        }
        val centerX = previousX + if (words.hasI) words.i else 0.0
        val centerY = previousY + if (words.hasJ) words.j else 0.0
        val radius = sqrt((previousX - centerX) * (previousX - centerX) + (previousY - centerY) * (previousY - centerY))
        if (radius <= 0.0) return 0.0
        val startAngle = atan2(previousY - centerY, previousX - centerX)
        val endAngle = atan2(y - centerY, x - centerX)
        var sweep = if (clockwise) startAngle - endAngle else endAngle - startAngle
        while (sweep <= 0.0) {
            sweep += 2.0 * PI
        }
        val planarDistance = radius * sweep
        val dz = z - previousZ
        return sqrt(planarDistance * planarDistance + dz * dz)
    }

    private fun formatDurationSeconds(totalSeconds: Long): String {
        val safeSeconds = totalSeconds.coerceAtLeast(0)
        val hours = safeSeconds / 3600
        val minutes = (safeSeconds % 3600) / 60
        val seconds = safeSeconds % 60
        return when {
            hours > 0 -> String.format(Locale.US, "%dh %02dm", hours, minutes)
            minutes > 0 -> String.format(Locale.US, "%dm %02ds", minutes, seconds)
            else -> String.format(Locale.US, "%ds", seconds)
        }
}

private class GcodeBoundsAccumulator {
    private var minX = Double.POSITIVE_INFINITY
    private var maxX = Double.NEGATIVE_INFINITY
    private var minY = Double.POSITIVE_INFINITY
    private var maxY = Double.NEGATIVE_INFINITY

    fun include(x: Double, y: Double) {
        if (!x.isFinite() || !y.isFinite()) return
        if (x < minX) minX = x
        if (x > maxX) maxX = x
        if (y < minY) minY = y
        if (y > maxY) maxY = y
    }

    fun toBoundsOrNull(): GcodeMotionBounds? =
        if (minX.isFinite() && maxX.isFinite() && minY.isFinite() && maxY.isFinite()) {
            GcodeMotionBounds(minX = minX, maxX = maxX, minY = minY, maxY = maxY)
        } else {
            null
        }
}

private data class GcodeMoveWords(
        var hasX: Boolean = false,
        var x: Double = 0.0,
        var hasY: Boolean = false,
        var y: Double = 0.0,
        var hasZ: Boolean = false,
        var z: Double = 0.0,
        var hasE: Boolean = false,
        var e: Double = 0.0,
        var hasA: Boolean = false,
        var a: Double = 0.0,
        var hasF: Boolean = false,
        var f: Double = 0.0,
        var hasI: Boolean = false,
        var i: Double = 0.0,
        var hasJ: Boolean = false,
        var j: Double = 0.0
    ) {
        fun clear() {
            hasX = false
            hasY = false
            hasZ = false
            hasE = false
            hasA = false
            hasF = false
            hasI = false
            hasJ = false
        }
    }
}
