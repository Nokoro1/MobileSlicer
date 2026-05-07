package com.mobileslicer.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mobileslicer.appCardColor
import com.mobileslicer.appCardColorMuted
import com.mobileslicer.appOutlineColor
import com.mobileslicer.appTitleColor
import com.mobileslicer.profiles.FilamentProfile
import com.mobileslicer.profiles.PrinterProfile
import java.util.Locale

@Composable
internal fun WorkspaceFilamentStrip(
    slots: List<PlateFilamentSlot>,
    selectedSlotIndex: Int,
    onSlotClick: (Int) -> Unit,
    onAddSlot: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        slots.sortedBy { it.index }.forEach { slot ->
            FilamentSlotPill(
                slot = slot,
                selected = slot.index == selectedSlotIndex,
                onClick = { onSlotClick(slot.index) }
            )
        }
        Button(
            onClick = onAddSlot,
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Text("+")
        }
    }
}

@Composable
private fun FilamentSlotPill(
    slot: PlateFilamentSlot,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        modifier = Modifier
            .height(48.dp)
            .clickable(onClick = onClick),
        shape = shape,
        color = if (selected) appCardColor().copy(alpha = 0.92f) else appCardColorMuted().copy(alpha = 0.86f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else appOutlineColor().copy(alpha = 0.52f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = slot.index.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = appTitleColor(),
                fontWeight = FontWeight.SemiBold
            )
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(slotColor(slot.colorHex))
                    .border(1.dp, appOutlineColor().copy(alpha = 0.48f), RoundedCornerShape(5.dp))
            )
            Text(
                text = slot.label.ifBlank { slot.materialType.ifBlank { "Filament" } },
                style = MaterialTheme.typography.labelMedium,
                color = appTitleColor(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            slot.physicalNozzleIndex?.let { nozzleIndex ->
                Text(
                    text = "N$nozzleIndex",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FilamentSlotSheet(
    slot: PlateFilamentSlot,
    selectedObjectLabel: String?,
    availableFilaments: List<FilamentProfile>,
    physicalNozzleCount: Int,
    modifier: Modifier = Modifier,
    landscapeLayout: Boolean = false,
    onAssignToSelected: () -> Unit,
    onColorSelected: (String) -> Unit,
    onFilamentSelected: (FilamentProfile) -> Unit,
    onNozzleSelected: (Int?) -> Unit,
    onRemoveSlot: () -> Unit,
    onDismiss: () -> Unit
) {
    var sheetPage by remember(slot.index) { mutableStateOf(FilamentSheetPage.Main) }
    var customHex by remember(slot.index) { mutableStateOf(slot.colorHex.normalizeColorHexInput() ?: slot.colorHex.ifBlank { "#8FC1FF" }) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colorChoices = remember { filamentColorChoices() }
    if (landscapeLayout) {
        Surface(
            modifier = modifier
                .widthIn(min = 420.dp, max = 620.dp)
                .fillMaxHeight(0.86f)
                .clickable(onClick = {}),
            shape = RoundedCornerShape(22.dp),
            color = appCardColor().copy(alpha = 0.96f),
            tonalElevation = 0.dp
        ) {
            FilamentSlotSheetContent(
                sheetPage = sheetPage,
                slot = slot,
                selectedObjectLabel = selectedObjectLabel,
                colorChoices = colorChoices,
                physicalNozzleCount = physicalNozzleCount,
                availableFilaments = availableFilaments,
                onAssignToSelected = onAssignToSelected,
                onColorSelected = onColorSelected,
                onNozzleSelected = onNozzleSelected,
                onRemoveSlot = onRemoveSlot,
                onProfilePageRequested = { sheetPage = FilamentSheetPage.Profile },
                onCustomColorPageRequested = { sheetPage = FilamentSheetPage.CustomColor },
                onProfileBack = { sheetPage = FilamentSheetPage.Main },
                onFilamentSelected = onFilamentSelected,
                customHex = customHex,
                onCustomHexChanged = { customHex = it },
                onCustomColorBack = { sheetPage = FilamentSheetPage.Main },
                onCustomColorSelected = {
                    customHex.normalizeColorHexInput()?.let(onColorSelected)
                    sheetPage = FilamentSheetPage.Main
                },
                heightFraction = 1f
            )
        }
        return
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        FilamentSlotSheetContent(
            sheetPage = sheetPage,
            slot = slot,
            selectedObjectLabel = selectedObjectLabel,
            colorChoices = colorChoices,
            physicalNozzleCount = physicalNozzleCount,
            availableFilaments = availableFilaments,
            onAssignToSelected = onAssignToSelected,
            onColorSelected = onColorSelected,
            onNozzleSelected = onNozzleSelected,
            onRemoveSlot = onRemoveSlot,
            onProfilePageRequested = { sheetPage = FilamentSheetPage.Profile },
            onCustomColorPageRequested = { sheetPage = FilamentSheetPage.CustomColor },
            onProfileBack = { sheetPage = FilamentSheetPage.Main },
            onFilamentSelected = onFilamentSelected,
            customHex = customHex,
            onCustomHexChanged = { customHex = it },
            onCustomColorBack = { sheetPage = FilamentSheetPage.Main },
            onCustomColorSelected = {
                customHex.normalizeColorHexInput()?.let(onColorSelected)
                sheetPage = FilamentSheetPage.Main
            },
            heightFraction = 0.56f
        )
    }
}

@Composable
private fun FilamentSlotSheetContent(
    sheetPage: FilamentSheetPage,
    slot: PlateFilamentSlot,
    selectedObjectLabel: String?,
    colorChoices: List<FilamentColorChoice>,
    physicalNozzleCount: Int,
    availableFilaments: List<FilamentProfile>,
    onAssignToSelected: () -> Unit,
    onColorSelected: (String) -> Unit,
    onNozzleSelected: (Int?) -> Unit,
    onRemoveSlot: () -> Unit,
    onProfilePageRequested: () -> Unit,
    onCustomColorPageRequested: () -> Unit,
    onProfileBack: () -> Unit,
    onFilamentSelected: (FilamentProfile) -> Unit,
    customHex: String,
    onCustomHexChanged: (String) -> Unit,
    onCustomColorBack: () -> Unit,
    onCustomColorSelected: () -> Unit,
    heightFraction: Float
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(heightFraction)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        when (sheetPage) {
            FilamentSheetPage.Main -> FilamentSlotMainPage(
                slot = slot,
                selectedObjectLabel = selectedObjectLabel,
                colorChoices = colorChoices,
                physicalNozzleCount = physicalNozzleCount,
                onAssignToSelected = onAssignToSelected,
                onColorSelected = onColorSelected,
                onNozzleSelected = onNozzleSelected,
                onRemoveSlot = onRemoveSlot,
                onProfilePageRequested = onProfilePageRequested,
                onCustomColorPageRequested = onCustomColorPageRequested
            )
            FilamentSheetPage.Profile -> FilamentSlotProfilePage(
                availableFilaments = availableFilaments,
                onBack = onProfileBack,
                onFilamentSelected = onFilamentSelected
            )
            FilamentSheetPage.CustomColor -> FilamentSlotCustomColorPage(
                hexValue = customHex,
                onHexValueChanged = onCustomHexChanged,
                onBack = onCustomColorBack,
                onColorSelected = onCustomColorSelected
            )
        }
    }
}

@Composable
private fun FilamentSlotMainPage(
    slot: PlateFilamentSlot,
    selectedObjectLabel: String?,
    colorChoices: List<FilamentColorChoice>,
    physicalNozzleCount: Int,
    onAssignToSelected: () -> Unit,
    onColorSelected: (String) -> Unit,
    onNozzleSelected: (Int?) -> Unit,
    onRemoveSlot: () -> Unit,
    onProfilePageRequested: () -> Unit,
    onCustomColorPageRequested: () -> Unit
) {
    Text(
        text = "Filament ${slot.index}: ${slot.materialType.ifBlank { slot.label.ifBlank { "Filament" } }}",
        style = MaterialTheme.typography.titleMedium,
        color = appTitleColor(),
        fontWeight = FontWeight.SemiBold
    )
    Button(
        onClick = onAssignToSelected,
        enabled = selectedObjectLabel != null,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(selectedObjectLabel?.let { "Assign to $it" } ?: "No object selected")
    }
    Text(
        text = "Profile",
        style = MaterialTheme.typography.labelLarge,
        color = appTitleColor(),
        fontWeight = FontWeight.SemiBold
    )
    Button(
        onClick = onProfilePageRequested,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = appCardColorMuted(),
            contentColor = appTitleColor()
        )
    ) {
        Text(
            text = slot.label.ifBlank { "Select profile" },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
    Text(
        text = "Color",
        style = MaterialTheme.typography.labelLarge,
        color = appTitleColor(),
        fontWeight = FontWeight.SemiBold
    )
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        colorChoices.forEach { choice ->
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(slotColor(choice.hex))
                    .border(
                        2.dp,
                        if (slot.colorHex.equals(choice.hex, ignoreCase = true)) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            appOutlineColor()
                        },
                        RoundedCornerShape(12.dp)
                    )
                    .clickable { onColorSelected(choice.hex) }
            )
        }
    }
    TextButton(
        onClick = onCustomColorPageRequested,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Custom hex color")
    }
    if (physicalNozzleCount > 1) {
        Text(
            text = "Nozzle",
            style = MaterialTheme.typography.labelLarge,
            color = appTitleColor(),
            fontWeight = FontWeight.SemiBold
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilamentSlotNozzleButton(
                label = "Auto",
                selected = slot.physicalNozzleIndex == null,
                onClick = { onNozzleSelected(null) }
            )
            repeat(physicalNozzleCount) { nozzleOffset ->
                val nozzleIndex = nozzleOffset + 1
                FilamentSlotNozzleButton(
                    label = "N$nozzleIndex",
                    selected = slot.physicalNozzleIndex == nozzleIndex,
                    onClick = { onNozzleSelected(nozzleIndex) }
                )
            }
        }
    }
    if (slot.index > 1) {
        TextButton(
            onClick = onRemoveSlot,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Delete filament ${slot.index}")
        }
    }
}

@Composable
private fun FilamentSlotProfilePage(
    availableFilaments: List<FilamentProfile>,
    onBack: () -> Unit,
    onFilamentSelected: (FilamentProfile) -> Unit
) {
    TextButton(onClick = onBack) {
        Text("Back")
    }
    Text(
        text = "Select profile",
        style = MaterialTheme.typography.titleMedium,
        color = appTitleColor(),
        fontWeight = FontWeight.SemiBold
    )
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 20.dp)
    ) {
        items(availableFilaments) { filament ->
            Button(
                onClick = { onFilamentSelected(filament) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = appCardColorMuted(),
                    contentColor = appTitleColor()
                )
            ) {
                Text(
                    text = listOf(filament.name, filament.materialType)
                        .filter { it.isNotBlank() }
                        .distinct()
                        .joinToString(" • "),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun FilamentSlotCustomColorPage(
    hexValue: String,
    onHexValueChanged: (String) -> Unit,
    onBack: () -> Unit,
    onColorSelected: () -> Unit
) {
    val normalizedHex = hexValue.normalizeColorHexInput()
    TextButton(onClick = onBack) {
        Text("Back")
    }
    Text(
        text = "Custom color",
        style = MaterialTheme.typography.titleMedium,
        color = appTitleColor(),
        fontWeight = FontWeight.SemiBold
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(slotColor(normalizedHex ?: "#8FC1FF"))
    )
    OutlinedTextField(
        value = hexValue,
        onValueChange = { value ->
            onHexValueChanged(value.take(7).uppercase(Locale.US))
        },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("Hex color") },
        placeholder = { Text("#FF8A00") },
        isError = normalizedHex == null,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
    )
    Button(
        onClick = onColorSelected,
        enabled = normalizedHex != null,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text("Use custom color")
    }
}

@Composable
private fun FilamentSlotNozzleButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary else appCardColorMuted(),
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else appTitleColor()
        )
    ) {
        Text(label)
    }
}

internal fun slotColor(colorHex: String): Color =
    runCatching {
        Color(android.graphics.Color.parseColor(colorHex.ifBlank { "#8FC1FF" }))
    }.getOrDefault(Color(0xFF8FC1FF))

internal fun PrinterProfile.physicalNozzleCount(): Int {
    fun parseNozzleValue(value: Any?): Int = when (value) {
        is org.json.JSONArray -> value.length()
        is String -> value.split(',', ';').map { it.trim() }.count { it.isNotBlank() }
        else -> 0
    }
    val resolvedCount = runCatching {
        org.json.JSONObject(orcaResolvedMachineJson).opt("nozzle_diameter")
    }.getOrNull().let(::parseNozzleValue)
    if (resolvedCount > 0) return resolvedCount
    val modelCount = runCatching {
        org.json.JSONObject(orcaMachineModelJson).opt("nozzle_diameter")
    }.getOrNull().let(::parseNozzleValue)
    if (modelCount > 0) return modelCount
    return extrudersCount.toIntOrNull()?.takeIf { it > 0 } ?: 1
}

private enum class FilamentSheetPage {
    Main,
    Profile,
    CustomColor
}

private data class FilamentColorChoice(
    val name: String,
    val hex: String
)

private fun filamentColorChoices(): List<FilamentColorChoice> = listOf(
    FilamentColorChoice("White", "#FFFFFF"),
    FilamentColorChoice("Natural", "#F6F1E4"),
    FilamentColorChoice("Light Gray", "#B8BEC6"),
    FilamentColorChoice("Gray", "#7A828C"),
    FilamentColorChoice("Dark Gray", "#3B424A"),
    FilamentColorChoice("Black", "#1E252D"),
    FilamentColorChoice("Brown", "#8B5E47"),
    FilamentColorChoice("Tan", "#D3A46F"),
    FilamentColorChoice("Red", "#E53935"),
    FilamentColorChoice("Coral", "#FF6F61"),
    FilamentColorChoice("Orange", "#FF8A00"),
    FilamentColorChoice("Gold", "#D6A100"),
    FilamentColorChoice("Yellow", "#FFD21F"),
    FilamentColorChoice("Lime", "#A6D608"),
    FilamentColorChoice("Green", "#2EAD4F"),
    FilamentColorChoice("Forest", "#087A45"),
    FilamentColorChoice("Mint", "#35D6A6"),
    FilamentColorChoice("Teal", "#009688"),
    FilamentColorChoice("Cyan", "#00BCD4"),
    FilamentColorChoice("Sky Blue", "#22A7F2"),
    FilamentColorChoice("Blue", "#2D6CDF"),
    FilamentColorChoice("Navy", "#233A8B"),
    FilamentColorChoice("Indigo", "#4452B8"),
    FilamentColorChoice("Purple", "#9C27B0"),
    FilamentColorChoice("Magenta", "#D81B60"),
    FilamentColorChoice("Pink", "#FF77B7"),
    FilamentColorChoice("Maroon", "#8E2430"),
    FilamentColorChoice("Copper", "#B87333"),
    FilamentColorChoice("Bronze", "#8C6A3D"),
    FilamentColorChoice("Silver", "#C8CED6"),
    FilamentColorChoice("Steel", "#8A96A3"),
    FilamentColorChoice("Translucent", "#DDEEFF")
)

private fun String.normalizeColorHexInput(): String? {
    val trimmed = trim()
    val candidate = if (trimmed.startsWith("#")) trimmed else "#$trimmed"
    return if (Regex("^#[0-9A-Fa-f]{6}$").matches(candidate)) {
        candidate.uppercase(Locale.US)
    } else {
        null
    }
}
