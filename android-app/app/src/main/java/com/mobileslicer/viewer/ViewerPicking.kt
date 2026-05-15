package com.mobileslicer.viewer

internal data class PickableObjectBounds(
    val id: Long,
    val centerX: Float,
    val centerY: Float,
    val centerZ: Float,
    val radius: Float,
    val sizeX: Float,
    val sizeY: Float,
    val sizeZ: Float
)

internal fun pickViewerObject(
    screenX: Float,
    screenY: Float,
    viewportWidth: Int,
    viewportHeight: Int,
    viewProjectionMatrix: FloatArray,
    sceneSpan: Float,
    objects: List<PickableObjectBounds>
): Long? = pickViewerObjects(
    screenX = screenX,
    screenY = screenY,
    viewportWidth = viewportWidth,
    viewportHeight = viewportHeight,
    viewProjectionMatrix = viewProjectionMatrix,
    sceneSpan = sceneSpan,
    objects = objects
).firstOrNull()

internal fun pickViewerObjects(
    screenX: Float,
    screenY: Float,
    viewportWidth: Int,
    viewportHeight: Int,
    viewProjectionMatrix: FloatArray,
    sceneSpan: Float,
    objects: List<PickableObjectBounds>
): List<Long> {
    val rayHits = pickObjectsWithRay(
        screenX = screenX,
        screenY = screenY,
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
        viewProjectionMatrix = viewProjectionMatrix,
        objects = objects
    )
    val projectedHits = pickObjectsWithProjectedBounds(
        screenX = screenX,
        screenY = screenY,
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
        viewProjectionMatrix = viewProjectionMatrix,
        sceneSpan = sceneSpan,
        objects = objects
    )
    return (rayHits + projectedHits).distinct()
}

internal fun pickViewerObjectHits(
    screenX: Float,
    screenY: Float,
    viewportWidth: Int,
    viewportHeight: Int,
    viewProjectionMatrix: FloatArray,
    sceneSpan: Float,
    objects: List<ModelObjectUpload>
): List<ViewerPickHit> {
    val ray = screenRay(screenX, screenY, viewportWidth, viewportHeight, viewProjectionMatrix) ?: return emptyList()
    val boundsById = objects.associateBy { it.id }
    val broadPhaseIds = pickViewerObjects(
        screenX = screenX,
        screenY = screenY,
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
        viewProjectionMatrix = viewProjectionMatrix,
        sceneSpan = sceneSpan,
        objects = objects.map { it.toPickableObjectBounds() }
    )
    return broadPhaseIds
        .mapNotNull { objectId -> boundsById[objectId]?.let { pickObjectTriangles(ray, it) } }
        .sortedBy { it.distance }
}

internal fun ModelObjectUpload.toPickableObjectBounds(): PickableObjectBounds =
    PickableObjectBounds(
        id = id,
        centerX = centerX,
        centerY = centerY,
        centerZ = centerZ,
        radius = radius,
        sizeX = sizeX,
        sizeY = sizeY,
        sizeZ = sizeZ
    )

private fun pickObjectsWithRay(
    screenX: Float,
    screenY: Float,
    viewportWidth: Int,
    viewportHeight: Int,
    viewProjectionMatrix: FloatArray,
    objects: List<PickableObjectBounds>
): List<Long> {
    val ray = screenRay(screenX, screenY, viewportWidth, viewportHeight, viewProjectionMatrix) ?: return emptyList()
    return objects
        .mapNotNull { objectBounds ->
            val distance = intersectObjectBounds(ray, objectBounds) ?: return@mapNotNull null
            objectBounds.id to distance
        }
        .sortedBy { it.second }
        .map { it.first }
}

private fun pickObjectsWithProjectedBounds(
    screenX: Float,
    screenY: Float,
    viewportWidth: Int,
    viewportHeight: Int,
    viewProjectionMatrix: FloatArray,
    sceneSpan: Float,
    objects: List<PickableObjectBounds>
): List<Long> {
    val viewport = intArrayOf(0, 0, viewportWidth.coerceAtLeast(1), viewportHeight.coerceAtLeast(1))
    return objects
        .mapNotNull { objectBounds ->
            val projectedBounds = projectObjectBounds(
                objectBounds = objectBounds,
                viewport = viewport,
                viewportHeight = viewportHeight,
                viewProjectionMatrix = viewProjectionMatrix,
                sceneSpan = sceneSpan
            ) ?: return@mapNotNull null
            val margin = 18f
            if (
                screenX >= projectedBounds.minX - margin &&
                screenX <= projectedBounds.maxX + margin &&
                screenY >= projectedBounds.minY - margin &&
                screenY <= projectedBounds.maxY + margin
            ) {
                objectBounds.id to projectedBounds.nearestDepth
            } else {
                null
            }
        }
        .sortedBy { it.second }
        .map { it.first }
}

private fun pickObjectTriangles(ray: PickRay, objectUpload: ModelObjectUpload): ViewerPickHit? {
    val mesh = objectUpload.mesh
    val vertices = mesh.vertices
    var bestHit: ViewerPickHit? = null
    val worldA = FloatArray(3)
    val worldB = FloatArray(3)
    val worldC = FloatArray(3)
    val worldNormal = FloatArray(3)

    mesh.forEachTriangleVertexOffsets { triangleIndex, a, b, c ->
        transformPosition(objectUpload.modelMatrix, vertices, a, worldA)
        transformPosition(objectUpload.modelMatrix, vertices, b, worldB)
        transformPosition(objectUpload.modelMatrix, vertices, c, worldC)
        val hit = intersectTriangle(ray, worldA, worldB, worldC)
        if (hit != null && (bestHit == null || hit.distance < bestHit.distance)) {
            val w = 1f - hit.u - hit.v
            val hitX = worldA[0] * w + worldB[0] * hit.u + worldC[0] * hit.v
            val hitY = worldA[1] * w + worldB[1] * hit.u + worldC[1] * hit.v
            val hitZ = worldA[2] * w + worldB[2] * hit.u + worldC[2] * hit.v
            triangleNormal(worldA, worldB, worldC, worldNormal)
            if (dotNormalWithRay(worldNormal, ray) > 0f) {
                worldNormal[0] = -worldNormal[0]
                worldNormal[1] = -worldNormal[1]
                worldNormal[2] = -worldNormal[2]
            }
            bestHit = ViewerPickHit(
                objectId = objectUpload.id,
                triangleIndex = triangleIndex,
                hitPoint = StlModelPlacement(hitX, hitY, hitZ),
                normal = StlModelPlacement(worldNormal[0], worldNormal[1], worldNormal[2]),
                distance = hit.distance
            )
        }
    }
    return bestHit
}
