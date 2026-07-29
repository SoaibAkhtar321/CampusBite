package com.campusbite.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OrderActionRepository @Inject constructor(
    private val auth: FirebaseAuth
) {
    private val functions: FirebaseFunctions by lazy {
        FirebaseFunctions.getInstance(REGION)
    }

    private suspend fun ensureLoggedIn() {
        val user = auth.currentUser

        if (user == null) {
            throw Exception("User not logged in")
        }

        user.getIdToken(true).await()
    }

    suspend fun updateOrderStatus(
        orderId: String,
        newStatus: String
    ): Result<Unit> {
        return callFunction(
            functionName = UPDATE_ORDER_STATUS,
            data = mapOf(
                "orderId" to orderId,
                "newStatus" to newStatus
            )
        )
    }

    suspend fun cancelOrderByShopkeeper(
        orderId: String,
        paymentReceivedType: String,
        cancelReason: String,
        paymentReceivedAmount: Double = 0.0
    ): Result<Unit> {
        return callFunction(
            functionName = CANCEL_ORDER_BY_SHOPKEEPER,
            data = mapOf(
                "orderId" to orderId,
                "paymentReceivedType" to paymentReceivedType,
                "paymentReceivedAmount" to paymentReceivedAmount,
                "reason" to cancelReason
            )
        )
    }

    suspend fun markRefundSettled(
        orderId: String,
        refundReferenceId: String,
        refundNote: String
    ): Result<Unit> {
        return callFunction(
            functionName = MARK_REFUND_SETTLED,
            data = mapOf(
                "orderId" to orderId,
                "refundReferenceId" to refundReferenceId,
                "refundNote" to refundNote
            )
        )
    }

    private suspend fun callFunction(
        functionName: String,
        data: Map<String, Any>
    ): Result<Unit> {
        return try {
            ensureLoggedIn()

            functions
                .getHttpsCallable(functionName)
                .call(data)
                .await()

            Result.success(Unit)
        } catch (e: FirebaseFunctionsException) {
            e.printStackTrace()

            Result.failure(
                Exception(
                    "Function failed: ${e.code.name} - ${e.message}",
                    e
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    companion object {
        private const val REGION = "us-central1"

        private const val UPDATE_ORDER_STATUS = "updateOrderStatus"
        private const val CANCEL_ORDER_BY_SHOPKEEPER = "cancelOrderByShopkeeper"
        private const val MARK_REFUND_SETTLED = "markRefundSettled"
    }
}