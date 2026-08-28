const crypto = require("crypto");
const admin = require("firebase-admin");
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const {
  onDocumentCreated,
  onDocumentUpdated,
} = require("firebase-functions/v2/firestore");
const logger = require("firebase-functions/logger");

admin.initializeApp();

const db = admin.firestore();
const FieldValue = admin.firestore.FieldValue;
const Timestamp = admin.firestore.Timestamp;

// TTL for idempotency-marker collections (processedNotificationEvents,
// processedAnalyticsEvents). These docs exist only to dedup at-least-once
// Cloud Functions/Eventarc retries and are never read again after that
// short window, so they are expired automatically via a Firestore TTL
// policy on `expiresAt` rather than kept forever or cleaned up manually.
const PROCESSED_EVENT_TTL_MS = 7 * 24 * 60 * 60 * 1000;

function processedEventExpiresAt() {
  return Timestamp.fromMillis(Date.now() + PROCESSED_EVENT_TTL_MS);
}

const REGION = "us-central1";
const ORDER_NOTIFICATION_CHANNEL_ID = "campusbite_order_alerts_v2";
const MAX_CART_ITEMS = 30;
const MAX_ITEM_QUANTITY = 20;
const MAX_COOKING_NOTE_LENGTH = 300;
const MAX_UPI_PAYER_NAME_LENGTH = 80;
const VALID_PAYMENT_METHODS = ["UPI_QR"];
const CLIENT_REQUEST_ID_REGEX = /^[A-Za-z0-9_-]{1,64}$/;
const PICKUP_DATE_REGEX = /^\d{4}-\d{2}-\d{2}$/;
const PICKUP_SLOT_REGEX = /^(0[1-9]|1[0-2]):[0-5]\d (AM|PM)$/;
const DEFAULT_MAX_ORDERS_PER_SLOT = 5;
const CREATE_ORDER_RATE_LIMIT_MAX_ATTEMPTS = 5;
const CREATE_ORDER_RATE_LIMIT_WINDOW_MS = 60 * 1000;

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

// Payment statuses written only by Cloud Functions.
const PAYMENT_STATUS = {
  PENDING_VERIFICATION: "pending_verification",
  PAID: "paid",
  PAYMENT_NOT_RECEIVED: "payment_not_received",
  REFUND_PENDING: "refund_pending",
  REFUNDED: "refunded",
};

const REFUND_STATUS = {
  NONE: "none",
  PENDING: "pending",
  SETTLED: "settled",
};

const PAYMENT_VERIFIABLE = new Set([
  PAYMENT_STATUS.PENDING_VERIFICATION,
]);

const PAYMENT_TERMINAL = new Set([
  PAYMENT_STATUS.PAYMENT_NOT_RECEIVED,
  PAYMENT_STATUS.REFUND_PENDING,
  PAYMENT_STATUS.REFUNDED,
]);

function requireAuth(request) {
  const uid = request.auth?.uid;

  if (!uid) {
    throw new HttpsError("unauthenticated", "Login required.");
  }

  return uid;
}

function logIfUnexpected(functionName, context, err) {
  if (err instanceof HttpsError) {
    return;
  }

  logger.error(`${functionName} failed unexpectedly`, {
    ...context,
    code: err.code || "unknown",
    message: err.message,
  });
}

function cleanString(value) {
  return String(value || "").trim();
}

function isValidCalendarDate(dateStr) {
  const [year, month, day] = dateStr.split("-").map(Number);
  const date = new Date(Date.UTC(year, month - 1, day));

  return (
    date.getUTCFullYear() === year &&
    date.getUTCMonth() === month - 1 &&
    date.getUTCDate() === day
  );
}

function getUniqueTokens(user) {
  const tokens = [];

  if (typeof user.fcmToken === "string" && user.fcmToken.trim()) {
    tokens.push(user.fcmToken.trim());
  }

  if (Array.isArray(user.fcmTokens)) {
    user.fcmTokens.forEach((token) => {
      if (typeof token === "string" && token.trim()) {
        tokens.push(token.trim());
      }
    });
  }

  return [...new Set(tokens)];
}

function getInvalidTokens(response, tokens) {
  const invalidTokens = [];

  response.responses.forEach((result, index) => {
    if (!result.success) {
      const errorCode = result.error?.code;

      if (
        errorCode === "messaging/registration-token-not-registered" ||
        errorCode === "messaging/invalid-registration-token"
      ) {
        invalidTokens.push(tokens[index]);
      }
    }
  });

  return invalidTokens;
}

async function removeInvalidTokensFromUser(userId, user, invalidTokens) {
  if (invalidTokens.length === 0) {
    return;
  }

  const updateData = {
    fcmTokens: FieldValue.arrayRemove(...invalidTokens),
  };

  if (
    typeof user.fcmToken === "string" &&
    invalidTokens.includes(user.fcmToken)
  ) {
    updateData.fcmToken = FieldValue.delete();
  }

  await db.collection("users").doc(userId).update(updateData);
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

function buildCancelPaymentFields(
  paymentReceivedType,
  paymentReceivedAmount,
  totalPrice
) {
  const cleanType = cleanString(paymentReceivedType).toLowerCase();
  const cleanTotalPrice = Number(totalPrice || 0);
  const cleanReceivedAmount = Number(paymentReceivedAmount || 0);

  if (cleanType === "full") {
    if (cleanTotalPrice <= 0) {
      throw new HttpsError(
        "failed-precondition",
        "Order total amount is missing."
      );
    }

    return {
      paymentReceivedType: "full",
      paymentReceivedByShopkeeper: true,
      paymentReceivedAmount: cleanTotalPrice,
      paymentStatus: "refund_pending",
      refundStatus: "pending",
      refundAmount: cleanTotalPrice,
    };
  }

  if (cleanType === "partial") {
    if (cleanTotalPrice <= 0) {
      throw new HttpsError(
        "failed-precondition",
        "Order total amount is missing."
      );
    }

    if (cleanReceivedAmount <= 0) {
      throw new HttpsError(
        "invalid-argument",
        "Enter the amount received."
      );
    }

    if (cleanReceivedAmount >= cleanTotalPrice) {
      throw new HttpsError(
        "invalid-argument",
        "For full amount, select Full payment received."
      );
    }

    return {
      paymentReceivedType: "partial",
      paymentReceivedByShopkeeper: true,
      paymentReceivedAmount: cleanReceivedAmount,
      paymentStatus: "refund_pending",
      refundStatus: "pending",
      refundAmount: cleanReceivedAmount,
    };
  }

  return {
    paymentReceivedType: "none",
    paymentReceivedByShopkeeper: false,
    paymentReceivedAmount: 0,
    paymentStatus: "payment_not_received",
    refundStatus: "none",
    refundAmount: 0,
  };
}

function validateCreateOrderRequest(data) {
  const clientRequestId = cleanString(data?.clientRequestId);
  const shopId = cleanString(data?.shopId);
  const pickupSlot = cleanString(data?.pickupSlot);
  const pickupDate = cleanString(data?.pickupDate);
  const paymentMethod = cleanString(data?.paymentMethod);
  const upiPayerName = cleanString(data?.upiPayerName);
  const items = Array.isArray(data?.items) ? data.items : null;

  if (!clientRequestId) {
    throw new HttpsError(
      "invalid-argument",
      "clientRequestId is required."
    );
  }

  if (!CLIENT_REQUEST_ID_REGEX.test(clientRequestId)) {
    throw new HttpsError(
      "invalid-argument",
      "clientRequestId format is invalid."
    );
  }

  if (!shopId) {
    throw new HttpsError("invalid-argument", "shopId is required.");
  }

  if (!pickupSlot) {
    throw new HttpsError("invalid-argument", "pickupSlot is required.");
  }

  if (!PICKUP_SLOT_REGEX.test(pickupSlot)) {
    throw new HttpsError("invalid-argument", "pickupSlot format is invalid.");
  }

  if (!pickupDate) {
    throw new HttpsError("invalid-argument", "pickupDate is required.");
  }

  if (!PICKUP_DATE_REGEX.test(pickupDate) || !isValidCalendarDate(pickupDate)) {
    throw new HttpsError("invalid-argument", "pickupDate format is invalid.");
  }

  if (!paymentMethod) {
    throw new HttpsError("invalid-argument", "paymentMethod is required.");
  }

  if (!VALID_PAYMENT_METHODS.includes(paymentMethod)) {
    throw new HttpsError("invalid-argument", "paymentMethod is invalid.");
  }

  if (!upiPayerName) {
    throw new HttpsError("invalid-argument", "upiPayerName is required.");
  }

  if (upiPayerName.length > MAX_UPI_PAYER_NAME_LENGTH) {
    throw new HttpsError(
      "invalid-argument",
      `upiPayerName must not exceed ${MAX_UPI_PAYER_NAME_LENGTH} characters.`
    );
  }

  if (!items || items.length === 0) {
    throw new HttpsError(
      "invalid-argument",
      "items must be a non-empty array."
    );
  }

  if (items.length > MAX_CART_ITEMS) {
    throw new HttpsError(
      "invalid-argument",
      `Cart cannot contain more than ${MAX_CART_ITEMS} items.`
    );
  }

  const seenItemIds = new Set();

  const cleanedItems = items.map((item, index) => {
    const itemId = cleanString(item?.itemId);
    const quantity = Number(item?.quantity);
    const cookingNote = cleanString(item?.cookingNote);

    if (!itemId) {
      throw new HttpsError(
        "invalid-argument",
        `items[${index}].itemId is required.`
      );
    }

    if (seenItemIds.has(itemId)) {
      throw new HttpsError(
        "invalid-argument",
        `Duplicate itemId in cart: ${itemId}.`
      );
    }

    seenItemIds.add(itemId);

    if (
      !Number.isInteger(quantity) ||
      quantity <= 0 ||
      quantity > MAX_ITEM_QUANTITY
    ) {
      throw new HttpsError(
        "invalid-argument",
        `items[${index}].quantity must be a positive integer no greater than ${MAX_ITEM_QUANTITY}.`
      );
    }

    if (cookingNote.length > MAX_COOKING_NOTE_LENGTH) {
      throw new HttpsError(
        "invalid-argument",
        `items[${index}].cookingNote must not exceed ${MAX_COOKING_NOTE_LENGTH} characters.`
      );
    }

    return {
      itemId,
      quantity,
      cookingNote,
    };
  });

  return {
    clientRequestId,
    shopId,
    pickupSlot,
    pickupDate,
    paymentMethod,
    upiPayerName,
    items: cleanedItems,
  };
}

function buildSlotId(shopId, date, slot) {
  return `${shopId}_${date}_${slot}`
    .replace(/ /g, "_")
    .replace(/:/g, "_")
    .replace(/\//g, "_");
}

function getMaxOrdersPerSlot(shop) {
  const value = Number(shop?.maxOrdersPerSlot);

  return Number.isInteger(value) && value > 0
    ? value
    : DEFAULT_MAX_ORDERS_PER_SLOT;
}

function isSlotClosed(shop, slotData, pickupSlot) {
  const closedSlots = Array.isArray(shop?.closedSlots) ? shop.closedSlots : [];

  return closedSlots.includes(pickupSlot) || slotData?.isClosed === true;
}

function computeCartHash(shopId, items) {
  const sortedItems = [...items]
    .map((item) => ({
      itemId: item.itemId,
      quantity: item.quantity,
    }))
    .sort((a, b) => a.itemId.localeCompare(b.itemId));

  const payload = JSON.stringify({
    shopId,
    items: sortedItems,
  });

  return crypto.createHash("sha256").update(payload).digest("hex");
}

function getStudentNotificationContent(status, order) {
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

async function sendMulticastNotification({
  tokens,
  title,
  body,
  data,
}) {
  const message = {
    tokens,
    notification: {
      title,
      body,
    },
    data,
    android: {
      priority: "high",
      ttl: 60 * 60 * 1000,
      notification: {
        channelId: ORDER_NOTIFICATION_CHANNEL_ID,
        sound: "default",
        priority: "max",
        defaultSound: true,
        defaultVibrateTimings: true,
      },
    },
  };

  return admin.messaging().sendEachForMulticast(message);
}

exports.updateOrderStatus = onCall(
  {
    region: REGION,
    enforceAppCheck: true,
  },
  async (request) => {
    const uid = requireAuth(request);

    const orderId = cleanString(request.data?.orderId);
    const newStatus = cleanString(request.data?.newStatus).toLowerCase();

    try {
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

        // One-way, idempotent payment verification on start-preparing.
        if (
          (currentStatus === "pending" || currentStatus === "accepted") &&
          newStatus === "preparing"
        ) {
          const currentPaymentStatus = cleanString(order.paymentStatus).toLowerCase();

          if (PAYMENT_TERMINAL.has(currentPaymentStatus)) {
            throw new HttpsError(
              "failed-precondition",
              `Cannot mark order preparing: payment is in '${currentPaymentStatus}' state.`
            );
          }

          if (currentPaymentStatus === PAYMENT_STATUS.PAID) {
            // Already verified — keep existing verification metadata.
          } else if (PAYMENT_VERIFIABLE.has(currentPaymentStatus)) {
            updates.paymentStatus = PAYMENT_STATUS.PAID;
            updates.paymentVerifiedAt = now;
            updates.paymentVerifiedBy = uid;
          } else {
            throw new HttpsError(
              "failed-precondition",
              `Cannot verify payment from status '${currentPaymentStatus || "unknown"}'.`
            );
          }
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
    } catch (err) {
      logIfUnexpected("updateOrderStatus", { uid, orderId }, err);
      throw err;
    }
  }
);

exports.cancelOrderByShopkeeper = onCall(
  {
    region: REGION,
    enforceAppCheck: true,
  },
  async (request) => {
    const uid = requireAuth(request);

    const orderId = cleanString(request.data?.orderId);

    const reason =
      cleanString(request.data?.reason) ||
      cleanString(request.data?.cancelReason);

    const paymentReceivedType = cleanString(
      request.data?.paymentReceivedType || "none"
    ).toLowerCase();

    const paymentReceivedAmount = Number(
      request.data?.paymentReceivedAmount || 0
    );

    try {
      if (!orderId) {
        throw new HttpsError("invalid-argument", "orderId is required.");
      }

      if (!["none", "full", "partial"].includes(paymentReceivedType)) {
        throw new HttpsError("invalid-argument", "Invalid payment status.");
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

        if (currentStatus === "cancelled") {
          throw new HttpsError(
            "failed-precondition",
            "Order is already cancelled."
          );
        }

        if (
          !["pending", "accepted", "preparing", "ready"].includes(currentStatus)
        ) {
          throw new HttpsError(
            "failed-precondition",
            `Order cannot be cancelled from status: ${currentStatus}.`
          );
        }

        const currentPaymentStatus = cleanString(order.paymentStatus).toLowerCase();
        const currentRefundStatus = cleanString(order.refundStatus).toLowerCase();

        if (
          currentPaymentStatus === PAYMENT_STATUS.REFUNDED ||
          currentRefundStatus === REFUND_STATUS.SETTLED
        ) {
          throw new HttpsError(
            "failed-precondition",
            "Payment/refund is already settled; cannot cancel again."
          );
        }

        if (currentPaymentStatus === PAYMENT_STATUS.REFUND_PENDING) {
          throw new HttpsError(
            "failed-precondition",
            "Order already cancelled with refund pending."
          );
        }

        // Unverified orders may only be cancelled as payment not received.
        let effectivePaymentReceivedType = paymentReceivedType;
        if (currentPaymentStatus === PAYMENT_STATUS.PENDING_VERIFICATION) {
          if (paymentReceivedType !== "none") {
            throw new HttpsError(
              "failed-precondition",
              "Payment was never verified; cancel as payment not received."
            );
          }
          effectivePaymentReceivedType = "none";
        }

        const paymentFields = buildCancelPaymentFields(
          effectivePaymentReceivedType,
          paymentReceivedAmount,
          order.totalPrice
        );

        const now = Date.now();

        const updates = {
          status: "cancelled",

          cancelReason: reason,
          cancellationReason: reason,

          cancelledBy: uid,
          cancelledByRole: "shopkeeper",
          cancelledAt: now,

          paymentReceivedByShopkeeper:
            paymentFields.paymentReceivedByShopkeeper,
          paymentReceivedType: paymentFields.paymentReceivedType,
          paymentReceivedAmount: paymentFields.paymentReceivedAmount,

          paymentStatus: paymentFields.paymentStatus,
          refundStatus: paymentFields.refundStatus,
          refundAmount: paymentFields.refundAmount,

          refundReferenceId: "",
          refundSettledAt: 0,
          refundNote: "",

          updatedAt: now,
          updatedBy: uid,
        };

        tx.update(orderRef, updates);
      });

      return {
        success: true,
        message: "Order cancelled successfully.",
      };
    } catch (err) {
      logIfUnexpected("cancelOrderByShopkeeper", { uid, orderId }, err);
      throw err;
    }
  }
);

exports.markRefundSettled = onCall(
  {
    region: REGION,
    enforceAppCheck: true,
  },
  async (request) => {
    const uid = requireAuth(request);

    const orderId = cleanString(request.data?.orderId);
    const refundReferenceId = cleanString(request.data?.refundReferenceId);
    const refundNote = cleanString(request.data?.refundNote);

    try {
      const actor = await getAdminOrShopkeeperOrThrow(uid);

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

        const currentPaymentStatus = cleanString(order.paymentStatus).toLowerCase();
        const currentRefundStatus = cleanString(order.refundStatus).toLowerCase();

        // Idempotent: same reference already settled → success no-op.
        if (
          currentRefundStatus === REFUND_STATUS.SETTLED &&
          currentPaymentStatus === PAYMENT_STATUS.REFUNDED
        ) {
          const existingRef = cleanString(order.refundReferenceId);
          if (existingRef && existingRef === refundReferenceId) {
            logger.info("markRefundSettled idempotent hit", { orderId });
            return;
          }
          throw new HttpsError(
            "failed-precondition",
            "Refund is already settled with a different reference."
          );
        }

        if (currentRefundStatus !== REFUND_STATUS.PENDING) {
          throw new HttpsError(
            "failed-precondition",
            "This order is not in refund pending state."
          );
        }

        if (currentPaymentStatus !== PAYMENT_STATUS.REFUND_PENDING) {
          throw new HttpsError(
            "failed-precondition",
            `Cannot settle refund from payment status '${currentPaymentStatus}'.`
          );
        }

        const now = Date.now();

        tx.update(orderRef, {
          paymentStatus: PAYMENT_STATUS.REFUNDED,
          refundStatus: REFUND_STATUS.SETTLED,
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
    } catch (err) {
      logIfUnexpected("markRefundSettled", { uid, orderId }, err);
      throw err;
    }
  }
);

exports.createOrder = onCall(
  {
    region: REGION,
    enforceAppCheck: true,
  },
  async (request) => {
    const uid = requireAuth(request);

    const {
      clientRequestId,
      shopId,
      pickupSlot,
      pickupDate,
      paymentMethod,
      upiPayerName,
      items,
    } = validateCreateOrderRequest(request.data);

    const cartHash = computeCartHash(shopId, items);

    const userRef = db.collection("users").doc(uid);
    const rateLimitRef = db.collection("rateLimits").doc(`createOrder_${uid}`);
    const orderRequestRef = db.collection("orderRequests").doc(clientRequestId);
    const shopRef = db.collection("shops").doc(shopId);
    const slotRef = db
      .collection("slotAvailability")
      .doc(buildSlotId(shopId, pickupDate, pickupSlot));
    const menuItemRefs = items.map((item) =>
      db.collection("menuItems").doc(item.itemId)
    );

    try {
      await db.runTransaction(async (rateTx) => {
        const rateLimitSnap = await rateTx.get(rateLimitRef);
        const rateLimitData = rateLimitSnap.exists ? rateLimitSnap.data() : null;
        const rateNow = Date.now();

        let windowStart = rateNow;
        let count = 1;

        if (
          rateLimitData &&
          rateNow - Number(rateLimitData.windowStart || 0) <
            CREATE_ORDER_RATE_LIMIT_WINDOW_MS
        ) {
          windowStart = Number(rateLimitData.windowStart);
          count = Number(rateLimitData.count || 0) + 1;

          if (count > CREATE_ORDER_RATE_LIMIT_MAX_ATTEMPTS) {
            throw new HttpsError(
              "resource-exhausted",
              "Too many order attempts. Please wait a moment and try again."
            );
          }
        }

        rateTx.set(rateLimitRef, {
          windowStart,
          count,
          updatedAt: rateNow,
        });
      });

      const result = await db.runTransaction(async (tx) => {
        const userSnap = await tx.get(userRef);

        if (!userSnap.exists) {
          throw new HttpsError("permission-denied", "User profile not found.");
        }

        const user = userSnap.data();

        if (user.isBlocked === true) {
          throw new HttpsError("permission-denied", "Your account is blocked.");
        }

        if (user.role !== "student") {
          throw new HttpsError(
            "permission-denied",
            "Only students can place orders."
          );
        }

        const now = Date.now();

        const orderRequestSnap = await tx.get(orderRequestRef);

        if (orderRequestSnap.exists) {
          const existingRequest = orderRequestSnap.data();

          if (existingRequest.cartHash === cartHash) {
            return {
              success: true,
              duplicate: true,
              orderId: existingRequest.orderId,
              message: "Order already created for this request.",
            };
          }

          throw new HttpsError(
            "already-exists",
            "This request ID was already used with a different cart."
          );
        }

        const shopSnap = await tx.get(shopRef);

        if (!shopSnap.exists) {
          throw new HttpsError("not-found", "Shop not found.");
        }

        const shop = shopSnap.data();
        const maxOrdersPerSlot = getMaxOrdersPerSlot(shop);

        const slotSnap = await tx.get(slotRef);
        const slotData = slotSnap.exists ? slotSnap.data() : null;
        const currentSlotOrderCount = Number(slotData?.orderCount || 0);

        if (isSlotClosed(shop, slotData, pickupSlot)) {
          throw new HttpsError(
            "failed-precondition",
            "This pickup slot is no longer available. Please select another slot."
          );
        }

        if (currentSlotOrderCount >= maxOrdersPerSlot) {
          throw new HttpsError(
            "failed-precondition",
            "This pickup slot is full. Please select another slot."
          );
        }

        const menuItemSnaps = await tx.getAll(...menuItemRefs);

        let totalPrice = 0;
        const orderItems = [];

        for (let i = 0; i < items.length; i++) {
          const requestedItem = items[i];
          const menuSnap = menuItemSnaps[i];

          if (!menuSnap.exists) {
            throw new HttpsError(
              "not-found",
              `Menu item not found: ${requestedItem.itemId}.`
            );
          }

          const menuItem = menuSnap.data();

          if (cleanString(menuItem.shopId) !== shopId) {
            throw new HttpsError(
              "failed-precondition",
              `Menu item ${requestedItem.itemId} does not belong to shop ${shopId}.`
            );
          }

          if (menuItem.isAvailable !== true) {
            throw new HttpsError(
              "failed-precondition",
              `Menu item ${requestedItem.itemId} is not available.`
            );
          }

          const unitPrice = Number(menuItem.price);

          if (!Number.isFinite(unitPrice) || unitPrice <= 0) {
            throw new HttpsError(
              "failed-precondition",
              `Menu item ${requestedItem.itemId} has an invalid price.`
            );
          }

          const lineTotal = unitPrice * requestedItem.quantity;
          totalPrice += lineTotal;

          orderItems.push({
            itemId: requestedItem.itemId,
            name: cleanString(menuItem.name),
            price: unitPrice,
            quantity: requestedItem.quantity,
            prepTimeMinutes: Number(menuItem.prepTimeMinutes || 0),
            shopId,
            cookingNote: requestedItem.cookingNote,
          });
        }

        totalPrice = Math.round(totalPrice * 100) / 100;

        const orderRef = db.collection("orders").doc();

        const orderData = {
          orderId: orderRef.id,
          shopId,
          shopName: cleanString(shop.name),
          shopkeeperPhone: cleanString(shop.phone),

          studentId: uid,
          studentName: cleanString(user.name),
          studentEmail: cleanString(user.email),
          studentPhone: cleanString(user.phone),

          items: orderItems,
          totalPrice,

          status: "pending",
          pickupSlot,
          pickupDate,

          paymentMethod,
          paymentStatus: "pending_verification",
          upiPayerName,

          cancelReason: "",
          cancelledBy: "",
          cancelledAt: 0,

          paymentReceivedByShopkeeper: false,
          paymentReceivedType: "none",

          refundStatus: "none",
          refundAmount: 0,
          refundReferenceId: "",
          refundSettledAt: 0,
          refundNote: "",

          clientRequestId,
          createdAt: now,
          updatedAt: now,
        };

        tx.set(orderRef, orderData);

        tx.set(orderRequestRef, {
          uid,
          orderId: orderRef.id,
          cartHash,
          createdAt: now,
        });

        tx.set(
          slotRef,
          {
            slotId: slotRef.id,
            shopId,
            date: pickupDate,
            slot: pickupSlot,
            maxOrders: maxOrdersPerSlot,
            orderCount: FieldValue.increment(1),
            updatedAt: now,
          },
          { merge: true }
        );

        return {
          success: true,
          duplicate: false,
          orderId: orderRef.id,
          message: "Order created successfully.",
        };
      });

      logger.info("createOrder completed", {
        uid,
        shopId,
        clientRequestId,
        orderId: result.orderId,
        duplicate: result.duplicate,
      });

      return result;
    } catch (err) {
      logIfUnexpected("createOrder", { uid, shopId, clientRequestId }, err);
      throw err;
    }
  }
);

exports.sendOrderStatusNotificationToStudent = onDocumentUpdated(
  {
    region: REGION,
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
    const studentId = cleanString(after.studentId);

    if (!studentId) {
      logger.warn("Order status changed but studentId missing", {
        orderId,
        afterStatus,
      });
      return;
    }

    // Idempotency guard: Cloud Functions/Eventarc triggers are at-least-once,
    // not exactly-once. event.id is the stable identifier of this logical
    // event and is unchanged across redeliveries/retries, so it is used as
    // the dedup key. The marker is created atomically before the FCM send;
    // a redelivered event finds the marker already present and returns
    // without sending a duplicate notification.
    const eventId = event.id;
    const processedEventRef = db
      .collection("processedNotificationEvents")
      .doc(eventId);

    try {
      await processedEventRef.create({
        orderId,
        studentId,
        status: afterStatus,
        processedAt: Date.now(),
        expiresAt: processedEventExpiresAt(),
      });
    } catch (err) {
      if (err.code === 6) {
        logger.info("Student notification event already processed; skipping", {
          orderId,
          eventId,
        });
        return;
      }
      throw err;
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
    const tokens = getUniqueTokens(student);

    if (tokens.length === 0) {
      logger.warn("No FCM token found for student", {
        orderId,
        studentId,
        afterStatus,
      });
      return;
    }

    const content = getStudentNotificationContent(afterStatus, after);

    const response = await sendMulticastNotification({
      tokens,
      title: content.title,
      body: content.body,
      data: {
        type: "order_update",
        orderId: String(orderId),
        status: String(afterStatus),
        title: String(content.title),
        body: String(content.body),
      },
    });

    logger.info("Order status notification sent to student", {
      orderId,
      studentId,
      status: afterStatus,
      successCount: response.successCount,
      failureCount: response.failureCount,
    });

    const invalidTokens = getInvalidTokens(response, tokens);
    await removeInvalidTokensFromUser(studentId, student, invalidTokens);
  }
);

exports.sendNewOrderNotificationToShopkeeper = onDocumentCreated(
  {
    region: REGION,
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
      logger.warn("New order created but shopId missing", {
        orderId,
      });
      return;
    }

    // Idempotency guard: Cloud Functions/Eventarc triggers are at-least-once,
    // not exactly-once. event.id is the stable identifier of this logical
    // event and is unchanged across redeliveries/retries, so it is used as
    // the dedup key. The marker is created atomically before the FCM send;
    // a redelivered event finds the marker already present and returns
    // without sending a duplicate notification.
    const eventId = event.id;
    const processedEventRef = db
      .collection("processedNotificationEvents")
      .doc(eventId);

    try {
      await processedEventRef.create({
        orderId,
        shopId,
        processedAt: Date.now(),
        expiresAt: processedEventExpiresAt(),
      });
    } catch (err) {
      if (err.code === 6) {
        logger.info("Shopkeeper notification event already processed; skipping", {
          orderId,
          eventId,
        });
        return;
      }
      throw err;
    }

    const shopkeepersSnap = await db
      .collection("users")
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
    const tokenOwners = [];

    shopkeepersSnap.docs.forEach((doc) => {
      const shopkeeper = doc.data();

      if (shopkeeper.isBlocked === true) {
        return;
      }

      const shopkeeperTokens = getUniqueTokens(shopkeeper);

      shopkeeperTokens.forEach((token) => {
        tokens.push(token);
        tokenOwners.push({
          token,
          userId: doc.id,
          user: shopkeeper,
        });
      });
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
    const totalPrice = Number(order.totalPrice || 0);

    const title = "New Order Received";
    const body = `${customerName} placed a new order of ₹${totalPrice}.`;

    const response = await sendMulticastNotification({
      tokens: uniqueTokens,
      title,
      body,
      data: {
        type: "new_order",
        orderId: String(orderId),
        status: "pending",
        title,
        body,
      },
    });

    logger.info("New order notification sent to shopkeeper", {
      orderId,
      shopId,
      successCount: response.successCount,
      failureCount: response.failureCount,
    });

    const invalidTokens = getInvalidTokens(response, uniqueTokens);

    if (invalidTokens.length === 0) {
      return;
    }

    // Group by userId before writing: multiple invalid tokens can belong to
    // the same shopkeeper (multi-device), so this avoids issuing several
    // sequential Firestore updates against the same user doc (which was
    // also wasteful when a single user had 2+ invalid tokens, since each
    // update only saw the pre-update snapshot's `user` object passed in
    // from the original query results, not any change from a prior write
    // in the same loop). Firestore's arrayRemove is safe to pass every
    // invalid token for that user in one call.
    const invalidTokensByUser = new Map();

    for (const token of invalidTokens) {
      const owner = tokenOwners.find((item) => item.token === token);

      if (!owner) {
        continue;
      }

      const existing = invalidTokensByUser.get(owner.userId);

      if (existing) {
        existing.tokens.push(token);
      } else {
        invalidTokensByUser.set(owner.userId, {
          user: owner.user,
          tokens: [token],
        });
      }
    }

    await Promise.all(
      Array.from(invalidTokensByUser.entries()).map(([userId, entry]) =>
        removeInvalidTokensFromUser(userId, entry.user, entry.tokens)
      )
    );
  }
);

// Releases the slot-capacity slot held by a cancelled order, exactly once.
//
// This is the single point where slotAvailability.orderCount is decremented,
// covering all 3 cancellation paths (cancelOrderByShopkeeper,
// updateOrderStatus, cancelOrderByAdmin) because all 3 converge on the same
// orders/{orderId} write, which this function is invoked in response to.
//
// Idempotency: correctness does not depend on Cloud Functions retry/redelivery
// semantics. It depends only on the order document's own "slotReleased" flag,
// which is read and written inside the SAME transaction as the decrement —
// so a duplicate invocation (retry, redelivery, or a race with another
// invocation for the same order) always observes either "not yet released"
// or "already released" as a consistent snapshot, never something in
// between. Firestore's optimistic-concurrency transaction retry handles the
// case where a concurrent createOrder increment (or a concurrent release
// attempt for a different order sharing the same slot) touches the same
// slotAvailability doc mid-transaction.
async function releaseSlotCapacityOnce(orderId) {
  const orderRef = db.collection("orders").doc(orderId);

  try {
    await db.runTransaction(async (tx) => {
      const orderSnap = await tx.get(orderRef);

      if (!orderSnap.exists) {
        return;
      }

      const order = orderSnap.data();

      // Re-check status from a fresh transactional read rather than trusting
      // the trigger's (possibly stale) event snapshot.
      if (cleanString(order.status).toLowerCase() !== "cancelled") {
        return;
      }

      if (order.slotReleased === true) {
        // Already released by a previous invocation (retry/redelivery) of
        // this trigger. No-op.
        return;
      }

      const now = Date.now();
      const shopId = cleanString(order.shopId);
      const pickupDate = cleanString(order.pickupDate);
      const pickupSlot = cleanString(order.pickupSlot);

      if (!shopId || !pickupDate || !pickupSlot) {
        logger.warn(
          "Slot release skipped: order missing shopId/pickupDate/pickupSlot",
          { orderId, shopId, pickupDate, pickupSlot }
        );

        // Nothing to release against, but mark released so this order is
        // never re-evaluated on future retries/redeliveries.
        tx.update(orderRef, {
          slotReleased: true,
          slotReleasedAt: now,
        });

        return;
      }

      const slotRef = db
        .collection("slotAvailability")
        .doc(buildSlotId(shopId, pickupDate, pickupSlot));

      const slotSnap = await tx.get(slotRef);

      if (slotSnap.exists) {
        const currentOrderCount = Number(slotSnap.data()?.orderCount || 0);

        // Never decrement below 0 (defensive floor — guards against any
        // pre-existing data drift from before this fix shipped).
        if (currentOrderCount > 0) {
          tx.set(
            slotRef,
            {
              orderCount: FieldValue.increment(-1),
              updatedAt: now,
            },
            { merge: true }
          );
        }
      }
      // If the slot doc doesn't exist, there's nothing to release against —
      // fall through and still mark the order released below.

      tx.update(orderRef, {
        slotReleased: true,
        slotReleasedAt: now,
      });
    });
  } catch (err) {
    // Deliberately swallowed: do not throw out of this helper. Letting this
    // propagate would fail the whole updateShopAnalyticsOnOrderChange
    // invocation and trigger a Cloud Functions retry, which would re-run the
    // (non-idempotent) analytics increments below and double-count them.
    // A slot that fails to release here is caught by the separate
    // orderCount reconciliation script, not by function-level retry.
    logger.error(
      "Slot release failed; leaving slotReleased unset for reconciliation",
      { orderId, error: err?.message || String(err) }
    );
  }
}

exports.updateShopAnalyticsOnOrderChange = onDocumentUpdated(
  {
    region: REGION,
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

    if (orderBecameCancelled) {
      // Independent of the analytics logic below (which requires shopId) —
      // releaseSlotCapacityOnce re-reads the order itself and handles a
      // missing shopId/pickupDate/pickupSlot on its own, so it must run
      // even if the analytics section below is about to bail out.
      await releaseSlotCapacityOnce(orderId);
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

    // Idempotency guard: Firestore/Eventarc triggers are at-least-once, not
    // exactly-once. event.id is the stable identifier of this logical event
    // and is unchanged across redeliveries/retries of the same event, so it
    // is used as the dedup key. The marker doc is created (not set) inside
    // the same transaction as the increments, so a duplicate delivery either
    // observes the marker already present (and applies no increments) or
    // creates it and applies increments exactly once — never both/neither.
    const eventId = event.id;
    const processedEventRef = db
      .collection("processedAnalyticsEvents")
      .doc(eventId);

    await db.runTransaction(async (tx) => {
      const processedSnap = await tx.get(processedEventRef);

      if (processedSnap.exists) {
        logger.info("Analytics event already processed; skipping", {
          orderId,
          eventId,
        });
        return;
      }

      tx.create(processedEventRef, {
        orderId,
        shopId,
        processedAt: Date.now(),
        expiresAt: processedEventExpiresAt(),
      });

      tx.set(dailyRef, updates, {
        merge: true,
      });

      tx.set(monthlyRef, updates, {
        merge: true,
      });

      tx.set(lifetimeRef, updates, {
        merge: true,
      });
    });

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