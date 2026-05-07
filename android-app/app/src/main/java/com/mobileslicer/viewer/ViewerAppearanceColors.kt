package com.mobileslicer.viewer

import android.graphics.Color
import kotlin.math.min

internal data class ViewerColorOverrides(
    val clearColor: FloatArray? = null,
    val plateColor: FloatArray? = null,
    val bedGridColor: FloatArray? = null,
    val bedBorderColor: FloatArray? = null,
    val bedWallColor: FloatArray? = null,
    val modelColor: FloatArray? = null,
    val selectedFootprintColor: FloatArray? = null
)

internal data class ViewerColors(
    val clearColor: FloatArray,
    val plateColor: FloatArray,
    val bedGridColor: FloatArray,
    val bedBorderColor: FloatArray,
    val bedWallColor: FloatArray,
    val modelColor: FloatArray,
    val selectedFootprintColor: FloatArray
) {
    fun dimmedModelColor(): FloatArray =
        floatArrayOf(modelColor[0] * 0.82f, modelColor[1] * 0.82f, modelColor[2] * 0.82f, 1f)

    fun paintBaseModelColor(): FloatArray =
        floatArrayOf(0.84f, 0.86f, 0.88f, 1f)

    fun plateFrontFaceColor(): FloatArray =
        floatArrayOf(plateColor[0], plateColor[1], plateColor[2], if (plateColor.size > 3) plateColor[3] else 1f)

    fun plateBackFaceColor(): FloatArray =
        floatArrayOf(plateColor[0], plateColor[1], plateColor[2], 0.10f)
}

internal fun buildViewerColors(
    appearance: ViewerAppearance,
    overrides: ViewerColorOverrides = ViewerColorOverrides()
): ViewerColors {
    val accentR = Color.red(appearance.accentColor) / 255f
    val accentG = Color.green(appearance.accentColor) / 255f
    val accentB = Color.blue(appearance.accentColor) / 255f
    val worldColor = appearance.worldColor
    return ViewerColors(
        clearColor = overrides.clearColor ?: if (worldColor != null) {
            floatArrayOf(
                Color.red(worldColor) / 255f,
                Color.green(worldColor) / 255f,
                Color.blue(worldColor) / 255f
            )
        } else if (appearance.darkTheme) {
            floatArrayOf(0.095f, 0.115f, 0.140f)
        } else {
            floatArrayOf(0.948f, 0.962f, 0.982f)
        },
        plateColor = overrides.plateColor ?: if (appearance.darkTheme) {
            floatArrayOf(0.175f, 0.198f, 0.228f, 0.82f)
        } else {
            floatArrayOf(0.80f, 0.835f, 0.88f, 0.84f)
        },
        bedGridColor = overrides.bedGridColor ?: if (appearance.darkTheme) {
            floatArrayOf(0.230f, 0.258f, 0.292f, 1f)
        } else {
            floatArrayOf(0.84f, 0.875f, 0.925f, 1f)
        },
        bedBorderColor = overrides.bedBorderColor ?: if (appearance.darkTheme) {
            floatArrayOf(0.250f, 0.280f, 0.315f, 1f)
        } else {
            floatArrayOf(0.82f, 0.865f, 0.920f, 1f)
        },
        bedWallColor = overrides.bedWallColor ?: if (appearance.darkTheme) {
            floatArrayOf(0.105f, 0.122f, 0.145f, 1f)
        } else {
            floatArrayOf(0.68f, 0.715f, 0.76f, 1f)
        },
        modelColor = overrides.modelColor ?: floatArrayOf(accentR, accentG, accentB, 1f),
        selectedFootprintColor = overrides.selectedFootprintColor ?: floatArrayOf(
            min(1f, accentR * 1.10f + 0.04f),
            min(1f, accentG * 1.10f + 0.04f),
            min(1f, accentB * 1.10f + 0.04f),
            if (appearance.darkTheme) 0.74f else 0.68f
        )
    )
}
