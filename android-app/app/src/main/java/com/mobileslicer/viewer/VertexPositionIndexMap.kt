package com.mobileslicer.viewer

internal class VertexPositionIndexMap(initialCapacity: Int = 262_144) {
    private var mask = normalizedCapacity(initialCapacity) - 1
    private var xBits = IntArray(mask + 1)
    private var yBits = IntArray(mask + 1)
    private var zBits = IntArray(mask + 1)
    private var values = IntArray(mask + 1)
    private var used = BooleanArray(mask + 1)
    private var resizeThreshold = ((mask + 1) * MaxLoadFactor).toInt()
    var size: Int = 0
        private set

    fun getOrPut(x: Float, y: Float, z: Float, create: () -> Int): Int {
        val xb = x.toRawBits()
        val yb = y.toRawBits()
        val zb = z.toRawBits()
        val existing = find(xb, yb, zb)
        if (existing >= 0) return values[existing]
        if (size + 1 > resizeThreshold) {
            grow()
        }
        val slot = emptySlot(xb, yb, zb)
        val value = create()
        used[slot] = true
        xBits[slot] = xb
        yBits[slot] = yb
        zBits[slot] = zb
        values[slot] = value
        size++
        return value
    }

    private fun find(xb: Int, yb: Int, zb: Int): Int {
        var slot = hash(xb, yb, zb) and mask
        while (used[slot]) {
            if (xBits[slot] == xb && yBits[slot] == yb && zBits[slot] == zb) return slot
            slot = (slot + 1) and mask
        }
        return -1
    }

    private fun emptySlot(xb: Int, yb: Int, zb: Int): Int {
        var slot = hash(xb, yb, zb) and mask
        while (used[slot]) {
            slot = (slot + 1) and mask
        }
        return slot
    }

    private fun grow() {
        val oldX = xBits
        val oldY = yBits
        val oldZ = zBits
        val oldValues = values
        val oldUsed = used
        mask = ((mask + 1) * 2) - 1
        xBits = IntArray(mask + 1)
        yBits = IntArray(mask + 1)
        zBits = IntArray(mask + 1)
        values = IntArray(mask + 1)
        used = BooleanArray(mask + 1)
        resizeThreshold = ((mask + 1) * MaxLoadFactor).toInt()
        for (index in oldUsed.indices) {
            if (!oldUsed[index]) continue
            val slot = emptySlot(oldX[index], oldY[index], oldZ[index])
            used[slot] = true
            xBits[slot] = oldX[index]
            yBits[slot] = oldY[index]
            zBits[slot] = oldZ[index]
            values[slot] = oldValues[index]
        }
    }

    private fun hash(xb: Int, yb: Int, zb: Int): Int {
        var h = xb * -1_640_531_527
        h = h xor (yb * -2_046_568_777)
        h = h xor (zb * -1_028_477_387)
        h = h xor (h ushr 16)
        return h
    }

    private companion object {
        private const val MaxLoadFactor = 0.72f

        private fun normalizedCapacity(requested: Int): Int {
            var capacity = 1
            val target = requested.coerceAtLeast(16)
            while (capacity < target) {
                capacity = capacity shl 1
            }
            return capacity
        }
    }
}
