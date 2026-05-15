package com.mobileslicer.viewer

import android.opengl.GLES20
import android.opengl.GLES30

internal fun drawTriangleUpload(
    programId: Int,
    handles: TriangleProgramHandles,
    upload: TriangleUpload,
    color: FloatArray,
    viewProjectionMatrix: FloatArray,
    modelMatrix: FloatArray,
    identityModelMatrix: FloatArray,
    applyPlacement: Boolean = true,
    stabilizeSurface: Boolean = false,
    modelMatrixOverride: FloatArray? = null,
    fullBright: Boolean = false
) {
    GLES20.glUseProgram(programId)
    if (stabilizeSurface) {
        GLES20.glEnable(GLES20.GL_POLYGON_OFFSET_FILL)
        GLES20.glPolygonOffset(-1f, -1f)
        GLES20.glDepthMask(false)
    }

    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, upload.vertexBufferId)
    GLES20.glEnableVertexAttribArray(handles.positionHandle)
    GLES20.glVertexAttribPointer(handles.positionHandle, 3, GLES20.GL_FLOAT, false, 0, 0)

    if (upload.normalBufferId != 0) {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, upload.normalBufferId)
        GLES20.glEnableVertexAttribArray(handles.normalHandle)
        GLES20.glVertexAttribPointer(handles.normalHandle, 3, GLES20.GL_FLOAT, false, 0, 0)
    } else {
        GLES20.glDisableVertexAttribArray(handles.normalHandle)
        GLES20.glVertexAttrib3f(handles.normalHandle, 0f, 0f, 1f)
    }

    GLES20.glUniformMatrix4fv(handles.matrixHandle, 1, false, viewProjectionMatrix, 0)
    GLES20.glUniformMatrix4fv(
        handles.modelMatrixHandle,
        1,
        false,
        if (applyPlacement) modelMatrixOverride ?: modelMatrix else identityModelMatrix,
        0
    )
    GLES20.glUniform4fv(handles.colorHandle, 1, color, 0)
    GLES20.glUniform3f(handles.lightHandle, -0.35f, 0.46f, 0.82f)
    GLES20.glUniform1i(handles.flatShadingHandle, if (upload.flatShaded) 1 else 0)
    GLES20.glUniform1i(handles.fullBrightHandle, if (fullBright) 1 else 0)
    if (upload.indexed) {
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, upload.indexBufferId)
        GLES30.glDrawElements(GLES20.GL_TRIANGLES, upload.indexCount, GLES30.GL_UNSIGNED_INT, 0)
    } else {
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, upload.vertexCount)
    }

    GLES20.glDisableVertexAttribArray(handles.positionHandle)
    GLES20.glDisableVertexAttribArray(handles.normalHandle)
    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
    if (stabilizeSurface) {
        GLES20.glDepthMask(true)
        GLES20.glDisable(GLES20.GL_POLYGON_OFFSET_FILL)
    }
}

internal fun drawTextureUpload(
    programId: Int,
    handles: TextureProgramHandles,
    upload: TextureUpload,
    viewProjectionMatrix: FloatArray,
    alpha: Float
) {
    GLES20.glUseProgram(programId)
    GLES20.glEnable(GLES20.GL_POLYGON_OFFSET_FILL)
    GLES20.glPolygonOffset(-2f, -2f)
    GLES20.glDepthMask(false)
    try {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, upload.vertexBufferId)
        GLES20.glEnableVertexAttribArray(handles.positionHandle)
        GLES20.glVertexAttribPointer(handles.positionHandle, 3, GLES20.GL_FLOAT, false, 0, 0)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, upload.uvBufferId)
        GLES20.glEnableVertexAttribArray(handles.uvHandle)
        GLES20.glVertexAttribPointer(handles.uvHandle, 2, GLES20.GL_FLOAT, false, 0, 0)

        GLES20.glUniformMatrix4fv(handles.matrixHandle, 1, false, viewProjectionMatrix, 0)
        GLES20.glUniform1f(handles.alphaHandle, alpha)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, upload.textureId)
        GLES20.glUniform1i(handles.textureHandle, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, upload.vertexCount)

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        GLES20.glDisableVertexAttribArray(handles.positionHandle)
        GLES20.glDisableVertexAttribArray(handles.uvHandle)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    } finally {
        GLES20.glDepthMask(true)
        GLES20.glDisable(GLES20.GL_POLYGON_OFFSET_FILL)
    }
}
