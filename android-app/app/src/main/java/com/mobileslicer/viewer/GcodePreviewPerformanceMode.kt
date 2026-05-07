package com.mobileslicer.viewer

internal enum class GcodePreviewPerformanceMode(
    val vertexBudget: Long,
    val displayLabel: String,
    val description: String
) {
    Low(
        vertexBudget = 400_000L,
        displayLabel = "Low",
        description = "Smaller preview chunks for older phones."
    ),
    MidRange(
        vertexBudget = 750_000L,
        displayLabel = "Medium",
        description = "Balanced preview loading for most phones."
    ),
    HighEnd(
        vertexBudget = 1_000_000L,
        displayLabel = "High",
        description = "Larger preview chunks for faster phones."
    );

    companion object {
        const val HARD_VERTEX_CEILING: Long = 1_000_000L
        val Default: GcodePreviewPerformanceMode = MidRange

        fun fromStoredName(value: String?): GcodePreviewPerformanceMode =
            entries.firstOrNull { it.name == value } ?: Default
    }
}
