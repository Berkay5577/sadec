// Initialize Firebase
if (typeof firebaseConfig === 'undefined') {
  console.error("firebaseConfig is not defined. Please check firebase-config.js");
} else {
  firebase.initializeApp(firebaseConfig);
}

const db = firebase.firestore();
const auth = firebase.auth();

auth.signInAnonymously().catch(err => {
  console.log("Anonymous sign-in skipped:", err.message);
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
let currentView = "categories"; // 'categories' or 'items'

// Category icon/color mapping for beautiful cards
const categoryMeta = {
  'sıcak': { icon: '☕', gradient: 'linear-gradient(135deg, #1E3A2F 0%, #2D5341 50%, #3a6b56 100%)' },
  'soğuk': { icon: '🧊', gradient: 'linear-gradient(135deg, #1a4a3a 0%, #2D5341 50%, #4a8b6e 100%)' },
  'spesiyal': { icon: '🥪', gradient: 'linear-gradient(135deg, #2D5341 0%, #1E3A2F 50%, #3d7a5f 100%)' },
  'sandviç': { icon: '🥪', gradient: 'linear-gradient(135deg, #2D5341 0%, #1E3A2F 50%, #3d7a5f 100%)' },
  'atıştırmalık': { icon: '🥐', gradient: 'linear-gradient(135deg, #233d32 0%, #3a6b56 50%, #1E3A2F 100%)' },
  'tost': { icon: '🥐', gradient: 'linear-gradient(135deg, #233d32 0%, #3a6b56 50%, #1E3A2F 100%)' },
  'tatlı': { icon: '🍰', gradient: 'linear-gradient(135deg, #3a2e1e 0%, #5a4530 50%, #2D5341 100%)' },
  'pasta': { icon: '🍰', gradient: 'linear-gradient(135deg, #3a2e1e 0%, #5a4530 50%, #2D5341 100%)' },
  'default': { icon: '🍽️', gradient: 'linear-gradient(135deg, #1E3A2F 0%, #2D5341 100%)' }
};

function getCategoryMeta(catName) {
  const lower = catName.toLowerCase();
  for (const [key, val] of Object.entries(categoryMeta)) {
    if (key !== 'default' && lower.includes(key)) return val;
  }
  return categoryMeta['default'];
}

// DOM Elements
const securityBlockViewEl = document.getElementById("securityBlockView");
const menuViewEl = document.getElementById("menuView");
const restNameEl = document.getElementById("restName");
const tableBadgeEl = document.getElementById("tableBadge");
const tableLabelTextEl = document.getElementById("tableLabelText");
const categoryLandingEl = document.getElementById("categoryLanding");
const btnBackToCategoriesEl = document.getElementById("btnBackToCategories");
const categoryNavEl = document.getElementById("categoryNav");
const menuSectionEl = document.getElementById("menuSection");
const menuItemsContainerEl = document.getElementById("menuItemsContainer");
const loadingIndicatorEl = document.getElementById("loadingIndicator");
const searchInputEl = document.getElementById("searchInput");

const cartFloatingBarEl = document.getElementById("cartFloatingBar");
const cartBarCountEl = document.getElementById("cartBarCount");
const cartBarTotalEl = document.getElementById("cartBarTotal");

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

const cartModalOverlay = document.getElementById("cartModalOverlay");
const btnCloseCartModal = document.getElementById("btnCloseCartModal");
const cartModalItems = document.getElementById("cartModalItems");
const cartModalTotalPrice = document.getElementById("cartModalTotalPrice");
const cartModalTable = document.getElementById("cartModalTable");
const cartOrderNote = document.getElementById("cartOrderNote");
const btnSubmitOrder = document.getElementById("btnSubmitOrder");

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

function formatCurrency(amount) {
  return new Intl.NumberFormat('tr-TR', { style: 'currency', currency: 'TRY' }).format(amount);
}

// ========== VIEW MANAGEMENT ==========
function showCategoryLanding() {
  if (categories.length === 0) {
    showMenuItems("all");
    return;
  }
  currentView = "categories";
  categoryLandingEl.style.display = "grid";
  menuSectionEl.style.display = "none";
  categoryNavEl.style.display = "none";
  btnBackToCategoriesEl.style.display = "none";
  window.scrollTo({ top: 0, behavior: 'smooth' });
}

function showMenuItems(categoryId) {
  currentView = "items";
  activeCategory = categoryId || "all";
  categoryLandingEl.style.display = "none";
  menuSectionEl.style.display = "block";
  categoryNavEl.style.display = categories.length > 0 ? "flex" : "none";
  btnBackToCategoriesEl.style.display = categories.length > 0 ? "flex" : "none";
  
  // Update active tab
  document.querySelectorAll(".category-btn").forEach(b => {
    b.classList.toggle("active", b.dataset.category === activeCategory);
  });
  
  renderMenuItems();
  window.scrollTo({ top: 0, behavior: 'smooth' });
}

btnBackToCategoriesEl.addEventListener("click", () => {
  searchInputEl.value = "";
  searchQuery = "";
  showCategoryLanding();
});

// ========== URL PARAMS & SECURITY ==========
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

  const urlKey = params.get("key") || params.get("k") || params.get("token") || params.get("sig");
  const sessionKey = sessionStorage.getItem("sadec_qr_auth_" + currentTableId);

  if (urlKey) {
    clientSecurityKey = urlKey;
    sessionStorage.setItem("sadec_qr_auth_" + currentTableId, urlKey);
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
        if (tableData.qrKey && tableData.qrKey.trim() !== "") {
          if (clientSecurityKey === tableData.qrKey) {
            isQrAuthorized = true;
            showMenuViewMain();
          } else {
            isQrAuthorized = false;
            showSecurityLock();
          }
        } else {
          isQrAuthorized = true;
          showMenuViewMain();
        }
      } else {
        if (clientSecurityKey) {
          isQrAuthorized = true;
          showMenuViewMain();
        } else {
          isQrAuthorized = false;
          showSecurityLock();
        }
      }
    }, err => {
      console.log("Security check error:", err.message);
      showMenuViewMain();
    });
}

function showSecurityLock() {
  if (securityBlockViewEl) securityBlockViewEl.style.display = "flex";
  if (menuViewEl) menuViewEl.style.display = "none";
  if (cartFloatingBarEl) cartFloatingBarEl.style.display = "none";
}

function showMenuViewMain() {
  if (securityBlockViewEl) securityBlockViewEl.style.display = "none";
  if (menuViewEl) menuViewEl.style.display = "block";
}

// ========== RESTAURANT DATA ==========
function listenRestaurantData() {
  db.collection("restaurants").doc(currentRestId).onSnapshot(doc => {
    if (doc.exists) {
      const data = doc.data();
      if (data.name) {
        restNameEl.textContent = data.name;
        document.title = `${data.name} - QR Menü`;
      }
    }
  }, err => console.log("Restaurant listen:", err.message));
}

// ========== CATEGORIES ==========
function listenCategories() {
  db.collection("restaurants").doc(currentRestId).collection("categories")
    .orderBy("sortOrder", "asc")
    .onSnapshot(snapshot => {
      categories = [];
      snapshot.forEach(doc => {
        categories.push({ id: doc.id, ...doc.data() });
      });

      if (categories.length === 0) {
        showMenuItems("all");
      } else {
        renderCategoryLanding();
        renderCategoryTabs();
      }
    }, err => {
      console.log("Category listen error:", err.message);
      db.collection("restaurants").doc(currentRestId).collection("categories")
        .onSnapshot(snap => {
          categories = snap.docs.map(d => ({ id: d.id, ...d.data() }));
          if (categories.length === 0) {
            showMenuItems("all");
          } else {
            renderCategoryLanding();
            renderCategoryTabs();
          }
        });
    });
}

function renderCategoryLanding() {
  if (loadingIndicatorEl) loadingIndicatorEl.style.display = "none";
  
  let html = '';
  categories.forEach((cat, index) => {
    const meta = getCategoryMeta(cat.name);
    const itemCount = menuItems.filter(i => i.categoryId === cat.id && i.isAvailable !== false).length;
    
    html += `
      <div class="category-card" data-category-id="${cat.id}" style="background: ${meta.gradient}; animation-delay: ${index * 0.08}s;">
        <div class="category-card-icon">${meta.icon}</div>
        <div class="category-card-overlay">
          <div class="category-card-name">${cat.name}</div>
          ${itemCount > 0 ? `<div class="category-card-count">${itemCount} ürün</div>` : ''}
        </div>
      </div>
    `;
  });
  
  categoryLandingEl.innerHTML = html;
  
  document.querySelectorAll(".category-card").forEach(card => {
    card.addEventListener("click", () => {
      const catId = card.dataset.categoryId;
      showMenuItems(catId);
    });
  });
}

function renderCategoryTabs() {
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

  categoryNavEl.querySelector('[data-category="all"]').addEventListener("click", (e) => {
    activeCategory = "all";
    document.querySelectorAll(".category-btn").forEach(b => b.classList.remove("active"));
    e.target.classList.add("active");
    renderMenuItems();
  });
}

// ========== MENU ITEMS ==========
function listenMenuItems() {
  db.collection("restaurants").doc(currentRestId).collection("menuItems")
    .orderBy("sortOrder", "asc")
    .onSnapshot(snapshot => {
      menuItems = [];
      snapshot.forEach(doc => {
        menuItems.push({ id: doc.id, ...doc.data() });
      });
      if (loadingIndicatorEl) loadingIndicatorEl.style.display = "none";
      
      if (currentView === "categories") {
        renderCategoryLanding();
      } else {
        renderMenuItems();
      }
    }, err => {
      console.log("Menu listen error, trying without index:", err.message);
      db.collection("restaurants").doc(currentRestId).collection("menuItems")
        .onSnapshot(snap => {
          menuItems = snap.docs.map(d => ({ id: d.id, ...d.data() }));
          if (loadingIndicatorEl) loadingIndicatorEl.style.display = "none";
          if (currentView === "categories") {
            renderCategoryLanding();
          } else {
            renderMenuItems();
          }
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

// ========== SEARCH ==========
searchInputEl.addEventListener("input", (e) => {
  searchQuery = e.target.value;
  if (searchQuery.trim() !== "") {
    activeCategory = "all";
    showMenuItems("all");
  } else if (currentView === "items" && searchQuery.trim() === "") {
    renderMenuItems();
  }
});

// ========== PRODUCT MODAL ==========
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

// ========== CART ==========
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

// ========== SUBMIT ORDER ==========
btnSubmitOrder.addEventListener("click", async () => {
  if (cart.length === 0 || !isQrAuthorized) return;

  const customerNameInput = document.getElementById("cartCustomerName");
  const customerName = customerNameInput ? customerNameInput.value.trim() : "";

  if (!customerName) {
    alert("Lütfen siparişin kime teslim edileceğini belirtmek için adınızı yazınız 👤");
    if (customerNameInput) customerNameInput.focus();
    return;
  }

  btnSubmitOrder.disabled = true;
  btnSubmitOrder.textContent = "Sipariş Gönderiliyor...";

  const totalPrice = cart.reduce((sum, item) => sum + (item.unitPrice * item.quantity), 0);
  const generalNote = cartOrderNote.value.trim();

  const orderData = {
    tableId: currentTableId,
    tableLabel: currentTableLabel,
    customerName: customerName,
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
    console.error("Order error:", error);
    alert("Sipariş iletilirken bir hata oluştu: " + error.message);
  } finally {
    btnSubmitOrder.disabled = false;
    btnSubmitOrder.textContent = "Siparişi Onayla & Gönder 🚀";
  }
});

// ========== ORDER TRACKING ==========
function startOrderTracking(orderId, initialData) {
  currentTrackingOrderId = orderId;
  menuViewEl.style.display = "none";
  trackingViewEl.style.display = "block";
  window.scrollTo({ top: 0, behavior: 'smooth' });

  trackOrderNoEl.textContent = `#${orderId.slice(-5).toUpperCase()}`;
  trackTableTextEl.textContent = initialData.tableLabel;
  const custNameEl = document.getElementById("trackCustomerNameText");
  if (custNameEl) custNameEl.textContent = initialData.customerName || "Misafir";
  trackTotalAmountEl.textContent = formatCurrency(initialData.totalPrice);

  trackItemsListEl.innerHTML = initialData.items.map(i => `
    <div style="display: flex; justify-content: space-between; margin-bottom: 4px;">
      <span>${i.quantity}x ${i.name} ${i.note ? `<small style="color: #d97706;">(${i.note})</small>` : ''}</span>
      <span>${formatCurrency(i.unitPrice * i.quantity)}</span>
    </div>
  `).join("");

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
  showCategoryLanding();
});

// ========== START ==========
window.addEventListener("DOMContentLoaded", () => {
  initUrlParams();
  listenRestaurantData();
  listenCategories();
  listenMenuItems();
});
