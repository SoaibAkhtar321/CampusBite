package com.campusbite.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campusbite.app.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState

    val isLoggedIn get() = authRepository.currentUser != null

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = authRepository.login(email, password)
            if (result.isSuccess) {
                val role = authRepository.getUserRole()
                val isApproved = authRepository.isShopkeeperApproved()   // ✅ NEW
                _authState.value = when (role) {
                    "admin" -> AuthState.AdminSuccess
                    "shopkeeper" -> if (isApproved) AuthState.ShopkeeperSuccess else AuthState.ShopkeeperPending
                    else -> AuthState.StudentSuccess
                }
            } else {
                _authState.value = AuthState.Error(
                    result.exceptionOrNull()?.message ?: "Login failed"
                )
            }
        }
    }

    fun register(name: String, email: String, password: String, role: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = authRepository.register(name, email, password, role) // ✅ updated
            _authState.value = if (result.isSuccess) {
                if (role == "shopkeeper") AuthState.ShopkeeperPending else AuthState.StudentSuccess
            } else {
                AuthState.Error(result.exceptionOrNull()?.message ?: "Registration failed")
            }
        }
    }

    fun logout() {
        authRepository.logout()
        _authState.value = AuthState.Idle
    }

    fun resetState() {
        _authState.value = AuthState.Idle
    }

    suspend fun getUserRole(): String = authRepository.getUserRole()
    suspend fun isShopkeeperApproved(): Boolean = authRepository.isShopkeeperApproved()

    private val _userRole = MutableStateFlow("student")
    val userRole: StateFlow<String> = _userRole

    fun checkUserRole() {
        viewModelScope.launch {
            _userRole.value = authRepository.getUserRole()
        }
    }
}

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    object StudentSuccess : AuthState()
    object ShopkeeperSuccess : AuthState()
    object ShopkeeperPending : AuthState()   // ✅ NEW
    object AdminSuccess : AuthState()
    data class Error(val message: String) : AuthState()
}