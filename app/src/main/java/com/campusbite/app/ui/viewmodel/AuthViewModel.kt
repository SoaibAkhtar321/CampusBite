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

    private val _userRole = MutableStateFlow("student")
    val userRole: StateFlow<String> = _userRole

    val isLoggedIn: Boolean
        get() = authRepository.currentUser != null

    val googleName: String
        get() = authRepository.currentUser?.displayName.orEmpty()

    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading

            val result = authRepository.signInWithGoogle(idToken)

            if (result.isSuccess) {
                val profileExists = result.getOrNull() ?: false

                if (profileExists) {
                    navigateByUserRole()
                } else {
                    _authState.value = AuthState.GoogleProfileRequired
                }
            } else {
                _authState.value = AuthState.Error(
                    result.exceptionOrNull()?.message ?: "Google sign-in failed"
                )
            }
        }
    }

    fun completeGoogleProfile(
        name: String,
        phone: String,
        role: String,
        university: String,
        universityId: String
    ) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading

            val result = authRepository.completeGoogleProfile(
                name = name,
                phone = phone,
                role = role,
                university = university,
                universityId = universityId
            )

            if (result.isSuccess) {
                val cleanRole = role.trim().lowercase()

                _authState.value = if (cleanRole == "shopkeeper") {
                    AuthState.ShopkeeperPending
                } else {
                    AuthState.StudentSuccess
                }
            } else {
                _authState.value = AuthState.Error(
                    result.exceptionOrNull()?.message ?: "Profile setup failed"
                )
            }
        }
    }

    private suspend fun navigateByUserRole() {
        val isBlocked = authRepository.isUserBlocked()

        if (isBlocked) {
            _authState.value = AuthState.Blocked
            return
        }

        val role = authRepository.getUserRole()
        val isApproved = authRepository.isShopkeeperApproved()

        _userRole.value = role

        _authState.value = when (role) {
            "admin" -> AuthState.AdminSuccess

            "shopkeeper" -> {
                if (isApproved) {
                    AuthState.ShopkeeperSuccess
                } else {
                    AuthState.ShopkeeperPending
                }
            }

            else -> AuthState.StudentSuccess
        }
    }

    fun logout() {
        authRepository.logout()
        _authState.value = AuthState.Idle
        _userRole.value = "student"
    }

    fun resetState() {
        _authState.value = AuthState.Idle
    }

    suspend fun getUserRole(): String {
        return authRepository.getUserRole()
    }

    suspend fun isShopkeeperApproved(): Boolean {
        return authRepository.isShopkeeperApproved()
    }

    fun checkUserRole() {
        viewModelScope.launch {
            _userRole.value = authRepository.getUserRole()
        }
    }
    suspend fun hasCompletedProfile(): Boolean {
        return authRepository.hasCompletedProfile()
    }
}

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()

    object StudentSuccess : AuthState()
    object ShopkeeperSuccess : AuthState()
    object ShopkeeperPending : AuthState()
    object AdminSuccess : AuthState()
    object Blocked : AuthState()

    object GoogleProfileRequired : AuthState()

    data class Error(
        val message: String
    ) : AuthState()
}