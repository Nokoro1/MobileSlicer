package com.mobileslicer.profiles

internal enum class ProcessSeamPosition(
    val configValue: String,
    val displayLabel: String,
    val detailLabel: String
) {
    Nearest("nearest", "Nearest", "Nearest seam"),
    Aligned("aligned", "Aligned", "Aligned seam"),
    AlignedBack("aligned_back", "Aligned back", "Aligned-back seam"),
    Back("back", "Back", "Back seam"),
    Random("random", "Random", "Random seam");

    companion object {
        fun fromConfigValue(value: String?): ProcessSeamPosition =
            entries.firstOrNull { it.configValue == value } ?: Aligned
    }
}

internal enum class TopSurfacePattern(
    val configValue: String,
    val displayLabel: String,
    val detailLabel: String
) {
    MonotonicLine("monotonicline", "Monotonic line", "Monotonic-line top surface"),
    Monotonic("monotonic", "Monotonic", "Monotonic top surface"),
    Rectilinear("rectilinear", "Rectilinear", "Rectilinear top surface"),
    AlignedRectilinear("alignedrectilinear", "Aligned Rectilinear", "Aligned-rectilinear top surface"),
    Concentric("concentric", "Concentric", "Concentric top surface"),
    HilbertCurve("hilbertcurve", "Hilbert Curve", "Hilbert-curve top surface"),
    ArchimedeanChords("archimedeanchords", "Archimedean Chords", "Archimedean-chords top surface"),
    OctagramSpiral("octagramspiral", "Octagram Spiral", "Octagram-spiral top surface");

    companion object {
        fun fromConfigValue(value: String?): TopSurfacePattern =
            entries.firstOrNull { it.configValue == value } ?: MonotonicLine
    }
}

internal enum class BottomSurfacePattern(
    val configValue: String,
    val displayLabel: String,
    val detailLabel: String
) {
    Monotonic("monotonic", "Monotonic", "Monotonic bottom surface"),
    MonotonicLine("monotonicline", "Monotonic line", "Monotonic-line bottom surface"),
    Rectilinear("rectilinear", "Rectilinear", "Rectilinear bottom surface"),
    AlignedRectilinear("alignedrectilinear", "Aligned Rectilinear", "Aligned-rectilinear bottom surface"),
    Concentric("concentric", "Concentric", "Concentric bottom surface"),
    HilbertCurve("hilbertcurve", "Hilbert Curve", "Hilbert-curve bottom surface"),
    ArchimedeanChords("archimedeanchords", "Archimedean Chords", "Archimedean-chords bottom surface"),
    OctagramSpiral("octagramspiral", "Octagram Spiral", "Octagram-spiral bottom surface");

    companion object {
        fun fromConfigValue(value: String?): BottomSurfacePattern =
            entries.firstOrNull { it.configValue == value } ?: MonotonicLine
    }
}

internal enum class InternalSolidInfillPattern(
    val configValue: String,
    val displayLabel: String,
    val detailLabel: String
) {
    Monotonic("monotonic", "Monotonic", "Monotonic internal solid infill"),
    MonotonicLine("monotonicline", "Monotonic line", "Monotonic-line internal solid infill"),
    Rectilinear("rectilinear", "Rectilinear", "Rectilinear internal solid infill"),
    AlignedRectilinear("alignedrectilinear", "Aligned Rectilinear", "Aligned-rectilinear internal solid infill"),
    ZigZag("zigzag", "Zig Zag", "Zig-zag internal solid infill"),
    Concentric("concentric", "Concentric", "Concentric internal solid infill");

    companion object {
        fun fromConfigValue(value: String?): InternalSolidInfillPattern =
            entries.firstOrNull { it.configValue == value } ?: MonotonicLine
    }
}

internal enum class SparseInfillPattern(
    val configValue: String,
    val displayLabel: String,
    val detailLabel: String
) {
    Rectilinear("rectilinear", "Rectilinear", "Rectilinear sparse infill"),
    AlignedRectilinear("alignedrectilinear", "Aligned Rectilinear", "Aligned-rectilinear sparse infill"),
    ZigZag("zigzag", "Zig Zag", "Zig-zag sparse infill"),
    CrossZag("crosszag", "Cross Zag", "Cross-zag sparse infill"),
    LockedZag("lockedzag", "Locked Zag", "Locked-zag sparse infill"),
    Line("line", "Line", "Line sparse infill"),
    Grid("grid", "Grid", "Grid sparse infill"),
    Triangles("triangles", "Triangles", "Triangles sparse infill"),
    TriHexagon("tri-hexagon", "Tri-hexagon", "Tri-hexagon sparse infill"),
    Cubic("cubic", "Cubic", "Cubic sparse infill"),
    AdaptiveCubic("adaptivecubic", "Adaptive Cubic", "Adaptive-cubic sparse infill"),
    QuarterCubic("quartercubic", "Quarter Cubic", "Quarter-cubic sparse infill"),
    SupportCubic("supportcubic", "Support Cubic", "Support-cubic sparse infill"),
    Lightning("lightning", "Lightning", "Lightning sparse infill"),
    Honeycomb("honeycomb", "Honeycomb", "Honeycomb sparse infill"),
    ThreeDHoneycomb("3dhoneycomb", "3D Honeycomb", "3D-honeycomb sparse infill"),
    LateralHoneycomb("lateral-honeycomb", "Lateral Honeycomb", "Lateral-honeycomb sparse infill"),
    LateralLattice("lateral-lattice", "Lateral Lattice", "Lateral-lattice sparse infill"),
    CrossHatch("crosshatch", "Cross Hatch", "Cross-hatch sparse infill"),
    TpmsD("tpmsd", "TPMS-D", "TPMS-D sparse infill"),
    TpmsFk("tpmsfk", "TPMS-FK", "TPMS-FK sparse infill"),
    Gyroid("gyroid", "Gyroid", "Gyroid sparse infill"),
    Concentric("concentric", "Concentric", "Concentric sparse infill"),
    HilbertCurve("hilbertcurve", "Hilbert Curve", "Hilbert-curve sparse infill"),
    ArchimedeanChords("archimedeanchords", "Archimedean Chords", "Archimedean-chords sparse infill"),
    OctagramSpiral("octagramspiral", "Octagram Spiral", "Octagram-spiral sparse infill");

    companion object {
        fun fromConfigValue(value: String?): SparseInfillPattern =
            entries.firstOrNull { it.configValue == value } ?: Grid
    }
}

internal enum class WallGenerator(
    val configValue: String,
    val displayLabel: String,
    val description: String
) {
    Classic("classic", "Classic", "Classic wall generator with constant line width"),
    Arachne("arachne", "Arachne", "Arachne wall generator with variable line width");

    companion object {
        fun fromConfigValue(value: String?): WallGenerator =
            entries.firstOrNull { it.configValue == value } ?: Classic
    }
}

internal enum class WallInfillOrder(
    val configValue: String,
    val displayLabel: String,
    val description: String
) {
    InnerOuterInfill(
        "inner wall/outer wall/infill",
        "Inner / Outer / Infill",
        "Print inner wall, then outer wall, then infill"
    ),
    OuterInnerInfill(
        "outer wall/inner wall/infill",
        "Outer / Inner / Infill",
        "Print outer wall, then inner wall, then infill"
    ),
    InnerOuterInnerInfill(
        "inner-outer-inner wall/infill",
        "Inner / Outer / Inner / Infill",
        "Print inner, outer, inner wall, then infill"
    ),
    InfillInnerOuter(
        "infill/inner wall/outer wall",
        "Infill / Inner / Outer",
        "Print infill before inner and outer walls"
    ),
    InfillOuterInner(
        "infill/outer wall/inner wall",
        "Infill / Outer / Inner",
        "Print infill before outer and inner walls"
    );

    companion object {
        fun fromConfigValue(value: String?): WallInfillOrder =
            entries.firstOrNull { it.configValue == value } ?: InnerOuterInfill
    }
}

internal enum class WallSequence(
    val configValue: String,
    val displayLabel: String
) {
    InnerOuter("inner wall/outer wall", "Inner / Outer"),
    OuterInner("outer wall/inner wall", "Outer / Inner"),
    InnerOuterInner("inner-outer-inner wall", "Inner / Outer / Inner");

    companion object {
        fun fromConfigValue(value: String?): WallSequence =
            entries.firstOrNull { it.configValue == value } ?: InnerOuter
    }
}

internal enum class WallDirection(
    val configValue: String,
    val displayLabel: String
) {
    Auto("auto", "Auto"),
    CounterClockwise("ccw", "Counter clockwise"),
    Clockwise("cw", "Clockwise");

    companion object {
        fun fromConfigValue(value: String?): WallDirection =
            entries.firstOrNull { it.configValue == value } ?: Auto
    }
}

internal enum class InternalBridgeFilterMode(
    val configValue: String,
    val displayLabel: String,
    val description: String
) {
    Filter("disabled", "Filter", "Default filtering for small internal bridges"),
    Limited("limited", "Limited filtering", "Create internal bridges on more difficult overhang regions only"),
    NoFilter("nofilter", "No filtering", "Create internal bridges for every potential internal overhang");

    companion object {
        fun fromConfigValue(value: String?): InternalBridgeFilterMode =
            entries.firstOrNull { it.configValue == value } ?: Filter
    }
}

internal enum class ExtraBridgeLayerMode(
    val configValue: String,
    val displayLabel: String
) {
    Disabled("disabled", "Disabled"),
    ExternalBridgeOnly("external_bridge_only", "External bridge only"),
    InternalBridgeOnly("internal_bridge_only", "Internal bridge only"),
    ApplyToAll("apply_to_all", "Apply to all");

    companion object {
        fun fromConfigValue(value: String?): ExtraBridgeLayerMode =
            entries.firstOrNull { it.configValue == value } ?: Disabled
    }
}

internal enum class EnsureVerticalShellThicknessMode(
    val configValue: String,
    val displayLabel: String,
    val detailLabel: String
) {
    None("none", "None", "Do not add compensating solid infill near sloped shells"),
    CriticalOnly("ensure_critical_only", "Critical only", "Only add compensating solid infill for critical shell cases"),
    Moderate("ensure_moderate", "Moderate", "Add compensating solid infill for more heavily sloped shells"),
    All("ensure_all", "All", "Add compensating solid infill for all suitable sloped shells");

    companion object {
        fun fromConfigValue(value: String?): EnsureVerticalShellThicknessMode =
            entries.firstOrNull { it.configValue == value } ?: All
    }
}

internal enum class SeamScarfType(
    val configValue: String,
    val displayLabel: String
) {
    None("none", "None"),
    External("external", "Contour"),
    All("all", "Contour and hole");

    companion object {
        fun fromConfigValue(value: String?): SeamScarfType =
            entries.firstOrNull { it.configValue == value } ?: None
    }
}

internal enum class SkirtType(
    val configValue: String,
    val displayLabel: String
) {
    Combined("combined", "Combined"),
    PerObject("perobject", "Per object");

    companion object {
        fun fromConfigValue(value: String?): SkirtType =
            entries.firstOrNull { it.configValue == value } ?: Combined
    }
}

internal enum class DraftShield(
    val configValue: String,
    val displayLabel: String
) {
    Disabled("disabled", "Disabled"),
    Enabled("enabled", "Enabled");

    companion object {
        fun fromConfigValue(value: String?): DraftShield =
            entries.firstOrNull { it.configValue == value } ?: Disabled
    }
}

internal enum class BrimType(
    val configValue: String,
    val displayLabel: String
) {
    Auto("auto_brim", "Auto"),
    MouseEar("brim_ears", "Mouse ear"),
    Painted("painted", "Painted"),
    OuterOnly("outer_only", "Outer brim only"),
    InnerOnly("inner_only", "Inner brim only"),
    OuterAndInner("outer_and_inner", "Outer and inner brim"),
    NoBrim("no_brim", "No brim");

    companion object {
        fun fromConfigValue(value: String?): BrimType =
            entries.firstOrNull { it.configValue == value } ?: Auto
    }
}

internal enum class PrintSequence(
    val configValue: String,
    val displayLabel: String
) {
    ByLayer("by layer", "By layer"),
    ByObject("by object", "By object");

    companion object {
        fun fromConfigValue(value: String?): PrintSequence =
            entries.firstOrNull { it.configValue == value } ?: ByLayer
    }
}

internal enum class SlicingMode(
    val configValue: String,
    val displayLabel: String
) {
    Regular("regular", "Regular"),
    EvenOdd("even_odd", "Even-odd"),
    CloseHoles("close_holes", "Close holes");

    companion object {
        fun fromConfigValue(value: String?): SlicingMode =
            entries.firstOrNull { it.configValue == value } ?: Regular
    }
}

internal enum class PrintOrder(
    val configValue: String,
    val displayLabel: String
) {
    Default("default", "Default"),
    AsObjectList("as_obj_list", "As object list");

    companion object {
        fun fromConfigValue(value: String?): PrintOrder =
            entries.firstOrNull { it.configValue == value } ?: Default
    }
}

internal enum class WipeTowerWallType(
    val configValue: String,
    val displayLabel: String
) {
    Rectangle("rectangle", "Rectangle"),
    Cone("cone", "Cone"),
    Rib("rib", "Rib");

    companion object {
        fun fromConfigValue(value: String?): WipeTowerWallType =
            entries.firstOrNull { it.configValue == value } ?: Rectangle
    }
}

internal enum class TimelapseType(
    val configValue: String,
    val displayLabel: String
) {
    Traditional("0", "Traditional"),
    Smooth("1", "Smooth");

    companion object {
        fun fromConfigValue(value: String?): TimelapseType =
            entries.firstOrNull { it.configValue == value } ?: Traditional
    }
}


internal enum class CounterboreHoleBridging(
    val configValue: String,
    val displayLabel: String
) {
    None("none", "None"),
    PartiallyBridged("partiallybridge", "Partially bridged"),
    SacrificialLayer("sacrificiallayer", "Sacrificial layer");

    companion object {
        fun fromConfigValue(value: String?): CounterboreHoleBridging =
            entries.firstOrNull { it.configValue == value } ?: None
    }
}

internal enum class SupportType(
    val configValue: String,
    val displayLabel: String,
    val detailLabel: String
) {
    NormalAuto("normal(auto)", "Normal (auto)", "Automatic normal support generation"),
    TreeAuto("tree(auto)", "Tree (auto)", "Automatic tree support generation"),
    NormalManual("normal(manual)", "Normal (manual)", "Only normal support enforcers are generated"),
    TreeManual("tree(manual)", "Tree (manual)", "Only tree support enforcers are generated");

    companion object {
        fun fromConfigValue(value: String?): SupportType =
            entries.firstOrNull { it.configValue == value } ?: NormalAuto
    }
}

internal enum class SupportStyle(
    val configValue: String,
    val displayLabel: String,
    val detailLabel: String
) {
    Default("default", "Default", "Default style for the chosen support type"),
    Grid("grid", "Grid", "Regular grid support style"),
    Snug("snug", "Snug", "Material-saving snug support towers"),
    Organic("organic", "Organic", "Organic tree support style"),
    TreeSlim("tree_slim", "Tree Slim", "Slim tree support style"),
    TreeStrong("tree_strong", "Tree Strong", "Stronger tree support branches"),
    TreeHybrid("tree_hybrid", "Tree Hybrid", "Hybrid tree support for larger flat overhangs");

    companion object {
        fun fromConfigValue(value: String?): SupportStyle =
            entries.firstOrNull { it.configValue == value } ?: Default
    }
}

internal enum class SupportInterfacePattern(
    val configValue: String,
    val displayLabel: String,
    val detailLabel: String
) {
    Auto("auto", "Default", "Default support interface pattern"),
    Rectilinear("rectilinear", "Rectilinear", "Rectilinear support interface pattern"),
    Concentric("concentric", "Concentric", "Concentric support interface pattern"),
    RectilinearInterlaced("rectilinear_interlaced", "Rectilinear Interlaced", "Rectilinear-interlaced support interface pattern"),
    Grid("grid", "Grid", "Grid support interface pattern");

    companion object {
        fun fromConfigValue(value: String?): SupportInterfacePattern =
            entries.firstOrNull { it.configValue == value } ?: Auto
    }
}

internal enum class SupportBasePattern(
    val configValue: String,
    val displayLabel: String,
    val detailLabel: String
) {
    Default("default", "Default", "Default support base pattern"),
    Rectilinear("rectilinear", "Rectilinear", "Rectilinear support base pattern"),
    RectilinearGrid("rectilinear-grid", "Rectilinear Grid", "Rectilinear-grid support base pattern"),
    Honeycomb("honeycomb", "Honeycomb", "Honeycomb support base pattern"),
    Lightning("lightning", "Lightning", "Lightning support base pattern"),
    Hollow("hollow", "Hollow", "Hollow support base pattern");

    companion object {
        fun fromConfigValue(value: String?): SupportBasePattern =
            entries.firstOrNull { it.configValue == value } ?: Default
    }
}

internal enum class FuzzySkinType(
    val configValue: String,
    val displayLabel: String,
    val detailLabel: String
) {
    Disabled("disabled_fuzzy", "Disabled", "Fuzzy skin disabled"),
    PaintedOnly("none", "Painted only", "Only painted fuzzy-skin facets"),
    Contour("external", "Contour", "External contours"),
    ContourAndHole("all", "Contour and hole", "Contours and holes"),
    AllWalls("allwalls", "All walls", "All walls");

    companion object {
        fun fromConfigValue(value: String?): FuzzySkinType =
            entries.firstOrNull { it.configValue == value } ?: Disabled
    }
}

internal enum class FuzzySkinMode(
    val configValue: String,
    val displayLabel: String
) {
    Displacement("displacement", "Displacement"),
    Extrusion("extrusion", "Extrusion"),
    Combined("combined", "Combined");

    companion object {
        fun fromConfigValue(value: String?): FuzzySkinMode =
            entries.firstOrNull { it.configValue == value } ?: Displacement
    }
}

internal enum class FuzzySkinNoiseType(
    val configValue: String,
    val displayLabel: String
) {
    Classic("classic", "Classic"),
    Perlin("perlin", "Perlin"),
    Billow("billow", "Billow"),
    RidgedMultifractal("ridgedmulti", "Ridged multifractal"),
    Voronoi("voronoi", "Voronoi");

    companion object {
        fun fromConfigValue(value: String?): FuzzySkinNoiseType =
            entries.firstOrNull { it.configValue == value } ?: Classic
    }
}

internal enum class ProcessIroningType(
    val configValue: String,
    val displayLabel: String
) {
    NoIroning("no ironing", "No ironing"),
    TopSurfaces("top", "Top surfaces"),
    TopmostSurface("topmost", "Topmost surface"),
    AllSolidLayers("solid", "All solid layers");

    companion object {
        fun fromConfigValue(value: String?): ProcessIroningType =
            entries.firstOrNull { it.configValue == value } ?: NoIroning
    }
}

internal enum class IroningPattern(
    val configValue: String,
    val displayLabel: String
) {
    Rectilinear("rectilinear", "Rectilinear"),
    Concentric("concentric", "Concentric");

    companion object {
        fun fromConfigValue(value: String?): IroningPattern =
            entries.firstOrNull { it.configValue == value } ?: Rectilinear
    }
}

internal enum class FilamentMapMode(
    val configValue: String,
    val displayLabel: String
) {
    AutoForFlush("Auto For Flush", "Auto for flush"),
    AutoForMatch("Auto For Match", "Auto for match"),
    Manual("Manual", "Manual"),
    Default("Default", "Default");

    companion object {
        fun fromConfigValue(value: String?): FilamentMapMode =
            entries.firstOrNull { it.configValue == value } ?: AutoForFlush
    }
}
