package com.biliaudio.ui.screens

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.biliaudio.data.BiliConstants
import com.biliaudio.data.network.CookieHelper
import com.biliaudio.ui.viewmodel.AuthViewModel

/**
 * B站授权登录页面（WebView 方式）。
 *
 * 在 WebView 中加载 B站官方登录页，用户完成登录后（扫码/短信/密码均可），
 * WebView 的 CookieManager 中会出现 SESSDATA 等登录 Cookie。
 * 本页面轮询检测这些 Cookie，一旦发现就同步到 OkHttp 的 BiliCookieJar，
 * 然后导航回主界面——相当于 B站给本应用授权。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebViewLoginScreen(
    authViewModel: AuthViewModel = hiltViewModel(),
    onLoginSuccess: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    val isLoggedIn by authViewModel.isLoggedIn.collectAsState()
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // 登录成功后自动返回
    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            // 清理 WebView 的 Cookie，避免残留（已同步到 BiliCookieJar）
            webViewRef?.let { wv ->
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
            }
            onLoginSuccess()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("B站授权登录", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            factory = { context ->
                createBiliWebView(context) { url, cookieString ->
                    // 检测到登录 Cookie 时同步到 BiliCookieJar
                    val cookies = CookieHelper.parseCookies(cookieString)
                    if (CookieHelper.hasLoginCookies(cookies)) {
                        authViewModel.onWebViewLoginCompleted(cookieString)
                    }
                }.also { webViewRef = it }
            }
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createBiliWebView(
    context: android.content.Context,
    onCookiesDetected: (url: String, cookieString: String) -> Unit
): WebView {
    // 确保 WebView 的 CookieManager 启用
    CookieManager.getInstance().setAcceptCookie(true)

    return WebView(context).apply {
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            userAgentString = BiliConstants.USER_AGENT
            allowFileAccess = false
            allowContentAccess = false
            cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
        }

        // 允许第三方 Cookie（B站登录流程需要）
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                return false
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                // 每次页面加载完成后检查 Cookie
                val cookieString = CookieManager.getInstance().getCookie(url)
                if (!cookieString.isNullOrEmpty()) {
                    onCookiesDetected(url, cookieString)
                }
            }
        }

        // 加载 B站登录页
        loadUrl("https://passport.bilibili.com/login")
    }
}
