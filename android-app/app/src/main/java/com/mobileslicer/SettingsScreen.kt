package com.mobileslicer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mobileslicer.ui.theme.AccentPaletteOption
import com.mobileslicer.ui.theme.LocalAppDarkTheme
import com.mobileslicer.ui.theme.PanelBlue
import com.mobileslicer.ui.theme.PanelCyan
import com.mobileslicer.ui.theme.PanelGreen
import com.mobileslicer.ui.theme.PanelGraphite
import com.mobileslicer.ui.theme.PanelOrange
import com.mobileslicer.ui.theme.PanelRed
import com.mobileslicer.ui.theme.PanelRose
import com.mobileslicer.ui.theme.PanelYellow
import com.mobileslicer.ui.theme.ThemeModeOption
import com.mobileslicer.ui.theme.WorldViewColorOption
import com.mobileslicer.viewer.GcodePreviewPerformanceMode

@Composable
internal fun SettingsScreen(
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
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val titleColor = appTitleColor()
    val bodyColor = appBodyColor()
    val outlineColor = appOutlineColor()
    var selectedTab by rememberSaveable { mutableStateOf(SettingsTab.Appearance) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = appBackgroundGradient()
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(appCardColorMuted())
                    .border(1.dp, outlineColor, RoundedCornerShape(18.dp))
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = titleColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.titleLarge,
                    color = titleColor,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Appearance, advanced controls, project info, and support.",
                    style = MaterialTheme.typography.bodySmall,
                    color = bodyColor
                )
            }
        }
        SettingsTabStrip(
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it },
            modifier = Modifier.padding(top = 16.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(top = 14.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            when (selectedTab) {
                SettingsTab.Appearance -> AppearanceSettingsSection(
                    themeMode = themeMode,
                    accentPalette = accentPalette,
                    worldViewColor = worldViewColor,
                    onThemeModeSelected = onThemeModeSelected,
                    onAccentPaletteSelected = onAccentPaletteSelected,
                    onWorldViewColorSelected = onWorldViewColorSelected
                )
                SettingsTab.Advanced -> AdvancedSettingsSection(
                    showAdvancedProfileSettings = showAdvancedProfileSettings,
                    activeStylusPaintOnly = activeStylusPaintOnly,
                    gcodePreviewPerformanceMode = gcodePreviewPerformanceMode,
                    onShowAdvancedProfileSettingsChanged = onShowAdvancedProfileSettingsChanged,
                    onActiveStylusPaintOnlyChanged = onActiveStylusPaintOnlyChanged,
                    onGcodePreviewPerformanceModeSelected = onGcodePreviewPerformanceModeSelected
                )
                SettingsTab.Info -> InfoSettingsSection(
                    appVersion = appVersion,
                    appPackageName = appPackageName,
                    outlineColor = outlineColor
                )
                SettingsTab.Support -> SupportSettingsSection()
            }
        }
    }
}

private enum class SettingsTab(val title: String) {
    Appearance("Appearance"),
    Advanced("Advanced"),
    Info("Info"),
    Support("Support")
}

@Composable
private fun SettingsTabStrip(
    selectedTab: SettingsTab,
    onTabSelected: (SettingsTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val darkTheme = LocalAppDarkTheme.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SettingsTab.entries.forEach { tab ->
            val selected = tab == selectedTab
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = if (darkTheme) 0.14f else 0.1f)
                        } else {
                            appCardColor()
                        }
                    )
                    .border(
                        width = 1.dp,
                        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f) else appOutlineColor(),
                        shape = RoundedCornerShape(14.dp)
                    )
                    .clickable(onClick = { onTabSelected(tab) })
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tab.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) appTitleColor() else appMutedColor(),
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun AppearanceSettingsSection(
    themeMode: ThemeModeOption,
    accentPalette: AccentPaletteOption,
    worldViewColor: WorldViewColorOption,
    onThemeModeSelected: (ThemeModeOption) -> Unit,
    onAccentPaletteSelected: (AccentPaletteOption) -> Unit,
    onWorldViewColorSelected: (WorldViewColorOption) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        SettingsSectionCard(
            title = "Theme mode",
            subtitle = "Choose light, dark, or your device setting."
        ) {
            SettingsOptionRow(
                options = listOf(
                    AppSettingOption(ThemeModeOption.System, "System", "Follow the device theme."),
                    AppSettingOption(ThemeModeOption.Light, "Light", "Use a bright app theme."),
                    AppSettingOption(ThemeModeOption.Dark, "Dark", "Use a dark app theme.")
                ),
                selectedValue = themeMode,
                accentColor = selectedAccentColor(accentPalette),
                onSelected = onThemeModeSelected
            )
        }

        SettingsSectionCard(
            title = "Accent color",
            subtitle = "Choose the color used for buttons and selected controls."
        ) {
            SettingsOptionRow(
                options = accentPaletteOptions(),
                selectedValue = accentPalette,
                accentColor = selectedAccentColor(accentPalette),
                onSelected = onAccentPaletteSelected
            )
        }

        SettingsSectionCard(
            title = "Workspace background",
            subtitle = "Choose the background behind the bed and model."
        ) {
            SettingsOptionRow(
                options = worldViewColorOptions(),
                selectedValue = worldViewColor,
                accentColor = selectedWorldColor(worldViewColor),
                onSelected = onWorldViewColorSelected
            )
        }
    }
}

@Composable
private fun AdvancedSettingsSection(
    showAdvancedProfileSettings: Boolean,
    activeStylusPaintOnly: Boolean,
    gcodePreviewPerformanceMode: GcodePreviewPerformanceMode,
    onShowAdvancedProfileSettingsChanged: (Boolean) -> Unit,
    onActiveStylusPaintOnlyChanged: (Boolean) -> Unit,
    onGcodePreviewPerformanceModeSelected: (GcodePreviewPerformanceMode) -> Unit
) {
    val titleColor = appTitleColor()
    val bodyColor = appBodyColor()
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        SettingsSectionCard(
            title = "Advanced profile controls",
            subtitle = "Show less common slicer options in the profile editors."
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Checkbox(
                        checked = showAdvancedProfileSettings,
                        onCheckedChange = onShowAdvancedProfileSettingsChanged,
                        enabled = true
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Show advanced controls",
                            style = MaterialTheme.typography.titleSmall,
                            color = titleColor,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Adds detailed printer, filament, and process options for deeper tuning.",
                            style = MaterialTheme.typography.bodySmall,
                            color = bodyColor
                        )
                    }
                }
                SettingsPill(label = if (showAdvancedProfileSettings) "Shown" else "Hidden")
            }
        }

        SettingsSectionCard(
            title = "Painting input",
            subtitle = "Choose how touch and stylus input behave while painting."
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Checkbox(
                        checked = activeStylusPaintOnly,
                        onCheckedChange = onActiveStylusPaintOnlyChanged,
                        enabled = true
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Stylus paints",
                            style = MaterialTheme.typography.titleSmall,
                            color = titleColor,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "When on, an active stylus paints and fingers keep moving the camera.",
                            style = MaterialTheme.typography.bodySmall,
                            color = bodyColor
                        )
                    }
                }
                SettingsPill(label = if (activeStylusPaintOnly) "Stylus paints" else "Off by default")
            }
        }

        SettingsSectionCard(
            title = "G-code preview performance",
            subtitle = "Choose how much G-code preview detail to load at once."
        ) {
            SettingsOptionRow(
                options = GcodePreviewPerformanceMode.entries.map { mode ->
                    AppSettingOption(
                        value = mode,
                        title = "${mode.displayLabel}: ${formatPreviewVertexBudget(mode.vertexBudget)}",
                        subtitle = mode.description
                    )
                },
                selectedValue = gcodePreviewPerformanceMode,
                accentColor = MaterialTheme.colorScheme.primary,
                onSelected = onGcodePreviewPerformanceModeSelected
            )
        }
    }
}

private fun formatPreviewVertexBudget(vertexBudget: Long): String =
    when (vertexBudget) {
        1_000_000L -> "1m"
        else -> "${vertexBudget / 1_000}k"
    }

@Composable
private fun InfoSettingsSection(
    appVersion: String,
    appPackageName: String,
    outlineColor: Color
) {
    SettingsSectionCard(
        title = "Project info",
        subtitle = "Local, touch-first slicing for Android."
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LegalInfoRow(
                title = "Version",
                value = "Mobile Slicer $appVersion"
            )
            HorizontalDivider(color = outlineColor)
            LegalInfoRow(
                title = "App package",
                value = appPackageName
            )
        }
    }

    SettingsSectionCard(
        title = "Privacy and legal",
        subtitle = "Privacy, attribution, licenses, safety, and warranty."
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LegalInfoRow(
                title = "MobileSlicer",
                value = "MobileSlicer is a local Android slicer for STL and 3MF files. It is not affiliated with, sponsored by, or endorsed by the OrcaSlicer project."
            )
            HorizontalDivider(color = outlineColor)
            LegalInfoRow(
                title = "OrcaSlicer attribution",
                value = "MobileSlicer uses slicer technology derived from OrcaSlicer and related open-source slicer projects. OrcaSlicer concepts, profile behavior, and terminology are credited to their upstream authors."
            )
            HorizontalDivider(color = outlineColor)
            LegalInfoRow(
                title = "License",
                value = "OrcaSlicer is licensed under the GNU Affero General Public License version 3. MobileSlicer keeps the required notices, license text, and source-availability obligations for AGPL-covered code."
            )
            HorizontalDivider(color = outlineColor)
            LegalInfoRow(
                title = "Source code",
                value = "Public source and release license materials are available at github.com/MobileSlicerApp/MobileSlicer, including corresponding source for AGPL-covered changes."
            )
            HorizontalDivider(color = outlineColor)
            LegalInfoRow(
                title = "Third-party notices",
                value = "Android, Kotlin, Compose, native, and slicer-related dependencies keep their own licenses and notices in the release materials."
            )
            HorizontalDivider(color = outlineColor)
            LegalInfoRow(
                title = "Privacy",
                value = "MobileSlicer does not require an account or automatic cloud slicing. Files stay on your device unless you choose to export, share, or send them."
            )
            HorizontalDivider(color = outlineColor)
            LegalInfoRow(
                title = "Print safety",
                value = "Review generated G-code, printer settings, materials, and machine behavior before starting a print."
            )
            HorizontalDivider(color = outlineColor)
            LegalInfoRow(
                title = "Warranty",
                value = "MobileSlicer is provided as-is, without warranty, to the extent allowed by the open-source licenses and applicable law."
            )
        }
    }
}

@Composable
private fun SupportSettingsSection() {
    val uriHandler = LocalUriHandler.current
    val bodyColor = appBodyColor()
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        SettingsSectionCard(
            title = "Support contact",
            subtitle = "Get help, report issues, or send feedback."
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "For issues, complaints, concerns, and questions, contact us by email or Discord.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = bodyColor
                )
                SettingsContactCard(
                    eyebrow = "Email",
                    title = "mobileslicerapp@gmail.com",
                    detail = "Best for account, privacy, billing, or support questions.",
                    onClick = { uriHandler.openUri("mailto:mobileslicerapp@gmail.com") }
                )
                SettingsContactCard(
                    eyebrow = "Discord",
                    title = "Join the community",
                    detail = "Good for feature requests, printer discussions, and quick help.",
                    onClick = { uriHandler.openUri("https://discord.gg/ckAAYAhRxE") }
                )
            }
        }

        SettingsSectionCard(
            title = "Support MobileSlicer",
            subtitle = ""
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "MobileSlicer is free, open source, and ad free.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = bodyColor
                    )
                    Text(
                        text = "Paid support never unlocks app features or changes the free app experience.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = bodyColor
                    )
                    Text(
                        text = "Optional support helps with device testing, printer coverage, development equipment, and continued Android slicer work.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = bodyColor
                    )
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            uriHandler.openUri("https://ko-fi.com/mobileslicer")
                        },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.88f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Ko-fi",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(0xFF08111E),
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Support MobileSlicer",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFF08111E),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Opens ko-fi.com",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF1A3557),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Text(
                    text = "Support is optional and never unlocks app features.",
                    style = MaterialTheme.typography.bodySmall,
                    color = bodyColor
                )
            }
        }
    }
}

@Composable
private fun SettingsContactCard(
    eyebrow: String,
    title: String,
    detail: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = appCardColor())
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = eyebrow,
                style = MaterialTheme.typography.labelSmall,
                color = appMutedColor(),
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = appTitleColor(),
                fontWeight = FontWeight.Bold
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = appBodyColor()
            )
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    val titleColor = appTitleColor()
    val bodyColor = appBodyColor()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = appCardColorMuted())
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = titleColor,
                    fontWeight = FontWeight.SemiBold
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = bodyColor
                    )
                }
            }
            content()
        }
    }
}

@Composable
private fun <T> SettingsOptionRow(
    options: List<AppSettingOption<T>>,
    selectedValue: T,
    accentColor: Color,
    onSelected: (T) -> Unit
) {
    val titleColor = appTitleColor()
    val bodyColor = appBodyColor()
    val darkTheme = LocalAppDarkTheme.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        options.forEach { option ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = option.value == selectedValue,
                        onClick = { onSelected(option.value) }
                    ),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (option.value == selectedValue) {
                        MaterialTheme.colorScheme.primary.copy(alpha = if (darkTheme) 0.18f else 0.12f)
                    } else {
                        appCardColor()
                    }
                )
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(option.swatchColor ?: if (option.value == selectedValue) accentColor else Color(0xFF5D6C80))
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = option.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = titleColor,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = option.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = bodyColor
                        )
                    }
                    SettingsPill(label = if (option.value == selectedValue) "Selected" else "Use")
                }
            }
        }
    }
}

@Composable
private fun SettingsPill(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = appBodyColor(),
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(appCardColor().copy(alpha = 0.36f))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}

@Composable
private fun LegalInfoRow(
    title: String,
    value: String
) {
    val titleColor = appTitleColor()
    val bodyColor = appBodyColor()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = title, style = MaterialTheme.typography.labelLarge, color = titleColor, fontWeight = FontWeight.SemiBold)
        Text(text = value, style = MaterialTheme.typography.bodySmall, color = bodyColor)
    }
}

private fun selectedAccentColor(accentPalette: AccentPaletteOption): Color =
    when (accentPalette) {
        AccentPaletteOption.Blue -> PanelBlue
        AccentPaletteOption.Cyan -> PanelCyan
        AccentPaletteOption.Green -> PanelGreen
        AccentPaletteOption.Yellow -> PanelYellow
        AccentPaletteOption.Rose -> PanelRose
        AccentPaletteOption.Red -> PanelRed
        AccentPaletteOption.Orange -> PanelOrange
        AccentPaletteOption.Graphite -> PanelGraphite
    }

private fun accentPaletteOptions(): List<AppSettingOption<AccentPaletteOption>> =
    listOf(
        AppSettingOption(AccentPaletteOption.Blue, "Blue", "Default action color.", PanelBlue),
        AppSettingOption(AccentPaletteOption.Cyan, "Cyan", "Cool blue-green controls.", PanelCyan),
        AppSettingOption(AccentPaletteOption.Green, "Green", "Green buttons and highlights.", PanelGreen),
        AppSettingOption(AccentPaletteOption.Yellow, "Yellow", "Yellow buttons and highlights.", PanelYellow),
        AppSettingOption(AccentPaletteOption.Rose, "Rose", "Rose buttons and highlights.", PanelRose),
        AppSettingOption(AccentPaletteOption.Red, "Red", "Red buttons and highlights.", PanelRed),
        AppSettingOption(AccentPaletteOption.Orange, "Orange", "Orange buttons and highlights.", PanelOrange),
        AppSettingOption(AccentPaletteOption.Graphite, "Graphite", "Neutral gray controls.", PanelGraphite)
    )

private fun selectedWorldColor(worldViewColor: WorldViewColorOption): Color =
    when (worldViewColor) {
        WorldViewColorOption.White -> Color(0xFFF3F7FC)
        WorldViewColorOption.Mist -> Color(0xFFDCE5EE)
        WorldViewColorOption.Slate -> Color(0xFF8E9AA6)
        WorldViewColorOption.Graphite -> Color(0xFF3F4852)
        WorldViewColorOption.Deep -> Color(0xFF071426)
        WorldViewColorOption.Navy -> Color(0xFF10233A)
        WorldViewColorOption.Charcoal -> Color(0xFF171B20)
        WorldViewColorOption.Black -> Color(0xFF020407)
    }

private fun worldViewColorOptions(): List<AppSettingOption<WorldViewColorOption>> =
    listOf(
        AppSettingOption(WorldViewColorOption.Slate, "Slate", "Default gray-blue background.", selectedWorldColor(WorldViewColorOption.Slate)),
        AppSettingOption(WorldViewColorOption.White, "White", "Light background.", selectedWorldColor(WorldViewColorOption.White)),
        AppSettingOption(WorldViewColorOption.Mist, "Mist", "Pale gray-blue background.", selectedWorldColor(WorldViewColorOption.Mist)),
        AppSettingOption(WorldViewColorOption.Graphite, "Graphite", "Dark gray background.", selectedWorldColor(WorldViewColorOption.Graphite)),
        AppSettingOption(WorldViewColorOption.Deep, "Deep", "Dark blue background.", selectedWorldColor(WorldViewColorOption.Deep)),
        AppSettingOption(WorldViewColorOption.Navy, "Navy", "Navy background.", selectedWorldColor(WorldViewColorOption.Navy)),
        AppSettingOption(WorldViewColorOption.Charcoal, "Charcoal", "Charcoal background.", selectedWorldColor(WorldViewColorOption.Charcoal)),
        AppSettingOption(WorldViewColorOption.Black, "Black", "Black background.", selectedWorldColor(WorldViewColorOption.Black))
    )
