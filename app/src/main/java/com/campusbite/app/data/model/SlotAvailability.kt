package com.campusbite.app.data.model

data class SlotAvailability(
    val slotId: String = "",
    val shopId: String = "",
    val date: String = "",
    val slot: String = "",
    val orderCount: Int = 0,
    val maxOrders: Int = 5,
    val isClosed: Boolean = false
)