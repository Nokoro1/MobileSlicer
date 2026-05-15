package com.mobileslicer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mobileslicer.printerconnection.PrinterConnectionChoicesResult
import com.mobileslicer.printerconnection.PrinterConnectionResult
import com.mobileslicer.printerconnection.SimplyPrintOAuthResult
import com.mobileslicer.profiles.ProfileStore
import com.mobileslicer.profiles.PrinterProfile
import com.mobileslicer.profiles.ProfilesScreen
import com.mobileslicer.scanner.ScannerScreen
import com.mobileslicer.storage.SavedProject
import com.mobileslicer.ui.theme.AccentPaletteOption
import com.mobileslicer.ui.theme.ThemeModeOption
import com.mobileslicer.ui.theme.WorldViewColorOption
import com.mobileslicer.viewer.GcodePreviewPerformanceMode
import com.mobileslicer.workspace.AppScreen
import com.mobileslicer.workspace.ModelLoadResult

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun ModelLoaderIncompleteProfileGate(
    currentScreen: AppScreen,
    profileStore: ProfileStore,
    currentModelLabel: String,
    savedProjects: List<SavedProject>,
    importInProgress: Boolean,
    currentSavedProjectId: String?,
    appVersion: String,
    appPackageName: String,
    themeMode: ThemeModeOption,
    accentPalette: AccentPaletteOption,
    worldViewColor: WorldViewColorOption,
    showAdvancedProfileSettings: Boolean,
    activeStylusPaintOnly: Boolean,
    gcodePreviewPerformanceMode: GcodePreviewPerformanceMode,
    onThemeModeSelected: (ThemeModeOption) -> Unit,
    onAccentPaletteSelected: (AccentPaletteOption) -> Unit,
    onWorldViewColorSelected: (WorldViewColorOption) -> Unit,
    onShowAdvancedProfileSettingsChanged: (Boolean) -> Unit,
    onActiveStylusPaintOnlyChanged: (Boolean) -> Unit,
    onGcodePreviewPerformanceModeSelected: (GcodePreviewPerformanceMode) -> Unit,
    onProfileStoreChanged: (ProfileStore) -> Unit,
    onSavedProjectsChanged: (List<SavedProject>) -> Unit,
    onCurrentSavedProjectIdChanged: (String?) -> Unit,
    onCurrentScreenChanged: (AppScreen) -> Unit,
    onProfilesReturnScreenNameChanged: (String) -> Unit,
    onPrinterBrowserRequested: (String, AppScreen) -> Unit,
    onMissingProfileMessage: (String) -> Unit,
    onBackNavigation: () -> Unit,
    onTestPrinterConnectionRequested: suspend (PrinterProfile) -> String,
    onPrinterStatusRequested: suspend (PrinterProfile) -> String,
    onDiscoverPrinterHostsRequested: suspend () -> PrinterConnectionChoicesResult,
    onBrowsePrinterConnectionTargetsRequested: suspend (PrinterProfile) -> PrinterConnectionChoicesResult,
    onBrowsePrinterConnectionGroupsRequested: suspend (PrinterProfile) -> PrinterConnectionChoicesResult,
    onSimplyPrintLoginRequested: suspend (PrinterProfile) -> SimplyPrintOAuthResult
) {
    Scaffold(contentWindowInsets = WindowInsets(0.dp)) { innerPadding ->
        when (currentScreen) {
            AppScreen.Profiles -> {
                BackHandler(enabled = true) { onBackNavigation() }
                ProfilesScreen(
                    store = profileStore,
                    showAdvancedProfileSettings = showAdvancedProfileSettings,
                    onStoreChanged = onProfileStoreChanged,
                    onTestPrinterConnection = onTestPrinterConnectionRequested,
                    onPrinterStatus = onPrinterStatusRequested,
                    onDiscoverPrinterHosts = onDiscoverPrinterHostsRequested,
                    onBrowsePrinterConnectionTargets = onBrowsePrinterConnectionTargetsRequested,
                    onBrowsePrinterConnectionGroups = onBrowsePrinterConnectionGroupsRequested,
                    onSimplyPrintLogin = onSimplyPrintLoginRequested,
                    onOpenPrinterUi = { printer ->
                        printerWebUiUrl(printer)?.let { url ->
                            onPrinterBrowserRequested(url, AppScreen.Profiles)
                        }
                    },
                    onBack = onBackNavigation,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }

            AppScreen.Settings -> SettingsScreen(
                appVersion = appVersion,
                appPackageName = appPackageName,
                themeMode = themeMode,
                accentPalette = accentPalette,
                worldViewColor = worldViewColor,
                showAdvancedProfileSettings = showAdvancedProfileSettings,
                activeStylusPaintOnly = activeStylusPaintOnly,
                gcodePreviewPerformanceMode = gcodePreviewPerformanceMode,
                onThemeModeSelected = onThemeModeSelected,
                onAccentPaletteSelected = onAccentPaletteSelected,
                onWorldViewColorSelected = onWorldViewColorSelected,
                onShowAdvancedProfileSettingsChanged = onShowAdvancedProfileSettingsChanged,
                onActiveStylusPaintOnlyChanged = onActiveStylusPaintOnlyChanged,
                onGcodePreviewPerformanceModeSelected = onGcodePreviewPerformanceModeSelected,
                onBack = onBackNavigation,
                modifier = Modifier.padding(innerPadding)
            )

            AppScreen.Scanner -> ScannerScreen(
                onBack = { onCurrentScreenChanged(AppScreen.Home) },
                onWorkspaceImportRequested = {
                    ModelLoadResult(
                        message = "Scanner workspace import requires a selected printer, filament, and process.",
                        loaded = false
                    )
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )

            else -> ModelLoaderHomeScreen(
                importedModel = currentModelLabel,
                printerTitle = profileStore.printers.firstOrNull { it.id == profileStore.selectedPrinterId }?.name
                    ?: "No printer selected",
                filamentTitle = profileStore.filaments.firstOrNull { it.id == profileStore.selectedFilamentId }?.name
                    ?: "No filament selected",
                processTitle = profileStore.processes.firstOrNull { it.id == profileStore.selectedProcessId }?.name
                    ?: "No process selected",
                projects = savedProjects,
                importInProgress = importInProgress,
                onOpenSettings = { onCurrentScreenChanged(AppScreen.Settings) },
                onSelectModel = {
                    onMissingProfileMessage(
                        profileStore.profileRequirementMessage()
                            ?: "Select a printer, filament, and process before importing a model."
                    )
                },
                onFindAndImportModel = {
                    onMissingProfileMessage(
                        profileStore.profileRequirementMessage()
                            ?: "Select a printer, filament, and process before finding a model."
                    )
                },
                onScannerClick = {
                    onCurrentScreenChanged(AppScreen.Scanner)
                },
                onCalibrationsClick = {
                    onMissingProfileMessage(
                        profileStore.profileRequirementMessage()
                            ?: "Select a printer, filament, and process before opening calibrations."
                    )
                },
                onProfilesClick = {
                    onProfilesReturnScreenNameChanged(AppScreen.Home.name)
                    onCurrentScreenChanged(AppScreen.Profiles)
                },
                onOpenProject = {
                    onMissingProfileMessage(
                        profileStore.profileRequirementMessage()
                            ?: "Select a printer, filament, and process before opening a saved plate."
                    )
                },
                onDeleteProject = { project ->
                    val nextProjects = savedProjects.filterNot { it.id == project.id }
                        .sortedByDescending { it.updatedAtEpochMs }
                        .take(24)
                    onSavedProjectsChanged(nextProjects)
                    if (currentSavedProjectId == project.id) {
                        onCurrentSavedProjectIdChanged(null)
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        }
    }
}
