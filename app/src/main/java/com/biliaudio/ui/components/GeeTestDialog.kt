package com.biliaudio.ui.components

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

data class GeeTestResult(
    val challenge: String,
    val validate: String,
    val seccode: String
)

/**
 * bilibili 密码登录所需的 GeeTest 滑块验证码弹窗。
 *
 * 通过 WebView 加载 GeeTest SDK，用 [gt] / [challenge] 初始化滑块；
 * 用户拖动完成后 JS 会回调 [onResult]，将结果传回 ViewModel 用于登录提交。
 *
 * @param gt         GeeTest 的 gt 标识
 * @param challenge   GeeTest 的 challenge 标识
 * @param onResult    用户验证完成时返回 GeeTestResult；用户取消时返回 null
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GeeTestDialog(
    gt: String,
    challenge: String,
    onResult: (GeeTestResult?) -> Unit
) {
    var html by remember(gt, challenge) { mutableStateOf("") }

    LaunchedEffect(gt, challenge) {
        html = """
        <!DOCTYPE html>
        <html>
        <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
        <style>
            html, body { margin:0; padding:0; height:100%; background:#fff; font-family: sans-serif; }
            #captcha { display:flex; align-items:center; justify-content:center; height:100%; }
        </style>
        <script src="https://static.geetest.com/static/js/gt.0.4.9.js"></script>
        </head>
        <body>
        <div id="captcha"></div>
        <script>
        function boot() {
          initGeetest({
            gt: '$gt',
            challenge: '$challenge',
            offline: false,
            new_captcha: true,
            product: 'popup',
            width: '100%',
            https: true
          }, function(captchaObj) {
            captchaObj.appendTo('#captcha');
            captchaObj.onSuccess(function() {
              var r = captchaObj.getValidate();
              if (r && r.geetest_challenge && r.geetest_validate && r.geetest_seccode) {
                Android.onSuccess(
                  String(r.geetest_challenge),
                  String(r.geetest_validate),
                  String(r.geetest_seccode)
                );
              } else {
                Android.onError();
              }
            });
            captchaObj.onClose(function() { Android.onClose(); });
            captchaObj.onError(function() { Android.onError(); });
          });
        }
        if (typeof initGeetest === 'function') {
          boot();
        } else {
          window.addEventListener('load', boot);
        }
        </script>
        </body>
        </html>
        """.trimIndent()
    }

    AlertDialog(
        onDismissRequest = { onResult(null) },
        title = { Text("滑块验证") },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp),
                contentAlignment = Alignment.Center
            ) {
                if (html.isEmpty()) {
                    CircularProgressIndicator()
                } else {
                    AndroidView(
                        factory = { context ->
                            WebView(context).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.allowFileAccess = false
                                webViewClient = WebViewClient()
                                addJavascriptInterface(
                                    object {
                                        @JavascriptInterface
                                        fun onSuccess(challenge: String, validate: String, seccode: String) {
                                            onResult(GeeTestResult(challenge, validate, seccode))
                                        }

                                        @JavascriptInterface
                                        fun onClose() {
                                            onResult(null)
                                        }

                                        @JavascriptInterface
                                        fun onError() {
                                            onResult(null)
                                        }
                                    },
                                    "Android"
                                )
                                loadDataWithBaseURL(
                                    "https://www.bilibili.com/",
                                    html,
                                    "text/html",
                                    "UTF-8",
                                    null
                                )
                            }
                        },
                        update = { /* no-op */ }
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { onResult(null) }) { Text("取消") }
        }
    )
}
