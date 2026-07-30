package com.biliaudio.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.biliaudio.data.model.UserInfo
import com.biliaudio.ui.viewmodel.AuthViewModel
import com.biliaudio.ui.viewmodel.SettingsViewModel

/**
 * 设置页 Tab 分类。
 *  - 账号：用户信息 + 退出登录/去登录
 *  - 播放：音质偏好 + 缓存清理
 *  - 关于：版本信息
 */
private enum class SettingsTab(val label: String) {
    Account("账号"),
    Playback("播放"),
    About("关于")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    authViewModel: AuthViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    onLogout: () -> Unit = {}
) {
    val userInfo by authViewModel.userInfo.collectAsState()
    val isLoggedIn by authViewModel.isLoggedIn.collectAsState()
    val audioQuality by settingsViewModel.audioQuality.collectAsState()
    val settingsToast by settingsViewModel.toast.collectAsState()
    var selectedTab by remember { mutableStateOf(SettingsTab.Account) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }

    // 设置操作 toast 用 Snackbar 展示
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(settingsToast) {
        settingsToast?.let {
            snackbarHostState.showSnackbar(it)
            settingsViewModel.consumeToast()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "设置",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            PrimaryTabRow(
                selectedTabIndex = selectedTab.ordinal,
                modifier = Modifier.fillMaxWidth()
            ) {
                SettingsTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(tab.label) }
                    )
                }
            }

            when (selectedTab) {
                SettingsTab.Account -> AccountTab(
                    userInfo = userInfo,
                    isLoggedIn = isLoggedIn,
                    onLogoutClick = { showLogoutDialog = true },
                    onLoginClick = onLogout
                )
                SettingsTab.Playback -> PlaybackTab(
                    audioQuality = audioQuality,
                    qualityOptions = settingsViewModel.audioQualityOptions,
                    onQualityClick = { showQualityDialog = true },
                    onClearCache = { settingsViewModel.clearCache() }
                )
                SettingsTab.About -> AboutTab()
            }
        }

        if (showLogoutDialog) {
            AlertDialog(
                onDismissRequest = { showLogoutDialog = false },
                title = { Text("退出登录") },
                text = { Text("确定要退出登录吗？本地登录状态将被清除。") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showLogoutDialog = false
                            authViewModel.logout()
                            onLogout()
                        }
                    ) {
                        Text("确定")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLogoutDialog = false }) {
                        Text("取消")
                    }
                }
            )
        }

        // 音质偏好选择对话框
        if (showQualityDialog) {
            AlertDialog(
                onDismissRequest = { showQualityDialog = false },
                title = { Text("音质偏好") },
                text = {
                    Column {
                        settingsViewModel.audioQualityOptions.forEach { (id, name) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                                    .clickable {
                                        settingsViewModel.setAudioQuality(id)
                                        showQualityDialog = false
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = id == audioQuality || (audioQuality == 0 && id == 30280),
                                    onClick = {
                                        settingsViewModel.setAudioQuality(id)
                                        showQualityDialog = false
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showQualityDialog = false }) {
                        Text("关闭")
                    }
                }
            )
        }
    }
}

@Composable
private fun AccountTab(
    userInfo: UserInfo?,
    isLoggedIn: Boolean,
    onLogoutClick: () -> Unit,
    onLoginClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (userInfo?.face?.isNotEmpty() == true) {
                    AsyncImage(
                        model = userInfo.face,
                        contentDescription = null,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = if (isLoggedIn) (userInfo?.name ?: "已登录") else "未登录",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    if (isLoggedIn) {
                        val sign = userInfo?.sign?.trim() ?: ""
                        Text(
                            text = sign.ifEmpty { "暂无简介" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            maxLines = 2
                        )
                    } else {
                        Text(
                            text = "登录后可同步B站收藏夹",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            if (isLoggedIn) {
                SettingsItem(
                    icon = Icons.Default.ExitToApp,
                    title = "退出登录",
                    subtitle = "清除本地登录状态",
                    onClick = onLogoutClick
                )
            } else {
                SettingsItem(
                    icon = Icons.Default.ExitToApp,
                    title = "去登录",
                    subtitle = "登录B站账号以同步收藏夹",
                    onClick = onLoginClick
                )
            }
        }
    }
}

@Composable
private fun PlaybackTab(
    audioQuality: Int,
    qualityOptions: List<Pair<Int, String>>,
    onQualityClick: () -> Unit,
    onClearCache: () -> Unit
) {
    val qualityName = qualityOptions.firstOrNull { it.first == audioQuality }?.second
        ?: "192K（推荐）"
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "播放",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column {
                SettingsItem(
                    icon = Icons.Default.GraphicEq,
                    title = "音质偏好",
                    subtitle = qualityName,
                    onClick = onQualityClick
                )
                SettingsItem(
                    icon = Icons.Default.CleaningServices,
                    title = "清理缓存",
                    subtitle = "清除已缓存的音频地址",
                    onClick = onClearCache
                )
            }
        }
    }
}

@Composable
private fun AboutTab() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "关于",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            SettingsItem(
                icon = Icons.Default.Info,
                title = "版本",
                subtitle = "BiliAudio v1.0"
            )
        }
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String = "",
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
