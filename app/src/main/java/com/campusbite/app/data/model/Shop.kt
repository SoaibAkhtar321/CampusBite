package com.campusbite.app.data.model

data class Shop(
    val shopId: String = "",
    val name: String = "",
    val description: String = "",
    val imageUrl: String = "",

    // Visibility
    val isOpen: Boolean = true,

    // Timings
    val openingTime: String = "08:00",
    val closingTime: String = "21:00",

    // Slot & capacity
    val maxOrdersPerSlot: Int = 5,
    val closedSlots: List<String> = emptyList(),
    val upiId: String = ""
)