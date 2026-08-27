const fs = require('fs');
const html = fs.readFileSync('C:/Users/berka/OneDrive/Desktop/tasarımprojeleri/sadec/index.html', 'utf8');

const sections = [...html.matchAll(/<section[^>]*id="([^"]+)"[^>]*>([\s\S]*?)<\/section>/gi)];
sections.forEach((s, i) => {
  const title = (s[2].match(/<h1[^>]*>([\s\S]*?)<\/h1>/i) || ['','No title'])[1].replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').trim();
  const names = [...s[2].matchAll(/<span class="menu-item__name">([\s\S]*?)<\/span>/gi)].map(m => m[1].trim());
  console.log(`[Slide ${i+1}] ID: ${s[1]} | Title: ${title} | Items: ${names.length}`);
  if (names.length > 0) console.log('   Items:', names.join(', '));
});
