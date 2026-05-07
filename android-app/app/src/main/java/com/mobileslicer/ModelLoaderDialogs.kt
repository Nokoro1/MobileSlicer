package com.mobileslicer

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.mobileslicer.calibration.CalibrationJob
import com.mobileslicer.calibration.resultApplySpec
import com.mobileslicer.profiles.ActiveSlicerConfiguration
import com.mobileslicer.workspace.SliceResult

@Composable
internal fun ModelLoaderMissingProfileDialog(
    message: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        },
        title = { Text("Profiles Required") },
        text = { Text(message) }
    )
}

@Composable
internal fun ModelLoaderPrinterUploadDialog(
    message: String,
    canRetry: Boolean,
    onDismiss: () -> Unit,
    onRetry: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        },
        dismissButton = if (canRetry) {
            {
                TextButton(onClick = onRetry) {
                    Text("Retry")
                }
            }
        } else {
            null
        },
        title = { Text("Printer Upload") },
        text = { Text(message) }
    )
}

@Composable
internal fun ModelLoaderPrinterUploadProgressDialog(
    progressPercent: Int?,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
        },
        title = { Text("Printer Upload") },
        text = {
            Text(
                progressPercent?.let { "Uploading G-code... $it%" }
                    ?: "Preparing upload..."
            )
        }
    )
}

@Composable
internal fun ModelLoaderSliceCompletionDialog(
    result: SliceResult,
    activeConfiguration: ActiveSlicerConfiguration,
    onDismiss: () -> Unit
) {
    val lines = result.message.lines().filter { it.isNotBlank() }
    val title = lines.firstOrNull() ?: "Slice failed"
    val detail = lines.drop(1).joinToString("\n").ifBlank {
        "The model could not be sliced."
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        },
        title = {
            Text(title)
        },
        text = {
            Text(
                buildString {
                    append(detail)
                    if (result.summary != null) {
                        append("\n\n")
                        append(result.summary.dialogBody(activeConfiguration))
                    }
                }
            )
        }
    )
}

@Composable
internal fun ModelLoaderCalibrationResultDialog(
    job: CalibrationJob,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit
) {
    val spec = job.resultApplySpec()
    var value by remember(job.type.name) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = spec != null,
                onClick = { onApply(value) }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Skip")
            }
        },
        title = {
            Text(spec?.title ?: "Calibration complete")
        },
        text = {
            if (spec == null) {
                Text("${job.type.title} was sliced. This calibration does not map to a saved Mobile Slicer profile field yet.")
            } else {
                androidx.compose.foundation.layout.Column {
                    Text(spec.helperText)
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it },
                        singleLine = true,
                        label = { Text(spec.valueLabel) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    )
                }
            }
        }
    )
}

@Composable
internal fun ModelLoaderSavePlateDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var projectName by remember(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(projectName)
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        title = {
            Text("Save plate")
        },
        text = {
            OutlinedTextField(
                value = projectName,
                onValueChange = { projectName = it },
                singleLine = true,
                label = { Text("Project name") },
                modifier = Modifier.fillMaxWidth()
            )
        }
    )
}

@Composable
internal fun BoxScope.ModelLoaderSuccessBanner(message: String) {
    val bannerShape = RoundedCornerShape(18.dp)
    Surface(
        modifier = Modifier
            .align(Alignment.Center)
            .padding(horizontal = 18.dp)
            .border(1.dp, appOutlineColor().copy(alpha = 0.62f), bannerShape),
        shape = bannerShape,
        tonalElevation = 8.dp,
        color = appCardColorMuted().copy(alpha = 0.96f)
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            style = MaterialTheme.typography.titleSmall,
            color = appTitleColor()
        )
    }
}
