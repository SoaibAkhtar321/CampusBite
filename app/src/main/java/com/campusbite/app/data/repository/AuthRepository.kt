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

    suspend fun login(email: String, password: String): Result<Unit> {
        return try {
            auth.signInWithEmailAndPassword(email.trim(), password).await()
            auth.currentUser?.reload()?.await()

            val firebaseUser = auth.currentUser ?: throw Exception("User not found")

            if (!firebaseUser.isEmailVerified) {
                auth.signOut()
                throw Exception("Please verify your email before logging in.")
            }

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
        role: String,
        university: String,
        universityId: String
    ): Result<Unit> {
        return try {
            validateProfileData(
                name = name.trim(),
                email = email.trim(),
                phone = phone.trim(),
                role = role.trim().lowercase(),
                university = university.trim(),
                universityId = universityId.trim().lowercase()
            )

            if (password.length < 6) {
                throw Exception("Password should be at least 6 characters")
            }

            val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
            val firebaseUser = result.user ?: throw Exception("User not found")

            firebaseUser.sendEmailVerification().await()
            auth.signOut()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun completeEmailRegistration(
        name: String,
        email: String,
        phone: String,
        password: String,
        role: String,
        university: String,
        universityId: String
    ): Result<Unit> {
        return try {
            auth.signInWithEmailAndPassword(email.trim(), password).await()
            auth.currentUser?.reload()?.await()

            val firebaseUser = auth.currentUser ?: throw Exception("User not found")

            if (!firebaseUser.isEmailVerified) {
                auth.signOut()
                throw Exception("Email not verified yet. Please verify your email first.")
            }

            val cleanRole = role.trim().lowercase()

            val user = User(
                uid = firebaseUser.uid,
                name = name.trim(),
                email = email.trim(),
                phone = phone.trim(),
                role = cleanRole,
                university = university.trim(),
                universityId = universityId.trim().lowercase(),
                shopId = "",
                isApproved = cleanRole != "shopkeeper",
                isBlocked = false,
                createdAt = System.currentTimeMillis()
            )

            firestore.collection("users")
                .document(firebaseUser.uid)
                .set(user)
                .await()

            auth.signOut()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

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
                auth.signOut()
                throw Exception("Your account has been blocked by admin.")
            }

            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun completeGoogleProfile(
        phone: String,
        role: String,
        university: String,
        universityId: String
    ): Result<Unit> {
        return try {
            val firebaseUser = auth.currentUser ?: throw Exception("User not logged in")

            val cleanRole = role.trim().lowercase()

            validateProfileData(
                name = firebaseUser.displayName?.trim().orEmpty(),
                email = firebaseUser.email?.trim().orEmpty(),
                phone = phone.trim(),
                role = cleanRole,
                university = university.trim(),
                universityId = universityId.trim().lowercase()
            )

            val user = User(
                uid = firebaseUser.uid,
                name = firebaseUser.displayName?.trim().orEmpty(),
                email = firebaseUser.email?.trim().orEmpty(),
                phone = phone.trim(),
                role = cleanRole,
                university = university.trim(),
                universityId = universityId.trim().lowercase(),
                shopId = "",
                isApproved = cleanRole != "shopkeeper",
                isBlocked = false,
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

    suspend fun resendVerificationEmail(email: String, password: String): Result<Unit> {
        return try {
            auth.signInWithEmailAndPassword(email.trim(), password).await()

            val user = auth.currentUser ?: throw Exception("User not found")

            if (user.isEmailVerified) {
                auth.signOut()
                throw Exception("Email is already verified. Please login.")
            }

            user.sendEmailVerification().await()
            auth.signOut()

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

    private fun validateProfileData(
        name: String,
        email: String,
        phone: String,
        role: String,
        university: String,
        universityId: String
    ) {
        if (name.isBlank()) throw Exception("Name is required")
        if (email.isBlank()) throw Exception("Email is required")

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            throw Exception("Enter a valid email address")
        }

        if (phone.length != 10) {
            throw Exception("Enter a valid 10 digit phone number")
        }

        if (role !in listOf("student", "shopkeeper", "admin")) {
            throw Exception("Invalid role")
        }

        if (university.isBlank() || universityId.isBlank()) {
            throw Exception("Please select your university")
        }
    }
}