package com.yunai.phototube.ui

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yunai.phototube.data.AppContainer
import com.yunai.phototube.ui.auth.LoadingGate
import com.yunai.phototube.ui.auth.LoginScreen
import com.yunai.phototube.ui.auth.PasswordNotInitializedScreen
import com.yunai.phototube.ui.auth.ServerSetupScreen
import com.yunai.phototube.ui.account.AccountRoute
import com.yunai.phototube.ui.collections.AlbumDetailRoute
import com.yunai.phototube.ui.collections.RemoteCollectionsRoute
import com.yunai.phototube.ui.creation.CreationRoute
import com.yunai.phototube.ui.photos.RemotePhotoTimelineRoute
import com.yunai.phototube.ui.library.LibraryAssetsRoute
import com.yunai.phototube.ui.library.LibraryCollectionMode
import com.yunai.phototube.ui.memory.MemoryExclusionRoute
import com.yunai.phototube.ui.trash.TrashRoute
import com.yunai.phototube.ui.trash.TrashViewModel
import com.yunai.phototube.ui.jobs.JobCenterRoute
import com.yunai.phototube.ui.duplicates.DuplicateCenterRoute
import com.yunai.phototube.ui.search.SearchRoute
import com.yunai.phototube.ui.viewer.AssetViewerRoute
import com.yunai.phototube.ui.xmp.XmpExportRoute
import com.yunai.phototube.ui.system.SystemStatusRoute
import com.yunai.phototube.ui.tags.TagLibraryChangeKind
import com.yunai.phototube.ui.tags.TagManagementRoute
import com.yunai.phototube.ui.theme.PhotoTubeColors
import kotlinx.coroutines.launch

@Composable
fun PhotoTubeApp(appContainer: AppContainer, hostActivity: Activity) {
    val appViewModel: AppViewModel = viewModel(
        factory = AppViewModel.factory(
            sessionRepository = appContainer.sessionRepository,
            sessionEventBus = appContainer.sessionEventBus,
        ),
    )
    val state by appViewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PhotoTubeColors.Background),
    ) {
        when (state.stage) {
            AppStage.Loading -> LoadingGate()
            AppStage.ServerSetup -> ServerSetupScreen(
                serverAddress = state.serverAddress,
                isBusy = state.isBusy,
                error = state.error,
                onServerAddressChanged = appViewModel::onServerAddressChanged,
                onConnect = appViewModel::connect,
            )
            AppStage.Login -> LoginScreen(
                serverAddress = state.serverRoot?.value.orEmpty(),
                username = state.username,
                password = state.password,
                isBusy = state.isBusy,
                error = state.error,
                onUsernameChanged = appViewModel::onUsernameChanged,
                onPasswordChanged = appViewModel::onPasswordChanged,
                onLogin = appViewModel::login,
                onChooseAnotherServer = appViewModel::chooseAnotherServer,
            )
            AppStage.PasswordNotInitialized -> PasswordNotInitializedScreen(
                serverAddress = state.serverRoot?.value.orEmpty(),
                onRetry = appViewModel::retry,
                onChooseAnotherServer = appViewModel::chooseAnotherServer,
            )
            AppStage.Content -> AuthenticatedPhotoTubeApp(
                appContainer = appContainer,
                hostActivity = hostActivity,
                appState = state,
                onLogout = appViewModel::logout,
                onSwitchServer = appViewModel::switchServer,
            )
        }
    }
}

internal enum class ContentDestination {
    Photos,
    Collections,
    Creation,
    AlbumDetail,
    Archived,
    Private,
    Trash,
    Jobs,
    Duplicates,
    XmpExport,
    SystemStatus,
    MemoryExclusions,
    TagManagement,
    Account,
    Search,
    Viewer,
}

@Composable
private fun AuthenticatedPhotoTubeApp(
    appContainer: AppContainer,
    hostActivity: Activity,
    appState: AppUiState,
    onLogout: () -> Unit,
    onSwitchServer: () -> Unit,
) {
    var destination by rememberSaveable { mutableStateOf(ContentDestination.Photos) }
    var selectedAssetId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedAlbumId by rememberSaveable { mutableStateOf<String?>(null) }
    var albumReturnDestination by rememberSaveable { mutableStateOf(ContentDestination.Collections) }
    var viewerReturnDestination by rememberSaveable { mutableStateOf(ContentDestination.Photos) }
    var jobsReturnDestination by rememberSaveable { mutableStateOf(ContentDestination.Photos) }
    var searchReturnDestination by rememberSaveable { mutableStateOf(ContentDestination.Photos) }
    var timelineRefreshRevision by rememberSaveable { mutableIntStateOf(0) }
    var collectionsRefreshRevision by rememberSaveable { mutableIntStateOf(0) }
    var albumDetailRefreshRevision by rememberSaveable { mutableIntStateOf(0) }
    var libraryRefreshRevision by rememberSaveable { mutableIntStateOf(0) }
    var trashRefreshRevision by rememberSaveable { mutableIntStateOf(0) }
    var duplicatesRefreshRevision by rememberSaveable { mutableIntStateOf(0) }
    var searchRefreshRevision by rememberSaveable { mutableIntStateOf(0) }
    var tagLibraryRevision by rememberSaveable { mutableIntStateOf(0) }
    var deletedTagId by rememberSaveable { mutableStateOf<Long?>(null) }
    var duplicatesPrivateScope by rememberSaveable { mutableStateOf(false) }
    var xmpPrivateScope by rememberSaveable { mutableStateOf(false) }
    var viewerAssetPrivate by remember { mutableStateOf<Boolean?>(null) }
    val scope = rememberCoroutineScope()
    val trashViewModel: TrashViewModel = viewModel(
        key = "trash:${requireNotNull(appState.serverRoot).value}:${appState.user?.id ?: appState.username}",
        factory = TrashViewModel.factory(
            trashRepository = appContainer.trashRepository,
            assetRepository = appContainer.assetRepository,
        ),
    )

    fun invalidateAssetConsumers(changes: Set<AssetChangeKind>) {
        invalidatedAssetConsumers(changes).forEach { consumer ->
            when (consumer) {
                AssetConsumer.TIMELINE_AND_HOME -> timelineRefreshRevision += 1
                AssetConsumer.COLLECTIONS -> collectionsRefreshRevision += 1
                AssetConsumer.ALBUM_DETAIL -> albumDetailRefreshRevision += 1
                AssetConsumer.LIBRARY -> libraryRefreshRevision += 1
                AssetConsumer.TRASH -> trashRefreshRevision += 1
                AssetConsumer.DUPLICATES -> duplicatesRefreshRevision += 1
                AssetConsumer.SEARCH -> searchRefreshRevision += 1
                AssetConsumer.TAG_LIBRARY -> {
                    tagLibraryRevision += 1
                    deletedTagId = null
                }
            }
        }
    }

    LaunchedEffect(trashViewModel) {
        trashViewModel.changes.collect { change ->
            invalidateAssetConsumers(setOf(change.kind))
        }
    }

    val privateContentVisible = shouldProtectPrivateContent(
        destination = destination,
        viewerReturnDestination = viewerReturnDestination,
        viewerAssetPrivate = viewerAssetPrivate,
        duplicatesPrivateScope = duplicatesPrivateScope,
        xmpPrivateScope = xmpPrivateScope,
    )

    PrivateContentProtectionEffect(
        activity = hostActivity,
        enabled = privateContentVisible,
        onBackground = {
            scope.launch {
                runCatching { appContainer.sessionRepository.lockPrivateAccess() }
                timelineRefreshRevision += 1
                duplicatesPrivateScope = false
                xmpPrivateScope = false
                viewerAssetPrivate = null
                destination = ContentDestination.Photos
            }
        },
    )

    BackHandler(enabled = destination != ContentDestination.Collections) {
        destination = when (destination) {
            ContentDestination.Viewer -> {
                viewerAssetPrivate = null
                viewerReturnDestination
            }
            ContentDestination.AlbumDetail -> {
                when (albumReturnDestination) {
                    ContentDestination.Photos -> timelineRefreshRevision += 1
                    else -> collectionsRefreshRevision += 1
                }
                albumReturnDestination
            }
            ContentDestination.Photos -> ContentDestination.Collections
            ContentDestination.Creation -> ContentDestination.Photos
            ContentDestination.Archived,
            ContentDestination.Private,
            ContentDestination.Trash,
            ContentDestination.XmpExport,
            ContentDestination.SystemStatus,
            ContentDestination.MemoryExclusions,
            ContentDestination.TagManagement,
            ContentDestination.Account,
            -> ContentDestination.Photos
            ContentDestination.Jobs -> jobsReturnDestination
            ContentDestination.Duplicates -> {
                duplicatesPrivateScope = false
                ContentDestination.Photos
            }
            ContentDestination.Search -> searchReturnDestination
            ContentDestination.Collections -> ContentDestination.Collections
        }
    }

    when (destination) {
        ContentDestination.Photos -> RemotePhotoTimelineRoute(
            repository = appContainer.timelineRepository,
            homeRepository = appContainer.homeRepository,
            folderRepository = appContainer.folderRepository,
            tagRepository = appContainer.tagRepository,
            onOpenCollections = { destination = ContentDestination.Collections },
            onOpenCreation = { destination = ContentDestination.Creation },
            onOpenSearch = {
                searchReturnDestination = ContentDestination.Photos
                destination = ContentDestination.Search
            },
            onOpenAsset = { assetId ->
                selectedAssetId = assetId
                viewerReturnDestination = ContentDestination.Photos
                destination = ContentDestination.Viewer
            },
            onOpenAlbum = { albumId ->
                selectedAlbumId = albumId
                albumReturnDestination = ContentDestination.Photos
                destination = ContentDestination.AlbumDetail
            },
            refreshRevision = timelineRefreshRevision,
            onOpenArchived = { destination = ContentDestination.Archived },
            onOpenPrivate = { destination = ContentDestination.Private },
            onOpenTrash = { destination = ContentDestination.Trash },
            onOpenJobs = {
                jobsReturnDestination = ContentDestination.Photos
                destination = ContentDestination.Jobs
            },
            onOpenDuplicates = { destination = ContentDestination.Duplicates },
            onOpenXmpExport = { destination = ContentDestination.XmpExport },
            onOpenSystemStatus = { destination = ContentDestination.SystemStatus },
            onOpenMemoryExclusions = { destination = ContentDestination.MemoryExclusions },
            onOpenTagManagement = { destination = ContentDestination.TagManagement },
            onOpenAccount = { destination = ContentDestination.Account },
            tagLibraryRevision = tagLibraryRevision,
            deletedTagId = deletedTagId,
        )
        ContentDestination.Collections -> RemoteCollectionsRoute(
            repository = appContainer.albumRepository,
            onAlbumClick = { albumId ->
                selectedAlbumId = albumId
                albumReturnDestination = ContentDestination.Collections
                destination = ContentDestination.AlbumDetail
            },
            onOpenPhotos = { destination = ContentDestination.Photos },
            onOpenCreation = { destination = ContentDestination.Creation },
            refreshRevision = collectionsRefreshRevision,
        )
        ContentDestination.Creation -> CreationRoute(
            repository = appContainer.timelineRepository,
            refreshRevision = timelineRefreshRevision,
            onOpenPhotos = { destination = ContentDestination.Photos },
            onOpenCollections = { destination = ContentDestination.Collections },
            onOpenSearch = {
                searchReturnDestination = ContentDestination.Creation
                destination = ContentDestination.Search
            },
            onOpenAsset = { assetId ->
                selectedAssetId = assetId
                viewerReturnDestination = ContentDestination.Creation
                destination = ContentDestination.Viewer
            },
        )
        ContentDestination.AlbumDetail -> {
            val albumId = selectedAlbumId
            if (albumId == null) {
                LoadingGate()
            } else {
                AlbumDetailRoute(
                    albumId = albumId,
                    repository = appContainer.albumRepository,
                    timelineRepository = appContainer.timelineRepository,
                    onBack = {
                        timelineRefreshRevision += 1
                        collectionsRefreshRevision += 1
                        destination = albumReturnDestination
                    },
                    onDeleted = {
                        timelineRefreshRevision += 1
                        collectionsRefreshRevision += 1
                        destination = albumReturnDestination
                    },
                    onChanged = {
                        timelineRefreshRevision += 1
                        collectionsRefreshRevision += 1
                    },
                    onOpenAsset = { assetId ->
                        selectedAssetId = assetId
                        viewerReturnDestination = ContentDestination.AlbumDetail
                        destination = ContentDestination.Viewer
                    },
                    refreshRevision = albumDetailRefreshRevision,
                )
            }
        }
        ContentDestination.Archived,
        ContentDestination.Private,
        -> {
            val mode = if (destination == ContentDestination.Archived) {
                LibraryCollectionMode.ARCHIVED
            } else {
                LibraryCollectionMode.PRIVATE
            }
            LibraryAssetsRoute(
                mode = mode,
                timelineRepository = appContainer.timelineRepository,
                sessionRepository = appContainer.sessionRepository,
                refreshRevision = libraryRefreshRevision,
                onBack = {
                    timelineRefreshRevision += 1
                    destination = ContentDestination.Photos
                },
                onOpenAsset = { assetId ->
                    selectedAssetId = assetId
                    viewerReturnDestination = destination
                    destination = ContentDestination.Viewer
                },
            )
        }
        ContentDestination.Trash -> TrashRoute(
            model = trashViewModel,
            refreshRevision = trashRefreshRevision,
            onBack = { destination = ContentDestination.Photos },
            onOpenAsset = { assetId ->
                selectedAssetId = assetId
                viewerReturnDestination = ContentDestination.Trash
                destination = ContentDestination.Viewer
            },
        )
        ContentDestination.Jobs -> JobCenterRoute(
            repository = appContainer.jobRepository,
            onBack = { destination = jobsReturnDestination },
        )
        ContentDestination.Duplicates -> DuplicateCenterRoute(
            repository = appContainer.duplicateRepository,
            sessionRepository = appContainer.sessionRepository,
            refreshRevision = duplicatesRefreshRevision,
            onPrivateScopeChanged = { duplicatesPrivateScope = it },
            onBack = {
                duplicatesPrivateScope = false
                destination = ContentDestination.Photos
            },
            onOpenAsset = { assetId ->
                selectedAssetId = assetId
                viewerReturnDestination = ContentDestination.Duplicates
                destination = ContentDestination.Viewer
            },
        )
        ContentDestination.Search -> SearchRoute(
            repository = appContainer.timelineRepository,
            refreshRevision = searchRefreshRevision,
            onBack = { destination = searchReturnDestination },
            onOpenAsset = { assetId ->
                selectedAssetId = assetId
                viewerReturnDestination = ContentDestination.Search
                destination = ContentDestination.Viewer
            },
        )
        ContentDestination.XmpExport -> XmpExportRoute(
            repository = appContainer.xmpExportRepository,
            sessionRepository = appContainer.sessionRepository,
            onPrivateScopeChanged = { xmpPrivateScope = it },
            onBack = {
                xmpPrivateScope = false
                destination = ContentDestination.Photos
            },
        )
        ContentDestination.SystemStatus -> SystemStatusRoute(
            repository = appContainer.systemRepository,
            onBack = { destination = ContentDestination.Photos },
            onOpenJobs = {
                jobsReturnDestination = ContentDestination.SystemStatus
                destination = ContentDestination.Jobs
            },
        )
        ContentDestination.MemoryExclusions -> MemoryExclusionRoute(
            repository = appContainer.memoryExclusionRepository,
            onBack = { destination = ContentDestination.Photos },
            onHomeChanged = { timelineRefreshRevision += 1 },
        )
        ContentDestination.TagManagement -> TagManagementRoute(
            repository = appContainer.tagRepository,
            refreshRevision = tagLibraryRevision,
            onBack = { destination = ContentDestination.Photos },
            onChanged = { change ->
                tagLibraryRevision += 1
                deletedTagId = change.tagId.takeIf { change.kind == TagLibraryChangeKind.DELETED }
                timelineRefreshRevision += 1
                collectionsRefreshRevision += 1
                albumDetailRefreshRevision += 1
                libraryRefreshRevision += 1
                trashRefreshRevision += 1
                searchRefreshRevision += 1
            },
        )
        ContentDestination.Account -> AccountRoute(
            user = appState.user,
            serverRoot = requireNotNull(appState.serverRoot) { "认证内容区缺少服务器地址" },
            isBusy = appState.isBusy,
            error = appState.error,
            onBack = { destination = ContentDestination.Photos },
            onLogout = onLogout,
            onSwitchServer = onSwitchServer,
        )
        ContentDestination.Viewer -> {
            val assetId = selectedAssetId
            if (assetId == null) {
                LoadingGate()
            } else {
                AssetViewerRoute(
                    assetId = assetId,
                    repository = appContainer.assetRepository,
                    editRepository = appContainer.editRepository,
                    tagRepository = appContainer.tagRepository,
                    sessionRepository = appContainer.sessionRepository,
                    httpClient = appContainer.serviceFactory.sharedHttpClient,
                    refreshRevision = tagLibraryRevision,
                    onPrivateAssetVisibilityChanged = { viewerAssetPrivate = it },
                    onBack = { changes ->
                        viewerAssetPrivate = null
                        invalidateAssetConsumers(changes)
                        destination = viewerReturnDestination
                    },
                )
            }
        }
    }
}

internal fun shouldProtectPrivateContent(
    destination: ContentDestination,
    viewerReturnDestination: ContentDestination,
    viewerAssetPrivate: Boolean?,
    duplicatesPrivateScope: Boolean,
    xmpPrivateScope: Boolean,
): Boolean = when (destination) {
    ContentDestination.Private -> true
    ContentDestination.Duplicates -> duplicatesPrivateScope
    ContentDestination.XmpExport -> xmpPrivateScope
    ContentDestination.Viewer -> viewerAssetPrivate != false ||
        viewerReturnDestination == ContentDestination.Private ||
        (viewerReturnDestination == ContentDestination.Duplicates && duplicatesPrivateScope)
    else -> false
}

@Composable
private fun PrivateContentProtectionEffect(
    activity: Activity,
    enabled: Boolean,
    onBackground: () -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnBackground by rememberUpdatedState(onBackground)
    DisposableEffect(activity, lifecycleOwner, enabled) {
        if (!enabled) return@DisposableEffect onDispose { }
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) currentOnBackground()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
