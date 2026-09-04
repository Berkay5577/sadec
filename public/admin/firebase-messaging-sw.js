// Sade.C — Firebase Cloud Messaging Service Worker
// iPhone PWA + Desktop push bildirim desteği

importScripts('https://www.gstatic.com/firebasejs/10.9.0/firebase-app-compat.js');
importScripts('https://www.gstatic.com/firebasejs/10.9.0/firebase-messaging-compat.js');

firebase.initializeApp({
  apiKey: "AIzaSyDOMtZ9I-f_tSAVnoutiMLScKsH1A7ALpE",
  authDomain: "sadec-9b458.firebaseapp.com",
  projectId: "sadec-9b458",
  storageBucket: "sadec-9b458.firebasestorage.app",
  messagingSenderId: "5087463503",
  appId: "1:5087463503:web:672df4999766da9855423a"
});

const messaging = firebase.messaging();

// Arka planda (telefon kilitli / uygulama kapalı) gelen bildirimler
messaging.onBackgroundMessage((payload) => {
  console.log('[SW] Background message received:', payload);

  const title = payload.notification?.title || '🔔 Yeni Sipariş';
  const options = {
    body: payload.notification?.body || 'Yeni bir sipariş geldi!',
    icon: '/logo.png',
    badge: '/logo.png',
    vibrate: [200, 100, 200, 100, 200],
    requireInteraction: true,
    data: payload.data || {},
    tag: 'order-' + (payload.data?.orderId || Date.now()),
    actions: [
      { action: 'view', title: 'Görüntüle' }
    ]
  };

  return self.registration.showNotification(title, options);
});

// Bildirime tıklandığında
self.addEventListener('notificationclick', (event) => {
  event.notification.close();

  // Admin panelini aç
  event.waitUntil(
    clients.matchAll({ type: 'window', includeUncontrolled: true }).then((windowClients) => {
      // Zaten açık bir pencere varsa onu öne getir
      for (const client of windowClients) {
        if (client.url.includes('/admin') && 'focus' in client) {
          return client.focus();
        }
      }
      // Yoksa yeni pencere aç
      return clients.openWindow('/admin');
    })
  );
});
