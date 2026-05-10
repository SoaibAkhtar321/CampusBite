package com.campusbite.app.data.model

data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val role: String = "student", // "student" or "staff"
    val shopId: String = ""       // only relevant if role is staff
)