package com.campusbite.app.data.model

data class Order(
    val orderId: String = "",
    val shopId: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val studentEmail: String = "",
    val items: List<OrderItem> = emptyList(),
    val totalPrice: Double = 0.0,
    val status: String = "pending",
    val pickupSlot: String = "",
    val pickupDate: String = "",
    val paymentMethod: String = "",
    val paymentStatus: String = "pending",
    val transactionRef: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

data class OrderItem(
    val itemId: String = "",
    val name: String = "",
    val price: Double = 0.0,
    val quantity: Int = 1,
    val prepTimeMinutes: Int = 0,
    val shopId: String = "",
    val cookingNote: String = ""   // e.g. "extra spicy", "less sugar"
)
