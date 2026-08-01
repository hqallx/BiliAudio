package com.biliaudio.ui.screens

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.biliaudio.data.network.BiliCookieJar
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class VideoWebViewActivity : ComponentActivity() {

    @Inject
    lateinit var cookieJar: BiliCookieJar

    private var webView: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bvid = intent.getStringExtra("bvid") ?: ""
        val title = intent.getStringExtra("title") ?: "视频详情"
        val url = if (bvid.isNotEmpty()) "https://www.bilibili.com/video/$bvid" else ""

        // Inject cookies into WebView before it loads
        val cookieManager = CookieManager.getInstance()
        cookieManager.removeAllCookies(null)
        cookieJar.getAllCookies().forEach { cookie ->
            cookieManager.setCookie(".bilibili.com", "${cookie.name}=${cookie.value}")
        }
        cookieManager.flush()

        setContent {
            MaterialTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(title, maxLines = 1) },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                                }
                            }
                        )
                    }
                ) { paddingValues ->
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                webViewClient = WebViewClient()
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.userAgentString = settings.userAgentString.replace("Mobile", "Mobile; AppName=BiliAudio")
                                
                                if (url.isNotEmpty()) {
                                    loadUrl(url)
                                }
                                webView = this
                            }
                        },
                        update = { it.loadUrl(url) }
                    )
                }
                
                BackHandler(enabled = true) {
                    if (webView?.canGoBack() == true) {
                        webView?.goBack()
                    } else {
                        finish()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        webView?.destroy()
        webView = null
        super.onDestroy()
    }
}
