package com.campusbite.app.util

object OrderStatusValue {
    const val PENDING = "pending"
    const val ACCEPTED = "accepted"
    const val PREPARING = "preparing"
    const val READY = "ready"
    const val PICKED_UP = "picked_up"
    const val CANCELLED = "cancelled"
}

object PaymentStatusValue {
    const val PENDING_VERIFICATION = "pending_verification"
    const val PAID = "paid"
    const val PAYMENT_NOT_RECEIVED = "payment_not_received"
    const val PARTIAL_PAYMENT_RECEIVED = "partial_payment_received"
    const val REFUND_PENDING = "refund_pending"
    const val REFUND_SETTLED = "refund_settled"
    const val REFUNDED = "refunded"
}

object RefundStatusValue {
    const val NONE = "none"
    const val REFUND_PENDING = "pending"
    const val REFUNDED = "settled"
    const val REFUND_DISPUTED = "disputed"
}

object PaymentReceivedType {
    const val NONE = "none"
    const val PARTIAL = "partial"
    const val FULL = "full"
}