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

    suspend fun login(
        email: String,
        password: String
    ): Result<Unit> {
        return try {
            auth.signInWithEmailAndPassword(
                email.trim(),
                password
            ).await()

            Result.success(Unit)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun register(
        name: String,
        email: String,
        phone: String,
        password: String,
        role: String
    ): Result<Unit> {
        return try {
            if (name.trim().isBlank()) {
                throw Exception("Name is required")
            }

            if (email.trim().isBlank()) {
                throw Exception("Email is required")
            }

            if (phone.trim().isBlank()) {
                throw Exception("Phone number is required")
            }

            if (phone.trim().length < 10) {
                throw Exception("Enter a valid phone number")
            }

            if (password.length < 6) {
                throw Exception("Password should be at least 6 characters")
            }

            val result = auth.createUserWithEmailAndPassword(
                email.trim(),
                password
            ).await()

            val uid = result.user?.uid
                ?: throw Exception("User ID not found")

            val normalizedRole = role.trim().lowercase()

            val isApproved = normalizedRole != "shopkeeper"

            val user = User(
                uid = uid,
                name = name.trim(),
                email = email.trim(),
                phone = phone.trim(),
                role = normalizedRole,
                shopId = "",
                isApproved = isApproved,
                isBlocked = false,
                createdAt = System.currentTimeMillis()
            )

            firestore.collection("users")
                .document(uid)
                .set(user)
                .await()

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

            val snapshot = firestore.collection("users")
                .document(uid)
                .get()
                .await()

            snapshot.getString("role") ?: "student"

        } catch (e: Exception) {
            "student"
        }
    }

    suspend fun isShopkeeperApproved(): Boolean {
        return try {
            val uid = auth.currentUser?.uid ?: return false

            val userSnap = firestore.collection("users")
                .document(uid)
                .get()
                .await()

            val role = userSnap.getString("role") ?: "student"

            if (role != "shopkeeper") {
                return true
            }

            userSnap.getBoolean("isApproved") ?: false

        } catch (e: Exception) {
            false
        }
    }

    suspend fun isUserBlocked(): Boolean {
        return try {
            val uid = auth.currentUser?.uid ?: return false

            val snapshot = firestore.collection("users")
                .document(uid)
                .get()
                .await()

            snapshot.getBoolean("isBlocked") ?: false

        } catch (e: Exception) {
            false
        }
    }
}