package com.mobileslicer

import com.mobileslicer.profiles.PrintHostType
import com.mobileslicer.profiles.PrinterProfile
import com.mobileslicer.profiles.ProfileStore
import com.mobileslicer.workspace.AppScreen
import com.mobileslicer.workspace.WorkspaceMode

internal fun appScreenFromName(name: String, fallback: AppScreen): AppScreen =
    runCatching { AppScreen.valueOf(name) }.getOrDefault(fallback)

internal data class ModelLoaderBackNavigationPlan(
    val screen: AppScreen,
    val workspaceMode: WorkspaceMode
)

internal fun planModelLoaderBackNavigation(
    currentScreen: AppScreen,
    workspaceMode: WorkspaceMode,
    profilesReturnScreenName: String,
    printerBrowserReturnScreenName: String
): ModelLoaderBackNavigationPlan =
    when (currentScreen) {
        AppScreen.Home -> ModelLoaderBackNavigationPlan(AppScreen.Home, workspaceMode)
        AppScreen.ModelSearch -> ModelLoaderBackNavigationPlan(AppScreen.Home, workspaceMode)
        AppScreen.Scanner -> ModelLoaderBackNavigationPlan(AppScreen.Home, workspaceMode)
        AppScreen.Workspace -> {
            if (workspaceMode == WorkspaceMode.Preview) {
                ModelLoaderBackNavigationPlan(AppScreen.Workspace, WorkspaceMode.Prepare)
            } else {
                ModelLoaderBackNavigationPlan(AppScreen.Home, workspaceMode)
            }
        }
        AppScreen.Profiles -> ModelLoaderBackNavigationPlan(
            screen = appScreenFromName(profilesReturnScreenName, AppScreen.Home),
            workspaceMode = workspaceMode
        )
        AppScreen.Calibrations -> ModelLoaderBackNavigationPlan(AppScreen.Home, workspaceMode)
        AppScreen.PrinterBrowser -> ModelLoaderBackNavigationPlan(
            screen = appScreenFromName(printerBrowserReturnScreenName, AppScreen.Workspace),
            workspaceMode = workspaceMode
        )
        AppScreen.Settings -> ModelLoaderBackNavigationPlan(AppScreen.Home, workspaceMode)
    }

internal fun ProfileStore.hasCompleteProfileSelection(): Boolean =
    printers.any { it.id == selectedPrinterId } &&
        filaments.any {
            it.id == selectedFilamentId &&
                it.printerProfileId == selectedPrinterId
        } &&
        processes.any {
            it.id == selectedProcessId &&
                it.printerProfileId == selectedPrinterId
        }

internal fun printerWebUiUrl(printerProfile: PrinterProfile): String? {
    val rawHost = printerProfile.printHostWebUi
        .ifBlank { printerProfile.printHost }
        .ifBlank {
            if (printerProfile.printHostType == PrintHostType.SimplyPrint) {
                "https://simplyprint.io/panel"
            } else {
                ""
            }
        }
        .trim()
    if (rawHost.isBlank()) return null
    return normalizePrinterWebUiUrl(rawHost)
}
