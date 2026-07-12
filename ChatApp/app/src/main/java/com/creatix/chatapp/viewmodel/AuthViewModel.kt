package com.creatix.chatapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creatix.chatapp.repository.AuthRepository
import com.creatix.chatapp.repository.PresenceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    object Success : AuthState()
    data class Error(val message: String) : AuthState()
}

class AuthViewModel(
    private val repository: AuthRepository = AuthRepository(),
    private val presenceRepository: PresenceRepository = PresenceRepository()
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState

    val isLoggedIn: Boolean get() = repository.isLoggedIn()
    val currentUid: String? get() = repository.currentUser?.uid
    val currentDisplayName: String? get() = repository.currentUser?.displayName

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _authState.value = AuthState.Error("من فضلك اكتب الإيميل والباسورد")
            return
        }
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            val result = repository.login(email.trim(), password)
            _authState.value = result.fold(
                onSuccess = {
                    presenceRepository.goOnline()
                    AuthState.Success
                },
                onFailure = { AuthState.Error(it.message ?: "حصل خطأ في تسجيل الدخول") }
            )
        }
    }

    fun register(email: String, password: String, displayName: String, photoUrl: String = "", bio: String = "") {
        if (email.isBlank() || password.length < 6 || displayName.isBlank()) {
            _authState.value = AuthState.Error("تأكد إن الاسم مكتوب والباسورد 6 حروف على الأقل")
            return
        }
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            val result = repository.register(email.trim(), password, displayName.trim(), photoUrl.trim(), bio.trim())
            _authState.value = result.fold(
                onSuccess = {
                    presenceRepository.goOnline()
                    AuthState.Success
                },
                onFailure = { AuthState.Error(it.message ?: "حصل خطأ في إنشاء الحساب") }
            )
        }
    }

    fun logout() {
        presenceRepository.goOffline()
        repository.logout()
        _authState.value = AuthState.Idle
    }

    /** بتتنادى من MainActivity وقت ما التطبيق يفتح في المقدمة (true) أو يروح للخلفية (false) */
    fun setOnlinePresence(online: Boolean) {
        if (online) presenceRepository.goOnline() else presenceRepository.goOffline()
    }
}
