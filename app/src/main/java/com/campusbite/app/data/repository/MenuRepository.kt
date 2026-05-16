package com.campusbite.app.data.repository

import com.campusbite.app.data.model.MenuItem
import kotlinx.coroutines.flow.Flow

interface MenuRepository {

    /**
     * Get all menu items for a specific shop in real-time.
     */
    fun getMenuItemsByShopId(shopId: String): Flow<List<MenuItem>>

    /**
     * Add a new menu item.
     * Returns generated Firestore document ID.
     */
    suspend fun addMenuItem(menuItem: MenuItem): String

    /**
     * Update an existing menu item.
     */
    suspend fun updateMenuItem(menuItem: MenuItem)

    /**
     * Delete a menu item only if it belongs to the current shop.
     */
    suspend fun deleteMenuItem(
        shopId: String,
        itemId: String
    )

    /**
     * Update availability of a menu item.
     */
    suspend fun updateItemAvailability(
        shopId: String,
        itemId: String,
        isAvailable: Boolean
    )
}