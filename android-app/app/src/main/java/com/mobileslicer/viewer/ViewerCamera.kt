package com.mobileslicer.viewer

import android.opengl.Matrix
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.tan

internal data class ViewerCameraState(
    val orbitYawDegrees: Float,
    val orbitPitchDegrees: Float,
    val zoom: Float,
    val panX: Float,
    val panY: Float,
    val panZ: Float = 0f
)

internal class ViewerCamera(
    bed: PrinterBedSpec
) {
    private var orbitYawDegrees = DEFAULT_YAW_DEGREES
    private var orbitPitchDegrees = DEFAULT_PITCH_DEGREES
    private var zoom = DEFAULT_ZOOM
    private var panX = 0f
    private var panY = 0f
    private var panZ = 0f
    private var focusX = 0f
    private var focusY = 0f
    private var focusZ = 0f
    private var modelHeight = 0f
    var sceneSpan = max(bed.widthMm, bed.depthMm)
        private set

    private var sinYaw = sin(Math.toRadians(orbitYawDegrees.toDouble())).toFloat()
    private var cosYaw = cos(Math.toRadians(orbitYawDegrees.toDouble())).toFloat()
    private var sinPitch = sin(Math.toRadians(orbitPitchDegrees.toDouble())).toFloat()
    private var cosPitch = cos(Math.toRadians(orbitPitchDegrees.toDouble())).toFloat()

    fun orbitBy(deltaX: Float, deltaY: Float): Boolean {
        if (abs(deltaX) < MIN_TOUCH_DELTA_PX && abs(deltaY) < MIN_TOUCH_DELTA_PX) return false
        orbitYawDegrees -= deltaX * YAW_DEGREES_PER_PIXEL
        orbitPitchDegrees = (orbitPitchDegrees + deltaY * PITCH_DEGREES_PER_PIXEL)
            .coerceIn(MIN_PITCH_DEGREES, MAX_PITCH_DEGREES)
        updateOrbitCache()
        return true
    }

    fun panBy(deltaX: Float, deltaY: Float, viewportHeight: Int): Boolean {
        if (abs(deltaX) < MIN_TOUCH_DELTA_PX && abs(deltaY) < MIN_TOUCH_DELTA_PX) return false
        val panScale = sceneSpan / viewportHeight.toFloat().coerceAtLeast(1f) * PAN_SCALE / zoom
        val rightX = -sinYaw
        val rightY = cosYaw
        val rightZ = 0f
        val upX = -cosYaw * sinPitch
        val upY = -sinYaw * sinPitch
        val upZ = cosPitch
        panX += (-deltaX * rightX + deltaY * upX) * panScale
        panY += (-deltaX * rightY + deltaY * upY) * panScale
        panZ = (panZ + (-deltaX * rightZ + deltaY * upZ) * panScale)
            .coerceIn(-sceneSpan, sceneSpan)
        return true
    }

    fun zoomBy(scaleFactor: Float): Boolean {
        val nextZoom = (zoom * scaleFactor).coerceIn(MIN_ZOOM, MAX_ZOOM)
        if (nextZoom == zoom) return false
        zoom = nextZoom
        return true
    }

    fun snapshotState(): ViewerCameraState =
        ViewerCameraState(
            orbitYawDegrees = orbitYawDegrees,
            orbitPitchDegrees = orbitPitchDegrees,
            zoom = zoom,
            panX = panX,
            panY = panY,
            panZ = panZ
        )

    fun restoreState(state: ViewerCameraState) {
        orbitYawDegrees = state.orbitYawDegrees
        orbitPitchDegrees = state.orbitPitchDegrees.coerceIn(MIN_PITCH_DEGREES, MAX_PITCH_DEGREES)
        zoom = state.zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        panX = state.panX
        panY = state.panY
        panZ = state.panZ.coerceIn(-sceneSpan, sceneSpan)
        updateOrbitCache()
    }

    fun resetView() {
        orbitYawDegrees = DEFAULT_YAW_DEGREES
        orbitPitchDegrees = DEFAULT_PITCH_DEGREES
        zoom = DEFAULT_ZOOM
        resetPan()
        updateOrbitCache()
    }

    internal fun cameraDistanceForTesting(): Float = cameraDistance()

    fun setEmptyScene(bed: PrinterBedSpec) {
        focusX = 0f
        focusY = 0f
        focusZ = 0f
        modelHeight = 0f
        sceneSpan = max(bed.widthMm, bed.depthMm)
        resetPan()
    }

    fun setModelScene(
        bed: PrinterBedSpec,
        focusX: Float,
        focusY: Float,
        sizeX: Float,
        sizeY: Float,
        sizeZ: Float
    ) {
        this.focusX = focusX
        this.focusY = focusY
        modelHeight = sizeZ
        sceneSpan = max(
            max(bed.widthMm, bed.depthMm),
            max(max(sizeX, sizeY), sizeZ).coerceAtLeast(MIN_SCENE_SPAN)
        )
        focusZ = max(sizeZ * FOCUS_HEIGHT_FACTOR, MIN_FOCUS_Z)
        resetPan()
    }

    fun setPlateObjectsScene(bed: PrinterBedSpec, maxObjectSize: Float) {
        focusX = 0f
        focusY = 0f
        modelHeight = maxObjectSize
        sceneSpan = max(max(bed.widthMm, bed.depthMm), maxObjectSize.coerceAtLeast(MIN_SCENE_SPAN))
        focusZ = max(maxObjectSize * FOCUS_HEIGHT_FACTOR, MIN_FOCUS_Z)
        resetPan()
    }

    fun updatePlateObjectsSceneKeepingView(bed: PrinterBedSpec, maxObjectSize: Float) {
        focusX = 0f
        focusY = 0f
        modelHeight = maxObjectSize
        sceneSpan = max(max(bed.widthMm, bed.depthMm), maxObjectSize.coerceAtLeast(MIN_SCENE_SPAN))
        focusZ = max(maxObjectSize * FOCUS_HEIGHT_FACTOR, MIN_FOCUS_Z)
    }

    fun updateMatrices(
        width: Int,
        height: Int,
        bed: PrinterBedSpec,
        projectionMatrix: FloatArray,
        viewMatrix: FloatArray,
        viewProjectionMatrix: FloatArray
    ) {
        val aspectRatio = width.toFloat() / height.toFloat().coerceAtLeast(1f)
        val effectiveSpan = sceneSpan.coerceAtLeast(MIN_SCENE_SPAN)
        val distance = cameraDistance()
        val targetX = focusX + panX
        val targetY = focusY + panY
        val targetZ = focusZ + panZ
        val eyeX = targetX + cosYaw * cosPitch * distance
        val eyeY = targetY + sinYaw * cosPitch * distance
        val eyeZ = targetZ + sinPitch * distance

        Matrix.setLookAtM(
            viewMatrix,
            0,
            eyeX,
            eyeY,
            eyeZ,
            targetX,
            targetY,
            targetZ,
            0f,
            0f,
            1f
        )
        val halfHeight = viewHalfHeight(distance)
        val halfWidth = halfHeight * aspectRatio
        Matrix.orthoM(
            projectionMatrix,
            0,
            -halfWidth,
            halfWidth,
            -halfHeight,
            halfHeight,
            NEAR_PLANE_MM,
            effectiveSpan * FAR_SPAN_FACTOR + bed.maxHeightMm * FAR_HEIGHT_FACTOR
        )
        Matrix.multiplyMM(viewProjectionMatrix, 0, projectionMatrix, 0, viewMatrix, 0)
    }

    private fun resetPan() {
        panX = 0f
        panY = 0f
        panZ = 0f
    }

    private fun cameraDistance(): Float {
        val effectiveSpan = sceneSpan.coerceAtLeast(MIN_SCENE_SPAN)
        val baseDistance = effectiveSpan * CAMERA_DISTANCE_FACTOR + max(modelHeight * MODEL_DISTANCE_FACTOR, MIN_CAMERA_DISTANCE)
        return baseDistance / zoom
    }

    private fun viewHalfHeight(distance: Float): Float =
        max(
            MIN_ORTHO_HALF_HEIGHT,
            (distance * tan(Math.toRadians((FIELD_OF_VIEW_DEGREES * 0.5f).toDouble()))).toFloat()
        )

    private fun updateOrbitCache() {
        val yawRadians = Math.toRadians(orbitYawDegrees.toDouble())
        val pitchRadians = Math.toRadians(orbitPitchDegrees.toDouble())
        sinYaw = sin(yawRadians).toFloat()
        cosYaw = cos(yawRadians).toFloat()
        sinPitch = sin(pitchRadians).toFloat()
        cosPitch = cos(pitchRadians).toFloat()
    }

    private companion object {
        private const val DEFAULT_YAW_DEGREES = -90f
        private const val DEFAULT_PITCH_DEGREES = 42f
        private const val DEFAULT_ZOOM = 0.55f
        private const val MIN_PITCH_DEGREES = -84f
        private const val MAX_PITCH_DEGREES = 82f
        private const val YAW_DEGREES_PER_PIXEL = 0.42f
        private const val PITCH_DEGREES_PER_PIXEL = 0.30f
        private const val PAN_SCALE = 1.15f
        private const val MIN_TOUCH_DELTA_PX = 0.35f
        private const val MIN_ZOOM = 0.28f
        private const val MAX_ZOOM = 24f
        private const val MIN_SCENE_SPAN = 40f
        private const val FOCUS_HEIGHT_FACTOR = 0.22f
        private const val MIN_FOCUS_Z = 4f
        private const val CAMERA_DISTANCE_FACTOR = 2.05f
        private const val MODEL_DISTANCE_FACTOR = 0.4f
        private const val MIN_CAMERA_DISTANCE = 24f
        private const val FIELD_OF_VIEW_DEGREES = 24f
        private const val MIN_ORTHO_HALF_HEIGHT = 12f
        private const val NEAR_PLANE_MM = 5f
        private const val FAR_SPAN_FACTOR = 8f
        private const val FAR_HEIGHT_FACTOR = 3f
    }
}
