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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mobileslicer.appBodyColor
import com.mobileslicer.appCardColorMuted
import com.mobileslicer.appOutlineColor
import com.mobileslicer.appTitleColor
import com.mobileslicer.viewer.GcodePreviewDisplayMode
import java.util.Locale

private enum class PreviewInfoTab {
    Display,
    LineType,
    Filament,
    Time,
    Cost
}

internal fun previewLineVisibilityKey(row: PreviewLineTypeRow): String = "${row.kind.name}:${row.nativeId}"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlateObjectListSheet(
    plateObjects: List<PlateObject>,
    filamentSlots: List<PlateFilamentSlot>,
    workspacePlates: List<WorkspacePlate>,
    activePlateId: Long,
    selectedPlateObjectId: Long?,
    onObjectSelected: (Long) -> Unit,
    onMoveObjectToPlate: (Long, Long) -> Unit,
    onMoveObjectToNewPlate: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val slotsByIndex = filamentSlots.associateBy { it.index }
    val targetPlates = workspacePlates.filterNot { it.id == activePlateId }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.52f)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Objects",
                style = MaterialTheme.typography.titleMedium,
                color = appTitleColor(),
                fontWeight = FontWeight.SemiBold
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(plateObjects, key = { it.id }) { objectOnPlate ->
                    val slot = slotsByIndex[objectOnPlate.filamentSlotIndex]
                    val selected = objectOnPlate.id == selectedPlateObjectId
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onObjectSelected(objectOnPlate.id) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
                        } else {
                            appCardColorMuted().copy(alpha = 0.72f)
                        },
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else appOutlineColor().copy(alpha = 0.46f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(slot?.colorHex?.let(::slotColor) ?: MaterialTheme.colorScheme.primary)
                                    .border(1.dp, appOutlineColor().copy(alpha = 0.62f), RoundedCornerShape(5.dp))
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = objectOnPlate.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = appTitleColor(),
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Slot ${objectOnPlate.filamentSlotIndex} • ${slot?.materialType?.ifBlank { slot.label } ?: "Filament"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = appBodyColor(),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = { onMoveObjectToNewPlate(objectOnPlate.id) },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                    ) {
                                        Text(
                                            text = "Move to new plate",
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1
                                        )
                                    }
                                    targetPlates.forEachIndexed { index, plate ->
                                        TextButton(
                                            onClick = { onMoveObjectToPlate(objectOnPlate.id, plate.id) },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                        ) {
                                            Text(
                                                text = "Move to ${plate.shortPlateMoveLabel(index)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                            if (selected) {
                                Text(
                                    text = "Selected",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun WorkspacePlate.shortPlateMoveLabel(fallbackIndex: Int): String =
    label.ifBlank { defaultWorkspacePlateLabel(fallbackIndex + 1) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkspacePlateSheet(
    plateLabel: String,
    plates: List<WorkspacePlate>,
    activePlateId: Long,
    objectCount: Int,
    selectedObjectId: Long?,
    selectedObjectLabel: String?,
    onDismiss: () -> Unit,
    onPlateSelected: (Long) -> Unit,
    onAddPlate: () -> Unit,
    onDuplicateActivePlate: () -> Unit,
    onDeleteActivePlate: () -> Unit,
    onRenameActivePlate: (String) -> Unit,
    onMoveObjectToPlate: (Long, Long) -> Unit,
    onMoveObjectToNewPlate: (Long) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val activePlate = plates.firstOrNull { it.id == activePlateId }
    var draftName by remember(activePlateId, activePlate?.label) {
        mutableStateOf(activePlate?.label ?: plateLabel)
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = plateLabel,
                style = MaterialTheme.typography.titleMedium,
                color = appTitleColor(),
                fontWeight = FontWeight.SemiBold
            )
            OutlinedTextField(
                value = draftName,
                onValueChange = { value ->
                    draftName = value
                    onRenameActivePlate(value)
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Plate name") }
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = appCardColorMuted().copy(alpha = 0.72f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    appOutlineColor().copy(alpha = 0.46f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PlateMetadataRow(label = "Objects", value = objectCount.toObjectCountLabel())
                    selectedObjectLabel?.takeIf { it.isNotBlank() }?.let { label ->
                        PlateMetadataRow(label = "Selected", value = label)
                    }
                }
            }
            if (selectedObjectId != null && selectedObjectLabel?.isNotBlank() == true) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = appCardColorMuted().copy(alpha = 0.60f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        appOutlineColor().copy(alpha = 0.42f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Move Selected Object",
                            style = MaterialTheme.typography.labelMedium,
                            color = appBodyColor(),
                            maxLines = 1
                        )
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { onMoveObjectToNewPlate(selectedObjectId) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.size(width = 88.dp, height = 32.dp)
                            ) {
                                Text(
                                    text = "New plate",
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1
                                )
                            }
                            plates.filterNot { it.id == activePlateId }.forEachIndexed { index, plate ->
                                TextButton(
                                    onClick = { onMoveObjectToPlate(selectedObjectId, plate.id) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    modifier = Modifier.size(width = 88.dp, height = 32.dp)
                                ) {
                                    Text(
                                        text = plate.shortPlateMoveLabel(index),
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.36f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(plates, key = { it.id }) { plate ->
                    val selected = plate.id == activePlateId
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !selected) { onPlateSelected(plate.id) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
                        } else {
                            appCardColorMuted().copy(alpha = 0.72f)
                        },
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else appOutlineColor().copy(alpha = 0.46f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = plate.label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = appTitleColor(),
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (plate.objectCount == 0) "Empty" else plate.objectCount.toObjectCountLabel(),
                                style = MaterialTheme.typography.labelMedium,
                                color = appBodyColor(),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onAddPlate,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Add")
                }
                Button(
                    onClick = onDuplicateActivePlate,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Duplicate")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onDeleteActivePlate,
                    enabled = plates.size > 1,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                        disabledContainerColor = appCardColorMuted(),
                        disabledContentColor = appBodyColor()
                    )
                ) {
                    Text("Delete")
                }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("Done")
                }
            }
        }
    }
}

@Composable
private fun PlateMetadataRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = appBodyColor(),
            modifier = Modifier.weight(0.36f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = appTitleColor(),
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.64f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun Int.toObjectCountLabel(): String = "$this ${if (this == 1) "object" else "objects"}"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PreviewInfoSheet(
    summary: SliceResultSummary,
    lineVisibility: Map<String, Boolean>,
    displayMode: GcodePreviewDisplayMode,
    onDisplayModeChanged: (GcodePreviewDisplayMode) -> Unit,
    onLineVisibilityChanged: (PreviewLineTypeRow, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val info = summary.previewInfo
    var selectedTab by remember { mutableStateOf(PreviewInfoTab.LineType) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.78f)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Preview info",
                    style = MaterialTheme.typography.titleMedium,
                    color = appTitleColor(),
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "G-code stats",
                    style = MaterialTheme.typography.labelMedium,
                    color = appBodyColor()
                )
            }
            PreviewInfoTabs(selectedTab = selectedTab, onSelected = { selectedTab = it })
            when (selectedTab) {
                PreviewInfoTab.Display -> PreviewDisplayModePanel(
                    selectedMode = displayMode,
                    onModeSelected = onDisplayModeChanged
                )
                PreviewInfoTab.LineType -> PreviewLineTypePanel(
                    rows = info.lineTypes,
                    visibility = lineVisibility,
                    onVisibilityChanged = onLineVisibilityChanged
                )
                PreviewInfoTab.Filament -> PreviewFilamentPanel(info = info)
                PreviewInfoTab.Time -> PreviewTimePanel(info = info, summary = summary)
                PreviewInfoTab.Cost -> PreviewCostPanel(info = info)
            }
        }
    }
}

@Composable
private fun PreviewInfoTabs(
    selectedTab: PreviewInfoTab,
    onSelected: (PreviewInfoTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PreviewInfoTab.values().forEach { tab ->
            val selected = selectedTab == tab
            Button(
                onClick = { onSelected(tab) },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selected) MaterialTheme.colorScheme.primary else appCardColorMuted(),
                    contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else appTitleColor()
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = when (tab) {
                        PreviewInfoTab.Display -> "Display"
                        PreviewInfoTab.LineType -> "Line Type"
                        PreviewInfoTab.Filament -> "Filament"
                        PreviewInfoTab.Time -> "Time"
                        PreviewInfoTab.Cost -> "Cost"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun PreviewDisplayModePanel(
    selectedMode: GcodePreviewDisplayMode,
    onModeSelected: (GcodePreviewDisplayMode) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 20.dp)
    ) {
        items(GcodePreviewDisplayMode.values().toList()) { mode ->
            val selected = selectedMode == mode
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onModeSelected(mode) },
                shape = RoundedCornerShape(12.dp),
                color = if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
                } else {
                    appCardColorMuted().copy(alpha = 0.72f)
                },
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (selected) MaterialTheme.colorScheme.primary else appOutlineColor().copy(alpha = 0.46f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = mode.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = appTitleColor(),
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                    )
                    if (selected) {
                        Text(
                            text = "Active",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewLineTypePanel(
    rows: List<PreviewLineTypeRow>,
    visibility: Map<String, Boolean>,
    onVisibilityChanged: (PreviewLineTypeRow, Boolean) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(7.dp),
        contentPadding = PaddingValues(bottom = 20.dp)
    ) {
        item {
            PreviewInfoHeaderRow(labels = listOf("Line", "Time", "%", "Usage", ""))
        }
        items(rows) { row ->
            val visible = visibility[previewLineVisibilityKey(row)] ?: row.defaultVisible
            PreviewLineTypeItem(
                row = row,
                visible = visible,
                onToggle = { onVisibilityChanged(row, !visible) }
            )
        }
    }
}

@Composable
private fun PreviewLineTypeItem(
    row: PreviewLineTypeRow,
    visible: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(appCardColorMuted().copy(alpha = 0.72f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1.45f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(13.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(slotColor(row.colorHex))
                    .border(1.dp, appOutlineColor().copy(alpha = 0.6f), RoundedCornerShape(3.dp))
            )
            Text(
                text = row.label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (visible) appTitleColor() else appBodyColor().copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = formatPreviewSeconds(row.timeSeconds),
            modifier = Modifier.weight(0.8f),
            style = MaterialTheme.typography.bodyMedium,
            color = appTitleColor(),
            maxLines = 1
        )
        Text(
            text = formatPreviewPercent(row.percent),
            modifier = Modifier.weight(0.55f),
            style = MaterialTheme.typography.bodyMedium,
            color = appBodyColor(),
            maxLines = 1
        )
        Text(
            text = if (row.usageMeters > 0.0 || row.usageGrams > 0.0) {
                "${formatPreviewMeters(row.usageMeters)} ${formatPreviewGrams(row.usageGrams)}"
            } else {
                "-"
            },
            modifier = Modifier.weight(1.05f),
            style = MaterialTheme.typography.bodyMedium,
            color = appBodyColor(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        TextButton(
            onClick = onToggle,
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
        ) {
            Text(if (visible) "Hide" else "Show")
        }
    }
}

@Composable
private fun PreviewFilamentPanel(info: PreviewInfoSummary) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 20.dp)
    ) {
        items(info.filaments) { filament ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(appCardColorMuted().copy(alpha = 0.76f))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(slotColor(filament.colorHex))
                            .border(1.dp, appOutlineColor(), RoundedCornerShape(4.dp))
                    )
                    Text(
                        text = "${filament.slotIndex}. ${filament.label}",
                        style = MaterialTheme.typography.titleSmall,
                        color = appTitleColor(),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                PreviewInfoMetricGrid(
                    metrics = listOf(
                        "Model" to "${formatPreviewMeters(filament.modelMeters)} ${formatPreviewGrams(filament.modelGrams)}",
                        "Support" to "${formatPreviewMeters(filament.supportMeters)} ${formatPreviewGrams(filament.supportGrams)}",
                        "Flushed" to "${formatPreviewMeters(filament.flushedMeters)} ${formatPreviewGrams(filament.flushedGrams)}",
                        "Tower" to "${formatPreviewMeters(filament.towerMeters)} ${formatPreviewGrams(filament.towerGrams)}",
                        "Total" to "${formatPreviewMeters(filament.totalMeters)} ${formatPreviewGrams(filament.totalGrams)}",
                        "Cost" to formatPreviewCost(filament.cost)
                    )
                )
            }
        }
    }
}

@Composable
private fun PreviewTimePanel(info: PreviewInfoSummary, summary: SliceResultSummary) {
    val topRows = info.lineTypes.sortedByDescending { it.timeSeconds }.take(6)
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 20.dp)
    ) {
        item {
            PreviewInfoMetricGrid(
                metrics = listOf(
                    "Total" to (info.totalSeconds?.let(::formatPreviewSeconds) ?: summary.estimatedPrintTimeLabel()),
                    "Model" to (info.modelSeconds?.let(::formatPreviewSeconds) ?: "-"),
                    "Prepare" to (info.prepareSeconds?.let(::formatPreviewSeconds) ?: "-"),
                    "Filament changes" to info.filamentChanges.toString(),
                    "Extruder changes" to info.extruderChanges.toString(),
                    "Layers" to summary.layerChangeCount.toString()
                )
            )
        }
        if (topRows.isNotEmpty()) {
            item {
                Text(
                    text = "Largest time contributors",
                    style = MaterialTheme.typography.labelLarge,
                    color = appTitleColor(),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        items(topRows) { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(row.label, color = appTitleColor(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${formatPreviewSeconds(row.timeSeconds)} • ${formatPreviewPercent(row.percent)}", color = appBodyColor())
            }
        }
    }
}

@Composable
private fun PreviewCostPanel(info: PreviewInfoSummary) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 20.dp)
    ) {
        item {
            PreviewInfoMetricGrid(
                metrics = listOf(
                    "Total cost" to formatPreviewCost(info.totalCost ?: 0.0),
                    "Filament" to "${formatPreviewMeters(info.filaments.sumOf { it.totalMeters })} ${formatPreviewGrams(info.filaments.sumOf { it.totalGrams })}",
                    "Flushed" to formatPreviewGrams(info.filaments.sumOf { it.flushedGrams }),
                    "Prime tower" to formatPreviewGrams(info.filaments.sumOf { it.towerGrams })
                )
            )
        }
        items(info.filaments) { filament ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(appCardColorMuted().copy(alpha = 0.72f))
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = filament.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = appTitleColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(formatPreviewCost(filament.cost), color = appBodyColor())
            }
        }
    }
}

@Composable
private fun PreviewInfoHeaderRow(labels: List<String>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        labels.forEachIndexed { index, label ->
            Text(
                text = label,
                modifier = Modifier.weight(
                    when (index) {
                        0 -> 1.45f
                        1 -> 0.8f
                        2 -> 0.55f
                        3 -> 1.05f
                        else -> 0.62f
                    }
                ),
                style = MaterialTheme.typography.labelMedium,
                color = appBodyColor(),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun PreviewInfoMetricGrid(metrics: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        metrics.chunked(2).forEach { rowMetrics ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowMetrics.forEach { (label, value) ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(appCardColorMuted().copy(alpha = 0.7f))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text(label, style = MaterialTheme.typography.labelMedium, color = appBodyColor())
                        Text(
                            value,
                            style = MaterialTheme.typography.bodyMedium,
                            color = appTitleColor(),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (rowMetrics.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private fun formatPreviewSeconds(seconds: Double): String {
    if (seconds < 1.0) return "<1s"
    val totalSeconds = seconds.toLong()
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val secs = totalSeconds % 60L
    return when {
        hours > 0L -> "${hours}h ${minutes}m"
        minutes > 0L -> "${minutes}m ${secs}s"
        else -> "${secs}s"
    }
}

private fun formatPreviewPercent(value: Double): String =
    if (value < 0.05) "<0.1" else String.format(Locale.US, "%.1f", value)

private fun formatPreviewMeters(value: Double): String =
    String.format(Locale.US, "%.2f m", value.coerceAtLeast(0.0))

private fun formatPreviewGrams(value: Double): String =
    String.format(Locale.US, "%.2f g", value.coerceAtLeast(0.0))

private fun formatPreviewCost(value: Double): String =
    if (value <= 0.0) "-" else String.format(Locale.US, "%.2f", value)
