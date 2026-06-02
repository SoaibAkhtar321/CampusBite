package com.campusbite.app.data.repository

import com.campusbite.app.data.model.SlotAvailability
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SlotAvailabilityRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {

    fun buildSlotId(
        shopId: String,
        date: String,
        slot: String
    ): String {
        return "${shopId}_${date}_${slot}"
            .replace(" ", "_")
            .replace(":", "_")
            .replace("/", "_")
    }

    suspend fun getSlotAvailability(
        shopId: String,
        date: String,
        slot: String,
        maxOrders: Int
    ): SlotAvailability {
        val slotId = buildSlotId(
            shopId = shopId,
            date = date,
            slot = slot
        )

        val snapshot = firestore.collection("slotAvailability")
            .document(slotId)
            .get()
            .await()

        return if (snapshot.exists()) {
            snapshot.toObject(SlotAvailability::class.java)
                ?: SlotAvailability(
                    slotId = slotId,
                    shopId = shopId,
                    date = date,
                    slot = slot,
                    orderCount = 0,
                    maxOrders = maxOrders,
                    isClosed = false
                )
        } else {
            SlotAvailability(
                slotId = slotId,
                shopId = shopId,
                date = date,
                slot = slot,
                orderCount = 0,
                maxOrders = maxOrders,
                isClosed = false
            )
        }
    }
}