package com.campusbite.app.data.model

data class Shop(
    val shopId: String = "", // Must be Firestore document ID
    val name: String = "",
    val description: String = "",
    val imageUrl: String = "",
    val phone: String = "",
    val upiId: String = "",

    val isOpen: Boolean = true,
    val isApproved: Boolean = false,
    val isBlocked: Boolean = false,
    val isDeleted: Boolean = false,
    val isVisible: Boolean = true,

    val openingTime: String = "",
    val closingTime: String = "",
    val displayOrder: Long = 0L
) {
    val canAcceptOrders: Boolean
        get() = isApproved && isOpen && !isBlocked && !isDeleted && isVisible
}