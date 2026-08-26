const functions = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();

/**
 * Triggered automatically when a new order document is created in Firestore.
 * Path: restaurants/{restId}/orders/{orderId}
 * Sends high-priority push notification (FCM) to all registered staff devices for this restaurant.
 */
exports.onNewOrderCreated = functions.firestore
  .document("restaurants/{restId}/orders/{orderId}")
  .onCreate(async (snap, context) => {
    const order = snap.data();
    const restId = context.params.restId;
    const orderId = context.params.orderId;

    if (!order) {
      console.log("No order data found");
      return null;
    }

    const tableLabel = order.tableLabel || "Masa";
    const totalPrice = order.totalPrice || 0;
    const itemCount = order.items ? order.items.reduce((sum, i) => sum + (i.quantity || 1), 0) : 0;

    console.log(`New Order received for Restaurant: ${restId}, Table: ${tableLabel}, OrderId: ${orderId}`);

    try {
      // Find all staff members for this restaurant
      const staffSnapshot = await admin.firestore()
        .collection("staff")
        .where("restaurantId", "==", restId)
        .get();

      const tokens = [];
      staffSnapshot.forEach((doc) => {
        const staffData = doc.data();
        if (staffData.fcmToken && typeof staffData.fcmToken === "string") {
          tokens.push(staffData.fcmToken);
        }
      });

      if (tokens.length === 0) {
        console.log("No active FCM tokens found for restaurant:", restId);
        return null;
      }

      const payload = {
        tokens: tokens,
        notification: {
          title: `🔔 Yeni Sipariş: ${tableLabel}`,
          body: `${itemCount} ürün • Toplam: ₺${totalPrice} - Dokunun ve görüntüleyin.`
        },
        data: {
          orderId: String(orderId),
          restaurantId: String(restId),
          tableLabel: String(tableLabel),
          click_action: "FLUTTER_NOTIFICATION_CLICK"
        },
        android: {
          priority: "high",
          notification: {
            sound: "default",
            channelId: "orders_channel",
            priority: "max"
          }
        }
      };

      const response = await admin.messaging().sendEachForMulticast(payload);
      console.log(`Successfully sent FCM notifications: ${response.successCount} success, ${response.failureCount} failed.`);
      return response;

    } catch (error) {
      console.error("Error sending order notification via FCM:", error);
      return null;
    }
  });
