package com.mobileslicer.profiles

import com.mobileslicer.AppSettingOption

internal class PrinterProfileEditorOptions {
    val boolEnabledDisabledOptions =
            listOf(
                AppSettingOption(true, "Enabled", ""),
                AppSettingOption(false, "Disabled", "")
            )
        val gcodeFlavorOptions =
            GcodeFlavor.entries.map { option ->
                AppSettingOption(option, option.displayLabel, "")
            }
        val printerStructureOptions =
            PrinterStructure.entries.map { option ->
                AppSettingOption(option, option.displayLabel, "")
            }
        val printerTechnologyOptions =
            PrinterTechnology.entries.map { option ->
                AppSettingOption(option, option.displayLabel, "")
            }
        val powerLossRecoveryOptions =
            PowerLossRecoveryMode.entries.map { option ->
                AppSettingOption(option, option.displayLabel, "")
            }
        val printHostTypeOptions =
            PrintHostType.entries.map { option ->
                AppSettingOption(option, option.displayLabel, "")
            }
        val printHostAuthorizationOptions =
            PrintHostAuthorizationType.entries.map { option ->
                AppSettingOption(option, option.displayLabel, "")
            }
        val nozzleTypeOptions =
            NozzleType.entries.map { option ->
                AppSettingOption(option, option.displayLabel, "")
            }
        val bedTemperatureFormulaOptions =
            BedTemperatureFormula.entries.map { option ->
                AppSettingOption(option, option.displayLabel, "")
            }
        val defaultBedTypeOptions =
            bedTypeOptions(supportMultiBedTypes = true)
        val wipeTowerTypeOptions =
            WipeTowerType.entries.map { option ->
                AppSettingOption(option, option.displayLabel, "")
            }
        val zHopTypeOptions =
            ZHopType.entries.map { option ->
                AppSettingOption(option, option.displayLabel, "")
            }
        val retractLiftEnforceOptions =
            RetractLiftEnforce.entries.map { option ->
                AppSettingOption(option, option.displayLabel, "")
            }
        val longRetractionWhenCutOptions =
            LongRetractionWhenCutMode.entries.map { option ->
                AppSettingOption(option, option.displayLabel, "")
            }
        val extruderTypeOptions =
            ExtruderType.entries.map { option ->
                AppSettingOption(option, option.displayLabel, "")
            }
        val nozzleVolumeTypeOptions =
            NozzleVolumeType.entries.map { option ->
                AppSettingOption(option, option.displayLabel, "")
            }

    fun bedTypeOptions(supportMultiBedTypes: Boolean): List<AppSettingOption<DefaultBedType>> {
        val bedTypes = if (supportMultiBedTypes) {
            DefaultBedType.entries.filter { it != DefaultBedType.NotSet }
        } else {
            emptyList()
        }
        return bedTypes.map { option ->
            AppSettingOption(
                option,
                option.displayLabel,
                ""
            )
        }
    }
}
