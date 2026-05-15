package com.mobileslicer.scanner

import java.io.File
import java.util.Base64
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

internal data class MarkerMatMarker(
    val id: Int,
    val xMm: Float,
    val yMm: Float,
    val sizeMm: Float
)

internal data class MarkerMatLayout(
    val markerSystem: ScannerMarkerSystem,
    val markerFamily: String,
    val pageName: String = "A4",
    val fileStem: String = "mobile_slicer_marker_mat_a4",
    val pageWidthMm: Float,
    val pageHeightMm: Float,
    val scaleBarMm: Float,
    val markers: List<MarkerMatMarker>
)

internal fun defaultMobileSlicerMarkerMatLayout(): MarkerMatLayout =
    MarkerMatLayout(
        markerSystem = ScannerMarkerSystem.AprilTag,
        markerFamily = "tag36h11",
        pageName = "A4",
        fileStem = "mobile_slicer_marker_mat_a4",
        pageWidthMm = 210f,
        pageHeightMm = 297f,
        scaleBarMm = 100f,
        markers = listOf(
            MarkerMatMarker(0, 20f, 20f, 32f),
            MarkerMatMarker(1, 79f, 20f, 32f),
            MarkerMatMarker(2, 138f, 20f, 32f),
            MarkerMatMarker(3, 20f, 232f, 32f),
            MarkerMatMarker(4, 79f, 232f, 32f),
            MarkerMatMarker(5, 138f, 232f, 32f),
            MarkerMatMarker(6, 20f, 126f, 32f),
            MarkerMatMarker(7, 138f, 126f, 32f)
        )
    )

internal fun usLetterMobileSlicerMarkerMatLayout(): MarkerMatLayout =
    MarkerMatLayout(
        markerSystem = ScannerMarkerSystem.AprilTag,
        markerFamily = "tag36h11",
        pageName = "US Letter",
        fileStem = "mobile_slicer_marker_mat_us_letter",
        pageWidthMm = 215.9f,
        pageHeightMm = 279.4f,
        scaleBarMm = 100f,
        markers = listOf(
            MarkerMatMarker(0, 20f, 20f, 32f),
            MarkerMatMarker(1, 91.95f, 20f, 32f),
            MarkerMatMarker(2, 163.9f, 20f, 32f),
            MarkerMatMarker(3, 20f, 214.4f, 32f),
            MarkerMatMarker(4, 91.95f, 214.4f, 32f),
            MarkerMatMarker(5, 163.9f, 214.4f, 32f),
            MarkerMatMarker(6, 20f, 123.7f, 32f),
            MarkerMatMarker(7, 163.9f, 123.7f, 32f)
        )
    )

internal fun standardMobileSlicerMarkerMatLayouts(): List<MarkerMatLayout> =
    listOf(
        defaultMobileSlicerMarkerMatLayout(),
        usLetterMobileSlicerMarkerMatLayout()
    )

internal fun writeMarkerMatAssets(
    directory: File,
    layout: MarkerMatLayout = defaultMobileSlicerMarkerMatLayout(),
    tagImageProvider: (Int) -> ByteArray? = { null }
) {
    require(directory.mkdirs() || directory.isDirectory) {
        "Unable to create marker mat output directory: ${directory.absolutePath}"
    }
    directory.resolve("mobile_slicer_marker_mat_layout.json").writeText(markerMatLayoutJson(layout).toString(2))
    directory.resolve("mobile_slicer_marker_mat_preview.svg").writeText(markerMatPreviewSvg(layout, tagImageProvider))
    directory.resolve("README.txt").writeText(
        """
        MobileSlicer marker mat assets

        This folder contains the metric layout for the scanner calibration mat and
        a preview SVG. When generated inside the Android app, the SVG embeds real
        AprilTag tag36h11 marker images at the IDs and positions in the JSON
        layout.

        Printable scale still requires detector observations plus marker
        pose/reprojection calibration. The app must not treat scale-bar entry
        alone as a slicer-ready proof.
        """.trimIndent()
    )
}

internal fun writeStandardMarkerMatAssets(
    directory: File,
    tagImageProvider: (Int) -> ByteArray? = { null }
): List<File> {
    require(directory.mkdirs() || directory.isDirectory) {
        "Unable to create marker mat output directory: ${directory.absolutePath}"
    }
    val written = mutableListOf<File>()
    standardMobileSlicerMarkerMatLayouts().forEach { layout ->
        val jsonFile = directory.resolve("${layout.fileStem}_layout.json")
        val svgFile = directory.resolve("${layout.fileStem}.svg")
        jsonFile.writeText(markerMatLayoutJson(layout).toString(2))
        svgFile.writeText(markerMatPreviewSvg(layout, tagImageProvider))
        written += jsonFile
        written += svgFile
    }
    val readme = directory.resolve("README.txt")
    readme.writeText(
        """
        MobileSlicer printable scanner marker mats

        Files:
        - mobile_slicer_marker_mat_a4.svg
        - mobile_slicer_marker_mat_a4_layout.json
        - mobile_slicer_marker_mat_us_letter.svg
        - mobile_slicer_marker_mat_us_letter_layout.json

        Choose the SVG that matches your printer paper. Print at 100% scale.
        Disable fit-to-page, shrink-to-fit, borderless scaling, and any automatic
        page scaling.

        After printing, measure the 100 mm scale bar on the page and enter that
        measured value in MobileSlicer. Keep the AprilTag markers visible around
        the object while scanning. Do not cover every marker with the object.

        Printable scale still requires detector observations plus marker
        pose/reprojection calibration. The app must not treat scale-bar entry
        alone as a slicer-ready proof.
        """.trimIndent()
    )
    written += readme
    return written
}

internal fun markerMatLayoutJson(layout: MarkerMatLayout): JSONObject =
    JSONObject()
        .put("schema_version", SCANNER_PACKAGE_SCHEMA_VERSION)
        .put("marker_system", layout.markerSystem.manifestValue)
        .put("marker_family", layout.markerFamily)
        .put("page_name", layout.pageName)
        .put("file_stem", layout.fileStem)
        .put("page_width_mm", layout.pageWidthMm.toDouble())
        .put("page_height_mm", layout.pageHeightMm.toDouble())
        .put("scale_bar_mm", layout.scaleBarMm.toDouble())
        .put(
            "markers",
            JSONArray().apply {
                layout.markers.forEach { marker ->
                    put(
                        JSONObject()
                            .put("id", marker.id)
                            .put("x_mm", marker.xMm.toDouble())
                            .put("y_mm", marker.yMm.toDouble())
                            .put("size_mm", marker.sizeMm.toDouble())
                    )
                }
            }
        )

internal fun markerMatPreviewSvg(
    layout: MarkerMatLayout,
    tagImageProvider: (Int) -> ByteArray? = { null }
): String = buildString {
    appendLine("""<svg xmlns="http://www.w3.org/2000/svg" width="${layout.pageWidthMm}mm" height="${layout.pageHeightMm}mm" viewBox="0 0 ${layout.pageWidthMm} ${layout.pageHeightMm}">""")
    appendLine("""  <rect x="0" y="0" width="${layout.pageWidthMm}" height="${layout.pageHeightMm}" fill="white"/>""")
    appendLine("""  <text x="12" y="12" font-family="sans-serif" font-size="5" font-weight="700" fill="black">MobileSlicer Printable Scanner Marker Mat - ${layout.pageName}</text>""")
    appendLine("""  <text x="12" y="19" font-family="sans-serif" font-size="3.6" fill="black">Print at 100% scale. Do not use fit-to-page. Keep tags visible around the object.</text>""")
    appendLine("""  <rect x="60" y="70" width="${layout.pageWidthMm - 120f}" height="${layout.pageHeightMm - 150f}" fill="none" stroke="#777777" stroke-width="0.6" stroke-dasharray="2 2"/>""")
    appendLine("""  <text x="${layout.pageWidthMm / 2f}" y="${layout.pageHeightMm / 2f}" text-anchor="middle" font-family="sans-serif" font-size="4.2" fill="#555555">Place object here</text>""")
    layout.markers.forEach { marker ->
        val imageBytes = tagImageProvider(marker.id)
        if (imageBytes != null) {
            val base64 = Base64.getEncoder().encodeToString(imageBytes)
            appendLine("""  <image x="${marker.xMm}" y="${marker.yMm}" width="${marker.sizeMm}" height="${marker.sizeMm}" href="data:image/png;base64,$base64" image-rendering="pixelated"/>""")
        } else {
            appendLine("""  <rect x="${marker.xMm}" y="${marker.yMm}" width="${marker.sizeMm}" height="${marker.sizeMm}" fill="none" stroke="black" stroke-width="0.8"/>""")
            appendLine("""  <text x="${marker.xMm + marker.sizeMm / 2f}" y="${marker.yMm + marker.sizeMm / 2f}" text-anchor="middle" dominant-baseline="middle" font-family="monospace" font-size="5" fill="black">tag ${marker.id}</text>""")
        }
    }
    val scaleY = layout.pageHeightMm - 26f
    appendLine("""  <line x1="55" y1="$scaleY" x2="${55 + layout.scaleBarMm}" y2="$scaleY" stroke="black" stroke-width="1.2"/>""")
    appendLine("""  <line x1="55" y1="${scaleY - 4}" x2="55" y2="${scaleY + 4}" stroke="black" stroke-width="1.2"/>""")
    appendLine("""  <line x1="${55 + layout.scaleBarMm}" y1="${scaleY - 4}" x2="${55 + layout.scaleBarMm}" y2="${scaleY + 4}" stroke="black" stroke-width="1.2"/>""")
    appendLine("""  <text x="${55 + layout.scaleBarMm / 2f}" y="${scaleY + 8}" text-anchor="middle" font-family="sans-serif" font-size="4" fill="black">${String.format(Locale.US, "%.0f", layout.scaleBarMm)} mm scale bar</text>""")
    appendLine("""  <text x="${55 + layout.scaleBarMm / 2f}" y="${scaleY + 14}" text-anchor="middle" font-family="sans-serif" font-size="3.3" fill="black">Measure this after printing and enter the value in MobileSlicer.</text>""")
    appendLine("</svg>")
}
