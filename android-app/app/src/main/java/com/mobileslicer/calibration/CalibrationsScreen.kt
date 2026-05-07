package com.mobileslicer.calibration

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mobileslicer.CompactWorkspaceBadge
import com.mobileslicer.appBackgroundGradient
import com.mobileslicer.appBodyColor
import com.mobileslicer.appCardColor
import com.mobileslicer.appCardColorMuted
import com.mobileslicer.appMutedColor
import com.mobileslicer.appOutlineColor
import com.mobileslicer.appTitleColor
import com.mobileslicer.profiles.ProfileStore
import com.mobileslicer.profiles.activeConfiguration
import org.json.JSONObject
import java.io.File
import java.util.Locale

@Composable
internal fun PrinterCalibrationsScreen(
    store: ProfileStore,
    onBack: () -> Unit,
    onStartCalibration: (CalibrationJob) -> Unit,
    modifier: Modifier = Modifier
) {
    val active = store.activeConfiguration()
    val titleColor = appTitleColor()
    val outlineColor = appOutlineColor()
    var pendingCalibration by remember { mutableStateOf<CalibrationType?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(appBackgroundGradient()))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
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
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Printer Calibrations",
                            style = MaterialTheme.typography.titleLarge,
                            color = titleColor,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Calibration prints for tuning the selected printer, filament, and process.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = appBodyColor()
                        )
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompactWorkspaceBadge(label = active.printer.name)
                    CompactWorkspaceBadge(label = active.filament.name)
                    CompactWorkspaceBadge(label = active.process.name)
                }
            }
            items(CalibrationType.entries) { calibration ->
                CalibrationCard(
                    calibration = calibration,
                    onStart = { pendingCalibration = calibration }
                )
            }
        }
    }

    pendingCalibration?.let { calibration ->
        CalibrationOptionsDialog(
            calibration = calibration,
            defaultOptions = defaultCalibrationOptions(calibration, active.filament.name),
            onDismiss = { pendingCalibration = null },
            onConfirm = { options ->
                pendingCalibration = null
                onStartCalibration(
                    CalibrationJob(
                        type = calibration,
                        printerName = active.printer.name,
                        filamentName = active.filament.name,
                        processName = active.process.name,
                        nozzleDiameterMm = active.printer.nozzleDiameterMm,
                        options = options
                    )
                )
            }
        )
    }
}
