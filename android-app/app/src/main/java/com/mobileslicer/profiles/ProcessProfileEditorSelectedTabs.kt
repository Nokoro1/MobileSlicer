package com.mobileslicer.profiles

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import com.mobileslicer.AppSettingOption
import com.mobileslicer.R
import com.mobileslicer.SoftPill
import com.mobileslicer.appBackgroundGradient
import com.mobileslicer.appBodyColor
import com.mobileslicer.appCardColor
import com.mobileslicer.appCardColorMuted
import com.mobileslicer.appMutedColor
import com.mobileslicer.appOutlineColor
import com.mobileslicer.appTitleColor
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import com.mobileslicer.viewer.StlMesh
import com.mobileslicer.nativebridge.NativeEngineBridge
import com.mobileslicer.ui.theme.AccentPaletteOption
import com.mobileslicer.viewer.PrinterBedSpec
import com.mobileslicer.viewer.TouchModelViewerView
import com.mobileslicer.viewer.ViewerModelTransform
import com.mobileslicer.viewer.ViewerPlateObject
import com.mobileslicer.ui.theme.MobileSlicerTheme
import com.mobileslicer.ui.theme.PanelAmber
import com.mobileslicer.ui.theme.PanelBlue
import com.mobileslicer.ui.theme.PanelGreen
import com.mobileslicer.ui.theme.PanelLavender
import com.mobileslicer.ui.theme.PanelSlate
import com.mobileslicer.ui.theme.LocalAppDarkTheme
import com.mobileslicer.ui.theme.ThemeModeOption
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlin.math.cos
import kotlin.math.sqrt
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject



@Composable
internal fun ProcessProfileEditorSelectedTabContent(
    initial: ProcessProfile,
    draft: ProcessProfileEditorDraft,
    options: ProcessProfileEditorOptions,
    showAdvancedProfileSettings: Boolean
) {
    @Composable
    fun ProcessQualitySelectedTabContent() {
        ProcessQualityTabContent(
                        showAdvancedProfileSettings = showAdvancedProfileSettings,
                        boolEnabledDisabledOptions = options.boolEnabledDisabledOptions,
                        seamPositionOptions = options.seamPositionOptions,
                        seamScarfTypeOptions = options.seamScarfTypeOptions,
                        ironingTypeOptions = options.ironingTypeOptions,
                        ironingPatternOptions = options.ironingPatternOptions,
                        wallGeneratorOptions = options.wallGeneratorOptions,
                        wallSequenceOptions = options.wallSequenceOptions,
                        wallDirectionOptions = options.wallDirectionOptions,
                        extraBridgeLayerOptions = options.extraBridgeLayerOptions,
                        internalBridgeFilterOptions = options.internalBridgeFilterOptions,
                        counterboreHoleBridgingOptions = options.counterboreHoleBridgingOptions,
                        firstLayerHeight = draft.firstLayerHeight,
                        onFirstLayerHeightChange = { draft.firstLayerHeight = it },
                        layerHeight = draft.layerHeight,
                        onLayerHeightChange = { draft.layerHeight = it },
                        lineWidth = draft.lineWidth,
                        onLineWidthChange = { draft.lineWidth = it },
                        initialLayerLineWidth = draft.initialLayerLineWidth,
                        onInitialLayerLineWidthChange = { draft.initialLayerLineWidth = it },
                        outerWallLineWidth = draft.outerWallLineWidth,
                        onOuterWallLineWidthChange = { draft.outerWallLineWidth = it },
                        innerWallLineWidth = draft.innerWallLineWidth,
                        onInnerWallLineWidthChange = { draft.innerWallLineWidth = it },
                        topSurfaceLineWidth = draft.topSurfaceLineWidth,
                        onTopSurfaceLineWidthChange = { draft.topSurfaceLineWidth = it },
                        sparseInfillLineWidth = draft.sparseInfillLineWidth,
                        onSparseInfillLineWidthChange = { draft.sparseInfillLineWidth = it },
                        internalSolidInfillLineWidth = draft.internalSolidInfillLineWidth,
                        onInternalSolidInfillLineWidthChange = { draft.internalSolidInfillLineWidth = it },
                        supportLineWidth = draft.supportLineWidth,
                        onSupportLineWidthChange = { draft.supportLineWidth = it },
                        seamPosition = draft.seamPosition,
                        onSeamPositionChange = { draft.seamPosition = it },
                        staggeredInnerSeams = draft.staggeredInnerSeams,
                        onStaggeredInnerSeamsChange = { draft.staggeredInnerSeams = it },
                        seamGap = draft.seamGap,
                        onSeamGapChange = { draft.seamGap = it },
                        seamScarfType = draft.seamScarfType,
                        onSeamScarfTypeChange = { draft.seamScarfType = it },
                        seamScarfConditional = draft.seamScarfConditional,
                        onSeamScarfConditionalChange = { draft.seamScarfConditional = it },
                        scarfAngleThreshold = draft.scarfAngleThreshold,
                        onScarfAngleThresholdChange = { draft.scarfAngleThreshold = it },
                        scarfOverhangThreshold = draft.scarfOverhangThreshold,
                        onScarfOverhangThresholdChange = { draft.scarfOverhangThreshold = it },
                        scarfJointSpeed = draft.scarfJointSpeed,
                        onScarfJointSpeedChange = { draft.scarfJointSpeed = it },
                        scarfJointFlowRatio = draft.scarfJointFlowRatio,
                        onScarfJointFlowRatioChange = { draft.scarfJointFlowRatio = it },
                        seamScarfStartHeight = draft.seamScarfStartHeight,
                        onSeamScarfStartHeightChange = { draft.seamScarfStartHeight = it },
                        seamScarfEntireLoop = draft.seamScarfEntireLoop,
                        onSeamScarfEntireLoopChange = { draft.seamScarfEntireLoop = it },
                        seamScarfMinLength = draft.seamScarfMinLength,
                        onSeamScarfMinLengthChange = { draft.seamScarfMinLength = it },
                        seamScarfSteps = draft.seamScarfSteps,
                        onSeamScarfStepsChange = { draft.seamScarfSteps = it },
                        seamScarfInnerWalls = draft.seamScarfInnerWalls,
                        onSeamScarfInnerWallsChange = { draft.seamScarfInnerWalls = it },
                        roleBasedWipeSpeed = draft.roleBasedWipeSpeed,
                        onRoleBasedWipeSpeedChange = { draft.roleBasedWipeSpeed = it },
                        wipeSpeed = draft.wipeSpeed,
                        onWipeSpeedChange = { draft.wipeSpeed = it },
                        wipeOnLoops = draft.wipeOnLoops,
                        onWipeOnLoopsChange = { draft.wipeOnLoops = it },
                        wipeBeforeExternalLoop = draft.wipeBeforeExternalLoop,
                        onWipeBeforeExternalLoopChange = { draft.wipeBeforeExternalLoop = it },
                        hasScarfJointSeam = draft.hasScarfJointSeam,
                        onHasScarfJointSeamChange = { draft.hasScarfJointSeam = it },
                        preciseOuterWall = draft.preciseOuterWall,
                        onPreciseOuterWallChange = { draft.preciseOuterWall = it },
                        sliceClosingRadius = draft.sliceClosingRadius,
                        onSliceClosingRadiusChange = { draft.sliceClosingRadius = it },
                        applyTopSurfaceCompensation = draft.applyTopSurfaceCompensation,
                        onApplyTopSurfaceCompensationChange = { draft.applyTopSurfaceCompensation = it },
                        resolution = draft.resolution,
                        onResolutionChange = { draft.resolution = it },
                        enableArcFitting = draft.enableArcFitting,
                        onEnableArcFittingChange = { draft.enableArcFitting = it },
                        xyHoleCompensation = draft.xyHoleCompensation,
                        onXyHoleCompensationChange = { draft.xyHoleCompensation = it },
                        xyContourCompensation = draft.xyContourCompensation,
                        onXyContourCompensationChange = { draft.xyContourCompensation = it },
                        elefantFootCompensation = draft.elefantFootCompensation,
                        onElefantFootCompensationChange = { draft.elefantFootCompensation = it },
                        elefantFootCompensationLayers = draft.elefantFootCompensationLayers,
                        onElefantFootCompensationLayersChange = { draft.elefantFootCompensationLayers = it },
                        preciseZHeight = draft.preciseZHeight,
                        onPreciseZHeightChange = { draft.preciseZHeight = it },
                        holeToPolyhole = draft.holeToPolyhole,
                        onHoleToPolyholeChange = { draft.holeToPolyhole = it },
                        holeToPolyholeThreshold = draft.holeToPolyholeThreshold,
                        onHoleToPolyholeThresholdChange = { draft.holeToPolyholeThreshold = it },
                        holeToPolyholeTwisted = draft.holeToPolyholeTwisted,
                        onHoleToPolyholeTwistedChange = { draft.holeToPolyholeTwisted = it },
                        ironingType = draft.ironingType,
                        onIroningTypeChange = { draft.ironingType = it },
                        ironingPattern = draft.ironingPattern,
                        onIroningPatternChange = { draft.ironingPattern = it },
                        ironingFlow = draft.ironingFlow,
                        onIroningFlowChange = { draft.ironingFlow = it },
                        ironingSpacing = draft.ironingSpacing,
                        onIroningSpacingChange = { draft.ironingSpacing = it },
                        ironingInset = draft.ironingInset,
                        onIroningInsetChange = { draft.ironingInset = it },
                        ironingAngle = draft.ironingAngle,
                        onIroningAngleChange = { draft.ironingAngle = it },
                        ironingAngleFixed = draft.ironingAngleFixed,
                        onIroningAngleFixedChange = { draft.ironingAngleFixed = it },
                        wallGenerator = draft.wallGenerator,
                        onWallGeneratorChange = { draft.wallGenerator = it },
                        wallTransitionAngle = draft.wallTransitionAngle,
                        onWallTransitionAngleChange = { draft.wallTransitionAngle = it },
                        wallTransitionFilterDeviation = draft.wallTransitionFilterDeviation,
                        onWallTransitionFilterDeviationChange = { draft.wallTransitionFilterDeviation = it },
                        wallTransitionLength = draft.wallTransitionLength,
                        onWallTransitionLengthChange = { draft.wallTransitionLength = it },
                        wallDistributionCount = draft.wallDistributionCount,
                        onWallDistributionCountChange = { draft.wallDistributionCount = it },
                        initialLayerMinBeadWidthPercent = draft.initialLayerMinBeadWidthPercent,
                        onInitialLayerMinBeadWidthPercentChange = { draft.initialLayerMinBeadWidthPercent = it },
                        minBeadWidth = draft.minBeadWidth,
                        onMinBeadWidthChange = { draft.minBeadWidth = it },
                        minFeatureSize = draft.minFeatureSize,
                        onMinFeatureSizeChange = { draft.minFeatureSize = it },
                        minLengthFactor = draft.minLengthFactor,
                        onMinLengthFactorChange = { draft.minLengthFactor = it },
                        wallSequence = draft.wallSequence,
                        onWallSequenceChange = { draft.wallSequence = it },
                        printInfillFirst = draft.printInfillFirst,
                        onPrintInfillFirstChange = { draft.printInfillFirst = it },
                        wallDirection = draft.wallDirection,
                        onWallDirectionChange = { draft.wallDirection = it },
                        printFlowRatio = draft.printFlowRatio,
                        onPrintFlowRatioChange = { draft.printFlowRatio = it },
                        setOtherFlowRatios = draft.setOtherFlowRatios,
                        onSetOtherFlowRatiosChange = { draft.setOtherFlowRatios = it },
                        onlyOneWallFirstLayer = draft.onlyOneWallFirstLayer,
                        onOnlyOneWallFirstLayerChange = { draft.onlyOneWallFirstLayer = it },
                        onlyOneWallTopSurfaces = draft.onlyOneWallTopSurfaces,
                        onOnlyOneWallTopSurfacesChange = { draft.onlyOneWallTopSurfaces = it },
                        minWidthTopSurface = draft.minWidthTopSurface,
                        onMinWidthTopSurfaceChange = { draft.minWidthTopSurface = it },
                        firstLayerFlowRatio = draft.firstLayerFlowRatio,
                        onFirstLayerFlowRatioChange = { draft.firstLayerFlowRatio = it },
                        outerWallFlowRatio = draft.outerWallFlowRatio,
                        onOuterWallFlowRatioChange = { draft.outerWallFlowRatio = it },
                        innerWallFlowRatio = draft.innerWallFlowRatio,
                        onInnerWallFlowRatioChange = { draft.innerWallFlowRatio = it },
                        topSolidInfillFlowRatio = draft.topSolidInfillFlowRatio,
                        onTopSolidInfillFlowRatioChange = { draft.topSolidInfillFlowRatio = it },
                        bottomSolidInfillFlowRatio = draft.bottomSolidInfillFlowRatio,
                        onBottomSolidInfillFlowRatioChange = { draft.bottomSolidInfillFlowRatio = it },
                        overhangFlowRatio = draft.overhangFlowRatio,
                        onOverhangFlowRatioChange = { draft.overhangFlowRatio = it },
                        sparseInfillFlowRatio = draft.sparseInfillFlowRatio,
                        onSparseInfillFlowRatioChange = { draft.sparseInfillFlowRatio = it },
                        internalSolidInfillFlowRatio = draft.internalSolidInfillFlowRatio,
                        onInternalSolidInfillFlowRatioChange = { draft.internalSolidInfillFlowRatio = it },
                        gapFillFlowRatio = draft.gapFillFlowRatio,
                        onGapFillFlowRatioChange = { draft.gapFillFlowRatio = it },
                        supportFlowRatio = draft.supportFlowRatio,
                        onSupportFlowRatioChange = { draft.supportFlowRatio = it },
                        supportInterfaceFlowRatio = draft.supportInterfaceFlowRatio,
                        onSupportInterfaceFlowRatioChange = { draft.supportInterfaceFlowRatio = it },
                        reduceCrossingWall = draft.reduceCrossingWall,
                        onReduceCrossingWallChange = { draft.reduceCrossingWall = it },
                        maxTravelDetourDistance = draft.maxTravelDetourDistance,
                        onMaxTravelDetourDistanceChange = { draft.maxTravelDetourDistance = it },
                        smallAreaInfillFlowCompensation = draft.smallAreaInfillFlowCompensation,
                        onSmallAreaInfillFlowCompensationChange = { draft.smallAreaInfillFlowCompensation = it },
                        bridgeFlowRatio = draft.bridgeFlowRatio,
                        onBridgeFlowRatioChange = { draft.bridgeFlowRatio = it },
                        internalBridgeFlowRatio = draft.internalBridgeFlowRatio,
                        onInternalBridgeFlowRatioChange = { draft.internalBridgeFlowRatio = it },
                        bridgeDensityPercent = draft.bridgeDensityPercent,
                        onBridgeDensityPercentChange = { draft.bridgeDensityPercent = it },
                        internalBridgeDensityPercent = draft.internalBridgeDensityPercent,
                        onInternalBridgeDensityPercentChange = { draft.internalBridgeDensityPercent = it },
                        thickBridges = draft.thickBridges,
                        onThickBridgesChange = { draft.thickBridges = it },
                        thickInternalBridges = draft.thickInternalBridges,
                        onThickInternalBridgesChange = { draft.thickInternalBridges = it },
                        extraBridgeLayer = draft.extraBridgeLayer,
                        onExtraBridgeLayerChange = { draft.extraBridgeLayer = it },
                        dontFilterInternalBridges = draft.dontFilterInternalBridges,
                        onDontFilterInternalBridgesChange = { draft.dontFilterInternalBridges = it },
                        counterboreHoleBridging = draft.counterboreHoleBridging,
                        onCounterboreHoleBridgingChange = { draft.counterboreHoleBridging = it },
                        detectOverhangWall = draft.detectOverhangWall,
                        onDetectOverhangWallChange = { draft.detectOverhangWall = it },
                        makeOverhangPrintable = draft.makeOverhangPrintable,
                        onMakeOverhangPrintableChange = { draft.makeOverhangPrintable = it },
                        makeOverhangPrintableAngle = draft.makeOverhangPrintableAngle,
                        onMakeOverhangPrintableAngleChange = { draft.makeOverhangPrintableAngle = it },
                        makeOverhangPrintableHoleSize = draft.makeOverhangPrintableHoleSize,
                        onMakeOverhangPrintableHoleSizeChange = { draft.makeOverhangPrintableHoleSize = it },
                        extraPerimetersOnOverhangs = draft.extraPerimetersOnOverhangs,
                        onExtraPerimetersOnOverhangsChange = { draft.extraPerimetersOnOverhangs = it },
                        overhangReverse = draft.overhangReverse,
                        onOverhangReverseChange = { draft.overhangReverse = it },
                        overhangReverseInternalOnly = draft.overhangReverseInternalOnly,
                        onOverhangReverseInternalOnlyChange = { draft.overhangReverseInternalOnly = it },
                        overhangReverseThreshold = draft.overhangReverseThreshold,
                        onOverhangReverseThresholdChange = { draft.overhangReverseThreshold = it }
                    )
    }

    @Composable
    fun ProcessStrengthSelectedTabContent() {
        ProcessStrengthTabContent(
                        showAdvancedProfileSettings = showAdvancedProfileSettings,
                        boolEnabledDisabledOptions = options.boolEnabledDisabledOptions,
                        topSurfacePatternOptions = options.topSurfacePatternOptions,
                        bottomSurfacePatternOptions = options.bottomSurfacePatternOptions,
                        internalSolidPatternOptions = options.internalSolidPatternOptions,
                        sparseInfillPatternOptions = options.sparseInfillPatternOptions,
                        gapFillTargetOptions = options.gapFillTargetOptions,
                        infillCombinationOptions = options.infillCombinationOptions,
                        ensureVerticalShellThicknessOptions = options.ensureVerticalShellThicknessOptions,
                        wallCount = draft.wallCount,
                        onWallCountChange = { draft.wallCount = it },
                        wallCountChanged = draft.wallCount != initial.wallCount.toString(),
                        alternateExtraWall = draft.alternateExtraWall,
                        onAlternateExtraWallChange = { draft.alternateExtraWall = it },
                        topShellLayers = draft.topShellLayers,
                        onTopShellLayersChange = { draft.topShellLayers = it },
                        topShellLayersChanged = draft.topShellLayers != initial.topShellLayers.toString(),
                        topShellThickness = draft.topShellThickness,
                        onTopShellThicknessChange = { draft.topShellThickness = it },
                        topShellThicknessChanged = draft.topShellThickness != initial.topShellThicknessMm.toString(),
                        topSurfaceDensityPercent = draft.topSurfaceDensityPercent,
                        onTopSurfaceDensityPercentChange = { draft.topSurfaceDensityPercent = it },
                        topSurfaceDensityPercentChanged = draft.topSurfaceDensityPercent != initial.topSurfaceDensityPercent.toString(),
                        topSurfacePattern = draft.topSurfacePattern,
                        onTopSurfacePatternChange = { draft.topSurfacePattern = it },
                        topSurfacePatternChanged = draft.topSurfacePattern != initial.topSurfacePattern,
                        bottomShellLayers = draft.bottomShellLayers,
                        onBottomShellLayersChange = { draft.bottomShellLayers = it },
                        bottomShellLayersChanged = draft.bottomShellLayers != initial.bottomShellLayers.toString(),
                        bottomShellThickness = draft.bottomShellThickness,
                        onBottomShellThicknessChange = { draft.bottomShellThickness = it },
                        bottomShellThicknessChanged = draft.bottomShellThickness != initial.bottomShellThicknessMm.toString(),
                        bottomSurfaceDensityPercent = draft.bottomSurfaceDensityPercent,
                        onBottomSurfaceDensityPercentChange = { draft.bottomSurfaceDensityPercent = it },
                        bottomSurfaceDensityPercentChanged = draft.bottomSurfaceDensityPercent != initial.bottomSurfaceDensityPercent.toString(),
                        bottomSurfacePattern = draft.bottomSurfacePattern,
                        onBottomSurfacePatternChange = { draft.bottomSurfacePattern = it },
                        bottomSurfacePatternChanged = draft.bottomSurfacePattern != initial.bottomSurfacePattern,
                        topBottomInfillWallOverlapPercent = draft.topBottomInfillWallOverlapPercent,
                        onTopBottomInfillWallOverlapPercentChange = { draft.topBottomInfillWallOverlapPercent = it },
                        internalSolidInfillPattern = draft.internalSolidInfillPattern,
                        onInternalSolidInfillPatternChange = { draft.internalSolidInfillPattern = it },
                        infillPercent = draft.infillPercent,
                        onInfillPercentChange = { draft.infillPercent = it },
                        infillPercentChanged = draft.infillPercent != initial.infillPercent.toString(),
                        fillMultiline = draft.fillMultiline,
                        onFillMultilineChange = { draft.fillMultiline = it },
                        fillMultilineChanged = draft.fillMultiline != initial.fillMultiline.toString(),
                        sparseInfillPattern = draft.sparseInfillPattern,
                        onSparseInfillPatternChange = { draft.sparseInfillPattern = it },
                        sparseInfillPatternChanged = draft.sparseInfillPattern != initial.sparseInfillPattern,
                        extraSolidInfills = draft.extraSolidInfills,
                        onExtraSolidInfillsChange = { draft.extraSolidInfills = it },
                        detectThinWall = draft.detectThinWall,
                        onDetectThinWallChange = { draft.detectThinWall = it },
                        infillDirectionDegrees = draft.infillDirectionDegrees,
                        onInfillDirectionDegreesChange = { draft.infillDirectionDegrees = it },
                        sparseInfillRotateTemplate = draft.sparseInfillRotateTemplate,
                        onSparseInfillRotateTemplateChange = { draft.sparseInfillRotateTemplate = it },
                        skinInfillDensity = draft.skinInfillDensity,
                        onSkinInfillDensityChange = { draft.skinInfillDensity = it },
                        skeletonInfillDensity = draft.skeletonInfillDensity,
                        onSkeletonInfillDensityChange = { draft.skeletonInfillDensity = it },
                        infillLockDepth = draft.infillLockDepth,
                        onInfillLockDepthChange = { draft.infillLockDepth = it },
                        skinInfillDepth = draft.skinInfillDepth,
                        onSkinInfillDepthChange = { draft.skinInfillDepth = it },
                        skinInfillLineWidth = draft.skinInfillLineWidth,
                        onSkinInfillLineWidthChange = { draft.skinInfillLineWidth = it },
                        skeletonInfillLineWidth = draft.skeletonInfillLineWidth,
                        onSkeletonInfillLineWidthChange = { draft.skeletonInfillLineWidth = it },
                        symmetricInfillYAxis = draft.symmetricInfillYAxis,
                        onSymmetricInfillYAxisChange = { draft.symmetricInfillYAxis = it },
                        infillShiftStep = draft.infillShiftStep,
                        onInfillShiftStepChange = { draft.infillShiftStep = it },
                        lateralLatticeAngle1 = draft.lateralLatticeAngle1,
                        onLateralLatticeAngle1Change = { draft.lateralLatticeAngle1 = it },
                        lateralLatticeAngle2 = draft.lateralLatticeAngle2,
                        onLateralLatticeAngle2Change = { draft.lateralLatticeAngle2 = it },
                        infillOverhangAngle = draft.infillOverhangAngle,
                        onInfillOverhangAngleChange = { draft.infillOverhangAngle = it },
                        infillAnchorMax = draft.infillAnchorMax,
                        onInfillAnchorMaxChange = { draft.infillAnchorMax = it },
                        infillAnchor = draft.infillAnchor,
                        onInfillAnchorChange = { draft.infillAnchor = it },
                        solidInfillDirectionDegrees = draft.solidInfillDirectionDegrees,
                        onSolidInfillDirectionDegreesChange = { draft.solidInfillDirectionDegrees = it },
                        solidInfillRotateTemplate = draft.solidInfillRotateTemplate,
                        onSolidInfillRotateTemplateChange = { draft.solidInfillRotateTemplate = it },
                        gapFillTarget = draft.gapFillTarget,
                        onGapFillTargetChange = { draft.gapFillTarget = it },
                        filterOutGapFill = draft.filterOutGapFill,
                        onFilterOutGapFillChange = { draft.filterOutGapFill = it },
                        infillWallOverlapPercent = draft.infillWallOverlapPercent,
                        onInfillWallOverlapPercentChange = { draft.infillWallOverlapPercent = it },
                        alignInfillDirectionToModel = draft.alignInfillDirectionToModel,
                        onAlignInfillDirectionToModelChange = { draft.alignInfillDirectionToModel = it },
                        bridgeAngleDegrees = draft.bridgeAngleDegrees,
                        onBridgeAngleDegreesChange = { draft.bridgeAngleDegrees = it },
                        internalBridgeAngleDegrees = draft.internalBridgeAngleDegrees,
                        onInternalBridgeAngleDegreesChange = { draft.internalBridgeAngleDegrees = it },
                        minimumSparseInfillAreaMm2 = draft.minimumSparseInfillAreaMm2,
                        onMinimumSparseInfillAreaMm2Change = { draft.minimumSparseInfillAreaMm2 = it },
                        infillCombination = draft.infillCombination,
                        onInfillCombinationChange = { draft.infillCombination = it },
                        infillCombinationMaxLayerHeight = draft.infillCombinationMaxLayerHeight,
                        onInfillCombinationMaxLayerHeightChange = { draft.infillCombinationMaxLayerHeight = it },
                        detectNarrowInternalSolidInfill = draft.detectNarrowInternalSolidInfill,
                        onDetectNarrowInternalSolidInfillChange = { draft.detectNarrowInternalSolidInfill = it },
                        ensureVerticalShellThickness = draft.ensureVerticalShellThickness,
                        onEnsureVerticalShellThicknessChange = { draft.ensureVerticalShellThickness = it }
                    )
    }

    @Composable
    fun ProcessSpeedSelectedTabContent() {
        ProcessSpeedTabContent(
                        showAdvancedProfileSettings = showAdvancedProfileSettings,
                        boolEnabledDisabledOptions = options.boolEnabledDisabledOptions,
                        firstLayerPrintSpeed = draft.firstLayerPrintSpeed,
                        onFirstLayerPrintSpeedChange = { draft.firstLayerPrintSpeed = it },
                        firstLayerInfillSpeed = draft.firstLayerInfillSpeed,
                        onFirstLayerInfillSpeedChange = { draft.firstLayerInfillSpeed = it },
                        firstLayerTravelSpeedPercent = draft.firstLayerTravelSpeedPercent,
                        onFirstLayerTravelSpeedPercentChange = { draft.firstLayerTravelSpeedPercent = it },
                        slowDownLayers = draft.slowDownLayers,
                        onSlowDownLayersChange = { draft.slowDownLayers = it },
                        outerWallSpeed = draft.outerWallSpeed,
                        onOuterWallSpeedChange = { draft.outerWallSpeed = it },
                        innerWallSpeed = draft.innerWallSpeed,
                        onInnerWallSpeedChange = { draft.innerWallSpeed = it },
                        smallPerimeterSpeed = draft.smallPerimeterSpeed,
                        onSmallPerimeterSpeedChange = { draft.smallPerimeterSpeed = it },
                        smallPerimeterThreshold = draft.smallPerimeterThreshold,
                        onSmallPerimeterThresholdChange = { draft.smallPerimeterThreshold = it },
                        sparseInfillSpeed = draft.sparseInfillSpeed,
                        onSparseInfillSpeedChange = { draft.sparseInfillSpeed = it },
                        internalSolidInfillSpeed = draft.internalSolidInfillSpeed,
                        onInternalSolidInfillSpeedChange = { draft.internalSolidInfillSpeed = it },
                        topSurfaceSpeed = draft.topSurfaceSpeed,
                        onTopSurfaceSpeedChange = { draft.topSurfaceSpeed = it },
                        ironingSpeed = draft.ironingSpeed,
                        onIroningSpeedChange = { draft.ironingSpeed = it },
                        gapInfillSpeed = draft.gapInfillSpeed,
                        onGapInfillSpeedChange = { draft.gapInfillSpeed = it },
                        supportSpeed = draft.supportSpeed,
                        onSupportSpeedChange = { draft.supportSpeed = it },
                        supportInterfaceSpeed = draft.supportInterfaceSpeed,
                        onSupportInterfaceSpeedChange = { draft.supportInterfaceSpeed = it },
                        overhang1_4Speed = draft.overhang1_4Speed,
                        onOverhang1_4SpeedChange = { draft.overhang1_4Speed = it },
                        overhang2_4Speed = draft.overhang2_4Speed,
                        onOverhang2_4SpeedChange = { draft.overhang2_4Speed = it },
                        overhang3_4Speed = draft.overhang3_4Speed,
                        onOverhang3_4SpeedChange = { draft.overhang3_4Speed = it },
                        overhang4_4Speed = draft.overhang4_4Speed,
                        onOverhang4_4SpeedChange = { draft.overhang4_4Speed = it },
                        bridgeSpeed = draft.bridgeSpeed,
                        onBridgeSpeedChange = { draft.bridgeSpeed = it },
                        internalBridgeSpeed = draft.internalBridgeSpeed,
                        onInternalBridgeSpeedChange = { draft.internalBridgeSpeed = it },
                        travelSpeed = draft.travelSpeed,
                        onTravelSpeedChange = { draft.travelSpeed = it },
                        defaultAcceleration = draft.defaultAcceleration,
                        onDefaultAccelerationChange = { draft.defaultAcceleration = it },
                        outerWallAcceleration = draft.outerWallAcceleration,
                        onOuterWallAccelerationChange = { draft.outerWallAcceleration = it },
                        innerWallAcceleration = draft.innerWallAcceleration,
                        onInnerWallAccelerationChange = { draft.innerWallAcceleration = it },
                        bridgeAcceleration = draft.bridgeAcceleration,
                        onBridgeAccelerationChange = { draft.bridgeAcceleration = it },
                        sparseInfillAcceleration = draft.sparseInfillAcceleration,
                        onSparseInfillAccelerationChange = { draft.sparseInfillAcceleration = it },
                        internalSolidInfillAcceleration = draft.internalSolidInfillAcceleration,
                        onInternalSolidInfillAccelerationChange = { draft.internalSolidInfillAcceleration = it },
                        initialLayerAcceleration = draft.initialLayerAcceleration,
                        onInitialLayerAccelerationChange = { draft.initialLayerAcceleration = it },
                        topSurfaceAcceleration = draft.topSurfaceAcceleration,
                        onTopSurfaceAccelerationChange = { draft.topSurfaceAcceleration = it },
                        travelAcceleration = draft.travelAcceleration,
                        onTravelAccelerationChange = { draft.travelAcceleration = it },
                        accelToDecelEnable = draft.accelToDecelEnable,
                        onAccelToDecelEnableChange = { draft.accelToDecelEnable = it },
                        accelToDecelFactor = draft.accelToDecelFactor,
                        onAccelToDecelFactorChange = { draft.accelToDecelFactor = it },
                        defaultJunctionDeviation = draft.defaultJunctionDeviation,
                        onDefaultJunctionDeviationChange = { draft.defaultJunctionDeviation = it },
                        defaultJerk = draft.defaultJerk,
                        onDefaultJerkChange = { draft.defaultJerk = it },
                        outerWallJerk = draft.outerWallJerk,
                        onOuterWallJerkChange = { draft.outerWallJerk = it },
                        innerWallJerk = draft.innerWallJerk,
                        onInnerWallJerkChange = { draft.innerWallJerk = it },
                        infillJerk = draft.infillJerk,
                        onInfillJerkChange = { draft.infillJerk = it },
                        topSurfaceJerk = draft.topSurfaceJerk,
                        onTopSurfaceJerkChange = { draft.topSurfaceJerk = it },
                        initialLayerJerk = draft.initialLayerJerk,
                        onInitialLayerJerkChange = { draft.initialLayerJerk = it },
                        travelJerk = draft.travelJerk,
                        onTravelJerkChange = { draft.travelJerk = it },
                        enableOverhangSpeed = draft.enableOverhangSpeed,
                        onEnableOverhangSpeedChange = { draft.enableOverhangSpeed = it },
                        slowdownForCurledPerimeters = draft.slowdownForCurledPerimeters,
                        onSlowdownForCurledPerimetersChange = { draft.slowdownForCurledPerimeters = it },
                        internalBridgeFlowRatio = draft.internalBridgeFlowRatio,
                        onInternalBridgeFlowRatioChange = { draft.internalBridgeFlowRatio = it },
                        internalBridgeSupportThickness = draft.internalBridgeSupportThickness,
                        onInternalBridgeSupportThicknessChange = { draft.internalBridgeSupportThickness = it },
                        maxVolumetricExtrusionRateSlope = draft.maxVolumetricExtrusionRateSlope,
                        onMaxVolumetricExtrusionRateSlopeChange = { draft.maxVolumetricExtrusionRateSlope = it },
                        maxVolumetricExtrusionRateSlopeSegmentLength = draft.maxVolumetricExtrusionRateSlopeSegmentLength,
                        onMaxVolumetricExtrusionRateSlopeSegmentLengthChange = { draft.maxVolumetricExtrusionRateSlopeSegmentLength = it },
                        extrusionRateSmoothingExternalPerimeterOnly = draft.extrusionRateSmoothingExternalPerimeterOnly,
                        onExtrusionRateSmoothingExternalPerimeterOnlyChange = { draft.extrusionRateSmoothingExternalPerimeterOnly = it }
                    )
    }

    @Composable
    fun ProcessSupportSelectedTabContent() {
        ProcessSupportTabContent(
                        showAdvancedProfileSettings = showAdvancedProfileSettings,
                        boolEnabledDisabledOptions = options.boolEnabledDisabledOptions,
                        supportTypeOptions = options.supportTypeOptions,
                        supportStyleOptions = options.supportStyleOptions,
                        ironingPatternOptions = options.ironingPatternOptions,
                        supportBasePatternOptions = options.supportBasePatternOptions,
                        supportInterfacePatternOptions = options.supportInterfacePatternOptions,
                        enableSupport = draft.enableSupport,
                        onEnableSupportChange = { draft.enableSupport = it },
                        supportType = draft.supportType,
                        onSupportTypeChange = { draft.supportType = it },
                        supportStyle = draft.supportStyle,
                        onSupportStyleChange = { draft.supportStyle = it },
                        supportThresholdAngle = draft.supportThresholdAngle,
                        onSupportThresholdAngleChange = { draft.supportThresholdAngle = it },
                        supportThresholdOverlap = draft.supportThresholdOverlap,
                        onSupportThresholdOverlapChange = { draft.supportThresholdOverlap = it },
                        raftFirstLayerDensity = draft.raftFirstLayerDensity,
                        onRaftFirstLayerDensityChange = { draft.raftFirstLayerDensity = it },
                        raftFirstLayerExpansion = draft.raftFirstLayerExpansion,
                        onRaftFirstLayerExpansionChange = { draft.raftFirstLayerExpansion = it },
                        supportBuildplateOnly = draft.supportBuildplateOnly,
                        onSupportBuildplateOnlyChange = { draft.supportBuildplateOnly = it },
                        supportCriticalRegionsOnly = draft.supportCriticalRegionsOnly,
                        onSupportCriticalRegionsOnlyChange = { draft.supportCriticalRegionsOnly = it },
                        supportRemoveSmallOverhang = draft.supportRemoveSmallOverhang,
                        onSupportRemoveSmallOverhangChange = { draft.supportRemoveSmallOverhang = it },
                        raftLayers = draft.raftLayers,
                        onRaftLayersChange = { draft.raftLayers = it },
                        raftContactDistance = draft.raftContactDistance,
                        onRaftContactDistanceChange = { draft.raftContactDistance = it },
                        raftExpansion = draft.raftExpansion,
                        onRaftExpansionChange = { draft.raftExpansion = it },
                        supportFilament = draft.supportFilament,
                        onSupportFilamentChange = { draft.supportFilament = it },
                        supportInterfaceFilament = draft.supportInterfaceFilament,
                        onSupportInterfaceFilamentChange = { draft.supportInterfaceFilament = it },
                        supportInterfaceNotForBody = draft.supportInterfaceNotForBody,
                        onSupportInterfaceNotForBodyChange = { draft.supportInterfaceNotForBody = it },
                        supportIroning = draft.supportIroning,
                        onSupportIroningChange = { draft.supportIroning = it },
                        supportIroningPattern = draft.supportIroningPattern,
                        onSupportIroningPatternChange = { draft.supportIroningPattern = it },
                        supportIroningFlow = draft.supportIroningFlow,
                        onSupportIroningFlowChange = { draft.supportIroningFlow = it },
                        supportIroningSpacing = draft.supportIroningSpacing,
                        onSupportIroningSpacingChange = { draft.supportIroningSpacing = it },
                        supportTopZDistance = draft.supportTopZDistance,
                        onSupportTopZDistanceChange = { draft.supportTopZDistance = it },
                        supportBottomZDistance = draft.supportBottomZDistance,
                        onSupportBottomZDistanceChange = { draft.supportBottomZDistance = it },
                        treeSupportWallCount = draft.treeSupportWallCount,
                        onTreeSupportWallCountChange = { draft.treeSupportWallCount = it },
                        supportBasePattern = draft.supportBasePattern,
                        onSupportBasePatternChange = { draft.supportBasePattern = it },
                        supportBasePatternSpacing = draft.supportBasePatternSpacing,
                        onSupportBasePatternSpacingChange = { draft.supportBasePatternSpacing = it },
                        supportAngle = draft.supportAngle,
                        onSupportAngleChange = { draft.supportAngle = it },
                        supportInterfaceTopLayers = draft.supportInterfaceTopLayers,
                        onSupportInterfaceTopLayersChange = { draft.supportInterfaceTopLayers = it },
                        supportInterfaceBottomLayers = draft.supportInterfaceBottomLayers,
                        onSupportInterfaceBottomLayersChange = { draft.supportInterfaceBottomLayers = it },
                        supportInterfacePattern = draft.supportInterfacePattern,
                        onSupportInterfacePatternChange = { draft.supportInterfacePattern = it },
                        supportInterfaceSpacing = draft.supportInterfaceSpacing,
                        onSupportInterfaceSpacingChange = { draft.supportInterfaceSpacing = it },
                        supportBottomInterfaceSpacing = draft.supportBottomInterfaceSpacing,
                        onSupportBottomInterfaceSpacingChange = { draft.supportBottomInterfaceSpacing = it },
                        supportExpansion = draft.supportExpansion,
                        onSupportExpansionChange = { draft.supportExpansion = it },
                        supportObjectXyDistance = draft.supportObjectXyDistance,
                        onSupportObjectXyDistanceChange = { draft.supportObjectXyDistance = it },
                        supportObjectFirstLayerGap = draft.supportObjectFirstLayerGap,
                        onSupportObjectFirstLayerGapChange = { draft.supportObjectFirstLayerGap = it },
                        bridgeNoSupport = draft.bridgeNoSupport,
                        onBridgeNoSupportChange = { draft.bridgeNoSupport = it },
                        supportMaxBridgeLength = draft.supportMaxBridgeLength,
                        onSupportMaxBridgeLengthChange = { draft.supportMaxBridgeLength = it },
                        supportInterfaceLoopPattern = draft.supportInterfaceLoopPattern,
                        onSupportInterfaceLoopPatternChange = { draft.supportInterfaceLoopPattern = it },
                        supportObjectElevation = draft.supportObjectElevation,
                        onSupportObjectElevationChange = { draft.supportObjectElevation = it },
                        independentSupportLayerHeight = draft.independentSupportLayerHeight,
                        onIndependentSupportLayerHeightChange = { draft.independentSupportLayerHeight = it },
                        treeSupportTipDiameter = draft.treeSupportTipDiameter,
                        onTreeSupportTipDiameterChange = { draft.treeSupportTipDiameter = it },
                        treeSupportBranchDistance = draft.treeSupportBranchDistance,
                        onTreeSupportBranchDistanceChange = { draft.treeSupportBranchDistance = it },
                        treeSupportBranchDistanceOrganic = draft.treeSupportBranchDistanceOrganic,
                        onTreeSupportBranchDistanceOrganicChange = { draft.treeSupportBranchDistanceOrganic = it },
                        treeSupportTopRate = draft.treeSupportTopRate,
                        onTreeSupportTopRateChange = { draft.treeSupportTopRate = it },
                        treeSupportBranchDiameter = draft.treeSupportBranchDiameter,
                        onTreeSupportBranchDiameterChange = { draft.treeSupportBranchDiameter = it },
                        treeSupportBranchDiameterOrganic = draft.treeSupportBranchDiameterOrganic,
                        onTreeSupportBranchDiameterOrganicChange = { draft.treeSupportBranchDiameterOrganic = it },
                        treeSupportBranchDiameterAngle = draft.treeSupportBranchDiameterAngle,
                        onTreeSupportBranchDiameterAngleChange = { draft.treeSupportBranchDiameterAngle = it },
                        treeSupportBranchAngle = draft.treeSupportBranchAngle,
                        onTreeSupportBranchAngleChange = { draft.treeSupportBranchAngle = it },
                        treeSupportBranchAngleOrganic = draft.treeSupportBranchAngleOrganic,
                        onTreeSupportBranchAngleOrganicChange = { draft.treeSupportBranchAngleOrganic = it },
                        treeSupportPreferredBranchAngle = draft.treeSupportPreferredBranchAngle,
                        onTreeSupportPreferredBranchAngleChange = { draft.treeSupportPreferredBranchAngle = it },
                        treeSupportAutoBrim = draft.treeSupportAutoBrim,
                        onTreeSupportAutoBrimChange = { draft.treeSupportAutoBrim = it },
                        treeSupportBrimWidth = draft.treeSupportBrimWidth,
                        onTreeSupportBrimWidthChange = { draft.treeSupportBrimWidth = it }
                    )
    }

    @Composable
    fun ProcessMultimaterialSelectedTabContent() {
        ProcessMultimaterialTabContent(
                        showAdvancedProfileSettings = showAdvancedProfileSettings,
                        boolEnabledDisabledOptions = options.boolEnabledDisabledOptions,
                        wipeTowerWallTypeOptions = options.wipeTowerWallTypeOptions,
                        enablePrimeTower = draft.enablePrimeTower,
                        onEnablePrimeTowerChange = { draft.enablePrimeTower = it },
                        primeTowerWidth = draft.primeTowerWidth,
                        onPrimeTowerWidthChange = { draft.primeTowerWidth = it },
                        primeTowerDetails = draft.primeTowerDetails,
                        onPrimeTowerDetailsChange = { draft.primeTowerDetails = it },
                        enableTowerInterfaceFeatures = draft.enableTowerInterfaceFeatures,
                        onEnableTowerInterfaceFeaturesChange = { draft.enableTowerInterfaceFeatures = it },
                        enableTowerInterfaceCooldownDuringTower = draft.enableTowerInterfaceCooldownDuringTower,
                        onEnableTowerInterfaceCooldownDuringTowerChange = { draft.enableTowerInterfaceCooldownDuringTower = it },
                        singleExtruderMultiMaterialPriming = draft.singleExtruderMultiMaterialPriming,
                        onSingleExtruderMultiMaterialPrimingChange = { draft.singleExtruderMultiMaterialPriming = it },
                        standbyTemperatureDelta = draft.standbyTemperatureDelta,
                        onStandbyTemperatureDeltaChange = { draft.standbyTemperatureDelta = it },
                        wipeTowerNoSparseLayers = draft.wipeTowerNoSparseLayers,
                        onWipeTowerNoSparseLayersChange = { draft.wipeTowerNoSparseLayers = it },
                        sparseInfillFilament = draft.sparseInfillFilament,
                        onSparseInfillFilamentChange = { draft.sparseInfillFilament = it },
                        flushIntoInfill = draft.flushIntoInfill,
                        onFlushIntoInfillChange = { draft.flushIntoInfill = it },
                        flushIntoObjects = draft.flushIntoObjects,
                        onFlushIntoObjectsChange = { draft.flushIntoObjects = it },
                        flushIntoSupport = draft.flushIntoSupport,
                        onFlushIntoSupportChange = { draft.flushIntoSupport = it },
                        interfaceShells = draft.interfaceShells,
                        onInterfaceShellsChange = { draft.interfaceShells = it }
                    )
    }

    @Composable
    fun ProcessOthersSelectedTabContent() {
        ProcessOthersTabContent(
                        showAdvancedProfileSettings = showAdvancedProfileSettings,
                        boolEnabledDisabledOptions = options.boolEnabledDisabledOptions,
                        skirtTypeOptions = options.skirtTypeOptions,
                        draftShieldOptions = options.draftShieldOptions,
                        brimTypeOptions = options.brimTypeOptions,
                        slicingModeOptions = options.slicingModeOptions,
                        printSequenceOptions = options.printSequenceOptions,
                        printOrderOptions = options.printOrderOptions,
                        timelapseTypeOptions = options.timelapseTypeOptions,
                        fuzzySkinOptions = options.fuzzySkinOptions,
                        fuzzySkinModeOptions = options.fuzzySkinModeOptions,
                        fuzzySkinNoiseOptions = options.fuzzySkinNoiseOptions,
                        skirtType = draft.skirtType,
                        onSkirtTypeChange = { draft.skirtType = it },
                        skirts = draft.skirts,
                        onSkirtsChange = { draft.skirts = it },
                        minSkirtLength = draft.minSkirtLength,
                        onMinSkirtLengthChange = { draft.minSkirtLength = it },
                        skirtHeight = draft.skirtHeight,
                        onSkirtHeightChange = { draft.skirtHeight = it },
                        skirtDistance = draft.skirtDistance,
                        onSkirtDistanceChange = { draft.skirtDistance = it },
                        skirtStartAngle = draft.skirtStartAngle,
                        onSkirtStartAngleChange = { draft.skirtStartAngle = it },
                        skirtSpeed = draft.skirtSpeed,
                        onSkirtSpeedChange = { draft.skirtSpeed = it },
                        draftShield = draft.draftShield,
                        onDraftShieldChange = { draft.draftShield = it },
                        singleLoopDraftShield = draft.singleLoopDraftShield,
                        onSingleLoopDraftShieldChange = { draft.singleLoopDraftShield = it },
                        brimType = draft.brimType,
                        onBrimTypeChange = { draft.brimType = it },
                        brimWidth = draft.brimWidth,
                        onBrimWidthChange = { draft.brimWidth = it },
                        brimObjectGap = draft.brimObjectGap,
                        onBrimObjectGapChange = { draft.brimObjectGap = it },
                        brimUseEfcOutline = draft.brimUseEfcOutline,
                        onBrimUseEfcOutlineChange = { draft.brimUseEfcOutline = it },
                        combineBrims = draft.combineBrims,
                        onCombineBrimsChange = { draft.combineBrims = it },
                        brimEars = draft.brimEars,
                        onBrimEarsChange = { draft.brimEars = it },
                        brimEarsDetectionLength = draft.brimEarsDetectionLength,
                        onBrimEarsDetectionLengthChange = { draft.brimEarsDetectionLength = it },
                        brimEarsMaxAngle = draft.brimEarsMaxAngle,
                        onBrimEarsMaxAngleChange = { draft.brimEarsMaxAngle = it },
                        slicingMode = draft.slicingMode,
                        onSlicingModeChange = { draft.slicingMode = it },
                        printSequence = draft.printSequence,
                        onPrintSequenceChange = { draft.printSequence = it },
                        printOrder = draft.printOrder,
                        onPrintOrderChange = { draft.printOrder = it },
                        spiralMode = draft.spiralMode,
                        onSpiralModeChange = { draft.spiralMode = it },
                        specialModeDetails = draft.specialModeDetails,
                        onSpecialModeDetailsChange = { draft.specialModeDetails = it },
                        fuzzySkin = draft.fuzzySkin,
                        onFuzzySkinChange = { draft.fuzzySkin = it },
                        fuzzySkinThickness = draft.fuzzySkinThickness,
                        onFuzzySkinThicknessChange = { draft.fuzzySkinThickness = it },
                        fuzzySkinPointDistance = draft.fuzzySkinPointDistance,
                        onFuzzySkinPointDistanceChange = { draft.fuzzySkinPointDistance = it },
                        fuzzySkinFirstLayer = draft.fuzzySkinFirstLayer,
                        onFuzzySkinFirstLayerChange = { draft.fuzzySkinFirstLayer = it },
                        fuzzySkinMode = draft.fuzzySkinMode,
                        onFuzzySkinModeChange = { draft.fuzzySkinMode = it },
                        fuzzySkinNoiseType = draft.fuzzySkinNoiseType,
                        onFuzzySkinNoiseTypeChange = { draft.fuzzySkinNoiseType = it },
                        fuzzySkinScale = draft.fuzzySkinScale,
                        onFuzzySkinScaleChange = { draft.fuzzySkinScale = it },
                        fuzzySkinOctaves = draft.fuzzySkinOctaves,
                        onFuzzySkinOctavesChange = { draft.fuzzySkinOctaves = it },
                        fuzzySkinPersistence = draft.fuzzySkinPersistence,
                        onFuzzySkinPersistenceChange = { draft.fuzzySkinPersistence = it },
                        reduceInfillRetraction = draft.reduceInfillRetraction,
                        onReduceInfillRetractionChange = { draft.reduceInfillRetraction = it },
                        gcodeOutputDetails = draft.gcodeOutputDetails,
                        onGcodeOutputDetailsChange = { draft.gcodeOutputDetails = it },
                        filenameFormat = draft.filenameFormat,
                        onFilenameFormatChange = { draft.filenameFormat = it },
                        postProcessScripts = draft.postProcessScripts,
                        onPostProcessScriptsChange = { draft.postProcessScripts = it },
                        processNotes = draft.processNotes,
                        onProcessNotesChange = { draft.processNotes = it }
                    )
    }

    @Composable
    fun ProcessSelectedTabContent() {
        when (draft.selectedTab) {
            ProcessEditorTab.Quality -> ProcessQualitySelectedTabContent()
            ProcessEditorTab.Strength -> ProcessStrengthSelectedTabContent()
            ProcessEditorTab.Speed -> ProcessSpeedSelectedTabContent()
            ProcessEditorTab.Support -> ProcessSupportSelectedTabContent()
            ProcessEditorTab.Multimaterial -> ProcessMultimaterialSelectedTabContent()
            ProcessEditorTab.Others -> ProcessOthersSelectedTabContent()
        }
    }

    ProcessSelectedTabContent()
}
