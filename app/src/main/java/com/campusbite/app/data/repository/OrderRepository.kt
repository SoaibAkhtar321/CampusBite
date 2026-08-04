package com.campusbite.app.data.repository

import android.util.Log
import com.campusbite.app.data.model.Order
import com.campusbite.app.util.OrderStatusValue
import com.campusbite.app.util.PaymentReceivedType
import com.campusbite.app.util.PaymentStatusValue
import com.campusbite.app.util.RefundStatusValue
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OrderRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val functions: FirebaseFunctions
) {

    /**
     * Creates an order exclusively through the `createOrder` Cloud Function.
     * The function performs a server-side transaction (auth/role/shop/menu
     * validation, pricing, and idempotency via clientRequestId) — the client
     * never writes to the `orders` collection directly.
     */
    suspend fun placeOrder(order: Order): Result<String> {
        return try {
            val user = auth.currentUser
                ?: return Result.failure(Exception("User not logged in"))

            if (order.shopId.isBlank()) {
                return Result.failure(Exception("Shop ID is missing"))
            }

            if (order.items.isEmpty()) {
                return Result.failure(Exception("Cart is empty"))
            }

            // Force-refresh the ID token so the callable's App Check /
            // auth checks see the latest claims.
            user.getIdToken(true).await()

            val clientRequestId = UUID.randomUUID().toString()

            val requestData = mapOf(
                "clientRequestId" to clientRequestId,
                "shopId" to order.shopId,
                "pickupDate" to order.pickupDate,
                "pickupSlot" to order.pickupSlot,
                "paymentMethod" to order.paymentMethod,
                "upiPayerName" to order.upiPayerName,
                "items" to order.items.map { item ->
                    mapOf(
                        "itemId" to item.itemId,
                        "quantity" to item.quantity,
                        "cookingNote" to item.cookingNote
                    )
                }
            )

            val callableResult = functions
                .getHttpsCallable(CREATE_ORDER_FUNCTION)
                .call(requestData)
                .await()

            @Suppress("UNCHECKED_CAST")
            val response = callableResult.getData() as? Map<String, Any?>
                ?: return Result.failure(Exception("Malformed response from server"))

            val orderId = (response["orderId"] as? String).orEmpty()

            if (orderId.isBlank()) {
                return Result.failure(Exception("Order creation did not return an order ID"))
            }

            if (response["duplicate"] as? Boolean == true) {
                Log.w(
                    "OrderRepository",
                    "createOrder resolved a duplicate clientRequestId=$clientRequestId " +
                            "to existing orderId=$orderId"
                )
            }

            Result.success(orderId)
        } catch (e: FirebaseFunctionsException) {
            Log.e("OrderRepository", "createOrder call failed: ${e.code}", e)
            Result.failure(Exception(describeFunctionError(e), e))
        } catch (e: Exception) {
            Log.e("OrderRepository", "Failed to place order", e)
            Result.failure(e)
        }
    }

    private fun describeFunctionError(e: FirebaseFunctionsException): String {
        return when (e.code) {
            FirebaseFunctionsException.Code.UNAUTHENTICATED ->
                "User not logged in"

            FirebaseFunctionsException.Code.ALREADY_EXISTS ->
                "This order request was already used with a different cart. " +
                        "Please clear cart and try again."

            FirebaseFunctionsException.Code.PERMISSION_DENIED,
            FirebaseFunctionsException.Code.NOT_FOUND,
            FirebaseFunctionsException.Code.FAILED_PRECONDITION,
            FirebaseFunctionsException.Code.INVALID_ARGUMENT ->
                e.message ?: "Failed to place order"

            else ->
                e.message ?: "Failed to place order"
        }
    }

    suspend fun getOrderById(orderId: String): Result<Order> {
        return try {
            if (orderId.isBlank()) {
                throw Exception("Order ID is missing")
            }

            val snapshot = firestore.collection("orders")
                .document(orderId)
                .get()
                .await()

            if (!snapshot.exists()) {
                throw Exception("Order not found")
            }

            Result.success(Order.from(snapshot))
        } catch (e: Exception) {
            Log.e("OrderRepository", "Failed to get order by ID: $orderId", e)
            Result.failure(e)
        }
    }

    suspend fun getStudentOrdersPaged(
        userId: String,
        lastVisible: DocumentSnapshot? = null,
        pageSize: Long = PAGE_SIZE
    ): Result<Pair<List<Order>, DocumentSnapshot?>> {
        return try {
            if (userId.isBlank()) {
                throw Exception("User ID is missing")
            }

            var query = firestore.collection("orders")
                .whereEqualTo("studentId", userId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(pageSize)

            if (lastVisible != null) {
                query = query.startAfter(lastVisible)
            }

            val snapshot = query.get().await()

            val orders = snapshot.documents.mapNotNull { doc ->
                try {
                    Order.from(doc)
                } catch (e: Exception) {
                    Log.e(
                        "OrderRepository",
                        "Failed to parse student order: ${doc.id}",
                        e
                    )
                    null
                }
            }

            val newLastVisible = snapshot.documents.lastOrNull()

            Result.success(Pair(orders, newLastVisible))
        } catch (e: Exception) {
            Log.e("OrderRepository", "Failed to get student orders page", e)
            Result.failure(e)
        }
    }

    suspend fun getShopOrdersPaged(
        shopId: String,
        lastVisible: DocumentSnapshot? = null,
        pageSize: Long = PAGE_SIZE
    ): Result<Pair<List<Order>, DocumentSnapshot?>> {
        return try {
            if (shopId.isBlank()) {
                throw Exception("Shop ID is missing")
            }

            var query = firestore.collection("orders")
                .whereEqualTo("shopId", shopId)
                .whereIn("status", TERMINAL_STATUSES)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(pageSize)

            if (lastVisible != null) {
                query = query.startAfter(lastVisible)
            }

            val snapshot = query.get().await()

            val orders = snapshot.documents.mapNotNull { doc ->
                try {
                    Order.from(doc)
                } catch (e: Exception) {
                    Log.e(
                        "OrderRepository",
                        "Failed to parse shop order: ${doc.id}",
                        e
                    )
                    null
                }
            }

            val newLastVisible = snapshot.documents.lastOrNull()

            Result.success(Pair(orders, newLastVisible))
        } catch (e: Exception) {
            Log.e("OrderRepository", "Failed to get shop orders page", e)
            Result.failure(e)
        }
    }

    suspend fun cancelOrderByShopkeeper(
        orderId: String,
        paymentReceivedType: String,
        cancelReason: String
    ): Result<Unit> {
        return try {
            if (orderId.isBlank()) {
                return Result.failure(Exception("Order ID is missing"))
            }

            val cleanPaymentType = paymentReceivedType.trim().lowercase()
            val cleanReason = cancelReason.trim()

            if (
                cleanPaymentType !in listOf(
                    PaymentReceivedType.NONE,
                    PaymentReceivedType.PARTIAL,
                    PaymentReceivedType.FULL
                )
            ) {
                return Result.failure(Exception("Invalid payment received type"))
            }

            val paymentReceivedByShopkeeper =
                cleanPaymentType in listOf(
                    PaymentReceivedType.PARTIAL,
                    PaymentReceivedType.FULL
                )

            if (paymentReceivedByShopkeeper && cleanReason.isBlank()) {
                return Result.failure(Exception("Please select a cancellation reason"))
            }

            val paymentStatus = when (cleanPaymentType) {
                PaymentReceivedType.PARTIAL ->
                    PaymentStatusValue.PARTIAL_PAYMENT_RECEIVED

                PaymentReceivedType.FULL ->
                    PaymentStatusValue.PAID

                else ->
                    PaymentStatusValue.PAYMENT_NOT_RECEIVED
            }

            val refundStatus = if (paymentReceivedByShopkeeper) {
                RefundStatusValue.REFUND_PENDING
            } else {
                RefundStatusValue.NONE
            }

            val finalCancelReason = when {
                cleanReason.isNotBlank() ->
                    cleanReason

                cleanPaymentType == PaymentReceivedType.NONE ->
                    "Payment not received"

                else ->
                    "Order cancelled after payment received"
            }

            val now = System.currentTimeMillis()

            firestore.collection("orders")
                .document(orderId)
                .update(
                    mapOf(
                        "status" to OrderStatusValue.CANCELLED,
                        "paymentStatus" to paymentStatus,
                        "cancelReason" to finalCancelReason,
                        "cancelledBy" to "shopkeeper",
                        "cancelledAt" to now,
                        "paymentReceivedByShopkeeper" to paymentReceivedByShopkeeper,
                        "paymentReceivedType" to cleanPaymentType,
                        "refundStatus" to refundStatus,
                        "refundReferenceId" to "",
                        "refundSettledAt" to 0L,
                        "refundNote" to "",
                        "updatedAt" to now
                    )
                )
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("OrderRepository", "Failed to cancel order: $orderId", e)
            Result.failure(e)
        }
    }

    suspend fun markRefundSettled(
        orderId: String,
        refundReferenceId: String,
        refundNote: String
    ): Result<Unit> {
        return try {
            if (orderId.isBlank()) {
                return Result.failure(Exception("Order ID is missing"))
            }

            val cleanRefId = refundReferenceId.trim()
            val cleanNote = refundNote.trim()

            if (cleanRefId.isBlank()) {
                return Result.failure(Exception("Refund reference ID is required"))
            }

            val now = System.currentTimeMillis()

            firestore.collection("orders")
                .document(orderId)
                .update(
                    mapOf(
                        "paymentStatus" to PaymentStatusValue.REFUNDED,
                        "refundStatus" to RefundStatusValue.REFUNDED,
                        "refundReferenceId" to cleanRefId,
                        "refundNote" to cleanNote,
                        "refundSettledAt" to now,
                        "updatedAt" to now
                    )
                )
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("OrderRepository", "Failed to mark refund settled: $orderId", e)
            Result.failure(e)
        }
    }

    fun listenToOrder(
        orderId: String,
        onUpdate: (Order?) -> Unit
    ): ListenerRegistration {
        return firestore.collection("orders")
            .document(orderId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("OrderRepository", "listenToOrder error: $orderId", error)
                    onUpdate(null)
                    return@addSnapshotListener
                }

                if (snapshot == null || !snapshot.exists()) {
                    Log.w("OrderRepository", "Order snapshot missing: $orderId")
                    onUpdate(null)
                    return@addSnapshotListener
                }

                val order = Order.from(snapshot)

                onUpdate(order)
            }
    }

    fun listenToActiveOrder(
        userId: String,
        onUpdate: (Order?) -> Unit
    ): ListenerRegistration {
        val activeStatuses = listOf(
            OrderStatusValue.PENDING,
            OrderStatusValue.ACCEPTED,
            OrderStatusValue.PREPARING,
            OrderStatusValue.READY
        )

        return firestore.collection("orders")
            .whereEqualTo("studentId", userId)
            .whereIn("status", activeStatuses)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("OrderRepository", "listenToActiveOrder error: $userId", error)
                    onUpdate(null)
                    return@addSnapshotListener
                }

                if (snapshot == null || snapshot.isEmpty) {
                    onUpdate(null)
                    return@addSnapshotListener
                }

                val activeOrder = snapshot.documents
                    .mapNotNull { doc ->
                        try {
                            Order.from(doc)
                        } catch (e: Exception) {
                            Log.e(
                                "OrderRepository",
                                "Failed to parse active order doc: ${doc.id}",
                                e
                            )
                            null
                        }
                    }
                    .maxByOrNull { it.createdAt }

                onUpdate(activeOrder)
            }
    }

    companion object {
        const val PAGE_SIZE = 10L
        private const val CREATE_ORDER_FUNCTION = "createOrder"

        val TERMINAL_STATUSES = listOf(
            OrderStatusValue.PICKED_UP,
            OrderStatusValue.CANCELLED
        )
    }
}