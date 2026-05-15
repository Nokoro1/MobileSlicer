package com.mobileslicer.viewer

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal data class CutPlaneGeometry(
    val center: FloatArray,
    val normal: FloatArray,
    val u: FloatArray,
    val v: FloatArray
)

private data class CutPlaneSegment(
    val u1: Float,
    val v1: Float,
    val u2: Float,
    val v2: Float
)

private data class CutClipVertex(
    val x: Float,
    val y: Float,
    val z: Float,
    val nx: Float,
    val ny: Float,
    val nz: Float,
    val distance: Float
)

internal fun ViewerCutPlaneSession.cutPlaneGeometry(bounds: MeshBounds): CutPlaneGeometry {
    val centerX = (bounds.minX + bounds.maxX) * 0.5f
    val centerY = (bounds.minY + bounds.maxY) * 0.5f
    val centerZ = (bounds.minZ + bounds.maxZ) * 0.5f
    val offset = offsetMm.coerceIn(bounds.minZ, bounds.maxZ)
    val rx = Math.toRadians(rotationXDegrees.toDouble())
    val ry = Math.toRadians(rotationYDegrees.toDouble())
    val cx = cos(rx).toFloat()
    val sx = sin(rx).toFloat()
    val cy = cos(ry).toFloat()
    val sy = sin(ry).toFloat()
    val normal = floatArrayOf(sy * cx, -sx, cy * cx)
    val travel = offset - centerZ
    return CutPlaneGeometry(
        center = floatArrayOf(
            centerX + normal[0] * travel,
            centerY + normal[1] * travel,
            centerZ + normal[2] * travel
        ),
        normal = normal,
        u = floatArrayOf(cy, 0f, -sy),
        v = floatArrayOf(sy * sx, cx, cy * sx)
    )
}

internal fun buildCutConnectorMarkerGeometry(
    points: List<ViewerCutConnectorPoint>,
    plane: CutPlaneGeometry,
    session: ViewerCutPlaneSession
): Pair<FloatArray, FloatArray> {
    val sides = when (session.connectorShape) {
        ViewerCutConnectorShape.Triangle -> 3
        ViewerCutConnectorShape.Square -> 4
        ViewerCutConnectorShape.Hexagon -> 6
        ViewerCutConnectorShape.Circle -> 32
    }
    val radiusMm = (session.connectorSizeMm * 0.5f).coerceAtLeast(0.2f)
    val toleranceRadiusMm = ((session.connectorSizeMm + session.connectorSizeToleranceMm) * 0.5f)
        .coerceAtLeast(radiusMm)
    val heightMm = session.connectorDepthMm.coerceAtLeast(0.2f)
    val vertices = ArrayList<Float>(points.size * sides * 36)
    val normals = ArrayList<Float>(points.size * sides * 36)
    fun append(
        x: Float,
        y: Float,
        z: Float,
        nx: Float = plane.normal[0],
        ny: Float = plane.normal[1],
        nz: Float = plane.normal[2]
    ) {
        vertices.add(x)
        vertices.add(y)
        vertices.add(z)
        normals.add(nx)
        normals.add(ny)
        normals.add(nz)
    }
    fun polygonPoint(center: FloatArray, radius: Float, index: Int, normalOffset: Float): FloatArray {
        val baseAngle = Math.toRadians(session.connectorRotationDegrees.toDouble()).toFloat()
        val angle = baseAngle + (index.toFloat() / sides.toFloat()) * (Math.PI.toFloat() * 2f)
        val cosA = cos(angle)
        val sinA = sin(angle)
        return floatArrayOf(
            center[0] + (plane.u[0] * cosA + plane.v[0] * sinA) * radius + plane.normal[0] * normalOffset,
            center[1] + (plane.u[1] * cosA + plane.v[1] * sinA) * radius + plane.normal[1] * normalOffset,
            center[2] + (plane.u[2] * cosA + plane.v[2] * sinA) * radius + plane.normal[2] * normalOffset
        )
    }
    fun appendCap(center: FloatArray, radius: Float, normalOffset: Float, reverse: Boolean = false) {
        val capCenter = floatArrayOf(
            center[0] + plane.normal[0] * normalOffset,
            center[1] + plane.normal[1] * normalOffset,
            center[2] + plane.normal[2] * normalOffset
        )
        var previous = polygonPoint(center, radius, 0, normalOffset)
        for (side in 1..sides) {
            val next = polygonPoint(center, radius, side % sides, normalOffset)
            if (reverse) {
                append(capCenter[0], capCenter[1], capCenter[2], -plane.normal[0], -plane.normal[1], -plane.normal[2])
                append(next[0], next[1], next[2], -plane.normal[0], -plane.normal[1], -plane.normal[2])
                append(previous[0], previous[1], previous[2], -plane.normal[0], -plane.normal[1], -plane.normal[2])
            } else {
                append(capCenter[0], capCenter[1], capCenter[2])
                append(previous[0], previous[1], previous[2])
                append(next[0], next[1], next[2])
            }
            previous = next
        }
    }
    fun appendSideWall(
        center: FloatArray,
        bottomRadius: Float,
        topRadius: Float,
        startOffset: Float,
        endOffset: Float
    ) {
        for (side in 0 until sides) {
            val nextSide = (side + 1) % sides
            val b1 = polygonPoint(center, bottomRadius, side, startOffset)
            val b2 = polygonPoint(center, bottomRadius, nextSide, startOffset)
            val t1 = polygonPoint(center, topRadius, side, endOffset)
            val t2 = polygonPoint(center, topRadius, nextSide, endOffset)
            append(b1[0], b1[1], b1[2])
            append(b2[0], b2[1], b2[2])
            append(t2[0], t2[1], t2[2])
            append(b1[0], b1[1], b1[2])
            append(t2[0], t2[1], t2[2])
            append(t1[0], t1[1], t1[2])
        }
    }
    points.forEach { point ->
        val center = floatArrayOf(point.xMm, point.yMm, point.zMm)
        if (session.connectorKind == ViewerCutConnectorKind.Dowel) {
            val previewHeight = if (
                session.connectorStyle == ViewerCutConnectorStyle.Prism &&
                session.connectorsEditing
            ) {
                0.05f
            } else if (session.connectorStyle == ViewerCutConnectorStyle.Prism) {
                heightMm * 2f
            } else {
                heightMm
            }
            val halfHeight = previewHeight * 0.5f
            appendCap(center, toleranceRadiusMm, -halfHeight, reverse = true)
            appendSideWall(center, toleranceRadiusMm, toleranceRadiusMm, -halfHeight, halfHeight)
            appendCap(center, toleranceRadiusMm, halfHeight)
            return@forEach
        }
        val topRadius = if (session.connectorStyle == ViewerCutConnectorStyle.Frustum) {
            radiusMm * 0.68f
        } else {
            radiusMm
        }
        appendCap(center, toleranceRadiusMm, 0.055f)
        appendCap(center, radiusMm, 0.12f)
        appendSideWall(center, radiusMm, topRadius, 0.08f, heightMm)
        appendCap(center, topRadius, heightMm)
        if (session.connectorKind == ViewerCutConnectorKind.Snap) {
            val bulgeRadius = radiusMm * (1f + session.connectorSnapBulgePercent.coerceAtLeast(0f) / 100f)
            appendCap(center, bulgeRadius, heightMm * 0.52f)
        }
    }
    return vertices.toFloatArray() to normals.toFloatArray()
}

internal fun buildCutFaceFillGeometry(mesh: StlMesh, plane: CutPlaneGeometry): Pair<FloatArray, FloatArray> {
    val segments = mesh.cutPlaneSegments(plane)
    if (segments.isEmpty()) return FloatArray(0) to FloatArray(0)
    val vertices = ArrayList<Float>(segments.size * 18)
    val normals = ArrayList<Float>(segments.size * 18)
    val stripHalfWidth = maxOf(0.35f, maxOf(mesh.bounds.sizeX, mesh.bounds.sizeY, mesh.bounds.sizeZ) * 0.006f)
    fun append(u: Float, v: Float) {
        vertices.add(plane.center[0] + plane.u[0] * u + plane.v[0] * v + plane.normal[0] * 0.045f)
        vertices.add(plane.center[1] + plane.u[1] * u + plane.v[1] * v + plane.normal[1] * 0.045f)
        vertices.add(plane.center[2] + plane.u[2] * u + plane.v[2] * v + plane.normal[2] * 0.045f)
        normals.add(plane.normal[0])
        normals.add(plane.normal[1])
        normals.add(plane.normal[2])
    }
    segments.forEach { segment ->
        val du = segment.u2 - segment.u1
        val dv = segment.v2 - segment.v1
        val length = sqrt(du * du + dv * dv)
        if (length <= 0.001f) return@forEach
        val normalU = -dv / length * stripHalfWidth
        val normalV = du / length * stripHalfWidth
        append(segment.u1 - normalU, segment.v1 - normalV)
        append(segment.u2 - normalU, segment.v2 - normalV)
        append(segment.u2 + normalU, segment.v2 + normalV)
        append(segment.u1 - normalU, segment.v1 - normalV)
        append(segment.u2 + normalU, segment.v2 + normalV)
        append(segment.u1 + normalU, segment.v1 + normalV)
    }
    return vertices.toFloatArray() to normals.toFloatArray()
}

internal fun buildCutClippedObjectGeometry(mesh: StlMesh, plane: CutPlaneGeometry): Pair<FloatArray, FloatArray> {
    val sourceVertices = mesh.vertices
    val sourceNormals = mesh.normals
    val vertices = ArrayList<Float>(mesh.triangleCount * 9)
    val normals = ArrayList<Float>(mesh.triangleCount * 9)
    mesh.forEachTriangleVertexOffsets { _, a, b, c ->
        val triangle = listOf(
            cutClipVertex(sourceVertices, sourceNormals, a, plane),
            cutClipVertex(sourceVertices, sourceNormals, b, plane),
            cutClipVertex(sourceVertices, sourceNormals, c, plane)
        )
        val clipped = clipTriangleToCutPlane(triangle)
        if (clipped.size >= 3) {
            for (fanIndex in 1 until clipped.lastIndex) {
                appendCutClipVertex(clipped[0], vertices, normals)
                appendCutClipVertex(clipped[fanIndex], vertices, normals)
                appendCutClipVertex(clipped[fanIndex + 1], vertices, normals)
            }
        }
    }
    return vertices.toFloatArray() to normals.toFloatArray()
}

internal fun StlMesh.containsCutPlanePoint(
    plane: CutPlaneGeometry,
    pointU: Float,
    pointV: Float
): Boolean {
    val segments = cutPlaneSegments(plane)
    if (segments.isEmpty()) return false
    val nearTolerance = maxOf(0.65f, maxOf(bounds.sizeX, bounds.sizeY) * 0.012f)
    if (segments.any { it.distanceTo(pointU, pointV) <= nearTolerance }) return true
    var intersections = 0
    segments.forEach { segment ->
        val crosses =
            (segment.v1 > pointV) != (segment.v2 > pointV) &&
                abs(segment.v2 - segment.v1) > 0.0001f
        if (crosses) {
            val uAtV = segment.u1 + (pointV - segment.v1) * (segment.u2 - segment.u1) / (segment.v2 - segment.v1)
            if (uAtV > pointU + 0.0001f) {
                intersections += 1
            }
        }
    }
    return intersections % 2 == 1
}

private fun cutClipVertex(
    vertices: FloatArray,
    normals: FloatArray,
    offset: Int,
    plane: CutPlaneGeometry
): CutClipVertex {
    val x = vertices[offset]
    val y = vertices[offset + 1]
    val z = vertices[offset + 2]
    return CutClipVertex(
        x = x,
        y = y,
        z = z,
        nx = normals.getOrElse(offset) { 0f },
        ny = normals.getOrElse(offset + 1) { 0f },
        nz = normals.getOrElse(offset + 2) { 1f },
        distance = (x - plane.center[0]) * plane.normal[0] +
            (y - plane.center[1]) * plane.normal[1] +
            (z - plane.center[2]) * plane.normal[2]
    )
}

private fun clipTriangleToCutPlane(triangle: List<CutClipVertex>): List<CutClipVertex> {
    if (triangle.all { it.distance >= 0f }) return triangle
    if (triangle.all { it.distance < 0f }) return emptyList()
    val clipped = ArrayList<CutClipVertex>(4)
    triangle.forEachIndexed { index, current ->
        val previous = triangle[(index + triangle.lastIndex) % triangle.size]
        val currentInside = current.distance >= 0f
        val previousInside = previous.distance >= 0f
        if (currentInside != previousInside) {
            clipped.add(interpolateCutClipVertex(previous, current))
        }
        if (currentInside) {
            clipped.add(current)
        }
    }
    return clipped
}

private fun interpolateCutClipVertex(start: CutClipVertex, end: CutClipVertex): CutClipVertex {
    val denominator = start.distance - end.distance
    val t = if (abs(denominator) <= 0.0001f) 0f else (start.distance / denominator).coerceIn(0f, 1f)
    fun lerp(a: Float, b: Float): Float = a + (b - a) * t
    return CutClipVertex(
        x = lerp(start.x, end.x),
        y = lerp(start.y, end.y),
        z = lerp(start.z, end.z),
        nx = lerp(start.nx, end.nx),
        ny = lerp(start.ny, end.ny),
        nz = lerp(start.nz, end.nz),
        distance = 0f
    )
}

private fun appendCutClipVertex(
    vertex: CutClipVertex,
    vertices: MutableList<Float>,
    normals: MutableList<Float>
) {
    vertices.add(vertex.x)
    vertices.add(vertex.y)
    vertices.add(vertex.z)
    normals.add(vertex.nx)
    normals.add(vertex.ny)
    normals.add(vertex.nz)
}

private fun StlMesh.cutPlaneSegments(plane: CutPlaneGeometry): List<CutPlaneSegment> {
    val segments = ArrayList<CutPlaneSegment>()
    val pointA = FloatArray(3)
    val pointB = FloatArray(3)
    val pointC = FloatArray(3)
    forEachTriangleVertexOffsets { _, a, b, c ->
        readVertex(vertices, a, pointA)
        readVertex(vertices, b, pointB)
        readVertex(vertices, c, pointC)
        val dA = signedDistanceToCutPlane(pointA, plane)
        val dB = signedDistanceToCutPlane(pointB, plane)
        val dC = signedDistanceToCutPlane(pointC, plane)
        val intersections = ArrayList<FloatArray>(2)
        appendCutIntersection(pointA, dA, pointB, dB, plane, intersections)
        appendCutIntersection(pointB, dB, pointC, dC, plane, intersections)
        appendCutIntersection(pointC, dC, pointA, dA, plane, intersections)
        if (intersections.size >= 2) {
            val first = intersections[0]
            val second = intersections.firstOrNull { other ->
                abs(other[0] - first[0]) > 0.01f ||
                    abs(other[1] - first[1]) > 0.01f
            }
            if (second != null) {
                segments.add(CutPlaneSegment(first[0], first[1], second[0], second[1]))
            }
        }
    }
    return segments
}

private fun readVertex(vertices: FloatArray, offset: Int, out: FloatArray) {
    out[0] = vertices[offset]
    out[1] = vertices[offset + 1]
    out[2] = vertices[offset + 2]
}

private fun signedDistanceToCutPlane(point: FloatArray, plane: CutPlaneGeometry): Float =
    (point[0] - plane.center[0]) * plane.normal[0] +
        (point[1] - plane.center[1]) * plane.normal[1] +
        (point[2] - plane.center[2]) * plane.normal[2]

private fun appendCutIntersection(
    start: FloatArray,
    startDistance: Float,
    end: FloatArray,
    endDistance: Float,
    plane: CutPlaneGeometry,
    intersections: MutableList<FloatArray>
) {
    val epsilon = 0.0001f
    if (abs(startDistance) <= epsilon && abs(endDistance) <= epsilon) return
    if (startDistance * endDistance > 0f) return
    val t = if (abs(startDistance - endDistance) <= epsilon) {
        0f
    } else {
        startDistance / (startDistance - endDistance)
    }.coerceIn(0f, 1f)
    val x = start[0] + (end[0] - start[0]) * t
    val y = start[1] + (end[1] - start[1]) * t
    val z = start[2] + (end[2] - start[2]) * t
    val fromCenterX = x - plane.center[0]
    val fromCenterY = y - plane.center[1]
    val fromCenterZ = z - plane.center[2]
    val projected = floatArrayOf(
        fromCenterX * plane.u[0] + fromCenterY * plane.u[1] + fromCenterZ * plane.u[2],
        fromCenterX * plane.v[0] + fromCenterY * plane.v[1] + fromCenterZ * plane.v[2]
    )
    val duplicate = intersections.any { existing ->
        abs(existing[0] - projected[0]) <= 0.01f &&
            abs(existing[1] - projected[1]) <= 0.01f
    }
    if (!duplicate) {
        intersections.add(projected)
    }
}

private fun CutPlaneSegment.distanceTo(pointU: Float, pointV: Float): Float {
    val du = u2 - u1
    val dv = v2 - v1
    val lengthSquared = du * du + dv * dv
    if (lengthSquared <= 0.0001f) {
        val pointDu = pointU - u1
        val pointDv = pointV - v1
        return sqrt(pointDu * pointDu + pointDv * pointDv)
    }
    val t = (((pointU - u1) * du + (pointV - v1) * dv) / lengthSquared).coerceIn(0f, 1f)
    val closestU = u1 + du * t
    val closestV = v1 + dv * t
    val pointDu = pointU - closestU
    val pointDv = pointV - closestV
    return sqrt(pointDu * pointDu + pointDv * pointDv)
}
