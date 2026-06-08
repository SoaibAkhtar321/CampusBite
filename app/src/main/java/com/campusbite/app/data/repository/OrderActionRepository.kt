package com.campusbite.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OrderActionRepository @Inject constructor(
    private val auth: FirebaseAuth
) {
    private val functions = FirebaseFunctions.getInstance("us-central1")

    private suspend fun ensureLoggedIn() {
        val user = auth.currentUser ?: throw Exception("User not logged in")
        user.getIdToken(true).await()
    }

    suspend fun updateOrderStatus(
        orderId: String,
        newStatus: String
    ): Result<Unit> {
        return try {
            ensureLoggedIn()

            functions
                .getHttpsCallable("updateOrderStatus")
                .call(
                    mapOf(
                        "orderId" to orderId,
                        "newStatus" to newStatus
                    )
                )
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun cancelOrderByShopkeeper(
        orderId: String,
        paymentReceivedType: String,
        cancelReason: String
    ): Result<Unit> {
        return try {
            ensureLoggedIn()

            functions
                .getHttpsCallable("cancelOrderByShopkeeper")
                .call(
                    mapOf(
                        "orderId" to orderId,
                        "paymentReceivedType" to paymentReceivedType,
                        "cancelReason" to cancelReason
                    )
                )
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun markRefundSettled(
        orderId: String,
        refundReferenceId: String,
        refundNote: String
    ): Result<Unit> {
        return try {
            ensureLoggedIn()

            functions
                .getHttpsCallable("markRefundSettled")
                .call(
                    mapOf(
                        "orderId" to orderId,
                        "refundReferenceId" to refundReferenceId,
                        "refundNote" to refundNote
                    )
                )
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}