package com.mobileslicer.workspace

import kotlin.math.max

internal enum class OrcaThumbnailCameraMode {
    AngledIso,
    TopPlate
}

internal data class OrcaThumbnailCameraContract(
    val mode: OrcaThumbnailCameraMode,
    val source: String,
    val pitchDegrees: Float? = null,
    val yawDegrees: Float? = null,
    val cameraDistanceFactor: Float? = null,
    val zoomToBoxMarginFactor: Float? = null,
    val boxHorizontalMarginFactor: Float? = null,
    val boxVerticalMarginFactor: Float? = null,
    val topPlateMargin: Float? = null,
    val picking: Boolean = false,
    val banLight: Boolean = false
)

internal data class OrcaThumbnailRenderSize(
    val outputWidth: Int,
    val outputHeight: Int,
    val renderWidth: Int,
    val renderHeight: Int,
    val supersampleFactor: Int
) {
    val supersampled: Boolean get() = supersampleFactor > 1
}

internal object OrcaThumbnailRenderPolicy {
    const val RendererName = "offscreen_egl"

    const val AngledPitchDegrees = 45f
    const val AngledYawDegrees = -45f
    const val AngledCameraDistanceFactor = 4f
    const val AngledZoomToBoxMarginFactor = 1.025f
    const val AngledBroadFootprintZoomToBoxMarginFactor = 1.38f
    const val AngledBroadFootprintMinSizeMm = 40f
    const val AngledBroadFootprintMinHeightRatio = 0.4f
    const val AngledBroadFootprintMaxHeightRatio = 0.8f
    const val AngledBoxHorizontalMarginFactor = 0.01f
    const val AngledBoxVerticalMarginFactor = 0.02f
    const val TopPlateMargin = 1.02f
    const val SmallThumbnailSupersampleFactor = 2
    const val PackageSupersampleMaxOutputDimension = 128
    const val GcodeSupersampleMaxOutputDimension = 300
    const val SupersampleMaxRenderDimension = 600

    fun cameraContract(role: ThumbnailRenderRole): OrcaThumbnailCameraContract =
        when (role) {
            ThumbnailRenderRole.Top -> topPlateContract(picking = false, banLight = false)
            ThumbnailRenderRole.Pick -> topPlateContract(picking = true, banLight = true)
            ThumbnailRenderRole.NoLight -> angledIsoContract(banLight = true)
            ThumbnailRenderRole.Gcode,
            ThumbnailRenderRole.Plate -> angledIsoContract(banLight = false)
        }

    fun renderSize(outputWidth: Int, outputHeight: Int, role: ThumbnailRenderRole): OrcaThumbnailRenderSize {
        val width = outputWidth.coerceIn(16, 1024)
        val height = outputHeight.coerceIn(16, 1024)
        val maxOutputDimension = max(width, height)
        val supersampleLimit = if (role == ThumbnailRenderRole.Gcode) {
            GcodeSupersampleMaxOutputDimension
        } else {
            PackageSupersampleMaxOutputDimension
        }
        val factor = if (
            maxOutputDimension <= supersampleLimit &&
            maxOutputDimension * SmallThumbnailSupersampleFactor <= SupersampleMaxRenderDimension
        ) {
            SmallThumbnailSupersampleFactor
        } else {
            1
        }
        return OrcaThumbnailRenderSize(
            outputWidth = width,
            outputHeight = height,
            renderWidth = width * factor,
            renderHeight = height * factor,
            supersampleFactor = factor
        )
    }

    private fun angledIsoContract(banLight: Boolean): OrcaThumbnailCameraContract =
        OrcaThumbnailCameraContract(
            mode = OrcaThumbnailCameraMode.AngledIso,
            source = "vendor/orcaslicer/src/slic3r/GUI/GLCanvas3D.cpp: render_thumbnail_internal uses Camera::ViewAngleType::Iso, expanded volumes_box, zoom_to_box, for_picking, and ban_light",
            pitchDegrees = AngledPitchDegrees,
            yawDegrees = AngledYawDegrees,
            cameraDistanceFactor = AngledCameraDistanceFactor,
            zoomToBoxMarginFactor = AngledZoomToBoxMarginFactor,
            boxHorizontalMarginFactor = AngledBoxHorizontalMarginFactor,
            boxVerticalMarginFactor = AngledBoxVerticalMarginFactor,
            picking = false,
            banLight = banLight
        )

    private fun topPlateContract(picking: Boolean, banLight: Boolean): OrcaThumbnailCameraContract =
        OrcaThumbnailCameraContract(
            mode = OrcaThumbnailCameraMode.TopPlate,
            source = "vendor/orcaslicer/src/slic3r/GUI/GLCanvas3D.cpp: render_thumbnail_internal handles Camera::ViewAngleType::Top_Plate with whole-plate orthographic zoom",
            topPlateMargin = TopPlateMargin,
            picking = picking,
            banLight = banLight
        )
}
