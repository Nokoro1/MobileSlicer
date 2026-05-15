package com.mobileslicer.modelsearch.importflow

enum class ImportFailureReason(val userMessage: String) {
    FILE_UNAVAILABLE("MobileSlicer could not open the selected source or file.")
}
