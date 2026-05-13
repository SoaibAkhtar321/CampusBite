package com.campusbite.app.data.model

data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val role: String = "student", // student | shopkeeper | admin
    val shopId: String = "",
    val isApproved: Boolean = true,   // ✅ NEW (shopkeeper approval)
    val isBlocked: Boolean = false    // ✅ optional future use
)