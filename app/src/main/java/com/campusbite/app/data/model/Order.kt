package com.campusbite.app.data.model

data class Order(
    val orderId: String = "",

    val shopId: String = "",
    val shopName: String = "",
    val shopkeeperPhone: String = "",

    val studentId: String = "",
    val studentName: String = "",
    val studentEmail: String = "",
    val studentPhone: String = "",

    val items: List<OrderItem> = emptyList(),
    val totalPrice: Double = 0.0,

    val status: String = "pending",
    val pickupSlot: String = "",
    val pickupDate: String = "",

    val paymentMethod: String = "UPI_QR",
    val paymentStatus: String = "pending_verification",
    val upiPayerName: String = "",

    val cancelReason: String = "",
    val cancelledBy: String = "",
    val cancelledAt: Long = 0L,

    val paymentReceivedByShopkeeper: Boolean = false,
    val paymentReceivedType: String = "none",
    // none, partial, full

    val refundStatus: String = "none",
    // none, refund_pending, refunded, refund_disputed

    val refundAmount: Double = 0.0,
    val refundReferenceId: String = "",
    val refundSettledAt: Long = 0L,
    val refundNote: String = "",

    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = 0L
)

data class OrderItem(
    val itemId: String = "",
    val name: String = "",
    val price: Double = 0.0,
    val quantity: Int = 1,
    val prepTimeMinutes: Int = 0,
    val shopId: String = "",
    val cookingNote: String = ""
)