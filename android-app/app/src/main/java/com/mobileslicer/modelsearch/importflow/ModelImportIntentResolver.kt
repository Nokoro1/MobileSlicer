package com.mobileslicer.modelsearch.importflow

import android.content.Intent
import android.net.Uri
import com.mobileslicer.workspace.detectSourceModelFileFormat
import com.mobileslicer.workspace.isSupportedModelMimeType

object ModelImportIntentResolver {
    fun resolve(intent: Intent?): Uri? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_VIEW -> intent.data?.takeIf { it.scheme in setOf("content", "file") }
            Intent.ACTION_SEND -> (intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)?.takeIf {
                isLikelyModelUri(type = intent.type, lastPathSegment = it.lastPathSegment)
            }
            else -> null
        }
    }

    fun acceptsModelUri(type: String?, lastPathSegment: String?): Boolean =
        isLikelyModelUri(type = type, lastPathSegment = lastPathSegment)

    private fun isLikelyModelUri(type: String?, lastPathSegment: String?): Boolean {
        return detectSourceModelFileFormat(lastPathSegment) != null ||
            isSupportedModelMimeType(type)
    }
}
