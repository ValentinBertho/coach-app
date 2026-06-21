// Génère les icônes PWA + favicons à partir de branding/icon-master.svg
// Usage : node branding/generate-icons.mjs   (nécessite sharp en devDependency)
import sharp from 'sharp';
import { mkdirSync, writeFileSync, copyFileSync, readFileSync } from 'node:fs';
import { dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = dirname(fileURLToPath(import.meta.url)) + '/..';
const master = `${root}/branding/icon-master.svg`;
const iconsDir = `${root}/src/assets/icons`;
mkdirSync(iconsDir, { recursive: true });

const pwaSizes = [72, 96, 128, 144, 152, 192, 384, 512];
for (const size of pwaSizes) {
  await sharp(master).resize(size, size).png().toFile(`${iconsDir}/icon-${size}x${size}.png`);
}
// Apple touch icon + favicon PNG
await sharp(master).resize(180, 180).png().toFile(`${root}/src/apple-touch-icon.png`);
await sharp(master).resize(32, 32).png().toFile(`${root}/src/favicon-32.png`);
await sharp(master).resize(16, 16).png().toFile(`${root}/src/favicon-16.png`);

// favicon.svg (copie du master pour les navigateurs modernes)
copyFileSync(master, `${root}/src/favicon.svg`);
// favicon.ico ← PNG 32 (accepté par les navigateurs ; remplace l'icône générique)
copyFileSync(`${root}/src/favicon-32.png`, `${root}/src/favicon.ico`);

console.log('Icônes générées :', pwaSizes.map((s) => `${s}x${s}`).join(', '), '+ apple/favicons');
