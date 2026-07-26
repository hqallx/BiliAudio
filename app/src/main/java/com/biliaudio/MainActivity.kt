package com.biliaudio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.biliaudio.player.PlaybackService
import com.biliaudio.ui.components.MiniPlayer
import com.biliaudio.ui.components.PlayerScreen
import com.biliaudio.ui.screens.FavoriteScreen
import com.biliaudio.ui.screens.LoginScreen
import com.biliaudio.ui.screens.PlaylistScreen
import com.biliaudio.ui.screens.ProfileScreen
import com.biliaudio.ui.screens.VideoListScreen
import com.biliaudio.ui.theme.BiliAudioTheme
import com.biliaudio.ui.viewmodel.AuthViewModel
import com.biliaudio.ui.viewmodel.FavoriteViewModel
import com.biliaudio.ui.viewmodel.PlayerViewModel
import androidx.media3.session.SessionToken
import android.content.ComponentName
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect

class MainActivity : ComponentActivity() {

    private val playerViewModel: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val sessionToken = SessionToken(
            this,
            ComponentName(this, PlaybackService::class.java)
        )
        playerViewModel.playbackManager.connectController(sessionToken)

        setContent {
            BiliAudioTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BiliAudioApp(playerViewModel)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        playerViewModel.playbackManager.releaseController()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BiliAudioApp(playerViewModel: PlayerViewModel) {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = viewModel()
    val favoriteViewModel: FavoriteViewModel = viewModel()

    val isLoggedIn by authViewModel.isLoggedIn.collectAsState()
    val currentTrack by playerViewModel.currentTrack.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()
    val currentPosition by playerViewModel.currentPosition.collectAsState()
    val duration by playerViewModel.duration.collectAsState()
    val repeatMode by playerViewModel.repeatMode.collectAsState()

    var showPlayer by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        playerViewModel.startProgressUpdate()
    }

    val items = listOf(
        BottomNavItem("favorite", "收藏", Icons.Default.Favorite),
        BottomNavItem("playlist", "播放列表", Icons.Default.QueueMusic),
        BottomNavItem("profile", "我的", Icons.Default.Person)
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val showBottomBar = currentDestination?.hierarchy?.any { destination ->
        items.any { it.route == destination.route }
    } == true && isLoggedIn

    Scaffold(
        bottomBar = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                if (currentTrack != null && showBottomBar) {
                    MiniPlayer(
                        track = currentTrack,
                        isPlaying = isPlaying,
                        onPlayPause = { playerViewModel.togglePlayPause() },
                        onExpand = { showPlayer = true }
                    )
                }
                if (showBottomBar) {
                    NavigationBar(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items.forEach { item ->
                            val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = item.label
                                    )
                                },
                                label = { Text(item.label) }
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            NavHost(
                navController = navController,
                startDestination = if (isLoggedIn) "favorite" else "login"
            ) {
                composable("login") {
                    LoginScreen(
                        authViewModel = authViewModel,
                        onLoginSuccess = {
                            navController.navigate("favorite") {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    )
                }

                composable("favorite") {
                    FavoriteScreen(
                        favoriteViewModel = favoriteViewModel,
                        authViewModel = authViewModel,
                        onFolderClick = { folderId, folderName ->
                            navController.navigate("videos/$folderId/$folderName")
                        }
                    )
                }

                composable("videos/{folderId}/{folderName}") { backStackEntry ->
                    val folderId = backStackEntry.arguments?.getString("folderId")?.toLong() ?: 0L
                    val folderName = backStackEntry.arguments?.getString("folderName") ?: ""
                    VideoListScreen(
                        folderId = folderId,
                        folderName = folderName,
                        favoriteViewModel = favoriteViewModel,
                        playerViewModel = playerViewModel,
                        onBackClick = { navController.popBackStack() }
                    )
                }

                composable("playlist") {
                    PlaylistScreen(playerViewModel = playerViewModel)
                }

                composable("profile") {
                    ProfileScreen(
                        authViewModel = authViewModel,
                        onLogout = {
                            navController.navigate("login") {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    )
                }
            }
        }
    }

    if (showPlayer && currentTrack != null) {
        PlayerScreen(
            track = currentTrack,
            isPlaying = isPlaying,
            currentPosition = currentPosition,
            duration = duration,
            repeatMode = repeatMode,
            onPlayPause = { playerViewModel.togglePlayPause() },
            onSeek = { position -> playerViewModel.seekTo(position) },
            onNext = { playerViewModel.next() },
            onPrevious = { playerViewModel.previous() },
            onToggleRepeat = { playerViewModel.toggleRepeatMode() },
            onDismiss = { showPlayer = false }
        )
    }
}

data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)
