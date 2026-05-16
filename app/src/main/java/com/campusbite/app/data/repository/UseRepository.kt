package com.campusbite.app.data.repository

interface UserRepository {
    suspend fun getCurrentUserId(): String?
    suspend fun getShopkeeperShopId(userId: String): String?
}
