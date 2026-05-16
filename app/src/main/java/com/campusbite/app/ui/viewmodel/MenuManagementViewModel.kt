package com.campusbite.app.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campusbite.app.data.model.MenuItem
import com.campusbite.app.data.repository.MenuRepository
import com.campusbite.app.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MenuManagementUiState(
    val isLoading: Boolean = false,
    val menuItems: List<MenuItem> = emptyList(),
    val shopId: String = "",
    val errorMessage: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class MenuManagementViewModel @Inject constructor(
    private val menuRepository: MenuRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MenuManagementUiState(isLoading = true))
    val uiState: StateFlow<MenuManagementUiState> = _uiState.asStateFlow()

    init {
        initializeMenuManagement()
    }

    private fun initializeMenuManagement() {
        viewModelScope.launch {
            try {
                val currentUserId = userRepository.getCurrentUserId()

                Log.d("MenuVM", "Current UID: $currentUserId")

                if (currentUserId.isNullOrBlank()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "User not logged in"
                    )
                    return@launch
                }

                val shopId = userRepository.getShopkeeperShopId(currentUserId)

                Log.d("MenuVM", "Actual shopId from user document: $shopId")

                if (shopId.isNullOrBlank()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Shop ID not found for this shopkeeper"
                    )
                    return@launch
                }

                _uiState.value = _uiState.value.copy(
                    shopId = shopId,
                    isLoading = false,
                    errorMessage = null
                )

                observeMenuItems(shopId)

            } catch (e: Exception) {
                Log.e("MenuVM", "Error initializing menu management", e)

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Something went wrong"
                )
            }
        }
    }

    private fun observeMenuItems(shopId: String) {
        viewModelScope.launch {
            try {
                menuRepository.getMenuItemsByShopId(shopId).collect { items ->
                    _uiState.value = _uiState.value.copy(
                        menuItems = items,
                        isLoading = false,
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                Log.e("MenuVM", "Error observing menu items", e)

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Failed to load menu items"
                )
            }
        }
    }

    fun addMenuItem(menuItem: MenuItem) {
        viewModelScope.launch {
            try {
                val correctShopId = _uiState.value.shopId

                if (correctShopId.isBlank()) {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "Cannot add item. Shop ID missing."
                    )
                    return@launch
                }

                val fixedItem = menuItem.copy(
                    shopId = correctShopId,
                    isAvailable = menuItem.isAvailable
                )

                Log.d("MenuVM", "Adding item: ${fixedItem.name}")
                Log.d("MenuVM", "Correct shopId used: ${fixedItem.shopId}")
                Log.d("MenuVM", "Availability used: ${fixedItem.isAvailable}")

                menuRepository.addMenuItem(fixedItem)

                _uiState.value = _uiState.value.copy(
                    successMessage = "Menu item added successfully",
                    errorMessage = null
                )

            } catch (e: Exception) {
                Log.e("MenuVM", "Error adding menu item", e)

                _uiState.value = _uiState.value.copy(
                    errorMessage = e.message ?: "Failed to add menu item"
                )
            }
        }
    }

    fun updateMenuItem(menuItem: MenuItem) {
        viewModelScope.launch {
            try {
                val correctShopId = _uiState.value.shopId

                if (correctShopId.isBlank()) {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "Cannot update item. Shop ID missing."
                    )
                    return@launch
                }

                if (menuItem.itemId.isBlank()) {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "Cannot update item. Item ID missing."
                    )
                    return@launch
                }

                val fixedItem = menuItem.copy(
                    shopId = correctShopId
                )

                Log.d("MenuVM", "Updating item: ${fixedItem.name}")
                Log.d("MenuVM", "Item ID: ${fixedItem.itemId}")
                Log.d("MenuVM", "Correct shopId used: ${fixedItem.shopId}")

                menuRepository.updateMenuItem(fixedItem)

                _uiState.value = _uiState.value.copy(
                    successMessage = "Menu item updated successfully",
                    errorMessage = null
                )

            } catch (e: Exception) {
                Log.e("MenuVM", "Error updating menu item", e)

                _uiState.value = _uiState.value.copy(
                    errorMessage = e.message ?: "Failed to update menu item"
                )
            }
        }
    }

    fun deleteMenuItem(itemId: String) {
        viewModelScope.launch {
            try {
                val currentShopId = _uiState.value.shopId

                if (currentShopId.isBlank()) {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "Cannot delete item. Shop ID missing."
                    )
                    return@launch
                }

                if (itemId.isBlank()) {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "Cannot delete item. Item ID missing."
                    )
                    return@launch
                }

                Log.d("MenuVM", "Deleting itemId: $itemId")
                Log.d("MenuVM", "Current shopId: $currentShopId")

                menuRepository.deleteMenuItem(
                    shopId = currentShopId,
                    itemId = itemId
                )

                _uiState.value = _uiState.value.copy(
                    successMessage = "Menu item deleted successfully",
                    errorMessage = null
                )

            } catch (e: Exception) {
                Log.e("MenuVM", "Error deleting menu item", e)

                _uiState.value = _uiState.value.copy(
                    errorMessage = e.message ?: "Failed to delete menu item"
                )
            }
        }
    }

    fun updateItemAvailability(
        itemId: String,
        isAvailable: Boolean
    ) {
        viewModelScope.launch {
            try {
                val currentShopId = _uiState.value.shopId

                if (currentShopId.isBlank()) {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "Cannot update availability. Shop ID missing."
                    )
                    return@launch
                }

                if (itemId.isBlank()) {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "Cannot update availability. Item ID missing."
                    )
                    return@launch
                }

                Log.d("MenuVM", "Updating availability for itemId: $itemId")
                Log.d("MenuVM", "Current shopId: $currentShopId")
                Log.d("MenuVM", "New isAvailable: $isAvailable")

                menuRepository.updateItemAvailability(
                    shopId = currentShopId,
                    itemId = itemId,
                    isAvailable = isAvailable
                )

                _uiState.value = _uiState.value.copy(
                    successMessage = if (isAvailable) {
                        "Item marked as available"
                    } else {
                        "Item marked as unavailable"
                    },
                    errorMessage = null
                )

            } catch (e: Exception) {
                Log.e("MenuVM", "Error updating item availability", e)

                _uiState.value = _uiState.value.copy(
                    errorMessage = e.message ?: "Failed to update availability"
                )
            }
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(
            errorMessage = null,
            successMessage = null
        )
    }
}