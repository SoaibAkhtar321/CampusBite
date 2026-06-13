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
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OrderRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {

    // ─────────────────────────────────────────────────────────────
    //  Place Order
    // ─────────────────────────────────────────────────────────────
    suspend fun placeOrder(order: Order): Result<String> {
        return try {
            val currentUser = auth.currentUser
                ?: throw Exception("User not logged in")

            val studentId = currentUser.uid

            if (order.shopId.isBlank()) throw Exception("Shop ID is missing")
            if (order.items.isEmpty()) throw Exception("Cart is empty")
            if (order.pickupSlot.isBlank()) throw Exception("Pickup slot is missing")

            val userDoc = firestore.collection("users")
                .document(studentId)
                .get()
                .await()

            if (!userDoc.exists()) {
                throw Exception("User profile not found")
            }

            val role = userDoc.getString("role") ?: ""
            val isUserBlocked = userDoc.getBoolean("isBlocked") ?: false
            val isUserDeleted = userDoc.getBoolean("isDeleted") ?: false

            if (role !in listOf("student", "user")) {
                throw Exception("Only users can place orders. Current role: $role")
            }

            if (isUserBlocked || isUserDeleted) {
                throw Exception("Your account is blocked or deleted")
            }

            val shopDoc = firestore.collection("shops")
                .document(order.shopId)
                .get()
                .await()

            if (!shopDoc.exists()) {
                throw Exception(
                    "Shop document not found for shopId=${order.shopId}. Check if shops document ID matches shopId."
                )
            }

            val isShopApproved = shopDoc.getBoolean("isApproved") ?: false
            val isShopBlocked = shopDoc.getBoolean("isBlocked") ?: false
            val isShopDeleted = shopDoc.getBoolean("isDeleted") ?: false
            val isShopVisible = shopDoc.getBoolean("isVisible") ?: true
            val isShopOpen = shopDoc.getBoolean("isOpen") ?: false

            if (!isShopApproved) {
                throw Exception("Shop is not approved")
            }

            if (isShopBlocked) {
                throw Exception("Shop is blocked")
            }

            if (isShopDeleted) {
                throw Exception("Shop is deleted")
            }

            if (!isShopVisible) {
                throw Exception("Shop is hidden by admin")
            }

            if (!isShopOpen) {
                throw Exception("Shop is currently closed")
            }

            val studentName = userDoc.getString("name") ?: "Student"
            val studentEmail = userDoc.getString("email") ?: currentUser.email.orEmpty()
            val studentPhone = userDoc.getString("phone") ?: currentUser.phoneNumber.orEmpty()

            val docRef = firestore.collection("orders").document()
            val orderId = docRef.id

            val now = System.currentTimeMillis()

            val finalOrder = order.copy(
                orderId = orderId,
                studentId = studentId,
                studentName = studentName,
                studentEmail = studentEmail,
                studentPhone = studentPhone,
                status = OrderStatusValue.PENDING,
                paymentStatus = PaymentStatusValue.PENDING_VERIFICATION,
                createdAt = now,
                updatedAt = now
            )

            Log.d(
                "OrderRepository",
                "Placing order=$orderId | uid=$studentId | role=$role | shopId=${finalOrder.shopId} | " +
                        "shopOpen=$isShopOpen | shopApproved=$isShopApproved | shopBlocked=$isShopBlocked | " +
                        "shopDeleted=$isShopDeleted | shopVisible=$isShopVisible | status=${finalOrder.status} | " +
                        "paymentStatus=${finalOrder.paymentStatus} | total=${finalOrder.totalPrice}"
            )

            docRef.set(finalOrder).await()

            Log.d("OrderRepository", "Order placed successfully: $orderId")

            Result.success(orderId)
        } catch (e: Exception) {
            Log.e("OrderRepository", "Failed to place order", e)
            Result.failure(e)
        }
    }
    // ─────────────────────────────────────────────────────────────
    //  One-shot fetch — uses Order.from() to handle Timestamps
    // ─────────────────────────────────────────────────────────────
    suspend fun getOrderById(orderId: String): Result<Order> {
        return try {
            if (orderId.isBlank()) throw Exception("Order ID is missing")

            val snapshot = firestore.collection("orders")
                .document(orderId)
                .get()
                .await()

            if (!snapshot.exists()) throw Exception("Order not found")

            Result.success(Order.from(snapshot))
        } catch (e: Exception) {
            Log.e("OrderRepository", "Failed to get order by ID: $orderId", e)
            Result.failure(e)
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Paginated student order history
    //  Returns (orders, lastVisible) so the caller can call again
    //  with lastVisible to load the next page.
    // ─────────────────────────────────────────────────────────────
    suspend fun getStudentOrdersPaged(
        userId: String,
        lastVisible: DocumentSnapshot? = null,
        pageSize: Long = PAGE_SIZE
    ): Result<Pair<List<Order>, DocumentSnapshot?>> {
        return try {
            if (userId.isBlank()) throw Exception("User ID is missing")

            var query = firestore.collection("orders")
                .whereEqualTo("studentId", userId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(pageSize)

            if (lastVisible != null) {
                query = query.startAfter(lastVisible)
            }

            val snapshot = query.get().await()
            val orders = snapshot.documents.mapNotNull { doc ->
                try { Order.from(doc) } catch (e: Exception) {
                    Log.e("OrderRepository", "Failed to parse student order: ${doc.id}", e)
                    null
                }
            }
            val newLastVisible = snapshot.documents.lastOrNull()

            Log.d("OrderRepository", "Student orders page: ${orders.size}, hasMore=${orders.size >= pageSize}")
            Result.success(Pair(orders, newLastVisible))
        } catch (e: Exception) {
            Log.e("OrderRepository", "Failed to get student orders page", e)
            Result.failure(e)
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Paginated shopkeeper order history
    //  Only terminal statuses: picked_up + cancelled
    //  Requires composite index: shopId ASC + status ASC + createdAt DESC
    //  (add to firestore.indexes.json — see bottom of this file)
    // ─────────────────────────────────────────────────────────────
    suspend fun getShopOrdersPaged(
        shopId: String,
        lastVisible: DocumentSnapshot? = null,
        pageSize: Long = PAGE_SIZE
    ): Result<Pair<List<Order>, DocumentSnapshot?>> {
        return try {
            if (shopId.isBlank()) throw Exception("Shop ID is missing")

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
                try { Order.from(doc) } catch (e: Exception) {
                    Log.e("OrderRepository", "Failed to parse shop order: ${doc.id}", e)
                    null
                }
            }
            val newLastVisible = snapshot.documents.lastOrNull()

            Log.d("OrderRepository", "Shop orders page: ${orders.size}, hasMore=${orders.size >= pageSize}")
            Result.success(Pair(orders, newLastVisible))
        } catch (e: Exception) {
            Log.e("OrderRepository", "Failed to get shop orders page", e)
            Result.failure(e)
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Cancel order (direct Firestore write — kept for legacy use,
    //  prefer OrderActionRepository.cancelOrderByShopkeeper which
    //  goes through the Cloud Function)
    // ─────────────────────────────────────────────────────────────
    suspend fun cancelOrderByShopkeeper(
        orderId: String,
        paymentReceivedType: String,
        cancelReason: String
    ): Result<Unit> {
        return try {
            if (orderId.isBlank()) return Result.failure(Exception("Order ID is missing"))

            val cleanPaymentType = paymentReceivedType.trim().lowercase()
            val cleanReason      = cancelReason.trim()

            if (cleanPaymentType !in listOf(
                    PaymentReceivedType.NONE,
                    PaymentReceivedType.PARTIAL,
                    PaymentReceivedType.FULL
                )
            ) {
                return Result.failure(Exception("Invalid payment received type"))
            }

            val paymentReceivedByShopkeeper = cleanPaymentType in listOf(
                PaymentReceivedType.PARTIAL,
                PaymentReceivedType.FULL
            )

            if (paymentReceivedByShopkeeper && cleanReason.isBlank()) {
                return Result.failure(Exception("Please select a cancellation reason"))
            }

            val paymentStatus = when (cleanPaymentType) {
                PaymentReceivedType.PARTIAL -> PaymentStatusValue.PARTIAL_PAYMENT_RECEIVED
                PaymentReceivedType.FULL    -> PaymentStatusValue.PAID
                else                        -> PaymentStatusValue.PAYMENT_NOT_RECEIVED
            }

            val refundStatus = if (paymentReceivedByShopkeeper) {
                RefundStatusValue.REFUND_PENDING
            } else {
                RefundStatusValue.NONE
            }

            val finalCancelReason = when {
                cleanReason.isNotBlank()                          -> cleanReason
                cleanPaymentType == PaymentReceivedType.NONE      -> "Payment not received"
                else                                              -> "Order cancelled after payment received"
            }

            val now = System.currentTimeMillis()

            firestore.collection("orders").document(orderId)
                .update(
                    mapOf(
                        "status"                     to OrderStatusValue.CANCELLED,
                        "paymentStatus"              to paymentStatus,
                        "cancelReason"               to finalCancelReason,
                        "cancelledBy"                to "shopkeeper",
                        "cancelledAt"                to now,
                        "paymentReceivedByShopkeeper" to paymentReceivedByShopkeeper,
                        "paymentReceivedType"         to cleanPaymentType,
                        "refundStatus"               to refundStatus,
                        "refundReferenceId"          to "",
                        "refundSettledAt"            to 0L,
                        "refundNote"                 to "",
                        "updatedAt"                  to now
                    )
                )
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("OrderRepository", "Failed to cancel order: $orderId", e)
            Result.failure(e)
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Mark refund settled
    // ─────────────────────────────────────────────────────────────
    suspend fun markRefundSettled(
        orderId: String,
        refundReferenceId: String,
        refundNote: String
    ): Result<Unit> {
        return try {
            if (orderId.isBlank()) return Result.failure(Exception("Order ID is missing"))

            val cleanRefId = refundReferenceId.trim()
            val cleanNote  = refundNote.trim()

            if (cleanRefId.isBlank()) {
                return Result.failure(Exception("Refund reference ID is required"))
            }

            val now = System.currentTimeMillis()

            firestore.collection("orders").document(orderId)
                .update(
                    mapOf(
                        "paymentStatus"    to PaymentStatusValue.REFUNDED,
                        "refundStatus"     to RefundStatusValue.REFUNDED,
                        "refundReferenceId" to cleanRefId,
                        "refundNote"       to cleanNote,
                        "refundSettledAt"  to now,
                        "updatedAt"        to now
                    )
                )
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("OrderRepository", "Failed to mark refund settled: $orderId", e)
            Result.failure(e)
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Live listener — single order (OrderStatusScreen)
    // ─────────────────────────────────────────────────────────────
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
                Log.d("OrderRepository", "Order update: ${order.orderId} status=${order.status}")
                onUpdate(order)
            }
    }

    // ─────────────────────────────────────────────────────────────
    //  Live listener — active order (HomeScreen banner)
    // ─────────────────────────────────────────────────────────────
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
                        try { Order.from(doc) } catch (e: Exception) {
                            Log.e("OrderRepository", "Failed to parse active order doc: ${doc.id}", e)
                            null
                        }
                    }
                    .maxByOrNull { it.createdAt }

                Log.d("OrderRepository", "Active order update: ${activeOrder?.orderId} status=${activeOrder?.status}")
                onUpdate(activeOrder)
            }
    }

    companion object {
        const val PAGE_SIZE = 10L

        // Terminal statuses shown in shopkeeper history
        val TERMINAL_STATUSES = listOf(
            OrderStatusValue.PICKED_UP,
            OrderStatusValue.CANCELLED
        )
    }
}

