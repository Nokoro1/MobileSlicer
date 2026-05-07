package com.mobileslicer.profiles

internal fun printerSettingTruthRows(): List<SettingTruthRow> = listOf(
    SettingTruthRow(
        label = "Bed dimensions",
        status = "Error-state only",
        detail = "Out-of-bounds emitted extrusion now fails slice via printable_area/printable_height."
    ),
    SettingTruthRow(
        label = "Nozzle diameter",
        status = "Device tested"
    ),
    SettingTruthRow(
        label = "Filament diameter",
        status = "Device tested"
    )
)

internal fun filamentSettingTruthRows(): List<SettingTruthRow> = listOf(
    SettingTruthRow(
        label = "Filament material",
        status = "Mapped",
        detail = "Mapped to Orca filament_type."
    ),
    SettingTruthRow(
        label = "Flow ratio",
        status = "Config mapped",
        detail = "Stored and emitted as Orca filament_flow_ratio."
    ),
    SettingTruthRow(
        label = "Filament retraction length",
        status = "Config mapped",
        detail = "Stored and emitted as Orca filament_retraction_length while preserving inherited defaults when left blank."
    ),
    SettingTruthRow(
        label = "Filament retraction speed",
        status = "Config mapped",
        detail = "Stored and emitted as Orca filament_retraction_speed while preserving inherited defaults when left blank."
    ),
    SettingTruthRow(
        label = "Filament deretraction speed",
        status = "Config mapped",
        detail = "Stored and emitted as Orca filament_deretraction_speed. Blank preserves inherited defaults; 0 keeps Orca's same-as-retraction-speed behavior."
    ),
    SettingTruthRow(
        label = "Pressure advance enable",
        status = "Config mapped",
        detail = "Stored and emitted as Orca enable_pressure_advance."
    ),
    SettingTruthRow(
        label = "Pressure advance",
        status = "Config mapped",
        detail = "Stored and emitted as Orca pressure_advance."
    ),
    SettingTruthRow(
        label = "Max volumetric speed",
        status = "Device tested",
        detail = "Validated against emitted non-first-layer feedrates by changing the explicit Orca filament_max_volumetric_speed cap."
    ),
    SettingTruthRow(
        label = "Volumetric speed coefficients",
        status = "Config mapped",
        detail = "Stored and emitted as the raw Orca volumetric_speed_coefficients string."
    ),
    SettingTruthRow(
        label = "Adaptive volumetric speed",
        status = "Config mapped",
        detail = "Stored and emitted as Orca filament_adaptive_volumetric_speed."
    ),
    SettingTruthRow(
        label = "First-layer nozzle temperature",
        status = "Start-sequence only",
        detail = "Changes the initial setup M104 command."
    ),
    SettingTruthRow(
        label = "Other-layers nozzle temperature",
        status = "Layer-change command only",
        detail = "Inserts M104 at the first ;LAYER_CHANGE without changing motion."
    ),
    SettingTruthRow(
        label = "First-layer bed temperature",
        status = "Start-sequence only",
        detail = "Changes the initial setup M190 command."
    ),
    SettingTruthRow(
        label = "Other-layers bed temperature",
        status = "Layer-change command only",
        detail = "Inserts M140 at the first ;LAYER_CHANGE without changing motion."
    ),
    SettingTruthRow(
        label = "Cooling baseline",
        status = "Fan-command only"
    ),
    SettingTruthRow(
        label = "No cooling for first X layers",
        status = "Fan-command only",
        detail = "Changes emitted fan-enable timing commands without changing motion."
    )
)

internal fun processSettingTruthRows(): List<SettingTruthRow> = listOf(
    SettingTruthRow(
        label = "Layer height",
        status = "Device tested"
    ),
    SettingTruthRow(
        label = "First layer height",
        status = "Device tested",
        detail = "The Android Process editor exposes first-layer height as Orca initial_layer_print_height, with first_layer_height kept in sync for compatibility."
    ),
    SettingTruthRow(
        label = "First layer print speed",
        status = "Device tested",
        detail = "Validated against emitted first-layer brim and perimeter feedrates while bottom surface remains on Orca's separate initial_layer_infill_speed path."
    ),
    SettingTruthRow(
        label = "First layer infill speed",
        status = "Device tested",
        detail = "Validated against emitted first-layer bottom-surface feedrates."
    ),
    SettingTruthRow(
        label = "First layer travel speed",
        status = "Device tested",
        detail = "Validated against emitted first-layer travel feedrates."
    ),
    SettingTruthRow(
        label = "Number of slow layers",
        status = "Device tested",
        detail = "Validated against emitted early-layer feedrates above layer 1."
    ),
    SettingTruthRow(
        label = "Outer wall speed",
        status = "Device tested",
        detail = "Validated against emitted outer-wall feedrates while normalized motion stays equal."
    ),
    SettingTruthRow(
        label = "Inner wall speed",
        status = "Device tested",
        detail = "Validated against emitted inner-wall feedrates while normalized motion stays equal."
    ),
    SettingTruthRow(
        label = "Top surface speed",
        status = "Device tested",
        detail = "Validated against emitted top-surface feedrates while feedrate-stripped motion stays equal."
    ),
    SettingTruthRow(
        label = "Travel speed",
        status = "Device tested",
        detail = "Validated against emitted non-first-layer travel feedrates while feedrate-stripped motion stays equal."
    ),
    SettingTruthRow(
        label = "Outer wall acceleration",
        status = "Device tested",
        detail = "Validated against emitted acceleration commands while acceleration-stripped motion stays equal."
    ),
    SettingTruthRow(
        label = "Inner wall acceleration",
        status = "Config only",
        detail = "Mapped into configuration; current emitted motion is unchanged for the standard regression model."
    ),
    SettingTruthRow(
        label = "Top surface acceleration",
        status = "Device tested",
        detail = "Validated against emitted top-surface acceleration commands while acceleration-stripped motion stays equal."
    ),
    SettingTruthRow(
        label = "Sparse infill acceleration",
        status = "Device tested",
        detail = "Validated against emitted sparse-infill acceleration commands while acceleration-stripped motion stays equal."
    ),
    SettingTruthRow(
        label = "Bridge speed",
        status = "Device tested",
        detail = "Validated against emitted overhang-wall and bridge feedrates while feedrate-stripped motion stays equal."
    ),
    SettingTruthRow(
        label = "Small perimeter speed",
        status = "Device tested",
        detail = "Validated against emitted small-feature inner-wall and outer-wall feedrates."
    ),
    SettingTruthRow(
        label = "Small perimeter threshold",
        status = "Device tested",
        detail = "Validated against emitted small-feature perimeter handling and feedrate changes."
    ),
    SettingTruthRow(
        label = "Sparse infill speed",
        status = "Device tested",
        detail = "Validated against emitted sparse-infill feedrates while feedrate-stripped motion stays equal."
    ),
    SettingTruthRow(
        label = "Internal solid infill speed",
        status = "Device tested",
        detail = "Validated against emitted internal solid infill feedrates while feedrate-stripped motion stays equal."
    ),
    SettingTruthRow(
        label = "Gap infill speed",
        status = "Config only",
        detail = "Mapped into configuration; the standard regression model does not emit separate gap-infill motion."
    ),
    SettingTruthRow(
        label = "Seam position",
        status = "Device tested",
        detail = "Validated against emitted seam placement behavior."
    ),
    SettingTruthRow(
        label = "Top shell layers",
        status = "Device tested",
        detail = "Validated against executable top-region motion."
    ),
    SettingTruthRow(
        label = "Bottom shell layers",
        status = "Device tested",
        detail = "Validated against executable bottom-region motion."
    ),
    SettingTruthRow(
        label = "Wall count",
        status = "Regression tested",
        detail = "Validated with a bounded wall-count regression model."
    ),
    SettingTruthRow(
        label = "Precise wall",
        status = "Device tested",
        detail = "Validated against executable wall motion."
    ),
    SettingTruthRow(
        label = "Only one wall on top surfaces",
        status = "Regression tested",
        detail = "Validated against roof-region wall and top-surface output."
    ),
    SettingTruthRow(
        label = "Top surface pattern",
        status = "Regression tested",
        detail = "Validated against emitted top-surface motion."
    ),
    SettingTruthRow(
        label = "Skirts",
        status = "Device tested",
        detail = "Validated across disabled, combined, per-object, and brim-plus-skirt outputs with finite in-bed first-layer geometry."
    ),
    SettingTruthRow(
        label = "Brim width",
        status = "Device tested",
        detail = "Validated against emitted ;TYPE:Brim blocks and executable motion."
    ),
    SettingTruthRow(
        label = "Infill density",
        status = "Device tested"
    ),
    SettingTruthRow(
        label = "Sparse infill pattern",
        status = "Device tested",
        detail = "Validated across grid, gyroid, and cubic sparse-infill output."
    ),
)
