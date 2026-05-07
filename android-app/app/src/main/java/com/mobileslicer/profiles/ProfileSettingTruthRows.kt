package com.mobileslicer.profiles

internal fun printerSettingTruthRows(): List<SettingTruthRow> = listOf(
    SettingTruthRow(
        label = "Bed dimensions",
        status = "Error-state only",
        detail = "Out-of-bounds emitted extrusion now fails slice via printable_area/printable_height."
    ),
    SettingTruthRow(
        label = "Nozzle diameter",
        status = "Device-proven"
    ),
    SettingTruthRow(
        label = "Filament diameter",
        status = "Device-proven"
    )
)

internal fun filamentSettingTruthRows(): List<SettingTruthRow> = listOf(
    SettingTruthRow(
        label = "Filament material",
        status = "Source-wired",
        detail = "Currently reaches slicer only as Orca filament_type."
    ),
    SettingTruthRow(
        label = "Flow ratio",
        status = "Config only - Waydroid",
        detail = "Android now stores and emits Orca filament_flow_ratio, but current validation is exploratory Waydroid transport only."
    ),
    SettingTruthRow(
        label = "Filament retraction length",
        status = "Config only - Waydroid",
        detail = "Android now stores and emits Orca filament_retraction_length as an optional filament override, preserving inherit behavior when left blank."
    ),
    SettingTruthRow(
        label = "Filament retraction speed",
        status = "Config only - Waydroid",
        detail = "Android now stores and emits Orca filament_retraction_speed as an optional filament override, preserving inherit behavior when left blank."
    ),
    SettingTruthRow(
        label = "Filament deretraction speed",
        status = "Config only - Waydroid",
        detail = "Android now stores and emits Orca filament_deretraction_speed as an optional filament override. Blank preserves inherit behavior, while 0 keeps Orca's explicit same-as-retraction-speed meaning."
    ),
    SettingTruthRow(
        label = "Pressure advance enable",
        status = "Config only - Waydroid",
        detail = "Android now stores and emits Orca enable_pressure_advance, but current validation is exploratory Waydroid transport only."
    ),
    SettingTruthRow(
        label = "Pressure advance",
        status = "Config only - Waydroid",
        detail = "Android now stores and emits Orca pressure_advance, but current validation is exploratory Waydroid transport only."
    ),
    SettingTruthRow(
        label = "Max volumetric speed",
        status = "Device-proven",
        detail = "Current RFCYA01ANVE proof changes emitted non-first-layer feedrates by lifting the explicit Orca filament_max_volumetric_speed cap while normalized motion stays equal."
    ),
    SettingTruthRow(
        label = "Volumetric speed coefficients",
        status = "Config only - Waydroid",
        detail = "Android stores and emits the raw Orca volumetric_speed_coefficients string, and Waydroid exploratory validation confirmed app -> JSON -> wrapper transport only."
    ),
    SettingTruthRow(
        label = "Adaptive volumetric speed",
        status = "Config only - Waydroid",
        detail = "Android now stores and emits Orca filament_adaptive_volumetric_speed, but current validation is exploratory Waydroid transport only."
    ),
    SettingTruthRow(
        label = "First-layer nozzle temperature",
        status = "Start-sequence only",
        detail = "The bounded RFCYA01ANVE proof changes only the initial setup M104 command."
    ),
    SettingTruthRow(
        label = "Other-layers nozzle temperature",
        status = "Layer-change command only",
        detail = "The bounded RFCYA01ANVE proof inserts M104 at the first ;LAYER_CHANGE without changing motion."
    ),
    SettingTruthRow(
        label = "First-layer bed temperature",
        status = "Start-sequence only",
        detail = "The bounded RFCYA01ANVE proof changes only the initial setup M190 command."
    ),
    SettingTruthRow(
        label = "Other-layers bed temperature",
        status = "Layer-change command only",
        detail = "The bounded RFCYA01ANVE proof inserts M140 at the first ;LAYER_CHANGE without changing motion."
    ),
    SettingTruthRow(
        label = "Cooling baseline",
        status = "Fan-command only"
    ),
    SettingTruthRow(
        label = "No cooling for first X layers",
        status = "Fan-command only",
        detail = "The bounded RFCYA01ANVE proof changes only emitted fan-enable timing commands without changing motion."
    )
)

internal fun processSettingTruthRows(): List<SettingTruthRow> = listOf(
    SettingTruthRow(
        label = "Layer height",
        status = "Device-proven"
    ),
    SettingTruthRow(
        label = "First layer height",
        status = "Device-proven",
        detail = "The Android Process editor exposes first-layer height as Orca initial_layer_print_height, with first_layer_height kept in sync for compatibility."
    ),
    SettingTruthRow(
        label = "First layer print speed",
        status = "Device-proven",
        detail = "Current initial_layer_speed 100 -> 10 proof on RFCYA01ANVE changes emitted first-layer brim and perimeter feedrates while bottom surface stays on Orca's separate initial_layer_infill_speed path."
    ),
    SettingTruthRow(
        label = "First layer infill speed",
        status = "Device-proven",
        detail = "Current initial_layer_infill_speed proof on RFCYA01ANVE changes emitted first-layer bottom-surface feedrates on the accepted cube fixture."
    ),
    SettingTruthRow(
        label = "First layer travel speed",
        status = "Device-proven",
        detail = "Current initial_layer_travel_speed proof on RFCYA01ANVE changes emitted first-layer travel feedrates on the accepted cube fixture."
    ),
    SettingTruthRow(
        label = "Number of slow layers",
        status = "Device-proven",
        detail = "Current slow_down_layers proof on RFCYA01ANVE changes emitted early-layer feedrates above layer 1 on the accepted cube fixture."
    ),
    SettingTruthRow(
        label = "Outer wall speed",
        status = "Device-proven",
        detail = "Current outer_wall_speed 100 -> 30 proof on RFCYA01ANVE changes emitted outer-wall feedrates while normalized motion stays equal on the accepted cube fixture."
    ),
    SettingTruthRow(
        label = "Inner wall speed",
        status = "Device-proven",
        detail = "Current inner_wall_speed 100 -> 30 proof on RFCYA01ANVE changes emitted inner-wall feedrates while normalized motion stays equal on the accepted cube fixture."
    ),
    SettingTruthRow(
        label = "Top surface speed",
        status = "Device-proven",
        detail = "Current top_surface_speed 100 -> 20 proof on RFCYA01ANVE changes emitted top-surface feedrates while feedrate-stripped motion stays equal on the accepted cube fixture."
    ),
    SettingTruthRow(
        label = "Travel speed",
        status = "Device-proven",
        detail = "Current travel_speed 120 -> 240 proof on RFCYA01ANVE changes emitted non-first-layer travel feedrates while feedrate-stripped motion stays equal on the accepted cube fixture."
    ),
    SettingTruthRow(
        label = "Outer wall acceleration",
        status = "Device tested",
        detail = "Current outer_wall_acceleration 500 -> 1500 proof on RFCYA01ANVE removes the emitted M204 S500 reset before Outer wall blocks, while acceleration-stripped motion stays equal on the accepted cube fixture."
    ),
    SettingTruthRow(
        label = "Inner wall acceleration",
        status = "Config only",
        detail = "Current inner_wall_acceleration 10000 -> 2000 proof on RFCYA01ANVE changes the echoed config comment but does not change emitted executable acceleration commands on the accepted cube fixture."
    ),
    SettingTruthRow(
        label = "Top surface acceleration",
        status = "Device tested",
        detail = "Current top_surface_acceleration 500 -> 2000 proof on RFCYA01ANVE changes emitted Top surface acceleration commands from M204 S500 to M204 S1500 while acceleration-stripped motion stays equal on the accepted cube fixture."
    ),
    SettingTruthRow(
        label = "Sparse infill acceleration",
        status = "Device tested",
        detail = "Current sparse_infill_acceleration 500 -> 1500 proof on RFCYA01ANVE changes emitted Sparse infill acceleration commands from M204 S500 to M204 S1500 while acceleration-stripped motion stays equal on the accepted cube fixture."
    ),
    SettingTruthRow(
        label = "Bridge speed",
        status = "Device-proven",
        detail = "Current bridge_speed 10 -> 40 proof on RFCYA01ANVE changes emitted Overhang wall and Bridge feedrates from G1 F600 to G1 F1807 on the accepted bridge fixture while feedrate-stripped motion stays equal."
    ),
    SettingTruthRow(
        label = "Small perimeter speed",
        status = "Device-proven",
        detail = "Current small_perimeter_speed 10 -> 50 proof on RFCYA01ANVE changes emitted small-feature Inner wall and Outer wall feedrates from G1 F600 to G1 F3000 on the accepted stronger small-feature fixture."
    ),
    SettingTruthRow(
        label = "Small perimeter threshold",
        status = "Device-proven",
        detail = "Current small_perimeter_threshold 0 -> 20 proof on RFCYA01ANVE activates emitted small-feature perimeter handling and drops Inner wall and Outer wall feedrates from G1 F6000 to G1 F600 on the accepted stronger small-feature fixture."
    ),
    SettingTruthRow(
        label = "Sparse infill speed",
        status = "Device-proven",
        detail = "Current sparse_infill_speed 20 -> 80 proof on RFCYA01ANVE changes emitted Sparse infill feedrates from G1 F1200 to G1 F3822 on mobileslicer_test_cube.stl while feedrate-stripped motion stays equal."
    ),
    SettingTruthRow(
        label = "Internal solid infill speed",
        status = "Device-proven",
        detail = "Current internal_solid_infill_speed 20 -> 80 proof on RFCYA01ANVE changes emitted Internal solid infill feedrates from G1 F1200 to G1 F4800 on mobileslicer_test_cube.stl while feedrate-stripped motion stays equal."
    ),
    SettingTruthRow(
        label = "Gap infill speed",
        status = "Config-labeling-only effect",
        detail = "Current dedicated strip-fixture reruns on RFCYA01ANVE echo gap_infill_speed correctly but still do not emit a Gap infill block or change executable motion."
    ),
    SettingTruthRow(
        label = "Seam position",
        status = "Device-proven",
        detail = "Latest user-confirmed RFCYA01ANVE result is the repo authority for this field and upgrades seam_position to Device-proven."
    ),
    SettingTruthRow(
        label = "Top shell layers",
        status = "Device-proven",
        detail = "Current 4 -> 6 proof on RFCYA01ANVE changes executable top-region motion on the bounded cube fixture."
    ),
    SettingTruthRow(
        label = "Bottom shell layers",
        status = "Device-proven",
        detail = "Current 3 -> 5 proof on RFCYA01ANVE changes executable bottom-region motion on the bounded cube fixture."
    ),
    SettingTruthRow(
        label = "Wall count",
        status = "Stronger-fixture proven",
        detail = "Weak cached ms_box is too weak for wall-count proof."
    ),
    SettingTruthRow(
        label = "Precise wall",
        status = "Device-proven",
        detail = "Current precise_outer_wall true -> false proof on RFCYA01ANVE changes executable motion on the bounded cached wall_smoke_box fixture."
    ),
    SettingTruthRow(
        label = "Only one wall on top surfaces",
        status = "Stronger-fixture proven",
        detail = "Current only_one_wall_top 1 -> 0 proof on RFCYA01ANVE changes the final roof-region wall/top-surface picture on the accepted flat-roof cube fixture."
    ),
    SettingTruthRow(
        label = "Top surface pattern",
        status = "Stronger-fixture proven",
        detail = "Current monotonic line -> concentric proof on RFCYA01ANVE changes emitted top-surface motion on the accepted flat-roof cube fixture."
    ),
    SettingTruthRow(
        label = "Skirts",
        status = "Device-proven",
        detail = "Current skirt parity proof on RFCYA01ANVE verifies disabled, combined, per-object, and brim-plus-skirt outputs with real ;TYPE:Skirt G-code and finite in-bed first-layer geometry."
    ),
    SettingTruthRow(
        label = "Brim width",
        status = "Device-proven",
        detail = "Current 0 -> 4 mm proof on RFCYA01ANVE emits a real ;TYPE:Brim block and changes executable motion on mobileslicer_test_cube.stl."
    ),
    SettingTruthRow(
        label = "Infill density",
        status = "Device-proven"
    ),
    SettingTruthRow(
        label = "Sparse infill pattern",
        status = "Device-proven",
        detail = "Current grid -> gyroid -> cubic proofs on RFCYA01ANVE all emit real sparse infill on the accepted cube fixture."
    ),
)
