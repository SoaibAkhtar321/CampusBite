const functions = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();

exports.sendOrderReadyNotification = functions.firestore
    .document("orders/{orderId}")
    .onUpdate(async (change, context) => {

        const before = change.before.data();
        const after = change.after.data();

        if (before.status === after.status) {
            return null;
        }

        if (after.status !== "ready") {
            return null;
        }

        const studentId = after.studentId;

        if (!studentId) {
            return null;
        }

        const userDoc = await admin
            .firestore()
            .collection("users")
            .doc(studentId)
            .get();

        const fcmToken = userDoc.data()?.fcmToken;

        if (!fcmToken) {
            return null;
        }

        const message = {
            token: fcmToken,
            notification: {
                title: "Order Ready 🎉",
                body: "Your order is ready for pickup.",
            },
            data: {
                orderId: context.params.orderId,
                status: "ready",
            },
        };

        return admin.messaging().send(message);
    });