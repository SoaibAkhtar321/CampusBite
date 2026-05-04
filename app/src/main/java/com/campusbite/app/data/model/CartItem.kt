package com.campusbite.app.data.model

data class CartItem(
    val itemId: String = "",
    val name: String = "",
    val price: Int = 0,
    val quantity: Int = 1,
    val shopId: String = ""   // IMPORTANT
)