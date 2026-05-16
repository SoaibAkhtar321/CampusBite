package com.campusbite.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campusbite.app.data.model.MenuItem
import com.campusbite.app.data.repository.MenuRepository
import com.campusbite.app.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MenuManagementUiState(
    val isLoading: Boolean = true,
    val shopId: String = "",
    val shopName: String = "",
    val menuItems: List<MenuItem> = emptyList(),
    val error: String? = null,
    val operationInProgress: Boolean = false,
    val operationSuccess: Boolean = false
)

@HiltViewModel
class MenuManagementViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val menuRepository: MenuRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MenuManagementUiState())
    val uiState: StateFlow<MenuManagementUiState> = _uiState.asStateFlow()

    init {
        initializeShopkeeperMenu()
    }

    private fun initializeShopkeeperMenu() {
        viewModelScope.launch {
            try {
                // STEP 1: Get current user ID
                val currentUserId = userRepository.getCurrentUserId()
                    ?: throw Exception("User not authenticated")

                // STEP 2: Get shopkeeper's shop ID from user profile
                val shopId = userRepository.getShopkeeperShopId(currentUserId)
                    ?: throw Exception("Shop not found for shopkeeper")

                // STEP 3: Listen to real-time menu updates for THIS SHOP
                menuRepository.getMenuItemsByShopId(shopId)
                    .catch { exception ->
                        _uiState.value = _uiState.value.copy(
                            error = exception.message ?: "Error loading menu",
                            isLoading = false
                        )
                    }
                    .collect { menuItems ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            shopId = shopId,
                            menuItems = menuItems,
                            error = null
                        )
                    }
            } catch (exception: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = exception.message ?: "Unknown error occurred"
                )
            }
        }
    }

    fun addMenuItem(menuItem: MenuItem) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(operationInProgress = true)

                // Ensure shopId is set to current shopkeeper's shop
                val itemWithCorrectShopId = menuItem.copy(
                    shopId = _uiState.value.shopId
                )

                menuRepository.addMenuItem(itemWithCorrectShopId)

                _uiState.value = _uiState.value.copy(
                    operationInProgress = false,
                    operationSuccess = true
                )
            } catch (exception: Exception) {
                _uiState.value = _uiState.value.copy(
                    operationInProgress = false,
                    error = exception.message ?: "Failed to add menu item"
                )
            }
        }
    }

    fun updateMenuItem(menuItem: MenuItem) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(operationInProgress = true)

                // Verify item belongs to current shop
                if (menuItem.shopId != _uiState.value.shopId) {
                    throw Exception("Cannot update menu item from another shop")
                }

                menuRepository.updateMenuItem(menuItem)

                _uiState.value = _uiState.value.copy(
                    operationInProgress = false,
                    operationSuccess = true
                )
            } catch (exception: Exception) {
                _uiState.value = _uiState.value.copy(
                    operationInProgress = false,
                    error = exception.message ?: "Failed to update menu item"
                )
            }
        }
    }

    fun deleteMenuItem(itemId: String) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(operationInProgress = true)

                menuRepository.deleteMenuItem(_uiState.value.shopId, itemId)

                _uiState.value = _uiState.value.copy(
                    operationInProgress = false,
                    operationSuccess = true
                )
            } catch (exception: Exception) {
                _uiState.value = _uiState.value.copy(
                    operationInProgress = false,
                    error = exception.message ?: "Failed to delete menu item"
                )
            }
        }
    }

    fun toggleItemAvailability(itemId: String, isAvailable: Boolean) {
        viewModelScope.launch {
            try {
                menuRepository.updateItemAvailability(
                    shopId = _uiState.value.shopId,
                    itemId = itemId,
                    isAvailable = isAvailable
                )
            } catch (exception: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = exception.message ?: "Failed to update availability"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearSuccess() {
        _uiState.value = _uiState.value.copy(operationSuccess = false)
    }
}
