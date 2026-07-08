package com.creatix.chatapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creatix.chatapp.repository.AuthRepository
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
    private val repository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState

    val isLoggedIn: Boolean get() = repository.isLoggedIn()
    val currentUid: String? get() = repository.currentUser?.uid

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _authState.value = AuthState.Error("من فضلك اكتب الإيميل والباسورد")
            return
        }
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            val result = repository.login(email.trim(), password)
            _authState.value = result.fold(
                onSuccess = { AuthState.Success },
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
                onSuccess = { AuthState.Success },
                onFailure = { AuthState.Error(it.message ?: "حصل خطأ في إنشاء الحساب") }
            )
        }
    }

    fun logout() {
        repository.logout()
        _authState.value = AuthState.Idle
    }
}
