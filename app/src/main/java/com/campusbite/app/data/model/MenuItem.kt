package com.campusbite.app.data.model

import com.google.firebase.firestore.PropertyName

data class MenuItem(
    val itemId: String = "",
    val shopId: String = "",
    val name: String = "",
    val price: Double = 0.0,
    val prepTimeMinutes: Int = 0,
    val category: String = "",

    @PropertyName("isAvailable")  // ✅ IMPORTANT: Tells Firestore to use exactly this field name
    val isAvailable: Boolean = true,

    val imageUrl: String = ""
) {
    // Empty constructor for Firestore deserialization
    constructor() : this(
        itemId = "",
        shopId = "",
        name = "",
        price = 0.0,
        prepTimeMinutes = 0,
        category = "",
        isAvailable = true,
        imageUrl = ""
    )
}
