package com.campusbite.app.data.repository

import android.util.Patterns
import com.campusbite.app.data.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
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

    suspend fun signInWithGoogle(idToken: String): Result<Boolean> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)

            val result = auth.signInWithCredential(credential).await()
            val firebaseUser = result.user ?: throw Exception("Google sign-in failed")

            val userDoc = firestore.collection("users")
                .document(firebaseUser.uid)
                .get()
                .await()

            if (!userDoc.exists()) {
                return Result.success(false)
            }

            val isBlocked = userDoc.getBoolean("isBlocked") ?: false

            if (isBlocked) {
                return Result.failure(Exception("Your account has been blocked by admin."))
            }

            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun completeGoogleProfile(
        name: String,
        phone: String,
        role: String,
        university: String,
        universityId: String
    ): Result<Unit> {
        return try {
            val firebaseUser = auth.currentUser ?: throw Exception("User not logged in")

            val cleanRole = role.trim().lowercase()
            val cleanName = name.trim()
            val cleanEmail = firebaseUser.email?.trim().orEmpty()
            val cleanPhone = phone.trim()
            val cleanUniversity = university.trim()
            val cleanUniversityId = universityId.trim().lowercase()

            validateGoogleProfileData(
                name = cleanName,
                email = cleanEmail,
                phone = cleanPhone,
                role = cleanRole,
                university = cleanUniversity,
                universityId = cleanUniversityId
            )

            val existingDoc = firestore.collection("users")
                .document(firebaseUser.uid)
                .get()
                .await()

            if (existingDoc.exists()) {
                return Result.success(Unit)
            }

            val finalUniversity = if (cleanRole == "shopkeeper") cleanUniversity else ""
            val finalUniversityId = if (cleanRole == "shopkeeper") cleanUniversityId else ""

            val user = User(
                uid = firebaseUser.uid,
                name = cleanName,
                email = cleanEmail,
                phone = cleanPhone,
                role = cleanRole,
                university = finalUniversity,
                universityId = finalUniversityId,
                shopId = "",
                isApproved = cleanRole == "student",
                isBlocked = false,
                authProvider = "google",
                createdAt = System.currentTimeMillis()
            )

            firestore.collection("users")
                .document(firebaseUser.uid)
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

            if (role != "shopkeeper") return true

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
    suspend fun hasCompletedProfile(): Boolean {
        return try {
            val uid = auth.currentUser?.uid ?: return false

            val snapshot = firestore.collection("users")
                .document(uid)
                .get()
                .await()

            snapshot.exists()
        } catch (e: Exception) {
            false
        }
    }

    private fun validateGoogleProfileData(
        name: String,
        email: String,
        phone: String,
        role: String,
        university: String,
        universityId: String
    ) {
        if (name.isBlank()) {
            throw Exception("Name is required")
        }

        if (email.isBlank()) {
            throw Exception("Google email not found")
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            throw Exception("Google email is invalid")
        }

        if (!phone.matches(Regex("^[6-9][0-9]{9}$"))) {
            throw Exception("Enter a valid 10 digit phone number")
        }

        if (role !in listOf("student", "shopkeeper")) {
            throw Exception("Invalid role")
        }

        if (role == "shopkeeper" && (university.isBlank() || universityId.isBlank())) {
            throw Exception("Please select your campus/university")
        }
    }
}