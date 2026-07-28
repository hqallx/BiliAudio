package com.biliaudio.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biliaudio.data.BiliConstants
import com.biliaudio.data.Result
import com.biliaudio.data.model.CaptchaResponse
import com.biliaudio.data.model.UserInfo
import com.biliaudio.data.model.WebKeyResponse
import com.biliaudio.data.network.CookieHelper
import com.biliaudio.data.preferences.PreferencesManager
import com.biliaudio.data.repository.AuthRepository
import com.biliaudio.ui.components.GeeTestResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    // ============ 二维码登录状态 ============

    private val _qrCodeUrl = MutableStateFlow<String?>(null)
    val qrCodeUrl: StateFlow<String?> = _qrCodeUrl.asStateFlow()

    private val _qrCodeKey = MutableStateFlow<String?>(null)

    private val _loginStatus = MutableStateFlow<LoginStatus>(LoginStatus.Idle)
    val loginStatus: StateFlow<LoginStatus> = _loginStatus.asStateFlow()

    // ============ 用户信息 ============

    private val _userInfo = MutableStateFlow<UserInfo?>(null)
    val userInfo: StateFlow<UserInfo?> = _userInfo.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    // ============ 密码登录状态 ============

    private val _passwordLoginStep = MutableStateFlow<PasswordLoginStep>(PasswordLoginStep.Idle)
    val passwordLoginStep: StateFlow<PasswordLoginStep> = _passwordLoginStep.asStateFlow()

    private val _captchaInfo = MutableStateFlow<CaptchaResponse?>(null)
    val captchaInfo: StateFlow<CaptchaResponse?> = _captchaInfo.asStateFlow()

    private var webKey: WebKeyResponse? = null
    private var captcha: CaptchaResponse? = null
    private var pendingUsername: String = ""
    private var pendingPassword: String = ""

    private var pollJob: Job? = null

    init {
        // 应用启动时检查 Cookie 是否有效
        viewModelScope.launch {
            val hasLogin = authRepository.isLoggedIn()
            _isLoggedIn.value = hasLogin
            if (hasLogin) {
                loadUserInfo()
            }
        }

        // 监听 Cookie 更新（扫码登录成功时触发）
        authRepository.onCookiesUpdated = { cookies ->
            viewModelScope.launch {
                if (CookieHelper.hasLoginCookies(cookies)) {
                    _isLoggedIn.value = true
                    loadUserInfo()
                }
            }
        }
    }

    // ============ 二维码登录 ============

    fun generateQrCode() {
        viewModelScope.launch {
            _loginStatus.value = LoginStatus.Loading
            when (val result = authRepository.generateQrCode()) {
                is Result.Success -> {
                    val response = result.data
                    if (response.code == 0 && response.data != null) {
                        _qrCodeUrl.value = response.data.url
                        _qrCodeKey.value = response.data.qrcodeKey
                        _loginStatus.value = LoginStatus.QrCodeReady
                        startPolling()
                    } else {
                        _loginStatus.value = LoginStatus.Error(response.message)
                    }
                }
                is Result.Error -> {
                    _loginStatus.value = LoginStatus.Error(result.message)
                    _toast.value = result.message
                }
                Result.Loading -> {}
            }
        }
    }

    private fun startPolling() {
        pollJob?.cancel()
        val qrKey = _qrCodeKey.value ?: return

        pollJob = viewModelScope.launch {
            while (true) {
                delay(BiliConstants.QR_CODE_POLL_INTERVAL_MS)
                when (val result = authRepository.checkQrCodeStatus(qrKey)) {
                    is Result.Success -> {
                        when (result.data.data?.code) {
                            BiliConstants.QrCodeStatus.EXPIRED -> {
                                _loginStatus.value = LoginStatus.Expired
                                break
                            }
                            BiliConstants.QrCodeStatus.SUCCESS -> {
                                _loginStatus.value = LoginStatus.Success
                                _isLoggedIn.value = true
                                loadUserInfo()
                                break
                            }
                            BiliConstants.QrCodeStatus.SCANNED_WAITING_CONFIRM -> {
                                _loginStatus.value = LoginStatus.Scanned
                            }
                            BiliConstants.QrCodeStatus.WAITING_FOR_SCAN -> {
                                _loginStatus.value = LoginStatus.QrCodeReady
                            }
                        }
                    }
                    is Result.Error -> {
                        // 网络错误时继续轮询
                    }
                    Result.Loading -> {}
                }
            }
        }
    }

    // ============ 密码登录 ============

    /**
     * 启动密码登录：拉取 web/key 与 captcha，
     * 若需要 GeeTest 则进入 WaitingForCaptcha 状态，由 UI 弹出滑块。
     */
    fun startPasswordLogin(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            _toast.value = "请输入用户名和密码"
            return
        }
        pendingUsername = username
        pendingPassword = password
        _passwordLoginStep.value = PasswordLoginStep.LoadingCaptcha

        viewModelScope.launch {
            // 1. 获取 RSA 公钥
            val keyResult = authRepository.getWebKey()
            when (keyResult) {
                is Result.Success -> {
                    val resp = keyResult.data
                    if (resp.code != 0 || resp.data == null) {
                        val msg = resp.message.ifEmpty { "获取密钥失败" }
                        _passwordLoginStep.value = PasswordLoginStep.Error(msg)
                        _toast.value = msg
                        return@launch
                    }
                    webKey = resp.data
                }
                is Result.Error -> {
                    _passwordLoginStep.value = PasswordLoginStep.Error(keyResult.message)
                    _toast.value = keyResult.message
                    return@launch
                }
                Result.Loading -> {}
            }

            // 2. 获取验证码
            when (val captchaResult = authRepository.getCaptcha()) {
                is Result.Success -> {
                    val resp = captchaResult.data
                    if (resp.code != 0 || resp.data == null) {
                        val msg = resp.message.ifEmpty { "获取验证码失败" }
                        _passwordLoginStep.value = PasswordLoginStep.Error(msg)
                        _toast.value = msg
                        return@launch
                    }
                    captcha = resp.data
                    _captchaInfo.value = resp.data

                    if (resp.data.type == "geetest" && resp.data.gt.isNotEmpty()) {
                        // 需要滑块验证：交给 UI 弹出 GeeTestDialog
                        _passwordLoginStep.value = PasswordLoginStep.WaitingForCaptcha
                    } else {
                        // 不需要滑块（极少见）：直接提交
                        submitPasswordLogin(
                            GeeTestResult(resp.data.challenge, "", "")
                        )
                    }
                }
                is Result.Error -> {
                    _passwordLoginStep.value = PasswordLoginStep.Error(captchaResult.message)
                    _toast.value = captchaResult.message
                }
                Result.Loading -> {}
            }
        }
    }

    /**
     * 用户在 GeeTestDialog 完成或取消后回调。
     * result == null 表示用户取消，重置到 Idle 状态。
     */
    fun submitCaptchaResult(result: GeeTestResult?) {
        if (result == null) {
            _passwordLoginStep.value = PasswordLoginStep.Idle
            return
        }
        submitPasswordLogin(result)
    }

    private fun submitPasswordLogin(geeTestResult: GeeTestResult) {
        val key = webKey
        val cap = captcha
        if (key == null || cap == null) {
            _passwordLoginStep.value = PasswordLoginStep.Error("登录状态错误")
            return
        }

        viewModelScope.launch {
            _passwordLoginStep.value = PasswordLoginStep.LoggingIn
            when (val result = authRepository.loginWithPassword(
                username = pendingUsername,
                password = pendingPassword,
                webKey = key,
                captcha = cap,
                geeTestResult = geeTestResult
            )) {
                is Result.Success -> {
                    val resp = result.data
                    val data = resp.data
                    if (resp.code == 0 && data?.isLogin == true) {
                        _isLoggedIn.value = true
                        _passwordLoginStep.value = PasswordLoginStep.Success
                        loadUserInfo()
                    } else {
                        // 失败：需要重新拉 key + captcha，回到 Idle 让用户重试
                        val msg = (data?.message?.ifEmpty { null } ?: resp.message)
                            .ifEmpty { "登录失败" }
                        _passwordLoginStep.value = PasswordLoginStep.Error(msg)
                        _toast.value = msg
                    }
                }
                is Result.Error -> {
                    _passwordLoginStep.value = PasswordLoginStep.Error(result.message)
                    _toast.value = result.message
                }
                Result.Loading -> {}
            }
        }
    }

    /** 重置密码登录状态，允许用户重新尝试。 */
    fun resetPasswordLogin() {
        _passwordLoginStep.value = PasswordLoginStep.Idle
        _captchaInfo.value = null
        webKey = null
        captcha = null
        pendingUsername = ""
        pendingPassword = ""
    }

    // ============ 通用 ============

    fun loadUserInfo() {
        viewModelScope.launch {
            when (val result = authRepository.getUserInfo()) {
                is Result.Success -> {
                    val response = result.data
                    if (response.code == 0 && response.data != null) {
                        _userInfo.value = response.data
                        preferencesManager.saveUserInfo(
                            id = response.data.mid.toString(),
                            name = response.data.name,
                            avatar = response.data.face
                        )
                    }
                }
                is Result.Error -> {
                    // Cookie 可能过期
                    if (_isLoggedIn.value) {
                        _toast.value = "登录已失效，请重新登录"
                        logout()
                    }
                }
                Result.Loading -> {}
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.clearCookies()
            preferencesManager.clearAll()
            _isLoggedIn.value = false
            _userInfo.value = null
            _loginStatus.value = LoginStatus.Idle
            _passwordLoginStep.value = PasswordLoginStep.Idle
            _captchaInfo.value = null
            webKey = null
            captcha = null
        }
    }

    fun refreshQrCode() {
        pollJob?.cancel()
        generateQrCode()
    }

    fun consumeToast() {
        _toast.value = null
    }

    override fun onCleared() {
        super.onCleared()
        pollJob?.cancel()
    }
}

sealed class LoginStatus {
    data object Idle : LoginStatus()
    data object Loading : LoginStatus()
    data object QrCodeReady : LoginStatus()
    data object Scanned : LoginStatus()
    data object Expired : LoginStatus()
    data object Success : LoginStatus()
    data class Error(val message: String) : LoginStatus()
}

/** 密码登录状态机。 */
sealed class PasswordLoginStep {
    /** 空闲：等待用户输入。 */
    data object Idle : PasswordLoginStep()
    /** 正在获取验证码 / RSA key。 */
    data object LoadingCaptcha : PasswordLoginStep()
    /** 等待用户完成滑块验证。 */
    data object WaitingForCaptcha : PasswordLoginStep()
    /** 正在提交登录请求。 */
    data object LoggingIn : PasswordLoginStep()
    /** 登录成功。 */
    data object Success : PasswordLoginStep()
    /** 登录失败，等待用户重试。 */
    data class Error(val message: String) : PasswordLoginStep()
}
