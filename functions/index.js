const functions = require("firebase-functions");
const admin = require("firebase-admin");
admin.initializeApp();

// 🚨 Trigger when a new alert document is created
exports.sendEmergencyPush = functions.firestore
  .document("alerts/{alertId}")
  .onCreate(async (snap, context) => {
    const alert = snap.data();
    const toUid = alert.toUid;
    const senderName = alert.fromName || "Unknown";
    const senderPhone = alert.fromPhone || "Unknown";

    console.log("🚨 New emergency alert for UID:", toUid);

    // Get receiver’s FCM token from Firestore
    const userDoc = await admin.firestore().collection("users").doc(toUid).get();
    const token = userDoc.get("fcmToken");

    if (!token) {
      console.log("❌ No FCM token for user", toUid);
      return null;
    }

    // Build push message
    const message = {
      token: token,
      android: {
        priority: "high",
        notification: {
          title: `🚨 Emergency Alert from ${senderName}`,
          body: `${senderName} (${senderPhone}) needs help!`,
          sound: "default",
          channelId: "emergency_alert_channel",
          clickAction: "OPEN_ALERT_POPUP"
        }
      },
      data: {
        senderName: senderName,
        senderPhone: senderPhone,
        message: "Emergency alert!"
      }
    };

    try {
      await admin.messaging().send(message);
      console.log("✅ FCM sent successfully to", toUid);
    } catch (error) {
      console.error("🔥 FCM send failed:", error);
    }

    return null;
  });
