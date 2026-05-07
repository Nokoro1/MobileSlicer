package com.mobileslicer.profiles

import com.mobileslicer.AppSettingOption

internal class FilamentProfileEditorOptions {
    val boolEnabledDisabledOptions =
            listOf(
                AppSettingOption(true, "Enabled", ""),
                AppSettingOption(false, "Disabled", "")
            )
        val nullableBoolOptions: List<AppSettingOption<Boolean?>> =
            listOf(
                AppSettingOption<Boolean?>(null, "Inherit", "Use the value from the selected printer or process preset."),
                AppSettingOption<Boolean?>(true, "Enabled", ""),
                AppSettingOption<Boolean?>(false, "Disabled", "")
            )
        val filamentZHopTypeOptions =
            listOf(AppSettingOption<ZHopType?>(null, "Inherit", "Use the value from the selected printer preset.")) +
                ZHopType.entries.map { option ->
                    AppSettingOption<ZHopType?>(option, option.displayLabel, "")
                }
        val filamentRetractLiftEnforceOptions =
            listOf(AppSettingOption<RetractLiftEnforce?>(null, "Inherit", "Use the value from the selected printer preset.")) +
                RetractLiftEnforce.entries.map { option ->
                    AppSettingOption<RetractLiftEnforce?>(option, option.displayLabel, "")
                }
        val overhangFanThresholdOptions =
            listOf("0%", "10%", "25%", "50%", "75%", "95%").map { threshold ->
                AppSettingOption(threshold, threshold, "")
            }
        val materialTypeOptions =
            listOf(
                "ABS",
                "ABS-CF",
                "ABS-GF",
                "ASA",
                "ASA-CF",
                "ASA-GF",
                "ASA-AERO",
                "BVOH",
                "CoPE",
                "EVA",
                "FLEX",
                "HIPS",
                "PA",
                "PA-CF",
                "PA-GF",
                "PA6",
                "PA6-CF",
                "PA6-GF",
                "PA11",
                "PA11-CF",
                "PA11-GF",
                "PA12",
                "PA12-CF",
                "PA12-GF",
                "PAHT",
                "PAHT-CF",
                "PAHT-GF",
                "PC",
                "PC-ABS",
                "PC-CF",
                "PC-PBT",
                "PCL",
                "PCTG",
                "PE",
                "PE-CF",
                "PE-GF",
                "PEI-1010",
                "PEI-1010-CF",
                "PEI-1010-GF",
                "PEI-9085",
                "PEI-9085-CF",
                "PEI-9085-GF",
                "PEEK",
                "PEEK-CF",
                "PEEK-GF",
                "PEKK",
                "PEKK-CF",
                "PEKK-GF",
                "PES",
                "PET",
                "PET-CF",
                "PET-GF",
                "PETG",
                "PETG-CF",
                "PETG-GF",
                "PHA",
                "PI",
                "PLA",
                "PLA-AERO",
                "PLA-CF",
                "POM",
                "PP",
                "PP-CF",
                "PP-GF",
                "PPA-CF",
                "PPA-GF",
                "PPS",
                "PPS-CF",
                "PPSU",
                "PSU",
                "PVA",
                "PVB",
                "PVDF",
                "SBS",
                "TPI",
                "TPU"
            ).map { type ->
                AppSettingOption(type, type, "")
            }
}
