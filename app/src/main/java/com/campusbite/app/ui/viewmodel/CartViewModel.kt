package com.campusbite.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.campusbite.app.data.model.MenuItem
import com.campusbite.app.data.model.OrderItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class CartViewModel @Inject constructor() : ViewModel() {

    private val _cartItems = MutableStateFlow<List<OrderItem>>(emptyList())
    val cartItems: StateFlow<List<OrderItem>> = _cartItems

    private val _currentShopId = MutableStateFlow<String?>(null)
    val currentShopId: StateFlow<String?> = _currentShopId

    private val _showShopConflict = MutableStateFlow(false)
    val showShopConflict: StateFlow<Boolean> = _showShopConflict

    private var pendingItem: MenuItem? = null

    val totalPrice: Double
        get() = _cartItems.value.sumOf { it.price * it.quantity }

    val itemCount: Int
        get() = _cartItems.value.sumOf { it.quantity }

    fun addItem(menuItem: MenuItem) {
        if (_currentShopId.value != null && _currentShopId.value != menuItem.shopId) {
            pendingItem = menuItem
            _showShopConflict.value = true
            return
        }

        val currentItems = _cartItems.value.toMutableList()
        val existingItem = currentItems.find { it.itemId == menuItem.itemId }

        if (existingItem != null) {
            val index = currentItems.indexOf(existingItem)
            currentItems[index] = existingItem.copy(quantity = existingItem.quantity + 1)
        } else {
            currentItems.add(
                OrderItem(
                    itemId = menuItem.itemId,
                    name = menuItem.name,
                    price = menuItem.price,
                    quantity = 1,
                    prepTimeMinutes = menuItem.prepTimeMinutes,
                    shopId = menuItem.shopId,
                    cookingNote = ""
                )
            )
        }

        _cartItems.value = currentItems
        _currentShopId.value = menuItem.shopId
    }

    // Update cooking preference note for a specific item
    fun updateCookingNote(itemId: String, note: String) {
        val currentItems = _cartItems.value.toMutableList()
        val index = currentItems.indexOfFirst { it.itemId == itemId }
        if (index != -1) {
            currentItems[index] = currentItems[index].copy(cookingNote = note)
            _cartItems.value = currentItems
        }
    }

    fun confirmClearCartAndAdd() {
        clearCart()
        pendingItem?.let { addItem(it) }
        pendingItem = null
        _showShopConflict.value = false
    }

    fun dismissShopConflict() {
        pendingItem = null
        _showShopConflict.value = false
    }

    fun removeItem(itemId: String) {
        val currentItems = _cartItems.value.toMutableList()
        val existingItem = currentItems.find { it.itemId == itemId }

        if (existingItem != null) {
            if (existingItem.quantity > 1) {
                val index = currentItems.indexOf(existingItem)
                currentItems[index] = existingItem.copy(quantity = existingItem.quantity - 1)
            } else {
                currentItems.remove(existingItem)
            }
        }

        if (currentItems.isEmpty()) _currentShopId.value = null
        _cartItems.value = currentItems
    }

    fun clearCart() {
        _cartItems.value = emptyList()
        _currentShopId.value = null
    }

    fun isItemInCart(itemId: String): Boolean =
        _cartItems.value.any { it.itemId == itemId }

    fun getItemQuantity(itemId: String): Int =
        _cartItems.value.find { it.itemId == itemId }?.quantity ?: 0

    fun isDifferentShop(shopId: String): Boolean =
        _currentShopId.value != null && _currentShopId.value != shopId
}
