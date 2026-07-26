package com.biliaudio.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.biliaudio.data.model.UserInfo
import com.biliaudio.data.preferences.PreferencesManager
import com.biliaudio.data.repository.BiliRepository
import com.biliaudio.data.network.QrCodeStatusData
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.Cookie
import okhttp3.HttpUrl

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BiliRepository()
    private val preferencesManager = PreferencesManager(application)

    private val _qrCodeUrl = MutableStateFlow<String?>(null)
    val qrCodeUrl: StateFlow<String?> = _qrCodeUrl.asStateFlow()

    private val _qrCodeKey = MutableStateFlow<String?>(null)

    private val _loginStatus = MutableStateFlow<LoginStatus>(LoginStatus.Idle)
    val loginStatus: StateFlow<LoginStatus> = _loginStatus.asStateFlow()

    private val _userInfo = MutableStateFlow<UserInfo?>(null)
    val userInfo: StateFlow<UserInfo?> = _userInfo.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private var pollJob: Job? = null

    init {
        viewModelScope.launch {
            preferencesManager.cookies.collect { cookies ->
                if (cookies.isNotEmpty()) {
                    _isLoggedIn.value = true
                    loadUserInfo()
                }
            }
        }
    }

    fun generateQrCode() {
        viewModelScope.launch {
            _loginStatus.value = LoginStatus.Loading
            try {
                val response = repository.generateQrCode()
                if (response.code == 0 && response.data != null) {
                    _qrCodeUrl.value = response.data.url
                    _qrCodeKey.value = response.data.qrcodeKey
                    _loginStatus.value = LoginStatus.QrCodeReady
                    startPolling()
                } else {
                    _loginStatus.value = LoginStatus.Error(response.message)
                }
            } catch (e: Exception) {
                _loginStatus.value = LoginStatus.Error(e.message ?: "Unknown error")
            }
        }
    }

    private fun startPolling() {
        pollJob?.cancel()
        val qrKey = _qrCodeKey.value ?: return

        repository.setCookieListener { cookies ->
            viewModelScope.launch {
                val cookieStr = cookies.joinToString("; ") { "${it.name}=${it.value}" }
                preferencesManager.saveCookies(cookieStr)
                _isLoggedIn.value = true
                loadUserInfo()
            }
        }

        pollJob = viewModelScope.launch {
            while (true) {
                try {
                    val response = repository.checkQrCodeStatus(qrKey)
                    when (response.data?.code) {
                        86101 -> {
                            _loginStatus.value = LoginStatus.Expired
                            break
                        }
                        0 -> {
                            _loginStatus.value = LoginStatus.Success
                            break
                        }
                        86090 -> {
                            _loginStatus.value = LoginStatus.Scanned
                        }
                        else -> {
                            _loginStatus.value = LoginStatus.QrCodeReady
                        }
                    }
                } catch (e: Exception) {
                    // ignore
                }
                delay(3000)
            }
        }
    }

    fun loadUserInfo() {
        viewModelScope.launch {
            try {
                val response = repository.getUserInfo()
                if (response.code == 0 && response.data != null) {
                    _userInfo.value = response.data
                    preferencesManager.saveUserInfo(
                        id = response.data.mid.toString(),
                        name = response.data.name,
                        avatar = response.data.face
                    )
                }
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.clearCookies()
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
