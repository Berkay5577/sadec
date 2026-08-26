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
const menuViewEl = document.getElementById("menuView");
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

// 1. Parse URL Parameters
function initUrlParams() {
  const params = new URLSearchParams(window.location.search);
  
  if (params.get("restId") || params.get("restaurantId") || params.get("restaurant")) {
    currentRestId = params.get("restId") || params.get("restaurantId") || params.get("restaurant");
  }
  
  if (params.get("table") || params.get("tableLabel") || params.get("masa")) {
    const rawTable = params.get("table") || params.get("tableLabel") || params.get("masa");
    currentTableLabel = rawTable.toLowerCase().startsWith("masa") ? rawTable : `Masa ${rawTable}`;
    currentTableId = params.get("tableId") || `table-${rawTable}`;
  } else if (params.get("tableId")) {
    currentTableId = params.get("tableId");
    currentTableLabel = `Masa (${currentTableId})`;
  }

  tableLabelTextEl.textContent = currentTableLabel;
  cartModalTable.textContent = currentTableLabel;
}

// 2. Fetch Restaurant Details & Listen Realtime
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

// 3. Listen Categories Realtime
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

// 4. Listen Menu Items Realtime
function listenMenuItems() {
  db.collection("restaurants").doc(currentRestId).collection("menuItems")
    .onSnapshot(snapshot => {
      menuItems = [];
      snapshot.forEach(doc => {
        menuItems.push({ id: doc.id, ...doc.data() });
      });

      loadingIndicatorEl.style.display = "none";
      renderMenu();
    }, err => {
      loadingIndicatorEl.style.display = "none";
      console.log("Menü öğeleri dinleme hatası:", err.message);
    });
}

// Render Categories Bar
function renderCategories() {
  categoryNavEl.innerHTML = `
    <button class="category-btn ${activeCategory === 'all' ? 'active' : ''}" data-category="all">Tümü</button>
  `;

  categories.forEach(cat => {
    const btn = document.createElement("button");
    btn.className = `category-btn ${activeCategory === cat.id ? 'active' : ''}`;
    btn.dataset.category = cat.id;
    btn.textContent = cat.name;
    btn.addEventListener("click", () => {
      activeCategory = cat.id;
      document.querySelectorAll(".category-btn").forEach(b => b.classList.remove("active"));
      btn.classList.add("active");
      renderMenu();
    });
    categoryNavEl.appendChild(btn);
  });

  categoryNavEl.querySelector('[data-category="all"]').addEventListener("click", () => {
    activeCategory = "all";
    document.querySelectorAll(".category-btn").forEach(b => b.classList.remove("active"));
    categoryNavEl.querySelector('[data-category="all"]').classList.add("active");
    renderMenu();
  });
}

// Render Menu Items
function renderMenu() {
  menuItemsContainerEl.innerHTML = "";

  let filteredItems = menuItems.filter(item => {
    if (item.isAvailable === false) return false;
    if (activeCategory !== "all" && item.categoryId !== activeCategory) return false;
    
    if (searchQuery.trim() !== "") {
      const q = searchQuery.toLowerCase();
      const matchName = item.name && item.name.toLowerCase().includes(q);
      const matchDesc = item.description && item.description.toLowerCase().includes(q);
      if (!matchName && !matchDesc) return false;
    }
    return true;
  });

  if (filteredItems.length === 0) {
    menuItemsContainerEl.innerHTML = `
      <div class="empty-state">
        <p>Bu kategoride veya aramada ürün bulunamadı.</p>
      </div>
    `;
    return;
  }

  if (activeCategory === "all" && categories.length > 0 && searchQuery.trim() === "") {
    categories.forEach(cat => {
      const catItems = filteredItems.filter(i => i.categoryId === cat.id);
      if (catItems.length > 0) {
        const catHeader = document.createElement("h3");
        catHeader.className = "section-title";
        catHeader.textContent = cat.name;
        menuItemsContainerEl.appendChild(catHeader);

        catItems.forEach(item => {
          menuItemsContainerEl.appendChild(createProductCard(item));
        });
      }
    });

    const uncategorized = filteredItems.filter(i => !i.categoryId || !categories.some(c => c.id === i.categoryId));
    if (uncategorized.length > 0) {
      const uncatHeader = document.createElement("h3");
      uncatHeader.className = "section-title";
      uncatHeader.textContent = "Diğer Lezzetler";
      menuItemsContainerEl.appendChild(uncatHeader);
      uncategorized.forEach(item => {
        menuItemsContainerEl.appendChild(createProductCard(item));
      });
    }
  } else {
    filteredItems.forEach(item => {
      menuItemsContainerEl.appendChild(createProductCard(item));
    });
  }
}

// Create Product Card DOM Element
function createProductCard(item) {
  const card = document.createElement("div");
  card.className = "product-card";
  
  const imgUrl = item.imageUrl || "images/hot_coffee.jpg";
  
  let allergensHtml = "";
  if (item.allergens && Array.isArray(item.allergens) && item.allergens.length > 0) {
    allergensHtml = `<div class="allergens-list">` + 
      item.allergens.slice(0, 3).map(a => `<span class="allergen-tag">${a}</span>`).join("") + 
      `</div>`;
  }

  card.innerHTML = `
    <div class="product-info">
      <div>
        <h4 class="product-title">${item.name}</h4>
        <p class="product-desc">${item.description || ''}</p>
        ${allergensHtml}
      </div>
      <div class="product-bottom">
        <span class="product-price">${formatCurrency(item.price || 0)}</span>
      </div>
    </div>
    <div class="product-image-container">
      <img class="product-image" src="${imgUrl}" alt="${item.name}" loading="lazy" onerror="this.src='images/hot_coffee.jpg'">
      <button class="add-btn-badge" title="Sepete Ekle">+</button>
    </div>
  `;

  card.addEventListener("click", () => openProductModal(item));
  return card;
}

// Open Product Detail Modal
function openProductModal(item) {
  selectedProduct = item;
  currentModalQty = 1;
  modalQtyNum.textContent = "1";
  modalProductNote.value = "";

  modalProductTitle.textContent = item.name;
  modalProductDesc.textContent = item.description || "Özenle hazırlanan taze Sade C lezzeti.";
  modalProductPrice.textContent = formatCurrency(item.price || 0);
  modalBtnTotal.textContent = formatCurrency(item.price || 0);
  modalProductImg.src = item.imageUrl || "images/hot_coffee.jpg";

  if (item.allergens && item.allergens.length > 0) {
    modalAllergens.innerHTML = item.allergens.map(a => `<span class="allergen-tag">⚠️ ${a}</span>`).join("");
    modalAllergens.style.display = "flex";
  } else {
    modalAllergens.style.display = "none";
  }

  productModalOverlay.classList.add("active");
}

function updateModalTotal() {
  if (!selectedProduct) return;
  const total = (selectedProduct.price || 0) * currentModalQty;
  modalBtnTotal.textContent = formatCurrency(total);
}

// Modal Quantity Controls
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

// Add to Cart from Modal
btnConfirmAddToCart.addEventListener("click", () => {
  if (!selectedProduct) return;

  const note = modalProductNote.value.trim();
  const existingIdx = cart.findIndex(c => c.menuItemId === selectedProduct.id && c.note === note);

  if (existingIdx > -1) {
    cart[existingIdx].quantity += currentModalQty;
  } else {
    cart.push({
      menuItemId: selectedProduct.id,
      name: selectedProduct.name,
      unitPrice: Number(selectedProduct.price || 0),
      quantity: currentModalQty,
      note: note,
      imageUrl: selectedProduct.imageUrl || ""
    });
  }

  productModalOverlay.classList.remove("active");
  updateCartUI();
});

// Update Floating Cart Bar & Modal
function updateCartUI() {
  const totalCount = cart.reduce((sum, item) => sum + item.quantity, 0);
  const totalPrice = cart.reduce((sum, item) => sum + (item.unitPrice * item.quantity), 0);

  if (totalCount > 0) {
    cartFloatingBarEl.style.display = "flex";
    cartBarCountEl.textContent = totalCount;
    cartBarTotalEl.textContent = formatCurrency(totalPrice);
  } else {
    cartFloatingBarEl.style.display = "none";
    cartModalOverlay.classList.remove("active");
  }

  // Update Cart Modal List
  cartModalItems.innerHTML = "";
  cart.forEach((item, index) => {
    const row = document.createElement("div");
    row.className = "cart-item-row";
    row.innerHTML = `
      <div class="cart-item-info">
        <h4>${item.name}</h4>
        <span>${formatCurrency(item.unitPrice)} x ${item.quantity} = <b>${formatCurrency(item.unitPrice * item.quantity)}</b></span>
        ${item.note ? `<div class="cart-item-note">Not: ${item.note}</div>` : ''}
      </div>
      <div class="qty-controls">
        <button class="qty-btn" onclick="changeCartQty(${index}, -1)">-</button>
        <span class="qty-number">${item.quantity}</span>
        <button class="qty-btn" onclick="changeCartQty(${index}, 1)">+</button>
      </div>
    `;
    cartModalItems.appendChild(row);
  });

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

// 5. Submit Order to Firestore
btnSubmitOrder.addEventListener("click", async () => {
  if (cart.length === 0) return;

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

// 6. Live Order Tracking
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
      <span>${i.quantity}x ${i.name}</span>
      <span>${formatCurrency(i.unitPrice * i.quantity)}</span>
    </div>
  `).join("");

  if (orderTrackingUnsubscribe) orderTrackingUnsubscribe();

  orderTrackingUnsubscribe = db.collection("restaurants").doc(currentRestId).collection("orders").doc(orderId)
    .onSnapshot(doc => {
      if (doc.exists) {
        const order = doc.data();
        updateTrackingStatusUI(order.status);
      }
    });
}

function updateTrackingStatusUI(status) {
  trackStatusBadgeEl.className = "status-badge-lg";
  [stepPending, stepPreparing, stepReady, stepDelivered].forEach(s => s.classList.remove("active"));

  if (status === "pending") {
    trackStatusBadgeEl.classList.add("status-pending");
    trackStatusBadgeEl.textContent = "🕒 Siparişiniz Alındı";
    stepPending.classList.add("active");
  } else if (status === "preparing") {
    trackStatusBadgeEl.classList.add("status-preparing");
    trackStatusBadgeEl.textContent = "👨‍🍳 Hazırlanıyor";
    stepPending.classList.add("active");
    stepPreparing.classList.add("active");
  } else if (status === "ready") {
    trackStatusBadgeEl.classList.add("status-ready");
    trackStatusBadgeEl.textContent = "🍽️ Hazır! Masanıza Geliyor";
    stepPending.classList.add("active");
    stepPreparing.classList.add("active");
    stepReady.classList.add("active");
  } else if (status === "delivered") {
    trackStatusBadgeEl.classList.add("status-delivered");
    trackStatusBadgeEl.textContent = "✅ Teslim Edildi • Afiyet Olsun";
    stepPending.classList.add("active");
    stepPreparing.classList.add("active");
    stepReady.classList.add("active");
    stepDelivered.classList.add("active");
  } else if (status === "cancelled") {
    trackStatusBadgeEl.classList.add("status-cancelled");
    trackStatusBadgeEl.textContent = "❌ İptal Edildi";
  }
}

btnNewOrderEl.addEventListener("click", () => {
  trackingViewEl.style.display = "none";
  menuViewEl.style.display = "block";
});

searchInputEl.addEventListener("input", (e) => {
  searchQuery = e.target.value;
  renderMenu();
});

// Seed Sade C Real Menu Data
function renderSampleDataPrompt() {
  menuItemsContainerEl.innerHTML = `
    <div style="text-align: center; padding: 30px 16px; background: #FFF8E1; border-radius: 16px; border: 1px dashed #D4A96A; margin: 20px 0;">
      <img src="logo.png" alt="Sade C Logo" style="width: 70px; margin-bottom: 12px;">
      <h3 style="color: #2C1A14; margin-bottom: 8px;">☕ Sade.C Kahve Menüsü</h3>
      <p style="color: #64748b; font-size: 0.9rem; margin-bottom: 16px;">
        Veritabanına Gerze Sade.C Kahve'nin tüm sıcak/soğuk kahveleri, tatlıları ve sandviçlerini yüklemek için tıklayın.
      </p>
      <button class="btn-primary" id="btnSeedDemo" style="max-width: 280px; margin: 0 auto; background: #2C1A14; border: 1px solid #D4A96A;">
        Sade.C Menüsünü Yükle ☕✨
      </button>
    </div>
  `;

  document.getElementById("btnSeedDemo").addEventListener("click", seedSampleData);
}

async function seedSampleData() {
  const seedBtn = document.getElementById("btnSeedDemo");
  if (seedBtn) {
    seedBtn.disabled = true;
    seedBtn.textContent = "Menü Yükleniyor...";
  }

  const restRef = db.collection("restaurants").doc(currentRestId);
  await restRef.set({
    name: "Sade.C Kahve Gerze",
    slug: currentRestId,
    phone: "0555 123 45 67",
    address: "Gerze / Sinop",
    instagram: "@sadeckahve",
    logoUrl: "logo.png",
    createdAt: firebase.firestore.FieldValue.serverTimestamp()
  }, { merge: true });

  // 1. Kategoriler
  const catHot = await restRef.collection("categories").add({ name: "Sıcak Kahve & İçecekler", sortOrder: 1 });
  const catCold = await restRef.collection("categories").add({ name: "Soğuk İçecekler & Kahveler", sortOrder: 2 });
  const catSpecial = await restRef.collection("categories").add({ name: "Spesiyeller & Sandviçler", sortOrder: 3 });
  const catSnack = await restRef.collection("categories").add({ name: "Atıştırmalıklar & Tostlar", sortOrder: 4 });
  const catDessert = await restRef.collection("categories").add({ name: "Tatlılar & Pastalar", sortOrder: 5 });

  // 2. Ürünler
  const sadeCProducts = [
    // --- SICAK KAHVE & İÇECEKLER ---
    { categoryId: catHot.id, name: "Espresso", description: "30ml taze çekilmiş espresso.", price: 100, imageUrl: "images/hot_coffee.jpg", allergens: [], isAvailable: true, sortOrder: 1 },
    { categoryId: catHot.id, name: "Double Espresso", description: "60ml yoğun espresso.", price: 120, imageUrl: "images/hot_coffee.jpg", allergens: [], isAvailable: true, sortOrder: 2 },
    { categoryId: catHot.id, name: "Double Shot Americano", description: "150ml sıcak su ve 60ml espresso.", price: 140, imageUrl: "images/hot_coffee.jpg", allergens: [], isAvailable: true, sortOrder: 3 },
    { categoryId: catHot.id, name: "Caffe Latte", description: "30ml espresso, 150ml taze sıcak süt.", price: 170, imageUrl: "images/hot_coffee.jpg", allergens: ["Süt"], isAvailable: true, sortOrder: 4 },
    { categoryId: catHot.id, name: "Cappuccino", description: "30ml espresso, 150ml sıcak süt, süt köpüğü ve çikolata tozu.", price: 170, imageUrl: "images/hot_coffee.jpg", allergens: ["Süt"], isAvailable: true, sortOrder: 5 },
    { categoryId: catHot.id, name: "Espresso Macchiato", description: "30ml espresso ve bir kaşık kadifemsi süt köpüğü.", price: 150, imageUrl: "images/hot_coffee.jpg", allergens: ["Süt"], isAvailable: true, sortOrder: 6 },
    { categoryId: catHot.id, name: "Caramel Macchiato", description: "30ml espresso, 180ml süt ve 30ml karamel sos.", price: 160, imageUrl: "images/hot_coffee.jpg", allergens: ["Süt"], isAvailable: true, sortOrder: 7 },
    { categoryId: catHot.id, name: "Mocha", description: "30ml espresso, 20gr çikolata, 130ml süt ve süt kreması.", price: 190, imageUrl: "images/hot_coffee.jpg", allergens: ["Süt"], isAvailable: true, sortOrder: 8 },
    { categoryId: catHot.id, name: "White Chocolate Mocha", description: "30ml espresso, 20gr beyaz çikolata, 130ml süt ve süt kreması.", price: 190, imageUrl: "images/hot_coffee.jpg", allergens: ["Süt"], isAvailable: true, sortOrder: 9 },
    { categoryId: catHot.id, name: "Cortado", description: "60ml espresso, 60ml süt ve kreması.", price: 160, imageUrl: "images/hot_coffee.jpg", allergens: ["Süt"], isAvailable: true, sortOrder: 10 },
    { categoryId: catHot.id, name: "Flat White", description: "60ml double espresso, 120ml sıcak süt.", price: 170, imageUrl: "images/hot_coffee.jpg", allergens: ["Süt"], isAvailable: true, sortOrder: 11 },
    { categoryId: catHot.id, name: "Black Eye", description: "180ml filtre kahve ve 60ml espresso.", price: 160, imageUrl: "images/hot_coffee.jpg", allergens: [], isAvailable: true, sortOrder: 12 },
    { categoryId: catHot.id, name: "Filtre Kahve V60", description: "Kolombiya, Sumatra, Guatemala bölgelerinden özel çekirdekler ile elle demlenir.", price: 180, imageUrl: "images/scene_pour.jpg", allergens: [], isAvailable: true, sortOrder: 13 },
    { categoryId: catHot.id, name: "Filtre Kahve Makina", description: "Özel Kolombiya kahvesi ile taze demlenir.", price: 120, imageUrl: "images/hot_coffee.jpg", allergens: [], isAvailable: true, sortOrder: 14 },
    { categoryId: catHot.id, name: "Siyah Çay", description: "Taze demlenmiş Rize çayı.", price: 30, imageUrl: "images/hot_coffee.jpg", allergens: [], isAvailable: true, sortOrder: 15 },
    { categoryId: catHot.id, name: "Bitki Çayları", description: "Adaçayı, Hibiscus, Papatya, Matcha vb. seçenekler.", price: 150, imageUrl: "images/hot_coffee.jpg", allergens: [], isAvailable: true, sortOrder: 16 },
    { categoryId: catHot.id, name: "Türk Kahvesi", description: "Çifte kavrulmuş lokum ve su ile geleneksel sunum.", price: 75, imageUrl: "images/hot_coffee.jpg", allergens: [], isAvailable: true, sortOrder: 17 },
    { categoryId: catHot.id, name: "Affogato", description: "İtalyan vanilyalı dondurma ve 60ml taze espresso.", price: 250, imageUrl: "images/hot_coffee.jpg", allergens: ["Süt"], isAvailable: true, sortOrder: 18 },
    { categoryId: catHot.id, name: "Sıcak Çikolata", description: "180ml sıcak süt ve eritilmiş Belçika çikolatası.", price: 180, imageUrl: "images/hot_coffee.jpg", allergens: ["Süt"], isAvailable: true, sortOrder: 19 },
    { categoryId: catHot.id, name: "Beyaz Sıcak Çikolata", description: "180ml sıcak süt ve beyaz çikolata.", price: 180, imageUrl: "images/hot_coffee.jpg", allergens: ["Süt"], isAvailable: true, sortOrder: 20 },
    { categoryId: catHot.id, name: "Pembe Ruby Sıcak Çikolata", description: "180ml sıcak süt ve özel meyvemsi ruby çikolata.", price: 180, imageUrl: "images/hot_coffee.jpg", allergens: ["Süt"], isAvailable: true, sortOrder: 21 },

    // --- SOĞUK İÇECEKLER & KAHVELER ---
    { categoryId: catCold.id, name: "Ice Americano", description: "150ml soğuk su, 60ml espresso ve bol buz.", price: 150, imageUrl: "images/cold_drinks.jpg", allergens: [], isAvailable: true, sortOrder: 1 },
    { categoryId: catCold.id, name: "Ice Latte", description: "130ml soğuk süt, 30ml espresso ve buz.", price: 180, imageUrl: "images/cold_drinks.jpg", allergens: ["Süt"], isAvailable: true, sortOrder: 2 },
    { categoryId: catCold.id, name: "Ice White Chocolate Mocha", description: "30ml espresso, 20gr beyaz çikolata, 130ml soğuk süt, süt kreması ve buz.", price: 200, imageUrl: "images/cold_drinks.jpg", allergens: ["Süt"], isAvailable: true, sortOrder: 3 },
    { categoryId: catCold.id, name: "Ice Mocha", description: "30ml espresso, 20gr çikolata, 130ml soğuk süt, süt kreması ve buz.", price: 200, imageUrl: "images/cold_drinks.jpg", allergens: ["Süt"], isAvailable: true, sortOrder: 4 },
    { categoryId: catCold.id, name: "Ice Caramel Macchiato", description: "30ml espresso, 180ml soğuk süt, 30ml karamel sos ve buz.", price: 190, imageUrl: "images/cold_drinks.jpg", allergens: ["Süt"], isAvailable: true, sortOrder: 5 },
    { categoryId: catCold.id, name: "Frapeler", description: "Çilekli, Muzlu, Kakaolu, Karpuzlu, Yeşil Elmalı, Mangolu vb.", price: 180, imageUrl: "images/cold_drinks.jpg", allergens: ["Süt"], isAvailable: true, sortOrder: 6 },
    { categoryId: catCold.id, name: "Cold Brew", description: "16 saat soğuk demlenmiş özel Kolombiya kahvesi.", price: 180, imageUrl: "images/cold_drinks.jpg", allergens: [], isAvailable: true, sortOrder: 7 },
    { categoryId: catCold.id, name: "Ev Yapımı Limonata", description: "200ml taze sıkılmış limon suyu, nane ve buz.", price: 160, imageUrl: "images/cold_drinks.jpg", allergens: [], isAvailable: true, sortOrder: 8 },
    { categoryId: catCold.id, name: "Ev Yapımı Erik Suyu", description: "200ml ev yapımı geleneksel erik suyu ve buz.", price: 180, imageUrl: "images/cold_drinks.jpg", allergens: [], isAvailable: true, sortOrder: 9 },
    { categoryId: catCold.id, name: "Ice Hibiscus Çayı", description: "Taze demlenmiş soğuk hibiscus çayı ve buz.", price: 200, imageUrl: "images/cold_drinks.jpg", allergens: [], isAvailable: true, sortOrder: 10 },
    { categoryId: catCold.id, name: "Muzlu Milkshake", description: "200ml dondurmalı muzlu ferahlatıcı milkshake.", price: 200, imageUrl: "images/cold_drinks.jpg", allergens: ["Süt"], isAvailable: true, sortOrder: 11 },
    { categoryId: catCold.id, name: "Mangolu Milkshake", description: "200ml dondurmalı egzotik mango milkshake.", price: 200, imageUrl: "images/cold_drinks.jpg", allergens: ["Süt"], isAvailable: true, sortOrder: 12 },
    { categoryId: catCold.id, name: "Çilekli Milkshake", description: "200ml taze çilek aromalı milkshake.", price: 200, imageUrl: "images/cold_drinks.jpg", allergens: ["Süt"], isAvailable: true, sortOrder: 13 },
    { categoryId: catCold.id, name: "Çikolatalı Milkshake", description: "200ml yoğun çikolatalı milkshake.", price: 200, imageUrl: "images/cold_drinks.jpg", allergens: ["Süt"], isAvailable: true, sortOrder: 14 },
    { categoryId: catCold.id, name: "Soda Limon", description: "Maden suyu ve taze limon dilimi.", price: 70, imageUrl: "images/cold_drinks.jpg", allergens: [], isAvailable: true, sortOrder: 15 },
    { categoryId: catCold.id, name: "Coca Cola", description: "330ml Kutu.", price: 80, imageUrl: "images/cold_drinks.jpg", allergens: [], isAvailable: true, sortOrder: 16 },
    { categoryId: catCold.id, name: "Fanta", description: "330ml Kutu.", price: 80, imageUrl: "images/cold_drinks.jpg", allergens: [], isAvailable: true, sortOrder: 17 },
    { categoryId: catCold.id, name: "Sprite", description: "330ml Kutu.", price: 80, imageUrl: "images/cold_drinks.jpg", allergens: [], isAvailable: true, sortOrder: 18 },
    { categoryId: catCold.id, name: "Bardak Su", description: "Doğal kaynak suyu.", price: 30, imageUrl: "images/cold_drinks.jpg", allergens: [], isAvailable: true, sortOrder: 19 },

    // --- SPESİYELLER & SANDVİÇLER ---
    { categoryId: catSpecial.id, name: "Köfte Sandviç", description: "Cibata ekmeği, çıtır dışı yumuşak içi, lezzetli köfte, mozzarella peyniri, karamelize soğan, közlenmiş kapya biber ve özel soslar.", price: 260, imageUrl: "images/promo_sandwich.jpg", allergens: ["Gluten", "Süt"], isAvailable: true, sortOrder: 1 },
    { categoryId: catSpecial.id, name: "Tavuk Sandviç", description: "Cibata ekmeği, çıtır dışı yumuşak içi, özel pişmiş ızgara tavuk, taze yeşillik ve özel gurme soslar.", price: 260, imageUrl: "images/promo_sandwich.jpg", allergens: ["Gluten", "Süt"], isAvailable: true, sortOrder: 2 },

    // --- ATIŞTIRMALIKLAR & TOSTLAR ---
    { categoryId: catSnack.id, name: "Üç Peynirli Bazlama Tost", description: "Mozzarella, kolot ve kaşar peyniri dolgulu sıcak bazlama tost.", price: 175, imageUrl: "images/snacks.jpg", allergens: ["Gluten", "Süt"], isAvailable: true, sortOrder: 1 },
    { categoryId: catSnack.id, name: "Sucuklu Kaşarlı Bazlama Tost", description: "Kavrulmuş dana sucuk ve bol kaşar peyniri.", price: 175, imageUrl: "images/snacks.jpg", allergens: ["Gluten", "Süt"], isAvailable: true, sortOrder: 2 },
    { categoryId: catSnack.id, name: "Mücver (Dip Soslu)", description: "Kabak, havuç, soğan, dereotu, maydanoz, baharat ve özel yoğurtlu dip sos.", price: 150, imageUrl: "images/snacks.jpg", allergens: ["Gluten", "Yumurta"], isAvailable: true, sortOrder: 3 },
    { categoryId: catSnack.id, name: "Ispanaklı Börek", description: "Ev yapımı çıtır yufka ve taze yerli ıspanak.", price: 60, imageUrl: "images/snacks.jpg", allergens: ["Gluten"], isAvailable: true, sortOrder: 4 },
    { categoryId: catSnack.id, name: "Peynirli Börek", description: "Lor peyniri, beyaz peynir ve Antep peyniri karışımı.", price: 60, imageUrl: "images/snacks.jpg", allergens: ["Gluten", "Süt"], isAvailable: true, sortOrder: 5 },
    { categoryId: catSnack.id, name: "Patatesli Börek", description: "Ev yapımı yufka, soğan, patates ve baharatlar.", price: 60, imageUrl: "images/snacks.jpg", allergens: ["Gluten"], isAvailable: true, sortOrder: 6 },
    { categoryId: catSnack.id, name: "Dereotlu Poğaça", description: "Dereotu, maydanoz, havuç, peynir — 110gr doyurucu lezzet.", price: 50, imageUrl: "images/snacks.jpg", allergens: ["Gluten", "Süt", "Yumurta"], isAvailable: true, sortOrder: 7 },
    { categoryId: catSnack.id, name: "Yumurtalı Peynirli Ekmek", description: "Ezine peyniri, yumurta, maydanoz, kekik fırınlanmış ekmek (25 dk hazırlanır).", price: 80, imageUrl: "images/snacks.jpg", allergens: ["Gluten", "Süt", "Yumurta"], isAvailable: true, sortOrder: 8 },

    // --- TATLILAR & PASTALAR ---
    { categoryId: catDessert.id, name: "San Sebastian Cheesecake", description: "Fırından taptaze, akışkan kıvamlı ve sıcak Belçika çikolatası sosu eşliğinde servis edilir.", price: 220, imageUrl: "images/promo_cheesecake.jpg", allergens: ["Süt", "Yumurta"], isAvailable: true, sortOrder: 1 },
    { categoryId: catDessert.id, name: "Amerikan Creamy Nemli Kek", description: "Bol kakaolu yumuşak kek ve özel kakaolu kreması ile.", price: 180, imageUrl: "images/desserts.jpg", allergens: ["Gluten", "Süt", "Yumurta"], isAvailable: true, sortOrder: 2 },
    { categoryId: catDessert.id, name: "Tres Leches (Trileçe)", description: "Mascarpone ve krema ile örtülmüş süt reçelli geleneksel kek.", price: 200, imageUrl: "images/desserts.jpg", allergens: ["Gluten", "Süt", "Yumurta"], isAvailable: true, sortOrder: 3 },
    { categoryId: catDessert.id, name: "Cup Cakes", description: "Limonlu, çikolatalı ve vanilyalı cupcake seçenekleri.", price: 60, imageUrl: "images/desserts.jpg", allergens: ["Gluten", "Süt", "Yumurta"], isAvailable: true, sortOrder: 4 },
    { categoryId: catDessert.id, name: "Çilekli Kap Pasta", description: "Taze çilekler, pandispanya ve hafif vanilyalı pasta kreması.", price: 200, imageUrl: "images/desserts.jpg", allergens: ["Gluten", "Süt", "Yumurta"], isAvailable: true, sortOrder: 5 },
    { categoryId: catDessert.id, name: "Supangle", description: "Hakiki çikolata pralin, süt ve bisküvi tabanı.", price: 190, imageUrl: "images/desserts.jpg", allergens: ["Süt"], isAvailable: true, sortOrder: 6 },
    { categoryId: catDessert.id, name: "Tiramisu", description: "Mascarpone ve krema ile hazırlanmış orijinal İtalyan harcı ve espresso ile ıslatılmış kedi dilleri.", price: 200, imageUrl: "images/desserts.jpg", allergens: ["Gluten", "Süt", "Yumurta"], isAvailable: true, sortOrder: 7 },
    { categoryId: catDessert.id, name: "Elmalı Kramble (Crumble)", description: "Tarçınlı elma ve üst çıtır fırın örtüsü ile sunulan sıcak tatlı.", price: 190, imageUrl: "images/desserts.jpg", allergens: ["Gluten", "Süt"], isAvailable: true, sortOrder: 8 },
    { categoryId: catDessert.id, name: "Brownie", description: "Bitter çikolata, tereyağı, un, ceviz ve fındık parçacıkları.", price: 190, imageUrl: "images/desserts.jpg", allergens: ["Gluten", "Süt", "Yumurta", "Fındık"], isAvailable: true, sortOrder: 9 },
    { categoryId: catDessert.id, name: "Çok Çikolatalı Kek", description: "Çikolata, espresso kahve, tereyağı ve özel çikolata sosu ile.", price: 180, imageUrl: "images/desserts.jpg", allergens: ["Gluten", "Süt", "Yumurta"], isAvailable: true, sortOrder: 10 },
    { categoryId: catDessert.id, name: "Dilim Cheesecake", description: "Frambuazlı, çikolatalı, limonlu veya vişneli seçenekleriyle.", price: 120, imageUrl: "images/desserts.jpg", allergens: ["Gluten", "Süt", "Yumurta"], isAvailable: true, sortOrder: 11 }
  ];

  for (const p of sadeCProducts) {
    await restRef.collection("menuItems").add(p);
  }

  // Masalar (1'den 10'a kadar ve Bahçe masaları)
  for (let i = 1; i <= 8; i++) {
    await restRef.collection("tables").doc(`table-${i}`).set({ label: `Masa ${i}`, isActive: true });
  }
  await restRef.collection("tables").doc("table-bahce-1").set({ label: "Bahçe 1", isActive: true });
  await restRef.collection("tables").doc("table-bahce-2").set({ label: "Bahçe 2", isActive: true });
  await restRef.collection("tables").doc("table-teras-1").set({ label: "Teras 1", isActive: true });

  listenCategories();
  listenMenuItems();
}

// Initial Boot
initUrlParams();
listenRestaurantData();
listenCategories();
listenMenuItems();
