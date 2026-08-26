// Initialize Firebase
if (typeof firebaseConfig === 'undefined') {
  console.error("firebaseConfig is not defined. Please check firebase-config.js");
} else {
  firebase.initializeApp(firebaseConfig);
}

const db = firebase.firestore();
const auth = firebase.auth();

// Try anonymous sign-in in background
auth.signInAnonymously().catch(err => {
  console.log("Anonymous sign-in skipped or not enabled:", err.message);
});

// App State
let currentRestId = "sadec-restaurant";
let currentTableId = "table-1";
let currentTableLabel = "Masa 1";
let isQrAuthorized = false;
let clientSecurityKey = "";

let categories = [];
let menuItems = [];
let cart = [];
let activeCategory = "all";
let searchQuery = "";
let selectedProduct = null;
let currentModalQty = 1;
let currentTrackingOrderId = null;
let orderTrackingUnsubscribe = null;

// DOM Elements
const securityBlockViewEl = document.getElementById("securityBlockView");
const menuViewEl = document.getElementById("menuView");
const restNameEl = document.getElementById("restName");
const restLogoEl = document.getElementById("restLogo");
const tableBadgeEl = document.getElementById("tableBadge");
const tableLabelTextEl = document.getElementById("tableLabelText");
const categoryNavEl = document.getElementById("categoryNav");
const menuItemsContainerEl = document.getElementById("menuItemsContainer");
const loadingIndicatorEl = document.getElementById("loadingIndicator");
const searchInputEl = document.getElementById("searchInput");

// Cart Elements
const cartFloatingBarEl = document.getElementById("cartFloatingBar");
const cartBarCountEl = document.getElementById("cartBarCount");
const cartBarTotalEl = document.getElementById("cartBarTotal");

// Modals
const productModalOverlay = document.getElementById("productModalOverlay");
const btnCloseProductModal = document.getElementById("btnCloseProductModal");
const modalProductImg = document.getElementById("modalProductImg");
const modalProductTitle = document.getElementById("modalProductTitle");
const modalProductDesc = document.getElementById("modalProductDesc");
const modalProductPrice = document.getElementById("modalProductPrice");
const modalAllergens = document.getElementById("modalAllergens");
const modalQtyMinus = document.getElementById("modalQtyMinus");
const modalQtyPlus = document.getElementById("modalQtyPlus");
const modalQtyNum = document.getElementById("modalQtyNum");
const modalProductNote = document.getElementById("modalProductNote");
const btnConfirmAddToCart = document.getElementById("btnConfirmAddToCart");
const modalBtnTotal = document.getElementById("modalBtnTotal");

// Cart Modal
const cartModalOverlay = document.getElementById("cartModalOverlay");
const btnCloseCartModal = document.getElementById("btnCloseCartModal");
const cartModalItems = document.getElementById("cartModalItems");
const cartModalTotalPrice = document.getElementById("cartModalTotalPrice");
const cartModalTable = document.getElementById("cartModalTable");
const cartOrderNote = document.getElementById("cartOrderNote");
const btnSubmitOrder = document.getElementById("btnSubmitOrder");

// Tracking View Elements
const trackingViewEl = document.getElementById("trackingView");
const trackTableTextEl = document.getElementById("trackTableText");
const trackOrderNoEl = document.getElementById("trackOrderNo");
const trackStatusBadgeEl = document.getElementById("trackStatusBadge");
const trackItemsListEl = document.getElementById("trackItemsList");
const trackTotalAmountEl = document.getElementById("trackTotalAmount");
const btnNewOrderEl = document.getElementById("btnNewOrder");
const stepPending = document.getElementById("stepPending");
const stepPreparing = document.getElementById("stepPreparing");
const stepReady = document.getElementById("stepReady");
const stepDelivered = document.getElementById("stepDelivered");

// Helper: Format Currency (₺)
function formatCurrency(amount) {
  return new Intl.NumberFormat('tr-TR', { style: 'currency', currency: 'TRY' }).format(amount);
}

// 1. Parse URL Parameters & Security Key
function initUrlParams() {
  const params = new URLSearchParams(window.location.search);
  
  if (params.get("restId") || params.get("restaurantId") || params.get("restaurant")) {
    currentRestId = params.get("restId") || params.get("restaurantId") || params.get("restaurant");
  }
  
  if (params.get("table") || params.get("tableLabel") || params.get("masa")) {
    const rawTable = params.get("table") || params.get("tableLabel") || params.get("masa");
    currentTableLabel = rawTable.toLowerCase().startsWith("masa") ? rawTable : `Masa ${rawTable}`;
    currentTableId = params.get("tableId") || (rawTable.startsWith("table-") ? rawTable : `table-${rawTable}`);
  } else if (params.get("tableId")) {
    currentTableId = params.get("tableId");
    currentTableLabel = `Masa (${currentTableId})`;
  }

  // Check QR Security Key from URL or Session Storage
  const urlKey = params.get("key") || params.get("k") || params.get("token") || params.get("sig");
  const sessionKey = sessionStorage.getItem("sadec_qr_auth_" + currentTableId);

  if (urlKey) {
    clientSecurityKey = urlKey;
    sessionStorage.setItem("sadec_qr_auth_" + currentTableId, urlKey);

    // Clean address bar so the secret key cannot be copied or shared via URL
    try {
      const cleanUrl = window.location.pathname + `?restId=${currentRestId}&table=${currentTableId}`;
      window.history.replaceState({}, document.title, cleanUrl);
    } catch(e) {}
  } else if (sessionKey) {
    clientSecurityKey = sessionKey;
  }

  tableLabelTextEl.textContent = currentTableLabel;
  cartModalTable.textContent = currentTableLabel;

  validateSecurity();
}

// 2. Validate Security with Firestore Table Key
function validateSecurity() {
  db.collection("restaurants").doc(currentRestId).collection("tables").doc(currentTableId)
    .onSnapshot(doc => {
      if (doc.exists) {
        const tableData = doc.data();
        if (tableData.label) {
          currentTableLabel = tableData.label;
          tableLabelTextEl.textContent = tableData.label;
          cartModalTable.textContent = tableData.label;
        }

        // Check if QR key is configured
        if (tableData.qrKey && tableData.qrKey.trim() !== "") {
          if (clientSecurityKey === tableData.qrKey) {
            isQrAuthorized = true;
            showMenuView();
          } else {
            isQrAuthorized = false;
            showSecurityLock();
          }
        } else {
          // If no security key is set yet on the table, permit access
          isQrAuthorized = true;
          showMenuView();
        }
      } else {
        // Table not found in DB
        if (clientSecurityKey) {
          isQrAuthorized = true;
          showMenuView();
        } else {
          isQrAuthorized = false;
          showSecurityLock();
        }
      }
    }, err => {
      console.log("Masa güvenlik kontrolü hatası:", err.message);
      // Fallback
      showMenuView();
    });
}

function showSecurityLock() {
  if (securityBlockViewEl) securityBlockViewEl.style.display = "flex";
  if (menuViewEl) menuViewEl.style.display = "none";
  if (cartFloatingBarEl) cartFloatingBarEl.style.display = "none";
}

function showMenuView() {
  if (securityBlockViewEl) securityBlockViewEl.style.display = "none";
  if (menuViewEl) menuViewEl.style.display = "block";
}

// 3. Fetch Restaurant Details & Listen Realtime
function listenRestaurantData() {
  db.collection("restaurants").doc(currentRestId).onSnapshot(doc => {
    if (doc.exists) {
      const data = doc.data();
      if (data.name) {
        restNameEl.textContent = data.name;
        document.title = `${data.name} - QR Menü`;
      }
    }
  }, err => console.log("Restoran bilgisi dinleme:", err.message));
}

// 4. Listen Categories Realtime
function listenCategories() {
  db.collection("restaurants").doc(currentRestId).collection("categories")
    .orderBy("sortOrder", "asc")
    .onSnapshot(snapshot => {
      categories = [];
      snapshot.forEach(doc => {
        categories.push({ id: doc.id, ...doc.data() });
      });

      if (categories.length === 0) {
        // Otomatik olarak tüm Sade C Gerze menüsünü yükle
        seedSampleData().catch(console.error);
      } else {
        renderCategories();
      }
    }, err => {
      console.log("Kategori dinleme hatası:", err.message);
      db.collection("restaurants").doc(currentRestId).collection("categories")
        .onSnapshot(snap => {
          categories = snap.docs.map(d => ({ id: d.id, ...d.data() }));
          if (categories.length === 0) {
            seedSampleData().catch(console.error);
          } else {
            renderCategories();
          }
        });
    });
}

function renderCategories() {
  categoryNavEl.innerHTML = `<button class="category-btn ${activeCategory === 'all' ? 'active' : ''}" data-category="all">Tümü</button>`;
  
  categories.forEach(cat => {
    const btn = document.createElement("button");
    btn.className = `category-btn ${activeCategory === cat.id ? 'active' : ''}`;
    btn.dataset.category = cat.id;
    btn.textContent = cat.name;
    btn.addEventListener("click", () => {
      activeCategory = cat.id;
      document.querySelectorAll(".category-btn").forEach(b => b.classList.remove("active"));
      btn.classList.add("active");
      renderMenuItems();
    });
    categoryNavEl.appendChild(btn);
  });

  // Attach click to 'all'
  categoryNavEl.querySelector('[data-category="all"]').addEventListener("click", (e) => {
    activeCategory = "all";
    document.querySelectorAll(".category-btn").forEach(b => b.classList.remove("active"));
    e.target.classList.add("active");
    renderMenuItems();
  });
}

// 5. Listen Menu Items Realtime
function listenMenuItems() {
  db.collection("restaurants").doc(currentRestId).collection("menuItems")
    .orderBy("sortOrder", "asc")
    .onSnapshot(snapshot => {
      menuItems = [];
      snapshot.forEach(doc => {
        menuItems.push({ id: doc.id, ...doc.data() });
      });
      loadingIndicatorEl.style.display = "none";
      renderMenuItems();
    }, err => {
      console.log("Menü dinleme hatası, indexsiz deneniyor:", err.message);
      db.collection("restaurants").doc(currentRestId).collection("menuItems")
        .onSnapshot(snap => {
          menuItems = snap.docs.map(d => ({ id: d.id, ...d.data() }));
          loadingIndicatorEl.style.display = "none";
          renderMenuItems();
        });
    });
}

function renderMenuItems() {
  let filtered = menuItems.filter(item => item.isAvailable !== false);

  if (activeCategory !== "all") {
    filtered = filtered.filter(item => item.categoryId === activeCategory);
  }

  if (searchQuery.trim() !== "") {
    const q = searchQuery.toLowerCase().trim();
    filtered = filtered.filter(item => 
      item.name.toLowerCase().includes(q) || 
      (item.description && item.description.toLowerCase().includes(q))
    );
  }

  if (filtered.length === 0) {
    menuItemsContainerEl.innerHTML = `
      <div class="empty-state">
        <div style="font-size: 2rem; margin-bottom: 8px;">☕</div>
        <p>Aradığınız kriterde ürün bulunamadı.</p>
      </div>
    `;
    return;
  }

  // Group by category if "all" is active
  if (activeCategory === "all" && searchQuery.trim() === "") {
    let html = "";
    categories.forEach(cat => {
      const catItems = filtered.filter(i => i.categoryId === cat.id);
      if (catItems.length > 0) {
        html += `<h3 class="section-title">${cat.name}</h3>`;
        catItems.forEach(item => {
          html += createProductCardHtml(item);
        });
      }
    });

    const otherItems = filtered.filter(i => !categories.some(c => c.id === i.categoryId));
    if (otherItems.length > 0) {
      html += `<h3 class="section-title">Diğer Lezzetler</h3>`;
      otherItems.forEach(item => {
        html += createProductCardHtml(item);
      });
    }

    menuItemsContainerEl.innerHTML = html;
  } else {
    menuItemsContainerEl.innerHTML = filtered.map(item => createProductCardHtml(item)).join("");
  }

  // Attach card click handlers
  document.querySelectorAll(".product-card").forEach(card => {
    card.addEventListener("click", () => {
      const itemId = card.dataset.id;
      const product = menuItems.find(i => i.id === itemId);
      if (product) openProductModal(product);
    });
  });
}

function createProductCardHtml(item) {
  const fallbackImg = item.imageUrl || "images/hot_coffee.jpg";
  const allergensHtml = (item.allergens && item.allergens.length > 0)
    ? `<div class="allergens-list">${item.allergens.map(a => `<span class="allergen-tag">${a}</span>`).join("")}</div>`
    : "";

  return `
    <div class="product-card" data-id="${item.id}">
      <div class="product-info">
        <div>
          <h3 class="product-title">${item.name}</h3>
          <p class="product-desc">${item.description || ''}</p>
          ${allergensHtml}
        </div>
        <div class="product-bottom">
          <span class="product-price">${formatCurrency(item.price)}</span>
        </div>
      </div>
      <div class="product-image-container">
        <img src="${fallbackImg}" alt="${item.name}" class="product-image" onerror="this.src='logo.png'">
        <div class="add-btn-badge">+</div>
      </div>
    </div>
  `;
}

// Search Filter
searchInputEl.addEventListener("input", (e) => {
  searchQuery = e.target.value;
  renderMenuItems();
});

// 6. Product Detail / Add to Cart Modal
function openProductModal(product) {
  selectedProduct = product;
  currentModalQty = 1;
  modalQtyNum.textContent = "1";
  modalProductNote.value = "";

  modalProductTitle.textContent = product.name;
  modalProductDesc.textContent = product.description || "Özel Sade.C reçetesiyle taptaze hazırlanır.";
  modalProductPrice.textContent = formatCurrency(product.price);
  modalProductImg.src = product.imageUrl || "images/hot_coffee.jpg";

  if (product.allergens && product.allergens.length > 0) {
    modalAllergens.innerHTML = product.allergens.map(a => `<span class="allergen-tag">⚠️ ${a}</span>`).join("");
  } else {
    modalAllergens.innerHTML = "";
  }

  updateModalTotal();
  productModalOverlay.classList.add("active");
}

function updateModalTotal() {
  if (!selectedProduct) return;
  const total = selectedProduct.price * currentModalQty;
  modalBtnTotal.textContent = formatCurrency(total);
}

modalQtyMinus.addEventListener("click", () => {
  if (currentModalQty > 1) {
    currentModalQty--;
    modalQtyNum.textContent = currentModalQty;
    updateModalTotal();
  }
});

modalQtyPlus.addEventListener("click", () => {
  currentModalQty++;
  modalQtyNum.textContent = currentModalQty;
  updateModalTotal();
});

btnCloseProductModal.addEventListener("click", () => {
  productModalOverlay.classList.remove("active");
});

productModalOverlay.addEventListener("click", (e) => {
  if (e.target === productModalOverlay) productModalOverlay.classList.remove("active");
});

btnConfirmAddToCart.addEventListener("click", () => {
  if (!selectedProduct) return;

  const note = modalProductNote.value.trim();
  const existingIdx = cart.findIndex(i => i.menuItemId === selectedProduct.id && i.note === note);

  if (existingIdx > -1) {
    cart[existingIdx].quantity += currentModalQty;
  } else {
    cart.push({
      menuItemId: selectedProduct.id,
      name: selectedProduct.name,
      unitPrice: selectedProduct.price,
      quantity: currentModalQty,
      note: note
    });
  }

  productModalOverlay.classList.remove("active");
  updateCartUI();
});

// 7. Cart UI & Management
function updateCartUI() {
  const totalCount = cart.reduce((sum, item) => sum + item.quantity, 0);
  const totalPrice = cart.reduce((sum, item) => sum + (item.unitPrice * item.quantity), 0);

  if (totalCount > 0 && isQrAuthorized) {
    cartFloatingBarEl.style.display = "flex";
    cartBarCountEl.textContent = totalCount;
    cartBarTotalEl.textContent = formatCurrency(totalPrice);
  } else {
    cartFloatingBarEl.style.display = "none";
  }

  // Render Cart Modal Items
  if (cart.length === 0) {
    cartModalItems.innerHTML = `<p style="text-align: center; color: #64748b; padding: 20px;">Sepetiniz boş.</p>`;
    btnSubmitOrder.disabled = true;
  } else {
    btnSubmitOrder.disabled = false;
    cartModalItems.innerHTML = cart.map((item, idx) => `
      <div class="cart-item-row">
        <div class="cart-item-info">
          <h4>${item.name}</h4>
          <span>${formatCurrency(item.unitPrice)}</span>
          ${item.note ? `<div class="cart-item-note">"${item.note}"</div>` : ''}
        </div>
        <div class="qty-controls">
          <button class="qty-btn" onclick="changeCartQty(${idx}, -1)">-</button>
          <span class="qty-number">${item.quantity}</span>
          <button class="qty-btn" onclick="changeCartQty(${idx}, 1)">+</button>
        </div>
      </div>
    `).join("");
  }

  cartModalTotalPrice.textContent = formatCurrency(totalPrice);
}

window.changeCartQty = function(index, delta) {
  if (cart[index]) {
    cart[index].quantity += delta;
    if (cart[index].quantity <= 0) {
      cart.splice(index, 1);
    }
    updateCartUI();
  }
};

cartFloatingBarEl.addEventListener("click", () => {
  cartModalOverlay.classList.add("active");
});

btnCloseCartModal.addEventListener("click", () => {
  cartModalOverlay.classList.remove("active");
});

cartModalOverlay.addEventListener("click", (e) => {
  if (e.target === cartModalOverlay) cartModalOverlay.classList.remove("active");
});

// 8. Submit Order to Firestore
btnSubmitOrder.addEventListener("click", async () => {
  if (cart.length === 0 || !isQrAuthorized) return;

  btnSubmitOrder.disabled = true;
  btnSubmitOrder.textContent = "Sipariş Gönderiliyor...";

  const totalPrice = cart.reduce((sum, item) => sum + (item.unitPrice * item.quantity), 0);
  const generalNote = cartOrderNote.value.trim();

  const orderData = {
    tableId: currentTableId,
    tableLabel: currentTableLabel,
    status: "pending",
    totalPrice: totalPrice,
    note: generalNote,
    items: cart.map(item => ({
      menuItemId: item.menuItemId,
      name: item.name,
      quantity: item.quantity,
      unitPrice: item.unitPrice,
      note: item.note || ""
    })),
    createdAt: firebase.firestore.FieldValue.serverTimestamp(),
    updatedAt: firebase.firestore.FieldValue.serverTimestamp()
  };

  try {
    const docRef = await db.collection("restaurants").doc(currentRestId).collection("orders").add(orderData);
    cart = [];
    updateCartUI();
    cartModalOverlay.classList.remove("active");
    startOrderTracking(docRef.id, orderData);

  } catch (error) {
    console.error("Sipariş hatası:", error);
    alert("Sipariş iletilirken bir hata oluştu: " + error.message);
  } finally {
    btnSubmitOrder.disabled = false;
    btnSubmitOrder.textContent = "Siparişi Onayla & Gönder 🚀";
  }
});

// 9. Live Order Tracking
function startOrderTracking(orderId, initialData) {
  currentTrackingOrderId = orderId;
  menuViewEl.style.display = "none";
  trackingViewEl.style.display = "block";
  window.scrollTo({ top: 0, behavior: 'smooth' });

  trackOrderNoEl.textContent = `#${orderId.slice(-5).toUpperCase()}`;
  trackTableTextEl.textContent = initialData.tableLabel;
  trackTotalAmountEl.textContent = formatCurrency(initialData.totalPrice);

  trackItemsListEl.innerHTML = initialData.items.map(i => `
    <div style="display: flex; justify-content: space-between; margin-bottom: 4px;">
      <span>${i.quantity}x ${i.name} ${i.note ? `<small style="color: #d97706;">(${i.note})</small>` : ''}</span>
      <span>${formatCurrency(i.unitPrice * i.quantity)}</span>
    </div>
  `).join("");

  // Listen live status updates
  if (orderTrackingUnsubscribe) orderTrackingUnsubscribe();

  orderTrackingUnsubscribe = db.collection("restaurants")
    .doc(currentRestId)
    .collection("orders")
    .doc(orderId)
    .onSnapshot(doc => {
      if (doc.exists) {
        const data = doc.data();
        updateTrackingUI(data.status);
      }
    });
}

function updateTrackingUI(status) {
  // Reset all steps
  [stepPending, stepPreparing, stepReady, stepDelivered].forEach(s => s.classList.remove("active"));

  if (status === "pending") {
    trackStatusBadgeEl.className = "status-badge-lg status-pending";
    trackStatusBadgeEl.textContent = "🕒 Siparişiniz Alındı";
    stepPending.classList.add("active");
  } else if (status === "preparing") {
    trackStatusBadgeEl.className = "status-badge-lg status-preparing";
    trackStatusBadgeEl.textContent = "👨‍🍳 Barista Hazırlıyor";
    stepPending.classList.add("active");
    stepPreparing.classList.add("active");
  } else if (status === "ready") {
    trackStatusBadgeEl.className = "status-badge-lg status-ready";
    trackStatusBadgeEl.textContent = "☕ Siparişiniz Hazır!";
    stepPending.classList.add("active");
    stepPreparing.classList.add("active");
    stepReady.classList.add("active");
  } else if (status === "delivered") {
    trackStatusBadgeEl.className = "status-badge-lg status-delivered";
    trackStatusBadgeEl.textContent = "✅ Masanıza Teslim Edildi";
    stepPending.classList.add("active");
    stepPreparing.classList.add("active");
    stepReady.classList.add("active");
    stepDelivered.classList.add("active");
  } else if (status === "cancelled") {
    trackStatusBadgeEl.className = "status-badge-lg status-cancelled";
    trackStatusBadgeEl.textContent = "❌ Sipariş İptal Edildi";
  }
}

btnNewOrderEl.addEventListener("click", () => {
  trackingViewEl.style.display = "none";
  menuViewEl.style.display = "block";
});

// 10. Seed Sade.C Kahve Gerze Full Menu Dataset
async function seedSampleData() {
  try {
    const restRef = db.collection("restaurants").doc(currentRestId);
    
    await restRef.set({
      name: "Sade.C Kahve Gerze",
      slug: currentRestId,
      address: "Gerze / Sinop",
      phone: "0555 123 45 67",
      instagram: "@sadeckahve",
      logoUrl: "logo.png"
    }, { merge: true });

    // 1. Categories
    const catHot = await restRef.collection("categories").add({ name: "Sıcak Kahveler & İçecekler", sortOrder: 1 });
    const catCold = await restRef.collection("categories").add({ name: "Soğuk İçecekler & Kahveler", sortOrder: 2 });
    const catSpecials = await restRef.collection("categories").add({ name: "Spesiyeller & Sandviçler", sortOrder: 3 });
    const catSnack = await restRef.collection("categories").add({ name: "Atıştırmalıklar & Tostlar", sortOrder: 4 });
    const catDessert = await restRef.collection("categories").add({ name: "Tatlılar & Pastalar", sortOrder: 5 });

    // 2. Menu Items
    const items = [
      // Sıcaklar
      { categoryId: catHot.id, name: "Espresso", description: "30ml klasik yoğun İtalyan espresso", price: 100, imageUrl: "images/hot_coffee.jpg", allergens: ["Kafein"], sortOrder: 1 },
      { categoryId: catHot.id, name: "Double Espresso", description: "60ml çift shot yoğun lezzet", price: 120, imageUrl: "images/hot_coffee.jpg", allergens: ["Kafein"], sortOrder: 2 },
      { categoryId: catHot.id, name: "Double Shot Americano", description: "150ml sıcak su ve 60ml espresso", price: 140, imageUrl: "images/hot_coffee.jpg", allergens: ["Kafein"], sortOrder: 3 },
      { categoryId: catHot.id, name: "Caffe Latte", description: "30ml espresso ve 150ml kadifemsi sıcak süt", price: 170, imageUrl: "images/hot_coffee.jpg", allergens: ["Süt", "Kafein"], sortOrder: 4 },
      { categoryId: catHot.id, name: "Cappuccino", description: "Espresso, sıcak süt ve yoğun süt köpüğü üzeri çikolata tozu", price: 170, imageUrl: "images/hot_coffee.jpg", allergens: ["Süt", "Kafein"], sortOrder: 5 },
      { categoryId: catHot.id, name: "Espresso Macchiato", description: "30ml espresso ve bir kaşık taze süt köpüğü", price: 150, imageUrl: "images/hot_coffee.jpg", allergens: ["Süt", "Kafein"], sortOrder: 6 },
      { categoryId: catHot.id, name: "Caramel Macchiato", description: "30ml espresso, 180ml süt ve 30ml karamel şurubu", price: 160, imageUrl: "images/hot_coffee.jpg", allergens: ["Süt", "Kafein"], sortOrder: 7 },
      { categoryId: catHot.id, name: "Mocha", description: "30ml espresso, 20gr eritilmiş çikolata, 130ml süt ve süt kreması", price: 190, imageUrl: "images/hot_coffee.jpg", allergens: ["Süt", "Kafein"], sortOrder: 8 },
      { categoryId: catHot.id, name: "White Chocolate Mocha", description: "Espresso, beyaz çikolata, sıcak süt ve süt kreması", price: 190, imageUrl: "images/hot_coffee.jpg", allergens: ["Süt", "Kafein"], sortOrder: 9 },
      { categoryId: catHot.id, name: "Cortado", description: "60ml espresso ve 60ml süt kreması dengesi", price: 160, imageUrl: "images/hot_coffee.jpg", allergens: ["Süt", "Kafein"], sortOrder: 10 },
      { categoryId: catHot.id, name: "Flat White", description: "60ml double espresso ve 120ml mikro köpüklü sıcak süt", price: 170, imageUrl: "images/hot_coffee.jpg", allergens: ["Süt", "Kafein"], sortOrder: 11 },
      { categoryId: catHot.id, name: "Black Eye", description: "180ml filtre kahve üzerine 60ml double espresso takviyesi", price: 160, imageUrl: "images/hot_coffee.jpg", allergens: ["Kafein"], sortOrder: 12 },
      { categoryId: catHot.id, name: "Filtre Kahve V60", description: "Kolombiya, Sumatra, Guatemala nitelikli çekirdekleri ile manuel demleme", price: 180, imageUrl: "images/hot_coffee.jpg", allergens: ["Kafein"], sortOrder: 13 },
      { categoryId: catHot.id, name: "Filtre Kahve Makina", description: "Taze öğütülmüş Kolombiya çekirdekleri ile taze demleme", price: 120, imageUrl: "images/hot_coffee.jpg", allergens: ["Kafein"], sortOrder: 14 },
      { categoryId: catHot.id, name: "Siyah Çay", description: "Geleneksel taze demlenmiş Rize çayı", price: 30, imageUrl: "images/hot_coffee.jpg", allergens: [], sortOrder: 15 },
      { categoryId: catHot.id, name: "Bitki Çayları", description: "Adaçayı, Hibiscus, Papatya, Yeşil Çay vb.", price: 150, imageUrl: "images/hot_coffee.jpg", allergens: [], sortOrder: 16 },
      { categoryId: catHot.id, name: "Türk Kahvesi", description: "Çifte kavrulmuş lokum ve su ile geleneksel sunum", price: 75, imageUrl: "images/hot_coffee.jpg", allergens: ["Kafein"], sortOrder: 17 },
      { categoryId: catHot.id, name: "Affogato", description: "Vanilyalı dondurma üzerine dökülen 60ml sıcak espresso", price: 250, imageUrl: "images/hot_coffee.jpg", allergens: ["Süt", "Kafein"], sortOrder: 18 },
      { categoryId: catHot.id, name: "Sıcak Çikolata", description: "180ml sıcak süt ve eritilmiş gerçek çikolata", price: 180, imageUrl: "images/hot_coffee.jpg", allergens: ["Süt"], sortOrder: 19 },
      { categoryId: catHot.id, name: "Beyaz Sıcak Çikolata", description: "180ml sıcak süt ve beyaz çikolata", price: 180, imageUrl: "images/hot_coffee.jpg", allergens: ["Süt"], sortOrder: 20 },
      { categoryId: catHot.id, name: "Pembe Ruby Sıcak Çikolata", description: "180ml sıcak süt ve ruby çikolata lezzeti", price: 180, imageUrl: "images/hot_coffee.jpg", allergens: ["Süt"], sortOrder: 21 },

      // Soğuklar
      { categoryId: catCold.id, name: "Ice Americano", description: "150ml soğuk su, 60ml espresso ve buz", price: 150, imageUrl: "images/cold_drinks.jpg", allergens: ["Kafein"], sortOrder: 1 },
      { categoryId: catCold.id, name: "Ice Latte", description: "130ml soğuk süt, 30ml espresso ve buz", price: 180, imageUrl: "images/cold_drinks.jpg", allergens: ["Süt", "Kafein"], sortOrder: 2 },
      { categoryId: catCold.id, name: "Ice White Chocolate Mocha", description: "30ml espresso, 20gr beyaz çikolata, soğuk süt, krema ve buz", price: 200, imageUrl: "images/cold_drinks.jpg", allergens: ["Süt", "Kafein"], sortOrder: 3 },
      { categoryId: catCold.id, name: "Ice Mocha", description: "30ml espresso, çikolata, soğuk süt, süt kreması ve buz", price: 200, imageUrl: "images/cold_drinks.jpg", allergens: ["Süt", "Kafein"], sortOrder: 4 },
      { categoryId: catCold.id, name: "Ice Caramel Macchiato", description: "Espresso, soğuk süt, 30ml karamel sosu ve buz", price: 190, imageUrl: "images/cold_drinks.jpg", allergens: ["Süt", "Kafein"], sortOrder: 5 },
      { categoryId: catCold.id, name: "Frapeler", description: "Çilekli, Muzlu, Kakaolu, Karpuzlu, Yeşil Elmalı, Mangolu", price: 180, imageUrl: "images/cold_drinks.jpg", allergens: ["Süt"], sortOrder: 6 },
      { categoryId: catCold.id, name: "Cold Brew", description: "16 saat soğuk demlenmiş özel Kolombiya kahvesi", price: 180, imageUrl: "images/cold_drinks.jpg", allergens: ["Kafein"], sortOrder: 7 },
      { categoryId: catCold.id, name: "Ev Yapımı Limonata", description: "Taze sıkım limon, nane ve buz (200ml)", price: 160, imageUrl: "images/cold_drinks.jpg", allergens: [], sortOrder: 8 },
      { categoryId: catCold.id, name: "Ev Yapımı Erik Suyu", description: "Doğal köy eriği, buz ile ferahlatıcı sunum (200ml)", price: 180, imageUrl: "images/cold_drinks.jpg", allergens: [], sortOrder: 9 },
      { categoryId: catCold.id, name: "Ice Hibiscus Çayı", description: "Soğuk demlenmiş ekşi-tatlı hibiscus çayı ve buz", price: 200, imageUrl: "images/cold_drinks.jpg", allergens: [], sortOrder: 10 },
      { categoryId: catCold.id, name: "Muzlu Milkshake", description: "Hakiki dondurma ve taze muz (200ml)", price: 200, imageUrl: "images/cold_drinks.jpg", allergens: ["Süt"], sortOrder: 11 },
      { categoryId: catCold.id, name: "Mangolu Milkshake", description: "Hakiki dondurma ve mango püresi (200ml)", price: 200, imageUrl: "images/cold_drinks.jpg", allergens: ["Süt"], sortOrder: 12 },
      { categoryId: catCold.id, name: "Çilekli Milkshake", description: "Hakiki dondurma ve taze çilek püresi (200ml)", price: 200, imageUrl: "images/cold_drinks.jpg", allergens: ["Süt"], sortOrder: 13 },
      { categoryId: catCold.id, name: "Çikolatalı Milkshake", description: "Hakiki dondurma ve Belçika çikolatası (200ml)", price: 200, imageUrl: "images/cold_drinks.jpg", allergens: ["Süt"], sortOrder: 14 },
      { categoryId: catCold.id, name: "Soda Limon", description: "Doğal maden suyu ve taze limon dilimleri", price: 70, imageUrl: "images/cold_drinks.jpg", allergens: [], sortOrder: 15 },
      { categoryId: catCold.id, name: "Coca Cola", description: "330ml Kutu", price: 80, imageUrl: "images/cold_drinks.jpg", allergens: [], sortOrder: 16 },
      { categoryId: catCold.id, name: "Fanta", description: "330ml Kutu", price: 80, imageUrl: "images/cold_drinks.jpg", allergens: [], sortOrder: 17 },
      { categoryId: catCold.id, name: "Sprite", description: "330ml Kutu", price: 80, imageUrl: "images/cold_drinks.jpg", allergens: [], sortOrder: 18 },
      { categoryId: catCold.id, name: "Bardak Su", description: "Doğal kaynak suyu", price: 30, imageUrl: "images/cold_drinks.jpg", allergens: [], sortOrder: 19 },

      // Spesiyeller
      { categoryId: catSpecials.id, name: "Köfte Sandviç", description: "Ciabatta ekmeği, dana köfte, eritilmiş mozzarella, karamelize soğan, közlenmiş kapya biber, özel soslar", price: 260, imageUrl: "images/promo_sandwich.jpg", allergens: ["Gluten", "Süt"], sortOrder: 1 },
      { categoryId: catSpecials.id, name: "Tavuk Sandviç", description: "Ciabatta ekmeği, özel marine ızgara tavuk, Akdeniz yeşillikleri, özel dükkan sosları", price: 260, imageUrl: "images/promo_sandwich.jpg", allergens: ["Gluten"], sortOrder: 2 },

      // Atıştırmalıklar & Tostlar
      { categoryId: catSnack.id, name: "Üç Peynirli Bazlama Tost", description: "Mozzarella, kolot ve eski kaşar peynirli fırın bazlama", price: 175, imageUrl: "images/snacks.jpg", allergens: ["Gluten", "Süt"], sortOrder: 1 },
      { categoryId: catSnack.id, name: "Sucuklu Kaşarlı Bazlama Tost", description: "Kavrulmuş dana sucuk ve bol kaşar peyniri", price: 175, imageUrl: "images/snacks.jpg", allergens: ["Gluten", "Süt"], sortOrder: 2 },
      { categoryId: catSnack.id, name: "Mücver (Dip Soslu)", description: "Kabak, havuç, soğan, dereotu, maydanoz, özel baharatlar ve yoğurtlu dip sos", price: 150, imageUrl: "images/snacks.jpg", allergens: ["Gluten", "Yumurta", "Süt"], sortOrder: 3 },
      { categoryId: catSnack.id, name: "Ispanaklı Börek", description: "Ev yapımı çıtır yufka, taze ıspanak", price: 60, imageUrl: "images/snacks.jpg", allergens: ["Gluten", "Yumurta"], sortOrder: 4 },
      { categoryId: catSnack.id, name: "Peynirli Börek", description: "Lor peyniri, beyaz peynir ve Antep peynirli harç", price: 60, imageUrl: "images/snacks.jpg", allergens: ["Gluten", "Süt", "Yumurta"], sortOrder: 5 },
      { categoryId: catSnack.id, name: "Patatesli Börek", description: "Ev yapımı yufka, baharatlı patates", price: 60, imageUrl: "images/snacks.jpg", allergens: ["Gluten"], sortOrder: 6 },
      { categoryId: catSnack.id, name: "Dereotlu Poğaça", description: "Dereotu, maydanoz, havuç ve peynir (110gr)", price: 50, imageUrl: "images/snacks.jpg", allergens: ["Gluten", "Süt", "Yumurta"], sortOrder: 7 },
      { categoryId: catSnack.id, name: "Yumurtalı Peynirli Ekmek", description: "Ezine peyniri, yumurta, maydanoz, dağ kekiği (25dk fırınlanır)", price: 80, imageUrl: "images/snacks.jpg", allergens: ["Gluten", "Süt", "Yumurta"], sortOrder: 8 },

      // Tatlılar
      { categoryId: catDessert.id, name: "San Sebastian Cheesecake", description: "Akışkan sıcak Belçika çikolata sosu ile fırından taptaze", price: 220, imageUrl: "images/promo_cheesecake.jpg", allergens: ["Süt", "Yumurta"], sortOrder: 1 },
      { categoryId: catDessert.id, name: "Amerikan Creamy Nemli Kek", description: "Bol kakaolu yumuşak kek ve özel kakaolu kreması ile", price: 180, imageUrl: "images/desserts.jpg", allergens: ["Gluten", "Süt", "Yumurta"], sortOrder: 2 },
      { categoryId: catDessert.id, name: "Tres Leches (Trileçe)", description: "Mascarpone ve krema ile örtülmüş süt reçelli geleneksel kek", price: 200, imageUrl: "images/desserts.jpg", allergens: ["Gluten", "Süt", "Yumurta"], sortOrder: 3 },
      { categoryId: catDessert.id, name: "Cup Cakes", description: "Limonlu, vanilyalı veya çikolatalı", price: 60, imageUrl: "images/desserts.jpg", allergens: ["Gluten", "Süt", "Yumurta"], sortOrder: 4 },
      { categoryId: catDessert.id, name: "Çilekli Kap Pasta", description: "Taze çilekler ve vanilyalı özel pasta kreması", price: 200, imageUrl: "images/desserts.jpg", allergens: ["Gluten", "Süt", "Yumurta"], sortOrder: 5 },
      { categoryId: catDessert.id, name: "Supangle", description: "Gerçek çikolata pralin ve süt ile", price: 190, imageUrl: "images/desserts.jpg", allergens: ["Süt"], sortOrder: 6 },
      { categoryId: catDessert.id, name: "Tiramisu", description: "Mascarpone ve krema ile hazırlanmış orijinal İtalyan harcı ve kedi dilleri", price: 200, imageUrl: "images/desserts.jpg", allergens: ["Gluten", "Süt", "Yumurta"], sortOrder: 7 },
      { categoryId: catDessert.id, name: "Elmalı Kramble (Crumble)", description: "Tarçınlı elma ve üst çıtır fırın örtüsü", price: 190, imageUrl: "images/desserts.jpg", allergens: ["Gluten", "Süt"], sortOrder: 8 },
      { categoryId: catDessert.id, name: "Brownie", description: "Bitter çikolata, tereyağı, ceviz ve fındık içi", price: 190, imageUrl: "images/desserts.jpg", allergens: ["Gluten", "Süt", "Yumurta", "Fındık"], sortOrder: 9 },
      { categoryId: catDessert.id, name: "Çok Çikolatalı Kek", description: "Çikolata, espresso kahve, tereyağı ve özel çikolata sosu", price: 180, imageUrl: "images/desserts.jpg", allergens: ["Gluten", "Süt", "Yumurta"], sortOrder: 10 },
      { categoryId: catDessert.id, name: "Dilim Cheesecake", description: "Frambuazlı, çikolatalı, limonlu ve vişneli", price: 120, imageUrl: "images/desserts.jpg", allergens: ["Gluten", "Süt", "Yumurta"], sortOrder: 11 }
    ];

    for (const it of items) {
      await restRef.collection("menuItems").add(it);
    }

    // 3. Masalar
    for (let i = 1; i <= 8; i++) {
      const key = "sk_t" + i + "_" + Math.floor(1000 + Math.random() * 9000);
      await restRef.collection("tables").doc("table-" + i).set({
        label: "Masa " + i,
        isActive: true,
        qrKey: key
      });
    }
    await restRef.collection("tables").doc("table-bahce-1").set({ label: "Bahçe 1", isActive: true, qrKey: "sk_bh1_" + Math.floor(1000 + Math.random() * 9000) });
    await restRef.collection("tables").doc("table-bahce-2").set({ label: "Bahçe 2", isActive: true, qrKey: "sk_bh2_" + Math.floor(1000 + Math.random() * 9000) });
    await restRef.collection("tables").doc("table-teras-1").set({ label: "Teras 1", isActive: true, qrKey: "sk_tr1_" + Math.floor(1000 + Math.random() * 9000) });

    console.log("Sade.C Menüsü ve Masaları Başarıyla Yüklendi!");
  } catch (err) {
    console.error("Menü yükleme hatası:", err);
  }
}

// Start Application
window.addEventListener("DOMContentLoaded", () => {
  initUrlParams();
  listenRestaurantData();
  listenCategories();
  listenMenuItems();
});
