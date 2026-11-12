// Génère src/assets/og-image.png (1200×630) à partir de branding/og-image.html.
// Usage : node branding/generate-og-image.mjs
// Nécessite Playwright et son Chromium, par exemple ceux du dossier e2e :
//   NODE_PATH=../e2e/node_modules node branding/generate-og-image.mjs
//
// Une page HTML plutôt qu'un SVG passé à sharp : elle utilise les vraies polices de
// l'application (Archivo, IBM Plex Sans), là où sharp retombe sur une police système.
import { createRequire } from 'node:module';
import { dirname } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

// require() et non import : il suit NODE_PATH, ce qui évite d'ajouter Playwright au front.
const { chromium } = createRequire(import.meta.url)('playwright');
const root = dirname(fileURLToPath(import.meta.url)) + '/..';
const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1200, height: 630 }, deviceScaleFactor: 1 });
await page.goto(pathToFileURL(`${root}/branding/og-image.html`).href);
await page.evaluate(() => document.fonts.ready);
await page.screenshot({ path: `${root}/src/assets/og-image.png` });
await browser.close();
console.log('og-image.png générée (1200×630)');
