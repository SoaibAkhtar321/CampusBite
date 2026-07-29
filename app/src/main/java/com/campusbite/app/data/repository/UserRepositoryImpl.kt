package com.campusbite.app.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class UserRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : UserRepository {

    override fun getCurrentUserId(): String? {
        return auth.currentUser?.uid
    }

    override suspend fun getShopkeeperShopId(userId: String): String? {
        return try {
            if (userId.isBlank()) {
                Log.e("UserRepository", "User ID is blank")
                return null
            }

            val userDoc = firestore.collection("users")
                .document(userId)
                .get()
                .await()

            if (!userDoc.exists()) {
                Log.e("UserRepository", "User document not found for UID: $userId")
                return null
            }

            val shopId = userDoc.getString("shopId")

            if (shopId.isNullOrBlank()) {
                Log.e("UserRepository", "shopId is missing or empty for UID: $userId")
                return null
            }

            shopId

        } catch (e: Exception) {
            Log.e("UserRepository", "Error fetching shopkeeper shopId", e)
            null
        }
    }
}