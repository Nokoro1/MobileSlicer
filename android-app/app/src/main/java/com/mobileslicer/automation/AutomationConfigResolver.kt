package com.mobileslicer.automation

import android.content.Intent
import android.os.SystemClock
import com.mobileslicer.profiles.BottomSurfacePattern
import com.mobileslicer.profiles.FilamentProfile
import com.mobileslicer.profiles.FuzzySkinMode
import com.mobileslicer.profiles.FuzzySkinNoiseType
import com.mobileslicer.profiles.FuzzySkinType
import com.mobileslicer.profiles.InternalBridgeFilterMode
import com.mobileslicer.profiles.InternalSolidInfillPattern
import com.mobileslicer.profiles.PrinterProfile
import com.mobileslicer.profiles.ProcessProfile
import com.mobileslicer.profiles.ProfileStore
import com.mobileslicer.profiles.SparseInfillPattern
import com.mobileslicer.profiles.SupportStyle
import com.mobileslicer.profiles.SupportType
import com.mobileslicer.profiles.TopSurfacePattern
import com.mobileslicer.profiles.WallGenerator
import com.mobileslicer.profiles.WallInfillOrder
import com.mobileslicer.profiles.activeConfiguration
import com.mobileslicer.profiles.derivedBridgeSpeedMmPerSec
import com.mobileslicer.profiles.derivedFirstLayerInfillSpeedMmPerSec
import com.mobileslicer.profiles.derivedFirstLayerPrintSpeedMmPerSec
import com.mobileslicer.profiles.derivedGapInfillSpeedMmPerSec
import com.mobileslicer.profiles.derivedInnerWallSpeedMmPerSec
import com.mobileslicer.profiles.derivedInternalSolidInfillSpeedMmPerSec
import com.mobileslicer.profiles.derivedOuterWallSpeedMmPerSec
import com.mobileslicer.profiles.derivedSmallPerimeterSpeedMmPerSec
import com.mobileslicer.profiles.derivedSparseInfillSpeedMmPerSec
import com.mobileslicer.profiles.derivedTopSurfaceSpeedMmPerSec
import com.mobileslicer.profiles.derivedTravelSpeedMmPerSec
import com.mobileslicer.profiles.toNativeSliceConfigJson
import com.mobileslicer.profiles.withChangedNativeProcessOverridesFrom
import org.json.JSONObject

private const val EXTRA_AUTOMATION_CONFIG_JSON = "automation_config_json"
private const val EXTRA_AUTOMATION_DUPLICATE_ACTIVE_PRINTER = "automation_duplicate_active_printer"
private const val EXTRA_AUTOMATION_PRINTER_ID = "automation_printer_id"
private const val EXTRA_AUTOMATION_PRINTER_NAME = "automation_printer_name"
private const val EXTRA_AUTOMATION_BED_WIDTH_MM = "automation_bed_width_mm"
private const val EXTRA_AUTOMATION_BED_DEPTH_MM = "automation_bed_depth_mm"
private const val EXTRA_AUTOMATION_MAX_HEIGHT_MM = "automation_max_height_mm"
private const val EXTRA_AUTOMATION_NOZZLE_DIAMETER_MM = "automation_nozzle_diameter_mm"
private const val EXTRA_AUTOMATION_FILAMENT_DIAMETER_MM = "automation_filament_diameter_mm"
private const val EXTRA_AUTOMATION_DUPLICATE_ACTIVE_FILAMENT = "automation_duplicate_active_filament"
private const val EXTRA_AUTOMATION_FILAMENT_ID = "automation_filament_id"
private const val EXTRA_AUTOMATION_FILAMENT_NAME = "automation_filament_name"
private const val EXTRA_AUTOMATION_MATERIAL_TYPE = "automation_material_type"
private const val EXTRA_AUTOMATION_FILAMENT_MAX_VOLUMETRIC_SPEED_MM3_PER_SEC = "automation_filament_max_volumetric_speed_mm3_per_sec"
private const val EXTRA_AUTOMATION_NOZZLE_TEMPERATURE_C = "automation_nozzle_temperature_c"
private const val EXTRA_AUTOMATION_BED_TEMPERATURE_C = "automation_bed_temperature_c"
private const val EXTRA_AUTOMATION_COOLING_PERCENT = "automation_cooling_percent"
private const val EXTRA_AUTOMATION_NO_COOLING_FIRST_LAYERS = "automation_no_cooling_first_layers"
private const val EXTRA_AUTOMATION_DUPLICATE_ACTIVE_PROCESS = "automation_duplicate_active_process"
private const val EXTRA_AUTOMATION_PROCESS_ID = "automation_process_id"
private const val EXTRA_AUTOMATION_PROCESS_NAME = "automation_process_name"
private const val EXTRA_AUTOMATION_LAYER_HEIGHT_MM = "automation_layer_height_mm"
private const val EXTRA_AUTOMATION_FIRST_LAYER_PRINT_SPEED_MM_PER_SEC = "automation_first_layer_print_speed_mm_per_sec"
private const val EXTRA_AUTOMATION_FIRST_LAYER_INFILL_SPEED_MM_PER_SEC = "automation_first_layer_infill_speed_mm_per_sec"
private const val EXTRA_AUTOMATION_FIRST_LAYER_TRAVEL_SPEED_PERCENT = "automation_first_layer_travel_speed_percent"
private const val EXTRA_AUTOMATION_SLOW_DOWN_LAYERS = "automation_slow_down_layers"
private const val EXTRA_AUTOMATION_OUTER_WALL_SPEED_MM_PER_SEC = "automation_outer_wall_speed_mm_per_sec"
private const val EXTRA_AUTOMATION_INNER_WALL_SPEED_MM_PER_SEC = "automation_inner_wall_speed_mm_per_sec"
private const val EXTRA_AUTOMATION_TOP_SURFACE_SPEED_MM_PER_SEC = "automation_top_surface_speed_mm_per_sec"
private const val EXTRA_AUTOMATION_TRAVEL_SPEED_MM_PER_SEC = "automation_travel_speed_mm_per_sec"
private const val EXTRA_AUTOMATION_OUTER_WALL_ACCELERATION_MM_PER_SEC2 = "automation_outer_wall_acceleration_mm_per_sec2"
private const val EXTRA_AUTOMATION_INNER_WALL_ACCELERATION_MM_PER_SEC2 = "automation_inner_wall_acceleration_mm_per_sec2"
private const val EXTRA_AUTOMATION_TOP_SURFACE_ACCELERATION_MM_PER_SEC2 = "automation_top_surface_acceleration_mm_per_sec2"
private const val EXTRA_AUTOMATION_SPARSE_INFILL_ACCELERATION_MM_PER_SEC2 = "automation_sparse_infill_acceleration_mm_per_sec2"
private const val EXTRA_AUTOMATION_BRIDGE_ACCELERATION = "automation_bridge_acceleration"
private const val EXTRA_AUTOMATION_BRIDGE_SPEED_MM_PER_SEC = "automation_bridge_speed_mm_per_sec"
private const val EXTRA_AUTOMATION_BRIDGE_ANGLE_DEGREES = "automation_bridge_angle_degrees"
private const val EXTRA_AUTOMATION_BRIDGE_DENSITY_PERCENT = "automation_bridge_density_percent"
private const val EXTRA_AUTOMATION_BRIDGE_FLOW_RATIO = "automation_bridge_flow_ratio"
private const val EXTRA_AUTOMATION_BRIDGE_NO_SUPPORT = "automation_bridge_no_support"
private const val EXTRA_AUTOMATION_INTERNAL_BRIDGE_ANGLE_DEGREES = "automation_internal_bridge_angle_degrees"
private const val EXTRA_AUTOMATION_INTERNAL_BRIDGE_DENSITY_PERCENT = "automation_internal_bridge_density_percent"
private const val EXTRA_AUTOMATION_INTERNAL_BRIDGE_FLOW_RATIO = "automation_internal_bridge_flow_ratio"
private const val EXTRA_AUTOMATION_INTERNAL_BRIDGE_SPEED = "automation_internal_bridge_speed"
private const val EXTRA_AUTOMATION_INTERNAL_BRIDGE_FAN_SPEED = "automation_internal_bridge_fan_speed"
private const val EXTRA_AUTOMATION_INTERNAL_BRIDGE_SUPPORT_THICKNESS = "automation_internal_bridge_support_thickness"
private const val EXTRA_AUTOMATION_SMALL_PERIMETER_SPEED_MM_PER_SEC = "automation_small_perimeter_speed_mm_per_sec"
private const val EXTRA_AUTOMATION_SMALL_PERIMETER_THRESHOLD_MM = "automation_small_perimeter_threshold_mm"
private const val EXTRA_AUTOMATION_SPARSE_INFILL_SPEED_MM_PER_SEC = "automation_sparse_infill_speed_mm_per_sec"
private const val EXTRA_AUTOMATION_INTERNAL_SOLID_INFILL_SPEED_MM_PER_SEC = "automation_internal_solid_infill_speed_mm_per_sec"
private const val EXTRA_AUTOMATION_GAP_INFILL_SPEED_MM_PER_SEC = "automation_gap_infill_speed_mm_per_sec"
private const val EXTRA_AUTOMATION_TOP_SHELL_LAYERS = "automation_top_shell_layers"
private const val EXTRA_AUTOMATION_BOTTOM_SHELL_LAYERS = "automation_bottom_shell_layers"
private const val EXTRA_AUTOMATION_PRECISE_OUTER_WALL = "automation_precise_outer_wall"
private const val EXTRA_AUTOMATION_ONLY_ONE_WALL_TOP = "automation_only_one_wall_top"
private const val EXTRA_AUTOMATION_TOP_SURFACE_PATTERN = "automation_top_surface_pattern"
private const val EXTRA_AUTOMATION_BOTTOM_SURFACE_PATTERN = "automation_bottom_surface_pattern"
private const val EXTRA_AUTOMATION_INTERNAL_SOLID_INFILL_PATTERN = "automation_internal_solid_infill_pattern"
private const val EXTRA_AUTOMATION_TOP_SURFACE_DENSITY_PERCENT = "automation_top_surface_density_percent"
private const val EXTRA_AUTOMATION_BOTTOM_SURFACE_DENSITY_PERCENT = "automation_bottom_surface_density_percent"
private const val EXTRA_AUTOMATION_SOLID_INFILL_DIRECTION_DEGREES = "automation_solid_infill_direction_degrees"
private const val EXTRA_AUTOMATION_SOLID_INFILL_ROTATE_TEMPLATE = "automation_solid_infill_rotate_template"
private const val EXTRA_AUTOMATION_LINE_WIDTH = "automation_line_width"
private const val EXTRA_AUTOMATION_OUTER_WALL_LINE_WIDTH = "automation_outer_wall_line_width"
private const val EXTRA_AUTOMATION_INNER_WALL_LINE_WIDTH = "automation_inner_wall_line_width"
private const val EXTRA_AUTOMATION_INITIAL_LAYER_LINE_WIDTH = "automation_initial_layer_line_width"
private const val EXTRA_AUTOMATION_INITIAL_LAYER_MIN_BEAD_WIDTH_PERCENT = "automation_initial_layer_min_bead_width_percent"
private const val EXTRA_AUTOMATION_TOP_SURFACE_LINE_WIDTH = "automation_top_surface_line_width"
private const val EXTRA_AUTOMATION_INTERNAL_SOLID_INFILL_LINE_WIDTH = "automation_internal_solid_infill_line_width"
private const val EXTRA_AUTOMATION_MIN_WIDTH_TOP_SURFACE = "automation_min_width_top_surface"
private const val EXTRA_AUTOMATION_SPARSE_INFILL_LINE_WIDTH = "automation_sparse_infill_line_width"
private const val EXTRA_AUTOMATION_INFILL_DIRECTION_DEGREES = "automation_infill_direction_degrees"
private const val EXTRA_AUTOMATION_SPARSE_INFILL_ROTATE_TEMPLATE = "automation_sparse_infill_rotate_template"
private const val EXTRA_AUTOMATION_ALIGN_INFILL_DIRECTION_TO_MODEL = "automation_align_infill_direction_to_model"
private const val EXTRA_AUTOMATION_INFILL_WALL_OVERLAP_PERCENT = "automation_infill_wall_overlap_percent"
private const val EXTRA_AUTOMATION_TOP_BOTTOM_INFILL_WALL_OVERLAP_PERCENT = "automation_top_bottom_infill_wall_overlap_percent"
private const val EXTRA_AUTOMATION_INFILL_ANCHOR = "automation_infill_anchor"
private const val EXTRA_AUTOMATION_INFILL_ANCHOR_MAX = "automation_infill_anchor_max"
private const val EXTRA_AUTOMATION_INFILL_COMBINATION = "automation_infill_combination"
private const val EXTRA_AUTOMATION_INFILL_COMBINATION_MAX_LAYER_HEIGHT = "automation_infill_combination_max_layer_height"
private const val EXTRA_AUTOMATION_MINIMUM_SPARSE_INFILL_AREA_MM2 = "automation_minimum_sparse_infill_area_mm2"
private const val EXTRA_AUTOMATION_DETECT_THIN_WALL = "automation_detect_thin_wall"
private const val EXTRA_AUTOMATION_DETECT_OVERHANG_WALL = "automation_detect_overhang_wall"
private const val EXTRA_AUTOMATION_THICK_BRIDGES = "automation_thick_bridges"
private const val EXTRA_AUTOMATION_DONT_FILTER_INTERNAL_BRIDGES = "automation_dont_filter_internal_bridges"
private const val EXTRA_AUTOMATION_WALL_GENERATOR = "automation_wall_generator"
private const val EXTRA_AUTOMATION_WALL_INFILL_ORDER = "automation_wall_infill_order"
private const val EXTRA_AUTOMATION_EXTRA_PERIMETERS_ON_OVERHANGS = "automation_extra_perimeters_on_overhangs"
private const val EXTRA_AUTOMATION_WALL_COUNT = "automation_wall_count"
private const val EXTRA_AUTOMATION_INFILL_PERCENT = "automation_infill_percent"
private const val EXTRA_AUTOMATION_SPARSE_INFILL_PATTERN = "automation_sparse_infill_pattern"
private const val EXTRA_AUTOMATION_ENABLE_SUPPORT = "automation_enable_support"
private const val EXTRA_AUTOMATION_SUPPORT_TYPE = "automation_support_type"
private const val EXTRA_AUTOMATION_SUPPORT_STYLE = "automation_support_style"
private const val EXTRA_AUTOMATION_SUPPORT_THRESHOLD_ANGLE = "automation_support_threshold_angle"
private const val EXTRA_AUTOMATION_SUPPORT_BUILDPLATE_ONLY = "automation_support_buildplate_only"

internal interface AutomationConfigInput {
    fun getStringExtra(name: String): String?
    fun getBooleanExtra(name: String, defaultValue: Boolean): Boolean
    fun getFloatExtra(name: String, defaultValue: Float): Float
    fun getIntExtra(name: String, defaultValue: Int): Int
    fun hasExtra(name: String): Boolean
}

private class AutomationIntentConfigInput(
    private val intent: Intent
) : AutomationConfigInput {
    override fun getStringExtra(name: String): String? = intent.getStringExtra(name)

    override fun getBooleanExtra(name: String, defaultValue: Boolean): Boolean =
        intent.getBooleanExtra(name, defaultValue)

    override fun getFloatExtra(name: String, defaultValue: Float): Float =
        intent.getFloatExtra(name, defaultValue)

    override fun getIntExtra(name: String, defaultValue: Int): Int =
        intent.getIntExtra(name, defaultValue)

    override fun hasExtra(name: String): Boolean = intent.hasExtra(name)
}

internal class AutomationConfigResolver(
    private val loadProfileStore: () -> ProfileStore,
    private val timestampMillis: () -> Long = { SystemClock.elapsedRealtime() }
) {
    fun resolve(intent: Intent): String =
        resolve(AutomationIntentConfigInput(intent))

    internal fun resolve(input: AutomationConfigInput): String {
        input.getStringExtra(EXTRA_AUTOMATION_CONFIG_JSON)?.let { explicitConfig ->
            return explicitConfig
        }
        return resolveProfileStore(input).activeConfiguration().toNativeSliceConfigJson()
    }

    internal fun resolveProfileStore(input: AutomationConfigInput): ProfileStore {
        val duplicateActivePrinter = input.getBooleanExtra(EXTRA_AUTOMATION_DUPLICATE_ACTIVE_PRINTER, false)
        val duplicateActiveFilament = input.getBooleanExtra(EXTRA_AUTOMATION_DUPLICATE_ACTIVE_FILAMENT, false)
        val duplicateActiveProcess = input.getBooleanExtra(EXTRA_AUTOMATION_DUPLICATE_ACTIVE_PROCESS, false)
        val bedWidthMm = input.getFloatExtra(EXTRA_AUTOMATION_BED_WIDTH_MM, Float.NaN)
        val bedDepthMm = input.getFloatExtra(EXTRA_AUTOMATION_BED_DEPTH_MM, Float.NaN)
        val maxHeightMm = input.getFloatExtra(EXTRA_AUTOMATION_MAX_HEIGHT_MM, Float.NaN)
        val nozzleDiameterMm = input.getFloatExtra(EXTRA_AUTOMATION_NOZZLE_DIAMETER_MM, Float.NaN)
        val filamentDiameterMm = input.getFloatExtra(EXTRA_AUTOMATION_FILAMENT_DIAMETER_MM, Float.NaN)
        val materialType = input.getStringExtra(EXTRA_AUTOMATION_MATERIAL_TYPE)
        val filamentMaxVolumetricSpeedMm3PerSec = input.getFloatExtra(EXTRA_AUTOMATION_FILAMENT_MAX_VOLUMETRIC_SPEED_MM3_PER_SEC, Float.NaN)
        val nozzleTemperatureC = input.getIntExtra(EXTRA_AUTOMATION_NOZZLE_TEMPERATURE_C, Int.MIN_VALUE)
        val bedTemperatureC = input.getIntExtra(EXTRA_AUTOMATION_BED_TEMPERATURE_C, Int.MIN_VALUE)
        val coolingPercent = input.getIntExtra(EXTRA_AUTOMATION_COOLING_PERCENT, Int.MIN_VALUE)
        val noCoolingFirstLayers = input.getIntExtra(EXTRA_AUTOMATION_NO_COOLING_FIRST_LAYERS, Int.MIN_VALUE)
        val layerHeightMm = input.getFloatExtra(EXTRA_AUTOMATION_LAYER_HEIGHT_MM, Float.NaN)
        val firstLayerPrintSpeedMmPerSec = input.getFloatExtra(EXTRA_AUTOMATION_FIRST_LAYER_PRINT_SPEED_MM_PER_SEC, Float.NaN)
        val firstLayerInfillSpeedMmPerSec = input.getFloatExtra(EXTRA_AUTOMATION_FIRST_LAYER_INFILL_SPEED_MM_PER_SEC, Float.NaN)
        val firstLayerTravelSpeedPercent = input.getIntExtra(EXTRA_AUTOMATION_FIRST_LAYER_TRAVEL_SPEED_PERCENT, Int.MIN_VALUE)
        val slowDownLayers = input.getIntExtra(EXTRA_AUTOMATION_SLOW_DOWN_LAYERS, Int.MIN_VALUE)
        val outerWallSpeedMmPerSec = input.getFloatExtra(EXTRA_AUTOMATION_OUTER_WALL_SPEED_MM_PER_SEC, Float.NaN)
        val innerWallSpeedMmPerSec = input.getFloatExtra(EXTRA_AUTOMATION_INNER_WALL_SPEED_MM_PER_SEC, Float.NaN)
        val topSurfaceSpeedMmPerSec = input.getFloatExtra(EXTRA_AUTOMATION_TOP_SURFACE_SPEED_MM_PER_SEC, Float.NaN)
        val travelSpeedMmPerSec = input.getFloatExtra(EXTRA_AUTOMATION_TRAVEL_SPEED_MM_PER_SEC, Float.NaN)
        val outerWallAccelerationMmPerSec2 = input.getFloatExtra(EXTRA_AUTOMATION_OUTER_WALL_ACCELERATION_MM_PER_SEC2, Float.NaN)
        val innerWallAccelerationMmPerSec2 = input.getFloatExtra(EXTRA_AUTOMATION_INNER_WALL_ACCELERATION_MM_PER_SEC2, Float.NaN)
        val topSurfaceAccelerationMmPerSec2 = input.getFloatExtra(EXTRA_AUTOMATION_TOP_SURFACE_ACCELERATION_MM_PER_SEC2, Float.NaN)
        val sparseInfillAccelerationMmPerSec2 = input.getFloatExtra(EXTRA_AUTOMATION_SPARSE_INFILL_ACCELERATION_MM_PER_SEC2, Float.NaN)
        val bridgeAcceleration = input.getStringExtra(EXTRA_AUTOMATION_BRIDGE_ACCELERATION)
        val bridgeSpeedMmPerSec = input.getFloatExtra(EXTRA_AUTOMATION_BRIDGE_SPEED_MM_PER_SEC, Float.NaN)
        val bridgeAngleDegrees = input.getFloatExtra(EXTRA_AUTOMATION_BRIDGE_ANGLE_DEGREES, Float.NaN)
        val bridgeDensityPercent = input.getIntExtra(EXTRA_AUTOMATION_BRIDGE_DENSITY_PERCENT, Int.MIN_VALUE)
        val bridgeFlowRatio = input.getFloatExtra(EXTRA_AUTOMATION_BRIDGE_FLOW_RATIO, Float.NaN)
        val hasBridgeNoSupportOverride = input.hasExtra(EXTRA_AUTOMATION_BRIDGE_NO_SUPPORT)
        val bridgeNoSupport = input.getBooleanExtra(EXTRA_AUTOMATION_BRIDGE_NO_SUPPORT, false)
        val internalBridgeAngleDegrees = input.getFloatExtra(EXTRA_AUTOMATION_INTERNAL_BRIDGE_ANGLE_DEGREES, Float.NaN)
        val internalBridgeDensityPercent = input.getIntExtra(EXTRA_AUTOMATION_INTERNAL_BRIDGE_DENSITY_PERCENT, Int.MIN_VALUE)
        val internalBridgeFlowRatio = input.getFloatExtra(EXTRA_AUTOMATION_INTERNAL_BRIDGE_FLOW_RATIO, Float.NaN)
        val internalBridgeSpeed = input.getStringExtra(EXTRA_AUTOMATION_INTERNAL_BRIDGE_SPEED)
        val internalBridgeFanSpeed = input.getStringExtra(EXTRA_AUTOMATION_INTERNAL_BRIDGE_FAN_SPEED)
        val internalBridgeSupportThickness = input.getStringExtra(EXTRA_AUTOMATION_INTERNAL_BRIDGE_SUPPORT_THICKNESS)
        val smallPerimeterSpeedMmPerSec = input.getFloatExtra(EXTRA_AUTOMATION_SMALL_PERIMETER_SPEED_MM_PER_SEC, Float.NaN)
        val smallPerimeterThresholdMm = input.getFloatExtra(EXTRA_AUTOMATION_SMALL_PERIMETER_THRESHOLD_MM, Float.NaN)
        val sparseInfillSpeedMmPerSec = input.getFloatExtra(EXTRA_AUTOMATION_SPARSE_INFILL_SPEED_MM_PER_SEC, Float.NaN)
        val internalSolidInfillSpeedMmPerSec = input.getFloatExtra(EXTRA_AUTOMATION_INTERNAL_SOLID_INFILL_SPEED_MM_PER_SEC, Float.NaN)
        val gapInfillSpeedMmPerSec = input.getFloatExtra(EXTRA_AUTOMATION_GAP_INFILL_SPEED_MM_PER_SEC, Float.NaN)
        val topShellLayers = input.getIntExtra(EXTRA_AUTOMATION_TOP_SHELL_LAYERS, Int.MIN_VALUE)
        val bottomShellLayers = input.getIntExtra(EXTRA_AUTOMATION_BOTTOM_SHELL_LAYERS, Int.MIN_VALUE)
        val hasPreciseOuterWallOverride = input.hasExtra(EXTRA_AUTOMATION_PRECISE_OUTER_WALL)
        val preciseOuterWall = input.getBooleanExtra(EXTRA_AUTOMATION_PRECISE_OUTER_WALL, false)
        val hasOnlyOneWallTopOverride = input.hasExtra(EXTRA_AUTOMATION_ONLY_ONE_WALL_TOP)
        val onlyOneWallTop = input.getBooleanExtra(EXTRA_AUTOMATION_ONLY_ONE_WALL_TOP, false)
        val topSurfacePattern = input.getStringExtra(EXTRA_AUTOMATION_TOP_SURFACE_PATTERN)
        val bottomSurfacePattern = input.getStringExtra(EXTRA_AUTOMATION_BOTTOM_SURFACE_PATTERN)
        val internalSolidInfillPattern = input.getStringExtra(EXTRA_AUTOMATION_INTERNAL_SOLID_INFILL_PATTERN)
        val topSurfaceDensityPercent = input.getIntExtra(EXTRA_AUTOMATION_TOP_SURFACE_DENSITY_PERCENT, Int.MIN_VALUE)
        val bottomSurfaceDensityPercent = input.getIntExtra(EXTRA_AUTOMATION_BOTTOM_SURFACE_DENSITY_PERCENT, Int.MIN_VALUE)
        val solidInfillDirectionDegrees = input.getFloatExtra(EXTRA_AUTOMATION_SOLID_INFILL_DIRECTION_DEGREES, Float.NaN)
        val solidInfillRotateTemplate = input.getStringExtra(EXTRA_AUTOMATION_SOLID_INFILL_ROTATE_TEMPLATE)
        val lineWidth = input.getStringExtra(EXTRA_AUTOMATION_LINE_WIDTH)
        val outerWallLineWidth = input.getStringExtra(EXTRA_AUTOMATION_OUTER_WALL_LINE_WIDTH)
        val innerWallLineWidth = input.getStringExtra(EXTRA_AUTOMATION_INNER_WALL_LINE_WIDTH)
        val initialLayerLineWidth = input.getStringExtra(EXTRA_AUTOMATION_INITIAL_LAYER_LINE_WIDTH)
        val initialLayerMinBeadWidthPercent = input.getIntExtra(EXTRA_AUTOMATION_INITIAL_LAYER_MIN_BEAD_WIDTH_PERCENT, Int.MIN_VALUE)
        val topSurfaceLineWidth = input.getStringExtra(EXTRA_AUTOMATION_TOP_SURFACE_LINE_WIDTH)
        val internalSolidInfillLineWidth = input.getStringExtra(EXTRA_AUTOMATION_INTERNAL_SOLID_INFILL_LINE_WIDTH)
        val minWidthTopSurface = input.getStringExtra(EXTRA_AUTOMATION_MIN_WIDTH_TOP_SURFACE)
        val sparseInfillLineWidth = input.getStringExtra(EXTRA_AUTOMATION_SPARSE_INFILL_LINE_WIDTH)
        val infillDirectionDegrees = input.getFloatExtra(EXTRA_AUTOMATION_INFILL_DIRECTION_DEGREES, Float.NaN)
        val sparseInfillRotateTemplate = input.getStringExtra(EXTRA_AUTOMATION_SPARSE_INFILL_ROTATE_TEMPLATE)
        val hasAlignInfillDirectionToModelOverride = input.hasExtra(EXTRA_AUTOMATION_ALIGN_INFILL_DIRECTION_TO_MODEL)
        val alignInfillDirectionToModel = input.getBooleanExtra(EXTRA_AUTOMATION_ALIGN_INFILL_DIRECTION_TO_MODEL, false)
        val infillWallOverlapPercent = input.getIntExtra(EXTRA_AUTOMATION_INFILL_WALL_OVERLAP_PERCENT, Int.MIN_VALUE)
        val topBottomInfillWallOverlapPercent = input.getIntExtra(EXTRA_AUTOMATION_TOP_BOTTOM_INFILL_WALL_OVERLAP_PERCENT, Int.MIN_VALUE)
        val infillAnchor = input.getStringExtra(EXTRA_AUTOMATION_INFILL_ANCHOR)
        val infillAnchorMax = input.getStringExtra(EXTRA_AUTOMATION_INFILL_ANCHOR_MAX)
        val hasInfillCombinationOverride = input.hasExtra(EXTRA_AUTOMATION_INFILL_COMBINATION)
        val infillCombination = input.getBooleanExtra(EXTRA_AUTOMATION_INFILL_COMBINATION, false)
        val infillCombinationMaxLayerHeight = input.getStringExtra(EXTRA_AUTOMATION_INFILL_COMBINATION_MAX_LAYER_HEIGHT)
        val minimumSparseInfillAreaMm2 = input.getFloatExtra(EXTRA_AUTOMATION_MINIMUM_SPARSE_INFILL_AREA_MM2, Float.NaN)
        val hasDetectThinWallOverride = input.hasExtra(EXTRA_AUTOMATION_DETECT_THIN_WALL)
        val detectThinWall = input.getBooleanExtra(EXTRA_AUTOMATION_DETECT_THIN_WALL, false)
        val hasDetectOverhangWallOverride = input.hasExtra(EXTRA_AUTOMATION_DETECT_OVERHANG_WALL)
        val detectOverhangWall = input.getBooleanExtra(EXTRA_AUTOMATION_DETECT_OVERHANG_WALL, true)
        val hasThickBridgesOverride = input.hasExtra(EXTRA_AUTOMATION_THICK_BRIDGES)
        val thickBridges = input.getBooleanExtra(EXTRA_AUTOMATION_THICK_BRIDGES, false)
        val dontFilterInternalBridges = input.getStringExtra(EXTRA_AUTOMATION_DONT_FILTER_INTERNAL_BRIDGES)
        val wallGenerator = input.getStringExtra(EXTRA_AUTOMATION_WALL_GENERATOR)
        val wallInfillOrder = input.getStringExtra(EXTRA_AUTOMATION_WALL_INFILL_ORDER)
        val hasExtraPerimetersOnOverhangsOverride = input.hasExtra(EXTRA_AUTOMATION_EXTRA_PERIMETERS_ON_OVERHANGS)
        val extraPerimetersOnOverhangs = input.getBooleanExtra(EXTRA_AUTOMATION_EXTRA_PERIMETERS_ON_OVERHANGS, false)
        val wallCount = input.getIntExtra(EXTRA_AUTOMATION_WALL_COUNT, Int.MIN_VALUE)
        val infillPercent = input.getIntExtra(EXTRA_AUTOMATION_INFILL_PERCENT, Int.MIN_VALUE)
        val sparseInfillPattern = input.getStringExtra(EXTRA_AUTOMATION_SPARSE_INFILL_PATTERN)
        val hasEnableSupportOverride = input.hasExtra(EXTRA_AUTOMATION_ENABLE_SUPPORT)
        val enableSupport = input.getBooleanExtra(EXTRA_AUTOMATION_ENABLE_SUPPORT, false)
        val supportType = input.getStringExtra(EXTRA_AUTOMATION_SUPPORT_TYPE)
        val supportStyle = input.getStringExtra(EXTRA_AUTOMATION_SUPPORT_STYLE)
        val supportThresholdAngle = input.getIntExtra(EXTRA_AUTOMATION_SUPPORT_THRESHOLD_ANGLE, Int.MIN_VALUE)
        val hasSupportBuildplateOnlyOverride = input.hasExtra(EXTRA_AUTOMATION_SUPPORT_BUILDPLATE_ONLY)
        val supportBuildplateOnly = input.getBooleanExtra(EXTRA_AUTOMATION_SUPPORT_BUILDPLATE_ONLY, false)

        val needsPrinterOverride =
            duplicateActivePrinter ||
                !bedWidthMm.isNaN() ||
                !bedDepthMm.isNaN() ||
                !maxHeightMm.isNaN() ||
                !nozzleDiameterMm.isNaN() ||
                !filamentDiameterMm.isNaN()
        val needsFilamentOverride =
            duplicateActiveFilament ||
                !materialType.isNullOrBlank() ||
                !filamentMaxVolumetricSpeedMm3PerSec.isNaN() ||
                nozzleTemperatureC != Int.MIN_VALUE ||
                bedTemperatureC != Int.MIN_VALUE ||
                coolingPercent != Int.MIN_VALUE ||
                noCoolingFirstLayers != Int.MIN_VALUE
        val needsProcessOverride =
            duplicateActiveProcess ||
                !layerHeightMm.isNaN() ||
                !firstLayerPrintSpeedMmPerSec.isNaN() ||
                !firstLayerInfillSpeedMmPerSec.isNaN() ||
                firstLayerTravelSpeedPercent != Int.MIN_VALUE ||
                slowDownLayers != Int.MIN_VALUE ||
                !outerWallSpeedMmPerSec.isNaN() ||
                !innerWallSpeedMmPerSec.isNaN() ||
                !topSurfaceSpeedMmPerSec.isNaN() ||
                !travelSpeedMmPerSec.isNaN() ||
                !outerWallAccelerationMmPerSec2.isNaN() ||
                !innerWallAccelerationMmPerSec2.isNaN() ||
                !topSurfaceAccelerationMmPerSec2.isNaN() ||
                !sparseInfillAccelerationMmPerSec2.isNaN() ||
                !bridgeAcceleration.isNullOrBlank() ||
                !bridgeSpeedMmPerSec.isNaN() ||
                !bridgeAngleDegrees.isNaN() ||
                bridgeDensityPercent != Int.MIN_VALUE ||
                !bridgeFlowRatio.isNaN() ||
                !internalBridgeAngleDegrees.isNaN() ||
                internalBridgeDensityPercent != Int.MIN_VALUE ||
                !internalBridgeFlowRatio.isNaN() ||
                !internalBridgeSpeed.isNullOrBlank() ||
                !internalBridgeFanSpeed.isNullOrBlank() ||
                !internalBridgeSupportThickness.isNullOrBlank() ||
                hasBridgeNoSupportOverride ||
                !smallPerimeterSpeedMmPerSec.isNaN() ||
                !smallPerimeterThresholdMm.isNaN() ||
                !sparseInfillSpeedMmPerSec.isNaN() ||
                !internalSolidInfillSpeedMmPerSec.isNaN() ||
                !gapInfillSpeedMmPerSec.isNaN() ||
                topShellLayers != Int.MIN_VALUE ||
                bottomShellLayers != Int.MIN_VALUE ||
                topSurfaceDensityPercent != Int.MIN_VALUE ||
                bottomSurfaceDensityPercent != Int.MIN_VALUE ||
                hasPreciseOuterWallOverride ||
                hasOnlyOneWallTopOverride ||
                !topSurfacePattern.isNullOrBlank() ||
                !bottomSurfacePattern.isNullOrBlank() ||
                !internalSolidInfillPattern.isNullOrBlank() ||
                !solidInfillDirectionDegrees.isNaN() ||
                !solidInfillRotateTemplate.isNullOrBlank() ||
                !lineWidth.isNullOrBlank() ||
                !outerWallLineWidth.isNullOrBlank() ||
                !innerWallLineWidth.isNullOrBlank() ||
                !initialLayerLineWidth.isNullOrBlank() ||
                initialLayerMinBeadWidthPercent != Int.MIN_VALUE ||
                !topSurfaceLineWidth.isNullOrBlank() ||
                !internalSolidInfillLineWidth.isNullOrBlank() ||
                !minWidthTopSurface.isNullOrBlank() ||
                !sparseInfillLineWidth.isNullOrBlank() ||
                !infillDirectionDegrees.isNaN() ||
                !sparseInfillRotateTemplate.isNullOrBlank() ||
                hasAlignInfillDirectionToModelOverride ||
                infillWallOverlapPercent != Int.MIN_VALUE ||
                topBottomInfillWallOverlapPercent != Int.MIN_VALUE ||
                !infillAnchor.isNullOrBlank() ||
                !infillAnchorMax.isNullOrBlank() ||
                hasInfillCombinationOverride ||
                !infillCombinationMaxLayerHeight.isNullOrBlank() ||
                !minimumSparseInfillAreaMm2.isNaN() ||
                hasDetectThinWallOverride ||
                hasDetectOverhangWallOverride ||
                hasThickBridgesOverride ||
                !dontFilterInternalBridges.isNullOrBlank() ||
                !wallGenerator.isNullOrBlank() ||
                !wallInfillOrder.isNullOrBlank() ||
                hasExtraPerimetersOnOverhangsOverride ||
                wallCount != Int.MIN_VALUE ||
                infillPercent != Int.MIN_VALUE ||
                !sparseInfillPattern.isNullOrBlank() ||
                hasEnableSupportOverride ||
                !supportType.isNullOrBlank() ||
                !supportStyle.isNullOrBlank() ||
                supportThresholdAngle != Int.MIN_VALUE ||
                hasSupportBuildplateOnlyOverride

        if (!needsPrinterOverride && !needsFilamentOverride && !needsProcessOverride) {
            return loadProfileStore()
        }

        var updatedStore = loadProfileStore()
        if (needsPrinterOverride) {
            val active = updatedStore.activeConfiguration()
            val activePrinter = active.printer
            val printerId = input.getStringExtra(EXTRA_AUTOMATION_PRINTER_ID)
                ?: "automation_printer_${timestampMillis()}"
            val printerName = input.getStringExtra(EXTRA_AUTOMATION_PRINTER_NAME)
                ?: "Automation ${activePrinter.name}"
            val nextBedWidth = if (bedWidthMm.isNaN()) activePrinter.bedWidthMm else bedWidthMm
            val nextBedDepth = if (bedDepthMm.isNaN()) activePrinter.bedDepthMm else bedDepthMm
            val nextMaxHeight = if (maxHeightMm.isNaN()) activePrinter.maxHeightMm else maxHeightMm
            val nextNozzleDiameter = if (nozzleDiameterMm.isNaN()) activePrinter.nozzleDiameterMm else nozzleDiameterMm
            val nextFilamentDiameter = if (filamentDiameterMm.isNaN()) activePrinter.filamentDiameterMm else filamentDiameterMm
            val nextPrintableArea = "0x0,${nextBedWidth}x0,${nextBedWidth}x${nextBedDepth},0x${nextBedDepth}"
            val duplicatedPrinter = activePrinter.copy(
                id = printerId,
                name = printerName,
                subtitle = "Automated real-device validation printer profile.",
                builtIn = false,
                bedWidthMm = nextBedWidth,
                bedDepthMm = nextBedDepth,
                maxHeightMm = nextMaxHeight,
                nozzleDiameterMm = nextNozzleDiameter,
                filamentDiameterMm = nextFilamentDiameter,
                bedShape = nextPrintableArea,
                orcaResolvedMachineJson = activePrinter.orcaResolvedMachineJson.withJsonOverrides(
                    "bed_width_mm" to nextBedWidth,
                    "bed_depth_mm" to nextBedDepth,
                    "max_height_mm" to nextMaxHeight,
                    "printable_height" to nextMaxHeight,
                    "nozzle_diameter" to nextNozzleDiameter,
                    "filament_diameter" to nextFilamentDiameter,
                    "bed_shape" to nextPrintableArea,
                    "printable_area" to nextPrintableArea
                )
            )
            val dependentFilament = active.filament.copy(
                id = "${active.filament.id}_$printerId",
                builtIn = false,
                printerProfileId = printerId,
                diameterMm = nextFilamentDiameter,
                orcaResolvedFilamentJson = active.filament.orcaResolvedFilamentJson.withJsonOverrides(
                    "filament_diameter" to nextFilamentDiameter
                )
            )
            val dependentProcess = active.process.withValues(
                "id" to "${active.process.id}_$printerId",
                "builtIn" to false,
                "printerProfileId" to printerId,
                "nozzleDiameterMm" to nextNozzleDiameter
            )
            updatedStore = updatedStore.copy(
                printers = updatedStore.printers.filterNot { it.id == duplicatedPrinter.id } + duplicatedPrinter,
                filaments = updatedStore.filaments.filterNot { it.id == dependentFilament.id } + dependentFilament,
                processes = updatedStore.processes.filterNot { it.id == dependentProcess.id } + dependentProcess,
                selectedPrinterId = duplicatedPrinter.id,
                selectedFilamentId = dependentFilament.id,
                selectedProcessId = dependentProcess.id
            )
        }

        if (needsFilamentOverride) {
            val activeFilament = updatedStore.activeConfiguration().filament
            val filamentId = input.getStringExtra(EXTRA_AUTOMATION_FILAMENT_ID)
                ?: "automation_filament_${timestampMillis()}"
            val filamentName = input.getStringExtra(EXTRA_AUTOMATION_FILAMENT_NAME)
                ?: "Automation ${activeFilament.name}"
            val duplicatedFilament = activeFilament.copy(
                id = filamentId,
                name = filamentName,
                subtitle = "Automated real-device validation filament profile.",
                builtIn = false,
                printerProfileId = updatedStore.selectedPrinterId,
                materialType = materialType ?: activeFilament.materialType,
                maxVolumetricSpeedMm3PerSec = if (filamentMaxVolumetricSpeedMm3PerSec.isNaN()) activeFilament.maxVolumetricSpeedMm3PerSec else filamentMaxVolumetricSpeedMm3PerSec,
                nozzleTemperatureInitialLayerC = if (nozzleTemperatureC == Int.MIN_VALUE) activeFilament.nozzleTemperatureInitialLayerC else nozzleTemperatureC,
                nozzleTemperatureC = if (nozzleTemperatureC == Int.MIN_VALUE) activeFilament.nozzleTemperatureC else nozzleTemperatureC,
                bedTemperatureInitialLayerC = if (bedTemperatureC == Int.MIN_VALUE) activeFilament.bedTemperatureInitialLayerC else bedTemperatureC,
                bedTemperatureC = if (bedTemperatureC == Int.MIN_VALUE) activeFilament.bedTemperatureC else bedTemperatureC,
                coolingPercent = if (coolingPercent == Int.MIN_VALUE) activeFilament.coolingPercent else coolingPercent,
                noCoolingFirstLayers = if (noCoolingFirstLayers == Int.MIN_VALUE) activeFilament.noCoolingFirstLayers else noCoolingFirstLayers,
                orcaResolvedFilamentJson = activeFilament.orcaResolvedFilamentJson.withJsonOverrides(
                    "filament_type" to (materialType ?: activeFilament.materialType),
                    "filament_max_volumetric_speed" to if (filamentMaxVolumetricSpeedMm3PerSec.isNaN()) activeFilament.maxVolumetricSpeedMm3PerSec else filamentMaxVolumetricSpeedMm3PerSec,
                    "nozzle_temperature_initial_layer" to if (nozzleTemperatureC == Int.MIN_VALUE) activeFilament.nozzleTemperatureInitialLayerC else nozzleTemperatureC,
                    "nozzle_temperature" to if (nozzleTemperatureC == Int.MIN_VALUE) activeFilament.nozzleTemperatureC else nozzleTemperatureC,
                    "bed_temperature_initial_layer" to if (bedTemperatureC == Int.MIN_VALUE) activeFilament.bedTemperatureInitialLayerC else bedTemperatureC,
                    "bed_temperature" to if (bedTemperatureC == Int.MIN_VALUE) activeFilament.bedTemperatureC else bedTemperatureC,
                    "fan_max_speed" to if (coolingPercent == Int.MIN_VALUE) activeFilament.coolingPercent else coolingPercent,
                    "close_fan_the_first_x_layers" to if (noCoolingFirstLayers == Int.MIN_VALUE) activeFilament.noCoolingFirstLayers else noCoolingFirstLayers
                )
            )
            updatedStore = updatedStore.copy(
                filaments = updatedStore.filaments.filterNot { it.id == duplicatedFilament.id } + duplicatedFilament,
                selectedFilamentId = duplicatedFilament.id
            )
        }

        if (!needsProcessOverride) {
            return updatedStore
        }

        val active = updatedStore.activeConfiguration().process
        val processId = input.getStringExtra(EXTRA_AUTOMATION_PROCESS_ID)
            ?: "automation_${timestampMillis()}"
        val processName = input.getStringExtra(EXTRA_AUTOMATION_PROCESS_NAME)
            ?: "Automation ${active.name}"

        val duplicated = active.withValues(
            "id" to processId,
            "name" to processName,
            "subtitle" to "Automated real-device validation profile.",
            "builtIn" to false,
            "printerProfileId" to updatedStore.selectedPrinterId,
            "nozzleDiameterMm" to updatedStore.activeConfiguration().printer.nozzleDiameterMm,
            "firstLayerHeightMm" to active.firstLayerHeightMm,
            "layerHeightMm" to if (layerHeightMm.isNaN()) active.layerHeightMm else layerHeightMm,
            "firstLayerPrintSpeedMmPerSec" to if (firstLayerPrintSpeedMmPerSec.isNaN()) active.firstLayerPrintSpeedMmPerSec else firstLayerPrintSpeedMmPerSec,
            "firstLayerInfillSpeedMmPerSec" to if (firstLayerInfillSpeedMmPerSec.isNaN()) active.firstLayerInfillSpeedMmPerSec else firstLayerInfillSpeedMmPerSec,
            "firstLayerTravelSpeedPercent" to if (firstLayerTravelSpeedPercent == Int.MIN_VALUE) active.firstLayerTravelSpeedPercent else firstLayerTravelSpeedPercent,
            "slowDownLayers" to if (slowDownLayers == Int.MIN_VALUE) active.slowDownLayers else slowDownLayers,
            "outerWallSpeedMmPerSec" to if (outerWallSpeedMmPerSec.isNaN()) active.outerWallSpeedMmPerSec else outerWallSpeedMmPerSec,
            "innerWallSpeedMmPerSec" to if (innerWallSpeedMmPerSec.isNaN()) active.innerWallSpeedMmPerSec else innerWallSpeedMmPerSec,
            "topSurfaceSpeedMmPerSec" to if (topSurfaceSpeedMmPerSec.isNaN()) active.topSurfaceSpeedMmPerSec else topSurfaceSpeedMmPerSec,
            "travelSpeedMmPerSec" to if (travelSpeedMmPerSec.isNaN()) active.travelSpeedMmPerSec else travelSpeedMmPerSec,
            "outerWallAccelerationMmPerSec2" to if (outerWallAccelerationMmPerSec2.isNaN()) active.outerWallAccelerationMmPerSec2 else outerWallAccelerationMmPerSec2,
            "innerWallAccelerationMmPerSec2" to if (innerWallAccelerationMmPerSec2.isNaN()) active.innerWallAccelerationMmPerSec2 else innerWallAccelerationMmPerSec2,
            "topSurfaceAccelerationMmPerSec2" to if (topSurfaceAccelerationMmPerSec2.isNaN()) active.topSurfaceAccelerationMmPerSec2 else topSurfaceAccelerationMmPerSec2,
            "sparseInfillAccelerationMmPerSec2" to if (sparseInfillAccelerationMmPerSec2.isNaN()) active.sparseInfillAccelerationMmPerSec2 else sparseInfillAccelerationMmPerSec2,
            "bridgeAcceleration" to (bridgeAcceleration ?: active.bridgeAcceleration),
            "bridgeSpeedMmPerSec" to if (bridgeSpeedMmPerSec.isNaN()) active.bridgeSpeedMmPerSec else bridgeSpeedMmPerSec,
            "bridgeAngleDegrees" to if (bridgeAngleDegrees.isNaN()) active.bridgeAngleDegrees else bridgeAngleDegrees,
            "bridgeDensityPercent" to if (bridgeDensityPercent == Int.MIN_VALUE) active.bridgeDensityPercent else bridgeDensityPercent,
            "bridgeFlowRatio" to if (bridgeFlowRatio.isNaN()) active.bridgeFlowRatio else bridgeFlowRatio,
            "bridgeNoSupport" to if (hasBridgeNoSupportOverride) bridgeNoSupport else active.bridgeNoSupport,
            "internalBridgeAngleDegrees" to if (internalBridgeAngleDegrees.isNaN()) active.internalBridgeAngleDegrees else internalBridgeAngleDegrees,
            "internalBridgeDensityPercent" to if (internalBridgeDensityPercent == Int.MIN_VALUE) active.internalBridgeDensityPercent else internalBridgeDensityPercent,
            "internalBridgeFlowRatio" to if (internalBridgeFlowRatio.isNaN()) active.internalBridgeFlowRatio else internalBridgeFlowRatio,
            "internalBridgeSpeed" to (internalBridgeSpeed ?: active.internalBridgeSpeed),
            "internalBridgeFanSpeed" to (internalBridgeFanSpeed ?: active.internalBridgeFanSpeed),
            "internalBridgeSupportThickness" to (internalBridgeSupportThickness ?: active.internalBridgeSupportThickness),
            "smallPerimeterSpeedMmPerSec" to if (smallPerimeterSpeedMmPerSec.isNaN()) active.smallPerimeterSpeedMmPerSec else smallPerimeterSpeedMmPerSec,
            "smallPerimeterThresholdMm" to if (smallPerimeterThresholdMm.isNaN()) active.smallPerimeterThresholdMm else smallPerimeterThresholdMm,
            "sparseInfillSpeedMmPerSec" to if (sparseInfillSpeedMmPerSec.isNaN()) active.sparseInfillSpeedMmPerSec else sparseInfillSpeedMmPerSec,
            "internalSolidInfillSpeedMmPerSec" to if (internalSolidInfillSpeedMmPerSec.isNaN()) active.internalSolidInfillSpeedMmPerSec else internalSolidInfillSpeedMmPerSec,
            "gapInfillSpeedMmPerSec" to if (gapInfillSpeedMmPerSec.isNaN()) active.gapInfillSpeedMmPerSec else gapInfillSpeedMmPerSec,
            "topShellLayers" to if (topShellLayers == Int.MIN_VALUE) active.topShellLayers else topShellLayers,
            "bottomShellLayers" to if (bottomShellLayers == Int.MIN_VALUE) active.bottomShellLayers else bottomShellLayers,
            "topSurfaceDensityPercent" to if (topSurfaceDensityPercent == Int.MIN_VALUE) active.topSurfaceDensityPercent else topSurfaceDensityPercent,
            "bottomSurfaceDensityPercent" to if (bottomSurfaceDensityPercent == Int.MIN_VALUE) active.bottomSurfaceDensityPercent else bottomSurfaceDensityPercent,
            "preciseOuterWall" to if (hasPreciseOuterWallOverride) preciseOuterWall else active.preciseOuterWall,
            "onlyOneWallTopSurfaces" to if (hasOnlyOneWallTopOverride) onlyOneWallTop else active.onlyOneWallTopSurfaces,
            "topSurfacePattern" to (topSurfacePattern?.let { TopSurfacePattern.fromConfigValue(it) } ?: active.topSurfacePattern),
            "bottomSurfacePattern" to (bottomSurfacePattern?.let { BottomSurfacePattern.fromConfigValue(it) } ?: active.bottomSurfacePattern),
            "internalSolidInfillPattern" to (internalSolidInfillPattern?.let { InternalSolidInfillPattern.fromConfigValue(it) } ?: active.internalSolidInfillPattern),
            "solidInfillDirectionDegrees" to if (solidInfillDirectionDegrees.isNaN()) active.solidInfillDirectionDegrees else solidInfillDirectionDegrees,
            "solidInfillRotateTemplate" to (solidInfillRotateTemplate ?: active.solidInfillRotateTemplate),
            "lineWidth" to (lineWidth ?: active.lineWidth),
            "outerWallLineWidth" to (outerWallLineWidth ?: active.outerWallLineWidth),
            "innerWallLineWidth" to (innerWallLineWidth ?: active.innerWallLineWidth),
            "initialLayerLineWidth" to (initialLayerLineWidth ?: active.initialLayerLineWidth),
            "initialLayerMinBeadWidthPercent" to if (initialLayerMinBeadWidthPercent == Int.MIN_VALUE) active.initialLayerMinBeadWidthPercent else initialLayerMinBeadWidthPercent,
            "topSurfaceLineWidth" to (topSurfaceLineWidth ?: active.topSurfaceLineWidth),
            "internalSolidInfillLineWidth" to (internalSolidInfillLineWidth ?: active.internalSolidInfillLineWidth),
            "minWidthTopSurface" to (minWidthTopSurface ?: active.minWidthTopSurface),
            "sparseInfillLineWidth" to (sparseInfillLineWidth ?: active.sparseInfillLineWidth),
            "infillDirectionDegrees" to if (infillDirectionDegrees.isNaN()) active.infillDirectionDegrees else infillDirectionDegrees,
            "sparseInfillRotateTemplate" to (sparseInfillRotateTemplate ?: active.sparseInfillRotateTemplate),
            "alignInfillDirectionToModel" to if (hasAlignInfillDirectionToModelOverride) alignInfillDirectionToModel else active.alignInfillDirectionToModel,
            "infillWallOverlapPercent" to if (infillWallOverlapPercent == Int.MIN_VALUE) active.infillWallOverlapPercent else infillWallOverlapPercent,
            "topBottomInfillWallOverlapPercent" to if (topBottomInfillWallOverlapPercent == Int.MIN_VALUE) active.topBottomInfillWallOverlapPercent else topBottomInfillWallOverlapPercent,
            "infillAnchor" to (infillAnchor ?: active.infillAnchor),
            "infillAnchorMax" to (infillAnchorMax ?: active.infillAnchorMax),
            "infillCombination" to if (hasInfillCombinationOverride) infillCombination else active.infillCombination,
            "infillCombinationMaxLayerHeight" to (infillCombinationMaxLayerHeight ?: active.infillCombinationMaxLayerHeight),
            "minimumSparseInfillAreaMm2" to if (minimumSparseInfillAreaMm2.isNaN()) active.minimumSparseInfillAreaMm2 else minimumSparseInfillAreaMm2,
            "detectThinWall" to if (hasDetectThinWallOverride) detectThinWall else active.detectThinWall,
            "detectOverhangWall" to if (hasDetectOverhangWallOverride) detectOverhangWall else active.detectOverhangWall,
            "thickBridges" to if (hasThickBridgesOverride) thickBridges else active.thickBridges,
            "dontFilterInternalBridges" to (dontFilterInternalBridges?.let { InternalBridgeFilterMode.fromConfigValue(it) } ?: active.dontFilterInternalBridges),
            "wallGenerator" to (wallGenerator?.let { WallGenerator.fromConfigValue(it) } ?: active.wallGenerator),
            "wallInfillOrder" to (wallInfillOrder?.let { WallInfillOrder.fromConfigValue(it) } ?: active.wallInfillOrder),
            "extraPerimetersOnOverhangs" to if (hasExtraPerimetersOnOverhangsOverride) extraPerimetersOnOverhangs else active.extraPerimetersOnOverhangs,
            "wallCount" to if (wallCount == Int.MIN_VALUE) active.wallCount else wallCount,
            "infillPercent" to if (infillPercent == Int.MIN_VALUE) active.infillPercent else infillPercent,
            "sparseInfillPattern" to (sparseInfillPattern?.let { SparseInfillPattern.fromConfigValue(it) } ?: active.sparseInfillPattern),
            "enableSupport" to if (hasEnableSupportOverride) enableSupport else active.enableSupport,
            "supportType" to (supportType?.let { SupportType.fromConfigValue(it) } ?: active.supportType),
            "supportStyle" to (supportStyle?.let { SupportStyle.fromConfigValue(it) } ?: active.supportStyle),
            "supportThresholdAngleDegrees" to if (supportThresholdAngle == Int.MIN_VALUE) active.supportThresholdAngleDegrees else supportThresholdAngle,
            "supportBuildplateOnly" to if (hasSupportBuildplateOnlyOverride) supportBuildplateOnly else active.supportBuildplateOnly,
            "orcaResolvedProcessJson" to active.orcaResolvedProcessJson.withJsonOverrides(
                "initial_layer_print_height" to active.firstLayerHeightMm,
                "layer_height" to if (layerHeightMm.isNaN()) active.layerHeightMm else layerHeightMm,
                "initial_layer_speed" to if (firstLayerPrintSpeedMmPerSec.isNaN()) active.firstLayerPrintSpeedMmPerSec else firstLayerPrintSpeedMmPerSec,
                "initial_layer_infill_speed" to if (firstLayerInfillSpeedMmPerSec.isNaN()) active.firstLayerInfillSpeedMmPerSec else firstLayerInfillSpeedMmPerSec,
                "initial_layer_travel_speed" to "${if (firstLayerTravelSpeedPercent == Int.MIN_VALUE) active.firstLayerTravelSpeedPercent else firstLayerTravelSpeedPercent}%",
                "outer_wall_speed" to if (outerWallSpeedMmPerSec.isNaN()) active.outerWallSpeedMmPerSec else outerWallSpeedMmPerSec,
                "inner_wall_speed" to if (innerWallSpeedMmPerSec.isNaN()) active.innerWallSpeedMmPerSec else innerWallSpeedMmPerSec,
                "top_surface_speed" to if (topSurfaceSpeedMmPerSec.isNaN()) active.topSurfaceSpeedMmPerSec else topSurfaceSpeedMmPerSec,
                "travel_speed" to if (travelSpeedMmPerSec.isNaN()) active.travelSpeedMmPerSec else travelSpeedMmPerSec,
                "outer_wall_acceleration" to if (outerWallAccelerationMmPerSec2.isNaN()) active.outerWallAccelerationMmPerSec2 else outerWallAccelerationMmPerSec2,
                "inner_wall_acceleration" to if (innerWallAccelerationMmPerSec2.isNaN()) active.innerWallAccelerationMmPerSec2 else innerWallAccelerationMmPerSec2,
                "top_surface_acceleration" to if (topSurfaceAccelerationMmPerSec2.isNaN()) active.topSurfaceAccelerationMmPerSec2 else topSurfaceAccelerationMmPerSec2,
                "sparse_infill_acceleration" to if (sparseInfillAccelerationMmPerSec2.isNaN()) active.sparseInfillAccelerationMmPerSec2 else sparseInfillAccelerationMmPerSec2,
                "wall_loops" to if (wallCount == Int.MIN_VALUE) active.wallCount else wallCount,
                "sparse_infill_density" to if (infillPercent == Int.MIN_VALUE) active.infillPercent else "$infillPercent%",
                "sparse_infill_pattern" to (sparseInfillPattern ?: active.sparseInfillPattern.configValue),
                "bridge_speed" to if (bridgeSpeedMmPerSec.isNaN()) active.bridgeSpeedMmPerSec else bridgeSpeedMmPerSec,
                "bridge_no_support" to if (hasBridgeNoSupportOverride) bridgeNoSupport else active.bridgeNoSupport,
                "small_perimeter_speed" to if (smallPerimeterSpeedMmPerSec.isNaN()) active.smallPerimeterSpeedMmPerSec else smallPerimeterSpeedMmPerSec,
                "small_perimeter_threshold" to if (smallPerimeterThresholdMm.isNaN()) active.smallPerimeterThresholdMm else smallPerimeterThresholdMm,
                "sparse_infill_speed" to if (sparseInfillSpeedMmPerSec.isNaN()) active.sparseInfillSpeedMmPerSec else sparseInfillSpeedMmPerSec,
                "internal_solid_infill_speed" to if (internalSolidInfillSpeedMmPerSec.isNaN()) active.internalSolidInfillSpeedMmPerSec else internalSolidInfillSpeedMmPerSec,
                "gap_infill_speed" to if (gapInfillSpeedMmPerSec.isNaN()) active.gapInfillSpeedMmPerSec else gapInfillSpeedMmPerSec,
                "top_shell_layers" to if (topShellLayers == Int.MIN_VALUE) active.topShellLayers else topShellLayers,
                "bottom_shell_layers" to if (bottomShellLayers == Int.MIN_VALUE) active.bottomShellLayers else bottomShellLayers,
                "enable_support" to if (hasEnableSupportOverride) enableSupport else active.enableSupport,
                "support_type" to (supportType ?: active.supportType.configValue),
                "support_style" to (supportStyle ?: active.supportStyle.configValue),
                "support_threshold_angle" to if (supportThresholdAngle == Int.MIN_VALUE) active.supportThresholdAngleDegrees else supportThresholdAngle,
                "support_on_build_plate_only" to if (hasSupportBuildplateOnlyOverride) supportBuildplateOnly else active.supportBuildplateOnly,
                "skirt_loops" to active.skirts
            )
        ).withChangedNativeProcessOverridesFrom(active)

        updatedStore = updatedStore.copy(
            processes = updatedStore.processes.filterNot { it.id == duplicated.id } + duplicated,
            selectedProcessId = duplicated.id
        )
        return updatedStore
    }
}

private fun String.withJsonOverrides(vararg overrides: Pair<String, Any?>): String {
    if (isBlank()) return this
    val json = runCatching { JSONObject(this) }.getOrNull() ?: return this
    overrides.forEach { (key, value) ->
        if (value != null) {
            json.put(key, value)
        }
    }
    return json.toString()
}
