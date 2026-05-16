package com.campusbite.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class UserRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : UserRepository {

    override suspend fun getCurrentUserId(): String? {
        return auth.currentUser?.uid
    }

    override suspend fun getShopkeeperShopId(userId: String): String? {
        return try {
            val userDoc = firestore.collection("users")
                .document(userId)
                .get()
                .await()

            userDoc.getString("shopId")
        } catch (exception: Exception) {
            null
        }
    }
}
