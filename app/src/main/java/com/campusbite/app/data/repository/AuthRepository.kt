package com.campusbite.app.data.repository

import com.campusbite.app.data.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {
    val currentUser get() = auth.currentUser

    suspend fun login(email: String, password: String): Result<Unit> {
        return try {
            auth.signInWithEmailAndPassword(email, password).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ✅ UPDATED: role-based register
    suspend fun register(name: String, email: String, password: String, role: String): Result<Unit> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val uid = result.user?.uid ?: throw Exception("User ID not found")

            val isApproved = role != "shopkeeper" // shopkeeper requires approval

            val user = User(
                uid = uid,
                name = name,
                email = email,
                role = role,
                isApproved = isApproved
            )

            firestore.collection("users").document(uid).set(user).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun logout() {
        auth.signOut()
    }

    suspend fun getUserRole(): String {
        return try {
            val uid = auth.currentUser?.uid ?: return "student"
            val snapshot = firestore.collection("users").document(uid).get().await()
            snapshot.getString("role") ?: "student"
        } catch (e: Exception) {
            "student"
        }
    }

    // ✅ NEW
    suspend fun isShopkeeperApproved(): Boolean {
        return try {
            val uid = auth.currentUser?.uid ?: return false
            val snapshot = firestore.collection("users").document(uid).get().await()
            snapshot.getBoolean("isApproved") ?: false
        } catch (e: Exception) {
            false
        }
    }
}