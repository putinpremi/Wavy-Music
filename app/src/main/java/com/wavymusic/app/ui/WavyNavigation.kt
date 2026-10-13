package com.wavymusic.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.wavymusic.app.playback.PlaybackViewModel
import com.wavymusic.app.ui.theme.WavyColors
import com.wavymusic.app.ui.theme.WavyTheme
import com.wavymusic.app.ui.theme.WavyWaveMark
import androidx.compose.ui.platform.LocalContext
import com.wavymusic.app.catalog.CatalogGraph
import androidx.compose.runtime.remember
import com.wavymusic.app.catalog.CatalogDetailRepository
import com.wavymusic.app.catalog.storage.WavyCatalogDatabase

internal object WavyRoutes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val NOW_PLAYING = "now-playing"
    const val QUEUE = "queue"
    const val LYRICS = "lyrics"
    const val DOWNLOADS = "downloads"
    const val ARTIST = "artist"
    const val ALBUM = "album"

    val all = listOf(
        HOME, SEARCH, LIBRARY, SETTINGS,
        NOW_PLAYING, QUEUE, LYRICS, DOWNLOADS, ARTIST, ALBUM
    )
}

private data class WavyTab(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val bottomTabs = listOf(
    WavyTab(WavyRoutes.HOME, "Home", Icons.Filled.Home),
    WavyTab(WavyRoutes.SEARCH, "Search", Icons.Filled.Search),
    WavyTab(WavyRoutes.LIBRARY, "Library", Icons.Filled.LibraryMusic),
    WavyTab(WavyRoutes.SETTINGS, "Settings", Icons.Filled.Settings)
)

@Composable
fun WavyMusicApp() {
    WavyTheme {
        val navController = rememberNavController()
        val playbackViewModel: PlaybackViewModel = viewModel()
        val playbackState by playbackViewModel.state.collectAsStateWithLifecycle()

        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route ?: WavyRoutes.HOME

        val navigateTo: (String) -> Unit = { route ->
            navController.navigate(route) {
                launchSingleTop = true
            }
        }

        Scaffold(
            containerColor = WavyColors.Background,
            topBar = {
                WavyTopBar {
                    navigateTo(WavyRoutes.SETTINGS)
                }
            },
            bottomBar = {
                Column {
                    MiniPlayerBar(
                        state = playbackState,
                        onOpenPlayer = {
                            navigateTo(WavyRoutes.NOW_PLAYING)
                        },
                        onTogglePlayback = playbackViewModel::togglePlayback
                    )

                    NavigationBar(
                        containerColor = WavyColors.Surface,
                        contentColor = WavyColors.SecondaryText
                    ) {
                        bottomTabs.forEach { tab ->
                            NavigationBarItem(
                                selected = currentRoute == tab.route,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(
                                            navController.graph
                                                .findStartDestination().id
                                        ) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(tab.icon, contentDescription = null)
                                },
                                label = {
                                    Text(tab.label)
                                }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            WavyNavHost(
                navController = navController,
                modifier = Modifier.padding(innerPadding),
                onNavigate = navigateTo,
                playbackViewModel = playbackViewModel,
                playbackState = playbackState
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WavyTopBar(onOpenSettings: () -> Unit) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WavyWaveMark(
                    modifier = Modifier.size(width = 42.dp, height = 32.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        "Wavy Music",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        "Feel every frequency.",
                        color = WavyColors.SecondaryText,
                        fontSize = 11.sp
                    )
                }
            }
        },
        actions = {
            androidx.compose.material3.IconButton(
                onClick = onOpenSettings
            ) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = "Open settings",
                    tint = WavyColors.SecondaryText
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = WavyColors.Background,
            titleContentColor = WavyColors.PrimaryText
        )
    )
}

@Composable
private fun WavyNavHost(
    navController: NavHostController,
    modifier: Modifier,
    onNavigate: (String) -> Unit,
    playbackViewModel: PlaybackViewModel,
    playbackState: com.wavymusic.app.playback.PlaybackUiState
) {
    NavHost(
        navController = navController,
        startDestination = WavyRoutes.HOME,
        modifier = modifier.fillMaxSize()
    ) {
        composable(WavyRoutes.HOME) {
            val vm: HomeViewModel = viewModel()
            val state by vm.contentState.collectAsStateWithLifecycle()
            HomeScreen(state, onNavigate)
        }

        composable(WavyRoutes.SEARCH) {
            val dataSource = CatalogGraph.searchDataSource(LocalContext.current)

            val vm: SearchViewModel = viewModel(
                factory = SearchViewModelFactory(dataSource)
            )

            val state by vm.uiState.collectAsStateWithLifecycle()

            CatalogSearchScreen(
                state = state,
                onQueryChanged = vm::onQueryChanged,
                onSearchTypeChanged = vm::onSearchTypeChanged,
                onRecentSearchSelected = vm::onRecentSearchSelected,
                onClearRecentSearches = vm::clearRecentSearches,
                onLoadMore = vm::loadMore,
                onOpenResult = { hit ->
                    val route = when (hit.itemType) {
                        com.wavymusic.app.catalog.CatalogSearchItemType.ARTIST ->
                            WavyRoutes.ARTIST

                        com.wavymusic.app.catalog.CatalogSearchItemType.ALBUM ->
                            WavyRoutes.ALBUM

                        else -> null
                    }

                    if (route != null) {
                        navController.currentBackStackEntry
                            ?.savedStateHandle
                            ?.apply {
                                set("catalog.detail.title", hit.title)
                                set("catalog.detail.id", hit.itemId)
                                set("catalog.detail.provider", hit.providerId)
                            }

                        onNavigate(route)
                    }
                }
            )
        }

        composable(WavyRoutes.LIBRARY) {
            val vm: LibraryViewModel = viewModel()
            val state by vm.contentState.collectAsStateWithLifecycle()
            LibraryScreen(state) {
                onNavigate(WavyRoutes.DOWNLOADS)
            }
        }

        composable(WavyRoutes.SETTINGS) {
            SettingsScreen {
                onNavigate(WavyRoutes.DOWNLOADS)
            }
        }

        composable(WavyRoutes.NOW_PLAYING) {
            NowPlayingScreen(
                state = playbackState,
                onPlayTestTrack = playbackViewModel::playDevelopmentTestTrack,
                onTogglePlayback = playbackViewModel::togglePlayback,
                onSeekTo = playbackViewModel::seekTo,
                onSkipNext = playbackViewModel::skipNext,
                onSkipPrevious = playbackViewModel::skipPrevious,
                onToggleShuffle = playbackViewModel::toggleShuffle,
                onCycleRepeat = playbackViewModel::cycleRepeatMode,
                onSetSpeed = playbackViewModel::setPlaybackSpeed,
                onOpenQueue = {
                    onNavigate(WavyRoutes.QUEUE)
                }
            )
        }

        composable(WavyRoutes.QUEUE) {
            QueueScreen(
                state = playbackState,
                onMoveQueueItem = playbackViewModel::moveQueueItem
            )
        }

        composable(WavyRoutes.LYRICS) {
            DestinationEmptyScreen(
                icon = Icons.Filled.MusicNote,
                title = "Lyrics are waiting",
                description = "Lyrics require a real active track and a supported source."
            )
        }

        composable(WavyRoutes.DOWNLOADS) {
            DestinationEmptyScreen(
                icon = Icons.Filled.Download,
                title = "No downloads yet",
                description = "Authorized offline downloads are not implemented yet."
            )
        }

        composable(WavyRoutes.ARTIST) {
            val selected = navController.previousBackStackEntry
                ?.takeIf { it.destination.route == WavyRoutes.SEARCH }
                ?.savedStateHandle

            val title = selected?.get<String>("catalog.detail.title")
            val itemId = selected?.get<String>("catalog.detail.id")

            val context = LocalContext.current
            val detailRepository = remember(context.applicationContext) {
                CatalogDetailRepository(
                    WavyCatalogDatabase
                        .getInstance(context.applicationContext)
                        .catalogDao()
                )
            }

            val vm: CatalogDetailViewModel = viewModel(
                key = "catalog-artist:${itemId ?: "missing"}",
                factory = CatalogDetailViewModelFactory(
                    repository = detailRepository,
                    kind = CatalogDetailKind.ARTIST,
                    catalogItemId = itemId
                )
            )

            val state by vm.uiState.collectAsStateWithLifecycle()

            CatalogDetailScreen(
                state = state,
                fallbackTitle = title,
                onBack = { navController.popBackStack() }
            )
        }

        composable(WavyRoutes.ALBUM) {
            val selected = navController.previousBackStackEntry
                ?.takeIf { it.destination.route == WavyRoutes.SEARCH }
                ?.savedStateHandle

            val title = selected?.get<String>("catalog.detail.title")
            val itemId = selected?.get<String>("catalog.detail.id")

            val context = LocalContext.current
            val detailRepository = remember(context.applicationContext) {
                CatalogDetailRepository(
                    WavyCatalogDatabase
                        .getInstance(context.applicationContext)
                        .catalogDao()
                )
            }

            val vm: CatalogDetailViewModel = viewModel(
                key = "catalog-album:${itemId ?: "missing"}",
                factory = CatalogDetailViewModelFactory(
                    repository = detailRepository,
                    kind = CatalogDetailKind.ALBUM,
                    catalogItemId = itemId
                )
            )

            val state by vm.uiState.collectAsStateWithLifecycle()

            CatalogDetailScreen(
                state = state,
                fallbackTitle = title,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

@Composable
private fun HomeScreen(
    state: CatalogContentState,
    onNavigate: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            WavyColors.SurfaceElevated,
                            WavyColors.Surface,
                            Color(0xFF292344)
                        )
                    )
                )
                .padding(22.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                WavyWaveMark(
                    modifier = Modifier.size(width = 66.dp, height = 46.dp)
                )
                Text(
                    "Find your next\nfavorite frequency.",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    "Feel every frequency.",
                    color = WavyColors.SecondaryText
                )
                Button(
                    onClick = { onNavigate(WavyRoutes.SEARCH) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WavyColors.Cyan,
                        contentColor = WavyColors.Background
                    ),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Explore music")
                }
            }
        }

        SectionHeading(
            "Discover",
            "Catalogue results will appear when a music source is connected."
        )
        CatalogStatePanel(state)

        SectionHeading("Your space", "Jump into part of Wavy.")

        listOf(
            Triple("Now Playing", "Playback controls", WavyRoutes.NOW_PLAYING),
            Triple("Queue", "Upcoming tracks", WavyRoutes.QUEUE),
            Triple("Lyrics", "Lyrics for a real track", WavyRoutes.LYRICS),
            Triple("Downloads", "Authorized offline files", WavyRoutes.DOWNLOADS),
            Triple("Artists", "Artist details", WavyRoutes.ARTIST),
            Triple("Albums", "Album details", WavyRoutes.ALBUM)
        ).chunked(2).forEach { pair ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                pair.forEach { item ->
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 96.dp)
                            .clickable { onNavigate(item.third) },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = WavyColors.Surface
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(item.first, fontWeight = FontWeight.SemiBold)
                            Text(
                                item.second,
                                color = WavyColors.SecondaryText,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryScreen(
    state: CatalogContentState,
    onOpenDownloads: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        SectionHeading(
            "Your library",
            "Music that belongs in your collection."
        )
        CatalogStatePanel(state)

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenDownloads() },
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = WavyColors.Surface
            )
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Download,
                    contentDescription = null,
                    tint = WavyColors.Cyan
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Downloads", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Authorized offline files",
                        color = WavyColors.SecondaryText
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(onOpenDownloads: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        SectionHeading(
            "Settings",
            "Your listening experience, your way."
        )
        SettingsInfoCard(
            "Appearance",
            "Wavy Midnight",
            "The native dark theme is active."
        )
        SettingsInfoCard(
            "Motion",
            "Minimal by design",
            "No decorative autoplay or continuous playback animations."
        )
        SettingsInfoCard(
            "Music sources",
            "Not connected",
            "No credentials or provider tokens are stored."
        )
        SettingsInfoCard(
            "Playback",
            "Media3 session",
            "Playback is controlled by the shared background session."
        )
        Button(
            onClick = onOpenDownloads,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
        ) {
            Icon(Icons.Filled.Download, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("View downloads")
        }
    }
}

@Composable
private fun DestinationEmptyScreen(
    icon: ImageVector,
    title: String,
    description: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = WavyColors.Cyan,
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            description,
            color = WavyColors.SecondaryText,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun CatalogStatePanel(
    state: CatalogContentState,
    descriptionOverride: String? = null
) {
    val (title, description, icon) = when (state) {
        CatalogContentState.Loading -> Triple(
            "Loading music",
            "Waiting for the catalogue source.",
            Icons.Filled.MusicNote
        )
        CatalogContentState.NotConfigured -> Triple(
            "No music source connected",
            "Connect an authorized source to display real catalogue data.",
            Icons.Filled.FolderOpen
        )
        CatalogContentState.Empty -> Triple(
            "Nothing here yet",
            "There are no items to display right now.",
            Icons.Filled.FolderOpen
        )
        CatalogContentState.Offline -> Triple(
            "You're offline",
            "Check your connection and try again when online.",
            Icons.Filled.Download
        )
        is CatalogContentState.Error -> Triple(
            "Something went wrong",
            state.message,
            Icons.Filled.FolderOpen
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = WavyColors.Surface
        )
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, contentDescription = null, tint = WavyColors.Cyan)
            Text(title, fontWeight = FontWeight.Bold)
            Text(
                descriptionOverride ?: description,
                color = WavyColors.SecondaryText
            )
        }
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            subtitle,
            color = WavyColors.SecondaryText,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun SettingsInfoCard(
    title: String,
    value: String,
    description: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = WavyColors.Surface
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(value, color = WavyColors.Cyan)
            Text(description, color = WavyColors.SecondaryText)
        }
    }
}