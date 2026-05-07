package com.mobileslicer.storage

import android.content.Context
import android.content.SharedPreferences
import com.mobileslicer.ui.theme.AccentPaletteOption
import com.mobileslicer.ui.theme.ThemeModeOption
import com.mobileslicer.ui.theme.WorldViewColorOption
import com.mobileslicer.viewer.GcodePreviewPerformanceMode

internal const val GCODE_MIME_TYPE = "text/x-gcode"

private const val APP_PREFERENCES = "mobile_slicer_app_preferences"
private const val KEY_THEME_MODE = "theme_mode"
private const val KEY_ACCENT_PALETTE = "accent_palette"
private const val KEY_WORLD_VIEW_COLOR = "world_view_color"
private const val KEY_SHOW_ADVANCED_PROFILE_SETTINGS = "show_advanced_profile_settings"
private const val KEY_ACTIVE_STYLUS_PAINT_ONLY = "active_stylus_paint_only"
private const val KEY_GCODE_PREVIEW_PERFORMANCE_MODE = "gcode_preview_performance_mode"

internal class AppPreferenceStore private constructor(
    private val preferences: SharedPreferences
) {
    val rawPreferences: SharedPreferences
        get() = preferences

    fun loadThemeMode(): ThemeModeOption {
        val stored = preferences.getString(KEY_THEME_MODE, ThemeModeOption.System.name)
        return ThemeModeOption.entries.firstOrNull { it.name == stored } ?: ThemeModeOption.System
    }

    fun loadAccentPalette(): AccentPaletteOption {
        val stored = preferences.getString(KEY_ACCENT_PALETTE, AccentPaletteOption.Blue.name)
        return AccentPaletteOption.entries.firstOrNull { it.name == stored } ?: AccentPaletteOption.Blue
    }

    fun loadWorldViewColor(): WorldViewColorOption {
        val stored = preferences.getString(KEY_WORLD_VIEW_COLOR, WorldViewColorOption.Slate.name)
        return WorldViewColorOption.entries.firstOrNull { it.name == stored } ?: WorldViewColorOption.Slate
    }

    fun loadShowAdvancedProfileSettings(): Boolean =
        preferences.getBoolean(KEY_SHOW_ADVANCED_PROFILE_SETTINGS, false)

    fun loadActiveStylusPaintOnly(): Boolean =
        preferences.getBoolean(KEY_ACTIVE_STYLUS_PAINT_ONLY, false)

    fun loadGcodePreviewPerformanceMode(): GcodePreviewPerformanceMode =
        GcodePreviewPerformanceMode.fromStoredName(preferences.getString(KEY_GCODE_PREVIEW_PERFORMANCE_MODE, null))

    fun storeThemeMode(themeMode: ThemeModeOption) {
        preferences.edit().putString(KEY_THEME_MODE, themeMode.name).apply()
    }

    fun storeAccentPalette(accentPalette: AccentPaletteOption) {
        preferences.edit().putString(KEY_ACCENT_PALETTE, accentPalette.name).apply()
    }

    fun storeWorldViewColor(worldViewColor: WorldViewColorOption) {
        preferences.edit().putString(KEY_WORLD_VIEW_COLOR, worldViewColor.name).apply()
    }

    fun storeShowAdvancedProfileSettings(showAdvancedProfileSettings: Boolean) {
        preferences.edit().putBoolean(KEY_SHOW_ADVANCED_PROFILE_SETTINGS, showAdvancedProfileSettings).apply()
    }

    fun storeActiveStylusPaintOnly(activeStylusPaintOnly: Boolean) {
        preferences.edit().putBoolean(KEY_ACTIVE_STYLUS_PAINT_ONLY, activeStylusPaintOnly).apply()
    }

    fun storeGcodePreviewPerformanceMode(mode: GcodePreviewPerformanceMode) {
        preferences.edit().putString(KEY_GCODE_PREVIEW_PERFORMANCE_MODE, mode.name).apply()
    }

    companion object {
        fun from(context: Context): AppPreferenceStore =
            AppPreferenceStore(context.getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE))
    }
}
