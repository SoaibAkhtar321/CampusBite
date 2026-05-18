package com.campusbite.app.data.repository

import android.util.Log
import com.campusbite.app.data.model.MenuItem
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class MenuRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : MenuRepository {

    override fun getMenuItemsByShopId(shopId: String): Flow<List<MenuItem>> = callbackFlow {
        Log.d("MenuRepo", "Setting up listener for shopId: $shopId")

        val listener = firestore.collection("menuItems")
            .whereEqualTo("shopId", shopId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("MenuRepo", "Listener error: ${error.message}", error)
                    close(error)
                    return@addSnapshotListener
                }

                val items = snapshot?.documents
                    ?.mapNotNull { doc ->
                        documentToMenuItem(doc)
                    }
                    ?.sortedWith(
                        compareBy<MenuItem> { it.category.lowercase() }
                            .thenBy { it.name.lowercase() }
                    )
                    ?: emptyList()

                items.forEach { item ->
                    Log.d(
                        "MenuRepo",
                        "Loaded item: ${item.name}, itemId=${item.itemId}, shopId=${item.shopId}, isAvailable=${item.isAvailable}"
                    )
                }

                Log.d("MenuRepo", "Listener fired: ${items.size} items for shopId: $shopId")

                trySend(items).isSuccess
            }

        awaitClose {
            listener.remove()
        }
    }

    override suspend fun addMenuItem(menuItem: MenuItem): String {
        return try {
            if (menuItem.shopId.isBlank()) {
                throw Exception("Cannot add item without shopId!")
            }

            val docRef = firestore.collection("menuItems").document()

            val itemMap = mapOf(
                "itemId" to docRef.id,
                "shopId" to menuItem.shopId,
                "name" to menuItem.name,
                "price" to menuItem.price,
                "prepTimeMinutes" to menuItem.prepTimeMinutes,
                "category" to menuItem.category,
                "isAvailable" to menuItem.isAvailable,
                "imageUrl" to menuItem.imageUrl
            )

            Log.d("MenuRepo", "Adding item: ${menuItem.name}")
            Log.d("MenuRepo", "itemId: ${docRef.id}")
            Log.d("MenuRepo", "shopId: ${menuItem.shopId}")
            Log.d("MenuRepo", "isAvailable: ${menuItem.isAvailable}")

            docRef.set(itemMap).await()

            Log.d("MenuRepo", "Item added successfully")

            docRef.id

        } catch (exception: Exception) {
            Log.e("MenuRepo", "Failed to add menu item", exception)
            throw Exception("Failed to add menu item: ${exception.message}")
        }
    }

    override suspend fun updateMenuItem(menuItem: MenuItem) {
        try {
            if (menuItem.itemId.isBlank()) {
                throw Exception("Cannot update item without itemId!")
            }

            if (menuItem.shopId.isBlank()) {
                throw Exception("Cannot update item without shopId!")
            }

            val itemMap = mapOf(
                "itemId" to menuItem.itemId,
                "shopId" to menuItem.shopId,
                "name" to menuItem.name,
                "price" to menuItem.price,
                "prepTimeMinutes" to menuItem.prepTimeMinutes,
                "category" to menuItem.category,
                "isAvailable" to menuItem.isAvailable,
                "imageUrl" to menuItem.imageUrl
            )

            Log.d("MenuRepo", "Updating item: ${menuItem.name}")
            Log.d("MenuRepo", "itemId: ${menuItem.itemId}")
            Log.d("MenuRepo", "shopId: ${menuItem.shopId}")
            Log.d("MenuRepo", "isAvailable: ${menuItem.isAvailable}")

            firestore.collection("menuItems")
                .document(menuItem.itemId)
                .set(itemMap)
                .await()

            Log.d("MenuRepo", "Item updated successfully")

        } catch (exception: Exception) {
            Log.e("MenuRepo", "Failed to update menu item", exception)
            throw Exception("Failed to update menu item: ${exception.message}")
        }
    }

    override suspend fun deleteMenuItem(
        shopId: String,
        itemId: String
    ) {
        try {
            if (shopId.isBlank()) {
                throw Exception("Cannot delete item without shopId!")
            }

            if (itemId.isBlank()) {
                throw Exception("Cannot delete item without itemId!")
            }

            val itemDoc = firestore.collection("menuItems")
                .document(itemId)
                .get()
                .await()

            if (!itemDoc.exists()) {
                throw Exception("Menu item does not exist!")
            }

            val itemShopId = itemDoc.getString("shopId")

            if (itemShopId != shopId) {
                throw Exception(
                    "Cannot delete item from another shop! Item shopId: $itemShopId, current shopId: $shopId"
                )
            }

            firestore.collection("menuItems")
                .document(itemId)
                .delete()
                .await()

            Log.d("MenuRepo", "Item deleted successfully")

        } catch (exception: Exception) {
            Log.e("MenuRepo", "Failed to delete menu item", exception)
            throw Exception("Failed to delete menu item: ${exception.message}")
        }
    }

    override suspend fun updateItemAvailability(
        shopId: String,
        itemId: String,
        isAvailable: Boolean
    ) {
        try {
            if (shopId.isBlank()) {
                throw Exception("Cannot update availability without shopId!")
            }

            if (itemId.isBlank()) {
                throw Exception("Cannot update availability without itemId!")
            }

            Log.d("MenuRepo", "Updating availability")
            Log.d("MenuRepo", "shopId: $shopId")
            Log.d("MenuRepo", "itemId: $itemId")
            Log.d("MenuRepo", "new isAvailable: $isAvailable")

            val itemDoc = firestore.collection("menuItems")
                .document(itemId)
                .get()
                .await()

            if (!itemDoc.exists()) {
                throw Exception("Menu item does not exist!")
            }

            val itemShopId = itemDoc.getString("shopId")

            if (itemShopId != shopId) {
                throw Exception(
                    "Cannot update item from another shop! Item shopId: $itemShopId, current shopId: $shopId"
                )
            }

            firestore.collection("menuItems")
                .document(itemId)
                .update(
                    mapOf(
                        "isAvailable" to isAvailable
                    )
                )
                .await()

            Log.d("MenuRepo", "Availability updated successfully to: $isAvailable")

        } catch (exception: Exception) {
            Log.e("MenuRepo", "Failed to update availability", exception)
            throw Exception("Failed to update availability: ${exception.message}")
        }
    }

    private fun documentToMenuItem(doc: DocumentSnapshot): MenuItem? {
        return try {
            val itemId = doc.getString("itemId") ?: doc.id
            val shopId = doc.getString("shopId") ?: ""
            val name = doc.getString("name") ?: ""
            val price = doc.getDouble("price") ?: 0.0
            val prepTimeMinutes = doc.getLong("prepTimeMinutes")?.toInt() ?: 0
            val category = doc.getString("category") ?: ""
            val imageUrl = doc.getString("imageUrl") ?: ""

            val isAvailable = doc.getBoolean("isAvailable") ?: true

            MenuItem(
                itemId = itemId,
                shopId = shopId,
                name = name,
                price = price,
                prepTimeMinutes = prepTimeMinutes,
                category = category,
                isAvailable = isAvailable,
                imageUrl = imageUrl
            )

        } catch (e: Exception) {
            Log.e("MenuRepo", "Failed to parse menu item: ${doc.id}", e)
            null
        }
    }
}