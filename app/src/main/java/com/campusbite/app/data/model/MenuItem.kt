package com.campusbite.app.data.model

import com.google.firebase.firestore.PropertyName

data class MenuItem(
    var itemId: String = "",
    var shopId: String = "",
    var name: String = "",
    var price: Double = 0.0,
    var prepTimeMinutes: Int = 0,
    var category: String = "",

    @get:PropertyName("isAvailable")
    @set:PropertyName("isAvailable")
    var isAvailable: Boolean = true,

    var imageUrl: String = ""
)