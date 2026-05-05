package com.campusbite.app.data.model


data class Shop(
    val shopId: String = "",
    val name: String = "",
    val description: String = "",
    val imageUrl: String = "",

    val isOpen: Boolean = true,
    val maxOrdersPerSlot: Int = 5,
    val closedSlots: List<String> = emptyList()
)