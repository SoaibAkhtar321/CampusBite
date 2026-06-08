const admin = require("firebase-admin");
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { onDocumentCreated, onDocumentUpdated } = require("firebase-functions/v2/firestore");const logger = require("firebase-functions/logger");

admin.initializeApp();

const db = admin.firestore();
const FieldValue = admin.firestore.FieldValue;

const ORDER_STATUSES = [
  "pending",
  "accepted",
  "preparing",
  "ready",
  "picked_up",
  "cancelled",
];

const VALID_TRANSITIONS = {
  pending: ["preparing", "cancelled"],
  accepted: ["preparing", "cancelled"],
  preparing: ["ready", "cancelled"],
  ready: ["picked_up", "cancelled"],
  picked_up: [],
  cancelled: [],
};

function requireAuth(request) {
  const uid = request.auth?.uid;

  if (!uid) {
    throw new HttpsError("unauthenticated", "Login required.");
  }

  return uid;
}

function cleanString(value) {
  return String(value || "").trim();
}

async function getUserOrThrow(uid) {
  const userSnap = await db.collection("users").doc(uid).get();

  if (!userSnap.exists) {
    throw new HttpsError("permission-denied", "User profile not found.");
  }

  return userSnap.data();
}

async function getApprovedShopkeeperOrThrow(uid) {
  const user = await getUserOrThrow(uid);

  if (user.role !== "shopkeeper") {
    throw new HttpsError(
      "permission-denied",
      "Only shopkeepers can perform this action."
    );
  }

  if (user.isBlocked === true) {
    throw new HttpsError("permission-denied", "Your account is blocked.");
  }

  if (user.isApproved !== true) {
    throw new HttpsError("permission-denied", "Shopkeeper is not approved.");
  }

  if (!user.shopId) {
    throw new HttpsError(
      "failed-precondition",
      "Shopkeeper shopId is missing."
    );
  }

  return user;
}

async function getAdminOrShopkeeperOrThrow(uid) {
  const user = await getUserOrThrow(uid);

  if (user.isBlocked === true) {
    throw new HttpsError("permission-denied", "Your account is blocked.");
  }

  if (user.role === "admin") {
    return user;
  }

  if (
    user.role === "shopkeeper" &&
    user.isApproved === true &&
    user.shopId
  ) {
    return user;
  }

  throw new HttpsError(
    "permission-denied",
    "Only admin or approved shopkeeper can perform this action."
  );
}

function buildCancelPaymentFields(paymentReceivedType) {
  const cleanType = cleanString(paymentReceivedType).toLowerCase();

  if (cleanType === "full") {
    return {
      paymentReceivedType: "full",
      paymentReceivedByShopkeeper: true,
      paymentStatus: "refund_pending",
      refundStatus: "pending",
      refundAmount: null,
    };
  }

  if (cleanType === "partial") {
    return {
      paymentReceivedType: "partial",
      paymentReceivedByShopkeeper: true,
      paymentStatus: "refund_pending",
      refundStatus: "pending",
      refundAmount: null,
    };
  }

  return {
    paymentReceivedType: "none",
    paymentReceivedByShopkeeper: false,
    paymentStatus: "payment_not_received",
    refundStatus: "none",
    refundAmount: 0,
  };
}

function getNotificationContent(status, order) {
  const shopName = cleanString(order.shopName) || "CampusBite";

  if (status === "preparing") {
    return {
      title: "Order Accepted",
      body: `${shopName} has accepted your order and started preparing it.`,
    };
  }

  if (status === "ready") {
    return {
      title: "Order Ready",
      body: "Your CampusBite order is ready for pickup.",
    };
  }

  if (status === "cancelled") {
    const reason =
      cleanString(order.cancelReason) ||
      cleanString(order.cancellationReason) ||
      "Please contact the shopkeeper for details.";

    return {
      title: "Order Cancelled",
      body: `Your order was cancelled. Reason: ${reason}`,
    };
  }

  if (status === "picked_up") {
    return {
      title: "Order Completed",
      body: "Your order has been marked as picked up.",
    };
  }

  return {
    title: "Order Updated",
    body: "Your CampusBite order status has changed.",
  };
}

exports.updateOrderStatus = onCall(
  { region: "us-central1" },
  async (request) => {
    const uid = requireAuth(request);

    const orderId = cleanString(request.data?.orderId);
    const newStatus = cleanString(request.data?.newStatus).toLowerCase();

    if (!orderId) {
      throw new HttpsError("invalid-argument", "orderId is required.");
    }

    if (!ORDER_STATUSES.includes(newStatus)) {
      throw new HttpsError("invalid-argument", "Invalid order status.");
    }

    const shopkeeper = await getApprovedShopkeeperOrThrow(uid);
    const orderRef = db.collection("orders").doc(orderId);

    await db.runTransaction(async (tx) => {
      const orderSnap = await tx.get(orderRef);

      if (!orderSnap.exists) {
        throw new HttpsError("not-found", "Order not found.");
      }

      const order = orderSnap.data();
      const currentStatus = cleanString(order.status).toLowerCase();

      if (!currentStatus) {
        throw new HttpsError(
          "failed-precondition",
          "Order current status is missing."
        );
      }

      if (order.shopId !== shopkeeper.shopId) {
        throw new HttpsError(
          "permission-denied",
          "This order does not belong to your shop."
        );
      }

      if (currentStatus === newStatus) {
        logger.info("Same status update ignored", {
          orderId,
          currentStatus,
          newStatus,
        });

        return;
      }

      const allowedNextStatuses = VALID_TRANSITIONS[currentStatus] || [];

      if (!allowedNextStatuses.includes(newStatus)) {
        throw new HttpsError(
          "failed-precondition",
          `Invalid order transition: ${currentStatus} to ${newStatus}.`
        );
      }

      const now = Date.now();

      const updates = {
        status: newStatus,
        updatedAt: now,
        updatedBy: uid,
      };

      if (
        (currentStatus === "pending" || currentStatus === "accepted") &&
        newStatus === "preparing"
      ) {
        updates.paymentStatus = "paid";
        updates.paymentVerifiedAt = now;
        updates.paymentVerifiedBy = uid;
      }

      if (newStatus === "ready") {
        updates.readyAt = now;
      }

      if (newStatus === "picked_up") {
        updates.pickedUpAt = now;
      }

      tx.update(orderRef, updates);
    });

    return {
      success: true,
      message: "Order status updated successfully.",
    };
  }
);

exports.cancelOrderByShopkeeper = onCall(
  { region: "us-central1" },
  async (request) => {
    const uid = requireAuth(request);

    const orderId = cleanString(request.data?.orderId);
    const reason =
      cleanString(request.data?.reason) ||
      cleanString(request.data?.cancelReason);

    const paymentReceivedType = cleanString(
      request.data?.paymentReceivedType || "none"
    ).toLowerCase();

    if (!orderId) {
      throw new HttpsError("invalid-argument", "orderId is required.");
    }

    if (!reason) {
      throw new HttpsError(
        "invalid-argument",
        "Cancellation reason is required."
      );
    }

    const shopkeeper = await getApprovedShopkeeperOrThrow(uid);
    const orderRef = db.collection("orders").doc(orderId);

    await db.runTransaction(async (tx) => {
      const orderSnap = await tx.get(orderRef);

      if (!orderSnap.exists) {
        throw new HttpsError("not-found", "Order not found.");
      }

      const order = orderSnap.data();
      const currentStatus = cleanString(order.status).toLowerCase();

      if (order.shopId !== shopkeeper.shopId) {
        throw new HttpsError(
          "permission-denied",
          "This order does not belong to your shop."
        );
      }

      if (!["pending", "accepted", "preparing", "ready"].includes(currentStatus)) {
        throw new HttpsError(
          "failed-precondition",
          `Order cannot be cancelled from status: ${currentStatus}.`
        );
      }

      const now = Date.now();
      const paymentFields = buildCancelPaymentFields(paymentReceivedType);

      const updates = {
        status: "cancelled",

        // Keep both names because your Android screens use cancelReason,
        // while earlier backend used cancellationReason.
        cancelReason: reason,
        cancellationReason: reason,

        cancelledBy: uid,
        cancelledByRole: "shopkeeper",
        cancelledAt: now,

        paymentReceivedByShopkeeper:
          paymentFields.paymentReceivedByShopkeeper,
        paymentReceivedType: paymentFields.paymentReceivedType,

        paymentStatus: paymentFields.paymentStatus,
        refundStatus: paymentFields.refundStatus,

        refundReferenceId: "",
        refundSettledAt: 0,
        refundNote: "",

        updatedAt: now,
        updatedBy: uid,
      };

      if (paymentFields.refundAmount === 0) {
        updates.refundAmount = 0;
      } else {
        updates.refundAmount = order.totalPrice || 0;
      }

      tx.update(orderRef, updates);
    });

    return {
      success: true,
      message: "Order cancelled successfully.",
    };
  }
);

exports.markRefundSettled = onCall(
  { region: "us-central1" },
  async (request) => {
    const uid = requireAuth(request);

    const actor = await getAdminOrShopkeeperOrThrow(uid);

    const orderId = cleanString(request.data?.orderId);
    const refundReferenceId = cleanString(request.data?.refundReferenceId);
    const refundNote = cleanString(request.data?.refundNote);

    if (!orderId) {
      throw new HttpsError("invalid-argument", "orderId is required.");
    }

    if (!refundReferenceId) {
      throw new HttpsError(
        "invalid-argument",
        "Refund reference ID is required."
      );
    }

    const orderRef = db.collection("orders").doc(orderId);

    await db.runTransaction(async (tx) => {
      const orderSnap = await tx.get(orderRef);

      if (!orderSnap.exists) {
        throw new HttpsError("not-found", "Order not found.");
      }

      const order = orderSnap.data();

      if (actor.role === "shopkeeper" && order.shopId !== actor.shopId) {
        throw new HttpsError(
          "permission-denied",
          "This order does not belong to your shop."
        );
      }

      if (order.status !== "cancelled") {
        throw new HttpsError(
          "failed-precondition",
          "Refund can be settled only for cancelled orders."
        );
      }

      if (order.refundStatus !== "pending") {
        throw new HttpsError(
          "failed-precondition",
          "This order is not in refund pending state."
        );
      }

      const now = Date.now();

      tx.update(orderRef, {
        paymentStatus: "refunded",
        refundStatus: "settled",
        refundReferenceId,
        refundNote,
        refundSettledAt: now,
        refundSettledBy: uid,
        updatedAt: now,
        updatedBy: uid,
      });
    });

    return {
      success: true,
      message: "Refund marked as settled.",
    };
  }
);

exports.sendOrderReadyNotification = onDocumentUpdated(
  {
    region: "us-central1",
    document: "orders/{orderId}",
  },
  async (event) => {
    const before = event.data?.before?.data();
    const after = event.data?.after?.data();

    if (!before || !after) {
      return;
    }

    const beforeStatus = cleanString(before.status).toLowerCase();
    const afterStatus = cleanString(after.status).toLowerCase();

    if (beforeStatus === afterStatus) {
      return;
    }

    const notifiableStatuses = [
      "preparing",
      "ready",
      "cancelled",
      "picked_up",
    ];

    if (!notifiableStatuses.includes(afterStatus)) {
      return;
    }

    const orderId = event.params.orderId;
    const studentId = after.studentId;

    if (!studentId) {
      logger.warn("Order status changed but studentId missing", {
        orderId,
        afterStatus,
      });
      return;
    }

    const studentSnap = await db.collection("users").doc(studentId).get();

    if (!studentSnap.exists) {
      logger.warn("Student not found for notification", {
        orderId,
        studentId,
      });
      return;
    }

    const student = studentSnap.data();

    const tokens = [];

    if (typeof student.fcmToken === "string" && student.fcmToken.trim()) {
      tokens.push(student.fcmToken.trim());
    }

    if (Array.isArray(student.fcmTokens)) {
      student.fcmTokens.forEach((token) => {
        if (typeof token === "string" && token.trim()) {
          tokens.push(token.trim());
        }
      });
    }

    const uniqueTokens = [...new Set(tokens)];

    if (uniqueTokens.length === 0) {
      logger.warn("No FCM token found for student", {
        orderId,
        studentId,
        afterStatus,
      });
      return;
    }

    const content = getNotificationContent(afterStatus, after);

    const message = {
      tokens: uniqueTokens,

      notification: {
        title: content.title,
        body: content.body,
      },

      data: {
        orderId: String(orderId),
        status: String(afterStatus),
        title: String(content.title),
        body: String(content.body),
      },

      android: {
        priority: "high",
        ttl: 60 * 60 * 1000,
        notification: {
          channelId: "order_updates_high",
          sound: "default",
          priority: "high",
          defaultSound: true,
        },
      },
    };

    const response = await admin.messaging().sendEachForMulticast(message);

    logger.info("Order notification sent", {
      orderId,
      studentId,
      status: afterStatus,
      successCount: response.successCount,
      failureCount: response.failureCount,
    });

    const invalidTokens = [];

    response.responses.forEach((result, index) => {
      if (!result.success) {
        const errorCode = result.error?.code;

        logger.warn("FCM send failed", {
          orderId,
          studentId,
          status: afterStatus,
          token: uniqueTokens[index],
          error: result.error?.message,
          code: errorCode,
        });

        if (
          errorCode === "messaging/registration-token-not-registered" ||
          errorCode === "messaging/invalid-registration-token"
        ) {
          invalidTokens.push(uniqueTokens[index]);
        }
      }
    });

    if (invalidTokens.length === 0) {
      return;
    }

    const updateData = {
      fcmTokens: FieldValue.arrayRemove(...invalidTokens),
    };

    if (
      typeof student.fcmToken === "string" &&
      invalidTokens.includes(student.fcmToken)
    ) {
      updateData.fcmToken = FieldValue.delete();
    }

    await db.collection("users").doc(studentId).update(updateData);

    logger.info("Invalid FCM tokens removed", {
      studentId,
      removedCount: invalidTokens.length,
    });
  }
);
exports.sendNewOrderNotificationToShopkeeper = onDocumentCreated(
  {
    region: "us-central1",
    document: "orders/{orderId}",
  },
  async (event) => {
    const order = event.data?.data();

    if (!order) {
      return;
    }

    const orderId = event.params.orderId;
    const shopId = cleanString(order.shopId);

    if (!shopId) {
      logger.warn("New order created but shopId missing", { orderId });
      return;
    }

    const shopkeepersSnap = await db.collection("users")
      .where("role", "==", "shopkeeper")
      .where("shopId", "==", shopId)
      .where("isApproved", "==", true)
      .get();

    if (shopkeepersSnap.empty) {
      logger.warn("No approved shopkeeper found for shop", {
        orderId,
        shopId,
      });
      return;
    }

    const tokens = [];

    shopkeepersSnap.docs.forEach((doc) => {
      const shopkeeper = doc.data();

      if (shopkeeper.isBlocked === true) {
        return;
      }

      if (typeof shopkeeper.fcmToken === "string" && shopkeeper.fcmToken.trim()) {
        tokens.push(shopkeeper.fcmToken.trim());
      }

      if (Array.isArray(shopkeeper.fcmTokens)) {
        shopkeeper.fcmTokens.forEach((token) => {
          if (typeof token === "string" && token.trim()) {
            tokens.push(token.trim());
          }
        });
      }
    });

    const uniqueTokens = [...new Set(tokens)];

    if (uniqueTokens.length === 0) {
      logger.warn("No FCM token found for shopkeeper", {
        orderId,
        shopId,
      });
      return;
    }

    const customerName = cleanString(order.studentName) || "A student";
    const totalPrice = order.totalPrice || 0;

    const message = {
      tokens: uniqueTokens,

      notification: {
        title: "New Order Received",
        body: `${customerName} placed a new order of ₹${totalPrice}.`,
      },

      data: {
        type: "new_order",
        orderId: String(orderId),
        status: "pending",
        title: "New Order Received",
        body: `${customerName} placed a new order of ₹${totalPrice}.`,
      },

      android: {
        priority: "high",
        ttl: 60 * 60 * 1000,
        notification: {
         channelId: "order_updates_high",
          sound: "default",
          priority: "high",
          defaultSound: true,
        },
      },
    };

    const response = await admin.messaging().sendEachForMulticast(message);

    logger.info("New order notification sent to shopkeeper", {
      orderId,
      shopId,
      successCount: response.successCount,
      failureCount: response.failureCount,
    });
  }
);

exports.updateShopAnalyticsOnOrderChange = onDocumentUpdated(
  {
    region: "us-central1",
    document: "orders/{orderId}",
  },
  async (event) => {
    const before = event.data?.before?.data();
    const after = event.data?.after?.data();

    if (!before || !after) {
      return;
    }

    const orderId = event.params.orderId;

    const beforePaymentStatus = cleanString(before.paymentStatus).toLowerCase();
    const afterPaymentStatus = cleanString(after.paymentStatus).toLowerCase();

    const beforeStatus = cleanString(before.status).toLowerCase();
    const afterStatus = cleanString(after.status).toLowerCase();

    const paymentBecamePaid =
      beforePaymentStatus !== "paid" && afterPaymentStatus === "paid";

    const orderBecameCancelled =
      beforeStatus !== "cancelled" && afterStatus === "cancelled";

    if (!paymentBecamePaid && !orderBecameCancelled) {
      return;
    }

    const shopId = cleanString(after.shopId);

    if (!shopId) {
      logger.warn("Analytics update skipped because shopId missing", {
        orderId,
      });
      return;
    }

    const pickupDate = cleanString(after.pickupDate);

    const today = new Date();
    const fallbackDate = today.toISOString().slice(0, 10);

    const dateKey =
      pickupDate.length >= 10 ? pickupDate.slice(0, 10) : fallbackDate;

    const monthKey = dateKey.slice(0, 7);

    const totalPrice = Number(after.totalPrice || 0);

    const dailyRef = db
      .collection("shopAnalytics")
      .doc(shopId)
      .collection("daily")
      .doc(dateKey);

    const monthlyRef = db
      .collection("shopAnalytics")
      .doc(shopId)
      .collection("monthly")
      .doc(monthKey);

    const lifetimeRef = db
      .collection("shopAnalytics")
      .doc(shopId)
      .collection("lifetime")
      .doc("summary");

    const updates = {
      updatedAt: Date.now(),
    };

    if (paymentBecamePaid) {
      updates.verifiedOrders = FieldValue.increment(1);
      updates.verifiedSales = FieldValue.increment(totalPrice);
    }

    if (orderBecameCancelled) {
      updates.cancelledOrders = FieldValue.increment(1);
    }

    const batch = db.batch();

    batch.set(dailyRef, updates, { merge: true });
    batch.set(monthlyRef, updates, { merge: true });
    batch.set(lifetimeRef, updates, { merge: true });

    await batch.commit();

    logger.info("Shop analytics updated", {
      orderId,
      shopId,
      dateKey,
      monthKey,
      paymentBecamePaid,
      orderBecameCancelled,
      totalPrice,
    });
  }
);