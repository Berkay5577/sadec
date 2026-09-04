(function() {
    // ==========================================
    // MODULE 1: Constants & State
    // ==========================================
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
    let manualSaleCart = [];

    // ==========================================
    // MODULE 10: Utilities
    // ==========================================
    function formatPrice(num) {
        if (num === null || num === undefined) return '0,00';
        return num.toLocaleString('tr-TR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
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
        const statuses = {
            'pending': 'Bekliyor 🕒',
            'preparing': 'Hazırlanıyor 👨🍳',
            'ready': 'Hazır ☕',
            'delivered': 'Teslim Edildi ✅',
            'cancelled': 'İptal Edildi ❌'
        };
        return statuses[status] || status;
    }

    function calcItemEffectivePrice(item) {
        if (item.isComplimentary) return 0;
        return Math.max(0, (item.unitPrice * item.quantity) - (item.discountAmount || 0));
    }

    function calcOrderTotal(order) {
        if (!order.items) return 0;
        return order.items.reduce((sum, item) => sum + calcItemEffectivePrice(item), 0);
    }

    function calcOrderRemaining(order) {
        if (!order.items) return 0;
        return order.items.reduce((sum, item) => {
            if (item.isPaid || item.isComplimentary) return sum;
            return sum + calcItemEffectivePrice(item);
        }, 0);
    }

    function showModal(title, bodyHtml, footerHtml) {
        const overlay = document.getElementById('modalOverlay');
        const titleEl = document.getElementById('modalTitle');
        const bodyEl = document.getElementById('modalBody');
        const footerEl = document.getElementById('modalFooter');
        
        if(titleEl) titleEl.innerText = title;
        if(bodyEl) bodyEl.innerHTML = bodyHtml;
        if(footerEl) footerEl.innerHTML = footerHtml;
        if(overlay) overlay.style.display = 'flex';
    }

    function closeModal() {
        const overlay = document.getElementById('modalOverlay');
        if(overlay) overlay.style.display = 'none';
        const bodyEl = document.getElementById('modalBody');
        const footerEl = document.getElementById('modalFooter');
        if(bodyEl) bodyEl.innerHTML = '';
        if(footerEl) footerEl.innerHTML = '';
    }

    function showToast(message) {
        const toast = document.getElementById('toast');
        if (!toast) return;
        toast.innerText = message;
        toast.classList.add('show');
        setTimeout(() => {
            toast.classList.remove('show');
        }, 3000);
    }

    function generateId() {
        return Math.random().toString(36).substring(2, 15) + Math.random().toString(36).substring(2, 15);
    }
    
    window.closeModal = closeModal;
    
    function playNotificationSound() {
        try {
            if (!audioContext) {
                audioContext = new (window.AudioContext || window.webkitAudioContext)();
            }
            if (audioContext.state === 'suspended') {
                audioContext.resume();
            }
            const oscillator = audioContext.createOscillator();
            const gainNode = audioContext.createGain();
            
            oscillator.type = 'sine';
            oscillator.frequency.setValueAtTime(440, audioContext.currentTime);
            oscillator.frequency.exponentialRampToValueAtTime(880, audioContext.currentTime + 0.1);
            
            gainNode.gain.setValueAtTime(0, audioContext.currentTime);
            gainNode.gain.linearRampToValueAtTime(1, audioContext.currentTime + 0.05);
            gainNode.gain.linearRampToValueAtTime(0, audioContext.currentTime + 0.2);
            
            oscillator.connect(gainNode);
            gainNode.connect(audioContext.destination);
            
            oscillator.start(audioContext.currentTime);
            oscillator.stop(audioContext.currentTime + 0.2);
        } catch (e) {
            console.error('Audio playback failed', e);
        }
    }

    // ==========================================
    // MODULE 2: Initialization
    // ==========================================
    function init() {
        const auth = firebase.auth();
        auth.onAuthStateChanged(onAuthStateChanged);
        
        document.getElementById('loginBtn')?.addEventListener('click', handleLogin);
        document.getElementById('logoutBtn')?.addEventListener('click', handleLogout);
        
        document.getElementById('modalClose')?.addEventListener('click', closeModal);
        document.getElementById('manualSaleFab')?.addEventListener('click', window.showManualSaleDialog);
        
        document.getElementById('dashboardPinSubmit')?.addEventListener('click', checkDashboardPin);
        
        document.body.addEventListener('click', () => {
            if (!audioContext) {
                audioContext = new (window.AudioContext || window.webkitAudioContext)();
            }
        }, { once: true });
        
        document.querySelectorAll('.bottom-nav .nav-item[data-page]').forEach(item => {
            item.addEventListener('click', (e) => {
                const page = e.currentTarget.getAttribute('data-page');
                navigateTo(page);
            });
        });
        
        document.querySelectorAll('#page-orders .filter-chips .chip[data-filter]').forEach(chip => {
            chip.addEventListener('click', (e) => {
                document.querySelectorAll('#page-orders .filter-chips .chip').forEach(c => c.classList.remove('active'));
                e.currentTarget.classList.add('active');
                ordersFilter = e.currentTarget.getAttribute('data-filter');
                renderOrders();
            });
        });
        
        document.querySelectorAll('#page-dashboard .time-filter-chips .chip[data-filter]').forEach(chip => {
            chip.addEventListener('click', (e) => {
                document.querySelectorAll('#page-dashboard .time-filter-chips .chip').forEach(c => c.classList.remove('active'));
                e.currentTarget.classList.add('active');
                dashboardFilter = e.currentTarget.getAttribute('data-filter');
                loadDashboardData();
            });
        });
    }

    // ==========================================
    // MODULE 3: Auth
    // ==========================================
    async function handleLogin() {
        const email = document.getElementById('loginEmail').value;
        const password = document.getElementById('loginPassword').value;
        const errorEl = document.getElementById('loginError');
        const loginBtn = document.getElementById('loginBtn');
        
        if (!email || !password) {
            if (errorEl) errorEl.innerText = 'Lütfen email ve şifre giriniz.';
            return;
        }
        
        try {
            if (loginBtn) {
                loginBtn.disabled = true;
                loginBtn.innerText = 'Giriş Yapılıyor...';
            }
            if (errorEl) errorEl.innerText = '';
            
            try {
                await firebase.auth().signInWithEmailAndPassword(email, password);
            } catch (signInErr) {
                // Hesap yoksa otomatik oluştur (Android uygulamasıyla aynı mantık)
                console.log('Sign-in failed, trying to create account...', signInErr.code);
                try {
                    await firebase.auth().createUserWithEmailAndPassword(email, password);
                } catch (signUpErr) {
                    // Her iki deneme de başarısız
                    throw signInErr;
                }
            }
        } catch (error) {
            console.error('Login error', error);
            let errorMsg = 'Giriş başarısız.';
            if (error.code === 'auth/wrong-password' || error.code === 'auth/invalid-credential') {
                errorMsg = 'E-posta veya şifre hatalı.';
            } else if (error.code === 'auth/user-not-found') {
                errorMsg = 'Kullanıcı bulunamadı.';
            } else if (error.code === 'auth/too-many-requests') {
                errorMsg = 'Çok fazla deneme. Lütfen biraz bekleyin.';
            } else if (error.code === 'auth/network-request-failed') {
                errorMsg = 'İnternet bağlantısını kontrol edin.';
            }
            if (errorEl) errorEl.innerText = errorMsg;
            if (loginBtn) {
                loginBtn.disabled = false;
                loginBtn.innerText = 'Giriş Yap';
            }
        }
    }

    async function handleLogout() {
        try {
            await firebase.auth().signOut();
        } catch (error) {
            console.error('Logout error', error);
            showToast('Çıkış yapılamadı');
        }
    }

    function onAuthStateChanged(user) {
        const loginScreen = document.getElementById('loginScreen');
        const appContainer = document.getElementById('appContainer');
        const loginBtn = document.getElementById('loginBtn');
        
        if (user) {
            currentUser = user;
            if(loginScreen) loginScreen.classList.add('hidden');
            if(appContainer) appContainer.classList.remove('hidden');
            
            startRestaurantListener();
            startOrdersListener();
            startTablesListener();
            startMenuListeners();
            
            navigateTo('orders');
        } else {
            currentUser = null;
            if(loginScreen) loginScreen.classList.remove('hidden');
            if(appContainer) appContainer.classList.add('hidden');
            if (loginBtn) {
                loginBtn.disabled = false;
                loginBtn.innerText = 'Giriş Yap';
            }
            
            if (unsubscribeOrders) unsubscribeOrders();
            if (unsubscribeRestaurant) unsubscribeRestaurant();
            if (unsubscribeTables) unsubscribeTables();
            if (unsubscribeCategories) unsubscribeCategories();
            if (unsubscribeMenuItems) unsubscribeMenuItems();
        }
    }
    
    function startRestaurantListener() {
        const db = firebase.firestore();
        unsubscribeRestaurant = db.doc(BASE_PATH).onSnapshot(doc => {
            if (doc.exists) {
                restaurant = doc.data();
            }
        });
    }

    // ==========================================
    // MODULE 4: Router
    // ==========================================
    function navigateTo(page) {
        document.querySelectorAll('.page-content').forEach(el => el.style.display = 'none');
        
        const targetPage = document.getElementById(`page-${page}`);
        if(targetPage) targetPage.style.display = 'block';
        
        document.querySelectorAll('.bottom-nav .nav-item').forEach(item => {
            if (item.getAttribute('data-page') === page) {
                item.classList.add('active');
            } else {
                item.classList.remove('active');
            }
        });
        
        currentPage = page;
        
        if (page === 'orders') {
            renderOrders();
        } else if (page === 'tables') {
            renderTables();
        } else if (page === 'menu') {
            renderCategories();
        } else if (page === 'dashboard') {
            const gate = document.getElementById('dashboardPinGate');
            const content = document.getElementById('dashboardContent');
            const pinInput = document.getElementById('dashboardPinInput');
            const error = document.getElementById('dashboardPinError');
            
            if (!dashboardUnlocked) {
                if (gate) gate.style.display = 'flex';
                if (content) content.style.display = 'none';
                if (pinInput) pinInput.value = '';
                if (error) error.innerText = '';
            } else {
                if (gate) gate.style.display = 'none';
                if (content) content.style.display = 'block';
                loadDashboardData();
            }
        } else if (page === 'settings') {
            renderSettings();
        }
    }

    // ==========================================
    // MODULE 5: Orders
    // ==========================================
    let lastPendingCount = 0;
    
    function startOrdersListener() {
        const db = firebase.firestore();
        unsubscribeOrders = db.collection(`${BASE_PATH}/orders`)
            .where('isArchived', '==', false)
            .orderBy('createdAt', 'desc')
            .onSnapshot(snapshot => {
                allOrders = [];
                let newPendingCount = 0;
                
                snapshot.forEach(doc => {
                    const data = doc.data();
                    data.id = doc.id;
                    allOrders.push(data);
                    
                    if (data.status === 'pending') {
                        newPendingCount++;
                    }
                });
                
                if (newPendingCount > lastPendingCount) {
                    playNotificationSound();
                }
                lastPendingCount = newPendingCount;
                
                if (currentPage === 'orders') renderOrders();
                if (currentPage === 'tables') {
                    computeActiveTables();
                    renderTables();
                }
                if (currentPage === 'dashboard' && dashboardUnlocked && dashboardFilter === 'today') {
                    loadDashboardData();
                }
                
                updateOrderBadges();
            }, error => {
                console.error("Orders listener error:", error);
            });
    }
    
    function updateOrderBadges() {
        const activeOrders = allOrders.filter(o => o.status !== 'delivered' && o.status !== 'cancelled');
        const activeCount = activeOrders.length;
        
        const badge1 = document.getElementById('ordersActiveBadge');
        const badge2 = document.getElementById('navOrdersBadge');
        
        if(badge1) {
            badge1.innerText = activeCount > 0 ? activeCount : '';
            badge1.style.display = activeCount > 0 ? 'inline-block' : 'none';
        }
        if(badge2) {
            badge2.innerText = activeCount > 0 ? activeCount : '';
            badge2.style.display = activeCount > 0 ? 'inline-block' : 'none';
        }
    }

    function renderOrderItems(items) {
        if (!items) return '';
        return items.map(item => `
            <div class="order-item-row ${item.isPaid ? 'paid' : ''} ${item.isComplimentary ? 'complimentary' : ''}">
                <div class="item-qty">${item.quantity}x</div>
                <div class="item-name">${item.name}</div>
                ${item.note ? `<div class="item-note">📝 ${item.note}</div>` : ''}
                <div class="item-price">₺${formatPrice(calcItemEffectivePrice(item))}</div>
            </div>
        `).join('');
    }

    function renderOrders() {
        const container = document.getElementById('ordersList');
        const emptyState = document.getElementById('ordersEmpty');
        if (!container || !emptyState) return;
        
        let filteredOrders = allOrders;
        if (ordersFilter === 'active') {
            filteredOrders = allOrders.filter(o => o.status !== 'delivered' && o.status !== 'cancelled');
        } else if (ordersFilter === 'delivered') {
            filteredOrders = allOrders.filter(o => o.status === 'delivered');
        } else if (ordersFilter === 'cancelled') {
            filteredOrders = allOrders.filter(o => o.status === 'cancelled');
        }
        
        if (filteredOrders.length === 0) {
            container.innerHTML = '';
            emptyState.style.display = 'flex';
        } else {
            emptyState.style.display = 'none';
            container.innerHTML = filteredOrders.map(order => `
                <div class="order-card card status-border-${order.status}" data-order-id="${order.id}">
                    <div class="order-header flex-between mb-8">
                        <div class="flex-col">
                            <span class="font-bold text-lg">📍 ${order.tableLabel || 'Masa ?'}</span>
                            <span class="text-gray text-sm">👤 ${order.customerName || 'Misafir'}</span>
                        </div>
                        <div class="flex-col text-right">
                            <span class="text-gray text-sm">${formatTime(order.createdAt)}</span>
                            <span class="badge status-${order.status}">${statusText(order.status)}</span>
                        </div>
                    </div>
                    <div class="order-items mb-8">
                        ${renderOrderItems(order.items)}
                    </div>
                    ${order.note ? `<div class="order-note bg-light-yellow p-4 rounded mb-8">📝 ${order.note}</div>` : ''}
                    <div class="flex-between mb-8">
                        <span class="font-bold text-lg">Toplam: ₺${formatPrice(calcOrderTotal(order))}</span>
                    </div>
                    ${order.status !== 'delivered' && order.status !== 'cancelled' ? `
                        <div class="order-actions flex gap-8">
                            <button class="btn btn-primary flex-1" onclick="window.deliverOrder('${order.id}')">Teslim Et ✅</button>
                            <button class="btn btn-danger flex-1" onclick="window.showCancelDialog('${order.id}')">İptal ❌</button>
                        </div>
                    ` : ''}
                    ${order.status === 'cancelled' && order.cancelReason ? `<div class="text-danger text-sm mt-8">❌ İptal Nedeni: ${order.cancelReason}</div>` : ''}
                </div>
            `).join('');
        }
    }

    window.deliverOrder = async function(orderId) {
        try {
            const db = firebase.firestore();
            await db.doc(`${BASE_PATH}/orders/${orderId}`).update({
                status: 'delivered',
                updatedAt: firebase.firestore.FieldValue.serverTimestamp()
            });
            showToast('Sipariş teslim edildi.');
        } catch(e) {
            console.error(e);
            showToast('Hata oluştu');
        }
    };

    window.showCancelDialog = function(orderId) {
        const bodyHtml = `
            <div class="cancel-options flex-col gap-8">
                <label class="radio-label"><input type="radio" name="cancelReason" value="Müşteri vazgeçti / ayrıldı" checked> Müşteri vazgeçti / ayrıldı</label>
                <label class="radio-label"><input type="radio" name="cancelReason" value="Ürün tükendi / stok yetersiz"> Ürün tükendi / stok yetersiz</label>
                <label class="radio-label"><input type="radio" name="cancelReason" value="Hatalı / Yanlış sipariş"> Hatalı / Yanlış sipariş</label>
                <label class="radio-label"><input type="radio" name="cancelReason" value="Masa boşaldı / Yanlış masa"> Masa boşaldı / Yanlış masa</label>
                <label class="radio-label">
                    <input type="radio" name="cancelReason" value="Diğer" id="cancelReasonOtherRadio"> Diğer...
                </label>
                <input type="text" id="cancelReasonOtherText" class="input mt-4" placeholder="Nedeni yazın..." style="display:none;">
            </div>
        `;
        const footerHtml = `
            <button class="btn" onclick="window.closeModal()">Vazgeç</button>
            <button class="btn btn-danger" onclick="window.cancelOrder('${orderId}')">İptal Et</button>
        `;
        
        showModal('Siparişi İptal Et', bodyHtml, footerHtml);
        
        setTimeout(() => {
            const radios = document.querySelectorAll('input[name="cancelReason"]');
            const otherText = document.getElementById('cancelReasonOtherText');
            if (otherText) {
                radios.forEach(r => r.addEventListener('change', (e) => {
                    if (e.target.id === 'cancelReasonOtherRadio') {
                        otherText.style.display = 'block';
                        otherText.focus();
                    } else {
                        otherText.style.display = 'none';
                    }
                }));
            }
        }, 100);
    };

    window.cancelOrder = async function(orderId) {
        const checked = document.querySelector('input[name="cancelReason"]:checked');
        let reason = checked ? checked.value : 'Diğer';
        if (reason === 'Diğer') {
            const otherEl = document.getElementById('cancelReasonOtherText');
            reason = otherEl ? otherEl.value || 'Belirtilmedi' : 'Belirtilmedi';
        }
        
        try {
            const db = firebase.firestore();
            await db.doc(`${BASE_PATH}/orders/${orderId}`).update({
                status: 'cancelled',
                cancelReason: reason,
                updatedAt: firebase.firestore.FieldValue.serverTimestamp()
            });
            closeModal();
            showToast('Sipariş iptal edildi.');
        } catch(e) {
            console.error(e);
            showToast('Hata oluştu');
        }
    };

    // ==========================================
    // MODULE 6: Tables & POS
    // ==========================================
    let activeTablesData = [];
    
    function startTablesListener() {
        const db = firebase.firestore();
        unsubscribeTables = db.collection(`${BASE_PATH}/tables`).onSnapshot(snapshot => {
            allTables = [];
            snapshot.forEach(doc => {
                const data = doc.data();
                data.id = doc.id;
                allTables.push(data);
            });
            computeActiveTables();
            if (currentPage === 'tables') renderTables();
        });
    }

    function computeActiveTables() {
        activeTablesData = [];
        let activeTableCount = 0;
        
        const tableOrders = {};
        allOrders.forEach(order => {
            if (order.status !== 'cancelled' && !order.isArchived) {
                if (!tableOrders[order.tableId]) tableOrders[order.tableId] = [];
                tableOrders[order.tableId].push(order);
            }
        });
        
        allTables.forEach(table => {
            const orders = tableOrders[table.id] || [];
            let remaining = 0;
            orders.forEach(o => remaining += calcOrderRemaining(o));
            
            if (remaining > 0.001 || orders.length > 0) {
                activeTablesData.push({
                    table: table,
                    orders: orders,
                    remaining: remaining
                });
                if (remaining > 0.001) activeTableCount++;
            }
        });
        
        const adhocOrders = allOrders.filter(o => !allTables.find(t => t.id === o.tableId) && o.status !== 'cancelled' && !o.isArchived);
        if (adhocOrders.length > 0) {
            const adhocGroups = {};
            adhocOrders.forEach(o => {
                const label = o.tableLabel || 'Bilinmeyen';
                if (!adhocGroups[label]) adhocGroups[label] = [];
                adhocGroups[label].push(o);
            });
            
            Object.keys(adhocGroups).forEach(label => {
                const orders = adhocGroups[label];
                let remaining = 0;
                orders.forEach(o => remaining += calcOrderRemaining(o));
                activeTablesData.push({
                    table: { id: `adhoc-${label}`, label: label },
                    orders: orders,
                    remaining: remaining,
                    isAdhoc: true
                });
                if (remaining > 0.001) activeTableCount++;
            });
        }
        
        const badge = document.getElementById('navTablesBadge');
        if(badge) {
            badge.innerText = activeTableCount > 0 ? activeTableCount : '';
            badge.style.display = activeTableCount > 0 ? 'inline-block' : 'none';
        }
        const summary = document.getElementById('tablesSummary');
        if(summary) summary.innerText = `Aktif Masalar (${activeTableCount})`;
    }

    function renderTables() {
        const container = document.getElementById('tablesGrid');
        const emptyState = document.getElementById('tablesEmpty');
        if (!container || !emptyState) return;
        
        if (activeTablesData.length === 0) {
            container.innerHTML = '';
            emptyState.style.display = 'flex';
            return;
        }
        
        emptyState.style.display = 'none';
        container.innerHTML = activeTablesData.map(data => {
            const tableId = data.table.id;
            const tableItems = [];
            
            data.orders.forEach(order => {
                if (order.items) {
                    order.items.forEach((item, index) => {
                        tableItems.push({
                            orderId: order.id,
                            index: index,
                            item: item,
                            customerName: order.customerName || 'Misafir',
                            key: `${order.id}_${index}`
                        });
                    });
                }
            });
            
            let selectedCount = 0;
            let selectedAmount = 0;
            tableItems.forEach(ti => {
                if (selectedTableItems[ti.key] && !ti.item.isPaid) {
                    selectedCount++;
                    selectedAmount += calcItemEffectivePrice(ti.item);
                }
            });
            
            return `
                <div class="card table-card">
                    <div class="flex-between mb-8 border-b pb-4">
                        <h3 class="font-bold text-xl">${data.table.label}</h3>
                        <div class="flex gap-4">
                            <span class="font-bold text-lg text-primary">₺${formatPrice(data.remaining)}</span>
                            ${!data.isAdhoc ? `<button class="btn btn-sm" onclick="window.showTransferDialog('${tableId}')">🔄</button>` : ''}
                        </div>
                    </div>
                    <div class="table-items-list mb-8">
                        ${tableItems.map(ti => {
                            const isSelected = !!selectedTableItems[ti.key];
                            const isPaid = ti.item.isPaid;
                            const isComplimentary = ti.item.isComplimentary;
                            const price = calcItemEffectivePrice(ti.item);
                            
                            return `
                                <div class="table-item-row flex-between p-4 border-b ${isSelected ? 'bg-light-blue' : ''} ${isPaid ? 'opacity-50' : ''}" 
                                     onclick="!${isPaid} && window.toggleItemSelection('${ti.key}')">
                                    <div class="flex gap-8 align-center">
                                        ${!isPaid ? `
                                            <input type="checkbox" ${isSelected ? 'checked' : ''} 
                                                onclick="event.stopPropagation(); window.toggleItemSelection('${ti.key}')">
                                        ` : '✅'}
                                        <div class="flex-col">
                                            <span>${ti.item.quantity}x ${ti.item.name}</span>
                                            <span class="text-xs text-gray">👤 ${ti.customerName}</span>
                                        </div>
                                    </div>
                                    <div class="flex gap-4 align-center">
                                        ${isComplimentary ? '<span class="badge bg-green">İkram</span>' : ''}
                                        <span class="font-bold">₺${formatPrice(price)}</span>
                                    </div>
                                </div>
                            `;
                        }).join('')}
                    </div>
                    <div class="table-actions">
                        ${selectedCount > 0 ? `
                            <button class="btn btn-primary w-full" onclick="window.preparePayment('${tableId}', 'selected', ${selectedAmount})">
                                Seçilenleri Öde (${selectedCount} Ürün • ₺${formatPrice(selectedAmount)}) 💳
                            </button>
                        ` : `
                            ${data.remaining > 0 ? `
                                <button class="btn btn-primary w-full" onclick="window.preparePayment('${tableId}', 'all', ${data.remaining})">
                                    Masanın Kalanını Kapat (₺${formatPrice(data.remaining)}) ✨
                                </button>
                            ` : `
                                <div class="text-center text-success font-bold">Tümü Ödendi ✅</div>
                            `}
                        `}
                    </div>
                </div>
            `;
        }).join('');
    }

    window.toggleItemSelection = function(key) {
        if (selectedTableItems[key]) {
            delete selectedTableItems[key];
        } else {
            selectedTableItems[key] = true;
        }
        renderTables();
    };

    window.preparePayment = function(tableId, mode, amount) {
        let itemKeys = [];
        const tableData = activeTablesData.find(t => t.table.id === tableId);
        if (!tableData) return;
        
        if (mode === 'selected') {
            itemKeys = Object.keys(selectedTableItems).filter(k => selectedTableItems[k]);
        } else {
            tableData.orders.forEach(order => {
                if (order.items) {
                    order.items.forEach((item, index) => {
                        if (!item.isPaid && !item.isComplimentary) {
                            itemKeys.push(`${order.id}_${index}`);
                        }
                    });
                }
            });
        }
        
        if (itemKeys.length === 0) {
            showToast('Ödenecek ürün bulunamadı.');
            return;
        }
        
        window.showPaymentDialog(tableId, amount, itemKeys);
    };

    let currentSplitState = { total: 0, cash: 0, card: 0, transfer: 0 };
    
    window.showPaymentDialog = function(tableId, amount, itemKeys) {
        const itemKeysStr = JSON.stringify(itemKeys).replace(/"/g, '&quot;');
        currentSplitState = { total: amount, cash: 0, card: 0, transfer: 0 };
        
        const bodyHtml = `
            <div class="text-center mb-8">
                <div class="text-2xl font-bold">₺${formatPrice(amount)}</div>
                <div class="text-gray text-sm">${itemKeys.length} ürün seçili</div>
            </div>
            
            <div class="grid grid-cols-2 gap-4 mb-8">
                <button class="btn bg-green text-white p-4 text-lg" onclick="window.processPayment('cash', '${tableId}', '${itemKeysStr}')">Nakit 💵</button>
                <button class="btn bg-blue text-white p-4 text-lg" onclick="window.processPayment('card', '${tableId}', '${itemKeysStr}')">Kart 💳</button>
                <button class="btn bg-purple text-white p-4 text-lg" onclick="window.processPayment('transfer', '${tableId}', '${itemKeysStr}')">Havale 📲</button>
                <button class="btn bg-yellow p-4 text-lg" onclick="window.processPayment('complimentary', '${tableId}', '${itemKeysStr}')">İkram 🎁</button>
            </div>
            
            <button class="btn w-full mb-4 border" onclick="window.toggleSplitPayment()">Parçalı Ödeme 🔀</button>
            
            <div id="splitPaymentContainer" style="display:none;" class="bg-gray-100 p-4 rounded">
                <div class="flex-col gap-4 mb-4">
                    <div class="flex-between align-center">
                        <label class="w-24">Nakit 💵</label>
                        <input type="number" id="splitCash" class="input flex-1 text-right" step="0.01" value="0" oninput="window.updateSplitDiff()">
                    </div>
                    <div class="flex-between align-center">
                        <label class="w-24">Kart 💳</label>
                        <input type="number" id="splitCard" class="input flex-1 text-right" step="0.01" value="0" oninput="window.updateSplitDiff()">
                    </div>
                    <div class="flex-between align-center">
                        <label class="w-24">Havale 📲</label>
                        <input type="number" id="splitTransfer" class="input flex-1 text-right" step="0.01" value="0" oninput="window.updateSplitDiff()">
                    </div>
                </div>
                
                <div class="flex gap-4 mb-4 text-xs">
                    <button class="btn btn-sm flex-1" onclick="window.splitHalf()">50/50 Böl</button>
                    <button class="btn btn-sm flex-1" onclick="window.fillRemaining('cash')">Kalanı Nakit</button>
                    <button class="btn btn-sm flex-1" onclick="window.fillRemaining('card')">Kalanı Kart</button>
                </div>
                
                <div id="splitDiff" class="text-center font-bold mb-4 p-2 rounded"></div>
                
                <button id="splitSubmitBtn" class="btn btn-primary w-full" disabled onclick="window.submitSplitPayment('${tableId}', '${itemKeysStr}')">Parçalı Ödemeyi Tamamla ✅</button>
            </div>
        `;
        
        showModal('Ödeme Al', bodyHtml, '<button class="btn" onclick="window.closeModal()">İptal</button>');
    };
    
    window.toggleSplitPayment = function() {
        const el = document.getElementById('splitPaymentContainer');
        if (el) {
            el.style.display = el.style.display === 'none' ? 'block' : 'none';
            window.updateSplitDiff();
        }
    };

    window.updateSplitDiff = function() {
        const cash = parseFloat(document.getElementById('splitCash').value) || 0;
        const card = parseFloat(document.getElementById('splitCard').value) || 0;
        const transfer = parseFloat(document.getElementById('splitTransfer').value) || 0;
        const sum = cash + card + transfer;
        const diff = sum - currentSplitState.total;
        
        currentSplitState.cash = cash;
        currentSplitState.card = card;
        currentSplitState.transfer = transfer;
        
        const diffEl = document.getElementById('splitDiff');
        const submitBtn = document.getElementById('splitSubmitBtn');
        if (!diffEl || !submitBtn) return;
        
        if (Math.abs(diff) < 0.01) {
            diffEl.innerHTML = `Tam Tutar ✅`;
            diffEl.className = 'text-center font-bold mb-4 p-2 rounded bg-green text-white';
            submitBtn.disabled = false;
        } else if (diff < 0) {
            diffEl.innerHTML = `Eksik: ₺${formatPrice(Math.abs(diff))} ⚠️`;
            diffEl.className = 'text-center font-bold mb-4 p-2 rounded bg-yellow';
            submitBtn.disabled = true;
        } else {
            diffEl.innerHTML = `Fazla (Para Üstü): ₺${formatPrice(diff)} ℹ️`;
            diffEl.className = 'text-center font-bold mb-4 p-2 rounded bg-blue text-white';
            submitBtn.disabled = false;
        }
    };
    
    window.splitHalf = function() {
        const half = +(currentSplitState.total / 2).toFixed(2);
        document.getElementById('splitCash').value = half;
        document.getElementById('splitCard').value = currentSplitState.total - half;
        document.getElementById('splitTransfer').value = 0;
        window.updateSplitDiff();
    };

    window.fillRemaining = function(target) {
        const cash = parseFloat(document.getElementById('splitCash').value) || 0;
        const card = parseFloat(document.getElementById('splitCard').value) || 0;
        const transfer = parseFloat(document.getElementById('splitTransfer').value) || 0;
        
        let currentSum = 0;
        if(target !== 'cash') currentSum += cash;
        if(target !== 'card') currentSum += card;
        if(target !== 'transfer') currentSum += transfer;
        
        const rem = Math.max(0, currentSplitState.total - currentSum);
        
        if(target === 'cash') document.getElementById('splitCash').value = rem.toFixed(2);
        if(target === 'card') document.getElementById('splitCard').value = rem.toFixed(2);
        if(target === 'transfer') document.getElementById('splitTransfer').value = rem.toFixed(2);
        
        window.updateSplitDiff();
    };

    window.submitSplitPayment = function(tableId, itemKeysStr) {
        const splitAmounts = {
            cash: currentSplitState.cash,
            card: currentSplitState.card,
            transfer: currentSplitState.transfer
        };
        window.processPayment('split', tableId, itemKeysStr, splitAmounts);
    };

    window.processPayment = async function(method, tableId, itemKeysStr, splitAmounts = null) {
        const itemKeys = JSON.parse(itemKeysStr.replace(/&quot;/g, '"'));
        const db = firebase.firestore();
        const batch = db.batch();
        const orderUpdates = {};
        
        itemKeys.forEach(key => {
            const [orderId, indexStr] = key.split('_');
            const index = parseInt(indexStr);
            if (!orderUpdates[orderId]) {
                const order = allOrders.find(o => o.id === orderId);
                if (order) {
                    orderUpdates[orderId] = {
                        ref: db.doc(`${BASE_PATH}/orders/${orderId}`),
                        items: JSON.parse(JSON.stringify(order.items)),
                        order: order
                    };
                }
            }
            if (orderUpdates[orderId]) {
                const item = orderUpdates[orderId].items[index];
                if (!item.isPaid) {
                    if (method === 'complimentary') {
                        item.isComplimentary = true;
                        item.isPaid = true;
                        item.paidAt = Date.now();
                        item.paymentMethod = 'complimentary';
                    } else {
                        item.isPaid = true;
                        item.paidAt = Date.now();
                        item.paymentMethod = method;
                        const price = calcItemEffectivePrice(item);
                        
                        if (method === 'cash') item.cashPaid = price;
                        else if (method === 'card') item.cardPaid = price;
                        else if (method === 'transfer') item.transferPaid = price;
                        else if (method === 'split' && splitAmounts) {
                            item.cashPaid = 0; item.cardPaid = 0; item.transferPaid = 0;
                            const totalPayment = splitAmounts.cash + splitAmounts.card + splitAmounts.transfer;
                            if (totalPayment > 0) {
                                item.cashPaid = +(price * (splitAmounts.cash / totalPayment)).toFixed(2);
                                item.cardPaid = +(price * (splitAmounts.card / totalPayment)).toFixed(2);
                                item.transferPaid = +(price * (splitAmounts.transfer / totalPayment)).toFixed(2);
                            }
                        }
                    }
                }
            }
        });
        
        Object.keys(orderUpdates).forEach(orderId => {
            const updateData = {
                items: orderUpdates[orderId].items,
                updatedAt: firebase.firestore.FieldValue.serverTimestamp()
            };
            
            const allPaid = updateData.items.every(item => item.isPaid || item.isComplimentary);
            if (allPaid && orderUpdates[orderId].order.status !== 'cancelled') {
                updateData.status = 'delivered';
            }
            
            batch.update(orderUpdates[orderId].ref, updateData);
        });
        
        try {
            await batch.commit();
            itemKeys.forEach(k => delete selectedTableItems[k]);
            closeModal();
            showToast('Ödeme başarıyla alındı ✅');
        } catch (e) {
            console.error('Payment error', e);
            showToast('Ödeme alınırken hata oluştu');
        }
    };
    
    window.showTransferDialog = function(fromTableId) {
        const fromTableData = activeTablesData.find(t => t.table.id === fromTableId);
        if (!fromTableData) return;
        
        const otherTables = allTables.filter(t => t.id !== fromTableId);
        
        const bodyHtml = `
            <div class="mb-4">Taşınacak Masa: <strong>${fromTableData.table.label}</strong></div>
            <div class="mb-8">Aktarılacak Masayı Seçin:</div>
            <select id="transferTargetSelect" class="input w-full mb-8">
                ${otherTables.map(t => `<option value="${t.id}">${t.label}</option>`).join('')}
            </select>
        `;
        const footerHtml = `
            <button class="btn" onclick="window.closeModal()">İptal</button>
            <button class="btn btn-primary" onclick="window.processTransfer('${fromTableId}')">Taşı 🔄</button>
        `;
        
        showModal('Masa Taşıma', bodyHtml, footerHtml);
    };
    
    window.processTransfer = async function(fromTableId) {
        const targetSelect = document.getElementById('transferTargetSelect');
        const targetTableId = targetSelect.value;
        const targetTableLabel = targetSelect.options[targetSelect.selectedIndex].text;
        
        const fromTableData = activeTablesData.find(t => t.table.id === fromTableId);
        if (!fromTableData) return;
        
        const db = firebase.firestore();
        const batch = db.batch();
        
        fromTableData.orders.forEach(order => {
            const remaining = calcOrderRemaining(order);
            if (remaining > 0 || order.status === 'pending' || order.status === 'preparing') {
                const ref = db.doc(`${BASE_PATH}/orders/${order.id}`);
                batch.update(ref, {
                    tableId: targetTableId,
                    tableLabel: targetTableLabel,
                    updatedAt: firebase.firestore.FieldValue.serverTimestamp()
                });
            }
        });
        
        try {
            await batch.commit();
            closeModal();
            showToast('Masa başarıyla taşındı.');
        } catch (e) {
            console.error(e);
            showToast('Taşıma sırasında hata oluştu.');
        }
    };

    // ==========================================
    // MODULE 7: Menu Management
    // ==========================================
    function startMenuListeners() {
        const db = firebase.firestore();
        unsubscribeCategories = db.collection(`${BASE_PATH}/categories`).orderBy('sortOrder').onSnapshot(snapshot => {
            allCategories = [];
            snapshot.forEach(doc => {
                const data = doc.data();
                data.id = doc.id;
                allCategories.push(data);
            });
            if (currentPage === 'menu') renderCategories();
        });
        
        unsubscribeMenuItems = db.collection(`${BASE_PATH}/menuItems`).orderBy('sortOrder').onSnapshot(snapshot => {
            allMenuItems = [];
            snapshot.forEach(doc => {
                const data = doc.data();
                data.id = doc.id;
                allMenuItems.push(data);
            });
            const detailContainer = document.getElementById('categoryDetail');
            if (currentPage === 'menu' && detailContainer && detailContainer.style.display !== 'none') {
                const currentCatId = detailContainer.getAttribute('data-current-cat');
                if (currentCatId) renderProducts(currentCatId);
            }
        });
        
        document.getElementById('addCategoryBtn')?.addEventListener('click', window.showAddCategoryDialog);
    }
    
    function renderCategories() {
        const list = document.getElementById('categoriesList');
        const detail = document.getElementById('categoryDetail');
        
        if(list) list.style.display = 'grid';
        if(detail) detail.style.display = 'none';
        
        if (list) {
            list.innerHTML = allCategories.map(cat => {
                const itemCount = allMenuItems.filter(item => item.categoryId === cat.id).length;
                return `
                    <div class="card cursor-pointer hover-bg" onclick="window.showCategoryDetail('${cat.id}')">
                        <div class="flex-between align-center">
                            <span class="font-bold text-lg">${cat.name}</span>
                            <span class="badge bg-gray text-white">${itemCount} Ürün</span>
                        </div>
                    </div>
                `;
            }).join('');
        }
    }
    
    window.showCategoryDetail = function(categoryId) {
        const cat = allCategories.find(c => c.id === categoryId);
        if (!cat) return;
        
        const list = document.getElementById('categoriesList');
        const detail = document.getElementById('categoryDetail');
        const nameEl = document.getElementById('categoryDetailName');
        const addBtn = document.getElementById('addProductBtn');
        
        if(list) list.style.display = 'none';
        if(detail) {
            detail.style.display = 'block';
            detail.setAttribute('data-current-cat', categoryId);
        }
        if(nameEl) nameEl.innerHTML = `<button class="btn btn-sm mr-4" onclick="window.backToCategories()">⬅️</button> ${cat.name}`;
        
        if(addBtn) {
            const newAddBtn = addBtn.cloneNode(true);
            addBtn.parentNode.replaceChild(newAddBtn, addBtn);
            newAddBtn.addEventListener('click', () => window.showAddProductDialog(categoryId));
        }
        
        renderProducts(categoryId);
    };
    
    window.backToCategories = function() {
        const list = document.getElementById('categoriesList');
        const detail = document.getElementById('categoryDetail');
        if(list) list.style.display = 'grid';
        if(detail) detail.style.display = 'none';
    };
    
    function renderProducts(categoryId) {
        const list = document.getElementById('productsList');
        const products = allMenuItems.filter(i => i.categoryId === categoryId);
        
        if (list) {
            if (products.length === 0) {
                list.innerHTML = `<div class="text-center text-gray p-8">Bu kategoride henüz ürün yok.</div>`;
                return;
            }
            
            list.innerHTML = products.map(item => `
                <div class="card flex-between align-center">
                    <div class="flex-col gap-2">
                        <div class="font-bold">${item.name}</div>
                        <div class="text-sm text-gray">${item.description || ''}</div>
                        <div class="font-bold text-primary mt-2">₺${formatPrice(item.price)}</div>
                    </div>
                    <div class="flex gap-4 align-center">
                        <label class="toggle-switch">
                            <input type="checkbox" ${item.isAvailable ? 'checked' : ''} onchange="window.toggleProductStock('${item.id}', this.checked)">
                            <span class="slider"></span>
                        </label>
                        <button class="btn btn-sm" onclick='window.showEditProductDialog(${JSON.stringify(item).replace(/'/g, "&apos;")})'>✏️</button>
                    </div>
                </div>
            `).join('');
        }
    }
    
    window.toggleProductStock = async function(itemId, isAvailable) {
        try {
            const db = firebase.firestore();
            await db.doc(`${BASE_PATH}/menuItems/${itemId}`).update({ isAvailable });
            showToast(isAvailable ? 'Ürün stokta' : 'Ürün tükendi');
        } catch(e) {
            console.error(e);
            showToast('Hata oluştu');
        }
    };
    
    window.showAddCategoryDialog = function() {
        const bodyHtml = `
            <div class="flex-col gap-4">
                <label>Kategori Adı</label>
                <input type="text" id="catName" class="input">
                <label>Sıra No</label>
                <input type="number" id="catSort" class="input" value="${allCategories.length * 10}">
            </div>
        `;
        const footerHtml = `
            <button class="btn" onclick="window.closeModal()">İptal</button>
            <button class="btn btn-primary" onclick="window.saveCategory()">Kaydet</button>
        `;
        showModal('Yeni Kategori', bodyHtml, footerHtml);
    };
    
    window.saveCategory = async function() {
        const nameEl = document.getElementById('catName');
        const sortEl = document.getElementById('catSort');
        const name = nameEl ? nameEl.value : '';
        const sortOrder = sortEl ? parseInt(sortEl.value) || 0 : 0;
        
        if (!name) return showToast('İsim zorunlu');
        
        try {
            const db = firebase.firestore();
            await db.collection(`${BASE_PATH}/categories`).add({
                name,
                sortOrder,
                imageUrl: ''
            });
            closeModal();
            showToast('Kategori eklendi');
        } catch (e) {
            console.error(e);
            showToast('Hata oluştu');
        }
    };

    window.showAddProductDialog = function(categoryId) {
        const bodyHtml = `
            <div class="flex-col gap-4">
                <label>Ürün Adı</label>
                <input type="text" id="prodName" class="input">
                
                <label>Fiyat (₺)</label>
                <input type="number" id="prodPrice" class="input" step="0.01">
                
                <label>Açıklama</label>
                <textarea id="prodDesc" class="input"></textarea>
                
                <label>Sıra No</label>
                <input type="number" id="prodSort" class="input" value="0">
            </div>
        `;
        const footerHtml = `
            <button class="btn" onclick="window.closeModal()">İptal</button>
            <button class="btn btn-primary" onclick="window.saveProduct('${categoryId}')">Kaydet</button>
        `;
        showModal('Yeni Ürün', bodyHtml, footerHtml);
    };
    
    window.saveProduct = async function(categoryId, existingId = null) {
        const name = document.getElementById('prodName').value;
        const price = parseFloat(document.getElementById('prodPrice').value);
        const description = document.getElementById('prodDesc').value;
        const sortOrder = parseInt(document.getElementById('prodSort').value) || 0;
        
        if (!name || isNaN(price)) return showToast('İsim ve geçerli fiyat zorunlu');
        
        const data = {
            categoryId,
            name,
            price,
            description,
            sortOrder,
            isAvailable: true,
            allergens: [],
            imageUrl: ''
        };
        
        try {
            const db = firebase.firestore();
            if (existingId) {
                delete data.isAvailable; 
                await db.doc(`${BASE_PATH}/menuItems/${existingId}`).update(data);
                showToast('Ürün güncellendi');
            } else {
                await db.collection(`${BASE_PATH}/menuItems`).add(data);
                showToast('Ürün eklendi');
            }
            closeModal();
        } catch (e) {
            console.error(e);
            showToast('Hata oluştu');
        }
    };
    
    window.showEditProductDialog = function(item) {
        const bodyHtml = `
            <div class="flex-col gap-4">
                <label>Ürün Adı</label>
                <input type="text" id="prodName" class="input" value="${item.name}">
                
                <label>Fiyat (₺)</label>
                <input type="number" id="prodPrice" class="input" step="0.01" value="${item.price}">
                
                <label>Açıklama</label>
                <textarea id="prodDesc" class="input">${item.description || ''}</textarea>
                
                <label>Sıra No</label>
                <input type="number" id="prodSort" class="input" value="${item.sortOrder || 0}">
                
                <button class="btn btn-danger mt-4" onclick="window.deleteProduct('${item.id}')">🗑️ Bu Ürünü Sil</button>
            </div>
        `;
        const footerHtml = `
            <button class="btn" onclick="window.closeModal()">İptal</button>
            <button class="btn btn-primary" onclick="window.saveProduct('${item.categoryId}', '${item.id}')">Güncelle</button>
        `;
        showModal('Ürünü Düzenle', bodyHtml, footerHtml);
    };
    
    window.deleteProduct = async function(itemId) {
        if (!confirm('Ürünü silmek istediğinize emin misiniz?')) return;
        try {
            const db = firebase.firestore();
            await db.doc(`${BASE_PATH}/menuItems/${itemId}`).delete();
            closeModal();
            showToast('Ürün silindi');
        } catch(e) {
            console.error(e);
            showToast('Silme hatası');
        }
    };

    // ==========================================
    // MODULE 8: Dashboard
    // ==========================================
    function checkDashboardPin() {
        const pin = document.getElementById('dashboardPinInput').value;
        const error = document.getElementById('dashboardPinError');
        const correctPin = (restaurant && restaurant.managerPin) ? restaurant.managerPin : '2569';
        
        if (pin === correctPin) {
            dashboardUnlocked = true;
            document.getElementById('dashboardPinGate').style.display = 'none';
            document.getElementById('dashboardContent').style.display = 'block';
            loadDashboardData();
        } else {
            if (error) error.innerText = 'Hatalı PIN kodu.';
        }
    }
    
    async function loadDashboardData() {
        const db = firebase.firestore();
        let query = db.collection(`${BASE_PATH}/orders`);
        
        let dashboardOrders = [];
        
        try {
            if (dashboardFilter === 'today') {
                const snapshot = await query.where('isArchived', '==', false).get();
                snapshot.forEach(doc => {
                    const data = doc.data();
                    if (!data.isDayClosed) dashboardOrders.push(data);
                });
            } else if (dashboardFilter === 'week') {
                const snapshot = await query.where('isArchived', '==', false).get();
                snapshot.forEach(doc => dashboardOrders.push(doc.data()));
            } else if (dashboardFilter === 'past') {
                const snapshot = await query.where('isArchived', '==', true)
                                          .orderBy('createdAt', 'desc').limit(200).get();
                snapshot.forEach(doc => dashboardOrders.push(doc.data()));
            }
            
            renderDashboardCards(dashboardOrders);
            
        } catch (error) {
            console.error("Dashboard data load error:", error);
            showToast('Veriler yüklenirken hata oluştu');
        }
    }
    
    function renderDashboardCards(orders) {
        let totalRevenue = 0;
        let cashTotal = 0;
        let cardTotal = 0;
        let transferTotal = 0;
        let complimentaryTotal = 0;
        let pendingTotal = 0;
        let orderCount = orders.length;
        let itemCount = 0;
        
        const productCounts = {};
        
        orders.forEach(order => {
            if (order.status !== 'cancelled') {
                pendingTotal += calcOrderRemaining(order);
                
                if (order.items) {
                    order.items.forEach(item => {
                        itemCount += item.quantity;
                        
                        if (item.isPaid && !item.isComplimentary) {
                            const price = calcItemEffectivePrice(item);
                            
                            if (item.cashPaid) cashTotal += item.cashPaid;
                            if (item.cardPaid) cardTotal += item.cardPaid;
                            if (item.transferPaid) transferTotal += item.transferPaid;
                            
                            if (!item.cashPaid && !item.cardPaid && !item.transferPaid) {
                                if (item.paymentMethod === 'cash') cashTotal += price;
                                else if (item.paymentMethod === 'card') cardTotal += price;
                                else if (item.paymentMethod === 'transfer') transferTotal += price;
                            }
                            
                            totalRevenue += (item.cashPaid || 0) + (item.cardPaid || 0) + (item.transferPaid || 0);
                            if(!item.cashPaid && !item.cardPaid && !item.transferPaid && item.paymentMethod !== 'complimentary') {
                                totalRevenue += price;
                            }
                            
                            if (!productCounts[item.name]) productCounts[item.name] = 0;
                            productCounts[item.name] += item.quantity;
                        } else if (item.isComplimentary) {
                            complimentaryTotal += (item.unitPrice * item.quantity);
                        }
                    });
                }
            }
        });
        
        const revCard = document.getElementById('revenueCard');
        if(revCard) revCard.innerHTML = `
            <div class="text-gray mb-2">Toplam Ciro</div>
            <div class="text-3xl font-bold text-success">₺${formatPrice(totalRevenue)}</div>
            <div class="text-sm mt-4">${orderCount} Sipariş • ${itemCount} Ürün</div>
        `;
        
        const breakdownCard = document.getElementById('paymentBreakdown');
        if(breakdownCard) breakdownCard.innerHTML = `
            <div class="text-gray mb-4 font-bold">Ödeme Kırılımı</div>
            <div class="flex-between mb-2"><span>Nakit 💵</span> <span class="font-bold">₺${formatPrice(cashTotal)}</span></div>
            <div class="flex-between mb-2"><span>Kart 💳</span> <span class="font-bold">₺${formatPrice(cardTotal)}</span></div>
            <div class="flex-between mb-2"><span>Havale 📲</span> <span class="font-bold">₺${formatPrice(transferTotal)}</span></div>
            <div class="border-t pt-2 mt-2 flex-between text-gray"><span>İkram 🎁</span> <span>₺${formatPrice(complimentaryTotal)}</span></div>
        `;
        
        const pCard = document.getElementById('pendingCard');
        if(pCard) pCard.innerHTML = `
            <div class="text-gray mb-2">Açık Hesap (Bekleyen)</div>
            <div class="text-3xl font-bold text-primary">₺${formatPrice(pendingTotal)}</div>
            ${dashboardFilter === 'today' ? `
                <button class="btn btn-primary w-full mt-4" onclick="window.closeDailyReport()">Günü Kapat 🌙</button>
            ` : ''}
        `;
        
        const topProducts = Object.keys(productCounts).map(name => ({ name, count: productCounts[name] }))
                                  .sort((a,b) => b.count - a.count).slice(0, 5);
                                  
        const topEl = document.getElementById('topProducts');
        if(topEl) topEl.innerHTML = topProducts.length > 0 ? topProducts.map(p => `
            <div class="flex-between p-3 border-b">
                <span>${p.name}</span>
                <span class="font-bold badge bg-gray text-white">${p.count} adet</span>
            </div>
        `).join('') : '<div class="text-gray p-4">Veri yok.</div>';
    }
    
    window.closeDailyReport = async function() {
        if (!confirm('Günü kapatmak istediğinize emin misiniz? Açık masalar etkilenmez, sadece bugünkü cirolar arşivlenir.')) return;
        
        try {
            const db = firebase.firestore();
            const batch = db.batch();
            const todayStr = new Date().toLocaleDateString('tr-TR');
            const ordersToClose = allOrders.filter(o => !o.isDayClosed && (o.status === 'delivered' || o.status === 'cancelled'));
            
            if (ordersToClose.length === 0) {
                return showToast('Kapatılacak tamamlanmış sipariş yok.');
            }
            
            ordersToClose.slice(0, 500).forEach(order => {
                const ref = db.doc(`${BASE_PATH}/orders/${order.id}`);
                batch.update(ref, {
                    isDayClosed: true,
                    closedDayDate: todayStr,
                    isArchived: true,
                    updatedAt: firebase.firestore.FieldValue.serverTimestamp()
                });
            });
            
            await batch.commit();
            showToast('Gün başarıyla kapatıldı 🌙');
            loadDashboardData();
            
        } catch(e) {
            console.error(e);
            showToast('Hata oluştu');
        }
    };

    // ==========================================
    // MODULE 9: Settings
    // ==========================================
    function renderSettings() {
        const container = document.getElementById('page-settings');
        if (container && restaurant) {
            container.innerHTML = `
                <div class="card mb-8">
                    <h2 class="font-bold text-xl mb-4">Restoran Bilgileri</h2>
                    <div class="mb-4"><strong>Ad:</strong> ${restaurant.name || RESTAURANT_ID}</div>
                    <div class="mb-4"><strong>Slug:</strong> ${restaurant.slug || '-'}</div>
                    <div class="mb-4"><strong>Yönetici PIN:</strong> **** (Gizli)</div>
                </div>
                <div class="card">
                    <h2 class="font-bold text-xl mb-4">Uygulama Bilgisi</h2>
                    <div class="mb-2">Versiyon: 1.0.0</div>
                    <div class="mb-2">Bağlantı: Aktif ✅</div>
                </div>
            `;
        }
    }

    // ==========================================
    // MODULE 11: Manual Cash Sale (Hızlı Satış)
    // ==========================================
    window.showManualSaleDialog = function() {
        manualSaleCart = [];
        
        const bodyHtml = `
            <div class="flex-col gap-4">
                <label class="font-bold">Konum / Masa</label>
                <select id="msLocation" class="input">
                    <option value="kasa">KASA / AL-GÖTÜR</option>
                    ${allTables.map(t => `<option value="${t.id}">${t.label}</option>`).join('')}
                </select>
                
                <label class="font-bold">İşlem Türü</label>
                <select id="msType" class="input" onchange="window.toggleMsPaymentFields()">
                    <option value="cash_now">Anında Peşin Ödeme ✅</option>
                    <option value="open_tab">Masaya Açık Hesap 📝</option>
                </select>
                
                <div id="msPaymentMethodDiv" class="flex-col gap-2">
                    <label class="font-bold">Ödeme Yöntemi</label>
                    <div class="flex gap-2">
                        <label class="radio-label flex-1"><input type="radio" name="msMethod" value="cash" checked> Nakit 💵</label>
                        <label class="radio-label flex-1"><input type="radio" name="msMethod" value="card"> Kart 💳</label>
                    </div>
                </div>
                
                <label class="font-bold">Müşteri Adı (Opsiyonel)</label>
                <input type="text" id="msCustomer" class="input" placeholder="Örn: Ahmet Bey">
                
                <hr class="my-4">
                
                <label class="font-bold">Ürün Ekle</label>
                <div class="flex gap-4">
                    <select id="msProductSelect" class="input flex-1">
                        ${allMenuItems.filter(i=>i.isAvailable).map(i => `<option value="${i.id}">${i.name} - ₺${formatPrice(i.price)}</option>`).join('')}
                    </select>
                    <button class="btn btn-primary" onclick="window.addMsProduct()">Ekle</button>
                </div>
                
                <div id="msCartList" class="mt-4 flex-col gap-2 bg-gray-100 p-4 rounded min-h-[100px]">
                </div>
                
                <div class="text-right font-bold text-xl mt-4" id="msTotal">Toplam: ₺0,00</div>
            </div>
        `;
        
        const footerHtml = `
            <button class="btn" onclick="window.closeModal()">İptal</button>
            <button class="btn btn-success" onclick="window.submitManualSale()">Siparişi Tamamla ✅</button>
        `;
        
        showModal('Yeni Sipariş / Satış', bodyHtml, footerHtml);
        window.renderMsCart();
    };
    
    window.toggleMsPaymentFields = function() {
        const type = document.getElementById('msType').value;
        const methodDiv = document.getElementById('msPaymentMethodDiv');
        if (methodDiv) methodDiv.style.display = type === 'cash_now' ? 'flex' : 'none';
    };
    
    window.addMsProduct = function() {
        const sel = document.getElementById('msProductSelect');
        if (!sel) return;
        const prodId = sel.value;
        const prod = allMenuItems.find(i => i.id === prodId);
        
        if (prod) {
            const existing = manualSaleCart.find(i => i.menuItemId === prod.id);
            if (existing) {
                existing.quantity++;
            } else {
                manualSaleCart.push({
                    menuItemId: prod.id,
                    name: prod.name,
                    unitPrice: prod.price,
                    quantity: 1,
                    note: '',
                    discountAmount: 0,
                    isComplimentary: false,
                    isPaid: false
                });
            }
            window.renderMsCart();
        }
    };
    
    window.removeMsProduct = function(index) {
        manualSaleCart.splice(index, 1);
        window.renderMsCart();
    };
    
    window.renderMsCart = function() {
        const list = document.getElementById('msCartList');
        const totalEl = document.getElementById('msTotal');
        
        if (!list || !totalEl) return;
        
        if (manualSaleCart.length === 0) {
            list.innerHTML = '<div class="text-gray text-center text-sm">Sepet boş</div>';
            totalEl.innerText = 'Toplam: ₺0,00';
            return;
        }
        
        let total = 0;
        list.innerHTML = manualSaleCart.map((item, index) => {
            const price = (item.unitPrice * item.quantity);
            total += price;
            return `
                <div class="flex-between bg-white p-2 rounded border">
                    <div>
                        <span class="font-bold">${item.quantity}x</span> ${item.name}
                    </div>
                    <div class="flex align-center gap-4">
                        <span>₺${formatPrice(price)}</span>
                        <button class="btn btn-sm btn-danger py-1 px-2" onclick="window.removeMsProduct(${index})">X</button>
                    </div>
                </div>
            `;
        }).join('');
        
        totalEl.innerText = `Toplam: ₺${formatPrice(total)}`;
    };
    
    window.submitManualSale = async function() {
        if (manualSaleCart.length === 0) {
            return showToast('Sepete ürün ekleyin');
        }
        
        const loc = document.getElementById('msLocation').value;
        const type = document.getElementById('msType').value;
        const customer = document.getElementById('msCustomer').value || 'Misafir';
        const methodEl = document.querySelector('input[name="msMethod"]:checked');
        const method = methodEl ? methodEl.value : 'cash';
        
        let tableId = loc;
        let tableLabel = 'KASA / AL-GÖTÜR';
        
        if (loc !== 'kasa') {
            const t = allTables.find(t => t.id === loc);
            if (t) tableLabel = t.label;
        }
        
        const isPaidNow = (type === 'cash_now');
        const items = manualSaleCart.map(i => {
            const item = { ...i };
            if (isPaidNow) {
                item.isPaid = true;
                item.paidAt = Date.now();
                item.paymentMethod = method;
                const price = item.unitPrice * item.quantity;
                if (method === 'cash') item.cashPaid = price;
                else if (method === 'card') item.cardPaid = price;
            }
            return item;
        });
        
        const total = items.reduce((sum, i) => sum + (i.unitPrice * i.quantity), 0);
        
        const orderData = {
            tableId,
            tableLabel,
            customerName: customer,
            status: isPaidNow ? 'delivered' : 'pending',
            totalPrice: total,
            note: 'Hızlı/Manuel Satış',
            isArchived: false,
            isDayClosed: false,
            createdAt: firebase.firestore.FieldValue.serverTimestamp(),
            updatedAt: firebase.firestore.FieldValue.serverTimestamp(),
            items: items
        };
        
        try {
            const db = firebase.firestore();
            await db.collection(`${BASE_PATH}/orders`).add(orderData);
            closeModal();
            showToast('Sipariş başarıyla oluşturuldu ✅');
        } catch(e) {
            console.error(e);
            showToast('Sipariş oluşturulamadı');
        }
    };

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }

})();
