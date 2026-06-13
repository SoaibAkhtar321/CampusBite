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

    suspend fun placeOrder(order: Order): Result<String> {
        return try {
            val user = auth.currentUser
                ?: return Result.failure(Exception("User not logged in"))

            val studentId = user.uid

            val userDoc = firestore.collection("users")
                .document(studentId)
                .get()
                .await()

            if (!userDoc.exists()) {
                return Result.failure(Exception("User profile not found"))
            }

            val studentName = userDoc.getString("name") ?: ""
            val studentEmail = userDoc.getString("email") ?: user.email.orEmpty()
            val studentPhone = userDoc.getString("phone") ?: ""

            if (order.shopId.isBlank()) {
                return Result.failure(Exception("Shop ID is missing"))
            }

            val shopDoc = firestore.collection("shops")
                .document(order.shopId)
                .get()
                .await()

            if (!shopDoc.exists()) {
                return Result.failure(
                    Exception("Shop not found. Please clear cart and try again.")
                )
            }

            val isOrderable =
                shopDoc.getBoolean("isApproved") == true &&
                        shopDoc.getBoolean("isOpen") == true &&
                        shopDoc.getBoolean("isBlocked") != true &&
                        shopDoc.getBoolean("isDeleted") != true &&
                        shopDoc.getBoolean("isVisible") != false

            if (!isOrderable) {
                return Result.failure(
                    Exception("This shop is currently not accepting orders.")
                )
            }

            val docRef = firestore.collection("orders").document()
            val orderId = docRef.id
            val now = System.currentTimeMillis()

            val normalizedItems = order.items.map { item ->
                item.copy(shopId = order.shopId)
            }

            val finalOrder = order.copy(
                orderId = orderId,
                shopId = order.shopId,
                studentId = studentId,
                studentName = studentName,
                studentEmail = studentEmail,
                studentPhone = studentPhone,
                items = normalizedItems,
                status = OrderStatusValue.PENDING,
                paymentStatus = PaymentStatusValue.PENDING_VERIFICATION,
                refundStatus = RefundStatusValue.NONE,
                createdAt = now,
                updatedAt = now
            )

            val orderData = buildOrderData(finalOrder)

            logOrderCreateDebug(
                studentId = studentId,
                orderId = orderId,
                finalOrder = finalOrder,
                orderData = orderData,
                userDoc = userDoc,
                shopDoc = shopDoc
            )

            docRef.set(orderData).await()

            Log.e(
                "ORDER_RULE_DEBUG",
                "ORDER WRITE SUCCESS: orderId=$orderId shopId=${finalOrder.shopId}"
            )

            Result.success(orderId)
        } catch (e: Exception) {
            Log.e("ORDER_RULE_DEBUG", "ORDER WRITE FAILED", e)
            Result.failure(e)
        }
    }

    private fun buildOrderData(order: Order): Map<String, Any> {
        return mapOf(
            "orderId" to order.orderId,

            "shopId" to order.shopId,
            "shopName" to order.shopName,
            "shopkeeperPhone" to order.shopkeeperPhone,

            "studentId" to order.studentId,
            "studentName" to order.studentName,
            "studentEmail" to order.studentEmail,
            "studentPhone" to order.studentPhone,

            "items" to order.items.map { item ->
                mapOf(
                    "itemId" to item.itemId,
                    "name" to item.name,
                    "price" to item.price,
                    "quantity" to item.quantity,
                    "prepTimeMinutes" to item.prepTimeMinutes,
                    "shopId" to item.shopId,
                    "cookingNote" to item.cookingNote
                )
            },

            "totalPrice" to order.totalPrice,
            "status" to OrderStatusValue.PENDING,

            "pickupSlot" to order.pickupSlot,
            "pickupDate" to order.pickupDate,

            "paymentMethod" to order.paymentMethod,
            "paymentStatus" to PaymentStatusValue.PENDING_VERIFICATION,
            "upiPayerName" to order.upiPayerName,

            "cancelReason" to "",
            "cancelledBy" to "",
            "cancelledAt" to 0L,

            "paymentReceivedByShopkeeper" to false,
            "paymentReceivedType" to PaymentReceivedType.NONE,

            "refundStatus" to RefundStatusValue.NONE,
            "refundAmount" to 0.0,
            "refundReferenceId" to "",
            "refundSettledAt" to 0L,
            "refundNote" to "",

            "createdAt" to order.createdAt,
            "updatedAt" to order.updatedAt
        )
    }

    private fun logOrderCreateDebug(
        studentId: String,
        orderId: String,
        finalOrder: Order,
        orderData: Map<String, Any>,
        userDoc: DocumentSnapshot,
        shopDoc: DocumentSnapshot
    ) {
        val localRuleCheck =
            userDoc.exists() &&
                    userDoc.getString("role") in listOf("student", "user", "Student", "User") &&
                    userDoc.getBoolean("isBlocked") != true &&
                    userDoc.getBoolean("isDeleted") != true &&
                    finalOrder.orderId == orderId &&
                    finalOrder.studentId == studentId &&
                    finalOrder.shopId.isNotBlank() &&
                    shopDoc.exists() &&
                    shopDoc.getBoolean("isApproved") == true &&
                    shopDoc.getBoolean("isOpen") == true &&
                    shopDoc.getBoolean("isBlocked") != true &&
                    shopDoc.getBoolean("isDeleted") != true &&
                    shopDoc.getBoolean("isVisible") != false &&
                    finalOrder.items.isNotEmpty() &&
                    finalOrder.totalPrice > 0 &&
                    finalOrder.status == OrderStatusValue.PENDING &&
                    finalOrder.paymentStatus == PaymentStatusValue.PENDING_VERIFICATION

        Log.e(
            "ORDER_RULE_DEBUG",
            """
            -------- ORDER CREATE DEBUG --------
            LOCAL_RULE_CHECK_SHOULD_PASS=$localRuleCheck

            authUid=$studentId
            orderDocId=$orderId

            orderData.orderId=${orderData["orderId"]}
            orderIdMatches=${orderData["orderId"] == orderId}

            orderData.studentId=${orderData["studentId"]}
            studentMatchesAuth=${orderData["studentId"] == studentId}

            userExists=${userDoc.exists()}
            userRole=${userDoc.getString("role")}
            userIsBlocked=${userDoc.getBoolean("isBlocked")}
            userIsDeleted=${userDoc.getBoolean("isDeleted")}

            orderData.shopId=${orderData["shopId"]}
            shopPath=shops/${orderData["shopId"]}
            shopExists=${shopDoc.exists()}
            shopFieldShopId=${shopDoc.getString("shopId")}
            shopIsOpen=${shopDoc.getBoolean("isOpen")}
            shopIsApproved=${shopDoc.getBoolean("isApproved")}
            shopIsBlocked=${shopDoc.getBoolean("isBlocked")}
            shopIsDeleted=${shopDoc.getBoolean("isDeleted")}
            shopIsVisible=${shopDoc.getBoolean("isVisible")}

            itemsSize=${finalOrder.items.size}
            itemShopIds=${finalOrder.items.map { it.shopId }.distinct()}
            totalPrice=${orderData["totalPrice"]}
            status=${orderData["status"]}
            paymentStatus=${orderData["paymentStatus"]}
            pickupSlot=${orderData["pickupSlot"]}
            pickupDate=${orderData["pickupDate"]}
            paymentMethod=${orderData["paymentMethod"]}

            orderDataKeys=${orderData.keys}
            ------------------------------------
            """.trimIndent()
        )
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

                Log.d(
                    "OrderRepository",
                    "Order update: ${order.orderId} status=${order.status}"
                )

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

                Log.d(
                    "OrderRepository",
                    "Active order update: ${activeOrder?.orderId} status=${activeOrder?.status}"
                )

                onUpdate(activeOrder)
            }
    }

    companion object {
        const val PAGE_SIZE = 10L

        val TERMINAL_STATUSES = listOf(
            OrderStatusValue.PICKED_UP,
            OrderStatusValue.CANCELLED
        )
    }
}