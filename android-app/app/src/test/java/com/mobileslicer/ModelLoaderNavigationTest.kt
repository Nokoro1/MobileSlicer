package com.mobileslicer

import com.mobileslicer.workspace.AppScreen
import com.mobileslicer.workspace.WorkspaceMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelLoaderNavigationTest {
    @Test
    fun backNavigationLeavesHomeUnchanged() {
        assertEquals(
            ModelLoaderBackNavigationPlan(AppScreen.Home, WorkspaceMode.Prepare),
            planModelLoaderBackNavigation(
                currentScreen = AppScreen.Home,
                workspaceMode = WorkspaceMode.Prepare,
                profilesReturnScreenName = AppScreen.Workspace.name,
                printerBrowserReturnScreenName = AppScreen.Workspace.name
            )
        )
    }

    @Test
    fun backNavigationReturnsPreviewWorkspaceToPrepareMode() {
        assertEquals(
            ModelLoaderBackNavigationPlan(AppScreen.Workspace, WorkspaceMode.Prepare),
            planModelLoaderBackNavigation(
                currentScreen = AppScreen.Workspace,
                workspaceMode = WorkspaceMode.Preview,
                profilesReturnScreenName = AppScreen.Home.name,
                printerBrowserReturnScreenName = AppScreen.Home.name
            )
        )
    }

    @Test
    fun backNavigationLeavesPrepareWorkspaceForHome() {
        assertEquals(
            ModelLoaderBackNavigationPlan(AppScreen.Home, WorkspaceMode.Prepare),
            planModelLoaderBackNavigation(
                currentScreen = AppScreen.Workspace,
                workspaceMode = WorkspaceMode.Prepare,
                profilesReturnScreenName = AppScreen.Home.name,
                printerBrowserReturnScreenName = AppScreen.Home.name
            )
        )
    }

    @Test
    fun backNavigationUsesStoredReturnScreensWithFallbacks() {
        assertEquals(
            ModelLoaderBackNavigationPlan(AppScreen.Workspace, WorkspaceMode.Prepare),
            planModelLoaderBackNavigation(
                currentScreen = AppScreen.Profiles,
                workspaceMode = WorkspaceMode.Prepare,
                profilesReturnScreenName = AppScreen.Workspace.name,
                printerBrowserReturnScreenName = AppScreen.Home.name
            )
        )
        assertEquals(
            ModelLoaderBackNavigationPlan(AppScreen.Workspace, WorkspaceMode.Prepare),
            planModelLoaderBackNavigation(
                currentScreen = AppScreen.PrinterBrowser,
                workspaceMode = WorkspaceMode.Prepare,
                profilesReturnScreenName = AppScreen.Home.name,
                printerBrowserReturnScreenName = "MissingScreen"
            )
        )
    }

    @Test
    fun backNavigationReturnsScannerToHome() {
        assertEquals(
            ModelLoaderBackNavigationPlan(AppScreen.Home, WorkspaceMode.Prepare),
            planModelLoaderBackNavigation(
                currentScreen = AppScreen.Scanner,
                workspaceMode = WorkspaceMode.Prepare,
                profilesReturnScreenName = AppScreen.Workspace.name,
                printerBrowserReturnScreenName = AppScreen.Workspace.name
            )
        )
    }
}
