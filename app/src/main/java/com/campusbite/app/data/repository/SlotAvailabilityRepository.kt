package com.campusbite.app.data.repository

import com.campusbite.app.data.model.SlotAvailability
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
     * for a 3-hour window at 15-minute intervals). The Android Firestore
     * client SDK (unlike the server/Admin SDK) has no multi-DocumentReference
     * getAll(), so this instead fires all per-document .get() calls
     * concurrently via coroutines and awaits them together, reading the
     * exact same documents while collapsing the wait to one round trip's
     * worth of latency instead of ~12 sequential ones. Returns a map keyed
     * by the original slot label so callers can look up results in
     * generation order. Non-existent slot documents still fall through to
     * the default "fully available" [SlotAvailability] via
     * [snapshotToSlotAvailability], same as [getSlotAvailability].
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

        return coroutineScope {
            slotIdToSlot.map { (slotId, slot) ->
                async {
                    val snapshot = firestore.collection("slotAvailability")
                        .document(slotId)
                        .get()
                        .await()

                    slot to snapshotToSlotAvailability(
                        snapshot = snapshot,
                        slotId = slotId,
                        shopId = shopId,
                        date = date,
                        slot = slot,
                        maxOrders = maxOrders
                    )
                }
            }.awaitAll().toMap()
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