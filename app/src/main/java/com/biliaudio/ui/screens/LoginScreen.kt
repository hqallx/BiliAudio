package com.biliaudio.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.biliaudio.ui.viewmodel.AuthViewModel
import com.biliaudio.ui.viewmodel.LoginStatus

@Composable
fun LoginScreen(
    authViewModel: AuthViewModel = hiltViewModel(),
    onLoginSuccess: () -> Unit = {}
) {
    val loginStatus by authViewModel.loginStatus.collectAsState()
    val qrCodeUrl by authViewModel.qrCodeUrl.collectAsState()
    val isLoggedIn by authViewModel.isLoggedIn.collectAsState()

    if (isLoggedIn) {
        onLoginSuccess()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.height(60.dp))

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

        Spacer(modifier = Modifier.height(48.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
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
                            text = (loginStatus as LoginStatus.Error).message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        when (loginStatus) {
            is LoginStatus.Idle, is LoginStatus.Error -> {
                Button(
                    onClick = { authViewModel.generateQrCode() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "获取二维码",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
            is LoginStatus.Expired -> {
                Button(
                    onClick = { authViewModel.refreshQrCode() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "刷新二维码",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
            else -> {
                OutlinedButton(
                    onClick = { authViewModel.refreshQrCode() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "刷新二维码",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
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
