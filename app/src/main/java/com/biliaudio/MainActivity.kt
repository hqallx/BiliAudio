package com.biliaudio

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.biliaudio.player.PlaybackService
import com.biliaudio.ui.components.MiniPlayer
import com.biliaudio.ui.components.PlayerScreen
import com.biliaudio.ui.screens.LibraryScreen
import com.biliaudio.ui.screens.LoginScreen
import com.biliaudio.ui.screens.PlaylistScreen
import com.biliaudio.ui.screens.ProfileScreen
import com.biliaudio.ui.screens.VideoListScreen
import com.biliaudio.ui.screens.VideoListSource
import com.biliaudio.ui.theme.BiliAudioTheme
import com.biliaudio.ui.viewmodel.AuthViewModel
import com.biliaudio.ui.viewmodel.FavoriteViewModel
import com.biliaudio.ui.viewmodel.PlayerViewModel
import com.biliaudio.util.NetworkMonitor
import com.biliaudio.util.NetworkStatus
import androidx.media3.session.SessionToken
import android.content.ComponentName
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            BiliAudioTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppRoot()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(
    playerViewModel: PlayerViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel(),
    favoriteViewModel: FavoriteViewModel = hiltViewModel()
) {
    val navController = rememberNavController()

    val isLoggedIn by authViewModel.isLoggedIn.collectAsStateWithLifecycle()
    val currentTrack by playerViewModel.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by playerViewModel.isPlaying.collectAsStateWithLifecycle()
    val currentPosition by playerViewModel.currentPosition.collectAsStateWithLifecycle()
    val duration by playerViewModel.duration.collectAsStateWithLifecycle()
    val repeatMode by playerViewModel.repeatMode.collectAsStateWithLifecycle()
    val isShuffle by playerViewModel.isShuffle.collectAsStateWithLifecycle()
    val playbackSpeed by playerViewModel.playbackSpeed.collectAsStateWithLifecycle()
    val sleepTimerMinutes by playerViewModel.sleepTimerMinutes.collectAsStateWithLifecycle()
    val isPlayerLoading by playerViewModel.isLoading.collectAsStateWithLifecycle()
    val playerError by playerViewModel.playbackError.collectAsStateWithLifecycle()
    val playlist by playerViewModel.playlist.collectAsStateWithLifecycle()
    val currentIndex by playerViewModel.currentIndex.collectAsStateWithLifecycle()

    val toast by authViewModel.toast.collectAsStateWithLifecycle()
    val favToast by favoriteViewModel.toast.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    var showPlayer by remember { mutableStateOf(false) }

    // 连接到播放服务 & 网络状态监听
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    LaunchedEffect(Unit) {
        val sessionToken = SessionToken(
            appContext,
            ComponentName(appContext, PlaybackService::class.java)
        )
        playerViewModel.playbackManager.connectController(sessionToken)
        playerViewModel.startProgressUpdate()

        NetworkMonitor.observe(appContext).collect { status ->
            if (status == NetworkStatus.LOST || status == NetworkStatus.UNAVAILABLE) {
                snackbarHostState.showSnackbar("网络不可用")
            }
        }
    }

    // Android 13+ 运行时请求通知权限，否则前台播放服务通知无法显示
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* 用户授予与否都不阻断，仅在未授予时无通知 */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // 全屏播放器打开时拦截返回键：先收起播放器，而不是直接导航退页
    BackHandler(enabled = showPlayer) {
        showPlayer = false
    }

    // 显示 Toast
    LaunchedEffect(toast) {
        toast?.let {
            snackbarHostState.showSnackbar(it)
            authViewModel.consumeToast()
        }
    }
    LaunchedEffect(favToast) {
        favToast?.let {
            snackbarHostState.showSnackbar(it)
            favoriteViewModel.consumeToast()
        }
    }

    val items = listOf(
        BottomNavItem("library", "库", Icons.Default.LibraryBooks),
        BottomNavItem("playlist", "播放列表", Icons.Default.QueueMusic),
        BottomNavItem("profile", "我的", Icons.Default.Person)
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    // 底部导航栏仅在主 Tab（库/播放列表/我的）层级显示，二级界面与登录页不显示。
    val showBottomBar = currentDestination?.hierarchy?.any { destination ->
        items.any { it.route == destination.route }
    } == true
    // 微缩播放器与底部导航栏解耦：只要有当前曲目且不在登录页就显示，
    // 这样进入二级界面（收藏夹/合集视频列表）时底部仍保留微缩播放器，
    // 用户可随时查看/控制播放、点击展开全屏播放器。
    val isOnLogin = currentDestination?.route == "login"
    val showMiniPlayer = currentTrack != null && !isOnLogin

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                if (showMiniPlayer) {
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
                startDestination = if (isLoggedIn) "library" else "login"
            ) {
                composable("login") {
                    LoginScreen(
                        authViewModel = authViewModel,
                        onLoginSuccess = {
                            navController.navigate("library") {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    )
                }

                composable("library") {
                    LibraryScreen(
                        favoriteViewModel = favoriteViewModel,
                        authViewModel = authViewModel,
                        playerViewModel = playerViewModel,
                        onFolderClick = { folderId, folderName ->
                            navController.navigate("videos/$folderId/$folderName")
                        },
                        onSeasonClick = { seasonId, seasonName, isSeries ->
                            navController.navigate("season/$seasonId/$seasonName/$isSeries")
                        },
                        onLoginClick = {
                            navController.navigate("login")
                        }
                    )
                }

                composable("videos/{folderId}/{folderName}") { backStackEntry ->
                    val folderId = backStackEntry.arguments?.getString("folderId")?.toLong() ?: 0L
                    val folderName = backStackEntry.arguments?.getString("folderName") ?: ""
                    VideoListScreen(
                        folderId = folderId,
                        folderName = folderName,
                        source = VideoListSource.FAVORITE,
                        favoriteViewModel = favoriteViewModel,
                        playerViewModel = playerViewModel,
                        onBackClick = { navController.popBackStack() }
                    )
                }

                composable("season/{seasonId}/{seasonName}/{isSeries}") { backStackEntry ->
                    val seasonId = backStackEntry.arguments?.getString("seasonId")?.toLong() ?: 0L
                    val seasonName = backStackEntry.arguments?.getString("seasonName") ?: ""
                    val isSeries = backStackEntry.arguments?.getString("isSeries")?.toBoolean() ?: false
                    VideoListScreen(
                        folderId = seasonId,
                        folderName = seasonName,
                        source = VideoListSource.SEASON,
                        isSeries = isSeries,
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
            isShuffle = isShuffle,
            playbackSpeed = playbackSpeed,
            sleepTimerMinutes = sleepTimerMinutes,
            isLoading = isPlayerLoading,
            playbackError = playerError,
            playlist = playlist,
            currentIndex = currentIndex,
            onPlayPause = { playerViewModel.togglePlayPause() },
            onSeek = { position -> playerViewModel.seekTo(position) },
            onNext = { playerViewModel.next() },
            onPrevious = { playerViewModel.previous() },
            onToggleRepeat = { playerViewModel.toggleRepeatMode() },
            onToggleShuffle = { playerViewModel.toggleShuffle() },
            onSetPlaybackSpeed = { speed -> playerViewModel.setPlaybackSpeed(speed) },
            onStartSleepTimer = { minutes -> playerViewModel.startSleepTimer(minutes) },
            onCancelSleepTimer = { playerViewModel.cancelSleepTimer() },
            onPlayAt = { index -> playerViewModel.playAt(index) },
            onRemoveFromPlaylist = { index -> playerViewModel.removeFromPlaylist(index) },
            onClearPlaylist = { playerViewModel.clearPlaylist() },
            onRetry = { playerViewModel.retry() },
            onDismiss = { showPlayer = false }
        )
    }
}

data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)
