package com.biliaudio.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.OpenInNew
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
import com.biliaudio.ui.theme.Motion
import com.biliaudio.ui.viewmodel.AuthViewModel
import com.biliaudio.ui.viewmodel.LoginStatus
import com.biliaudio.ui.viewmodel.SmsLoginStep
import com.biliaudio.util.QrCodeGenerator

private enum class LoginTab(val label: String) {
    QrCode("扫码登录"),
    Sms("短信登录")
}

/**
 * 常用国家/地区。
 *
 * @param cid bilibili SMS 接口的 cid 参数，直接传区号（如中国大陆=86）。
 *             参考 BBPlayer: const COUNTRY_CODE = '86'
 * @param areaCode 区号数字（仅用于显示，如 86）
 * @param label 显示文本
 */
private data class CountryCode(val cid: String, val areaCode: String, val label: String)
private val COUNTRY_CODES = listOf(
    CountryCode("86", "86", "+86 中国大陆"),
    CountryCode("852", "852", "+852 中国香港"),
    CountryCode("853", "853", "+853 中国澳门"),
    CountryCode("886", "886", "+886 中国台湾"),
    CountryCode("1", "1", "+1 美国/加拿大"),
    CountryCode("81", "81", "+81 日本"),
    CountryCode("82", "82", "+82 韩国"),
    CountryCode("65", "65", "+65 新加坡"),
    CountryCode("60", "60", "+60 马来西亚")
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
                // Tab 内容切换：Crossfade 淡入淡出
                Crossfade(
                    targetState = selectedTab,
                    animationSpec = tween(Motion.DurationMedium, easing = Motion.EasingStandard),
                    label = "loginTab"
                ) { tab ->
                    when (tab) {
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
            }

            Spacer(modifier = Modifier.height(16.dp))

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "登录后即可访问您的B站收藏夹\n仅用于音频播放，不会收集任何用户信息",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        // 右上角「跳过」按钮：直接进入主界面浏览（无需登录）
        // 注意：必须放在 Column 之后声明，使其绘制在 Column 之上层。
        // 之前放在 Column 之前，被 fillMaxSize 的 Column 覆盖，导致点击事件被拦截。
        TextButton(
            onClick = { onLoginSuccess() },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
        ) {
            Text(
                text = "跳过",
                style = MaterialTheme.typography.titleSmall
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
    val context = LocalContext.current
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

        // 客户端授权登录：直接打开二维码 URL，B站 App 通过 App Links 拦截后
        // 弹出授权确认页（无需扫码）。应用后台轮询检测登录成功并提取 Cookie。
        if (loginStatus is LoginStatus.QrCodeReady ||
            loginStatus is LoginStatus.Scanned
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = {
                    val url = qrCodeUrl
                    if (url.isNullOrEmpty()) {
                        Toast.makeText(context, "请先获取二维码", Toast.LENGTH_SHORT).show()
                    } else {
                        openBiliClientAuth(context, url)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "在B站客户端授权",
                    style = MaterialTheme.typography.titleSmall
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "同一设备可直接跳转B站App授权，无需扫码",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
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
                onClick = { onSendCode(selectedCountry.cid, phone) },
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
 * 跳转B站客户端进行授权登录。
 *
 * 优先通过 setPackage 直接拉起B站 App（tv.danmaku.bili），
 * B站 App 会解析二维码登录 URL 并弹出「授权登录」确认页——
 * 用户在B站 App 内点确认后，本应用后台的轮询会检测到登录成功并提取 Cookie。
 *
 * 若未安装B站 App，回退到系统浏览器打开该链接。
 *
 * 注意：不依赖 resolveActivity 判断——在 Android 11+ 包可见性限制下，
 * 即使声明了 <queries>，resolveActivity 对隐式 intent 仍可能返回 null。
 * 改为直接 startActivity 并捕获 ActivityNotFoundException，更稳健。
 *
 * 参考：BBPlayer 的 WebBrowser.openBrowserAsync(qrcodeUrl) 实现；
 *      bilibili-API-collect 的 web 端扫码登录流程。
 */
private fun openBiliClientAuth(context: Context, url: String) {
    // B站 App 包名列表（国际版/概念版等），按优先级排列
    val biliPackages = listOf(
        "tv.danmaku.bili",           // 哔哩哔哩（主版本）
        "tv.danmaku.bilibilimirror", // 哔哩哔哩（镜像版）
        "com.bilibili.app.in",       // 哔哩哔哩（国际版）
        "com.bilibili.app.blue"      // 哔哩哔哩（概念版/蓝色版）
    )

    // 1. 优先尝试直接拉起B站 App（捕获 ActivityNotFoundException 表示未安装该包）
    for (pkg in biliPackages) {
        val biliIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            setPackage(pkg)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(biliIntent)
            return // 拉起成功
        } catch (e: android.content.ActivityNotFoundException) {
            // 该包名未安装，继续尝试下一个
        } catch (e: SecurityException) {
            // setPackage 被安全策略拒绝，继续尝试下一个
        }
    }

    // 2. 未安装B站 App，回退到系统浏览器（隐式 intent）
    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(browserIntent)
    } catch (e: android.content.ActivityNotFoundException) {
        Toast.makeText(
            context,
            "无法打开授权页面，请安装B站App或使用扫码登录",
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
