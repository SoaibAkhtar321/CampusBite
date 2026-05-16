package com.campusbite.app.data.repository

import com.campusbite.app.data.model.MenuItem
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.toObject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class MenuRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : MenuRepository {

    override fun getMenuItemsByShopId(shopId: String): Flow<List<MenuItem>> = callbackFlow {
        // ✅ CRITICAL: Real-time listener with shopId filter for isolation
        val listener = firestore.collection("menuItems")
            .whereEqualTo("shopId", shopId)
            .orderBy("category")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val items = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject<MenuItem>()?.copy(itemId = doc.id)
                } ?: emptyList()

                trySend(items).isSuccess
            }

        awaitClose { listener.remove() }
    }

    override suspend fun addMenuItem(menuItem: MenuItem): String {
        return try {
            val docRef = firestore.collection("menuItems")
                .add(menuItem.toMap())
                .await()

            docRef.id
        } catch (exception: Exception) {
            throw Exception("Failed to add menu item: ${exception.message}")
        }
    }

    override suspend fun updateMenuItem(menuItem: MenuItem) {
        try {
            firestore.collection("menuItems")
                .document(menuItem.itemId)
                .update(menuItem.toMap())
                .await()
        } catch (exception: Exception) {
            throw Exception("Failed to update menu item: ${exception.message}")
        }
    }

    override suspend fun deleteMenuItem(shopId: String, itemId: String) {
        try {
            // Verify item belongs to this shop before deleting
            val item = firestore.collection("menuItems")
                .document(itemId)
                .get()
                .await()

            val itemShopId = item.getString("shopId")
            if (itemShopId != shopId) {
                throw Exception("Cannot delete menu item from another shop")
            }

            firestore.collection("menuItems")
                .document(itemId)
                .delete()
                .await()
        } catch (exception: Exception) {
            throw Exception("Failed to delete menu item: ${exception.message}")
        }
    }

    override suspend fun updateItemAvailability(
        shopId: String,
        itemId: String,
        isAvailable: Boolean
    ) {
        try {
            firestore.collection("menuItems")
                .document(itemId)
                .update("isAvailable", isAvailable)
                .await()
        } catch (exception: Exception) {
            throw Exception("Failed to update availability: ${exception.message}")
        }
    }

    private fun MenuItem.toMap(): Map<String, Any> = mapOf(
        "shopId" to shopId,
        "name" to name,
        "category" to category,
        "price" to price,
        "prepTimeMinutes" to prepTimeMinutes,
        "isAvailable" to isAvailable,
        "imageUrl" to imageUrl
    )
}
