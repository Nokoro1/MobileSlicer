package com.mobileslicer.workspace

import com.mobileslicer.nativebridge.NativeCutMode

internal fun WorkspaceCutMode.toNativeCutMode(): NativeCutMode = when (this) {
    WorkspaceCutMode.Groove -> NativeCutMode.Groove
    WorkspaceCutMode.Contour -> NativeCutMode.Contour
    WorkspaceCutMode.Line,
    WorkspaceCutMode.Plane -> NativeCutMode.Plane
}
