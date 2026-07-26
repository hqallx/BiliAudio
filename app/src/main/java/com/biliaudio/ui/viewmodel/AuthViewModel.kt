package com.biliaudio.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.biliaudio.data.Result
import com.biliaudio.data.model.UserInfo
import com.biliaudio.data.network.CookieHelper
import com.biliaudio.data.preferences.PreferencesManager
import com.biliaudio.data.repository.AuthRepository
import com.biliaudio.data.network.QrCodeStatusData
import com.biliaudio.data.BiliConstants
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
    application: Application,
    private val authRepository: AuthRepository,
    private val preferencesManager: PreferencesManager
) : AndroidViewModel(application) {

    private val _qrCodeUrl = MutableStateFlow<String?>(null)
    val qrCodeUrl: StateFlow<String?> = _qrCodeUrl.asStateFlow()

    private val _qrCodeKey = MutableStateFlow<String?>(null)

    private val _loginStatus = MutableStateFlow<LoginStatus>(LoginStatus.Idle)
    val loginStatus: StateFlow<LoginStatus> = _loginStatus.asStateFlow()

    private val _userInfo = MutableStateFlow<UserInfo?>(null)
    val userInfo: StateFlow<UserInfo?> = _userInfo.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

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
