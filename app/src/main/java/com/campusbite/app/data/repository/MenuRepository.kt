package com.campusbite.app.data.repository

import com.campusbite.app.data.model.MenuItem
import kotlinx.coroutines.flow.Flow

interface MenuRepository {
    // Get all menu items for a specific shop (real-time listener)
    fun getMenuItemsByShopId(shopId: String): Flow<List<MenuItem>>

    // CRUD operations
    suspend fun addMenuItem(menuItem: MenuItem): String
    suspend fun updateMenuItem(menuItem: MenuItem)
    suspend fun deleteMenuItem(shopId: String, itemId: String)

    // Toggle item availability
    suspend fun updateItemAvailability(shopId: String, itemId: String, isAvailable: Boolean)
}
