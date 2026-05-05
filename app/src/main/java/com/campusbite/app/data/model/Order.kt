package com.campusbite.app.data.model

data class Order(
    val orderId: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val shopId: String = "",
    val items: List<OrderItem> = emptyList(),
    val totalPrice: Double = 0.0,
    val status: String = "pending",
    val pickupSlot: String = "",
    val paymentMethod: String = "Cash on Delivery",
    val createdAt: Long = 0L,
    val pickupDate: String = ""
)

data class OrderItem(
    val itemId: String = "",
    val name: String = "",
    val price: Double = 0.0,
    val quantity: Int = 1,
    val prepTimeMinutes: Int = 0,
    val shopId: String = ""
)