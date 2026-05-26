package com.campusbite.app.data.model

data class Shop(
    val shopId: String = "",
    val name: String = "",
    val description: String = "",
    val imageUrl: String = "",

    val isOpen: Boolean = true,
    val isApproved: Boolean = false,
    val isBlocked: Boolean = false,
    val isDeleted: Boolean = false,

    val openingTime: String = "08:00",
    val closingTime: String = "21:00",

    val maxOrdersPerSlot: Int = 5,
    val closedSlots: List<String> = emptyList(),

    val upiId: String = "",
    val phone: String = "",

    val ownerUid: String = "",
    val ownerEmail: String = "",
    val ownerPhone: String = "",

    val createdAt: Long = 0L
)