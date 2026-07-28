package com.biliaudio.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.biliaudio.ui.components.GeeTestDialog
import com.biliaudio.ui.viewmodel.AuthViewModel
import com.biliaudio.ui.viewmodel.LoginStatus
import com.biliaudio.ui.viewmodel.PasswordLoginStep

private enum class LoginTab(val label: String) {
    QrCode("扫码登录"),
    Password("密码登录")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    authViewModel: AuthViewModel = hiltViewModel(),
    onLoginSuccess: () -> Unit = {}
) {
    val loginStatus by authViewModel.loginStatus.collectAsState()
    val qrCodeUrl by authViewModel.qrCodeUrl.collectAsState()
    val isLoggedIn by authViewModel.isLoggedIn.collectAsState()
    val passwordStep by authViewModel.passwordLoginStep.collectAsState()
    val captchaInfo by authViewModel.captchaInfo.collectAsState()

    var selectedTab by remember { mutableStateOf(LoginTab.QrCode) }

    if (isLoggedIn) {
        onLoginSuccess()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(40.dp))

        Text(
            text = "BiliAudio",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "B站收藏夹音频播放器",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        PrimaryTabRow(
            selectedTabIndex = selectedTab.ordinal,
            modifier = Modifier.fillMaxWidth()
        ) {
            LoginTab.entries.forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = {
                        selectedTab = tab
                        if (tab == LoginTab.Password) {
                            authViewModel.resetPasswordLogin()
                        }
                    },
                    text = { Text(tab.label) }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            when (selectedTab) {
                LoginTab.QrCode -> QrCodeContent(
                    loginStatus = loginStatus,
                    qrCodeUrl = qrCodeUrl,
                    onGenerate = { authViewModel.generateQrCode() },
                    onRefresh = { authViewModel.refreshQrCode() }
                )
                LoginTab.Password -> PasswordContent(
                    step = passwordStep,
                    onLogin = { username, password ->
                        authViewModel.startPasswordLogin(username, password)
                    },
                    onReset = { authViewModel.resetPasswordLogin() }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "登录后即可访问您的B站收藏夹\n仅用于音频播放，不会收集任何用户信息",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }

    // GeeTest 滑块弹窗：等待用户验证
    val needCaptcha = passwordStep is PasswordLoginStep.WaitingForCaptcha
    val cap = captchaInfo
    if (needCaptcha && cap != null && cap.gt.isNotEmpty() && cap.challenge.isNotEmpty()) {
        GeeTestDialog(
            gt = cap.gt,
            challenge = cap.challenge,
            onResult = { result -> authViewModel.submitCaptchaResult(result) }
        )
    }
}

@Composable
private fun QrCodeContent(
    loginStatus: LoginStatus,
    qrCodeUrl: String?,
    onGenerate: () -> Unit,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (loginStatus) {
            is LoginStatus.Idle -> {
                QrCodePlaceholder()
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "点击下方按钮获取二维码",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            is LoginStatus.Loading -> {
                Box(
                    modifier = Modifier.size(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "正在生成二维码...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            is LoginStatus.QrCodeReady -> {
                QrCodeImage(qrCodeUrl)
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "打开B站APP扫码登录",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            is LoginStatus.Scanned -> {
                QrCodeImage(qrCodeUrl)
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "扫码成功，请在手机上确认",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            is LoginStatus.Expired -> {
                QrCodePlaceholder()
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "二维码已过期",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error
                )
            }
            is LoginStatus.Success -> {
                QrCodePlaceholder()
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "登录成功！",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            is LoginStatus.Error -> {
                QrCodePlaceholder()
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = loginStatus.message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        when (loginStatus) {
            is LoginStatus.Idle, is LoginStatus.Error -> {
                Button(
                    onClick = onGenerate,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(text = "获取二维码", style = MaterialTheme.typography.titleMedium)
                }
            }
            is LoginStatus.Expired -> {
                Button(
                    onClick = onRefresh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(text = "刷新二维码", style = MaterialTheme.typography.titleMedium)
                }
            }
            else -> {
                OutlinedButton(
                    onClick = onRefresh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(text = "刷新二维码", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun PasswordContent(
    step: PasswordLoginStep,
    onLogin: (String, String) -> Unit,
    onReset: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val isBusy = step is PasswordLoginStep.LoadingCaptcha ||
        step is PasswordLoginStep.LoggingIn

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("手机号 / 邮箱") },
            singleLine = true,
            enabled = !isBusy,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("密码") },
            singleLine = true,
            enabled = !isBusy,
            visualTransformation = if (passwordVisible) VisualTransformation.None
                else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff
                            else Icons.Default.Visibility,
                        contentDescription = if (passwordVisible) "隐藏密码" else "显示密码"
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 状态提示
        when (step) {
            is PasswordLoginStep.Idle -> { /* 等待输入 */ }
            is PasswordLoginStep.LoadingCaptcha -> {
                Text(
                    text = "正在准备验证码...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            is PasswordLoginStep.WaitingForCaptcha -> {
                Text(
                    text = "请完成下方滑块验证",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            is PasswordLoginStep.LoggingIn -> {
                Text(
                    text = "正在登录...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            is PasswordLoginStep.Success -> {
                Text(
                    text = "登录成功",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            is PasswordLoginStep.Error -> {
                Text(
                    text = step.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        Button(
            onClick = { onLogin(username, password) },
            enabled = !isBusy && username.isNotBlank() && password.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            if (isBusy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text(text = "登录", style = MaterialTheme.typography.titleMedium)
            }
        }

        if (step is PasswordLoginStep.Error) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    onReset()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(text = "重置", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun QrCodeImage(qrCodeUrl: String?) {
    if (qrCodeUrl != null) {
        AsyncImage(
            model = qrCodeUrl,
            contentDescription = null,
            modifier = Modifier
                .size(200.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
        )
    } else {
        QrCodePlaceholder()
    }
}

@Composable
private fun QrCodePlaceholder() {
    Box(
        modifier = Modifier
            .size(200.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        Image(
            imageVector = Icons.Default.QrCode2,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant)
        )
    }
}
