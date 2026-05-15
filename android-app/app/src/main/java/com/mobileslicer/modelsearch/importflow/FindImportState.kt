package com.mobileslicer.modelsearch.importflow

sealed interface FindImportState {
    data object SourcePicker : FindImportState

    data class ExternalSiteOpened(
        val sourceId: String,
        val openedAtEpochMs: Long
    ) : FindImportState

    data class AwaitingUserFile(
        val sourceId: String?,
        val openedAtEpochMs: Long?
    ) : FindImportState

    data class ImportBlocked(
        val reason: ImportFailureReason
    ) : FindImportState
}

object FindImportStateMachine {
    fun openExternalSource(sourceId: String, openedAtEpochMs: Long): FindImportState =
        FindImportState.ExternalSiteOpened(
            sourceId = sourceId,
            openedAtEpochMs = openedAtEpochMs
        )

    fun onAppResumed(state: FindImportState): FindImportState =
        when (state) {
            is FindImportState.ExternalSiteOpened -> FindImportState.AwaitingUserFile(
                sourceId = state.sourceId,
                openedAtEpochMs = state.openedAtEpochMs
            )
            else -> state
        }
}
