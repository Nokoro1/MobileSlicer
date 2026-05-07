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



internal fun ProcessProfileEditorDraft.toProcessProfile(
    initial: ProcessProfile,
    isNew: Boolean
): ProcessProfile {
    val updated = initial.withValues(
        "name" to name.ifBlank { initial.name },
        "subtitle" to subtitle.ifBlank { "Custom process profile" },
        "builtIn" to (initial.builtIn && !isNew),
        "firstLayerHeightMm" to (firstLayerHeight.parseFloatGreaterThanZero() ?: initial.firstLayerHeightMm),
        "layerHeightMm" to (layerHeight.parseFloatGreaterThanZero() ?: initial.layerHeightMm),
        "firstLayerPrintSpeedMmPerSec" to (firstLayerPrintSpeed.parseFloatAtLeast(1f) ?: initial.firstLayerPrintSpeedMmPerSec),
        "firstLayerInfillSpeedMmPerSec" to (firstLayerInfillSpeed.parseFloatAtLeast(1f) ?: initial.firstLayerInfillSpeedMmPerSec),
        "firstLayerTravelSpeedPercent" to (firstLayerTravelSpeedPercent.parseIntIn(0, 1000) ?: initial.firstLayerTravelSpeedPercent),
        "slowDownLayers" to (slowDownLayers.parseIntIn(0, 1000) ?: initial.slowDownLayers),
        "initialLayerAccelerationMmPerSec2" to (initialLayerAcceleration.parseFloatAtLeast(0f) ?: initial.initialLayerAccelerationMmPerSec2),
        "initialLayerJerkMmPerSec" to (initialLayerJerk.parseFloatAtLeast(0f) ?: initial.initialLayerJerkMmPerSec),
        "firstLayerFlowRatio" to (firstLayerFlowRatio.parseFloatAtLeast(0f) ?: initial.firstLayerFlowRatio),
        "printExtruderId" to printExtruderId.trim(),
        "printExtruderVariant" to printExtruderVariant.trim(),
        "outerWallSpeedMmPerSec" to (outerWallSpeed.parseFloatAtLeast(1f) ?: initial.outerWallSpeedMmPerSec),
        "innerWallSpeedMmPerSec" to (innerWallSpeed.parseFloatAtLeast(1f) ?: initial.innerWallSpeedMmPerSec),
        "topSurfaceSpeedMmPerSec" to (topSurfaceSpeed.parseFloatAtLeast(1f) ?: initial.topSurfaceSpeedMmPerSec),
        "travelSpeedMmPerSec" to (travelSpeed.parseFloatAtLeast(1f) ?: initial.travelSpeedMmPerSec),
        "defaultAccelerationMmPerSec2" to (defaultAcceleration.parseFloatAtLeast(0f) ?: initial.defaultAccelerationMmPerSec2),
        "outerWallAccelerationMmPerSec2" to (outerWallAcceleration.parseFloatAtLeast(0f) ?: initial.outerWallAccelerationMmPerSec2),
        "innerWallAccelerationMmPerSec2" to (innerWallAcceleration.parseFloatAtLeast(0f) ?: initial.innerWallAccelerationMmPerSec2),
        "topSurfaceAccelerationMmPerSec2" to (topSurfaceAcceleration.parseFloatAtLeast(0f) ?: initial.topSurfaceAccelerationMmPerSec2),
        "sparseInfillAccelerationMmPerSec2" to (sparseInfillAcceleration.parseFloatAtLeast(0f) ?: initial.sparseInfillAccelerationMmPerSec2),
        "internalSolidInfillAcceleration" to internalSolidInfillAcceleration.trim(),
        "travelAccelerationMmPerSec2" to (travelAcceleration.parseFloatAtLeast(0f) ?: initial.travelAccelerationMmPerSec2),
        "accelToDecelEnable" to accelToDecelEnable,
        "accelToDecelFactorPercent" to (accelToDecelFactor.parseIntIn(0, 1000) ?: initial.accelToDecelFactorPercent),
        "defaultJunctionDeviationMm" to (defaultJunctionDeviation.parseFloatAtLeast(0f) ?: initial.defaultJunctionDeviationMm),
        "defaultJerkMmPerSec" to (defaultJerk.parseFloatAtLeast(0f) ?: initial.defaultJerkMmPerSec),
        "innerWallJerkMmPerSec" to (innerWallJerk.parseFloatAtLeast(0f) ?: initial.innerWallJerkMmPerSec),
        "infillJerkMmPerSec" to (infillJerk.parseFloatAtLeast(0f) ?: initial.infillJerkMmPerSec),
        "topSurfaceJerkMmPerSec" to (topSurfaceJerk.parseFloatAtLeast(0f) ?: initial.topSurfaceJerkMmPerSec),
        "travelJerkMmPerSec" to (travelJerk.parseFloatAtLeast(0f) ?: initial.travelJerkMmPerSec),
        "innerWallFlowRatio" to (innerWallFlowRatio.parseFloatAtLeast(0f) ?: initial.innerWallFlowRatio),
        "outerWallJerkMmPerSec" to (outerWallJerk.parseFloatAtLeast(0f) ?: initial.outerWallJerkMmPerSec),
        "outerWallFlowRatio" to (outerWallFlowRatio.parseFloatAtLeast(0f) ?: initial.outerWallFlowRatio),
        "topSolidInfillFlowRatio" to (topSolidInfillFlowRatio.parseFloatAtLeast(0f) ?: initial.topSolidInfillFlowRatio),
        "bottomSolidInfillFlowRatio" to (bottomSolidInfillFlowRatio.parseFloatAtLeast(0f) ?: initial.bottomSolidInfillFlowRatio),
        "overhang1_4Speed" to overhang1_4Speed.trim(),
        "overhang2_4Speed" to overhang2_4Speed.trim(),
        "overhang3_4Speed" to overhang3_4Speed.trim(),
        "overhang4_4Speed" to overhang4_4Speed.trim(),
        "enableOverhangSpeed" to enableOverhangSpeed,
        "slowdownForCurledPerimeters" to slowdownForCurledPerimeters,
        "overhangFlowRatio" to (overhangFlowRatio.parseFloatAtLeast(0f) ?: initial.overhangFlowRatio),
        "dontSlowDownOuterWall" to dontSlowDownOuterWall,
        "bridgeAcceleration" to bridgeAcceleration.trim(),
        "bridgeSpeedMmPerSec" to (bridgeSpeed.parseFloatAtLeast(0f) ?: initial.bridgeSpeedMmPerSec),
        "smallPerimeterSpeedMmPerSec" to (smallPerimeterSpeed.parseFloatAtLeast(0f) ?: initial.smallPerimeterSpeedMmPerSec),
        "smallPerimeterThresholdMm" to (smallPerimeterThreshold.parseFloatAtLeast(0f) ?: initial.smallPerimeterThresholdMm),
        "bridgeAngleDegrees" to (bridgeAngleDegrees.parseFloatAtLeast(0f) ?: initial.bridgeAngleDegrees),
        "bridgeDensityPercent" to (bridgeDensityPercent.parseIntIn(0, 1000) ?: initial.bridgeDensityPercent),
        "bridgeFlowRatio" to (bridgeFlowRatio.parseFloatAtLeast(0f) ?: initial.bridgeFlowRatio),
        "bridgeNoSupport" to bridgeNoSupport,
        "internalBridgeAngleDegrees" to (internalBridgeAngleDegrees.parseFloatAtLeast(0f) ?: initial.internalBridgeAngleDegrees),
        "internalBridgeDensityPercent" to (internalBridgeDensityPercent.parseIntIn(0, 1000) ?: initial.internalBridgeDensityPercent),
        "internalBridgeFlowRatio" to (internalBridgeFlowRatio.parseFloatAtLeast(0f) ?: initial.internalBridgeFlowRatio),
        "internalBridgeSpeed" to internalBridgeSpeed.trim(),
        "internalBridgeFanSpeed" to internalBridgeFanSpeed.trim(),
        "internalBridgeSupportThickness" to internalBridgeSupportThickness.trim(),
        "maxVolumetricExtrusionRateSlope" to (maxVolumetricExtrusionRateSlope.parseFloatAtLeast(0f) ?: initial.maxVolumetricExtrusionRateSlope),
        "maxVolumetricExtrusionRateSlopeSegmentLengthMm" to (maxVolumetricExtrusionRateSlopeSegmentLength.parseFloatAtLeast(0f) ?: initial.maxVolumetricExtrusionRateSlopeSegmentLengthMm),
        "extrusionRateSmoothingExternalPerimeterOnly" to extrusionRateSmoothingExternalPerimeterOnly,
        "sparseInfillSpeedMmPerSec" to (sparseInfillSpeed.parseFloatAtLeast(0f) ?: initial.sparseInfillSpeedMmPerSec),
        "internalSolidInfillSpeedMmPerSec" to (internalSolidInfillSpeed.parseFloatAtLeast(0f) ?: initial.internalSolidInfillSpeedMmPerSec),
        "gapInfillSpeedMmPerSec" to (gapInfillSpeed.parseFloatAtLeast(0f) ?: initial.gapInfillSpeedMmPerSec),
        "adaptiveLayerHeight" to adaptiveLayerHeight,
        "topShellLayers" to (topShellLayers.parseIntIn(0, 1000) ?: initial.topShellLayers),
        "bottomShellLayers" to (bottomShellLayers.parseIntIn(0, 1000) ?: initial.bottomShellLayers),
        "topShellThicknessMm" to (topShellThickness.parseFloatAtLeast(0f) ?: initial.topShellThicknessMm),
        "bottomShellThicknessMm" to (bottomShellThickness.parseFloatAtLeast(0f) ?: initial.bottomShellThicknessMm),
        "topSurfaceDensityPercent" to (topSurfaceDensityPercent.parseIntIn(0, 1000) ?: initial.topSurfaceDensityPercent),
        "bottomSurfaceDensityPercent" to (bottomSurfaceDensityPercent.parseIntIn(0, 1000) ?: initial.bottomSurfaceDensityPercent),
        "seamPosition" to seamPosition,
        "staggeredInnerSeams" to staggeredInnerSeams,
        "seamGap" to seamGap.trim(),
        "seamScarfType" to seamScarfType,
        "seamScarfConditional" to seamScarfConditional,
        "scarfAngleThresholdDegrees" to (scarfAngleThreshold.parseIntIn(0, 1000) ?: initial.scarfAngleThresholdDegrees),
        "scarfOverhangThresholdPercent" to (scarfOverhangThreshold.parseIntIn(0, 1000) ?: initial.scarfOverhangThresholdPercent),
        "scarfJointSpeed" to scarfJointSpeed.trim(),
        "scarfJointFlowRatio" to (scarfJointFlowRatio.parseFloatAtLeast(0f) ?: initial.scarfJointFlowRatio),
        "seamScarfStartHeight" to seamScarfStartHeight.trim(),
        "seamScarfEntireLoop" to seamScarfEntireLoop,
        "seamScarfMinLengthMm" to (seamScarfMinLength.parseFloatAtLeast(0f) ?: initial.seamScarfMinLengthMm),
        "seamScarfSteps" to (seamScarfSteps.parseIntIn(0, 1000) ?: initial.seamScarfSteps),
        "seamScarfInnerWalls" to seamScarfInnerWalls,
        "roleBasedWipeSpeed" to roleBasedWipeSpeed,
        "wipeSpeed" to wipeSpeed.trim(),
        "wipeOnLoops" to wipeOnLoops,
        "wipeBeforeExternalLoop" to wipeBeforeExternalLoop,
        "hasScarfJointSeam" to hasScarfJointSeam,
        "counterboreHoleBridging" to counterboreHoleBridging,
        "preciseOuterWall" to preciseOuterWall,
        "onlyOneWallTopSurfaces" to onlyOneWallTopSurfaces,
        "topSurfacePattern" to topSurfacePattern,
        "bottomSurfacePattern" to bottomSurfacePattern,
        "internalSolidInfillPattern" to internalSolidInfillPattern,
        "solidInfillDirectionDegrees" to (solidInfillDirectionDegrees.parseFloatAtLeast(0f) ?: initial.solidInfillDirectionDegrees),
        "solidInfillRotateTemplate" to solidInfillRotateTemplate.trim(),
        "lineWidth" to lineWidth.trim(),
        "outerWallLineWidth" to outerWallLineWidth.trim(),
        "innerWallLineWidth" to innerWallLineWidth.trim(),
        "initialLayerLineWidth" to initialLayerLineWidth.trim(),
        "initialLayerMinBeadWidthPercent" to (initialLayerMinBeadWidthPercent.parseIntIn(0, 1000) ?: initial.initialLayerMinBeadWidthPercent),
        "topSurfaceLineWidth" to topSurfaceLineWidth.trim(),
        "internalSolidInfillLineWidth" to internalSolidInfillLineWidth.trim(),
        "minWidthTopSurface" to minWidthTopSurface.trim(),
        "sparseInfillLineWidth" to sparseInfillLineWidth.trim(),
        "infillDirectionDegrees" to (infillDirectionDegrees.parseFloatAtLeast(0f) ?: initial.infillDirectionDegrees),
        "sparseInfillRotateTemplate" to sparseInfillRotateTemplate.trim(),
        "alignInfillDirectionToModel" to alignInfillDirectionToModel,
        "infillWallOverlapPercent" to (infillWallOverlapPercent.parseIntIn(0, 1000) ?: initial.infillWallOverlapPercent),
        "topBottomInfillWallOverlapPercent" to (topBottomInfillWallOverlapPercent.parseIntIn(0, 1000) ?: initial.topBottomInfillWallOverlapPercent),
        "infillAnchor" to infillAnchor.trim(),
        "infillAnchorMax" to infillAnchorMax.trim(),
        "infillCombination" to infillCombination,
        "infillCombinationMaxLayerHeight" to infillCombinationMaxLayerHeight.trim(),
        "alternateExtraWall" to alternateExtraWall,
        "extraSolidInfills" to extraSolidInfills.trim(),
        "minimumSparseInfillAreaMm2" to (minimumSparseInfillAreaMm2.parseFloatAtLeast(0f) ?: initial.minimumSparseInfillAreaMm2),
        "detectThinWall" to detectThinWall,
        "detectOverhangWall" to detectOverhangWall,
        "thickBridges" to thickBridges,
        "sliceClosingRadiusMm" to (sliceClosingRadius.parseFloatAtLeast(0f) ?: initial.sliceClosingRadiusMm),
        "resolutionMm" to (resolution.parseFloatAtLeast(0f) ?: initial.resolutionMm),
        "interfaceShells" to interfaceShells,
        "dontFilterInternalBridges" to dontFilterInternalBridges,
        "detectNarrowInternalSolidInfill" to detectNarrowInternalSolidInfill,
        "elefantFootCompensationMm" to (elefantFootCompensation.parseFloatAtLeast(0f) ?: initial.elefantFootCompensationMm),
        "applyTopSurfaceCompensation" to applyTopSurfaceCompensation,
        "ensureVerticalShellThickness" to ensureVerticalShellThickness,
        "wallGenerator" to wallGenerator,
        "wallTransitionAngleDegrees" to (wallTransitionAngle.parseFloatAtLeast(0f) ?: initial.wallTransitionAngleDegrees),
        "wallTransitionFilterDeviationPercent" to (wallTransitionFilterDeviation.parseIntIn(0, 1000) ?: initial.wallTransitionFilterDeviationPercent),
        "wallTransitionLengthPercent" to (wallTransitionLength.parseIntIn(0, 1000) ?: initial.wallTransitionLengthPercent),
        "wallDistributionCount" to (wallDistributionCount.parseIntIn(0, 1000) ?: initial.wallDistributionCount),
        "minBeadWidthPercent" to (minBeadWidth.parseIntIn(0, 1000) ?: initial.minBeadWidthPercent),
        "minFeatureSizePercent" to (minFeatureSize.parseIntIn(0, 1000) ?: initial.minFeatureSizePercent),
        "minLengthFactorMm" to (minLengthFactor.parseFloatAtLeast(0f) ?: initial.minLengthFactorMm),
        "wallInfillOrder" to wallInfillOrder,
        "wallSequence" to wallSequence,
        "enableArcFitting" to enableArcFitting,
        "reduceCrossingWall" to reduceCrossingWall,
        "maxTravelDetourDistance" to maxTravelDetourDistance.trim(),
        "holeToPolyhole" to holeToPolyhole,
        "holeToPolyholeThreshold" to holeToPolyholeThreshold.trim(),
        "holeToPolyholeTwisted" to holeToPolyholeTwisted,
        "extraPerimetersOnOverhangs" to extraPerimetersOnOverhangs,
        "xyHoleCompensationMm" to (xyHoleCompensation.parseFloatAtLeast(0f) ?: initial.xyHoleCompensationMm),
        "xyContourCompensationMm" to (xyContourCompensation.parseFloatAtLeast(0f) ?: initial.xyContourCompensationMm),
        "wallCount" to (wallCount.parseIntIn(0, 1000) ?: initial.wallCount),
        "infillPercent" to (infillPercent.parseIntIn(0, 100) ?: initial.infillPercent),
        "sparseInfillPattern" to sparseInfillPattern,
        "strengthInfillDetails" to ProcessStrengthInfillDetails(
            skinInfillDensity = skinInfillDensity.parseIntIn(0, 1000) ?: initial.skinInfillDensity,
            skeletonInfillDensity = skeletonInfillDensity.parseIntIn(0, 1000) ?: initial.skeletonInfillDensity,
            infillLockDepthMm = infillLockDepth.parseFloatAtLeast(0f) ?: initial.infillLockDepthMm,
            skinInfillDepthMm = skinInfillDepth.parseFloatAtLeast(0f) ?: initial.skinInfillDepthMm,
            skinInfillLineWidth = skinInfillLineWidth.trim(),
            skeletonInfillLineWidth = skeletonInfillLineWidth.trim(),
            symmetricInfillYAxis = symmetricInfillYAxis,
            infillShiftStepMm = infillShiftStep.parseFloatAtLeast(0f) ?: initial.infillShiftStepMm,
            infillOverhangAngleDegrees = infillOverhangAngle.parseFloatAtLeast(0f) ?: initial.infillOverhangAngleDegrees,
            gapFillTarget = gapFillTarget,
            filterOutGapFillMm = filterOutGapFill.parseFloatAtLeast(0f) ?: initial.filterOutGapFillMm
        ),
        "sparseInfillFilament" to (sparseInfillFilament.parseIntIn(0, 1000) ?: initial.sparseInfillFilament),
        "sparseInfillFlowRatio" to (sparseInfillFlowRatio.parseFloatAtLeast(0f) ?: initial.sparseInfillFlowRatio),
        "lateralLatticeAngle1Degrees" to (lateralLatticeAngle1.parseFloatAtLeast(0f) ?: initial.lateralLatticeAngle1Degrees),
        "lateralLatticeAngle2Degrees" to (lateralLatticeAngle2.parseFloatAtLeast(0f) ?: initial.lateralLatticeAngle2Degrees),
        "fillMultiline" to (fillMultiline.parseIntIn(0, 1000) ?: initial.fillMultiline),
        "gapFillFlowRatio" to (gapFillFlowRatio.parseFloatAtLeast(0f) ?: initial.gapFillFlowRatio),
        "enableSupport" to enableSupport,
        "supportType" to supportType,
        "supportStyle" to supportStyle,
        "supportThresholdAngleDegrees" to (supportThresholdAngle.parseIntIn(0, 90) ?: initial.supportThresholdAngleDegrees),
        "supportThresholdOverlap" to supportThresholdOverlap.trim(),
        "supportBuildplateOnly" to supportBuildplateOnly,
        "supportCriticalRegionsOnly" to supportCriticalRegionsOnly,
        "supportRemoveSmallOverhang" to supportRemoveSmallOverhang,
        "raftFirstLayerDensityPercent" to (raftFirstLayerDensity.parseIntIn(0, 1000) ?: initial.raftFirstLayerDensityPercent),
        "raftFirstLayerExpansionMm" to (raftFirstLayerExpansion.parseFloatAtLeast(0f) ?: initial.raftFirstLayerExpansionMm),
        "raftLayers" to (raftLayers.parseIntIn(0, 1000) ?: initial.raftLayers),
        "raftContactDistanceMm" to (raftContactDistance.parseFloatAtLeast(0f) ?: initial.raftContactDistanceMm),
        "raftExpansionMm" to (raftExpansion.parseFloatAtLeast(0f) ?: initial.raftExpansionMm),
        "supportFilament" to (supportFilament.parseIntIn(0, 1000) ?: initial.supportFilament),
        "supportInterfaceFilament" to (supportInterfaceFilament.parseIntIn(0, 1000) ?: initial.supportInterfaceFilament),
        "supportInterfaceNotForBody" to supportInterfaceNotForBody,
        "supportTopZDistanceMm" to (supportTopZDistance.parseFloatAtLeast(0f) ?: initial.supportTopZDistanceMm),
        "supportBottomZDistanceMm" to (supportBottomZDistance.parseFloatAtLeast(0f) ?: initial.supportBottomZDistanceMm),
        "supportInterfaceTopLayers" to (supportInterfaceTopLayers.parseIntIn(0, 1000) ?: initial.supportInterfaceTopLayers),
        "supportInterfaceBottomLayers" to (supportInterfaceBottomLayers.parseIntIn(0, 1000) ?: initial.supportInterfaceBottomLayers),
        "supportInterfaceSpacingMm" to (supportInterfaceSpacing.parseFloatAtLeast(0f) ?: initial.supportInterfaceSpacingMm),
        "supportBottomInterfaceSpacingMm" to (supportBottomInterfaceSpacing.parseFloatAtLeast(0f) ?: initial.supportBottomInterfaceSpacingMm),
        "supportInterfaceSpeedMmPerSec" to (supportInterfaceSpeed.parseFloatAtLeast(0f) ?: initial.supportInterfaceSpeedMmPerSec),
        "supportInterfaceFlowRatio" to (supportInterfaceFlowRatio.parseFloatAtLeast(0f) ?: initial.supportInterfaceFlowRatio),
        "supportMaterialInterfaceFanSpeed" to supportMaterialInterfaceFanSpeed.trim(),
        "supportInterfacePattern" to supportInterfacePattern,
        "supportInterfaceLoopPattern" to supportInterfaceLoopPattern,
        "supportLineWidth" to supportLineWidth.trim(),
        "supportBasePattern" to supportBasePattern,
        "supportBasePatternSpacingMm" to (supportBasePatternSpacing.parseFloatAtLeast(0f) ?: initial.supportBasePatternSpacingMm),
        "supportAngleDegrees" to (supportAngle.parseFloatAtLeast(0f) ?: initial.supportAngleDegrees),
        "supportSpeedMmPerSec" to (supportSpeed.parseFloatAtLeast(0f) ?: initial.supportSpeedMmPerSec),
        "supportFlowRatio" to (supportFlowRatio.parseFloatAtLeast(0f) ?: initial.supportFlowRatio),
        "supportObjectElevationMm" to (supportObjectElevation.parseFloatAtLeast(0f) ?: initial.supportObjectElevationMm),
        "supportObjectFirstLayerGapMm" to (supportObjectFirstLayerGap.parseFloatAtLeast(0f) ?: initial.supportObjectFirstLayerGapMm),
        "supportMaxBridgeLengthMm" to (supportMaxBridgeLength.parseFloatAtLeast(0f) ?: initial.supportMaxBridgeLengthMm),
        "supportIroning" to supportIroning,
        "supportIroningPattern" to supportIroningPattern,
        "supportIroningFlowPercent" to (supportIroningFlow.parseFloatAtLeast(0f) ?: initial.supportIroningFlowPercent),
        "supportIroningSpacingMm" to (supportIroningSpacing.parseFloatAtLeast(0.05f) ?: initial.supportIroningSpacingMm),
        "supportExpansionMm" to (supportExpansion.parseFloatAtLeast(0f) ?: initial.supportExpansionMm),
        "supportObjectXyDistanceMm" to (supportObjectXyDistance.parseFloatAtLeast(0f) ?: initial.supportObjectXyDistanceMm),
        "independentSupportLayerHeight" to independentSupportLayerHeight,
        "treeSupportBranchAngleDegrees" to (treeSupportBranchAngle.parseFloatAtLeast(0f) ?: initial.treeSupportBranchAngleDegrees),
        "treeSupportBranchDiameterMm" to (treeSupportBranchDiameter.parseFloatAtLeast(0f) ?: initial.treeSupportBranchDiameterMm),
        "treeSupportWallCount" to (treeSupportWallCount.parseIntIn(0, 1000) ?: initial.treeSupportWallCount),
        "treeSupportTipDiameterMm" to (treeSupportTipDiameter.parseFloatAtLeast(0f) ?: initial.treeSupportTipDiameterMm),
        "treeSupportBranchDistanceMm" to (treeSupportBranchDistance.parseFloatAtLeast(0f) ?: initial.treeSupportBranchDistanceMm),
        "treeSupportBranchDistanceOrganicMm" to (treeSupportBranchDistanceOrganic.parseFloatAtLeast(0f) ?: initial.treeSupportBranchDistanceOrganicMm),
        "treeSupportTopRatePercent" to (treeSupportTopRate.parseIntIn(0, 1000) ?: initial.treeSupportTopRatePercent),
        "treeSupportBranchDiameterOrganicMm" to (treeSupportBranchDiameterOrganic.parseFloatAtLeast(0f) ?: initial.treeSupportBranchDiameterOrganicMm),
        "treeSupportBranchDiameterAngleDegrees" to (treeSupportBranchDiameterAngle.parseFloatAtLeast(0f) ?: initial.treeSupportBranchDiameterAngleDegrees),
        "treeSupportBranchAngleOrganicDegrees" to (treeSupportBranchAngleOrganic.parseFloatAtLeast(0f) ?: initial.treeSupportBranchAngleOrganicDegrees),
        "treeSupportPreferredBranchAngleDegrees" to (treeSupportPreferredBranchAngle.parseFloatAtLeast(0f) ?: initial.treeSupportPreferredBranchAngleDegrees),
        "treeSupportAutoBrim" to treeSupportAutoBrim,
        "treeSupportBrimWidthMm" to (treeSupportBrimWidth.parseFloatAtLeast(0f) ?: initial.treeSupportBrimWidthMm),
        "enablePrimeTower" to enablePrimeTower,
        "primeTowerWidthMm" to (primeTowerWidth.parseFloatAtLeast(0f) ?: initial.primeTowerWidthMm),
        "primeTowerDetails" to primeTowerDetails.toDetails(initial),
        "enableTowerInterfaceFeatures" to enableTowerInterfaceFeatures,
        "enableTowerInterfaceCooldownDuringTower" to enableTowerInterfaceCooldownDuringTower,
        "singleExtruderMultiMaterialPriming" to singleExtruderMultiMaterialPriming,
        "standbyTemperatureDeltaC" to (standbyTemperatureDelta.toIntOrNull() ?: initial.standbyTemperatureDeltaC),
        "wipeTowerNoSparseLayers" to wipeTowerNoSparseLayers,
        "flushIntoInfill" to flushIntoInfill,
        "flushIntoObjects" to flushIntoObjects,
        "flushIntoSupport" to flushIntoSupport,
        "skirts" to (skirts.parseIntIn(0, 1000) ?: initial.skirts),
        "skirtType" to skirtType,
        "minSkirtLengthMm" to (minSkirtLength.parseFloatAtLeast(0f) ?: initial.minSkirtLengthMm),
        "skirtDistanceMm" to (skirtDistance.parseFloatAtLeast(0f) ?: initial.skirtDistanceMm),
        "skirtStartAngleDegrees" to (skirtStartAngle.parseFloatAtLeast(0f) ?: initial.skirtStartAngleDegrees),
        "skirtSpeedMmPerSec" to (skirtSpeed.parseFloatAtLeast(0f) ?: initial.skirtSpeedMmPerSec),
        "skirtHeightLayers" to (skirtHeight.parseIntIn(0, 1000) ?: initial.skirtHeightLayers),
        "draftShield" to draftShield,
        "singleLoopDraftShield" to singleLoopDraftShield,
        "brimType" to brimType,
        "brimWidthMm" to (brimWidth.parseFloatAtLeast(0f) ?: initial.brimWidthMm),
        "brimObjectGapMm" to (brimObjectGap.parseFloatAtLeast(0f) ?: initial.brimObjectGapMm),
        "brimUseEfcOutline" to brimUseEfcOutline,
        "combineBrims" to combineBrims,
        "brimEars" to brimEars,
        "brimEarsDetectionLengthMm" to (brimEarsDetectionLength.parseFloatAtLeast(0f) ?: initial.brimEarsDetectionLengthMm),
        "brimEarsMaxAngleDegrees" to (brimEarsMaxAngle.parseFloatAtLeast(0f) ?: initial.brimEarsMaxAngleDegrees),
        "slicingMode" to slicingMode,
        "printSequence" to printSequence,
        "printOrder" to printOrder,
        "spiralMode" to spiralMode,
        "specialModeDetails" to specialModeDetails.toDetails(initial),
        "reduceInfillRetraction" to reduceInfillRetraction,
        "gcodeOutputDetails" to gcodeOutputDetails.toDetails(),
        "filamentMapMode" to filamentMapMode,
        "allowMixTemp" to allowMixTemp,
        "allowMulticolorOnePlate" to allowMulticolorOnePlate,
        "filenameFormat" to filenameFormat.trim(),
        "postProcessScripts" to postProcessScripts.trim(),
        "fuzzySkin" to fuzzySkin,
        "fuzzySkinThicknessMm" to (fuzzySkinThickness.parseFloatAtLeast(0f) ?: initial.fuzzySkinThicknessMm),
        "fuzzySkinPointDistanceMm" to (fuzzySkinPointDistance.parseFloatAtLeast(0f) ?: initial.fuzzySkinPointDistanceMm),
        "fuzzySkinFirstLayer" to fuzzySkinFirstLayer,
        "fuzzySkinMode" to fuzzySkinMode,
        "fuzzySkinNoiseType" to fuzzySkinNoiseType,
        "fuzzySkinScaleMm" to (fuzzySkinScale.parseFloatAtLeast(0f) ?: initial.fuzzySkinScaleMm),
        "fuzzySkinOctaves" to (fuzzySkinOctaves.parseIntIn(0, 1000) ?: initial.fuzzySkinOctaves),
        "fuzzySkinPersistence" to (fuzzySkinPersistence.parseFloatAtLeast(0f) ?: initial.fuzzySkinPersistence),
        "ironingType" to ironingType,
        "ironingPattern" to ironingPattern,
        "ironingFlowPercent" to (ironingFlow.parseFloatAtLeast(0f) ?: initial.ironingFlowPercent),
        "ironingSpacingMm" to (ironingSpacing.parseFloatAtLeast(0.05f) ?: initial.ironingSpacingMm),
        "ironingInsetMm" to (ironingInset.parseFloatAtLeast(0f) ?: initial.ironingInsetMm),
        "ironingAngleDegrees" to (ironingAngle.parseFloatAtLeast(0f) ?: initial.ironingAngleDegrees),
        "ironingAngleFixed" to ironingAngleFixed,
        "ironingSpeedMmPerSec" to (ironingSpeed.parseFloatAtLeast(0f) ?: initial.ironingSpeedMmPerSec),
        "qualitySurfaceDetails" to ProcessQualitySurfaceDetails(
            thickInternalBridges = thickInternalBridges,
            extraBridgeLayer = extraBridgeLayer,
            preciseZHeight = preciseZHeight,
            onlyOneWallFirstLayer = onlyOneWallFirstLayer,
            printInfillFirst = printInfillFirst,
            wallDirection = wallDirection,
            printFlowRatio = printFlowRatio.parseFloatAtLeast(0f) ?: initial.printFlowRatio,
            elefantFootCompensationLayers = elefantFootCompensationLayers.parseIntIn(0, 1000) ?: initial.elefantFootCompensationLayers,
            internalSolidInfillFlowRatio = internalSolidInfillFlowRatio.parseFloatAtLeast(0f) ?: initial.internalSolidInfillFlowRatio,
            setOtherFlowRatios = setOtherFlowRatios,
            smallAreaInfillFlowCompensation = smallAreaInfillFlowCompensation,
            makeOverhangPrintable = makeOverhangPrintable,
            makeOverhangPrintableAngleDegrees = makeOverhangPrintableAngle.parseFloatAtLeast(0f) ?: initial.makeOverhangPrintableAngleDegrees,
            makeOverhangPrintableHoleSizeMm2 = makeOverhangPrintableHoleSize.parseFloatAtLeast(0f) ?: initial.makeOverhangPrintableHoleSizeMm2,
            overhangReverse = overhangReverse,
            overhangReverseInternalOnly = overhangReverseInternalOnly,
            overhangReverseThreshold = overhangReverseThreshold.trim()
        ),
        "notes" to processNotes.trim()
    )
    return updated.withChangedNativeProcessOverridesFrom(initial)
}
