package com.biliaudio.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biliaudio.data.BiliConstants
import com.biliaudio.data.Result
import com.biliaudio.data.model.CaptchaResponse
import com.biliaudio.data.model.UserInfo
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

    // ============ 短信登录状态 ============

    private val _smsLoginStep = MutableStateFlow<SmsLoginStep>(SmsLoginStep.Idle)
    val smsLoginStep: StateFlow<SmsLoginStep> = _smsLoginStep.asStateFlow()

    private val _captchaInfo = MutableStateFlow<CaptchaResponse?>(null)
    val captchaInfo: StateFlow<CaptchaResponse?> = _captchaInfo.asStateFlow()

    /** 短信倒计时（秒），>0 时按钮禁用并显示倒计时。 */
    private val _smsCountdown = MutableStateFlow(0)
    val smsCountdown: StateFlow<Int> = _smsCountdown.asStateFlow()

    private var captcha: CaptchaResponse? = null
    private var geeTestResult: GeeTestResult? = null
    private var smsCaptchaKey: String? = null
    private var pendingCid: String = "86"
    private var pendingTel: String = ""
    private var countdownJob: Job? = null

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

        // 监听 Cookie 更新（扫码/短信登录成功时触发）
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
                        _toast.value = response.message
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

    // ============ 短信登录 ============

    /**
     * 启动短信登录第一步：拉取 GeeTest 验证码。
     * 成功后进入 WaitingForCaptcha 状态，由 UI 弹出滑块。
     */
    fun startSmsLogin(cid: String, tel: String) {
        if (tel.isBlank()) {
            _toast.value = "请输入手机号"
            return
        }
        pendingCid = cid
        pendingTel = tel
        _smsLoginStep.value = SmsLoginStep.LoadingCaptcha

        viewModelScope.launch {
            when (val captchaResult = authRepository.getCaptcha()) {
                is Result.Success -> {
                    val resp = captchaResult.data
                    if (resp.code != 0 || resp.data == null) {
                        val msg = resp.message.ifEmpty { "获取验证码失败" }
                        _smsLoginStep.value = SmsLoginStep.Error(msg)
                        _toast.value = msg
                        return@launch
                    }
                    captcha = resp.data
                    _captchaInfo.value = resp.data

                    if (resp.data.type == "geetest" && resp.data.gt.isNotEmpty()) {
                        // 需要滑块验证：交给 UI 弹出 GeeTestDialog
                        _smsLoginStep.value = SmsLoginStep.WaitingForCaptcha
                    } else {
                        // 不需要滑块（极少见）：直接发短信
                        sendSmsInternal(
                            GeeTestResult(resp.data.challenge, "", "")
                        )
                    }
                }
                is Result.Error -> {
                    _smsLoginStep.value = SmsLoginStep.Error(captchaResult.message)
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
            _smsLoginStep.value = SmsLoginStep.Idle
            return
        }
        geeTestResult = result
        sendSmsInternal(result)
    }

    private fun sendSmsInternal(gee: GeeTestResult) {
        val cap = captcha
        if (cap == null) {
            _smsLoginStep.value = SmsLoginStep.Error("验证码状态错误")
            return
        }

        viewModelScope.launch {
            _smsLoginStep.value = SmsLoginStep.SendingSms
            when (val result = authRepository.sendSmsCode(
                cid = pendingCid,
                tel = pendingTel,
                captcha = cap,
                geeTestResult = gee
            )) {
                is Result.Success -> {
                    val resp = result.data
                    if (resp.code == 0 && resp.data != null) {
                        smsCaptchaKey = resp.data.captchaKey
                        _smsLoginStep.value = SmsLoginStep.WaitingForSmsCode
                        startSmsCountdown()
                        _toast.value = "验证码已发送"
                    } else {
                        val msg = resp.message.ifEmpty { "发送验证码失败" }
                        _smsLoginStep.value = SmsLoginStep.Error(msg)
                        _toast.value = msg
                    }
                }
                is Result.Error -> {
                    _smsLoginStep.value = SmsLoginStep.Error(result.message)
                    _toast.value = result.message
                }
                Result.Loading -> {}
            }
        }
    }

    /** 用户输入短信验证码后调用，完成登录。 */
    fun loginWithSmsCode(code: String) {
        val cap = captcha
        val gee = geeTestResult
        val key = smsCaptchaKey
        if (cap == null || gee == null || key == null) {
            _smsLoginStep.value = SmsLoginStep.Error("请先获取验证码")
            return
        }
        if (code.isBlank()) {
            _toast.value = "请输入验证码"
            return
        }

        viewModelScope.launch {
            _smsLoginStep.value = SmsLoginStep.LoggingIn
            when (val result = authRepository.loginWithSms(
                cid = pendingCid,
                tel = pendingTel,
                code = code,
                captchaKey = key,
                captcha = cap,
                geeTestResult = gee
            )) {
                is Result.Success -> {
                    val resp = result.data
                    val data = resp.data
                    if (resp.code == 0 && data?.isLogin == true) {
                        _isLoggedIn.value = true
                        _smsLoginStep.value = SmsLoginStep.Success
                        loadUserInfo()
                    } else {
                        val msg = (data?.message?.ifEmpty { null } ?: resp.message)
                            .ifEmpty { "登录失败" }
                        _smsLoginStep.value = SmsLoginStep.Error(msg)
                        _toast.value = msg
                    }
                }
                is Result.Error -> {
                    _smsLoginStep.value = SmsLoginStep.Error(result.message)
                    _toast.value = result.message
                }
                Result.Loading -> {}
            }
        }
    }

    private fun startSmsCountdown() {
        countdownJob?.cancel()
        _smsCountdown.value = 60
        countdownJob = viewModelScope.launch {
            while (_smsCountdown.value > 0) {
                delay(1000)
                _smsCountdown.value = _smsCountdown.value - 1
            }
        }
    }

    /** 重置短信登录状态，允许用户重新尝试。 */
    fun resetSmsLogin() {
        countdownJob?.cancel()
        _smsLoginStep.value = SmsLoginStep.Idle
        _captchaInfo.value = null
        _smsCountdown.value = 0
        captcha = null
        geeTestResult = null
        smsCaptchaKey = null
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
            _smsLoginStep.value = SmsLoginStep.Idle
            _captchaInfo.value = null
            _smsCountdown.value = 0
            captcha = null
            geeTestResult = null
            smsCaptchaKey = null
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
        countdownJob?.cancel()
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

/** 短信登录状态机。 */
sealed class SmsLoginStep {
    /** 空闲：等待用户输入手机号。 */
    data object Idle : SmsLoginStep()
    /** 正在获取 GeeTest 验证码。 */
    data object LoadingCaptcha : SmsLoginStep()
    /** 等待用户完成滑块验证。 */
    data object WaitingForCaptcha : SmsLoginStep()
    /** 正在发送短信。 */
    data object SendingSms : SmsLoginStep()
    /** 短信已发送，等待用户输入验证码。 */
    data object WaitingForSmsCode : SmsLoginStep()
    /** 正在提交登录请求。 */
    data object LoggingIn : SmsLoginStep()
    /** 登录成功。 */
    data object Success : SmsLoginStep()
    /** 登录失败，等待用户重试。 */
    data class Error(val message: String) : SmsLoginStep()
}
