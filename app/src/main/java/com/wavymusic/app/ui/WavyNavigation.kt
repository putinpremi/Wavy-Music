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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
import com.wavymusic.app.ui.theme.WavyColors
import com.wavymusic.app.ui.theme.WavyTheme
import com.wavymusic.app.ui.theme.WavyWaveMark

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
        HOME,
        SEARCH,
        LIBRARY,
        SETTINGS,
        NOW_PLAYING,
        QUEUE,
        LYRICS,
        DOWNLOADS,
        ARTIST,
        ALBUM
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

        val backStackEntry by
            navController.currentBackStackEntryAsState()

        val currentRoute =
            backStackEntry?.destination?.route ?: WavyRoutes.HOME

        val navigateTo: (String) -> Unit = { route ->
            navController.navigate(route) {
                launchSingleTop = true
            }
        }

        /*
         * There is no playback service yet, so no mini-player is
         * displayed. This empty state is intentional, not fake media.
         */
        val miniPlayerState = MiniPlayerUiState()

        Scaffold(
            containerColor = WavyColors.Background,
            topBar = {
                WavyTopBar(
                    onOpenSettings = {
                        navigateTo(WavyRoutes.SETTINGS)
                    }
                )
            },
            bottomBar = {
                Column {
                    if (miniPlayerState.isVisible) {
                        MiniPlayerBar(
                            state = miniPlayerState,
                            onOpenPlayer = {
                                navigateTo(WavyRoutes.NOW_PLAYING)
                            }
                        )
                    }

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
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = null
                                    )
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
                onNavigate = navigateTo
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WavyTopBar(
    onOpenSettings: () -> Unit
) {
    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                WavyWaveMark(
                    modifier = Modifier.size(
                        width = 42.dp,
                        height = 32.dp
                    )
                )

                Spacer(Modifier.width(10.dp))

                Column {
                    Text(
                        text = "Wavy Music",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = WavyColors.PrimaryText
                    )

                    Text(
                        text = "Feel every frequency.",
                        fontSize = 11.sp,
                        color = WavyColors.SecondaryText
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = Icons.Filled.Settings,
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
    onNavigate: (String) -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = WavyRoutes.HOME,
        modifier = modifier.fillMaxSize()
    ) {
        composable(WavyRoutes.HOME) {
            val homeViewModel: HomeViewModel = viewModel()
            val state by homeViewModel.contentState
                .collectAsStateWithLifecycle()

            HomeScreen(
                state = state,
                onNavigate = onNavigate
            )
        }

        composable(WavyRoutes.SEARCH) {
            val searchViewModel: SearchViewModel = viewModel()
            val state by searchViewModel.uiState
                .collectAsStateWithLifecycle()

            SearchScreen(
                state = state,
                onQueryChanged = searchViewModel::onQueryChanged
            )
        }

        composable(WavyRoutes.LIBRARY) {
            val libraryViewModel: LibraryViewModel = viewModel()
            val state by libraryViewModel.contentState
                .collectAsStateWithLifecycle()

            LibraryScreen(
                state = state,
                onOpenDownloads = {
                    onNavigate(WavyRoutes.DOWNLOADS)
                }
            )
        }

        composable(WavyRoutes.SETTINGS) {
            SettingsScreen(
                onOpenDownloads = {
                    onNavigate(WavyRoutes.DOWNLOADS)
                }
            )
        }

        composable(WavyRoutes.NOW_PLAYING) {
            DestinationEmptyScreen(
                icon = Icons.Filled.PlayArrow,
                title = "Nothing is playing",
                description =
                    "Playback is not connected yet. Wavy will not start " +
                    "audio automatically or display a fake track."
            )
        }

        composable(WavyRoutes.QUEUE) {
            DestinationEmptyScreen(
                icon = Icons.Filled.QueueMusic,
                title = "Your queue is empty",
                description =
                    "The queue will reflect the real playback session " +
                    "when the playback service is implemented."
            )
        }

        composable(WavyRoutes.LYRICS) {
            DestinationEmptyScreen(
                icon = Icons.Filled.MusicNote,
                title = "Lyrics are waiting",
                description =
                    "Lyrics will be available when a supported source " +
                    "and a real active track are available."
            )
        }

        composable(WavyRoutes.DOWNLOADS) {
            DestinationEmptyScreen(
                icon = Icons.Filled.Download,
                title = "No downloads yet",
                description =
                    "Authorized offline downloads are not implemented. " +
                    "No placeholder files or fake downloads are shown."
            )
        }

        composable(WavyRoutes.ARTIST) {
            DestinationEmptyScreen(
                icon = Icons.Filled.Person,
                title = "Artist details",
                description =
                    "Connect an authorized music catalogue to open " +
                    "real artist details."
            )
        }

        composable(WavyRoutes.ALBUM) {
            DestinationEmptyScreen(
                icon = Icons.Filled.Album,
                title = "Album details",
                description =
                    "Album information will come from real catalogue " +
                    "results. No sample album is being displayed."
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
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            WavyColors.SurfaceElevated,
                            WavyColors.Surface,
                            Color(0xFF292344)
                        )
                    )
                )
                .padding(22.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    WavyWaveMark(
                        modifier = Modifier.size(
                            width = 44.dp,
                            height = 34.dp
                        )
                    )

                    Spacer(Modifier.width(10.dp))

                    Text(
                        text = "YOUR SOUND, YOUR SPACE",
                        color = WavyColors.Cyan,
                        fontSize = 10.sp,
                        letterSpacing = 1.8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(22.dp))

                Text(
                    text = "Find your next\nfavorite frequency.",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = WavyColors.PrimaryText
                )

                Spacer(Modifier.height(10.dp))

                Text(
                    text = "Music discovery, made personal.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = WavyColors.SecondaryText
                )

                Spacer(Modifier.height(22.dp))

                Button(
                    onClick = {
                        onNavigate(WavyRoutes.SEARCH)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WavyColors.Cyan,
                        contentColor = WavyColors.Background
                    ),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null
                    )

                    Spacer(Modifier.width(8.dp))

                    Text(
                        text = "Explore music",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        SectionHeading(
            title = "Discover",
            subtitle = "Your next listen starts here."
        )

        CatalogStatePanel(state = state)

        SectionHeading(
            title = "Your space",
            subtitle = "Jump into a part of Wavy."
        )

        val shortcuts = listOf(
            QuickAction(
                title = "Now Playing",
                subtitle = "Playback",
                icon = Icons.Filled.PlayArrow,
                route = WavyRoutes.NOW_PLAYING
            ),
            QuickAction(
                title = "Queue",
                subtitle = "Up next",
                icon = Icons.Filled.QueueMusic,
                route = WavyRoutes.QUEUE
            ),
            QuickAction(
                title = "Lyrics",
                subtitle = "Words & meaning",
                icon = Icons.Filled.MusicNote,
                route = WavyRoutes.LYRICS
            ),
            QuickAction(
                title = "Downloads",
                subtitle = "Offline listening",
                icon = Icons.Filled.Download,
                route = WavyRoutes.DOWNLOADS
            ),
            QuickAction(
                title = "Artists",
                subtitle = "Artist details",
                icon = Icons.Filled.Person,
                route = WavyRoutes.ARTIST
            ),
            QuickAction(
                title = "Albums",
                subtitle = "Album details",
                icon = Icons.Filled.Album,
                route = WavyRoutes.ALBUM
            )
        )

        shortcuts.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                pair.forEach { item ->
                    QuickActionCard(
                        item = item,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onNavigate(item.route)
                        }
                    )
                }

                if (pair.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SearchScreen(
    state: SearchUiState,
    onQueryChanged: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        SectionHeading(
            title = "Search",
            subtitle = "Find songs, artists, and albums."
        )

        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChanged,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = {
                Text("Search music")
            },
            placeholder = {
                Text("Songs, artists, albums")
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null
                )
            },
            shape = RoundedCornerShape(18.dp)
        )

        CatalogStatePanel(
            state = state.contentState,
            descriptionOverride = if (state.query.isNotBlank()) {
                "Your search has not been sent anywhere. " +
                    "An authorized music source is not configured yet."
            } else {
                null
            }
        )
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
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        SectionHeading(
            title = "Your library",
            subtitle = "Music that belongs in your collection."
        )

        CatalogStatePanel(state = state)

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = WavyColors.Surface
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        role = Role.Button,
                        onClick = onOpenDownloads
                    )
                    .heightIn(min = 76.dp)
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Download,
                    contentDescription = null,
                    tint = WavyColors.Cyan,
                    modifier = Modifier.size(26.dp)
                )

                Spacer(Modifier.width(14.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Downloads",
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        text = "Your authorized offline files",
                        color = WavyColors.SecondaryText,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Icon(
                    imageVector = Icons.Filled.ArrowForward,
                    contentDescription = null,
                    tint = WavyColors.SecondaryText
                )
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    onOpenDownloads: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        SectionHeading(
            title = "Settings",
            subtitle = "Your listening experience, your way."
        )

        SettingsInfoCard(
            title = "Appearance",
            value = "Wavy Midnight",
            description = "The native dark theme is active."
        )

        SettingsInfoCard(
            title = "Motion",
            value = "Minimal by design",
            description =
                "This foundation uses no decorative autoplay " +
                    "or continuous playback animations."
        )

        SettingsInfoCard(
            title = "Music sources",
            value = "Not connected",
            description =
                "No account credentials or provider tokens are stored."
        )

        SettingsInfoCard(
            title = "Playback",
            value = "Not configured",
            description =
                "Background audio and player controls will be " +
                    "connected to one playback service in a later phase."
        )

        Button(
            onClick = onOpenDownloads,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = WavyColors.SurfaceElevated,
                contentColor = WavyColors.PrimaryText
            )
        ) {
            Icon(
                imageVector = Icons.Filled.Download,
                contentDescription = null
            )

            Spacer(Modifier.width(10.dp))

            Text("View downloads")
        }

        Text(
            text = "Wavy Music · Feel every frequency.",
            color = WavyColors.MutedText,
            style = MaterialTheme.typography.labelMedium
        )
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
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        EmptyStatePanel(
            icon = icon,
            title = title,
            description = description
        )
    }
}

@Composable
private fun CatalogStatePanel(
    state: CatalogContentState,
    modifier: Modifier = Modifier,
    descriptionOverride: String? = null
) {
    when (state) {
        CatalogContentState.Loading -> {
            Card(
                modifier = modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = WavyColors.Surface
                ),
                shape = RoundedCornerShape(22.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = WavyColors.Cyan,
                        strokeWidth = 3.dp
                    )

                    Text(
                        text = "Loading music…",
                        color = WavyColors.SecondaryText
                    )
                }
            }
        }

        CatalogContentState.NotConfigured -> {
            EmptyStatePanel(
                icon = Icons.Filled.MusicNote,
                title = "No music source connected",
                description = descriptionOverride
                    ?: "This section will show real catalogue data " +
                    "when an authorized music source is configured.",
                modifier = modifier
            )
        }

        CatalogContentState.Empty -> {
            EmptyStatePanel(
                icon = Icons.Filled.FolderOpen,
                title = "Nothing here yet",
                description = descriptionOverride
                    ?: "There are no items to display right now.",
                modifier = modifier
            )
        }

        CatalogContentState.Offline -> {
            EmptyStatePanel(
                icon = Icons.Filled.WifiOff,
                title = "You're offline",
                description = descriptionOverride
                    ?: "Check your connection and try again when online.",
                modifier = modifier
            )
        }

        is CatalogContentState.Error -> {
            EmptyStatePanel(
                icon = Icons.Filled.ErrorOutline,
                title = "Something went wrong",
                description = descriptionOverride ?: state.message,
                modifier = modifier
            )
        }
    }
}

@Composable
private fun EmptyStatePanel(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = WavyColors.Surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(WavyColors.SurfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = WavyColors.Cyan,
                    modifier = Modifier.size(26.dp)
                )
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = WavyColors.PrimaryText
            )

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = WavyColors.SecondaryText
            )
        }
    }
}

private data class QuickAction(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val route: String
)

@Composable
private fun QuickActionCard(
    item: QuickAction,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .heightIn(min = 104.dp)
            .clickable(
                role = Role.Button,
                onClick = onClick
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = WavyColors.Surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = WavyColors.Cyan,
                modifier = Modifier.size(24.dp)
            )

            Text(
                text = item.title,
                fontWeight = FontWeight.SemiBold,
                color = WavyColors.PrimaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = item.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = WavyColors.SecondaryText
            )
        }
    }
}

@Composable
private fun SectionHeading(
    title: String,
    subtitle: String
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = WavyColors.PrimaryText,
            modifier = Modifier.semantics {
                heading()
            }
        )

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = WavyColors.SecondaryText
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
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = value,
                color = WavyColors.Cyan,
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = description,
                color = WavyColors.SecondaryText,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun MiniPlayerBar(
    state: MiniPlayerUiState,
    onOpenPlayer: () -> Unit
) {
    if (!state.isVisible) return

    Surface(
        color = WavyColors.SurfaceElevated,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                role = Role.Button,
                onClick = onOpenPlayer
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WavyWaveMark(
                modifier = Modifier.size(
                    width = 38.dp,
                    height = 30.dp
                )
            )

            Spacer(Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = state.title.orEmpty(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = state.artist ?: "Artist unavailable",
                    style = MaterialTheme.typography.bodySmall,
                    color = WavyColors.SecondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = Icons.Filled.ArrowForward,
                contentDescription = "Open Now Playing",
                tint = WavyColors.Cyan
            )
        }
    }
}