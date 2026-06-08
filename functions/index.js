const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { onDocumentUpdated } = require("firebase-functions/v2/firestore");
const admin = require("firebase-admin");

admin.initializeApp();

const db = admin.firestore();

const ORDER_STATUS = {
  PENDING: "pending",
  ACCEPTED: "accepted",
  PREPARING: "preparing",
  READY: "ready",
  PICKED_UP: "picked_up",
  CANCELLED: "cancelled",
};

const PAYMENT_STATUS = {
  PENDING_VERIFICATION: "pending_verification",
  VERIFIED: "verified",
  PAID: "paid",
};

function getString(value) {
  return typeof value === "string" ? value.trim() : "";
}

function getLowerString(value) {
  return getString(value).toLowerCase();
}

function getNumber(value) {
  if (typeof value === "number") return value;

  if (typeof value === "string") {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : 0;
  }

  return 0;
}

function dailyAnalyticsRef(shopId, date) {
  return db
    .collection("shopAnalytics")
    .doc(shopId)
    .collection("daily")
    .doc(date);
}

function monthlyAnalyticsRef(shopId, month) {
  return db
    .collection("shopAnalytics")
    .doc(shopId)
    .collection("monthly")
    .doc(month);
}

function lifetimeAnalyticsRef(shopId) {
  return db
    .collection("shopAnalytics")
    .doc(shopId)
    .collection("lifetime")
    .doc("summary");
}

function incrementVerifiedAnalytics(transaction, shopId, pickupDate, month, amount) {
  const update = {
    verifiedOrders: admin.firestore.FieldValue.increment(1),
    verifiedSales: admin.firestore.FieldValue.increment(amount),
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  };

  transaction.set(dailyAnalyticsRef(shopId, pickupDate), update, { merge: true });
  transaction.set(monthlyAnalyticsRef(shopId, month), update, { merge: true });
  transaction.set(lifetimeAnalyticsRef(shopId), update, { merge: true });
}

exports.updateOrderStatus = onCall(async (request) => {
  const uid = request.auth && request.auth.uid;

  if (!uid) {
    throw new HttpsError("unauthenticated", "User must be logged in.");
  }

  const data = request.data || {};
  const orderId = getString(data.orderId);
  const newStatus = getLowerString(data.newStatus);

  if (!orderId) {
    throw new HttpsError("invalid-argument", "Order ID is required.");
  }

  if (!newStatus) {
    throw new HttpsError("invalid-argument", "New order status is required.");
  }

  const allowedStatuses = [
    ORDER_STATUS.ACCEPTED,
    ORDER_STATUS.PREPARING,
    ORDER_STATUS.READY,
    ORDER_STATUS.PICKED_UP,
  ];

  if (!allowedStatuses.includes(newStatus)) {
    throw new HttpsError("invalid-argument", "Invalid order status.");
  }

  const userRef = db.collection("users").doc(uid);
  const orderRef = db.collection("orders").doc(orderId);

  return await db.runTransaction(async (transaction) => {
    const userSnap = await transaction.get(userRef);
    const orderSnap = await transaction.get(orderRef);

    if (!userSnap.exists) {
      throw new HttpsError("not-found", "User not found.");
    }

    if (!orderSnap.exists) {
      throw new HttpsError("not-found", "Order not found.");
    }

    const user = userSnap.data() || {};
    const order = orderSnap.data() || {};

    const role = getLowerString(user.role);
    const userShopId = getString(user.shopId);
    const isApproved = user.isApproved === true;
    const isBlocked = user.isBlocked === true;

    if (role !== "shopkeeper") {
      throw new HttpsError(
        "permission-denied",
        "Only shopkeepers can update order status."
      );
    }

    if (isBlocked) {
      throw new HttpsError("permission-denied", "Your account is blocked.");
    }

    if (!isApproved) {
      throw new HttpsError(
        "permission-denied",
        "Shopkeeper is not approved yet."
      );
    }

    if (!userShopId) {
      throw new HttpsError(
        "failed-precondition",
        "Shopkeeper has no assigned shop."
      );
    }

    const orderShopId = getString(order.shopId);

    if (orderShopId !== userShopId) {
      throw new HttpsError(
        "permission-denied",
        "This order does not belong to your shop."
      );
    }

    const oldStatus = getLowerString(order.status);
    const oldPaymentStatus = getLowerString(order.paymentStatus);

    if (oldStatus === ORDER_STATUS.CANCELLED) {
      throw new HttpsError(
        "failed-precondition",
        "Cancelled order cannot be updated."
      );
    }

    if (oldStatus === ORDER_STATUS.PICKED_UP) {
      throw new HttpsError(
        "failed-precondition",
        "Picked up order cannot be updated."
      );
    }

    const validTransitions = {
      [ORDER_STATUS.PENDING]: [
        ORDER_STATUS.ACCEPTED,
        ORDER_STATUS.PREPARING,
      ],
      [ORDER_STATUS.ACCEPTED]: [ORDER_STATUS.PREPARING],
      [ORDER_STATUS.PREPARING]: [ORDER_STATUS.READY],
      [ORDER_STATUS.READY]: [ORDER_STATUS.PICKED_UP],
    };

    const nextAllowedStatuses = validTransitions[oldStatus] || [];

    if (!nextAllowedStatuses.includes(newStatus)) {
      throw new HttpsError(
        "failed-precondition",
        `Cannot change order from ${oldStatus || "unknown"} to ${newStatus}.`
      );
    }

    const updates = {
      status: newStatus,
      updatedAt: Date.now(),
      updatedBy: uid,
    };

    const shouldVerifyPayment =
      newStatus === ORDER_STATUS.PREPARING &&
      oldPaymentStatus !== PAYMENT_STATUS.VERIFIED &&
      oldPaymentStatus !== PAYMENT_STATUS.PAID;

    if (shouldVerifyPayment) {
      updates.paymentStatus = PAYMENT_STATUS.VERIFIED;
    }

    transaction.update(orderRef, updates);

    if (shouldVerifyPayment) {
      const pickupDate =
        getString(order.pickupDate) || new Date().toISOString().slice(0, 10);

      const month = pickupDate.slice(0, 7);
      const totalPrice = getNumber(order.totalPrice);

      incrementVerifiedAnalytics(
        transaction,
        userShopId,
        pickupDate,
        month,
        totalPrice
      );
    }

    return {
      success: true,
      orderId,
      oldStatus,
      newStatus,
    };
  });
});

exports.sendOrderReadyNotification = onDocumentUpdated(
  "orders/{orderId}",
  async (event) => {
    if (!event.data) {
      return null;
    }

    const before = event.data.before.data() || {};
    const after = event.data.after.data() || {};

    if (before.status === after.status) {
      return null;
    }

    if (after.status !== ORDER_STATUS.READY) {
      return null;
    }

    const studentId = getString(after.studentId);

    if (!studentId) {
      return null;
    }

    const userDoc = await db.collection("users").doc(studentId).get();
    const user = userDoc.data() || {};
    const fcmToken = getString(user.fcmToken);

    if (!fcmToken) {
      return null;
    }

    const message = {
      token: fcmToken,
      notification: {
        title: "Order Ready",
        body: "Your order is ready for pickup.",
      },
      data: {
        orderId: event.params.orderId,
        status: ORDER_STATUS.READY,
      },
    };

    return admin.messaging().send(message);
  }
);