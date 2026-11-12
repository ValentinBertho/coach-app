/**
 * Fabrique les guides illustrés en PDF à partir de docs/guides/*.md et des captures :
 *   NODE_PATH=../e2e/node_modules node branding/guides/generate-guides.mjs
 *
 * Sortie : src/guides/guide-coach.pdf et src/guides/guide-athlete.pdf, servis par le site en
 * /guides/ (hors /assets, comme la vidéo) et proposés par la page publique /aide. Ce sont aussi
 * les fichiers à joindre à une invitation.
 *
 * Les guides n'utilisent qu'un petit morceau de Markdown (titres, paragraphes, listes, images,
 * tableaux d'images, gras, code, liens, citation, séparateur) : le convertisseur ci-dessous s'en
 * tient là plutôt que d'ajouter une dépendance au front.
 */
import { createRequire } from 'node:module';
import { mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const require = createRequire(import.meta.url);
const { chromium } = require('playwright');

const here = dirname(fileURLToPath(import.meta.url));
const front = resolve(here, '..', '..');
const docs = resolve(front, '..', 'docs', 'guides');
const out = resolve(front, 'src', 'guides');
mkdirSync(out, { recursive: true });

const GUIDES = [
  { md: 'GUIDE-COACH.md', pdf: 'guide-coach.pdf', kicker: 'Guide du coach' },
  { md: 'GUIDE-ATHLETE.md', pdf: 'guide-athlete.pdf', kicker: 'Guide de l’athlète' },
];

const esc = (t) => t.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

function inline(t, base) {
  return esc(t)
    .replace(/!\[([^\]]*)\]\(([^)]+)\)/g, (_, alt, src) =>
      `<figure><img src="${pathToFileURL(resolve(base, src)).href}" alt="${alt}"><figcaption>${alt}</figcaption></figure>`)
    .replace(/\[([^\]]+)\]\(([^)]+)\)/g, '$1')
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/`([^`]+)`/g, '<code>$1</code>');
}

function toHtml(md, base) {
  const lines = md.split('\n');
  const html = [];
  let i = 0;
  while (i < lines.length) {
    const line = lines[i];
    if (!line.trim()) { i++; continue; }
    // La citation d'en-tête s'adresse aux développeurs (d'où viennent les images) : pas au PDF.
    if (line.startsWith('>')) { while (i < lines.length && lines[i].startsWith('>')) i++; continue; }
    if (line.startsWith('---')) { i++; continue; }
    const h = line.match(/^(#{1,3}) (.*)/);
    if (h) { html.push(`<h${h[1].length}>${inline(h[2], base)}</h${h[1].length}>`); i++; continue; }
    if (line.startsWith('|')) {
      const rows = [];
      while (i < lines.length && lines[i].startsWith('|')) { rows.push(lines[i]); i++; }
      const cells = (r) => r.split('|').slice(1, -1).map((c) => c.trim());
      const body = rows.slice(2).map(cells);
      html.push(`<div class="grid g${body[0].length}">${body.flat().map((c) => inline(c, base)).join('')}</div>`);
      continue;
    }
    if (/^(- |\d+\. )/.test(line)) {
      const ordered = /^\d+\. /.test(line);
      const items = [];
      while (i < lines.length && (/^(- |\d+\. )/.test(lines[i]) || /^ {2,}\S/.test(lines[i]))) {
        if (/^ {2,}\S/.test(lines[i])) items[items.length - 1] += ' ' + lines[i].trim();
        else items.push(lines[i].replace(/^(- |\d+\. )/, ''));
        i++;
      }
      const tag = ordered ? 'ol' : 'ul';
      html.push(`<${tag}>${items.map((it) => `<li>${inline(it, base)}</li>`).join('')}</${tag}>`);
      continue;
    }
    const para = [];
    while (i < lines.length && lines[i].trim() && !/^(#|>|\||- |\d+\. |---)/.test(lines[i])) { para.push(lines[i]); i++; }
    const text = para.join(' ');
    html.push(text.startsWith('![') ? inline(text, base) : `<p>${inline(text, base)}</p>`);
  }
  return html.join('\n');
}

const FONTS = pathToFileURL(resolve(front, 'src', 'assets', 'fonts')).href;
const CSS = `
  @font-face { font-family: 'Archivo'; src: url('${FONTS}/archivo-latin.woff2') format('woff2'); font-weight: 100 900; }
  @font-face { font-family: 'IBM Plex Sans'; src: url('${FONTS}/ibm-plex-sans-latin.woff2') format('woff2'); font-weight: 100 700; }
  @page { size: A4; margin: 16mm 16mm 18mm; }
  body { font-family: 'IBM Plex Sans', sans-serif; color: #1b2b2e; font-size: 10.5pt; line-height: 1.55; margin: 0; }
  .cover { height: 250mm; display: flex; flex-direction: column; justify-content: center; page-break-after: always;
    background: linear-gradient(165deg, #0f2125 0%, #081316 78%); color: #eef6f6; margin: -16mm -16mm 0; padding: 0 22mm; }
  .cover .kicker { font-family: 'Archivo'; font-weight: 700; letter-spacing: .08em; text-transform: uppercase; color: #2fb3c0; font-size: 13pt; }
  .cover h1 { font-family: 'Archivo'; font-weight: 800; font-size: 34pt; line-height: 1.1; margin: 10mm 0 6mm; color: #fff; }
  .cover p { font-size: 13pt; color: rgba(238,246,246,.75); max-width: 140mm; }
  .cover .brand { font-family: 'Archivo'; font-weight: 800; font-size: 20pt; margin-top: 18mm; }
  .cover .brand span { color: #2fb3c0; }
  h1 { display: none; }
  h2 { font-family: 'Archivo'; font-weight: 800; font-size: 18pt; color: #0a4f58; margin: 9mm 0 3mm; page-break-after: avoid; }
  h3 { font-family: 'Archivo'; font-weight: 700; font-size: 13pt; margin: 6mm 0 2mm; page-break-after: avoid; }
  h2 + h3 { margin-top: 2mm; }
  p, li { orphans: 3; widows: 3; }
  strong { color: #0a4f58; }
  code { font-size: 9.5pt; background: #eef4f5; padding: 0 3px; border-radius: 3px; }
  figure { margin: 3mm 0 4mm; page-break-inside: avoid; }
  figure img { width: 100%; border-radius: 6px; border: 1px solid #d6e1e3; display: block; }
  figcaption { font-size: 8.5pt; color: #6b7a7d; margin-top: 1.5mm; }
  .grid { display: grid; gap: 4mm; page-break-inside: avoid; }
  .grid.g2 { grid-template-columns: 1fr 1fr; }
  .grid.g4 { grid-template-columns: repeat(4, 1fr); }
  .grid figure { margin: 2mm 0; }
  .athlete figure:not(.grid figure) img { max-width: 70mm; }
`;

const browser = await chromium.launch({ args: ['--allow-file-access-from-files'] });
const page = await browser.newPage();
for (const g of GUIDES) {
  const md = readFileSync(resolve(docs, g.md), 'utf8');
  const title = md.match(/^# (.*)/m)[1];
  // Le chapeau : premier paragraphe ordinaire (lignes jointes jusqu'à la ligne vide).
  const intro = md.split(/\n\s*\n/).map((p) => p.trim())
    .find((p) => p && !/^(#|>|\||!\[|---)/.test(p))?.replace(/\n/g, ' ') ?? '';
  const body = toHtml(md, docs);
  // Le premier paragraphe sert déjà de chapeau à la couverture.
  const rest = body.replace(`<p>${inline(intro, docs)}</p>`, '');
  // Passer par un fichier : une page chargée par setContent n'a pas le droit de lire file://
  // (captures, polices).
  const tmp = resolve(docs, `.${g.pdf}.html`);
  writeFileSync(tmp, `<!doctype html><html lang="fr"><head><meta charset="utf-8"><style>${CSS}</style></head>
    <body class="${g.md.includes('ATHLETE') ? 'athlete' : 'coach'}">
    <section class="cover"><div class="kicker">${g.kicker}</div><h1 style="display:block">${esc(title.replace('Guide du coach', 'Le coaching de course à pied, dans un seul outil').replace('Guide de l\'athlète', 'Ton entraînement, sur ton téléphone'))}</h1>
      <p>${inline(intro, docs)}</p><div class="brand">Dari<span>lab</span></div></section>
    ${rest}</body></html>`);
  await page.goto(pathToFileURL(tmp).href, { waitUntil: 'load' });
  rmSync(tmp);
  await page.evaluate(async () => {
    await document.fonts.ready;
    await Promise.all([...document.images].map((i) => i.decode().catch(() => undefined)));
  });
  await page.pdf({ path: resolve(out, g.pdf), format: 'A4', printBackground: true,
    displayHeaderFooter: true, headerTemplate: '<span></span>',
    footerTemplate: `<div style="font-family: sans-serif; font-size: 8px; color: #8a9799; width: 100%; padding: 0 16mm; display: flex; justify-content: space-between">
      <span>DARI Lab · ${g.kicker}</span><span class="pageNumber"></span></div>`,
    margin: { top: '16mm', bottom: '18mm', left: '16mm', right: '16mm' } });
  console.log(`PDF : ${resolve(out, g.pdf)}`);
}
await browser.close();
