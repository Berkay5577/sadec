const fs = require('fs');

const html = fs.readFileSync('C:/Users/berka/OneDrive/Desktop/tasarımprojeleri/sadec/index.html', 'utf8');

// Regex for categories / headers / menu items
const lines = html.split('\n');
const parsed = [];
let currentCategory = "Genel";

for (let i = 0; i < lines.length; i++) {
  const line = lines[i];
  
  // Check for category title
  if (line.includes('class="category-title"') || line.includes('class="menu-category"') || line.includes('class="section-title"') || (line.includes('<h2') && line.includes('class="title"'))) {
    const text = line.replace(/<[^>]+>/g, '').trim();
    if (text) {
      currentCategory = text;
      console.log("\n=== CATEGORY:", currentCategory);
    }
  }

  // Check for product names and prices
  if (line.includes('class="item-name"') || line.includes('class="product-name"') || line.includes('class="menu-item-title"') || line.includes('class="item-title"')) {
    const name = line.replace(/<[^>]+>/g, '').trim();
    let price = "";
    let desc = "";
    // look ahead for price and desc
    for (let j = i; j < Math.min(i + 10, lines.length); j++) {
      if (lines[j].includes('class="price"') || lines[j].includes('class="item-price"')) {
        price = lines[j].replace(/<[^>]+>/g, '').trim();
      }
      if (lines[j].includes('class="desc"') || lines[j].includes('class="item-desc"') || lines[j].includes('class="description"')) {
        desc = lines[j].replace(/<[^>]+>/g, '').trim();
      }
    }
    console.log(`- [${currentCategory}] ${name} | ${price} | ${desc}`);
  }
}
