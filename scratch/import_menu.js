const fs = require('fs');

async function importFullMenu() {
  const restId = 'sadec-gerze';
  const baseUrl = `https://firestore.googleapis.com/v1/projects/sadec-9b458/databases/(default)/documents/restaurants/${restId}`;

  // 1. Wipe existing categories & menuItems in sadec-gerze just to be 100% clean
  console.log("Cleaning sadec-gerze...");
  const oldCatsRes = await fetch(`${baseUrl}/categories?pageSize=300`);
  const oldCats = await oldCatsRes.json();
  if (oldCats.documents) {
    await Promise.all(oldCats.documents.map(d => fetch(`https://firestore.googleapis.com/v1/${d.name}`, { method: 'DELETE' })));
  }

  const oldItemsRes = await fetch(`${baseUrl}/menuItems?pageSize=300`);
  const oldItems = await oldItemsRes.json();
  if (oldItems.documents) {
    await Promise.all(oldItems.documents.map(d => fetch(`https://firestore.googleapis.com/v1/${d.name}`, { method: 'DELETE' })));
  }

  console.log("Adding 5 Core Categories with AI images...");

  // Category definitions with AI images & sort orders
  const categoryDefs = [
    {
      name: "Sıcak Kahve & Sıcak İçecekler",
      sortOrder: 1,
      imageUrl: "images/cat_hot.jpg"
    },
    {
      name: "Soğuk İçecekler & Kahveler",
      sortOrder: 2,
      imageUrl: "images/cat_cold.jpg"
    },
    {
      name: "Atıştırmalıklar & Tostlar",
      sortOrder: 3,
      imageUrl: "images/cat_snacks.jpg"
    },
    {
      name: "Tatlılar & Pastalar",
      sortOrder: 4,
      imageUrl: "images/cat_desserts.jpg"
    },
    {
      name: "Spesiyeller & Sandviçler",
      sortOrder: 5,
      imageUrl: "images/cat_specials.jpg"
    }
  ];

  const createdCats = {};

  for (const cat of categoryDefs) {
    const res = await fetch(`${baseUrl}/categories`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        fields: {
          name: { stringValue: cat.name },
          sortOrder: { integerValue: cat.sortOrder.toString() },
          imageUrl: { stringValue: cat.imageUrl }
        }
      })
    });
    const data = await res.json();
    const docId = data.name.split('/').pop();
    createdCats[cat.name] = docId;
    console.log(`Created Category: ${cat.name} -> ID: ${docId}`);
  }

  // All 61 products from C:\Users\berka\OneDrive\Desktop\tasarımprojeleri\sadec\index.html
  const allProducts = [
    // 1. Sıcak Kahve & İçecekler (21 adet)
    { cat: "Sıcak Kahve & Sıcak İçecekler", name: "Espresso", desc: "30ml klasik yoğun İtalyan espresso", price: 100, allergens: ["Kafein"], sort: 1, img: "images/cat_hot.jpg" },
    { cat: "Sıcak Kahve & Sıcak İçecekler", name: "Double Espresso", desc: "60ml çift shot espresso", price: 120, allergens: ["Kafein"], sort: 2, img: "images/cat_hot.jpg" },
    { cat: "Sıcak Kahve & Sıcak İçecekler", name: "Double Shot Americano", desc: "150ml sıcak su ve 60ml espresso", price: 140, allergens: ["Kafein"], sort: 3, img: "images/cat_hot.jpg" },
    { cat: "Sıcak Kahve & Sıcak İçecekler", name: "Caffe Latte", desc: "30ml espresso ve 150ml kadifemsi sıcak süt", price: 170, allergens: ["Süt", "Kafein"], sort: 4, img: "images/cat_hot.jpg" },
    { cat: "Sıcak Kahve & Sıcak İçecekler", name: "Cappuccino", desc: "30ml espresso, 150ml sıcak süt, süt köpüğü ve çikolata tozu", price: 170, allergens: ["Süt", "Kafein"], sort: 5, img: "images/cat_hot.jpg" },
    { cat: "Sıcak Kahve & Sıcak İçecekler", name: "Espresso Macchiato", desc: "30ml espresso ve bir kaşık süt köpüğü", price: 150, allergens: ["Süt", "Kafein"], sort: 6, img: "images/cat_hot.jpg" },
    { cat: "Sıcak Kahve & Sıcak İçecekler", name: "Caramel Macchiato", desc: "30ml espresso, 180ml süt ve 30ml karamel", price: 160, allergens: ["Süt", "Kafein"], sort: 7, img: "images/cat_hot.jpg" },
    { cat: "Sıcak Kahve & Sıcak İçecekler", name: "Mocha", desc: "30ml espresso, 20gr çikolata, 130ml süt ve süt kreması", price: 190, allergens: ["Süt", "Kafein"], sort: 8, img: "images/cat_hot.jpg" },
    { cat: "Sıcak Kahve & Sıcak İçecekler", name: "White Chocolate Mocha", desc: "30ml espresso, 20gr beyaz çikolata, 130ml süt ve süt kreması", price: 190, allergens: ["Süt", "Kafein"], sort: 9, img: "images/cat_hot.jpg" },
    { cat: "Sıcak Kahve & Sıcak İçecekler", name: "Cortado", desc: "60ml espresso, 60ml süt ve kreması", price: 160, allergens: ["Süt", "Kafein"], sort: 10, img: "images/cat_hot.jpg" },
    { cat: "Sıcak Kahve & Sıcak İçecekler", name: "Flat White", desc: "60ml double espresso, 120ml sıcak süt", price: 170, allergens: ["Süt", "Kafein"], sort: 11, img: "images/cat_hot.jpg" },
    { cat: "Sıcak Kahve & Sıcak İçecekler", name: "Black Eye", desc: "180ml filtre kahve ve 60ml espresso", price: 160, allergens: ["Kafein"], sort: 12, img: "images/cat_hot.jpg" },
    { cat: "Sıcak Kahve & Sıcak İçecekler", name: "Filtre Kahve V60", desc: "Kolombiya, Sumatra, Guatemala bölgelerinden çekirdekler ile manuel demleme", price: 180, allergens: ["Kafein"], sort: 13, img: "images/cat_hot.jpg" },
    { cat: "Sıcak Kahve & Sıcak İçecekler", name: "Filtre Kahve Makina", desc: "Kolombiya kahvesi ile taze demlenir", price: 120, allergens: ["Kafein"], sort: 14, img: "images/cat_hot.jpg" },
    { cat: "Sıcak Kahve & Sıcak İçecekler", name: "Siyah Çay", desc: "Taze demlenmiş Rize çayı", price: 30, allergens: [], sort: 15, img: "images/cat_hot.jpg" },
    { cat: "Sıcak Kahve & Sıcak İçecekler", name: "Bitki Çayları", desc: "Adaçayı, Hibiscus, Papatya, Matcha vb.", price: 150, allergens: [], sort: 16, img: "images/cat_hot.jpg" },
    { cat: "Sıcak Kahve & Sıcak İçecekler", name: "Türk Kahvesi", desc: "Çifte kavrulmuş lokum ve su ile geleneksel sunum", price: 75, allergens: ["Kafein"], sort: 17, img: "images/cat_hot.jpg" },
    { cat: "Sıcak Kahve & Sıcak İçecekler", name: "Affogato", desc: "Vanilyalı dondurma ve 60ml sıcak espresso", price: 250, allergens: ["Süt", "Kafein"], sort: 18, img: "images/cat_hot.jpg" },
    { cat: "Sıcak Kahve & Sıcak İçecekler", name: "Beyaz Sıcak Çikolata", desc: "180ml sıcak süt ve beyaz çikolata", price: 180, allergens: ["Süt"], sort: 19, img: "images/cat_hot.jpg" },
    { cat: "Sıcak Kahve & Sıcak İçecekler", name: "Pembe Ruby Sıcak Çikolata", desc: "180ml sıcak süt ve ruby çikolata", price: 180, allergens: ["Süt"], sort: 20, img: "images/cat_hot.jpg" },
    { cat: "Sıcak Kahve & Sıcak İçecekler", name: "Sıcak Çikolata", desc: "180ml sıcak süt ve eritilmiş çikolata", price: 180, allergens: ["Süt"], sort: 21, img: "images/cat_hot.jpg" },

    // 2. Soğuk İçecekler (19 adet)
    { cat: "Soğuk İçecekler & Kahveler", name: "Ice Americano", desc: "150ml soğuk su, 60ml espresso ve buz", price: 150, allergens: ["Kafein"], sort: 1, img: "images/cat_cold.jpg" },
    { cat: "Soğuk İçecekler & Kahveler", name: "Ice Latte", desc: "130ml soğuk süt, 30ml espresso ve buz", price: 180, allergens: ["Süt", "Kafein"], sort: 2, img: "images/cat_cold.jpg" },
    { cat: "Soğuk İçecekler & Kahveler", name: "Ice White Chocolate Mocha", desc: "30ml espresso, 20gr beyaz çikolata, 130ml soğuk süt, krema ve buz", price: 200, allergens: ["Süt", "Kafein"], sort: 3, img: "images/cat_cold.jpg" },
    { cat: "Soğuk İçecekler & Kahveler", name: "Ice Mocha", desc: "30ml espresso, 20gr çikolata, 130ml soğuk süt, krema ve buz", price: 200, allergens: ["Süt", "Kafein"], sort: 4, img: "images/cat_cold.jpg" },
    { cat: "Soğuk İçecekler & Kahveler", name: "Ice Caramel Macchiato", desc: "30ml espresso, 180ml soğuk süt, 30ml karamel ve buz", price: 190, allergens: ["Süt", "Kafein"], sort: 5, img: "images/cat_cold.jpg" },
    { cat: "Soğuk İçecekler & Kahveler", name: "Frapeler", desc: "Çilekli, Muzlu, Kakaolu, Karpuzlu, Yeşil Elmalı, Mangolu vb.", price: 180, allergens: ["Süt"], sort: 6, img: "images/cat_cold.jpg" },
    { cat: "Soğuk İçecekler & Kahveler", name: "Cold Brew", desc: "Soğuk dem Kolombiya kahvesi", price: 180, allergens: ["Kafein"], sort: 7, img: "images/cat_cold.jpg" },
    { cat: "Soğuk İçecekler & Kahveler", name: "Ev Yapımı Limonata", desc: "200ml ev yapımı ferahlatıcı limonata ve buz", price: 160, allergens: [], sort: 8, img: "images/cat_cold.jpg" },
    { cat: "Soğuk İçecekler & Kahveler", name: "Ev Yapımı Erik Suyu", desc: "200ml ev yapımı erik suyu ve buz", price: 180, allergens: [], sort: 9, img: "images/cat_cold.jpg" },
    { cat: "Soğuk İçecekler & Kahveler", name: "Ice Hibiscus Çayı", desc: "Buz ve soğuk demlenmiş hibiscus çayı", price: 200, allergens: [], sort: 10, img: "images/cat_cold.jpg" },
    { cat: "Soğuk İçecekler & Kahveler", name: "Muzlu Milkshake", desc: "200ml dondurmalı muzlu milkshake", price: 200, allergens: ["Süt"], sort: 11, img: "images/cat_cold.jpg" },
    { cat: "Soğuk İçecekler & Kahveler", name: "Mangolu Milkshake", desc: "200ml dondurmalı mango milkshake", price: 200, allergens: ["Süt"], sort: 12, img: "images/cat_cold.jpg" },
    { cat: "Soğuk İçecekler & Kahveler", name: "Çilekli Milkshake", desc: "200ml dondurmalı çilek milkshake", price: 200, allergens: ["Süt"], sort: 13, img: "images/cat_cold.jpg" },
    { cat: "Soğuk İçecekler & Kahveler", name: "Çikolatalı Milkshake", desc: "200ml dondurmalı çikolata milkshake", price: 200, allergens: ["Süt"], sort: 14, img: "images/cat_cold.jpg" },
    { cat: "Soğuk İçecekler & Kahveler", name: "Soda Limon", desc: "Maden suyu ve taze limon dilimi", price: 70, allergens: [], sort: 15, img: "images/cat_cold.jpg" },
    { cat: "Soğuk İçecekler & Kahveler", name: "Coca Cola", desc: "330ml Kutu", price: 80, allergens: [], sort: 16, img: "images/cat_cold.jpg" },
    { cat: "Soğuk İçecekler & Kahveler", name: "Fanta", desc: "330ml Kutu", price: 80, allergens: [], sort: 17, img: "images/cat_cold.jpg" },
    { cat: "Soğuk İçecekler & Kahveler", name: "Sprite", desc: "330ml Kutu", price: 80, allergens: [], sort: 18, img: "images/cat_cold.jpg" },
    { cat: "Soğuk İçecekler & Kahveler", name: "Bardak Su", desc: "Doğal kaynak suyu", price: 30, allergens: [], sort: 19, img: "images/cat_cold.jpg" },

    // 3. Atıştırmalıklar (8 adet)
    { cat: "Atıştırmalıklar & Tostlar", name: "Üç Peynirli Bazlama Tost", desc: "Mozzarella, kolot ve kaşar peynirli bazlama tost", price: 175, allergens: ["Gluten", "Süt"], sort: 1, img: "images/cat_snacks.jpg" },
    { cat: "Atıştırmalıklar & Tostlar", name: "Mücver (Dip Soslu)", desc: "Kabak, havuç, soğan, dereotu, maydanoz, yoğurtlu dip sos", price: 150, allergens: ["Gluten", "Yumurta", "Süt"], sort: 2, img: "images/cat_snacks.jpg" },
    { cat: "Atıştırmalıklar & Tostlar", name: "Ispanaklı Börek", desc: "Ev yapımı çıtır yufka, taze yerli ıspanak", price: 60, allergens: ["Gluten", "Yumurta"], sort: 3, img: "images/cat_snacks.jpg" },
    { cat: "Atıştırmalıklar & Tostlar", name: "Peynirli Börek", desc: "Lor peyniri, beyaz peynir, Antep peyniri", price: 60, allergens: ["Gluten", "Süt", "Yumurta"], sort: 4, img: "images/cat_snacks.jpg" },
    { cat: "Atıştırmalıklar & Tostlar", name: "Patatesli Börek", desc: "Ev yapımı yufka, soğan, patates, baharat", price: 60, allergens: ["Gluten"], sort: 5, img: "images/cat_snacks.jpg" },
    { cat: "Atıştırmalıklar & Tostlar", name: "Dereotlu Poğaça", desc: "Dereotu, maydanoz, havuç, peynir — 110gr", price: 50, allergens: ["Gluten", "Süt", "Yumurta"], sort: 6, img: "images/cat_snacks.jpg" },
    { cat: "Atıştırmalıklar & Tostlar", name: "Yumurtalı Peynirli Ekmek", desc: "Ezine peyniri, yumurta, maydanoz, kekik (25dk hazırlanır)", price: 80, allergens: ["Gluten", "Süt", "Yumurta"], sort: 7, img: "images/cat_snacks.jpg" },
    { cat: "Atıştırmalıklar & Tostlar", name: "Sucuklu Kaşarlı Bazlama Tost", desc: "Kavrulmuş dana sucuk ve bol kaşar peyniri", price: 175, allergens: ["Gluten", "Süt"], sort: 8, img: "images/cat_snacks.jpg" },

    // 4. Tatlılar & Pastalar (11 adet)
    { cat: "Tatlılar & Pastalar", name: "San Sebastian Cheesecake", desc: "Özel yapım sıcak Belçika çikolata sosu ile taptaze fırından", price: 220, allergens: ["Süt", "Yumurta"], sort: 1, img: "images/cat_desserts.jpg" },
    { cat: "Tatlılar & Pastalar", name: "Amerikan Creamy Nemli Kek", desc: "Bol kakaolu yumuşak kek ve özel kakaolu kreması ile", price: 180, allergens: ["Gluten", "Süt", "Yumurta"], sort: 2, img: "images/cat_desserts.jpg" },
    { cat: "Tatlılar & Pastalar", name: "Tres Leches (Trileçe)", desc: "Mascarpone ve krema ile örtülmüş süt reçelli kek", price: 200, allergens: ["Gluten", "Süt", "Yumurta"], sort: 3, img: "images/cat_desserts.jpg" },
    { cat: "Tatlılar & Pastalar", name: "Cup Cakes", desc: "Limonlu, vanilyalı veya çikolatalı", price: 60, allergens: ["Gluten", "Süt", "Yumurta"], sort: 4, img: "images/cat_desserts.jpg" },
    { cat: "Tatlılar & Pastalar", name: "Çilekli Kap Pasta", desc: "Taze çilekler ve vanilyalı özel pasta kreması", price: 200, allergens: ["Gluten", "Süt", "Yumurta"], sort: 5, img: "images/cat_desserts.jpg" },
    { cat: "Tatlılar & Pastalar", name: "Supangle", desc: "Gerçek çikolata pralin ve taze süt ile", price: 190, allergens: ["Süt"], sort: 6, img: "images/cat_desserts.jpg" },
    { cat: "Tatlılar & Pastalar", name: "Tiramisu", desc: "Mascarpone ve krema ile hazırlanmış orijinal harç ve kedi dilleri", price: 200, allergens: ["Gluten", "Süt", "Yumurta"], sort: 7, img: "images/cat_desserts.jpg" },
    { cat: "Tatlılar & Pastalar", name: "Elmalı Kramble (Crumble)", desc: "Tarçınlı elma ve üst çıtır fırın örtüsü ile", price: 190, allergens: ["Gluten", "Süt"], sort: 8, img: "images/cat_desserts.jpg" },
    { cat: "Tatlılar & Pastalar", name: "Brownie", desc: "Bitter çikolata, tereyağı, ceviz ve fındık içi", price: 190, allergens: ["Gluten", "Süt", "Yumurta", "Kuruyemiş"], sort: 9, img: "images/cat_desserts.jpg" },
    { cat: "Tatlılar & Pastalar", name: "Çok Çikolatalı Kek", desc: "Çikolata, espresso kahve, tereyağı ve özel çikolata sosu ile", price: 180, allergens: ["Gluten", "Süt", "Yumurta"], sort: 10, img: "images/cat_desserts.jpg" },
    { cat: "Tatlılar & Pastalar", name: "Dilim Cheesecake", desc: "Frambuazlı, çikolatalı, limonlu ve vişneli seçenekleriyle", price: 120, allergens: ["Gluten", "Süt", "Yumurta"], sort: 11, img: "images/cat_desserts.jpg" },

    // 5. Spesiyeller & Sandviçler (2 adet)
    { cat: "Spesiyeller & Sandviçler", name: "Köfte Sandviç", desc: "Ciabatta ekmeği, çıtır dışı yumuşak içi, dana köfte, mozzarella peyniri, karamelize soğan, közlenmiş kapya biber, özel soslar", price: 260, allergens: ["Gluten", "Süt"], sort: 1, img: "images/cat_specials.jpg" },
    { cat: "Spesiyeller & Sandviçler", name: "Tavuk Sandviç", desc: "Ciabatta ekmeği, çıtır dışı yumuşak içi, özel marine ızgara tavuk, Akdeniz yeşillikleri, özel dükkan sosları", price: 260, allergens: ["Gluten", "Süt"], sort: 2, img: "images/cat_specials.jpg" }
  ];

  console.log(`Inserting ${allProducts.length} menu items...`);

  for (const p of allProducts) {
    const catId = createdCats[p.cat];
    if (!catId) continue;

    const allergenValues = p.allergens.map(a => ({ stringValue: a }));

    await fetch(`${baseUrl}/menuItems`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        fields: {
          categoryId: { stringValue: catId },
          name: { stringValue: p.name },
          description: { stringValue: p.desc },
          price: { doubleValue: p.price },
          imageUrl: { stringValue: p.img },
          allergens: { arrayValue: { values: allergenValues } },
          isAvailable: { booleanValue: true },
          sortOrder: { integerValue: p.sort.toString() }
        }
      })
    });
  }

  console.log("SUCCESSFULLY IMPORTED ALL CATEGORIES & PRODUCTS TO sadec-gerze!");
}

importFullMenu();
