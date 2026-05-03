package com.campusbite.app.data.repository

import com.campusbite.app.data.model.Order
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
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
            val docRef = firestore.collection("orders").document()
            val orderId = docRef.id
            val finalOrder = order.copy(
                orderId = orderId,
                studentId = auth.currentUser?.uid ?: "",
                createdAt = System.currentTimeMillis()
            )
            docRef.set(finalOrder).await()
            Result.success(orderId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getOrderById(orderId: String): Result<Order> {
        return try {
            val snapshot = firestore.collection("orders")
                .document(orderId).get().await()
            val order = snapshot.toObject(Order::class.java)
                ?: throw Exception("Order not found")
            Result.success(order)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun listenToOrder(orderId: String, onUpdate: (Order) -> Unit) {
        firestore.collection("orders").document(orderId)
            .addSnapshotListener { snapshot, _ ->
                snapshot?.toObject(Order::class.java)?.let { onUpdate(it) }
            }
    }
}