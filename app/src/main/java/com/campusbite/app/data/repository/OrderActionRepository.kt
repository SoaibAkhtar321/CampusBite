package com.campusbite.app.data.repository

import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OrderActionRepository @Inject constructor(
    private val functions: FirebaseFunctions
) {

    suspend fun updateOrderStatus(
        orderId: String,
        newStatus: String
    ): Result<Unit> {
        return try {
            if (orderId.isBlank()) {
                return Result.failure(
                    IllegalArgumentException("Order ID is missing.")
                )
            }

            if (newStatus.isBlank()) {
                return Result.failure(
                    IllegalArgumentException("Order status is missing.")
                )
            }

            val data = hashMapOf(
                "orderId" to orderId.trim(),
                "newStatus" to newStatus.trim().lowercase()
            )

            functions
                .getHttpsCallable("updateOrderStatus")
                .call(data)
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}