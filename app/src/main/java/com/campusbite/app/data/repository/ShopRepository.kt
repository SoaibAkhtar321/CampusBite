package com.campusbite.app.data.repository

import com.campusbite.app.data.model.MenuItem
import com.campusbite.app.data.model.Shop
import com.google.firebase.firestore.DocumentSnapshot
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
            val snapshot = firestore.collection("shops")
                .get()
                .await()

            val shops = snapshot.documents.mapNotNull { doc ->
                try {
                    doc.toShop()
                } catch (e: Exception) {
                    null
                }
            }

            Result.success(
                shops.sortedWith(
                    compareBy<Shop> { it.displayOrder }
                        .thenBy { it.name.lowercase() }
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAllMenuItems(): Result<List<MenuItem>> {
        return try {
            val snapshot = firestore.collection("menuItems")
                .get()
                .await()

            val items = snapshot.documents.mapNotNull { doc ->
                try {
                    doc.toObject(MenuItem::class.java)
                } catch (e: Exception) {
                    null
                }
            }

            Result.success(items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMenuItemsByShop(
        shopId: String
    ): Result<List<MenuItem>> {
        return try {
            if (shopId.isBlank()) {
                return Result.success(emptyList())
            }

            val snapshot = firestore.collection("menuItems")
                .whereEqualTo("shopId", shopId)
                .get()
                .await()

            val items = snapshot.documents.mapNotNull { doc ->
                try {
                    doc.toObject(MenuItem::class.java)
                } catch (e: Exception) {
                    null
                }
            }

            Result.success(items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun DocumentSnapshot.toShop(): Shop {
        return Shop(
            shopId = id,
            name = getString("name") ?: "",
            description = getString("description") ?: "",
            imageUrl = getString("imageUrl") ?: "",

            isOpen = getBoolean("isOpen") ?: false,
            isApproved = getBoolean("isApproved") ?: false,
            isBlocked = getBoolean("isBlocked") ?: false,
            isDeleted = getBoolean("isDeleted") ?: false,

            openingTime = getString("openingTime") ?: "08:00",
            closingTime = getString("closingTime") ?: "21:00",

            upiId = getString("upiId") ?: "",
            phone = getString("phone")
                ?: getString("ownerPhone")
                ?: "",

            displayOrder = getLong("displayOrder") ?: 1000L
        )
    }
}