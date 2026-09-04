const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();

/**
 * Yeni sipariş oluşturulduğunda TÜM kayıtlı cihazlara push bildirim gönder.
 * Region: europe-west1 (Firestore eur3 ile aynı bölge)
 */
exports.onNewOrderCreated = onDocumentCreated(
  {
    document: "restaurants/{restId}/orders/{orderId}",
    region: "europe-west1"
  },
  async (event) => {
    const snap = event.data;
    if (!snap) return null;

    const order = snap.data();
    const restId = event.params.restId;
    const orderId = event.params.orderId;

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
      const db = getFirestore();
      const tokens = [];

      // 1. Android tokens from staff collection
      const staffSnapshot = await db
        .collection("staff")
        .where("restaurantId", "==", restId)
        .get();

      staffSnapshot.forEach((doc) => {
        const staffData = doc.data();
        if (staffData.fcmToken && typeof staffData.fcmToken === "string") {
          tokens.push(staffData.fcmToken);
        }
        if (Array.isArray(staffData.fcmTokens)) {
          staffData.fcmTokens.forEach(t => {
            if (t && typeof t === "string") tokens.push(t);
          });
        }
      });

      // 2. Web push tokens (iPhone PWA + desktop browsers)
      const webTokensSnapshot = await db
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

      const messaging = getMessaging();
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
          type: "new_order"
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
            requireInteraction: true
          },
          fcmOptions: {
            link: "/admin"
          }
        }
      };

      const response = await messaging.sendEachForMulticast(payload);
      console.log(`FCM: ${response.successCount} success, ${response.failureCount} failed`);

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

        if (invalidTokens.length > 0) {
          const batch = db.batch();
          const webTokensDocs = await db
            .collection(`restaurants/${restId}/pushTokens`)
            .where("token", "in", invalidTokens.slice(0, 10))
            .get();
          webTokensDocs.forEach(doc => batch.delete(doc.ref));
          await batch.commit();
          console.log(`Cleaned ${webTokensDocs.size} invalid tokens`);
        }
      }

      return response;
    } catch (error) {
      console.error("Error sending notification:", error);
      return null;
    }
  }
);

/**
 * Android mobil uygulamasından Web'e test bildirimi göndermek için kullanılır.
 */
exports.onTestNotification = onDocumentCreated(
  {
    document: "restaurants/{restId}/testNotifications/{docId}",
    region: "europe-west1"
  },
  async (event) => {
    const snap = event.data;
    if (!snap) return null;
    const restId = event.params.restId;

    try {
      const db = getFirestore();
      const tokens = [];

      // Sadece Web / iPhone PWA tokenlarını al
      const webTokensSnapshot = await db
        .collection(`restaurants/${restId}/pushTokens`)
        .get();

      webTokensSnapshot.forEach((doc) => {
        const data = doc.data();
        if (data.token && typeof data.token === "string") {
          tokens.push(data.token);
        }
      });

      const uniqueTokens = [...new Set(tokens)];
      if (uniqueTokens.length === 0) {
        console.log("No web tokens found for test notification");
        return null;
      }

      const messaging = getMessaging();
      const payload = {
        tokens: uniqueTokens,
        notification: {
          title: "🔔 Test Bildirimi",
          body: "Mobil uygulamadan gönderilen test bildirimi başarıyla ulaştı!"
        },
        webpush: {
          headers: {
            Urgency: "high"
          },
          notification: {
            icon: "/logo.png",
            badge: "/logo.png",
            vibrate: [200, 100, 200, 100, 200],
            requireInteraction: true
          }
        }
      };

      const response = await messaging.sendEachForMulticast(payload);
      console.log(`Test FCM sent: ${response.successCount} success`);
      return response;
    } catch (err) {
      console.error("Test notification error:", err);
      return null;
    }
  }
);
