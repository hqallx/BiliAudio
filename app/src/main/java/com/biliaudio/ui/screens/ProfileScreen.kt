package com.biliaudio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.biliaudio.data.model.UserInfo
import com.biliaudio.ui.viewmodel.AuthViewModel
import com.biliaudio.ui.theme.AppColors
import com.biliaudio.ui.viewmodel.SettingsViewModel

/** 文字底色与页面背景一致，避免出现白色底 */
private val GroupBg = Color.Transparent

/**
 * 元素整体缩放系数：用户要求缩小到原来的 90%。
 */
private const val SCALE = 0.9f
private fun Int.scaled() = (this * SCALE).dp

/**
 * 设置页（截图风格）。
 * - 浅蓝灰背景，文字无白色底（分组卡片透明，直接落在页面背景上）
 * - 单条列表项：左图标 + 标题 + 右 chevron；无副标题、无红点
 * - 退出登录/切换账号 使用纯文字按钮
 * - 各元素尺寸为原来的 90%
 * - 状态栏颜色与页面背景一致
 * - 提供调试模式开关与日志查看
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
    val debugEnabled by settingsViewModel.debugEnabled.collectAsState()
    val settingsToast by settingsViewModel.toast.collectAsState()
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var showDebugLog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(settingsToast) {
        settingsToast?.let {
            snackbarHostState.showSnackbar(it)
            settingsViewModel.consumeToast()
        }
    }

    // 状态栏沿用全局 enableEdgeToEdge() 的透明策略，与其他页面保持一致，
    // 不在此处单独修改 statusBarColor，避免页面切换时颜色闪烁。

    Scaffold(
        containerColor = AppColors.ScreenBg,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppColors.ScreenBg
                ),
                title = {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "设置",
                            style = MaterialTheme.typography.titleLarge,
                            color = AppColors.TextPrimary
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.ScreenBg)
                .padding(paddingValues)
                .padding(horizontal = 12.scaled())
                .verticalScroll(rememberScrollState())
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

            Spacer(modifier = Modifier.height(12.scaled()))

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

            Spacer(modifier = Modifier.height(12.scaled()))

            // 调试
            SettingsGroup {
                SwitchRow(
                    icon = Icons.Default.BugReport,
                    title = "调试模式",
                    subtitle = "启用日志记录",
                    checked = debugEnabled,
                    onCheckedChange = { settingsViewModel.setDebugEnabled(it) },
                    iconTint = AppColors.AccentBlue
                )
                Divider()
                SettingsItemRow(
                    icon = Icons.Default.Info,
                    title = "查看日志",
                    onClick = { showDebugLog = true },
                    iconTint = AppColors.AccentBlue
                )
            }

            Spacer(modifier = Modifier.height(12.scaled()))

            // 关于
            SettingsGroup {
                SettingsItemRow(
                    icon = Icons.Default.Info,
                    title = "关于",
                    onClick = { settingsViewModel.toast("功能开发中") }
                )
            }

            Spacer(modifier = Modifier.height(24.scaled()))

            // 底部退出按钮
            if (isLoggedIn) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.scaled()))
                        .background(AppColors.CardBg)
                        .clickable { showLogoutDialog = true }
                        .padding(vertical = 18.scaled()),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "退出登录",
                        style = MaterialTheme.typography.bodyLarge,
                        color = AppColors.TextPrimary
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
                                    .padding(vertical = 8.scaled())
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
                                Spacer(modifier = Modifier.width(8.scaled()))
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

        if (showDebugLog) {
            DebugLogDialog(
                onDismiss = { showDebugLog = false }
            )
        }
    }
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.scaled()))
            .background(GroupBg)
    ) {
        content()
    }
}

@Composable
private fun SettingsItemRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    iconTint: Color = AppColors.AccentPink
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.scaled(), vertical = 18.scaled()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(22.scaled())
        )
        Spacer(modifier = Modifier.width(14.scaled()))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = AppColors.TextPrimary,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = AppColors.TextMuted,
            modifier = Modifier.size(20.scaled())
        )
    }
}

@Composable
private fun SwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    iconTint: Color = AppColors.AccentPink
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.scaled(), vertical = 18.scaled()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(22.scaled())
        )
        Spacer(modifier = Modifier.width(14.scaled()))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = AppColors.TextPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.TextSecondary
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
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
            .padding(horizontal = 16.scaled(), vertical = 18.scaled()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isLoggedIn && userInfo?.face?.isNotEmpty() == true) {
            AsyncImage(
                model = userInfo.face,
                contentDescription = null,
                modifier = Modifier
                    .size(40.scaled())
                    .clip(CircleShape)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(40.scaled())
                    .clip(CircleShape)
                    .background(AppColors.CardBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = AppColors.IconMuted,
                    modifier = Modifier.size(22.scaled())
                )
            }
        }
        Spacer(modifier = Modifier.width(14.scaled()))
        Text(
            text = if (isLoggedIn) (userInfo?.name ?: "已登录") else "账号与安全",
            style = MaterialTheme.typography.bodyLarge,
            color = AppColors.TextPrimary,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = AppColors.TextMuted,
            modifier = Modifier.size(20.scaled())
        )
    }
}

@Composable
private fun Divider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(AppColors.Divider)
    )
}

@Composable
private fun DebugLogDialog(onDismiss: () -> Unit) {
    val logs by com.biliaudio.util.DebugLogger.logs.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    // 复制成功后短暂显示「已复制」提示，2 秒后自动消失
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(2000)
            copied = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("调试日志 (${logs.size})")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = {
                            if (logs.isNotEmpty()) {
                                clipboardManager.setText(AnnotatedString(logs.joinToString("\n")))
                                copied = true
                            }
                        },
                        enabled = logs.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (copied) "已复制" else "全部复制")
                    }
                }
            }
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
            ) {
                if (logs.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "暂无日志（请先启用调试模式）",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppColors.TextSecondary
                        )
                    }
                } else {
                    // SelectionContainer 让日志文本可被长按选中、复制片段
                    SelectionContainer {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(logs) { line ->
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppColors.LogText
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}
