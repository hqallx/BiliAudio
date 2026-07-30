package com.biliaudio.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.biliaudio.data.model.UserInfo
import com.biliaudio.ui.viewmodel.AuthViewModel
import com.biliaudio.ui.viewmodel.SettingsViewModel

/** 浅蓝灰底色，与截图风格一致 */
private val ScreenBg = Color(0xFFF1F3F8)
private val GroupBg = Color(0xFFFFFFFF)

/**
 * 设置页（截图风格）。
 * - 浅蓝灰背景，白底分组卡片
 * - 单条列表项：左图标 + 标题 + 右 chevron；无副标题、无红点
 * - 退出登录/切换账号 使用纯文字按钮
 */
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
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(settingsToast) {
        settingsToast?.let {
            snackbarHostState.showSnackbar(it)
            settingsViewModel.consumeToast()
        }
    }

    Scaffold(
        containerColor = ScreenBg,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = ScreenBg
                ),
                navigationIcon = {
                    IconButton(onClick = { /* 由导航回退 */ }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "返回",
                            tint = Color(0xFF1F1F1F)
                        )
                    }
                },
                title = {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "设置",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color(0xFF1F1F1F)
                        )
                    }
                },
                actions = { Spacer(modifier = Modifier.width(48.dp)) }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ScreenBg)
                .padding(paddingValues)
                .padding(horizontal = 12.dp)
        ) {
            // 账号与安全
            SettingsGroup {
                AccountRow(
                    isLoggedIn = isLoggedIn,
                    userInfo = userInfo,
                    onClick = {
                        if (isLoggedIn) showLogoutDialog = true else onLogout()
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 播放与下载
            SettingsGroup {
                SettingsItemRow(
                    icon = Icons.Default.GraphicEq,
                    title = "音质",
                    onClick = { showQualityDialog = true }
                )
                Divider()
                SettingsItemRow(
                    icon = Icons.Default.CleaningServices,
                    title = "清理缓存",
                    onClick = { settingsViewModel.clearCache() }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 关于
            SettingsGroup {
                SettingsItemRow(
                    icon = Icons.Default.Info,
                    title = "关于",
                    onClick = { /* 关于页待实现 */ }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 底部退出按钮
            if (isLoggedIn) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                        .background(GroupBg)
                        .clickable { showLogoutDialog = true }
                        .padding(vertical = 18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "退出登录/关闭",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFF1F1F1F)
                    )
                }
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
                                androidx.compose.material3.RadioButton(
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
private fun SettingsGroup(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .background(GroupBg)
    ) {
        content()
    }
}

@Composable
private fun SettingsItemRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFF1F1F1F),
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = Color(0xFF1F1F1F),
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Color(0xFFBFBFBF),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun AccountRow(
    isLoggedIn: Boolean,
    userInfo: UserInfo?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isLoggedIn && userInfo?.face?.isNotEmpty() == true) {
            AsyncImage(
                model = userInfo.face,
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE6E8EE)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = Color(0xFF9097A3),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = if (isLoggedIn) (userInfo?.name ?: "已登录") else "账号与安全",
            style = MaterialTheme.typography.bodyLarge,
            color = Color(0xFF1F1F1F),
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Color(0xFFBFBFBF),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun Divider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(Color(0xFFEEEEF2))
    )
}
