package com.biliaudio.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.biliaudio.ui.components.GeeTestDialog
import com.biliaudio.ui.viewmodel.AuthViewModel
import com.biliaudio.ui.viewmodel.LoginStatus
import com.biliaudio.ui.viewmodel.SmsLoginStep
import com.biliaudio.util.QrCodeGenerator

private enum class LoginTab(val label: String) {
    QrCode("扫码登录"),
    Sms("短信登录")
}

/** 常用国家/地区区号。 */
private data class CountryCode(val code: String, val label: String)
private val COUNTRY_CODES = listOf(
    CountryCode("86", "+86 中国大陆"),
    CountryCode("852", "+852 中国香港"),
    CountryCode("853", "+853 中国澳门"),
    CountryCode("886", "+886 中国台湾"),
    CountryCode("1", "+1 美国/加拿大"),
    CountryCode("81", "+81 日本"),
    CountryCode("82", "+82 韩国"),
    CountryCode("65", "+65 新加坡"),
    CountryCode("60", "+60 马来西亚"),
    CountryCode("1", "+1 其他")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    authViewModel: AuthViewModel = hiltViewModel(),
    onLoginSuccess: () -> Unit = {}
) {
    val loginStatus by authViewModel.loginStatus.collectAsState()
    val qrCodeUrl by authViewModel.qrCodeUrl.collectAsState()
    val isLoggedIn by authViewModel.isLoggedIn.collectAsState()
    val smsStep by authViewModel.smsLoginStep.collectAsState()
    val captchaInfo by authViewModel.captchaInfo.collectAsState()
    val smsCountdown by authViewModel.smsCountdown.collectAsState()

    var selectedTab by remember { mutableStateOf(LoginTab.QrCode) }

    // 登录成功回调必须在副作用中执行，不能在组合过程中直接调用导航
    // 否则会触发 "Navigating during composition" 异常导致闪退
    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            onLoginSuccess()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 右上角「跳过」按钮：进入游客模式浏览
        TextButton(
            onClick = {
                authViewModel.enterGuestMode()
                onLoginSuccess()
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
        ) {
            Text(
                text = "跳过",
                style = MaterialTheme.typography.titleSmall
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
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
                            if (tab == LoginTab.Sms) {
                                authViewModel.resetSmsLogin()
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
                    LoginTab.Sms -> SmsContent(
                        step = smsStep,
                        countdown = smsCountdown,
                        onSendCode = { cid, tel -> authViewModel.startSmsLogin(cid, tel) },
                        onLogin = { code -> authViewModel.loginWithSmsCode(code) },
                        onReset = { authViewModel.resetSmsLogin() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 用 B站 App 直接授权登录：打开二维码接口返回的 url，
            // B站 App 通过 App Links 拦截该 passport.bilibili.com 链接，
            // 直接弹出授权确认界面（无需扫码）。
            BiliClientButton(
                qrCodeUrl = qrCodeUrl,
                onGenerate = { authViewModel.generateQrCode() }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "登录后即可访问您的B站收藏夹\n仅用于音频播放，不会收集任何用户信息",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }

    // GeeTest 滑块弹窗：等待用户验证
    val needCaptcha = smsStep is SmsLoginStep.WaitingForCaptcha
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SmsContent(
    step: SmsLoginStep,
    countdown: Int,
    onSendCode: (String, String) -> Unit,
    onLogin: (String) -> Unit,
    onReset: () -> Unit
) {
    var selectedCountry by remember { mutableStateOf(COUNTRY_CODES[0]) }
    var expanded by remember { mutableStateOf(false) }
    var phone by remember { mutableStateOf("") }
    var smsCode by remember { mutableStateOf("") }

    val showCodeInput = step is SmsLoginStep.WaitingForSmsCode ||
        step is SmsLoginStep.LoggingIn ||
        step is SmsLoginStep.Success
    val isBusy = step is SmsLoginStep.LoadingCaptcha ||
        step is SmsLoginStep.SendingSms ||
        step is SmsLoginStep.LoggingIn

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 区号 + 手机号：ExposedDropdownMenuBox 只包住区号输入框，
        // 否则 menuAnchor 会作用到整个 Row，点击手机号输入框也会弹出区号菜单。
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
                modifier = Modifier.weight(0.45f)
            ) {
                OutlinedTextField(
                    value = selectedCountry.label,
                    onValueChange = {},
                    readOnly = true,
                    enabled = !isBusy,
                    singleLine = true,
                    label = { Text("区号") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    COUNTRY_CODES.forEach { country ->
                        DropdownMenuItem(
                            text = { Text(country.label) },
                            onClick = {
                                selectedCountry = country
                                expanded = false
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it.filter { c -> c.isDigit() } },
                enabled = !isBusy,
                singleLine = true,
                label = { Text("手机号") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.weight(0.55f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 验证码输入框 + 发送按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = smsCode,
                onValueChange = { smsCode = it.filter { c -> c.isDigit() }.take(6) },
                enabled = showCodeInput && step !is SmsLoginStep.LoggingIn,
                singleLine = true,
                label = { Text("验证码") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier.weight(0.55f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { onSendCode(selectedCountry.code, phone) },
                enabled = !isBusy && phone.isNotBlank() && countdown <= 0,
                modifier = Modifier
                    .weight(0.45f)
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = if (countdown > 0) "${countdown}s" else "发送验证码",
                    style = MaterialTheme.typography.titleSmall
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 状态提示
        when (step) {
            is SmsLoginStep.Idle -> { /* 等待输入 */ }
            is SmsLoginStep.LoadingCaptcha -> {
                Text(
                    text = "正在准备滑块验证...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            is SmsLoginStep.WaitingForCaptcha -> {
                Text(
                    text = "请完成下方滑块验证",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            is SmsLoginStep.SendingSms -> {
                Text(
                    text = "正在发送短信...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            is SmsLoginStep.WaitingForSmsCode -> {
                Text(
                    text = "验证码已发送，请输入收到的验证码",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            is SmsLoginStep.LoggingIn -> {
                Text(
                    text = "正在登录...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            is SmsLoginStep.Success -> {
                Text(
                    text = "登录成功",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            is SmsLoginStep.Error -> {
                Text(
                    text = step.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        Button(
            onClick = { onLogin(smsCode) },
            enabled = showCodeInput &&
                step !is SmsLoginStep.LoggingIn &&
                smsCode.length >= 4,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            if (step is SmsLoginStep.LoggingIn) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text(text = "登录", style = MaterialTheme.typography.titleMedium)
            }
        }

        if (step is SmsLoginStep.Error) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    onReset()
                    smsCode = ""
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(text = "重置", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/**
 * 用 B站 App 直接授权登录按钮。
 *
 * 实现方式（参考 BBPlayer）：打开二维码接口返回的 url（passport.bilibili.com
 * 的 HTTPS 链接）。已安装的 B站 App 通过 App Links 拦截该链接，直接弹出
 * 授权确认界面，用户点确认后本应用的二维码轮询即检测到登录成功——全程无需扫码。
 *
 * 若未安装 B站 App，系统浏览器会打开该链接进行网页端确认。
 *
 * @param qrCodeUrl 二维码接口返回的登录链接；为空时先触发 onGenerate 生成
 * @param onGenerate qrCodeUrl 为空时回调，用于生成二维码
 */
@Composable
private fun BiliClientButton(
    qrCodeUrl: String?,
    onGenerate: () -> Unit
) {
    val context = LocalContext.current
    OutlinedButton(
        onClick = {
            val url = qrCodeUrl
            if (url.isNullOrEmpty()) {
                // 还没生成二维码：先生成，提示用户稍候再点
                onGenerate()
                Toast.makeText(
                    context,
                    "正在生成二维码，请稍候再次点击此按钮",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                openBiliAuthorization(context, url)
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Icon(
            imageVector = Icons.Default.PhoneAndroid,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "用B站App授权登录",
            style = MaterialTheme.typography.titleSmall
        )
    }
}

/**
 * 通过 ACTION_VIEW 打开二维码登录链接。
 * B站 App（若已安装）会通过 App Links 拦截 passport.bilibili.com 链接，
 * 直接显示授权确认界面；否则由系统浏览器打开网页端确认。
 */
private fun openBiliAuthorization(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
        .onFailure {
            it.printStackTrace()
            Toast.makeText(
                context,
                "无法打开授权页面，请确认已安装B站App或浏览器",
                Toast.LENGTH_SHORT
            ).show()
        }
}

/**
 * 用 zxing 把 bilibili 返回的登录链接生成本地二维码 Bitmap。
 * 这是二维码不显示的根本修复：之前误把登录链接当图片 URL 用 Coil 加载。
 */
@Composable
private fun QrCodeImage(qrCodeUrl: String?) {
    var bitmap by remember(qrCodeUrl) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(qrCodeUrl) {
        bitmap = qrCodeUrl?.let { QrCodeGenerator.generate(it, 600) }
    }

    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = "登录二维码",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(220.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
        )
    } else {
        QrCodePlaceholder()
    }
}

@Composable
private fun QrCodePlaceholder() {
    Box(
        modifier = Modifier
            .size(220.dp)
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
