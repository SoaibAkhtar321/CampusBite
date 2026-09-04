package com.campusbite.app.data.repository

interface UserRepository {

    /**
     * Returns the currently logged-in Firebase user UID.
     */
    fun getCurrentUserId(): String?

    /**
     * Returns the actual shopId linked with the shopkeeper.
     *
     * Example:
     * UID: MwqoOv6HJLWDRypqH8TqSRsYH6R2
     * shopId: samosa_point
     *
     * This should return "samosa_point", not the UID.
     */
    suspend fun getShopkeeperShopId(userId: String): String?
}