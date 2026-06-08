const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { onDocumentUpdated } = require("firebase-functions/v2/firestore");
const admin = require("firebase-admin");

admin.initializeApp();
const db = admin.firestore();

function clean(v) {
  return typeof v === "string" ? v.trim().toLowerCase() : "";
}

function str(v) {
  return typeof v === "string" ? v.trim() : "";
}

async function verifyShopkeeper(uid, orderId) {
  const userSnap = await db.collection("users").doc(uid).get();
  const orderSnap = await db.collection("orders").doc(orderId).get();

  if (!userSnap.exists) throw new HttpsError("not-found", "User not found");
  if (!orderSnap.exists) throw new HttpsError("not-found", "Order not found");

  const user = userSnap.data();
  const order = orderSnap.data();

  if (user.role !== "shopkeeper") throw new HttpsError("permission-denied", "Only shopkeeper allowed");
  if (user.isApproved !== true || user.isBlocked === true) throw new HttpsError("permission-denied", "Shopkeeper inactive");
  if (order.shopId !== user.shopId) throw new HttpsError("permission-denied", "Order not from your shop");

  return { user, order };
}

exports.updateOrderStatus = onCall({ region: "us-central1" }, async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Login required");

  const orderId = str(request.data?.orderId);
  const newStatus = clean(request.data?.newStatus);

  if (!orderId || !newStatus) throw new HttpsError("invalid-argument", "Missing data");

  const allowed = {
    pending: ["preparing"],
    preparing: ["ready"],
    ready: ["picked_up"],
  };

  const orderRef = db.collection("orders").doc(orderId);

  await db.runTransaction(async (tx) => {
    const userSnap = await tx.get(db.collection("users").doc(uid));
    const orderSnap = await tx.get(orderRef);

    if (!userSnap.exists) throw new HttpsError("not-found", "User not found");
    if (!orderSnap.exists) throw new HttpsError("not-found", "Order not found");

    const user = userSnap.data();
    const order = orderSnap.data();

    if (user.role !== "shopkeeper") throw new HttpsError("permission-denied", "Only shopkeeper allowed");
    if (user.isApproved !== true || user.isBlocked === true) throw new HttpsError("permission-denied", "Shopkeeper inactive");
    if (order.shopId !== user.shopId) throw new HttpsError("permission-denied", "Order not from your shop");

    const oldStatus = clean(order.status);
    if (!allowed[oldStatus]?.includes(newStatus)) {
      throw new HttpsError("failed-precondition", `Cannot change ${oldStatus} to ${newStatus}`);
    }

    const now = Date.now();
    const update = {
      status: newStatus,
      updatedAt: now,
    };

    if (newStatus === "preparing") {
      update.paymentStatus = "verified";
      update.preparingAt = now;
    }

    if (newStatus === "ready") {
      update.readyAt = now;
    }

    if (newStatus === "picked_up") {
      update.pickedUpAt = now;
    }

    tx.update(orderRef, update);
  });

  return { success: true };
});

exports.cancelOrderByShopkeeper = onCall({ region: "us-central1" }, async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Login required");

  const orderId = str(request.data?.orderId);
  const paymentReceivedType = clean(request.data?.paymentReceivedType);
  const cancelReason = str(request.data?.cancelReason);

  if (!orderId) throw new HttpsError("invalid-argument", "Order ID required");
  if (!["none", "partial", "full"].includes(paymentReceivedType)) {
    throw new HttpsError("invalid-argument", "Invalid payment type");
  }

  const orderRef = db.collection("orders").doc(orderId);

  await db.runTransaction(async (tx) => {
    const userSnap = await tx.get(db.collection("users").doc(uid));
    const orderSnap = await tx.get(orderRef);

    if (!userSnap.exists) throw new HttpsError("not-found", "User not found");
    if (!orderSnap.exists) throw new HttpsError("not-found", "Order not found");

    const user = userSnap.data();
    const order = orderSnap.data();

    if (user.role !== "shopkeeper") throw new HttpsError("permission-denied", "Only shopkeeper allowed");
    if (user.isApproved !== true || user.isBlocked === true) throw new HttpsError("permission-denied", "Shopkeeper inactive");
    if (order.shopId !== user.shopId) throw new HttpsError("permission-denied", "Order not from your shop");

    const oldStatus = clean(order.status);
    if (!["pending", "preparing"].includes(oldStatus)) {
      throw new HttpsError("failed-precondition", "This order cannot be cancelled");
    }

    const paymentReceived = paymentReceivedType === "partial" || paymentReceivedType === "full";
    const totalPrice = typeof order.totalPrice === "number" ? order.totalPrice : 0;
    const now = Date.now();

    tx.update(orderRef, {
      status: "cancelled",
      paymentStatus: paymentReceived
        ? paymentReceivedType === "full"
          ? "paid"
          : "partial_payment_received"
        : "payment_not_received",
      paymentReceivedByShopkeeper: paymentReceived,
      paymentReceivedType,
      cancelReason: cancelReason || "Payment not received",
      cancelledBy: "shopkeeper",
      cancelledAt: now,
      refundStatus: paymentReceived ? "refund_pending" : "none",
      refundAmount: paymentReceivedType === "full" ? totalPrice : 0,
      refundReferenceId: "",
      refundSettledAt: 0,
      refundSettledBy: "",
      refundNote: "",
      updatedAt: now,
    });
  });

  return { success: true };
});

exports.markRefundSettled = onCall({ region: "us-central1" }, async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Login required");

  const orderId = str(request.data?.orderId);
  const refundReferenceId = str(request.data?.refundReferenceId);
  const refundNote = str(request.data?.refundNote);

  if (!orderId || !refundReferenceId) {
    throw new HttpsError("invalid-argument", "Missing refund details");
  }

  const orderRef = db.collection("orders").doc(orderId);

  await db.runTransaction(async (tx) => {
    const userSnap = await tx.get(db.collection("users").doc(uid));
    const orderSnap = await tx.get(orderRef);

    if (!userSnap.exists) throw new HttpsError("not-found", "User not found");
    if (!orderSnap.exists) throw new HttpsError("not-found", "Order not found");

    const user = userSnap.data();
    const order = orderSnap.data();

    if (user.role !== "shopkeeper") throw new HttpsError("permission-denied", "Only shopkeeper allowed");
    if (order.shopId !== user.shopId) throw new HttpsError("permission-denied", "Order not from your shop");
    if (clean(order.refundStatus) !== "refund_pending") throw new HttpsError("failed-precondition", "Refund not pending");

    tx.update(orderRef, {
      paymentStatus: "refunded",
      refundStatus: "refunded",
      refundReferenceId,
      refundNote,
      refundSettledBy: "shopkeeper",
      refundSettledAt: Date.now(),
      updatedAt: Date.now(),
    });
  });

  return { success: true };
});

exports.sendOrderReadyNotification = onDocumentUpdated(
  { document: "orders/{orderId}", region: "asia-south1" },
  async (event) => {
    if (!event.data) return null;

    const before = event.data.before.data() || {};
    const after = event.data.after.data() || {};

    if (clean(before.status) === clean(after.status)) return null;
    if (clean(after.status) !== "ready") return null;

    const studentId = str(after.studentId);
    if (!studentId) return null;

    const userSnap = await db.collection("users").doc(studentId).get();
    const token = str(userSnap.data()?.fcmToken);

    if (!token) return null;

    return admin.messaging().send({
      token,
      notification: {
        title: "Order Ready ✅",
        body: "Your CampusBite order is ready for pickup.",
      },
      data: {
        orderId: event.params.orderId,
        status: "ready",
      },
      android: {
        priority: "high",
        notification: {
          channelId: "order_updates",
          sound: "default",
        },
      },
    });
  }
);