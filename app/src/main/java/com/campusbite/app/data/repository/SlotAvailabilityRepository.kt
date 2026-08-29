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

        return snapshotToSlotAvailability(
            snapshot = snapshot,
            slotId = slotId,
            shopId = shopId,
            date = date,
            slot = slot,
            maxOrders = maxOrders
        )
    }

    /**
     * Batched equivalent of calling [getSlotAvailability] once per slot in a
     * loop. Each slot in a loadAvailableSlots() call was previously a
     * separate sequential Firestore document .get() (up to ~12 round trips
     * for a 3-hour window at 15-minute intervals); this fetches all of them
     * in a single Firestore getAll() batch call, cutting that down to one
     * network round trip while reading the exact same documents. Returns a
     * map keyed by the original slot label so callers can look up results
     * in generation order.
     */
    suspend fun getSlotAvailabilityBatch(
        shopId: String,
        date: String,
        slots: List<String>,
        maxOrders: Int
    ): Map<String, SlotAvailability> {
        if (slots.isEmpty()) return emptyMap()

        val slotIdToSlot = slots.associateBy { slot ->
            buildSlotId(shopId = shopId, date = date, slot = slot)
        }

        val docRefs = slotIdToSlot.keys.map { slotId ->
            firestore.collection("slotAvailability").document(slotId)
        }

        val snapshots = firestore.getAll(*docRefs.toTypedArray()).await()

        return snapshots.associate { snapshot ->
            val slotId = snapshot.id
            val slot = slotIdToSlot.getValue(slotId)

            slot to snapshotToSlotAvailability(
                snapshot = snapshot,
                slotId = slotId,
                shopId = shopId,
                date = date,
                slot = slot,
                maxOrders = maxOrders
            )
        }
    }

    private fun snapshotToSlotAvailability(
        snapshot: com.google.firebase.firestore.DocumentSnapshot,
        slotId: String,
        shopId: String,
        date: String,
        slot: String,
        maxOrders: Int
    ): SlotAvailability {
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