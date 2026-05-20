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

    fun login(
        email: String,
        password: String
    ) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading

            val result = authRepository.login(
                email = email,
                password = password
            )

            if (result.isSuccess) {
                navigateByUserRole()
            } else {
                _authState.value = AuthState.Error(
                    result.exceptionOrNull()?.message ?: "Login failed"
                )
            }
        }
    }

    fun register(
        name: String,
        email: String,
        phone: String,
        password: String,
        role: String,
        university: String,
        universityId: String
    ) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading

            val result = authRepository.register(
                name = name,
                email = email,
                phone = phone,
                password = password,
                role = role,
                university = university,
                universityId = universityId
            )

            _authState.value = if (result.isSuccess) {
                AuthState.EmailVerificationSent
            } else {
                AuthState.Error(
                    result.exceptionOrNull()?.message ?: "Registration failed"
                )
            }
        }
    }

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
                    result.exceptionOrNull()?.message
                        ?: "Google sign-in failed"
                )
            }
        }
    }

    fun completeGoogleProfile(
        phone: String,
        role: String,
        university: String,
        universityId: String
    ) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading

            val result = authRepository.completeGoogleProfile(
                phone = phone,
                role = role,
                university = university,
                universityId = universityId
            )

            _authState.value = if (result.isSuccess) {

                when (role.trim().lowercase()) {
                    "shopkeeper" -> AuthState.ShopkeeperPending
                    else -> AuthState.StudentSuccess
                }

            } else {
                AuthState.Error(
                    result.exceptionOrNull()?.message
                        ?: "Profile setup failed"
                )
            }
        }
    }

    private suspend fun navigateByUserRole() {

        val isBlocked = authRepository.isUserBlocked()

        if (isBlocked) {
            authRepository.logout()

            _authState.value = AuthState.Error(
                "Your account has been blocked by admin."
            )

            return
        }

        val role = authRepository.getUserRole()
        val isApproved = authRepository.isShopkeeperApproved()

        _userRole.value = role

        _authState.value = when (role) {

            "admin" -> {
                AuthState.AdminSuccess
            }

            "shopkeeper" -> {

                if (isApproved) {
                    AuthState.ShopkeeperSuccess
                } else {
                    AuthState.ShopkeeperPending
                }
            }

            else -> {
                AuthState.StudentSuccess
            }
        }
    }
    fun resendVerificationEmail(
        email: String,
        password: String
    ) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading

            val result = authRepository.resendVerificationEmail(
                email = email,
                password = password
            )

            _authState.value = if (result.isSuccess) {
                AuthState.EmailVerificationSent
            } else {
                AuthState.Error(
                    result.exceptionOrNull()?.message ?: "Failed to resend verification email"
                )
            }
        }
    }
    fun completeEmailRegistration(
        name: String,
        email: String,
        phone: String,
        password: String,
        role: String,
        university: String,
        universityId: String
    ) {

        viewModelScope.launch {

            _authState.value = AuthState.Loading

            val result = authRepository.completeEmailRegistration(
                name = name,
                email = email,
                phone = phone,
                password = password,
                role = role,
                university = university,
                universityId = universityId
            )

            _authState.value = if (result.isSuccess) {

                AuthState.EmailRegistrationCompleted

            } else {

                AuthState.Error(
                    result.exceptionOrNull()?.message
                        ?: "Verification failed"
                )
            }
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
}

sealed class AuthState {

    object Idle : AuthState()

    object Loading : AuthState()

    object StudentSuccess : AuthState()

    object ShopkeeperSuccess : AuthState()

    object ShopkeeperPending : AuthState()

    object AdminSuccess : AuthState()

    object GoogleProfileRequired : AuthState()

    object EmailVerificationSent : AuthState()

    object EmailRegistrationCompleted : AuthState()

    data class Error(
        val message: String
    ) : AuthState()
}