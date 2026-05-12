package com.campusbite.app.data.model

data class Shop(
    val shopId: String = "",
    val name: String = "",
    val shopName: String = "",          // display name (same as name, kept for compatibility)
    val description: String = "",
    val imageUrl: String = "",
    val shopImageUrl: String = "",      // alias used in UI

    // Visibility
    val isOpen: Boolean = false,
    val acceptingOrders: Boolean = true, // emergency pause without closing

    // Timings
    val openingTime: String = "08:00",
    val closingTime: String = "21:00",

    // Slot & capacity
    val maxOrdersPerSlot: Int = 5,
    val closedSlots: List<String> = emptyList(),
    val availableSlots: List<String> = emptyList(),

    // Operational
    val averagePreparationTime: Int = 10, // minutes, used for queue estimates

    // Location & identity
    val collegeId: String = "",
    val locationDescription: String = "", // e.g. "Near Block A"

    // Metadata
    val createdAt: String = ""
)
