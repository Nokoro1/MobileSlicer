package com.mobileslicer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.mobileslicer.profiles.ProfilesLandingSection
import com.mobileslicer.storage.SavedProject

@Composable
internal fun ModelLoaderHomeScreen(
    importedModel: String,
    printerTitle: String,
    filamentTitle: String,
    processTitle: String,
    projects: List<SavedProject>,
    importInProgress: Boolean,
    showScannerEntry: Boolean,
    onOpenSettings: () -> Unit,
    onSelectModel: () -> Unit,
    onFindAndImportModel: () -> Unit,
    onScannerClick: () -> Unit,
    onCalibrationsClick: () -> Unit,
    onProfilesClick: () -> Unit,
    onOpenProject: (SavedProject) -> Unit,
    onDeleteProject: (SavedProject) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(brush = Brush.verticalGradient(colors = appBackgroundGradient()))
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        HomeTopBar(onOpenSettings = onOpenSettings)
        HeroImportCard(
            importedModel = importedModel,
            importInProgress = importInProgress,
            showScannerEntry = showScannerEntry,
            onSelectModel = onSelectModel,
            onFindAndImportModel = onFindAndImportModel,
            onScannerClick = onScannerClick
        )
        PrinterCalibrationsLandingSection(
            importInProgress = importInProgress,
            onCalibrationsClick = onCalibrationsClick
        )
        ProfilesLandingSection(
            printerTitle = printerTitle,
            filamentTitle = filamentTitle,
            processTitle = processTitle,
            importInProgress = importInProgress,
            onProfilesClick = onProfilesClick
        )
        SavedProjectsLandingSection(
            projects = projects,
            importInProgress = importInProgress,
            onOpenProject = onOpenProject,
            onDeleteProject = onDeleteProject
        )
    }
}
