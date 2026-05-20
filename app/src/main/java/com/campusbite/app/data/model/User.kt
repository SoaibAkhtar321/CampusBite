package com.campusbite.app.data.model

data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val role: String = "student",
    val university: String = "",
    val universityId: String = "",
    val shopId: String = "",
    val isApproved: Boolean = true,
    val isBlocked: Boolean = false,
    val createdAt: Long = 0L
)