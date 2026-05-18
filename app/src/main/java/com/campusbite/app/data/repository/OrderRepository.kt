package com.campusbite.app.data.repository

import android.util.Log
import com.campusbite.app.data.model.Order
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

    suspend fun placeOrder(order: Order): Result<String> {
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

            val studentName = userDoc.getString("name") ?: "Student"
            val studentEmail = userDoc.getString("email") ?: currentUser.email.orEmpty()

            val docRef = firestore.collection("orders").document()
            val orderId = docRef.id

            val finalOrder = order.copy(
                orderId = orderId,
                studentId = studentId,
                studentName = studentName,
                studentEmail = studentEmail,
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

    suspend fun getOrderById(orderId: String): Result<Order> {
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

                val order = snapshot?.toObject(Order::class.java)
                onUpdate(order)
            }
    }

    fun listenToActiveOrder(
        userId: String,
        onUpdate: (Order?) -> Unit
    ): ListenerRegistration {
        return firestore.collection("orders")
            .whereEqualTo("studentId", userId)
            .whereIn("status", listOf("pending", "accepted", "preparing", "ready"))
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