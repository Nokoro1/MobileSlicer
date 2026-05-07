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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mobileslicer.CompactWorkspaceBadge
import com.mobileslicer.R
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
internal fun CalibrationOptionsDialog(
    calibration: CalibrationType,
    defaultOptions: CalibrationOptions,
    onDismiss: () -> Unit,
    onConfirm: (CalibrationOptions) -> Unit
) {
    val stateKey = listOf(
        calibration.name,
        defaultOptions.filamentType,
        defaultOptions.startValue,
        defaultOptions.endValue,
        defaultOptions.stepValue,
        defaultOptions.method,
        defaultOptions.extruderType
    ).joinToString("|")
    var extruderType by rememberSaveable(stateKey) { mutableStateOf(defaultOptions.extruderType) }
    var method by rememberSaveable(stateKey) { mutableStateOf(defaultOptions.method) }
    var filamentType by rememberSaveable(stateKey) { mutableStateOf(defaultOptions.filamentType) }
    var flowPass by rememberSaveable(stateKey) { mutableStateOf(defaultOptions.flowPass) }
    var start by rememberSaveable(stateKey) { mutableStateOf(defaultOptions.startValue) }
    var end by rememberSaveable(stateKey) { mutableStateOf(defaultOptions.endValue) }
    var step by rememberSaveable(stateKey) { mutableStateOf(defaultOptions.stepValue) }
    var printNumbers by rememberSaveable(stateKey) { mutableStateOf(defaultOptions.printNumbers) }
    var flowRatioBaseline by rememberSaveable(stateKey) { mutableStateOf(defaultOptions.flowRatioBaseline) }
    var patternAccelerations by rememberSaveable(stateKey) { mutableStateOf(defaultOptions.patternAccelerations) }
    var patternSpeeds by rememberSaveable(stateKey) { mutableStateOf(defaultOptions.patternSpeeds) }
    var testModel by rememberSaveable(stateKey) { mutableStateOf(defaultOptions.testModel) }
    var shaperType by rememberSaveable(stateKey) { mutableStateOf(defaultOptions.shaperType) }
    val bodyColor = appBodyColor()

    fun applyPressureAdvanceDefaults(nextMethod: String = method, nextExtruderType: String = extruderType) {
        val defaults = pressureAdvanceDefaults(method = nextMethod, extruderType = nextExtruderType)
        method = defaults.method
        extruderType = defaults.extruderType
        start = defaults.startValue
        end = defaults.endValue
        step = defaults.stepValue
        printNumbers = defaults.printNumbers
        patternAccelerations = defaults.patternAccelerations
        patternSpeeds = defaults.patternSpeeds
    }

    fun applyTemperatureFilamentType(nextFilamentType: String) {
        filamentType = nextFilamentType
        val (nextStart, nextEnd) = when (nextFilamentType) {
            "ABS/ASA" -> "270" to "230"
            "PETG" -> "250" to "230"
            "PCTG" -> "280" to "240"
            "TPU" -> "240" to "210"
            "PA-CF", "PET-CF" -> "320" to "280"
            else -> "230" to "190"
        }
        start = nextStart
        end = nextEnd
        step = "5"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(calibration.title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (calibration) {
                    CalibrationType.PressureAdvance -> {
                        OptionSection("Extruder type") {
                            RadioRow("DDE", extruderType == "DDE") { applyPressureAdvanceDefaults(nextExtruderType = "DDE") }
                            RadioRow("Bowden", extruderType == "Bowden") { applyPressureAdvanceDefaults(nextExtruderType = "Bowden") }
                        }
                        OptionSection("Method") {
                            listOf("PA Tower", "PA Line", "PA Pattern").forEach { value ->
                                RadioRow(value, method == value) { applyPressureAdvanceDefaults(nextMethod = value) }
                            }
                        }
                        NumericField(start, { start = it }, "Start PA")
                        NumericField(end, { end = it }, "End PA")
                        NumericField(step, { step = it }, "PA step")
                        if (method == "PA Pattern") {
                            NumericField(
                                patternAccelerations,
                                { patternAccelerations = it },
                                "Accelerations",
                                keyboardType = KeyboardType.Text
                            )
                            NumericField(patternSpeeds, { patternSpeeds = it }, "Speeds", keyboardType = KeyboardType.Text)
                        }
                        if (method == "PA Line") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = printNumbers, onCheckedChange = { printNumbers = it })
                                Text("Print numbers", color = bodyColor)
                            }
                        } else if (printNumbers) {
                            Text("Print numbers enabled", color = bodyColor)
                        }
                    }
                    CalibrationType.TemperatureTower -> {
                        OptionSection("Filament type") {
                            listOf("PLA", "ABS/ASA", "PETG", "PCTG", "TPU", "PA-CF", "PET-CF", "Custom").forEach { value ->
                                RadioRow(value, filamentType == value) { applyTemperatureFilamentType(value) }
                            }
                        }
                        NumericField(start, { start = it }, "Start temp", "°C")
                        NumericField(end, { end = it }, "End temp", "°C")
                        NumericField(step, { step = it }, "Temp step", "°C")
                    }
                    CalibrationType.MaxVolumetricSpeed -> {
                        NumericField(start, { start = it }, "Start volumetric speed", "mm3/s")
                        NumericField(end, { end = it }, "End volumetric speed", "mm3/s")
                        NumericField(step, { step = it }, "Step", "mm3/s")
                    }
                    CalibrationType.FlowRate -> {
                        OptionSection("Calibration Type") {
                            RadioRow(FLOW_RATE_COMPLETE, flowPass == FLOW_RATE_COMPLETE) {
                                flowPass = FLOW_RATE_COMPLETE
                            }
                            RadioRow(FLOW_RATE_FINE, flowPass == FLOW_RATE_FINE) {
                                flowPass = FLOW_RATE_FINE
                            }
                        }
                        if (flowPass == FLOW_RATE_FINE) {
                            NumericField(flowRatioBaseline, { flowRatioBaseline = it }, "Flow ratio")
                            Text(
                                text = "Use a value greater than 0.0 and less than 2.0.",
                                style = MaterialTheme.typography.bodySmall,
                                color = appMutedColor()
                            )
                        }
                    }
                    CalibrationType.Retraction -> {
                        NumericField(start, { start = it }, "Start retraction", "mm")
                        NumericField(end, { end = it }, "End retraction", "mm")
                        NumericField(step, { step = it }, "Step", "mm")
                    }
                    CalibrationType.Vfa -> {
                        NumericField(start, { start = it }, "Start speed", "mm/s")
                        NumericField(end, { end = it }, "End speed", "mm/s")
                        NumericField(step, { step = it }, "Speed step", "mm/s")
                    }
                    CalibrationType.InputShapingFrequency -> {
                        OptionSection("Test model") {
                            listOf(CALIBRATION_TEST_MODEL_RINGING, CALIBRATION_TEST_MODEL_FAST).forEach { value ->
                                RadioRow(value, testModel == value) { testModel = value }
                            }
                        }
                        NumericField(start, { start = it }, "Start frequency", "Hz")
                        NumericField(end, { end = it }, "End frequency", "Hz")
                        NumericField(step, { step = it }, "Frequency step", "Hz")
                        NumericField(shaperType, { shaperType = it }, "Shaper type", keyboardType = KeyboardType.Text)
                    }
                    CalibrationType.InputShapingDamping -> {
                        OptionSection("Test model") {
                            listOf(CALIBRATION_TEST_MODEL_RINGING, CALIBRATION_TEST_MODEL_FAST).forEach { value ->
                                RadioRow(value, testModel == value) { testModel = value }
                            }
                        }
                        NumericField(start, { start = it }, "Start damping")
                        NumericField(end, { end = it }, "End damping")
                        NumericField(step, { step = it }, "Damping step")
                        NumericField(shaperType, { shaperType = it }, "Shaper type", keyboardType = KeyboardType.Text)
                    }
                    CalibrationType.Cornering -> {
                        OptionSection("Test model") {
                            listOf(
                                CALIBRATION_TEST_MODEL_RINGING,
                                CALIBRATION_TEST_MODEL_FAST,
                                CALIBRATION_TEST_MODEL_CORNERING
                            ).forEach { value ->
                                RadioRow(value, testModel == value) { testModel = value }
                            }
                        }
                        NumericField(start, { start = it }, "Start cornering speed", "mm/s")
                        NumericField(end, { end = it }, "End cornering speed", "mm/s")
                        NumericField(step, { step = it }, "Step", "mm/s")
                    }
                    CalibrationType.Tolerance -> {
                        Text("Uses the bundled tolerance test model.", color = bodyColor)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        CalibrationOptions(
                            extruderType = extruderType,
                            method = method,
                            filamentType = filamentType,
                            startValue = start,
                            endValue = end,
                            stepValue = step,
                            printNumbers = printNumbers,
                            flowPass = flowPass,
                            flowRatioBaseline = flowRatioBaseline,
                            patternAccelerations = patternAccelerations,
                            patternSpeeds = patternSpeeds,
                            testModel = testModel,
                            shaperType = shaperType
                        )
                    )
                }
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun OptionSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = appTitleColor(),
            fontWeight = FontWeight.SemiBold
        )
        content()
    }
}

@Composable
private fun RadioRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(text = label, color = appBodyColor())
    }
}

@Composable
private fun NumericField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    suffix: String? = null,
    keyboardType: KeyboardType = KeyboardType.Decimal
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(label) },
        suffix = suffix?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
    )
}
