package com.mobileslicer.modelsearch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mobileslicer.appBackgroundGradient
import com.mobileslicer.appBodyColor
import com.mobileslicer.appCardColor
import com.mobileslicer.appCardColorMuted
import com.mobileslicer.appOutlineColor
import com.mobileslicer.appTitleColor
import com.mobileslicer.modelsearch.ModelSearchImplementationGuide
import com.mobileslicer.modelsearch.importflow.FindImportState
import com.mobileslicer.modelsearch.importflow.ImportFailureReason
import com.mobileslicer.modelsearch.sources.ModelSourcePolicy
import com.mobileslicer.modelsearch.thingiverse.ThingiverseFileResult
import com.mobileslicer.modelsearch.thingiverse.ThingiverseSearchResult
import com.mobileslicer.modelsearch.thingiverse.ThingiverseSearchUiState
import android.graphics.BitmapFactory
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val thingiverseThumbnailMemoryCache = Collections.synchronizedMap(mutableMapOf<String, ImageBitmap>())

@Composable
fun FindAndImportModelScreen(
    state: FindImportState,
    sources: List<ModelSourcePolicy>,
    thingiverseState: ThingiverseSearchUiState,
    thingiverseQuery: String,
    thingiverseSignedInLabel: String?,
    thingiverseAuthMessage: String?,
    thingiverseSignInAvailable: Boolean,
    importInProgress: Boolean,
    onOpenSource: (ModelSourcePolicy) -> Unit,
    onThingiverseQueryChange: (String) -> Unit,
    onThingiverseSearch: () -> Unit,
    onThingiverseLoadMore: () -> Unit,
    onThingiverseOpenFiles: (ThingiverseSearchResult) -> Unit,
    onThingiverseOpenPage: (String) -> Unit,
    onThingiverseImportFile: (ThingiverseSearchResult, ThingiverseFileResult) -> Unit,
    onThingiverseImportFiles: (ThingiverseSearchResult, List<ThingiverseFileResult>) -> Unit,
    onThingiverseBackToResults: () -> Unit,
    onThingiverseClearSearch: () -> Unit,
    onThingiverseSignIn: () -> Unit,
    onThingiverseSignOut: () -> Unit,
    onImportDownloadedFile: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.background(Brush.verticalGradient(appBackgroundGradient())),
        containerColor = Color.Transparent,
        topBar = {
            FindImportTopBar(onBack = onBack)
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (state) {
                FindImportState.SourcePicker,
                is FindImportState.ExternalSiteOpened,
                is FindImportState.AwaitingUserFile -> SourcePickerContent(
                    sources = sources,
                    awaitingFile = state is FindImportState.AwaitingUserFile,
                    thingiverseState = thingiverseState,
                    thingiverseQuery = thingiverseQuery,
                    thingiverseSignedInLabel = thingiverseSignedInLabel,
                    thingiverseAuthMessage = thingiverseAuthMessage,
                    thingiverseSignInAvailable = thingiverseSignInAvailable,
                    importInProgress = importInProgress,
                    onOpenSource = onOpenSource,
                    onThingiverseQueryChange = onThingiverseQueryChange,
                    onThingiverseSearch = onThingiverseSearch,
                    onThingiverseLoadMore = onThingiverseLoadMore,
                    onThingiverseOpenFiles = onThingiverseOpenFiles,
                    onThingiverseOpenPage = onThingiverseOpenPage,
                    onThingiverseImportFile = onThingiverseImportFile,
                    onThingiverseImportFiles = onThingiverseImportFiles,
                    onThingiverseBackToResults = onThingiverseBackToResults,
                    onThingiverseClearSearch = onThingiverseClearSearch,
                    onThingiverseSignIn = onThingiverseSignIn,
                    onThingiverseSignOut = onThingiverseSignOut,
                    onImportDownloadedFile = onImportDownloadedFile
                )
                is FindImportState.ImportBlocked -> BlockedImportContent(
                    reason = state.reason,
                    onImportDownloadedFile = onImportDownloadedFile,
                    onBack = onBack
                )
            }
        }
    }
}

@Composable
private fun FindImportTopBar(onBack: () -> Unit) {
    val titleColor = appTitleColor()
    val outlineColor = appOutlineColor()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(appCardColorMuted())
                .border(1.dp, outlineColor, RoundedCornerShape(18.dp))
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = titleColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = ModelSearchImplementationGuide.HOME_ENTRY_LABEL,
                style = MaterialTheme.typography.titleLarge,
                color = titleColor,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Search Thingiverse or browse source sites.",
                style = MaterialTheme.typography.bodyMedium,
                color = appBodyColor(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SourcePickerContent(
    sources: List<ModelSourcePolicy>,
    awaitingFile: Boolean,
    thingiverseState: ThingiverseSearchUiState,
    thingiverseQuery: String,
    thingiverseSignedInLabel: String?,
    thingiverseAuthMessage: String?,
    thingiverseSignInAvailable: Boolean,
    importInProgress: Boolean,
    onOpenSource: (ModelSourcePolicy) -> Unit,
    onThingiverseQueryChange: (String) -> Unit,
    onThingiverseSearch: () -> Unit,
    onThingiverseLoadMore: () -> Unit,
    onThingiverseOpenFiles: (ThingiverseSearchResult) -> Unit,
    onThingiverseOpenPage: (String) -> Unit,
    onThingiverseImportFile: (ThingiverseSearchResult, ThingiverseFileResult) -> Unit,
    onThingiverseImportFiles: (ThingiverseSearchResult, List<ThingiverseFileResult>) -> Unit,
    onThingiverseBackToResults: () -> Unit,
    onThingiverseClearSearch: () -> Unit,
    onThingiverseSignIn: () -> Unit,
    onThingiverseSignOut: () -> Unit,
    onImportDownloadedFile: () -> Unit
) {
    var showOtherSources by remember { mutableStateOf(false) }
    ThingiverseApiPanel(
        state = thingiverseState,
        query = thingiverseQuery,
        signedInLabel = thingiverseSignedInLabel,
        authMessage = thingiverseAuthMessage,
        signInAvailable = thingiverseSignInAvailable,
        importInProgress = importInProgress,
        onQueryChange = onThingiverseQueryChange,
        onSearch = onThingiverseSearch,
        onLoadMore = onThingiverseLoadMore,
        onOpenFiles = onThingiverseOpenFiles,
        onOpenPage = onThingiverseOpenPage,
        onImportFile = onThingiverseImportFile,
        onImportFiles = onThingiverseImportFiles,
        onBackToResults = onThingiverseBackToResults,
        onClearSearch = onThingiverseClearSearch,
        onSignIn = onThingiverseSignIn,
        onSignOut = onThingiverseSignOut
    )
    OtherSourcesSection(
        sources = sources,
        expanded = showOtherSources,
        importInProgress = importInProgress,
        onExpandedChange = { showOtherSources = it },
        onOpenSource = onOpenSource
    )
}

@Composable
private fun OtherSourcesSection(
    sources: List<ModelSourcePolicy>,
    expanded: Boolean,
    importInProgress: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onOpenSource: (ModelSourcePolicy) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = appCardColor().copy(alpha = 0.70f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, appOutlineColor().copy(alpha = 0.64f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpandedChange(!expanded) },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Other sources",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Browse original sites when direct import is not available",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OutlinedButton(
                    onClick = { onExpandedChange(!expanded) },
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(if (expanded) "Hide" else "Show")
                }
            }
            if (expanded) {
                sources.forEach { source ->
                    SourceRow(
                        source = source,
                        enabled = !importInProgress && source.canOpenExternally,
                        onOpenSource = onOpenSource
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    detail: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CompactThingiverseSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (enabled) {
        MaterialTheme.colorScheme.outline
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.40f)
    }
    val searchEnabled = enabled && value.isNotBlank()

    Row(
        modifier = modifier
            .height(56.dp)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .padding(start = 14.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { if (searchEnabled) onSearch() }),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (value.isBlank()) {
                        Text(
                            "Search Thingiverse",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    innerTextField()
                }
            },
            modifier = Modifier.weight(1f)
        )
        Text(
            "Search",
            style = MaterialTheme.typography.labelMedium,
            color = if (searchEnabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.clickable(enabled = searchEnabled) { onSearch() }
        )
    }
}

@Composable
private fun ThingiverseApiPanel(
    state: ThingiverseSearchUiState,
    query: String,
    signedInLabel: String?,
    authMessage: String?,
    signInAvailable: Boolean,
    importInProgress: Boolean,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenFiles: (ThingiverseSearchResult) -> Unit,
    onOpenPage: (String) -> Unit,
    onImportFile: (ThingiverseSearchResult, ThingiverseFileResult) -> Unit,
    onImportFiles: (ThingiverseSearchResult, List<ThingiverseFileResult>) -> Unit,
    onBackToResults: () -> Unit,
    onClearSearch: () -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = appCardColor().copy(alpha = 0.70f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, appOutlineColor().copy(alpha = 0.64f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Search models, then choose a printable file.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                StatusPill("STL / 3MF")
            }
            ThingiverseAuthRow(
                signedInLabel = signedInLabel,
                authMessage = authMessage,
                signInAvailable = signInAvailable,
                importInProgress = importInProgress,
                onSignIn = onSignIn,
                onSignOut = onSignOut
            )
            CompactThingiverseSearchField(
                value = query,
                onValueChange = onQueryChange,
                enabled = !importInProgress && state !is ThingiverseSearchUiState.Searching,
                modifier = Modifier.fillMaxWidth(),
                onSearch = {
                    if (!importInProgress && query.isNotBlank() && state !is ThingiverseSearchUiState.Searching) {
                        focusManager.clearFocus()
                        onSearch()
                    }
                }
            )
            if (signedInLabel != null && state is ThingiverseSearchUiState.Idle) {
                Text(
                    if (query.isBlank()) {
                        "Try Benchy, phone stand, or calibration cube."
                    } else {
                        "Press Search to update results."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            authMessage?.takeIf { it.isNotBlank() }?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            when (state) {
                ThingiverseSearchUiState.Idle -> Unit
                ThingiverseSearchUiState.MissingToken -> {
                    if (signedInLabel == null) {
                        Text(
                            if (signInAvailable) {
                                "Sign in to Thingiverse to search and import models."
                            } else {
                                "Thingiverse sign-in is not configured in this build."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                ThingiverseSearchUiState.Searching -> Text(
                    "Searching Thingiverse...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                is ThingiverseSearchUiState.SearchResults -> ThingiverseResultsList(
                    query = state.query,
                    results = state.results,
                    canLoadMore = state.canLoadMore,
                    isLoadingMore = state.isLoadingMore,
                    onOpenFiles = onOpenFiles,
                    onClearSearch = onClearSearch,
                    onLoadMore = onLoadMore
                )
                is ThingiverseSearchUiState.LoadingFiles -> Text(
                    "Loading files for ${state.thing.name}...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                is ThingiverseSearchUiState.FileResults -> ThingiverseFilesList(
                    thing = state.thing,
                    files = state.files,
                    importInProgress = importInProgress,
                    onImportFile = onImportFile,
                    onImportFiles = onImportFiles,
                    onOpenPage = onOpenPage,
                    onBackToResults = onBackToResults,
                    onClearSearch = onClearSearch
                )
                is ThingiverseSearchUiState.ImportingFile -> ThingiverseImportingRow(
                    thing = state.thing,
                    file = state.file,
                    indexLabel = null
                )
                is ThingiverseSearchUiState.ImportingFiles -> ThingiverseImportingRow(
                    thing = state.thing,
                    file = state.currentFile,
                    indexLabel = "Importing ${state.completedCount + 1} of ${state.totalCount}"
                )
                is ThingiverseSearchUiState.Error -> ThingiverseErrorRow(
                    message = state.message,
                    canRetry = state.canRetry,
                    onRetry = onSearch
                )
            }
        }
    }
}

@Composable
private fun ThingiverseAuthRow(
    signedInLabel: String?,
    authMessage: String?,
    signInAvailable: Boolean,
    importInProgress: Boolean,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (signedInLabel == null) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = if (signInAvailable) {
                        "Sign in to use Thingiverse imports."
                    } else {
                        "Thingiverse sign-in is not configured."
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            OutlinedButton(
                onClick = onSignIn,
                enabled = signInAvailable && !importInProgress,
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Sign in")
            }
        } else {
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
            ) {
                Text(
                    text = signedInLabel.replace("Signed in to Thingiverse as ", "Signed in: "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                )
            }
            TextButton(
                onClick = onSignOut,
                enabled = !importInProgress
            ) {
                Text("Sign out")
            }
        }
    }
}

@Composable
private fun ThingiverseResultsList(
    query: String,
    results: List<ThingiverseSearchResult>,
    canLoadMore: Boolean,
    isLoadingMore: Boolean,
    onOpenFiles: (ThingiverseSearchResult) -> Unit,
    onClearSearch: () -> Unit,
    onLoadMore: () -> Unit
) {
    if (results.isEmpty()) {
        Text("No Thingiverse results found.", style = MaterialTheme.typography.bodyMedium)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ResultsHeader(
            title = "${results.size} results",
            detail = query.takeIf { it.isNotBlank() }?.let { "for \"$it\"" },
            actionLabel = "Clear",
            onAction = onClearSearch
        )
        results.forEach { result ->
            ThingiverseResultRow(
                result = result,
                onOpenFiles = onOpenFiles
            )
        }
        if (canLoadMore) {
            OutlinedButton(
                onClick = onLoadMore,
                enabled = !isLoadingMore,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Text(if (isLoadingMore) "Loading more..." else "Load more results")
            }
        }
    }
}

@Composable
private fun ThingiverseResultRow(
    result: ThingiverseSearchResult,
    onOpenFiles: (ThingiverseSearchResult) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onOpenFiles(result) },
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.20f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RemoteThumbnail(
                thumbnailUrl = result.thumbnailUrl,
                fallbackLabel = "3D",
                size = 64
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    result.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    result.creatorName ?: "Unknown creator",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    listOfNotNull(result.license, result.fileCount?.let { "$it files" }).joinToString(" | "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            OutlinedButton(
                onClick = { onOpenFiles(result) },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .height(40.dp)
                    .width(88.dp)
            ) {
                Text("Files")
            }
        }
    }
}

@Composable
private fun ThingiverseErrorRow(
    message: String,
    canRetry: Boolean,
    onRetry: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f)
        )
        if (canRetry) {
            OutlinedButton(
                onClick = onRetry,
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Retry")
            }
        }
    }
}

@Composable
private fun ResultsCountLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp)
    )
}

@Composable
private fun ResultsHeader(
    title: String,
    detail: String?,
    actionLabel: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            detail?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        TextButton(onClick = onAction) {
            Text(actionLabel)
        }
    }
}

@Composable
private fun StatusPill(label: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.20f))
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun RemoteThumbnail(
    thumbnailUrl: String?,
    fallbackLabel: String,
    size: Int
) {
    val image = rememberRemoteThumbnail(thumbnailUrl)
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.20f), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                fallbackLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun rememberRemoteThumbnail(thumbnailUrl: String?): ImageBitmap? {
    val imageState = produceState<ImageBitmap?>(initialValue = thumbnailUrl?.let { thingiverseThumbnailMemoryCache[it] }, thumbnailUrl) {
        value = withContext(Dispatchers.IO) {
            val url = thumbnailUrl?.takeIf { it.startsWith("https://") } ?: return@withContext null
            thingiverseThumbnailMemoryCache[url]?.let { return@withContext it }
            runCatching {
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = 8_000
                    readTimeout = 12_000
                    requestMethod = "GET"
                    setRequestProperty("Accept", "image/*")
                    setRequestProperty("User-Agent", "MobileSlicer Thingiverse Thumbnail Preview")
                }
                try {
                    if (connection.responseCode !in 200..299) {
                        return@runCatching null
                    }
                    connection.inputStream.use { input ->
                        BitmapFactory.decodeStream(input)?.asImageBitmap()?.also { decoded ->
                            thingiverseThumbnailMemoryCache[url] = decoded
                        }
                    }
                } finally {
                    connection.disconnect()
                }
            }.getOrNull()
        }
    }
    return imageState.value
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ThingiverseFilesList(
    thing: ThingiverseSearchResult,
    files: List<ThingiverseFileResult>,
    importInProgress: Boolean,
    onImportFile: (ThingiverseSearchResult, ThingiverseFileResult) -> Unit,
    onImportFiles: (ThingiverseSearchResult, List<ThingiverseFileResult>) -> Unit,
    onOpenPage: (String) -> Unit,
    onBackToResults: () -> Unit,
    onClearSearch: () -> Unit
) {
    fun sortedPrintableFiles(): List<ThingiverseFileResult> =
        files
            .filter { it.isSupportedModelFile }
            .sortedWith(compareBy<ThingiverseFileResult> { it.printableFileSortKey }.thenBy { it.displayName.lowercase() })

    val supportedFiles = sortedPrintableFiles()
    val unsupportedCount = files.size - supportedFiles.size
    var selectedFileIds by remember(thing.thingId, files) { mutableStateOf<Set<Long>>(emptySet()) }
    val selectedFiles = supportedFiles.filter { it.fileId in selectedFileIds }
    val selectionMode = selectedFileIds.isNotEmpty()
    fun toggleSelection(file: ThingiverseFileResult) {
        selectedFileIds = if (file.fileId in selectedFileIds) {
            selectedFileIds - file.fileId
        } else {
            selectedFileIds + file.fileId
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            RemoteThumbnail(
                thumbnailUrl = thing.thumbnailUrl,
                fallbackLabel = "3D",
                size = 64
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    thing.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "Choose a file to import",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onBackToResults) {
                Text("Results")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            thing.publicUrl?.takeIf { it.isNotBlank() }?.let { url ->
                OutlinedButton(
                    onClick = { onOpenPage(url) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Open original page")
                }
            }
            TextButton(
                onClick = onClearSearch,
                modifier = Modifier.weight(1f)
            ) {
                Text("Clear search")
            }
        }
        ResultsCountLabel(
            buildString {
                append("${supportedFiles.size} printable files")
                if (unsupportedCount > 0) {
                    append(" | $unsupportedCount unsupported hidden")
                }
            }
        )
        if (supportedFiles.isEmpty()) {
            Text("No STL or 3MF files found for this model.", style = MaterialTheme.typography.bodyMedium)
        }
        if (supportedFiles.isNotEmpty()) {
            if (selectionMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { onImportFiles(thing, selectedFiles) },
                        enabled = !importInProgress && selectedFiles.isNotEmpty(),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                    ) {
                        Text("Import selected (${selectedFiles.size})")
                    }
                    TextButton(
                        onClick = { selectedFileIds = emptySet() },
                        enabled = !importInProgress
                    ) {
                        Text("Cancel")
                    }
                }
            } else if (supportedFiles.size > 1) {
                Button(
                    onClick = { onImportFiles(thing, supportedFiles) },
                    enabled = !importInProgress,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                ) {
                    Text("Import all ${supportedFiles.size}")
                }
            }
        }
        supportedFiles.forEach { file ->
            val selected = file.fileId in selectedFileIds
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .combinedClickable(
                        enabled = !importInProgress,
                        onClick = {
                            if (selectionMode) {
                                toggleSelection(file)
                            }
                        },
                        onLongClick = {
                            toggleSelection(file)
                        }
                    ),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.20f),
                shape = RoundedCornerShape(8.dp),
                border = if (selected) {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
                } else {
                    null
                }
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RemoteThumbnail(
                        thumbnailUrl = file.thumbnailUrl,
                        fallbackLabel = file.extensionLabel,
                        size = 56
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            file.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            listOfNotNull(file.extensionLabel, file.displaySize).joinToString(" | "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (selectionMode) {
                        Checkbox(
                            checked = selected,
                            onCheckedChange = { toggleSelection(file) },
                            enabled = !importInProgress
                        )
                    } else {
                        Button(
                            onClick = { onImportFile(thing, file) },
                            enabled = !importInProgress,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .height(44.dp)
                                .width(96.dp)
                        ) {
                            Text("Import")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThingiverseImportingRow(
    thing: ThingiverseSearchResult,
    file: ThingiverseFileResult?,
    indexLabel: String?
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RemoteThumbnail(
                thumbnailUrl = file?.thumbnailUrl ?: thing.thumbnailUrl,
                fallbackLabel = file?.extensionLabel ?: "3D",
                size = 46
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    indexLabel ?: "Importing from Thingiverse",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    file?.displayName ?: thing.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            StatusPill(file?.extensionLabel ?: "STL / 3MF")
        }
    }
}

@Composable
private fun SourceRow(
    source: ModelSourcePolicy,
    enabled: Boolean,
    onOpenSource: (ModelSourcePolicy) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.24f))
    ) {
        Row(
            modifier = Modifier
                .clickable(enabled = enabled) { onOpenSource(source) }
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .heightIn(min = 64.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SourceInitial(source.displayName)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = source.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Browse there, then open the downloaded file here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            OutlinedButton(
                onClick = { onOpenSource(source) },
                enabled = enabled
            ) {
                Text("Browse")
            }
        }
    }
}

@Composable
private fun SourceInitial(sourceName: String) {
    val letter = sourceName.firstOrNull()?.uppercaseChar()?.toString().orEmpty()
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = letter,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun BlockedImportContent(
    reason: ImportFailureReason,
    onImportDownloadedFile: () -> Unit,
    onBack: () -> Unit
) {
    StatusCard(
        title = "Import blocked",
        body = reason.userMessage
    )
    Button(
        onClick = onImportDownloadedFile,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(ModelSearchImplementationGuide.IMPORT_DOWNLOADED_FILE_LABEL)
    }
    TextButton(
        onClick = onBack,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Back to MobileSlicer")
    }
}

@Composable
private fun StatusCard(
    title: String,
    body: String
) {
    CompactPanel(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Spacer(modifier = Modifier.height(2.dp))
}

@Composable
private fun CompactPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.24f)
        ),
        content = { content() }
    )
}
