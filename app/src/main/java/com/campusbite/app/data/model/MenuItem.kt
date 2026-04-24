package com.campusbite.app.data.model

data class MenuItem(
    val itemId: String = "",
    val shopId: String = "",
    val name: String = "",
    val price: Double = 0.0,
    val prepTimeMinutes: Int = 0,
    val category: String = "",
    val isAvailable: Boolean = true,
    val imageUrl: String = ""
)