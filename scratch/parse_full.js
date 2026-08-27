const fs = require('fs');

const html = fs.readFileSync('C:/Users/berka/OneDrive/Desktop/tasarımprojeleri/sadec/index.html', 'utf8');

// Parse slides
const slideRegex = /<section[^>]*class="[^"]*slide[^"]*"[^>]*id="([^"]+)"[^>]*>([\s\S]*?)<\/section>/gi;
const slides = [];

let match;
while ((match = slideRegex.exec(html)) !== null) {
  const slideId = match[1];
  const content = match[2];

  // Title
  const titleMatch = content.match(/<h1[^>]*class="slide__title"[^>]*>([\s\S]*?)<\/h1>/i);
  let categoryName = "Kategori";
  if (titleMatch) {
    categoryName = titleMatch[1].replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').trim();
  }

  // Background Image
  const imgMatch = content.match(/url\(&quot;([^&]+)&quot;\)/i) || content.match(/url\("([^"]+)"\)/i);
  const bgImg = imgMatch ? imgMatch[1] : "";

  // Items
  const items = [];
  const itemBlockRegex = /<div[^>]*class="anim-target"[^>]*>([\s\S]*?)<\/div>\s*<\/div>/gi;
  // Alternative item parser
  const nameRegex = /<span class="menu-item__name">([\s\S]*?)<\/span>/gi;
  const priceRegex = /<span class="menu-item__price">([0-9.,]+)/gi;
  const descRegex = /<div class="menu-item__desc">([\s\S]*?)<\/div>/gi;

  const rawItemRegex = /<div class="menu-item">[\s\S]*?<span class="menu-item__name">([\s\S]*?)<\/span>[\s\S]*?<span class="menu-item__price">([0-9.,]+)[\s\S]*?<\/div>[\s\S]*?<div class="menu-item__desc">([\s\S]*?)<\/div>/gi;

  let itemMatch;
  while ((itemMatch = rawItemRegex.exec(content)) !== null) {
    const name = itemMatch[1].replace(/<[^>]+>/g, '').trim();
    const price = parseFloat(itemMatch[2].replace(',', '.'));
    const desc = itemMatch[3].replace(/<[^>]+>/g, '').trim();

    // Determine allergens based on description or name
    const allergens = [];
    const lowerDesc = (name + " " + desc).toLowerCase();
    if (lowerDesc.includes("süt") || lowerDesc.includes("peynir") || lowerDesc.includes("mozzarella") || lowerDesc.includes("krema") || lowerDesc.includes("kaşar") || lowerDesc.includes("lor") || lowerDesc.includes("kolot") || lowerDesc.includes("ezine") || lowerDesc.includes("mascarpone") || lowerDesc.includes("latte") || lowerDesc.includes("cappucino") || lowerDesc.includes("macchiato") || lowerDesc.includes("cortado") || lowerDesc.includes("flat white") || lowerDesc.includes("frape") || lowerDesc.includes("milkshake") || lowerDesc.includes("çikolata")) {
      allergens.push("Süt");
    }
    if (lowerDesc.includes("espresso") || lowerDesc.includes("kahve") || lowerDesc.includes("americano") || lowerDesc.includes("mocha") || lowerDesc.includes("black eye") || lowerDesc.includes("v60") || lowerDesc.includes("türk kahvesi") || lowerDesc.includes("affogato") || lowerDesc.includes("cold brew")) {
      allergens.push("Kafein");
    }
    if (lowerDesc.includes("ekmek") || lowerDesc.includes("cibata") || lowerDesc.includes("ciabatta") || lowerDesc.includes("bazlama") || lowerDesc.includes("tost") || lowerDesc.includes("börek") || lowerDesc.includes("yufka") || lowerDesc.includes("poğaça") || lowerDesc.includes("kek") || lowerDesc.includes("pasta") || lowerDesc.includes("un") || lowerDesc.includes("trileçe") || lowerDesc.includes("cup cake") || lowerDesc.includes("tiramisu") || lowerDesc.includes("kramble") || lowerDesc.includes("crumble") || lowerDesc.includes("brownie") || lowerDesc.includes("cheesecake")) {
      allergens.push("Gluten");
    }
    if (lowerDesc.includes("yumurta") || lowerDesc.includes("mücver") || lowerDesc.includes("poğaça") || lowerDesc.includes("cheesecake") || lowerDesc.includes("trileçe") || lowerDesc.includes("cup cake") || lowerDesc.includes("tiramisu") || lowerDesc.includes("brownie") || lowerDesc.includes("kek") || lowerDesc.includes("pasta")) {
      allergens.push("Yumurta");
    }
    if (lowerDesc.includes("ceviz") || lowerDesc.includes("fındık") || lowerDesc.includes("antep")) {
      allergens.push("Kuruyemiş");
    }

    items.push({
      name: name,
      price: price,
      description: desc,
      allergens: [...new Set(allergens)]
    });
  }

  slides.push({
    slideId: slideId,
    categoryName: categoryName,
    bgImg: bgImg,
    items: items
  });
}

fs.writeFileSync('C:/Users/berka/AndroidStudioProjects/sadec/scratch/parsed_menu.json', JSON.stringify(slides, null, 2));
console.log("Parsed " + slides.length + " categories!");
slides.forEach(s => console.log(`Category: ${s.categoryName} (${s.items.length} items)`));
