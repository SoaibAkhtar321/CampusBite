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
                val session = result.getOrNull()

                if (session != null) {
                    _userRole.value = session.role

                    _authState.value = when (session.role) {
                        "admin" -> AuthState.AdminSuccess

                        "shopkeeper" -> {
                            if (session.isApproved) {
                                AuthState.ShopkeeperSuccess
                            } else {
                                AuthState.ShopkeeperPending
                            }
                        }

                        else -> AuthState.StudentSuccess
                    }
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

    suspend fun validateSessionForAppStart(): Boolean {
        if (authRepository.currentUser == null) {
            return false
        }

        val isFirebaseUserValid = authRepository.reloadCurrentUser()

        if (!isFirebaseUserValid) {
            logout()
            return false
        }

        val hasCompletedProfile = authRepository.hasCompletedProfile()

        if (!hasCompletedProfile) {
            logout()
            return false
        }

        return true
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

    /**
     * Single-read replacement for the splash-screen sequence of
     * hasCompletedProfile() + getUserRole() + isShopkeeperApproved().
     * Fetches users/{uid} once and returns the destination to navigate to.
     */
    suspend fun resolveStartupDestination(): StartupDestination {
        val session = authRepository.getUserSessionSnapshot()
            ?: return StartupDestination.CompleteProfile

        return when (session.role) {
            "admin" -> StartupDestination.Admin

            "shopkeeper" -> {
                if (session.isApproved) {
                    StartupDestination.ShopkeeperApproved
                } else {
                    StartupDestination.ShopkeeperPending
                }
            }

            else -> StartupDestination.Student
        }
    }
}

sealed class StartupDestination {
    object CompleteProfile : StartupDestination()
    object Admin : StartupDestination()
    object ShopkeeperApproved : StartupDestination()
    object ShopkeeperPending : StartupDestination()
    object Student : StartupDestination()
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