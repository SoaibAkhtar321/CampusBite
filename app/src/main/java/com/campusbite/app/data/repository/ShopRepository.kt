package com.campusbite.app.data.repository

import com.campusbite.app.data.model.MenuItem
import com.campusbite.app.data.model.Shop
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShopRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    suspend fun getAllShops(): Result<List<Shop>> {
        return try {
            val snapshot = firestore.collection("shops").get().await()
            val shops = snapshot.documents.mapNotNull { it.toObject(Shop::class.java) }
            Result.success(shops)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAllMenuItems(): Result<List<MenuItem>> {
        return try {
            val snapshot = firestore.collection("menuItems").get().await()
            val items = snapshot.documents.mapNotNull { it.toObject(MenuItem::class.java) }
            Result.success(items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMenuItemsByShop(shopId: String): Result<List<MenuItem>> {
        return try {
            val snapshot = firestore.collection("menuItems")
                .whereEqualTo("shopId", shopId)
                .get().await()
            val items = snapshot.documents.mapNotNull { it.toObject(MenuItem::class.java) }
            Result.success(items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}