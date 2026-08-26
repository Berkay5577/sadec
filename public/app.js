// Initialize Firebase
if (typeof firebaseConfig === 'undefined') {
  console.error("firebaseConfig is not defined. Please check firebase-config.js");
} else {
  firebase.initializeApp(firebaseConfig);
}

const db = firebase.firestore();
const auth = firebase.auth();

auth.signInAnonymously().catch(err => {
  console.log("Anonymous auth skipped:", err.message);
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
const tableLabelTextEl = document.getElementById("tableLabelText");
const categoryNavEl = document.getElementById("categoryNav");
const menuItemsContainerEl = document.getElementById("menuItemsContainer");
const loadingIndicatorEl = document.getElementById("loadingIndicator");
const searchInputEl = document.getElementById("searchInput");

const cartFloatingBarEl = document.getElementById("cartFloatingBar");
const cartBarCountEl = document.getElementById("cartBarCount");
const cartBarTotalEl = document.getElementById("cartBarTotal");

const productModalOverlay = document.getElementById("productModalOverlay");
const btnCloseProductModal = document.getElementById("btnCloseProductModal");
const modalImgContainer = document.getElementById("modalImgContainer");
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
const cartCustomerNameInput = document.getElementById("cartCustomerName");

const trackingViewEl = document.getElementById("trackingView");
const trackTableTextEl = document.getElementById("trackTableText");
const trackOrderNoEl = document.getElementById("trackOrderNo");
const trackStatusBadgeEl = document.getElementById("trackStatusBadge");
const trackItemsListEl = document.getElementById("trackItemsList");
const trackTotalAmountEl = document.getElementById("trackTotalAmount");
const btnNewOrderEl = document.getElementById("btnNewOrder");
const trackCustomerNameTextEl = document.getElementById("trackCustomerNameText");
const stepPending = document.getElementById("stepPending");
const stepPreparing = document.getElementById("stepPreparing");
const stepReady = document.getElementById("stepReady");
const stepDelivered = document.getElementById("stepDelivered");

function formatCurrency(amount) {
  return new Intl.NumberFormat('tr-TR', { style: 'currency', currency: 'TRY' }).format(amount);
}

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
        document.title = `${data.name} — Menü`;
      }
    }
  }, err => console.log("Restaurant listen:", err.message));
}

// ========== CATEGORIES (Real-time) ==========
function listenCategories() {
  db.collection("restaurants").doc(currentRestId).collection("categories")
    .orderBy("sortOrder", "asc")
    .onSnapshot(snapshot => {
      categories = [];
      snapshot.forEach(doc => {
        categories.push({ id: doc.id, ...doc.data() });
      });
      renderCategoryTabs();
      renderMenuItems();
    }, err => {
      console.log("Category listen error:", err.message);
      db.collection("restaurants").doc(currentRestId).collection("categories")
        .onSnapshot(snap => {
          categories = snap.docs.map(d => ({ id: d.id, ...d.data() }));
          renderCategoryTabs();
          renderMenuItems();
        });
    });
}

function renderCategoryTabs() {
  if (categories.length === 0) {
    categoryNavEl.style.display = "none";
    return;
  }

  categoryNavEl.style.display = "flex";
  categoryNavEl.innerHTML = `<button class="cat-pill ${activeCategory === 'all' ? 'active' : ''}" data-category="all">Tümü</button>`;

  categories.forEach(cat => {
    const btn = document.createElement("button");
    btn.className = `cat-pill ${activeCategory === cat.id ? 'active' : ''}`;
    btn.dataset.category = cat.id;
    btn.textContent = cat.name;
    btn.addEventListener("click", () => {
      activeCategory = cat.id;
      document.querySelectorAll(".cat-pill").forEach(b => b.classList.remove("active"));
      btn.classList.add("active");
      renderMenuItems();
    });
    categoryNavEl.appendChild(btn);
  });

  categoryNavEl.querySelector('[data-category="all"]').addEventListener("click", (e) => {
    activeCategory = "all";
    document.querySelectorAll(".cat-pill").forEach(b => b.classList.remove("active"));
    e.target.classList.add("active");
    renderMenuItems();
  });
}

// ========== MENU ITEMS (Real-time) ==========
function listenMenuItems() {
  db.collection("restaurants").doc(currentRestId).collection("menuItems")
    .orderBy("sortOrder", "asc")
    .onSnapshot(snapshot => {
      menuItems = [];
      snapshot.forEach(doc => {
        menuItems.push({ id: doc.id, ...doc.data() });
      });
      if (loadingIndicatorEl) loadingIndicatorEl.style.display = "none";
      renderMenuItems();
    }, err => {
      console.log("Menu items error:", err.message);
      db.collection("restaurants").doc(currentRestId).collection("menuItems")
        .onSnapshot(snap => {
          menuItems = snap.docs.map(d => ({ id: d.id, ...d.data() }));
          if (loadingIndicatorEl) loadingIndicatorEl.style.display = "none";
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
        <div style="font-size: 2.2rem; margin-bottom: 8px;">☕</div>
        <p style="font-size: 0.95rem; font-weight: 500;">Henüz ürün bulunmuyor</p>
        <small style="color: #94A39B;">Eklenen lezzetler anlık olarak burada görüntülenecektir.</small>
      </div>
    `;
    return;
  }

  // If "all" is active and categories exist, group by category
  if (activeCategory === "all" && categories.length > 0 && searchQuery.trim() === "") {
    let html = "";
    categories.forEach(cat => {
      const catItems = filtered.filter(i => i.categoryId === cat.id);
      if (catItems.length > 0) {
        html += `<h3 class="category-header-title">${cat.name}</h3>`;
        catItems.forEach(item => {
          html += createMinimalProductCard(item);
        });
      }
    });

    const otherItems = filtered.filter(i => !categories.some(c => c.id === i.categoryId));
    if (otherItems.length > 0) {
      html += `<h3 class="category-header-title">Diğer Lezzetler</h3>`;
      otherItems.forEach(item => {
        html += createMinimalProductCard(item);
      });
    }

    menuItemsContainerEl.innerHTML = html;
  } else {
    menuItemsContainerEl.innerHTML = filtered.map(item => createMinimalProductCard(item)).join("");
  }

  // Click handler to open product customization bottom sheet
  document.querySelectorAll(".minimal-product-card").forEach(card => {
    card.addEventListener("click", () => {
      const itemId = card.dataset.id;
      const product = menuItems.find(i => i.id === itemId);
      if (product) openProductModal(product);
    });
  });
}

function createMinimalProductCard(item) {
  const hasImage = Boolean(item.imageUrl && item.imageUrl.trim() !== "");
  const allergensHtml = (item.allergens && item.allergens.length > 0)
    ? `<div class="minimal-allergens">${item.allergens.map(a => `<span class="allergen-chip">${a}</span>`).join("")}</div>`
    : "";

  return `
    <div class="minimal-product-card" data-id="${item.id}">
      <div class="minimal-product-info">
        <h4 class="minimal-product-title">${item.name}</h4>
        ${item.description ? `<p class="minimal-product-desc">${item.description}</p>` : ''}
        ${allergensHtml}
        <span class="minimal-product-price">${formatCurrency(item.price)}</span>
      </div>
      <div class="minimal-product-media">
        <img src="${hasImage ? item.imageUrl : 'logo.png'}" alt="${item.name}" onerror="this.src='logo.png'">
        <div class="minimal-add-btn">+</div>
      </div>
    </div>
  `;
}

// ========== SEARCH ==========
searchInputEl.addEventListener("input", (e) => {
  searchQuery = e.target.value;
  renderMenuItems();
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

  if (product.imageUrl && product.imageUrl.trim() !== "") {
    modalImgContainer.style.display = "block";
    modalProductImg.src = product.imageUrl;
  } else {
    modalImgContainer.style.display = "none";
  }

  if (product.allergens && product.allergens.length > 0) {
    modalAllergens.innerHTML = product.allergens.map(a => `<span class="allergen-chip">⚠️ ${a}</span>`).join("");
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
    cartModalItems.innerHTML = `<p style="text-align: center; color: #94A39B; padding: 24px 0;">Sepetiniz boş.</p>`;
    btnSubmitOrder.disabled = true;
  } else {
    btnSubmitOrder.disabled = false;
    cartModalItems.innerHTML = cart.map((item, idx) => `
      <div class="cart-item-entry">
        <div>
          <div class="cart-item-title">${item.name}</div>
          <div class="cart-item-price">${formatCurrency(item.unitPrice)}</div>
          ${item.note ? `<div class="cart-item-note-text">"${item.note}"</div>` : ''}
        </div>
        <div class="stepper">
          <button class="step-btn" onclick="changeCartQty(${idx}, -1)">−</button>
          <span class="step-value">${item.quantity}</span>
          <button class="step-btn" onclick="changeCartQty(${idx}, 1)">+</button>
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

  const customerName = cartCustomerNameInput ? cartCustomerNameInput.value.trim() : "";
  if (!customerName) {
    alert("Lütfen siparişin kime getirileceğini belirtmek için adınızı giriniz 👤");
    if (cartCustomerNameInput) cartCustomerNameInput.focus();
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
  if (trackCustomerNameTextEl) trackCustomerNameTextEl.textContent = initialData.customerName || "Misafir";
  trackTotalAmountEl.textContent = formatCurrency(initialData.totalPrice);

  trackItemsListEl.innerHTML = initialData.items.map(i => `
    <div style="display: flex; justify-content: space-between; font-size: 0.88rem; margin-bottom: 4px;">
      <span>${i.quantity}x ${i.name} ${i.note ? `<small style="color: #D97706;">(${i.note})</small>` : ''}</span>
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
    trackStatusBadgeEl.className = "status-pill-lg status-pending";
    trackStatusBadgeEl.textContent = "🕒 Siparişiniz Alındı";
    stepPending.classList.add("active");
  } else if (status === "preparing") {
    trackStatusBadgeEl.className = "status-pill-lg status-preparing";
    trackStatusBadgeEl.textContent = "👨‍🍳 Barista Hazırlıyor";
    stepPending.classList.add("active");
    stepPreparing.classList.add("active");
  } else if (status === "ready") {
    trackStatusBadgeEl.className = "status-pill-lg status-ready";
    trackStatusBadgeEl.textContent = "☕ Siparişiniz Hazır!";
    stepPending.classList.add("active");
    stepPreparing.classList.add("active");
    stepReady.classList.add("active");
  } else if (status === "delivered") {
    trackStatusBadgeEl.className = "status-pill-lg status-delivered";
    trackStatusBadgeEl.textContent = "✅ Masanıza Teslim Edildi";
    stepPending.classList.add("active");
    stepPreparing.classList.add("active");
    stepReady.classList.add("active");
    stepDelivered.classList.add("active");
  } else if (status === "cancelled") {
    trackStatusBadgeEl.className = "status-pill-lg status-cancelled";
    trackStatusBadgeEl.textContent = "❌ Sipariş İptal Edildi";
  }
}

btnNewOrderEl.addEventListener("click", () => {
  trackingViewEl.style.display = "none";
  menuViewEl.style.display = "block";
});

// ========== START ==========
window.addEventListener("DOMContentLoaded", () => {
  initUrlParams();
  listenRestaurantData();
  listenCategories();
  listenMenuItems();
});
