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
            val snapshot = firestore.collection("shops")
                .get()
                .await()

            val shops = snapshot.documents.mapNotNull { doc ->
                try {
                    Shop(
                        shopId = doc.getString("shopId") ?: doc.id,
                        name = doc.getString("name") ?: "",
                        description = doc.getString("description") ?: "",
                        imageUrl = doc.getString("imageUrl") ?: "",

                        isOpen = doc.getBoolean("isOpen") ?: false,
                        isApproved = doc.getBoolean("isApproved") ?: false,
                        isBlocked = doc.getBoolean("isBlocked") ?: false,
                        isDeleted = doc.getBoolean("isDeleted") ?: false,

                        openingTime = doc.getString("openingTime") ?: "08:00",
                        closingTime = doc.getString("closingTime") ?: "21:00",

                        maxOrdersPerSlot =
                            doc.getLong("maxOrdersPerSlot")?.toInt() ?: 5,

                        closedSlots =
                            doc.get("closedSlots") as? List<String> ?: emptyList(),

                        upiId = doc.getString("upiId") ?: "",
                        phone = doc.getString("phone")
                            ?: doc.getString("ownerPhone")
                            ?: "",

                        ownerUid = doc.getString("ownerUid") ?: "",
                        ownerEmail = doc.getString("ownerEmail") ?: "",
                        ownerPhone = doc.getString("ownerPhone") ?: "",

                        createdAt = doc.getLong("createdAt") ?: 0L
                    )
                } catch (e: Exception) {
                    null
                }
            }

            Result.success(shops)

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
}