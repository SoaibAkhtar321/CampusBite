package com.campusbite.app.data.model

import com.google.firebase.firestore.PropertyName

data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val role: String = "student",
    val university: String = "",
    val universityId: String = "",
    val shopId: String = "",

    // Kotlin generates a JavaBean-style getter for a Boolean property named
    // "isXxx" as isXxx() rather than getIsXxx(), and Firestore's POJO
    // serializer strips that "is" prefix per the JavaBean convention when
    // it infers the field name from the getter. Without these explicit
    // @PropertyName annotations, isApproved/isBlocked silently get written
    // to and read from Firestore as "approved"/"blocked" instead of
    // "isApproved"/"isBlocked" — which is exactly what broke isAdmin() /
    // isShopkeeper() / isStudent() in firestore.rules, since those check
    // for "isApproved"/"isBlocked" specifically.
    @get:PropertyName("isApproved") @set:PropertyName("isApproved")
    var isApproved: Boolean = true,

    @get:PropertyName("isBlocked") @set:PropertyName("isBlocked")
    var isBlocked: Boolean = false,

    val authProvider: String = "google",
    val createdAt: Long = 0L
)