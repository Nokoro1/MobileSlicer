package com.mobileslicer.viewer

import android.opengl.GLES20

internal object ViewerTriangleProgram {
    fun create(): TriangleProgram {
        val programId = buildProgram(TRIANGLE_VERTEX_SHADER, TRIANGLE_FRAGMENT_SHADER)
        return TriangleProgram(
            programId = programId,
            handles = TriangleProgramHandles(
                positionHandle = GLES20.glGetAttribLocation(programId, "aPosition"),
                normalHandle = GLES20.glGetAttribLocation(programId, "aNormal"),
                matrixHandle = GLES20.glGetUniformLocation(programId, "uViewProjectionMatrix"),
                modelMatrixHandle = GLES20.glGetUniformLocation(programId, "uModelMatrix"),
                colorHandle = GLES20.glGetUniformLocation(programId, "uColor"),
                lightHandle = GLES20.glGetUniformLocation(programId, "uLightDirection"),
                flatShadingHandle = GLES20.glGetUniformLocation(programId, "uFlatShading"),
                fullBrightHandle = GLES20.glGetUniformLocation(programId, "uFullBright")
            )
        )
    }

    private fun buildProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        return GLES20.glCreateProgram().also { program ->
            GLES20.glAttachShader(program, vertexShader)
            GLES20.glAttachShader(program, fragmentShader)
            GLES20.glLinkProgram(program)
            val linkStatus = IntArray(1)
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] == 0) {
                val log = GLES20.glGetProgramInfoLog(program)
                GLES20.glDeleteProgram(program)
                error("OpenGL program link failed: $log")
            }
            GLES20.glDeleteShader(vertexShader)
            GLES20.glDeleteShader(fragmentShader)
        }
    }

    private fun compileShader(type: Int, source: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
            val compileStatus = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
            if (compileStatus[0] == 0) {
                val log = GLES20.glGetShaderInfoLog(shader)
                GLES20.glDeleteShader(shader)
                error("OpenGL shader compile failed: $log")
            }
        }
    }

    private const val TRIANGLE_VERTEX_SHADER = """
        uniform mat4 uViewProjectionMatrix;
        uniform mat4 uModelMatrix;
        uniform vec3 uLightDirection;
        uniform bool uFlatShading;
        uniform bool uFullBright;
        attribute vec3 aPosition;
        attribute vec3 aNormal;
        varying float vLighting;
        varying vec3 vWorldPosition;
        varying vec3 vLightDirection;

        void main() {
            vec4 worldPosition = uModelMatrix * vec4(aPosition, 1.0);
            vec3 normal = normalize((uModelMatrix * vec4(aNormal, 0.0)).xyz);
            vWorldPosition = worldPosition.xyz;
            vLightDirection = normalize(uLightDirection);
            vLighting = (uFlatShading || uFullBright) ? 1.0 : max(dot(normal, vLightDirection), 0.0);
            gl_Position = uViewProjectionMatrix * worldPosition;
        }
    """

    private const val TRIANGLE_FRAGMENT_SHADER = """
        #extension GL_OES_standard_derivatives : enable
        precision mediump float;
        uniform vec4 uColor;
        uniform bool uFlatShading;
        uniform bool uFullBright;
        varying float vLighting;
        varying vec3 vWorldPosition;
        varying vec3 vLightDirection;

        void main() {
            float lighting = vLighting;
            if (uFullBright) {
                gl_FragColor = uColor;
                return;
            }
            if (uFlatShading) {
                vec3 dx = dFdx(vWorldPosition);
                vec3 dy = dFdy(vWorldPosition);
                vec3 normal = normalize(cross(dx, dy));
                lighting = abs(dot(normal, vLightDirection));
            }
            float diffuse = 0.30 + 0.70 * lighting;
            gl_FragColor = vec4(uColor.rgb * diffuse, uColor.a);
        }
    """
}
