package com.campusbite.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot

// ─────────────────────────────────────────────────────────────────
//  Order data model
//
//  All timestamp fields are Long (epoch millis) in Kotlin so the
//  rest of the app never has to import firebase.Timestamp outside
//  this file.  Firestore stores them as Timestamp objects, so we
//  NEVER use toObject<Order>() — always use Order.from(snapshot).
// ─────────────────────────────────────────────────────────────────
data class Order(
    val orderId: String = "",

    val shopId: String = "",
    val shopName: String = "",
    val shopkeeperPhone: String = "",

    val studentId: String = "",
    val studentName: String = "",
    val studentEmail: String = "",
    val studentPhone: String = "",

    val items: List<OrderItem> = emptyList(),
    val totalPrice: Double = 0.0,

    val status: String = "pending",
    val pickupSlot: String = "",
    val pickupDate: String = "",

    val paymentMethod: String = "UPI_QR",
    val paymentStatus: String = "pending_verification",
    val upiPayerName: String = "",

    val cancelReason: String = "",
    val cancelledBy: String = "",
    val cancelledAt: Long = 0L,

    val paymentReceivedByShopkeeper: Boolean = false,
    val paymentReceivedType: String = "none",
    // none | partial | full

    val refundStatus: String = "none",
    // none | refund_pending | refunded | refund_disputed

    val refundAmount: Double = 0.0,
    val refundReferenceId: String = "",
    val refundSettledAt: Long = 0L,
    val refundNote: String = "",

    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = 0L
) {
    companion object {

        // ── Main entry point ────────────────────────────────────────
        // Use this everywhere instead of snapshot.toObject<Order>()
        // ────────────────────────────────────────────────────────────
        fun from(snapshot: DocumentSnapshot): Order {
            val d = snapshot.data ?: return Order(orderId = snapshot.id)

            return Order(
                orderId = snapshot.id,           // always from document ID

                shopId              = d.str("shopId"),
                shopName            = d.str("shopName"),
                shopkeeperPhone     = d.str("shopkeeperPhone"),

                studentId           = d.str("studentId"),
                studentName         = d.str("studentName"),
                studentEmail        = d.str("studentEmail"),
                studentPhone        = d.str("studentPhone"),

                items               = parseItems(d["items"]),
                totalPrice          = d.dbl("totalPrice"),

                status              = d.str("status", "pending"),
                pickupSlot          = d.str("pickupSlot"),
                pickupDate          = d.str("pickupDate"),

                paymentMethod       = d.str("paymentMethod", "UPI_QR"),
                paymentStatus       = d.str("paymentStatus", "pending_verification"),
                upiPayerName        = d.str("upiPayerName"),

                cancelReason        = d.str("cancelReason"),
                cancelledBy         = d.str("cancelledBy"),
                cancelledAt         = d.millis("cancelledAt"),

                paymentReceivedByShopkeeper = d["paymentReceivedByShopkeeper"] as? Boolean ?: false,
                paymentReceivedType = d.str("paymentReceivedType", "none"),

                refundStatus        = d.str("refundStatus", "none"),
                refundAmount        = d.dbl("refundAmount"),
                refundReferenceId   = d.str("refundReferenceId"),
                refundSettledAt     = d.millis("refundSettledAt"),
                refundNote          = d.str("refundNote"),

                createdAt           = d.millis("createdAt"),
                updatedAt           = d.millis("updatedAt")
            )
        }

        // ── Private helpers (defined ONCE, used for both Order and OrderItem parsing) ──

        /** Firestore Timestamp OR Long/Number → epoch millis. */
        private fun Map<String, Any?>.millis(key: String): Long =
            when (val v = this[key]) {
                is Timestamp -> v.toDate().time
                is Long      -> v
                is Number    -> v.toLong()
                else         -> 0L
            }

        private fun Map<String, Any?>.str(key: String, default: String = ""): String =
            this[key] as? String ?: default

        private fun Map<String, Any?>.dbl(key: String, default: Double = 0.0): Double =
            (this[key] as? Number)?.toDouble() ?: default

        /** Parses the items array from Firestore. */
        @Suppress("UNCHECKED_CAST")
        private fun parseItems(raw: Any?): List<OrderItem> {
            val list = raw as? List<Map<String, Any?>> ?: return emptyList()
            return list.map { m ->
                OrderItem(
                    itemId          = m.str("itemId"),
                    name            = m.str("name"),
                    price           = m.dbl("price"),
                    quantity        = (m["quantity"] as? Number)?.toInt() ?: 1,
                    prepTimeMinutes = (m["prepTimeMinutes"] as? Number)?.toInt() ?: 0,
                    shopId          = m.str("shopId"),
                    cookingNote     = m.str("cookingNote")
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
//  OrderItem
// ─────────────────────────────────────────────────────────────────
data class OrderItem(
    val itemId: String = "",
    val name: String = "",
    val price: Double = 0.0,
    val quantity: Int = 1,
    val prepTimeMinutes: Int = 0,
    val shopId: String = "",
    val cookingNote: String = ""
)
