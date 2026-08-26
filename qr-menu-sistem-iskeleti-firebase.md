# QR Menü & Sipariş Sistemi — Firebase + Android Studio Mimarisi

## 1. Genel Bakış

Güncellenmiş mimari:

- **Backend:** Ayrı bir sunucu kurulmayacak, tamamen **Firebase** kullanılacak (Firestore, Auth, Cloud Messaging, Storage, Hosting, opsiyonel Cloud Functions).
- **Mobil Uygulama (Dükkan Sahibi):** **Android Studio** ile native olarak yazılacak (Kotlin önerilir).
- **QR Menü (Müşteri Web Sayfası):** Web app (React/Next.js veya sade HTML/JS) → Firestore'a doğrudan bağlanacak, Firebase Hosting üzerinde yayınlanabilir.

```
[Müşteri Telefonu - Tarayıcı]              [Dükkan Sahibi Telefonu - Android App]
   QR okut -> Web Menü Sayfası                     Android Studio (Kotlin)
         |                                                  |
         |  Firestore SDK (JS)                Firestore SDK (Android) + FCM
         v                                                  v
                    -------- FIREBASE --------
                    | Firestore (Veritabanı) |
                    | Authentication          |
                    | Cloud Messaging (FCM)   |
                    | Storage (görseller)     |
                    | Hosting (web menü)      |
                    | Cloud Functions (ops.)  |
                    ---------------------------
```

**Neden bu mimari mantıklı:**
- Ayrı backend sunucusu yazmana/yönetmene gerek kalmaz, DevOps yükü neredeyse sıfır.
- Firestore'un **realtime listener** özelliği sayesinde "sipariş geldi" anlık olarak hem web hem Android tarafına otomatik düşer (websocket kurmana gerek yok).
- FCM zaten Firebase'in bir parçası, push bildirim entegrasyonu doğrudan aynı proje içinde.
- Küçük/orta ölçekli bir işletme için maliyet düşük (Firestore ücretsiz katman genelde yeterli).

---

## 2. Firebase Proje Yapısı

Tek bir Firebase projesi altında:

| Servis | Kullanım Amacı |
|---|---|
| **Firestore Database** | Menü, masa, sipariş, kullanıcı verileri |
| **Authentication** | Dükkan sahibi/personel girişi (Email+Şifre veya Telefon+OTP) |
| **Cloud Messaging (FCM)** | Yeni sipariş geldiğinde Android uygulamasına push bildirim |
| **Storage** | Ürün fotoğrafları, logo |
| **Hosting** | QR menü web sayfasının barındırılması (`menu.siteadin.com`) |
| **Cloud Functions** (opsiyonel ama önerilir) | Sipariş oluşunca FCM bildirimini **güvenli şekilde** sunucu tarafında tetiklemek için |

> **Önemli not:** Sipariş oluştuğunda push bildirimi doğrudan web sayfasından (client-side) FCM'e tetikletmek güvenlik açısından doğru değildir — bu iş **Cloud Functions** (Firestore trigger) ile sunucu tarafında yapılmalı. Detay Bölüm 7'de.

---

## 3. Firestore Veri Modeli (Koleksiyon Yapısı)

Firestore doküman-tabanlı olduğu için ilişkisel şema yerine koleksiyon/alt-koleksiyon mantığıyla kurulur:

```
restaurants (collection)
  └── {restaurantId} (document)
        - name, slug, logoUrl, phone, address, createdAt

        categories (subcollection)
          └── {categoryId}
                - name, sortOrder

        menuItems (subcollection)
          └── {menuItemId}
                - categoryId, name, description, price
                - imageUrl, isAvailable, sortOrder, allergens[]

        tables (subcollection)
          └── {tableId}
                - label ("Masa 4"), qrCodeUrl, isActive

        orders (subcollection)
          └── {orderId}
                - tableId, status (pending/preparing/ready/delivered/cancelled)
                - totalPrice, note, createdAt, updatedAt
                items (subcollection veya array field)
                  - menuItemId, name, quantity, unitPrice, note

staff (collection)
  └── {staffId} (Firebase Auth UID ile eşleşir)
        - restaurantId, name, role (owner/staff/kitchen)
        - fcmToken, fcmTokenUpdatedAt
```

**Neden bu yapı:**
- `restaurantId` altında her şeyin gruplanması, ileride çoklu şube/işletme (multi-tenant) desteğine kolayca geçiş sağlar.
- `orders` alt koleksiyonu sayesinde hem Android hem web tarafı sadece ilgili restorana ait siparişleri dinler (`.collection("restaurants/{id}/orders")`).
- `items` array olarak da tutulabilir (küçük siparişler için basitlik), büyük menülerde subcollection daha esnek.

### 3.1 Firestore Güvenlik Kuralları (Security Rules) — Kritik!

- Müşteri (kimliksiz/anonim kullanıcı) sadece:
  - `menuItems`, `categories` → **okuma** yapabilmeli.
  - `orders` → sadece **yeni doküman oluşturabilmeli** (create), başka siparişleri okuyamamalı/değiştirememeli.
- Dükkan sahibi/personel (Auth ile giriş yapmış, `restaurantId` eşleşen):
  - Kendi restoranının `menuItems`, `categories`, `tables` üzerinde **tam yetki** (CRUD).
  - `orders` üzerinde okuma + durum güncelleme yetkisi.
- Örnek mantık (pseudo-rule):
```
match /restaurants/{restId}/orders/{orderId} {
  allow create: if true; // müşteri sipariş oluşturabilir
  allow read, update: if request.auth != null 
                        && get(/databases/$(database)/documents/staff/$(request.auth.uid)).data.restaurantId == restId;
}
```
Bu kurallar olmadan **herkes** birbirinin siparişini görebilir/değiştirebilir — mutlaka yazılmalı.

---

## 4. QR Menü Web Sayfası (Müşteri Tarafı)

- **Teknoloji:** React/Next.js veya sade HTML+JS — Firebase Web SDK (`firebase/firestore`) ile doğrudan Firestore'a bağlanır.
- **URL yapısı:** `https://menu-siteadin.web.app/{restaurantSlug}?table={tableId}`
- QR kod bu linki taşır; sayfa açıldığında `tableId` URL'den okunur.
- Menü verisi Firestore'dan **realtime listener** (`onSnapshot`) ile çekilir → dükkan sahibi menüde değişiklik yaptığı an, müşteri sayfasını yenilemeden bile menü güncellenir.
- Sipariş gönderme: müşteri sepeti onaylayınca `orders` koleksiyonuna yeni doküman `addDoc()` ile eklenir. **Kullanıcı girişi gerekmez** (Firestore rules'da "create" izinli, kimliksiz/anonim auth kullanılabilir).
- **Firebase Hosting** üzerinde barındırılabilir (`firebase deploy`), SSL otomatik gelir.

---

## 5. Android Uygulaması (Dükkan Sahibi) — Android Studio

### 5.1 Proje Kurulumu
- Dil: **Kotlin** (önerilir), UI: Jetpack Compose (modern) veya XML+ViewBinding (klasik).
- Firebase bağlantısı: Android Studio içinden **Tools > Firebase** paneli ile `google-services.json` projeye eklenir.
- Kullanılacak Firebase Android kütüphaneleri (Gradle):
  - `firebase-firestore-ktx` — veritabanı
  - `firebase-auth-ktx` — giriş
  - `firebase-messaging-ktx` — push bildirim (FCM)
  - `firebase-storage-ktx` — görsel yükleme

### 5.2 Ekran/Modül Listesi
1. **Giriş Ekranı** — Firebase Auth (email/şifre).
2. **Ana Sayfa / Sipariş Listesi** — `orders` koleksiyonunu `status == pending` filtresiyle **realtime dinler** (`addSnapshotListener`), yeni sipariş geldiğinde liste otomatik güncellenir + ses/titreşim tetiklenir.
3. **Sipariş Detay Ekranı** — Ürünler, adet, not, toplam tutar; durum güncelleme butonları (Hazırlanıyor/Hazır/Teslim Edildi) → Firestore'da `update()`.
4. **Menü Yönetimi Ekranı** — Kategori/ürün listesi, ekle/düzenle/sil.
5. **Ürün Ekle/Düzenle Formu** — İsim, fiyat, açıklama, fotoğraf seçimi → fotoğraf **Firebase Storage**'a yüklenir, dönen URL Firestore'daki `menuItems` dokümanına yazılır.
6. **Masa/QR Yönetimi Ekranı** — Yeni masa ekle → `tableId` üretilir → QR kod **uygulama içinde** üretilip (örn. `ZXing` kütüphanesi ile) görüntülenir/paylaşılır/yazdırılabilir.
7. **Bildirim Ayarları / Profil Ekranı**.

### 5.3 Önerilen Android Kütüphaneleri
| İhtiyaç | Kütüphane |
|---|---|
| QR kod üretme | `com.google.zxing:core` + `journeyapps:zxing-android-embedded` |
| Görsel yükleme/gösterme | `Coil` veya `Glide` |
| Asenkron/reaktif veri | Kotlin `Coroutines` + `Flow` (Firestore listener'ları Flow'a çevirerek) |
| Bildirim sesi/titreşim | Android `NotificationChannel` (Android 8+ zorunlu) + özel ses dosyası |

---

## 6. Kullanıcı Akışları (Firebase'e Göre Güncellenmiş)

### 6.1 Müşteri Sipariş Akışı
1. QR kod okutulur → web sayfası açılır (`tableId` URL parametresinde).
2. Sayfa Firestore'dan `menuItems` + `categories` verisini gerçek zamanlı çeker.
3. Sepet oluşturulur, "Siparişi Gönder" ile Firestore `orders` koleksiyonuna yeni doküman eklenir (`status: "pending"`).
4. Firestore'a yazılan bu doküman, **Cloud Function trigger**'ını tetikler (bkz. Bölüm 7).
5. Müşteri ekranında "Siparişiniz alındı" onayı gösterilir; istenirse `orderId` ile sipariş durumu realtime dinlenmeye devam edilir.

### 6.2 Dükkan Sahibi Sipariş Alma Akışı
1. Android uygulaması `orders` koleksiyonunu sürekli dinliyor (`addSnapshotListener`), yeni doküman geldiğinde liste anında güncellenir.
2. Aynı anda Cloud Function tetiklediği için **push bildirim (FCM)** da telefona düşer — uygulama arka planda/kapalıyken bile haberdar olunur.
3. Bildirime dokununca uygulama açılır, ilgili sipariş detayına yönlenir (`Intent` + `orderId` extra data).
4. Sahibi durumu günceller → Firestore `orders/{orderId}` dokümanı `update()` edilir.
5. (Opsiyonel) Müşteri tarafı bu değişikliği realtime dinliyorsa anında görür.

### 6.3 Menü Düzenleme Akışı
1. Android uygulamasında ürün eklenir/düzenlenir.
2. Fotoğraf varsa önce Storage'a yüklenir, URL alınır.
3. Firestore `menuItems/{id}` dokümanı `set()`/`update()` ile güncellenir.
4. Web tarafı bu koleksiyonu realtime dinlediği için **anında**, sayfa yenilemeden menüye yansır.

### 6.4 Masa/QR Kod Oluşturma Akışı
1. Android uygulamasında "Masa Ekle" ile `tables` koleksiyonuna yeni doküman (`tableId` otomatik/UUID).
2. Uygulama, `https://menu-siteadin.web.app/{slug}?table={tableId}` linkini QR koduna çevirir (ZXing).
3. QR görsel olarak ekranda gösterilir, kaydedilip yazıcıdan bastırılabilir.

---

## 7. Bildirim Sistemi — Firestore + Cloud Functions + FCM

**Neden client-side değil Cloud Functions ile yapılmalı:**
Web sayfası (müşteri tarafı) kimliksiz/güvensiz bir ortamdır; buradan doğrudan "şu personele push gönder" komutu çalıştırmak hem güvenlik açığı hem de FCM sunucu anahtarının client'a sızması riski taşır. Bunun yerine:

1. Müşteri sipariş oluşturur → Firestore'a yeni `orders/{orderId}` dokümanı yazılır.
2. **Cloud Function** (`onCreate` trigger, `functions.firestore.document('restaurants/{restId}/orders/{orderId}').onCreate(...)`) bu olayı yakalar.
3. Function, ilgili restorana ait `staff` kayıtlarındaki `fcmToken`'ları Firestore'dan okur.
4. `admin.messaging().sendMulticast(...)` ile bu token'lara push bildirim gönderir.
5. Android tarafında `FirebaseMessagingService` bildirim payload'ını alır, bildirim oluşturur (ses+titreşim ile), kullanıcı dokununca ilgili sipariş ekranına yönlendirir.

**Örnek Cloud Function mantığı (Node.js, pseudo-code):**
```javascript
exports.onNewOrder = functions.firestore
  .document('restaurants/{restId}/orders/{orderId}')
  .onCreate(async (snap, context) => {
    const order = snap.data();
    const restId = context.params.restId;

    const staffSnap = await admin.firestore()
      .collection('staff')
      .where('restaurantId', '==', restId)
      .get();

    const tokens = staffSnap.docs.map(d => d.data().fcmToken).filter(Boolean);

    if (tokens.length > 0) {
      await admin.messaging().sendEachForMulticast({
        tokens,
        notification: {
          title: 'Yeni Sipariş!',
          body: `Masa ${order.tableLabel} - Yeni sipariş geldi`
        },
        data: { orderId: context.params.orderId, restId }
      });
    }
  });
```

6. Android'de `fcmToken`, uygulama ilk açıldığında ve token yenilendiğinde (`onNewToken`) Firestore'daki `staff/{uid}` dokümanına yazılmalı.

---

## 8. Güvenlik Konuları (Firebase'e Özel)

- **Firestore Security Rules mutlaka yazılmalı** (Bölüm 3.1) — varsayılan "test mode" kurallarıyla asla canlıya çıkılmamalı, herkes her şeyi okuyup yazabilir hale gelir.
- Cloud Functions içindeki FCM gönderim kodu **Admin SDK** ile server-side çalıştığı için güvenlidir, client bu yetkiye asla sahip olmamalı.
- Müşteri tarafı **App Check** (Firebase App Check) ile korunabilir — botların/otomatik scriptlerin sahte sipariş açmasını zorlaştırır.
- `tableId` tahmin edilemeyecek şekilde otomatik Firestore doküman ID'si (rastgele) kullanılmalı, sıralı sayı (1,2,3...) kullanılmamalı.
- Sipariş oluşturma sıklığına Cloud Functions veya Firestore Rules ile basit bir rate-limit eklenmesi önerilir (spam siparişleri engellemek için).

---

## 9. Maliyet / Firebase Plan Notu

- **Spark Plan (ücretsiz)**: Küçük/orta trafik için Firestore okuma/yazma ve Hosting genelde yeterli.
- **Cloud Functions kullanmak için Blaze Plan (kullandıkça öde)** gereklidir — Spark planda dış API'lere (FCM dahil bazı senaryolarda) giden Cloud Functions çalışmaz. Blaze plan'da da düşük trafikte pratikte ücretsiz katman sınırları içinde kalınabilir, yine de dikkatli izlenmeli.

---

## 10. MVP Kapsamı (Firebase + Android Studio için Güncel)

**İlk sürümde olmalı:**
- Firebase projesi kurulumu (Firestore, Auth, FCM, Storage, Hosting)
- Web QR menü sayfası (menü listeleme + sipariş gönderme)
- Android uygulaması: giriş, sipariş listesi (realtime), sipariş detay/durum güncelleme
- Cloud Function ile push bildirim tetikleme
- Menü yönetimi (Android'den ürün/kategori CRUD)
- Masa/QR kod oluşturma (Android'de ZXing ile)

**Sonraya bırakılabilir:**
- Online ödeme entegrasyonu
- Çoklu personel rol yönetimi (owner/staff/kitchen ayrımı)
- Raporlama/analitik ekranı
- Müşteri tarafı sipariş durumu canlı takip ekranı
- App Check ile bot koruması
- Çoklu dil desteği (web menü)

---

## 11. Teknoloji Yığını (Güncel Özet)

| Katman | Teknoloji |
|---|---|
| QR Menü (müşteri, web) | React/Next.js veya HTML+JS + Firebase Web SDK |
| Mobil Uygulama (dükkan sahibi) | Android Studio, Kotlin, Jetpack Compose |
| Veritabanı | Firebase Firestore |
| Kimlik Doğrulama | Firebase Authentication |
| Push Bildirim | Firebase Cloud Messaging (FCM) |
| Sunucu Mantığı (bildirim tetikleme) | Firebase Cloud Functions (Node.js) |
| Görsel Depolama | Firebase Storage |
| Web Barındırma | Firebase Hosting |
| QR Kod Üretimi | ZXing (Android tarafında) |

---

## 12. Genel Akış Şeması (Firebase Sürümü)

```
Müşteri QR okutur
      |
      v
Web Menü Sayfası açılır (Firestore realtime dinler)
      |
      v
Sepet oluşturulur -> "orders" koleksiyonuna yeni doküman yazılır
      |
      v
Cloud Function tetiklenir (onCreate trigger)
      |
      +---> Firestore realtime listener -> Android uygulaması listesi otomatik güncellenir
      |
      +---> FCM push bildirimi -> Dükkan sahibi Android telefonu
                     |
                     v
         Bildirime dokunulur -> Sipariş Detay ekranı açılır
                     |
                     v
         Durum güncellenir -> Firestore orders/{id} update()
                     |
                     v
         (Opsiyonel) Web tarafı realtime dinliyorsa anında yansır
```
