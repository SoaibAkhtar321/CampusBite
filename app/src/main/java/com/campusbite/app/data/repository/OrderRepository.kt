package com.campusbite.app.data.repository

import android.util.Log
import com.campusbite.app.data.model.Order
import com.campusbite.app.util.OrderStatusValue
import com.campusbite.app.util.PaymentReceivedType
import com.campusbite.app.util.PaymentStatusValue
import com.campusbite.app.util.RefundStatusValue
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OrderRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {

    suspend fun placeOrder(
        order: Order
    ): Result<String> {
        return try {
            val currentUser = auth.currentUser
                ?: throw Exception("User not logged in")

            val studentId = currentUser.uid

            if (order.shopId.isBlank()) {
                throw Exception("Shop ID is missing")
            }

            if (order.items.isEmpty()) {
                throw Exception("Cart is empty")
            }

            if (order.pickupSlot.isBlank()) {
                throw Exception("Pickup slot is missing")
            }

            val userDoc = firestore.collection("users")
                .document(studentId)
                .get()
                .await()

            val studentName = userDoc.getString("name")
                ?: "Student"

            val studentEmail = userDoc.getString("email")
                ?: currentUser.email.orEmpty()

            val studentPhone = userDoc.getString("phone")
                ?: currentUser.phoneNumber.orEmpty()

            val docRef = firestore.collection("orders").document()
            val orderId = docRef.id

            val finalOrder = order.copy(
                orderId = orderId,
                studentId = studentId,
                studentName = studentName,
                studentEmail = studentEmail,
                studentPhone = studentPhone,
                status = OrderStatusValue.PENDING,
                paymentStatus = PaymentStatusValue.PENDING_VERIFICATION,
                createdAt = System.currentTimeMillis()
            )

            Log.d("OrderRepository", "Placing order...")
            Log.d("OrderRepository", "orderId: $orderId")
            Log.d("OrderRepository", "studentId: $studentId")
            Log.d("OrderRepository", "studentName: $studentName")
            Log.d("OrderRepository", "studentEmail: $studentEmail")
            Log.d("OrderRepository", "shopId: ${finalOrder.shopId}")
            Log.d("OrderRepository", "pickupSlot: ${finalOrder.pickupSlot}")
            Log.d("OrderRepository", "items count: ${finalOrder.items.size}")

            docRef.set(finalOrder).await()

            Log.d("OrderRepository", "Order placed successfully")

            Result.success(orderId)
        } catch (e: Exception) {
            Log.e("OrderRepository", "Failed to place order", e)
            Result.failure(e)
        }
    }

    suspend fun getOrderById(
        orderId: String
    ): Result<Order> {
        return try {
            if (orderId.isBlank()) {
                throw Exception("Order ID is missing")
            }

            val snapshot = firestore.collection("orders")
                .document(orderId)
                .get()
                .await()

            val order = snapshot.toObject(Order::class.java)
                ?: throw Exception("Order not found")

            Result.success(order)
        } catch (e: Exception) {
            Log.e("OrderRepository", "Failed to get order by ID", e)
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

            val paymentReceivedByShopkeeper = cleanPaymentType in listOf(
                PaymentReceivedType.PARTIAL,
                PaymentReceivedType.FULL
            )

            if (paymentReceivedByShopkeeper && cleanReason.isBlank()) {
                return Result.failure(
                    Exception("Please select a cancellation reason")
                )
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
                cleanReason.isNotBlank() -> cleanReason

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
            Log.e("OrderRepository", "Failed to cancel order", e)
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

            val cleanRefundReferenceId = refundReferenceId.trim()
            val cleanRefundNote = refundNote.trim()

            if (cleanRefundReferenceId.isBlank()) {
                return Result.failure(Exception("Refund reference ID is required"))
            }

            val now = System.currentTimeMillis()

            firestore.collection("orders")
                .document(orderId)
                .update(
                    mapOf(
                        "paymentStatus" to PaymentStatusValue.REFUNDED,
                        "refundStatus" to RefundStatusValue.REFUNDED,
                        "refundReferenceId" to cleanRefundReferenceId,
                        "refundNote" to cleanRefundNote,
                        "refundSettledAt" to now,
                        "updatedAt" to now
                    )
                )
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("OrderRepository", "Failed to mark refund settled", e)
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
                    Log.e("OrderRepository", "listenToOrder error", error)
                    onUpdate(null)
                    return@addSnapshotListener
                }

                if (snapshot == null || !snapshot.exists()) {
                    Log.e("OrderRepository", "Order snapshot missing: $orderId")
                    onUpdate(null)
                    return@addSnapshotListener
                }

                try {
                    val order = snapshot.toObject(Order::class.java)

                    if (order == null) {
                        Log.e("OrderRepository", "Order parse failed: $orderId")
                        onUpdate(null)
                        return@addSnapshotListener
                    }

                    val finalOrder = if (order.orderId.isBlank()) {
                        order.copy(orderId = snapshot.id)
                    } else {
                        order
                    }

                    Log.d(
                        "OrderRepository",
                        "Order live update: ${finalOrder.orderId}, status=${finalOrder.status}"
                    )

                    onUpdate(finalOrder)
                } catch (e: Exception) {
                    Log.e("OrderRepository", "Failed to parse order update", e)
                    onUpdate(null)
                }
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
                    Log.e("OrderRepository", "listenToActiveOrder error", error)
                    onUpdate(null)
                    return@addSnapshotListener
                }

                val activeOrder = snapshot?.documents
                    ?.mapNotNull { document ->
                        try {
                            document.toObject(Order::class.java)
                        } catch (e: Exception) {
                            Log.e("OrderRepository", "Failed to parse active order", e)
                            null
                        }
                    }
                    ?.maxByOrNull { it.createdAt }

                onUpdate(activeOrder)
            }
    }
}