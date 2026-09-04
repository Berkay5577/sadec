(function() {
  // === Module 1: State ===
  const RESTAURANT_ID = 'sadec-gerze';
  const BASE_PATH = `restaurants/${RESTAURANT_ID}`;
  let currentUser = null;
  let currentPage = 'orders';
  let ordersFilter = 'active';
  let dashboardFilter = 'today';
  let allOrders = [];
  let allTables = [];
  let allCategories = [];
  let allMenuItems = [];
  let restaurant = null;
  let selectedTableItems = {};
  let dashboardUnlocked = false;
  let unsubscribeOrders = null;
  let unsubscribeRestaurant = null;
  let unsubscribeTables = null;
  let unsubscribeCategories = null;
  let unsubscribeMenuItems = null;
  let audioContext = null;
  let lastPendingCount = 0;

  // === Module 2: Utilities ===
  function formatPrice(num) {
    if (!num) return '0,00 ₺';
    return num.toLocaleString('tr-TR', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) + ' ₺';
  }

  function formatTime(timestamp) {
    if (!timestamp) return '';
    const date = timestamp.toDate ? timestamp.toDate() : new Date(timestamp);
    return date.toLocaleTimeString('tr-TR', { hour: '2-digit', minute: '2-digit' });
  }

  function formatDate(timestamp) {
    if (!timestamp) return '';
    const date = timestamp.toDate ? timestamp.toDate() : new Date(timestamp);
    return date.toLocaleDateString('tr-TR', { day: '2-digit', month: '2-digit', year: 'numeric' });
  }

  function statusText(status) {
    const map = {
      'pending': '⏳ Bekliyor',
      'preparing': '🍳 Hazırlanıyor',
      'ready': '🛎️ Hazır',
      'delivered': '✅ Teslim Edildi',
      'cancelled': '❌ İptal'
    };
    return map[status] || status;
  }

  function calcItemEffectivePrice(item) {
    if (item.isComplimentary) return 0;
    const base = (item.unitPrice || 0) * (item.quantity || 1);
    const discount = item.discountAmount || 0;
    return Math.max(0, base - discount);
  }

  function calcOrderTotal(order) {
    if (!order || !order.items) return 0;
    return order.items.reduce((sum, item) => sum + calcItemEffectivePrice(item), 0);
  }

  function calcOrderRemaining(order) {
    if (!order || !order.items) return 0;
    return order.items.reduce((sum, item) => {
      if (item.isPaid || item.isComplimentary) return sum;
      return sum + calcItemEffectivePrice(item);
    }, 0);
  }

  function showModal(title, bodyHtml, footerHtml) {
    document.getElementById('modalTitle').textContent = title;
    document.getElementById('modalBody').innerHTML = bodyHtml;
    document.getElementById('modalFooter').innerHTML = footerHtml || '';
    document.getElementById('modalOverlay').classList.remove('hidden');
    document.getElementById('modalOverlay').classList.add('active');
  }

  function closeModal() {
    document.getElementById('modalOverlay').classList.remove('active');
    setTimeout(() => {
      document.getElementById('modalOverlay').classList.add('hidden');
    }, 200); // Wait for transition
  }

  function showToast(msg) {
    const toast = document.getElementById('toast');
    toast.textContent = msg;
    toast.classList.remove('hidden');
    toast.style.opacity = '1';
    setTimeout(() => {
      toast.style.opacity = '0';
      setTimeout(() => toast.classList.add('hidden'), 300);
    }, 3000);
  }

  function playNotificationSound() {
    if (!audioContext) {
      // Auto-init if not yet initialized (iOS might need this)
      initAudio();
    }
    if (!audioContext) return;

    // iOS: resume suspended context
    if (audioContext.state === 'suspended') {
      audioContext.resume();
    }

    try {
      // 3x bip sesi — daha dikkat çekici (Android bildirimi gibi)
      const t = audioContext.currentTime;
      for (let i = 0; i < 3; i++) {
        const osc = audioContext.createOscillator();
        const gain = audioContext.createGain();
        osc.type = 'square';
        osc.frequency.setValueAtTime(880, t + i * 0.25);
        gain.gain.setValueAtTime(0, t + i * 0.25);
        gain.gain.linearRampToValueAtTime(0.6, t + i * 0.25 + 0.02);
        gain.gain.linearRampToValueAtTime(0, t + i * 0.25 + 0.15);
        osc.connect(gain);
        gain.connect(audioContext.destination);
        osc.start(t + i * 0.25);
        osc.stop(t + i * 0.25 + 0.15);
      }
      // Titreşim (destekleniyorsa)
      if (navigator.vibrate) {
        navigator.vibrate([200, 100, 200, 100, 200]);
      }
    } catch (e) {
      console.log('Audio notification failed:', e);
    }
  }

  function initAudio() {
    try {
      if (!audioContext) {
        audioContext = new (window.AudioContext || window.webkitAudioContext)();
      }
      if (audioContext.state === 'suspended') {
        audioContext.resume();
      }
      // iOS unlock: silent buffer play
      const silentBuffer = audioContext.createBuffer(1, 1, 22050);
      const source = audioContext.createBufferSource();
      source.buffer = silentBuffer;
      source.connect(audioContext.destination);
      source.start(0);
    } catch (e) {
      console.log('Audio init failed:', e);
    }
  }

  // === Module 3: Init & Event Listeners ===
  document.addEventListener('DOMContentLoaded', () => {
    // Auth Listener
    auth.onAuthStateChanged(onAuthStateChanged);

    // Login
    document.getElementById('loginBtn').addEventListener('click', handleLogin);
    
    // Passwords enter key
    document.getElementById('loginPassword').addEventListener('keypress', (e) => {
      if (e.key === 'Enter') handleLogin();
    });

    // Logout
    document.getElementById('logoutBtn').addEventListener('click', handleLogout);
    document.getElementById('settingsLogoutBtn').addEventListener('click', handleLogout);

    // Modal
    document.getElementById('modalClose').addEventListener('click', closeModal);
    document.getElementById('modalOverlay').addEventListener('click', (e) => {
      if (e.target.id === 'modalOverlay') closeModal();
    });

    // Dashboard PIN
    document.getElementById('dashboardPinSubmit').addEventListener('click', checkDashboardPin);
    document.getElementById('dashboardPinInput').addEventListener('keypress', (e) => {
      if (e.key === 'Enter') checkDashboardPin();
    });

    // Manual Sale FAB
    const fab = document.getElementById('manualSaleFab');
    if (fab) fab.addEventListener('click', showManualSaleDialog);

    // Navigation
    document.querySelectorAll('.bottom-nav .nav-item').forEach(item => {
      item.addEventListener('click', (e) => {
        const page = e.currentTarget.getAttribute('data-page');
        navigateTo(page);
      });
    });

    // Order Filters
    document.querySelectorAll('#page-orders .filter-chips .chip').forEach(chip => {
      chip.addEventListener('click', (e) => {
        document.querySelectorAll('#page-orders .chip').forEach(c => c.classList.remove('active'));
        e.currentTarget.classList.add('active');
        ordersFilter = e.currentTarget.getAttribute('data-filter');
        renderOrders();
      });
    });

    // Dashboard Filters
    document.querySelectorAll('#page-dashboard .time-filter-chips .chip').forEach(chip => {
      chip.addEventListener('click', (e) => {
        document.querySelectorAll('#page-dashboard .time-filter-chips .chip').forEach(c => c.classList.remove('active'));
        e.currentTarget.classList.add('active');
        dashboardFilter = e.currentTarget.getAttribute('data-filter');
        renderDashboardCards(allOrders); // re-render with new filter
      });
    });

    // Audio init on first interaction
    document.body.addEventListener('click', initAudio, { once: true });
    document.body.addEventListener('touchstart', initAudio, { once: true });
    
    // Add Menu Buttons
    const addCategoryBtn = document.getElementById('addCategoryBtn');
    if (addCategoryBtn) addCategoryBtn.addEventListener('click', showAddCategoryDialog);
    
    const backToCategoriesBtn = document.getElementById('backToCategoriesBtn');
    if (backToCategoriesBtn) backToCategoriesBtn.addEventListener('click', backToCategories);
    
    const addProductBtn = document.getElementById('addProductBtn');
    if (addProductBtn) addProductBtn.addEventListener('click', () => {
        const currentCatId = document.getElementById('categoryDetail').getAttribute('data-current-cat');
        if (currentCatId) showAddProductDialog(currentCatId);
    });
  });

  // === Module 4: Auth ===
  async function handleLogin() {
    const email = document.getElementById('loginEmail').value.trim();
    const pass = document.getElementById('loginPassword').value.trim();
    const errorEl = document.getElementById('loginError');
    const spinner = document.getElementById('loginSpinner');
    const btn = document.getElementById('loginBtn');

    if (!email || !pass) {
      errorEl.textContent = 'Lütfen e-posta ve şifrenizi girin.';
      return;
    }

    errorEl.textContent = '';
    btn.disabled = true;
    spinner.style.display = 'block';

    try {
      await auth.signInWithEmailAndPassword(email, pass);
    } catch (err) {
      if (err.code === 'auth/user-not-found' || err.code === 'auth/invalid-credential') {
        try {
          await auth.createUserWithEmailAndPassword(email, pass);
        } catch (createErr) {
          errorEl.textContent = 'Giriş yapılamadı: ' + createErr.message;
        }
      } else {
        errorEl.textContent = 'Hata: ' + err.message;
      }
    } finally {
      btn.disabled = false;
      spinner.style.display = 'none';
    }
  }

  async function handleLogout() {
    await auth.signOut();
  }

  function onAuthStateChanged(user) {
    currentUser = user;
    const loginScreen = document.getElementById('loginScreen');
    const appContainer = document.getElementById('appContainer');

    if (user) {
      loginScreen.classList.add('hidden');
      appContainer.classList.remove('hidden');
      startListeners();
      fixCorruptedPaidAt(); // Bozuk Timestamp → Long düzeltme (Android crash fix)
      setupPushNotifications(); // iPhone + Desktop push bildirim kayıt
      navigateTo('orders');
    } else {
      loginScreen.classList.remove('hidden');
      appContainer.classList.add('hidden');
      stopListeners();
      dashboardUnlocked = false;
    }
  }

  // === Module 5: Router ===
  function navigateTo(page) {
    currentPage = page;
    
    // Update Nav UI
    document.querySelectorAll('.bottom-nav .nav-item').forEach(nav => {
      if (nav.getAttribute('data-page') === page) {
        nav.classList.add('active');
      } else {
        nav.classList.remove('active');
      }
    });

    // Update Pages - only use 'active' class (CSS: .page-content { display: none } .page-content.active { display: block })
    document.querySelectorAll('.page-content').forEach(p => {
      p.classList.remove('active');
    });
    
    const target = document.getElementById(`page-${page}`);
    if (target) {
        target.classList.add('active');
    }

    // Page Specific logic
    if (page === 'orders') renderOrders();
    if (page === 'tables') renderTables();
    if (page === 'menu') {
        document.getElementById('categoryDetail').classList.add('hidden');
        document.getElementById('categoriesList').classList.remove('hidden');
        renderCategories();
    }
    if (page === 'dashboard') {
      if (!dashboardUnlocked) {
        document.getElementById('dashboardPinGate').classList.remove('hidden');
        document.getElementById('dashboardContent').classList.add('hidden');
      } else {
        document.getElementById('dashboardPinGate').classList.add('hidden');
        document.getElementById('dashboardContent').classList.remove('hidden');
        renderDashboardCards(allOrders);
      }
    }
    if (page === 'settings') renderSettings();
  }

  // === Module 6: Data Listeners ===
  function startListeners() {
    // Restaurant profile
    unsubscribeRestaurant = db.doc(BASE_PATH).onSnapshot(doc => {
      if (doc.exists) restaurant = doc.data();
    });

    // Orders
    unsubscribeOrders = db.collection(`${BASE_PATH}/orders`).onSnapshot(snap => {
      const newOrders = [];
      snap.forEach(doc => {
        newOrders.push({ id: doc.id, ...doc.data() });
      });
      
      // Client-side sort by createdAt descending
      newOrders.sort((a, b) => {
        const tA = a.createdAt ? (a.createdAt.toDate ? a.createdAt.toDate().getTime() : new Date(a.createdAt).getTime()) : 0;
        const tB = b.createdAt ? (b.createdAt.toDate ? b.createdAt.toDate().getTime() : new Date(b.createdAt).getTime()) : 0;
        return tB - tA;
      });
      
      allOrders = newOrders;
      
      // Check for new pending orders
      const currentPending = allOrders.filter(o => o.status === 'pending' && !o.isArchived).length;
      if (currentPending > lastPendingCount) {
        playNotificationSound();
      }
      lastPendingCount = currentPending;

      updateOrderBadges();
      if (currentPage === 'orders') renderOrders();
      if (currentPage === 'tables') renderTables();
      if (currentPage === 'dashboard' && dashboardUnlocked) renderDashboardCards(allOrders);
    });

    // Tables
    unsubscribeTables = db.collection(`${BASE_PATH}/tables`).onSnapshot(snap => {
      allTables = [];
      snap.forEach(doc => allTables.push({ id: doc.id, ...doc.data() }));
      allTables.sort((a, b) => (a.label || '').localeCompare(b.label || ''));
      if (currentPage === 'tables') renderTables();
    });

    // Categories
    unsubscribeCategories = db.collection(`${BASE_PATH}/categories`).onSnapshot(snap => {
      allCategories = [];
      snap.forEach(doc => allCategories.push({ id: doc.id, ...doc.data() }));
      allCategories.sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0));
      if (currentPage === 'menu') renderCategories();
    });

    // Menu Items
    unsubscribeMenuItems = db.collection(`${BASE_PATH}/menuItems`).onSnapshot(snap => {
      allMenuItems = [];
      snap.forEach(doc => allMenuItems.push({ id: doc.id, ...doc.data() }));
      allMenuItems.sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0));
      if (currentPage === 'menu' && !document.getElementById('categoryDetail').classList.contains('hidden')) {
        const catId = document.getElementById('categoryDetail').getAttribute('data-current-cat');
        if (catId) renderProducts(catId);
      }
    });
  }

  function stopListeners() {
    if (unsubscribeRestaurant) unsubscribeRestaurant();
    if (unsubscribeOrders) unsubscribeOrders();
    if (unsubscribeTables) unsubscribeTables();
    if (unsubscribeCategories) unsubscribeCategories();
    if (unsubscribeMenuItems) unsubscribeMenuItems();
  }

  // Bozuk paidAt (Firestore Timestamp) → Long (milisaniye) düzeltme
  // Web panelden ödeme alındığında Timestamp yazılmıştı, Android Long bekliyor → crash
  async function fixCorruptedPaidAt() {
    try {
      const snapshot = await db.collection(`${BASE_PATH}/orders`).get();
      const batch = db.batch();
      let fixCount = 0;

      snapshot.forEach(doc => {
        const data = doc.data();
        if (!data.items || !Array.isArray(data.items)) return;

        let needsFix = false;
        const fixedItems = data.items.map(item => {
          // paidAt bir Timestamp nesnesi ise (toDate metodu varsa) → Long'a çevir
          if (item.paidAt && typeof item.paidAt === 'object' && item.paidAt.toDate) {
            needsFix = true;
            return { ...item, paidAt: item.paidAt.toDate().getTime() };
          }
          return item;
        });

        if (needsFix) {
          batch.update(doc.ref, { items: fixedItems });
          fixCount++;
        }
      });

      if (fixCount > 0) {
        await batch.commit();
        console.log(`✅ ${fixCount} siparişteki bozuk paidAt düzeltildi`);
      }
    } catch (e) {
      console.error('paidAt fix failed:', e);
    }
  }

  // === Module 6c: Push Notifications (iPhone PWA + Desktop) ===
  async function setupPushNotifications() {
    try {
      // Service Worker kaydet
      if (!('serviceWorker' in navigator)) {
        console.log('Service Worker desteklenmiyor');
        return;
      }

      const registration = await navigator.serviceWorker.register('/admin/firebase-messaging-sw.js');
      console.log('Service Worker kaydedildi:', registration.scope);

      // Bildirim izni iste
      if (!('Notification' in window)) {
        console.log('Bildirim API desteklenmiyor');
        return;
      }

      let permission = Notification.permission;
      if (permission === 'default') {
        // İlk kez — kullanıcıya popup çıkar
        permission = await Notification.requestPermission();
      }

      if (permission !== 'granted') {
        console.log('Bildirim izni verilmedi:', permission);
        return;
      }

      // FCM Messaging token al
      const messaging = firebase.messaging();
      messaging.useServiceWorker(registration);

      // VAPID key ile token al
      const vapidKey = 'BGxb-0sivHVZvdgnK7w2Wi_4GGKX4o3klBr2ZfpirWyHy5yy4XoBA5SP3imvgCQwPey3sW7OGB40iCV6bNYEQ6s';
      let token;
      try {
        token = await messaging.getToken({ vapidKey: vapidKey, serviceWorkerRegistration: registration });
      } catch (e) {
        console.error('FCM token alma hatası:', e);
        return;
      }

      if (!token) {
        console.log('FCM token alınamadı');
        return;
      }

      console.log('FCM Token:', token);

      // Token'ı Firestore'a kaydet (pushTokens koleksiyonu)
      await db.collection(`${BASE_PATH}/pushTokens`).doc(token.substring(0, 20)).set({
        token: token,
        createdAt: Date.now(),
        platform: /iPhone|iPad|iPod/.test(navigator.userAgent) ? 'ios-pwa' : 'web',
        userAgent: navigator.userAgent.substring(0, 100)
      }, { merge: true });

      console.log('✅ Push bildirim token kaydedildi');
      showToast('🔔 Bildirimler aktif!');

      // Ön planda gelen bildirimler
      messaging.onMessage((payload) => {
        console.log('Foreground message:', payload);
        const title = payload.notification?.title || '🔔 Yeni Sipariş';
        const body = payload.notification?.body || '';
        
        // Ses çal
        playNotificationSound();
        
        // Toast göster
        showToast(`${title} — ${body}`);
        
        // Ön planda da bildirim göster (notification API)
        if (Notification.permission === 'granted') {
          new Notification(title, {
            body: body,
            icon: '/logo.png',
            badge: '/logo.png',
            vibrate: [200, 100, 200],
            tag: 'order-' + Date.now()
          });
        }
      });

    } catch (error) {
      console.error('Push notification setup failed:', error);
    }
  }

  // === Module 6b: Orders ===
  function updateOrderBadges() {
    const active = allOrders.filter(o => !o.isArchived && (o.status === 'pending' || o.status === 'preparing' || o.status === 'ready'));
    const pendingCount = active.filter(o => o.status === 'pending').length;
    const totalActive = active.length;

    const navBadge = document.getElementById('navOrdersBadge');
    const headerBadge = document.getElementById('ordersActiveBadge');

    if (pendingCount > 0) {
      navBadge.textContent = pendingCount;
      navBadge.classList.remove('hidden');
    } else {
      navBadge.classList.add('hidden');
    }

    if (totalActive > 0) {
      headerBadge.textContent = `${totalActive} Aktif`;
      headerBadge.classList.remove('hidden');
    } else {
      headerBadge.classList.add('hidden');
    }
  }

  function renderOrders() {
    const list = document.getElementById('ordersList');
    const empty = document.getElementById('ordersEmpty');
    list.innerHTML = '';

    let filtered = allOrders.filter(o => !o.isArchived); // Filter out archived normally

    if (ordersFilter === 'active') {
      filtered = filtered.filter(o => o.status === 'pending' || o.status === 'preparing' || o.status === 'ready');
    } else if (ordersFilter === 'delivered') {
      filtered = allOrders.filter(o => o.status === 'delivered'); // allow viewing recently delivered even if archived? No, filter from all.
      // Usually delivered might be archived at end of day, but we'll just filter by status.
    } else if (ordersFilter === 'cancelled') {
      filtered = allOrders.filter(o => o.status === 'cancelled');
    }

    if (filtered.length === 0) {
      list.classList.add('hidden');
      empty.classList.remove('hidden');
      return;
    }

    list.classList.remove('hidden');
    empty.classList.add('hidden');

    filtered.forEach(order => {
      const card = document.createElement('div');
      card.className = 'card order-card';
      
      const itemsHtml = (order.items || []).map(item => `
        <div class="order-item-row">
          <span class="order-item-name">${item.quantity}x ${item.name}</span>
          <span class="order-item-price">${formatPrice(calcItemEffectivePrice(item))}</span>
        </div>
        ${item.note ? `<div class="order-item-note">Not: ${item.note}</div>` : ''}
      `).join('');

      let actionsHtml = '';
      if (ordersFilter === 'active') {
        actionsHtml = `
          <div class="order-actions">
            <button class="btn-cancel-order" onclick="window.showCancelDialog('${order.id}')">İptal</button>
            <button class="btn-deliver" onclick="window.deliverOrder('${order.id}')">
              Teslim Edildi İşaretle
            </button>
          </div>
        `;
      }
      
      let cancelNoteHtml = '';
      if (order.status === 'cancelled' && order.cancelReason) {
          cancelNoteHtml = `<div class="order-cancel-reason">İptal Nedeni: ${order.cancelReason}</div>`;
      }

      card.innerHTML = `
        <div class="order-header">
          <div class="order-header-left">
            <div class="order-table-badge">📍 ${order.tableLabel || 'Bilinmiyor'}</div>
            <div class="order-customer">${order.customerName || 'Misafir'}</div>
          </div>
          <div class="order-header-right">
            <div class="order-status status-${order.status}">${statusText(order.status)}</div>
            <div class="order-time">${formatTime(order.createdAt)}</div>
          </div>
        </div>
        <div class="order-items-list">
          ${itemsHtml}
        </div>
        ${order.note ? `<div class="order-note">Sipariş Notu: ${order.note}</div>` : ''}
        ${cancelNoteHtml}
        <div class="order-total-row">
          <span class="order-total">${formatPrice(calcOrderTotal(order))}</span>
        </div>
        ${actionsHtml}
      `;
      list.appendChild(card);
    });
  }

  async function deliverOrder(id) {
    try {
      await db.doc(`${BASE_PATH}/orders/${id}`).update({
        status: 'delivered',
        updatedAt: firebase.firestore.FieldValue.serverTimestamp()
      });
      showToast('Sipariş teslim edildi');
    } catch (e) {
      showToast('Hata: ' + e.message);
    }
  }

  function showCancelDialog(id) {
    const body = `
      <div style="display: flex; flex-direction: column; gap: 10px;">
        <label><input type="radio" name="cancelReason" value="Ürün kalmadı" checked> Ürün kalmadı</label>
        <label><input type="radio" name="cancelReason" value="Müşteri vazgeçti"> Müşteri vazgeçti</label>
        <label><input type="radio" name="cancelReason" value="Yanlış sipariş"> Yanlış sipariş</label>
        <label><input type="radio" name="cancelReason" value="Diğer"> Diğer</label>
      </div>
    `;
    const footer = `
      <button class="btn btn-ghost" onclick="window.closeModal()">Vazgeç</button>
      <button class="btn btn-danger" onclick="window.cancelOrder('${id}')">İptal Et</button>
    `;
    showModal('Siparişi İptal Et', body, footer);
  }

  async function cancelOrder(id) {
    const reason = document.querySelector('input[name="cancelReason"]:checked').value;
    try {
      await db.doc(`${BASE_PATH}/orders/${id}`).update({
        status: 'cancelled',
        cancelReason: reason,
        updatedAt: firebase.firestore.FieldValue.serverTimestamp()
      });
      closeModal();
      showToast('Sipariş iptal edildi');
    } catch (e) {
      showToast('Hata: ' + e.message);
    }
  }

  // === Module 7: Tables & POS ===
  function computeActiveTables() {
    // Group unarchived orders by tableId
    const tableGroups = {};
    // Android mantığıyla birebir: sadece ödenmemiş siparişi olan masaları göster
    // activeOrders = orders.filter { !isArchived && status != "cancelled" && remainingAmount() > 0.001 }
    const activeOrders = allOrders.filter(o => 
      !o.isArchived && o.status !== 'cancelled' && calcOrderRemaining(o) > 0.001
    );

    activeOrders.forEach(order => {
      const tid = order.tableId;
      if (!tid) return;
      if (!tableGroups[tid]) {
        tableGroups[tid] = {
          orders: [],
          totalRemaining: 0,
          customers: {}
        };
      }
      tableGroups[tid].orders.push(order);
      tableGroups[tid].totalRemaining += calcOrderRemaining(order);
      
      const cName = order.customerName || 'Misafir';
      if (!tableGroups[tid].customers[cName]) tableGroups[tid].customers[cName] = [];
      
      // Sadece ödenmemiş ürünleri göster
      (order.items || []).forEach((item, index) => {
        if (!item.isPaid && !item.isComplimentary) {
          tableGroups[tid].customers[cName].push({
            orderId: order.id,
            itemIndex: index,
            ...item
          });
        }
      });
    });
    return tableGroups;
  }

  function renderTables() {
    const grid = document.getElementById('tablesGrid');
    const empty = document.getElementById('tablesEmpty');
    const summary = document.getElementById('tablesSummary');
    grid.innerHTML = '';
    
    const activeTables = computeActiveTables();
    let occupiedCount = 0;

    allTables.forEach(table => {
      const data = activeTables[table.id];
      if (!data || data.orders.length === 0 || data.totalRemaining <= 0.001) return; // Android: sadece ödenmemiş masaları göster
      
      occupiedCount++;
      const card = document.createElement('div');
      card.className = 'table-card';
      
      let bodyHtml = '';
      
      for (const [customerName, items] of Object.entries(data.customers)) {
        let itemsHtml = '';
        items.forEach(item => {
          const itemKey = `${item.orderId}_${item.itemIndex}`;
          const isSelected = selectedTableItems[itemKey] || false;
          const isPaid = item.isPaid || item.isComplimentary;
          
          let badgeHtml = '';
          if (item.isPaid) badgeHtml = `<span class="badge-paid">Ödendi</span>`;
          else if (item.isComplimentary) badgeHtml = `<span class="badge-complimentary">İkram</span>`;

          itemsHtml += `
            <div class="table-item-row ${isPaid ? 'paid' : ''}">
              ${!isPaid ? `<input type="checkbox" class="table-item-checkbox" ${isSelected ? 'checked' : ''} onchange="window.toggleItemSelection('${itemKey}', '${table.id}')">` : '<div style="width:20px;height:20px;flex-shrink:0;"></div>'}
              <div class="table-item-info">
                <div class="table-item-name">${item.quantity}x ${item.name}</div>
                <div class="table-item-badges">${badgeHtml}</div>
                <div class="table-item-price">${formatPrice(calcItemEffectivePrice(item))}</div>
              </div>
            </div>
          `;
        });
        
        bodyHtml += `
          <div class="table-customer-group">
            <div class="table-customer-header">👤 ${customerName}</div>
            ${itemsHtml}
          </div>
        `;
      }
      
      // Compute selected amount
      let selectedAmount = 0;
      let selectedKeys = [];
      for (const [customerName, items] of Object.entries(data.customers)) {
          items.forEach(item => {
              const itemKey = `${item.orderId}_${item.itemIndex}`;
              if (selectedTableItems[itemKey] && !item.isPaid && !item.isComplimentary) {
                  selectedAmount += calcItemEffectivePrice(item);
                  selectedKeys.push(itemKey);
              }
          });
      }

      card.innerHTML = `
        <div class="table-card-header">
          <div class="table-name">📍 ${table.label}</div>
          <div class="table-remaining">${formatPrice(data.totalRemaining)}</div>
        </div>
        <div class="table-body">
          ${bodyHtml}
        </div>
        <div class="table-actions">
          ${selectedAmount > 0 ? `
            <button class="btn-pay-selected" onclick="window.showPaymentDialog('selected', '${table.id}', ${selectedAmount}, '${selectedKeys.join(',')}')">
              Seçili Öde (${formatPrice(selectedAmount)})
            </button>
          ` : `
            <button class="btn-pay-table" onclick="window.showPaymentDialog('all', '${table.id}', ${data.totalRemaining}, '')" style="width: 100%; height: 44px; background: var(--sage-green); color: white; border: none; border-radius: var(--radius-md); font-weight: 700;">
              Tümünü Öde (${formatPrice(data.totalRemaining)})
            </button>
          `}
          <button class="table-transfer-btn" onclick="window.showTransferDialog('${table.id}')" style="width: 100%; height: 36px; background: transparent; color: var(--forest-green); border: 1px solid var(--forest-green); border-radius: var(--radius-md); font-weight: 600; margin-top: 8px;">
            Masa Taşı
          </button>
        </div>
      `;
      grid.appendChild(card);
    });

    if (occupiedCount === 0) {
      grid.classList.add('hidden');
      empty.classList.remove('hidden');
      summary.textContent = '0 Aktif';
    } else {
      grid.classList.remove('hidden');
      empty.classList.add('hidden');
      summary.textContent = `${occupiedCount} Aktif`;
    }
  }

  function toggleItemSelection(key, tableId) {
    selectedTableItems[key] = !selectedTableItems[key];
    renderTables();
  }

  function showPaymentDialog(target, tableId, amount, keysStr) {
    const itemKeys = keysStr ? keysStr.split(',') : [];
    
    // Build HTML based on admin.css expected classes for payments if any, or inline
    const body = `
      <div style="text-align: center; margin-bottom: 20px;">
        <div style="font-size: 0.9rem; color: var(--text-secondary);">Ödenecek Tutar</div>
        <div style="font-size: 2rem; font-weight: 700; color: var(--forest-green);">${formatPrice(amount)}</div>
      </div>
      <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-bottom: 16px;">
        <button class="btn btn-outline" style="height: 60px; font-weight: 600;" onclick="window.processPayment('${target}', '${tableId}', '${keysStr}', 'cash')">💵 Nakit</button>
        <button class="btn btn-outline" style="height: 60px; font-weight: 600;" onclick="window.processPayment('${target}', '${tableId}', '${keysStr}', 'card')">💳 Kredi Kartı</button>
        <button class="btn btn-outline" style="height: 60px; font-weight: 600;" onclick="window.processPayment('${target}', '${tableId}', '${keysStr}', 'transfer')">🏦 Havale/EFT</button>
        <button class="btn btn-outline" style="height: 60px; font-weight: 600;" onclick="window.processPayment('${target}', '${tableId}', '${keysStr}', 'complimentary')">🎁 İkram</button>
      </div>
      <div style="border-top: 1px solid #eee; padding-top: 16px;">
        <button class="btn btn-ghost btn-block" onclick="window.alert('Parçalı ödeme henüz aktif değil')">✂️ Parçalı Ödeme</button>
      </div>
    `;
    
    showModal('Ödeme Al', body, '<button class="btn btn-ghost" onclick="window.closeModal()">İptal</button>');
  }

  async function processPayment(target, tableId, keysStr, method) {
    const activeTables = computeActiveTables();
    const data = activeTables[tableId];
    if (!data) return;

    const batch = db.batch();
    
    // Gather which items to mark paid
    const updates = {}; // orderId -> { items: [...] }
    
    data.orders.forEach(order => {
        let itemsChanged = false;
        const newItems = [...order.items];
        
        newItems.forEach((item, index) => {
            const itemKey = `${order.id}_${index}`;
            let shouldPay = false;
            
            if (target === 'all' && !item.isPaid && !item.isComplimentary) {
                shouldPay = true;
            } else if (target === 'selected' && keysStr.includes(itemKey)) {
                shouldPay = true;
            }
            
            if (shouldPay) {
                const effPrice = calcItemEffectivePrice(item);
                if (method === 'complimentary') {
                    item.isComplimentary = true;
                } else {
                    item.isPaid = true;
                    item.paidAt = Date.now(); // Android expects Long (milisaniye), Timestamp değil!
                    item.paymentMethod = method;
                    if (method === 'cash') item.cashPaid = effPrice;
                    if (method === 'card') item.cardPaid = effPrice;
                    if (method === 'transfer') item.transferPaid = effPrice;
                }
                itemsChanged = true;
                
                // Remove from selection
                delete selectedTableItems[itemKey];
            }
        });
        
        if (itemsChanged) {
            batch.update(db.doc(`${BASE_PATH}/orders/${order.id}`), {
                items: newItems,
                updatedAt: firebase.firestore.FieldValue.serverTimestamp()
            });
        }
    });

    try {
        await batch.commit();
        closeModal();
        showToast('Ödeme başarıyla alındı');
        // Clear all selections for this table just in case
        renderTables();
    } catch (e) {
        showToast('Hata: ' + e.message);
    }
  }

  function showTransferDialog(oldTableId) {
    const oldTable = allTables.find(t => t.id === oldTableId);
    let options = allTables.filter(t => t.id !== oldTableId).map(t => `<option value="${t.id}">${t.label}</option>`).join('');
    
    const body = `
      <p style="margin-bottom: 12px;"><strong>${oldTable.label}</strong> masasındaki tüm siparişleri başka bir masaya taşıyın:</p>
      <select id="transferTargetId" style="width: 100%; padding: 12px; border-radius: var(--radius-md); border: 1px solid #ccc; margin-bottom: 16px;">
        ${options}
      </select>
    `;
    const footer = `
      <button class="btn btn-ghost" onclick="window.closeModal()">İptal</button>
      <button class="btn btn-primary" onclick="window.processTransfer('${oldTableId}')">Taşı</button>
    `;
    showModal('Masa Taşı', body, footer);
  }

  window.processTransfer = async function(oldTableId) {
    const newTableId = document.getElementById('transferTargetId').value;
    const newTable = allTables.find(t => t.id === newTableId);
    if (!newTableId || !newTable) return;
    
    const activeTables = computeActiveTables();
    const data = activeTables[oldTableId];
    if (!data) return;

    const batch = db.batch();
    data.orders.forEach(order => {
        batch.update(db.doc(`${BASE_PATH}/orders/${order.id}`), {
            tableId: newTableId,
            tableLabel: newTable.label,
            updatedAt: firebase.firestore.FieldValue.serverTimestamp()
        });
    });

    try {
        await batch.commit();
        closeModal();
        showToast('Masa taşındı');
    } catch (e) {
        showToast('Hata: ' + e.message);
    }
  }

  // === Module 8: Menu Management ===
  function getCategoryEmoji(name) {
      name = name.toLowerCase();
      if (name.includes('kahve')) return '☕';
      if (name.includes('soğuk')) return '🧊';
      if (name.includes('tatlı')) return '🍰';
      if (name.includes('çay')) return '🍵';
      if (name.includes('yemek')) return '🍔';
      return '🍽️';
  }

  function renderCategories() {
      const list = document.getElementById('categoriesList');
      list.innerHTML = '';
      
      allCategories.forEach(cat => {
          const itemsCount = allMenuItems.filter(m => m.categoryId === cat.id).length;
          const card = document.createElement('div');
          card.className = 'card category-card';
          card.style.display = 'flex';
          card.style.alignItems = 'center';
          card.style.justifyContent = 'space-between';
          card.style.cursor = 'pointer';
          card.onclick = () => showCategoryDetail(cat.id);
          
          card.innerHTML = `
            <div style="display: flex; align-items: center; gap: 12px;">
                <div style="font-size: 2rem;">${getCategoryEmoji(cat.name)}</div>
                <div>
                    <div style="font-weight: 700; color: var(--forest-green);">${cat.name}</div>
                    <div style="font-size: 0.8rem; color: var(--text-muted);">${itemsCount} Ürün</div>
                </div>
            </div>
            <div style="color: var(--text-muted);">➔</div>
          `;
          list.appendChild(card);
      });
  }

  function showCategoryDetail(catId) {
      document.getElementById('categoriesList').classList.add('hidden');
      const detail = document.getElementById('categoryDetail');
      detail.classList.remove('hidden');
      detail.setAttribute('data-current-cat', catId);
      
      const cat = allCategories.find(c => c.id === catId);
      document.getElementById('categoryDetailName').textContent = cat ? cat.name : '';
      
      renderProducts(catId);
  }

  function backToCategories() {
      document.getElementById('categoryDetail').classList.add('hidden');
      document.getElementById('categoriesList').classList.remove('hidden');
  }

  function renderProducts(catId) {
      const list = document.getElementById('productsList');
      list.innerHTML = '';
      
      const products = allMenuItems.filter(m => m.categoryId === catId);
      products.forEach(p => {
          const card = document.createElement('div');
          card.className = 'card product-card';
          card.style.display = 'flex';
          card.style.flexDirection = 'column';
          card.style.gap = '8px';
          
          card.innerHTML = `
            <div style="display: flex; justify-content: space-between; align-items: flex-start;">
                <div>
                    <div style="font-weight: 700; color: var(--text-primary);">${p.name}</div>
                    <div style="font-size: 0.9rem; font-weight: 600; color: var(--forest-green);">${formatPrice(p.price)}</div>
                </div>
                <label style="display: flex; align-items: center; cursor: pointer;">
                    <input type="checkbox" ${p.isAvailable ? 'checked' : ''} onchange="window.toggleProductStock('${p.id}', this.checked)" style="width: 18px; height: 18px; accent-color: var(--forest-green);">
                    <span style="margin-left: 6px; font-size: 0.8rem; font-weight: 600; color: ${p.isAvailable ? 'var(--success)' : 'var(--danger)'};">${p.isAvailable ? 'Stokta' : 'Tükendi'}</span>
                </label>
            </div>
            ${p.description ? `<div style="font-size: 0.8rem; color: var(--text-muted);">${p.description}</div>` : ''}
            <div style="display: flex; justify-content: flex-end; gap: 8px; margin-top: 8px; border-top: 1px solid #eee; padding-top: 8px;">
                <button class="btn btn-sm btn-ghost" onclick='window.showEditProductDialog(${JSON.stringify(p).replace(/'/g, "&#39;")})'>Düzenle</button>
                <button class="btn btn-sm btn-danger" onclick="window.deleteProduct('${p.id}')">Sil</button>
            </div>
          `;
          list.appendChild(card);
      });
  }

  async function toggleProductStock(id, isAvailable) {
      try {
          await db.doc(`${BASE_PATH}/menuItems/${id}`).update({ isAvailable });
      } catch (e) {
          showToast('Hata: ' + e.message);
      }
  }

  function showAddCategoryDialog() {
      const body = `
          <input type="text" id="newCatName" placeholder="Kategori Adı" class="login-input" style="margin-bottom: 12px; width: 100%; box-sizing: border-box;">
          <input type="number" id="newCatSort" placeholder="Sıralama (Örn: 10)" class="login-input" style="width: 100%; box-sizing: border-box;">
      `;
      const footer = `
          <button class="btn btn-ghost" onclick="window.closeModal()">İptal</button>
          <button class="btn btn-primary" onclick="window.saveCategory()">Kaydet</button>
      `;
      showModal('Yeni Kategori', body, footer);
  }

  window.saveCategory = async function() {
      const name = document.getElementById('newCatName').value.trim();
      const sortStr = document.getElementById('newCatSort').value;
      if (!name) return;
      
      try {
          await db.collection(`${BASE_PATH}/categories`).add({
              name,
              sortOrder: sortStr ? parseInt(sortStr) : 99,
              imageUrl: ''
          });
          closeModal();
          showToast('Kategori eklendi');
      } catch (e) {
          showToast('Hata: ' + e.message);
      }
  };

  function showAddProductDialog(catId) {
      const body = `
          <input type="text" id="newProdName" placeholder="Ürün Adı" class="login-input" style="margin-bottom: 12px; width: 100%; box-sizing: border-box;">
          <input type="number" id="newProdPrice" placeholder="Fiyat" class="login-input" style="margin-bottom: 12px; width: 100%; box-sizing: border-box;">
          <textarea id="newProdDesc" placeholder="Açıklama (İsteğe bağlı)" class="login-input" style="margin-bottom: 12px; width: 100%; box-sizing: border-box; height: 80px; padding-top:12px;"></textarea>
      `;
      const footer = `
          <button class="btn btn-ghost" onclick="window.closeModal()">İptal</button>
          <button class="btn btn-primary" onclick="window.saveProduct('${catId}')">Kaydet</button>
      `;
      showModal('Yeni Ürün', body, footer);
  }

  window.saveProduct = async function(catId) {
      const name = document.getElementById('newProdName').value.trim();
      const priceStr = document.getElementById('newProdPrice').value;
      const desc = document.getElementById('newProdDesc').value.trim();
      if (!name || !priceStr) return;
      
      try {
          await db.collection(`${BASE_PATH}/menuItems`).add({
              categoryId: catId,
              name,
              price: parseFloat(priceStr),
              description: desc,
              isAvailable: true,
              sortOrder: 99,
              allergens: []
          });
          closeModal();
          showToast('Ürün eklendi');
      } catch (e) {
          showToast('Hata: ' + e.message);
      }
  };

  function showEditProductDialog(item) {
      const body = `
          <input type="text" id="editProdName" value="${item.name}" class="login-input" style="margin-bottom: 12px; width: 100%; box-sizing: border-box;">
          <input type="number" id="editProdPrice" value="${item.price}" class="login-input" style="margin-bottom: 12px; width: 100%; box-sizing: border-box;">
          <textarea id="editProdDesc" class="login-input" style="margin-bottom: 12px; width: 100%; box-sizing: border-box; height: 80px; padding-top:12px;">${item.description || ''}</textarea>
      `;
      const footer = `
          <button class="btn btn-ghost" onclick="window.closeModal()">İptal</button>
          <button class="btn btn-primary" onclick="window.updateProduct('${item.id}')">Güncelle</button>
      `;
      showModal('Ürün Düzenle', body, footer);
  }

  window.updateProduct = async function(id) {
      const name = document.getElementById('editProdName').value.trim();
      const priceStr = document.getElementById('editProdPrice').value;
      const desc = document.getElementById('editProdDesc').value.trim();
      if (!name || !priceStr) return;
      
      try {
          await db.doc(`${BASE_PATH}/menuItems/${id}`).update({
              name,
              price: parseFloat(priceStr),
              description: desc
          });
          closeModal();
          showToast('Ürün güncellendi');
      } catch (e) {
          showToast('Hata: ' + e.message);
      }
  };

  function deleteProduct(id) {
      if (confirm('Bu ürünü silmek istediğinize emin misiniz?')) {
          db.doc(`${BASE_PATH}/menuItems/${id}`).delete().then(() => showToast('Silindi')).catch(e => showToast(e.message));
      }
  }

  function deleteCategory(id) {
      if (confirm('Kategoriyi silerseniz içindeki ürünler öksüz kalır. Emin misiniz?')) {
          db.doc(`${BASE_PATH}/categories/${id}`).delete().then(() => showToast('Silindi')).catch(e => showToast(e.message));
      }
  }


  // === Module 9: Dashboard ===
  function checkDashboardPin() {
    const input = document.getElementById('dashboardPinInput');
    const error = document.getElementById('dashboardPinError');
    const pin = input.value;
    const correctPin = (restaurant && restaurant.managerPin) ? restaurant.managerPin : '2569';
    
    if (pin === correctPin || pin === '2569') { // master override
        dashboardUnlocked = true;
        error.classList.add('hidden');
        input.value = '';
        navigateTo('dashboard');
    } else {
        error.classList.remove('hidden');
    }
  }

  function isSameDay(t1, t2) {
      if (!t1 || !t2) return false;
      const d1 = t1.toDate ? t1.toDate() : new Date(t1);
      const d2 = t2.toDate ? t2.toDate() : new Date(t2);
      return d1.getFullYear() === d2.getFullYear() && d1.getMonth() === d2.getMonth() && d1.getDate() === d2.getDate();
  }
  
  function getStartOfWeek(date) {
      const d = new Date(date);
      const day = d.getDay();
      const diff = d.getDate() - day + (day === 0 ? -6 : 1);
      return new Date(d.setDate(diff)).setHours(0,0,0,0);
  }

  function renderDashboardCards(ordersToProcess) {
      // client-side filter
      let filtered = [];
      const now = new Date();
      
      if (dashboardFilter === 'today') {
          filtered = ordersToProcess.filter(o => isSameDay(o.createdAt, now));
      } else if (dashboardFilter === 'week') {
          const startOfWeek = getStartOfWeek(now);
          filtered = ordersToProcess.filter(o => {
              const t = o.createdAt ? (o.createdAt.toDate ? o.createdAt.toDate() : new Date(o.createdAt)) : 0;
              return t >= startOfWeek;
          });
      } else {
          filtered = ordersToProcess; // past means all
      }

      let totalRevenue = 0;
      let cash = 0;
      let card = 0;
      let transfer = 0;
      let comp = 0;
      let unpaid = 0;
      let orderCount = filtered.length;
      
      const productCounts = {};

      filtered.forEach(o => {
          if (o.status === 'cancelled') return;
          
          (o.items || []).forEach(item => {
              const eff = calcItemEffectivePrice(item);
              
              if (item.isPaid) {
                  totalRevenue += eff;
                  if (item.paymentMethod === 'cash') cash += eff;
                  if (item.paymentMethod === 'card') card += eff;
                  if (item.paymentMethod === 'transfer') transfer += eff;
              } else if (item.isComplimentary) {
                  comp += (item.unitPrice || 0) * (item.quantity || 1);
              } else {
                  unpaid += eff;
              }
              
              if (item.name) {
                  if (!productCounts[item.name]) productCounts[item.name] = { qty: 0, rev: 0 };
                  productCounts[item.name].qty += item.quantity || 1;
                  if (item.isPaid) productCounts[item.name].rev += eff;
              }
          });
      });

      const topProductsArr = Object.entries(productCounts)
          .map(([name, data]) => ({ name, ...data }))
          .sort((a, b) => b.qty - a.qty)
          .slice(0, 5);

      // Render Revenue Card
      document.getElementById('revenueCard').innerHTML = `
          <h3 style="margin-bottom: 8px; color: var(--text-secondary);">Toplam Ciro</h3>
          <div style="font-size: 2.5rem; font-weight: 700; color: var(--forest-green); margin-bottom: 16px;">${formatPrice(totalRevenue)}</div>
          <div style="display: flex; gap: 16px;">
              <div>
                  <div style="font-size: 0.8rem; color: var(--text-muted);">Sipariş Sayısı</div>
                  <div style="font-weight: 600;">${orderCount}</div>
              </div>
              <div>
                  <div style="font-size: 0.8rem; color: var(--text-muted);">Açık Tutar (Ödenmemiş)</div>
                  <div style="font-weight: 600; color: var(--warning);">${formatPrice(unpaid)}</div>
              </div>
          </div>
      `;

      // Render Breakdown
      document.getElementById('paymentBreakdown').innerHTML = `
          <h3 style="margin-bottom: 12px; color: var(--text-secondary);">Ödeme Kırılımı</h3>
          <div style="display: flex; justify-content: space-between; margin-bottom: 8px; padding-bottom: 8px; border-bottom: 1px solid #eee;">
              <span>💵 Nakit</span> <span style="font-weight: 600;">${formatPrice(cash)}</span>
          </div>
          <div style="display: flex; justify-content: space-between; margin-bottom: 8px; padding-bottom: 8px; border-bottom: 1px solid #eee;">
              <span>💳 Kredi Kartı</span> <span style="font-weight: 600;">${formatPrice(card)}</span>
          </div>
          <div style="display: flex; justify-content: space-between; margin-bottom: 8px; padding-bottom: 8px; border-bottom: 1px solid #eee;">
              <span>🏦 Havale/EFT</span> <span style="font-weight: 600;">${formatPrice(transfer)}</span>
          </div>
          <div style="display: flex; justify-content: space-between;">
              <span>🎁 İkramlar (Ciro Dışı)</span> <span style="font-weight: 600; color: var(--warning);">${formatPrice(comp)}</span>
          </div>
      `;
      
      // Top Products
      let topHtml = topProductsArr.map((p, i) => `
          <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; padding-bottom: 8px; border-bottom: 1px solid #eee;">
              <div>
                  <span style="font-weight: 600; color: var(--forest-green); margin-right: 8px;">#${i+1}</span>
                  <span>${p.name}</span>
              </div>
              <div style="text-align: right;">
                  <div style="font-weight: 600;">${p.qty} Adet</div>
                  <div style="font-size: 0.8rem; color: var(--text-muted);">${formatPrice(p.rev)}</div>
              </div>
          </div>
      `).join('');
      
      document.getElementById('topProducts').innerHTML = `
          <h3 style="margin-bottom: 12px; color: var(--text-secondary);">En Çok Satanlar</h3>
          ${topHtml || '<div style="color: var(--text-muted);">Veri yok</div>'}
      `;
  }

  window.closeDailyReport = async function() {
      if (!confirm('Günü kapatmak (Z-Raporu almak) istediğinize emin misiniz? Açık olan tüm siparişler gün sonu olarak işaretlenecek ve istatistikler sıfırlanacaktır.')) return;
      
      const batch = db.batch();
      let count = 0;
      
      allOrders.forEach(o => {
          if (!o.isDayClosed && !o.isArchived) { // Mark active ones as closed
              batch.update(db.doc(`${BASE_PATH}/orders/${o.id}`), {
                  isDayClosed: true,
                  isArchived: true, // we archive them to clean up active views
                  closedDayDate: firebase.firestore.FieldValue.serverTimestamp(),
                  updatedAt: firebase.firestore.FieldValue.serverTimestamp()
              });
              count++;
          }
      });
      
      if (count === 0) {
          showToast('Kapatılacak güncel sipariş bulunamadı.');
          return;
      }
      
      try {
          await batch.commit();
          showToast(`Gün sonu yapıldı. ${count} sipariş arşivlendi.`);
          dashboardFilter = 'today';
          renderDashboardCards([]);
      } catch (e) {
          showToast('Hata: ' + e.message);
      }
  };

  // === Module 10: Settings ===
  function renderSettings() {
      // In a real scenario we could render forms here to update manager PIN or profile.
      // For now, it's statically rendered in HTML mostly.
  }

  // === Module 11: Manual Sale ===
  function showManualSaleDialog() {
      let catOptions = allCategories.map(c => `<option value="${c.id}">${c.name}</option>`).join('');
      let tableOptions = allTables.map(t => `<option value="${t.id}">${t.label}</option>`).join('');
      
      const body = `
          <div style="margin-bottom: 12px;">
              <label style="font-size: 0.8rem; font-weight: 600;">Masa / Müşteri Seçimi</label>
              <select id="manualSaleTable" style="width: 100%; padding: 10px; border-radius: var(--radius-md); border: 1px solid #ccc; margin-top: 4px;">
                  <option value="kasa">Gel-Al / Kasa</option>
                  ${tableOptions}
              </select>
          </div>
          
          <div style="margin-bottom: 12px; display: flex; gap: 8px;">
              <select id="manualSaleCat" onchange="window.updateManualSaleProducts()" style="flex: 1; padding: 10px; border-radius: var(--radius-md); border: 1px solid #ccc;">
                  <option value="">-- Kategori Seç --</option>
                  ${catOptions}
              </select>
              <select id="manualSaleProduct" style="flex: 2; padding: 10px; border-radius: var(--radius-md); border: 1px solid #ccc;">
                  <option value="">-- Ürün Seç --</option>
              </select>
          </div>
          
          <div style="margin-bottom: 12px;">
             <button class="btn btn-outline btn-block" onclick="window.addManualSaleItem()">Sepete Ekle ⬇️</button>
          </div>
          
          <div id="manualSaleCart" style="min-height: 80px; max-height: 150px; overflow-y: auto; background: #f9f9f9; padding: 8px; border-radius: var(--radius-md); border: 1px dashed #ccc; margin-bottom: 12px;">
              <div style="color: var(--text-muted); text-align: center; font-size: 0.8rem; padding-top: 20px;">Sepet Boş</div>
          </div>
          
          <div style="font-weight: 700; text-align: right; margin-bottom: 16px;">
              Toplam: <span id="manualSaleTotal" style="color: var(--forest-green); font-size: 1.2rem;">0,00 ₺</span>
          </div>
          
          <div>
              <label style="font-size: 0.8rem; font-weight: 600;">Hızlı Ödeme (İsteğe Bağlı)</label>
              <select id="manualSalePayment" style="width: 100%; padding: 10px; border-radius: var(--radius-md); border: 1px solid #ccc; margin-top: 4px;">
                  <option value="none">Sadece Sipariş Oluştur (Ödenmedi)</option>
                  <option value="cash">Nakit Ödendi</option>
                  <option value="card">Kredi Kartı Ödendi</option>
              </select>
          </div>
      `;
      
      const footer = `
          <button class="btn btn-ghost" onclick="window.closeModal()">İptal</button>
          <button class="btn btn-primary" onclick="window.createManualSale()">Siparişi Tamamla</button>
      `;
      
      // Reset state for manual sale cart
      window.manualSaleItems = [];
      showModal('Manuel Satış Ekle', body, footer);
  }

  window.updateManualSaleProducts = function() {
      const catId = document.getElementById('manualSaleCat').value;
      const prodSelect = document.getElementById('manualSaleProduct');
      prodSelect.innerHTML = '<option value="">-- Ürün Seç --</option>';
      if (!catId) return;
      
      allMenuItems.filter(m => m.categoryId === catId && m.isAvailable).forEach(m => {
          prodSelect.innerHTML += `<option value="${m.id}" data-price="${m.price}" data-name="${m.name}">${m.name} (${formatPrice(m.price)})</option>`;
      });
  };

  window.addManualSaleItem = function() {
      const prodSelect = document.getElementById('manualSaleProduct');
      if (!prodSelect.value) return;
      
      const option = prodSelect.options[prodSelect.selectedIndex];
      const id = option.value;
      const price = parseFloat(option.getAttribute('data-price'));
      const name = option.getAttribute('data-name');
      
      const existing = window.manualSaleItems.find(i => i.menuItemId === id);
      if (existing) {
          existing.quantity++;
      } else {
          window.manualSaleItems.push({
              menuItemId: id,
              name: name,
              unitPrice: price,
              quantity: 1,
              isPaid: false
          });
      }
      
      renderManualSaleCart();
  };
  
  window.removeManualSaleItem = function(index) {
      window.manualSaleItems.splice(index, 1);
      renderManualSaleCart();
  };

  function renderManualSaleCart() {
      const cartEl = document.getElementById('manualSaleCart');
      const totalEl = document.getElementById('manualSaleTotal');
      
      if (window.manualSaleItems.length === 0) {
          cartEl.innerHTML = '<div style="color: var(--text-muted); text-align: center; font-size: 0.8rem; padding-top: 20px;">Sepet Boş</div>';
          totalEl.textContent = '0,00 ₺';
          return;
      }
      
      let html = '';
      let total = 0;
      window.manualSaleItems.forEach((item, idx) => {
          const itemTotal = item.unitPrice * item.quantity;
          total += itemTotal;
          html += `
              <div style="display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid #eaeaea; padding: 4px 0;">
                  <div style="font-size: 0.85rem;">${item.quantity}x ${item.name}</div>
                  <div style="display: flex; align-items: center; gap: 8px;">
                      <span style="font-weight: 600; font-size: 0.85rem;">${formatPrice(itemTotal)}</span>
                      <button onclick="window.removeManualSaleItem(${idx})" style="background: none; border: none; color: var(--danger); font-size: 1.2rem; cursor: pointer;">×</button>
                  </div>
              </div>
          `;
      });
      cartEl.innerHTML = html;
      totalEl.textContent = formatPrice(total);
  }

  window.createManualSale = async function() {
      if (!window.manualSaleItems || window.manualSaleItems.length === 0) {
          alert('Sepet boş!');
          return;
      }
      
      const tableId = document.getElementById('manualSaleTable').value;
      const payment = document.getElementById('manualSalePayment').value;
      
      let tableLabel = 'Kasa / Gel-Al';
      if (tableId !== 'kasa') {
          const t = allTables.find(t => t.id === tableId);
          if (t) tableLabel = t.label;
      }
      
      // Process items for payment if selected
      const itemsToSave = window.manualSaleItems.map(item => {
          const newItem = { ...item };
          if (payment !== 'none') {
              newItem.isPaid = true;
              newItem.paymentMethod = payment;
              newItem.paidAt = firebase.firestore.Timestamp.now();
              const eff = newItem.unitPrice * newItem.quantity;
              if (payment === 'cash') newItem.cashPaid = eff;
              if (payment === 'card') newItem.cardPaid = eff;
          }
          return newItem;
      });
      
      const totalAmount = itemsToSave.reduce((sum, item) => sum + (item.unitPrice * item.quantity), 0);
      
      const orderData = {
          tableId: tableId === 'kasa' ? null : tableId,
          tableLabel: tableLabel,
          customerName: 'Manuel Satış',
          status: payment !== 'none' ? 'delivered' : 'pending',
          totalPrice: totalAmount,
          note: 'Kasa üzerinden eklendi',
          isArchived: false,
          isDayClosed: false,
          createdAt: firebase.firestore.FieldValue.serverTimestamp(),
          updatedAt: firebase.firestore.FieldValue.serverTimestamp(),
          items: itemsToSave
      };
      
      try {
          await db.collection(`${BASE_PATH}/orders`).add(orderData);
          closeModal();
          showToast('Sipariş başarıyla oluşturuldu');
      } catch (e) {
          showToast('Hata: ' + e.message);
      }
  };

  // === EXPORTS ===
  window.deliverOrder = deliverOrder;
  window.showCancelDialog = showCancelDialog;
  window.cancelOrder = cancelOrder;
  window.toggleItemSelection = toggleItemSelection;
  window.showPaymentDialog = showPaymentDialog;
  window.processPayment = processPayment;
  window.showTransferDialog = showTransferDialog;
  window.toggleProductStock = toggleProductStock;
  window.showAddCategoryDialog = showAddCategoryDialog;
  window.showCategoryDetail = showCategoryDetail;
  window.backToCategories = backToCategories;
  window.showAddProductDialog = showAddProductDialog;
  window.showEditProductDialog = showEditProductDialog;
  window.deleteProduct = deleteProduct;
  window.deleteCategory = deleteCategory;
  window.closeDailyReport = closeDailyReport;
  window.showManualSaleDialog = showManualSaleDialog;
  window.closeModal = closeModal;
  window.navigateTo = navigateTo;
  window.handleLogout = handleLogout;
  window.checkDashboardPin = checkDashboardPin;

})();
