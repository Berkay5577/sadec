const functions = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();

/**
 * Yeni sipariş oluşturulduğunda TÜM kayıtlı cihazlara push bildirim gönder.
 * - Android: staff koleksiyonundaki fcmToken
 * - Web/iPhone PWA: pushTokens koleksiyonundaki token
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
    const customerName = order.customerName || "";
    const totalPrice = order.totalPrice || 0;
    const itemCount = order.items ? order.items.reduce((sum, i) => sum + (i.quantity || 1), 0) : 0;
    const firstItems = (order.items || []).slice(0, 3).map(i => i.name).join(", ");

    console.log(`New Order: Restaurant=${restId}, Table=${tableLabel}, OrderId=${orderId}`);

    try {
      const tokens = [];

      // 1. Android tokens from staff collection
      const staffSnapshot = await admin.firestore()
        .collection("staff")
        .where("restaurantId", "==", restId)
        .get();

      staffSnapshot.forEach((doc) => {
        const staffData = doc.data();
        if (staffData.fcmToken && typeof staffData.fcmToken === "string") {
          tokens.push(staffData.fcmToken);
        }
        // Multiple tokens per staff
        if (Array.isArray(staffData.fcmTokens)) {
          staffData.fcmTokens.forEach(t => {
            if (t && typeof t === "string") tokens.push(t);
          });
        }
      });

      // 2. Web push tokens (iPhone PWA + desktop browsers)
      const webTokensSnapshot = await admin.firestore()
        .collection(`restaurants/${restId}/pushTokens`)
        .get();

      webTokensSnapshot.forEach((doc) => {
        const data = doc.data();
        if (data.token && typeof data.token === "string") {
          tokens.push(data.token);
        }
      });

      // Deduplicate
      const uniqueTokens = [...new Set(tokens)];

      if (uniqueTokens.length === 0) {
        console.log("No active FCM tokens found for restaurant:", restId);
        return null;
      }

      console.log(`Sending to ${uniqueTokens.length} devices`);

      const payload = {
        tokens: uniqueTokens,
        notification: {
          title: `🔔 Yeni Sipariş: ${tableLabel}`,
          body: customerName
            ? `${customerName} • ${itemCount} ürün • ₺${totalPrice.toFixed(2)}`
            : `${itemCount} ürün (${firstItems}) • ₺${totalPrice.toFixed(2)}`
        },
        data: {
          orderId: String(orderId),
          restaurantId: String(restId),
          tableLabel: String(tableLabel),
          type: "new_order",
          click_action: "FLUTTER_NOTIFICATION_CLICK"
        },
        android: {
          priority: "high",
          notification: {
            sound: "default",
            channelId: "orders_channel",
            priority: "max"
          }
        },
        webpush: {
          headers: {
            Urgency: "high"
          },
          notification: {
            icon: "/logo.png",
            badge: "/logo.png",
            vibrate: [200, 100, 200, 100, 200],
            requireInteraction: true,
            actions: [
              { action: "view", title: "Görüntüle" }
            ]
          },
          fcmOptions: {
            link: "/admin"
          }
        }
      };

      const response = await admin.messaging().sendEachForMulticast(payload);
      console.log(`FCM result: ${response.successCount} success, ${response.failureCount} failed`);

      // Clean up invalid tokens
      if (response.failureCount > 0) {
        const invalidTokens = [];
        response.responses.forEach((resp, idx) => {
          if (!resp.success && resp.error &&
            (resp.error.code === "messaging/invalid-registration-token" ||
             resp.error.code === "messaging/registration-token-not-registered")) {
            invalidTokens.push(uniqueTokens[idx]);
          }
        });

        // Remove invalid web tokens from Firestore
        if (invalidTokens.length > 0) {
          const batch = admin.firestore().batch();
          const webTokensDocs = await admin.firestore()
            .collection(`restaurants/${restId}/pushTokens`)
            .where("token", "in", invalidTokens.slice(0, 10))
            .get();
          webTokensDocs.forEach(doc => batch.delete(doc.ref));
          await batch.commit();
          console.log(`Cleaned ${webTokensDocs.size} invalid web tokens`);
        }
      }

      return response;
    } catch (error) {
      console.error("Error sending notification:", error);
      return null;
    }
  });
