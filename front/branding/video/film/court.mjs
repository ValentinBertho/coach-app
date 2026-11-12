/**
 * Version courte et verticale (1080 × 1920, ~30 s) pour les réseaux sociaux, tirée des mêmes
 * plans que montage.mjs :
 *   node court.mjs [plans] [sortie.mp4] [--musique piste]
 *
 * Les écrans d'ordinateur sont recadrés sur la zone utile (le texte d'un écran entier serait
 * illisible sur un téléphone), et chaque plan est raccourci et un peu accéléré.
 * Playwright : NODE_PATH vers un node_modules qui le contient.
 */
import { createRequire } from 'node:module';
import { spawnSync } from 'node:child_process';
import { mkdirSync, rmSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const require = createRequire(import.meta.url);
const { chromium } = require('playwright');

const here = dirname(fileURLToPath(import.meta.url));
const args = process.argv.slice(2);
const music = args.includes('--musique') ? args[args.indexOf('--musique') + 1] : null;
/** La musique commence là où la rythmique entre (mesure 8 à 110 BPM). */
const MUSIC_FROM = 17.45;
const pos = args.filter((a, i) => !a.startsWith('--') && args[i - 1] !== '--musique');
const PLANS = resolve(pos[0] ?? join(here, '..', '..', '..', 'dist', 'film'));
const OUT = resolve(pos[1] ?? join(here, '..', '..', '..', 'dist', 'presentation-courte.mp4'));
const WORK = OUT + '.court';
const W = 1080, H = 1920, FPS = 30, FADE = 0.4;

/**
 * `from`/`to` : secondes du plan d'origine ; `speed` : accélération ; `crop` : zone utile d'un
 * plan d'ordinateur (pixels du plan 1920 × 1080, au rapport de la fenêtre : 982 × 1080).
 */
const STORY = [
  { plan: 'coach-planifie', kind: 'ordinateur', from: 1.0, to: 8.5, speed: 1.4, crop: [380, 0, 982, 1080],
    kicker: 'Planifier', title: 'Glissez une séance,<br><em>les allures suivent.</em>' },
  { plan: 'athlete-retour', kind: 'telephone', from: 0.4, to: 10.0, speed: 1.5,
    kicker: 'Côté athlète', title: 'Son retour<br><em>en trente secondes.</em>' },
  { plan: 'coach-journee', kind: 'ordinateur', from: 4.0, to: 11.0, speed: 1.3, crop: [520, 0, 982, 1080],
    kicker: 'Votre journée', title: 'Qui a besoin de vous,<br><em>d’un coup d’œil.</em>' },
  { plan: 'athlete-matin', kind: 'telephone', from: 0.4, to: 6.8, speed: 1.3,
    kicker: 'Chaque matin', title: 'Sa forme<br><em>avant la séance.</em>' },
  { plan: 'annuaire', kind: 'ordinateur', from: 0.4, to: 10.5, speed: 1.5, crop: [460, 0, 982, 1080],
    kicker: 'L’annuaire', title: 'De nouveaux athlètes<br><em>vous trouvent.</em>' },
];

const WIN = { x: 40, y: 600, w: 1000, h: 1100, r: 22 };
const SCREEN = { x: 262, y: 560, w: 556, h: 1236, r: 46 };
const BEZEL = 14;

const FONTS = pathToFileURL(resolve(here, '..', '..', '..', 'src', 'assets', 'fonts')).href;
const CSS = `
  @font-face { font-family: 'Archivo'; src: url('${FONTS}/archivo-latin.woff2') format('woff2'); font-weight: 100 900; }
  @font-face { font-family: 'IBM Plex Sans'; src: url('${FONTS}/ibm-plex-sans-latin.woff2') format('woff2'); font-weight: 100 700; }
  html, body { margin: 0; width: ${W}px; height: ${H}px; overflow: hidden; position: relative; color: #eef6f6;
    font-family: 'IBM Plex Sans', sans-serif;
    background:
      radial-gradient(70% 50% at 10% 0%, rgba(18, 133, 145, 0.36) 0%, transparent 60%),
      radial-gradient(70% 50% at 100% 100%, rgba(47, 179, 192, 0.28) 0%, transparent 62%),
      linear-gradient(165deg, #0f2125 0%, #081316 78%); }
  em { font-style: normal; background: linear-gradient(120deg, #8fdbe3 0%, #2fb3c0 55%, #6ee7d0 100%);
    -webkit-background-clip: text; background-clip: text; color: transparent; }
  .kicker { font-family: 'Archivo'; font-weight: 700; letter-spacing: 0.08em; text-transform: uppercase; color: #2fb3c0; font-size: 34px; }
  .title { font-family: 'Archivo'; font-weight: 800; letter-spacing: -0.02em; font-size: 76px; line-height: 1.06; margin-top: 18px; }
  .cap { position: absolute; left: 70px; right: 70px; }
  .brand { display: flex; align-items: center; gap: 22px; font-family: 'Archivo'; font-weight: 800; font-size: 84px; }
  .brand span span { color: #2fb3c0; }
  .center { position: absolute; inset: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; text-align: center; padding: 0 70px; }
  .cta { margin-top: 70px; background: #128591; color: #fff; font-family: 'Archivo'; font-weight: 700; font-size: 44px;
    padding: 30px 60px; border-radius: 22px; box-shadow: 0 20px 60px rgba(18,133,145,0.45); }
  .url { margin-top: 40px; font-size: 40px; color: rgba(238,246,246,0.8); }
`;
const LOGO = `<svg width="110" height="110" viewBox="0 0 1024 1024" fill="none"><defs><linearGradient id="g" x1="0" y1="0" x2="1024" y2="1024" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="#12808B"/><stop offset="1" stop-color="#0A4F58"/></linearGradient></defs><rect width="1024" height="1024" rx="232" fill="url(#g)"/><path d="M250 720 C 420 700, 540 580, 770 360" stroke="#fff" stroke-width="76" stroke-linecap="round"/><path d="M250 560 L 360 560" stroke="#fff" stroke-width="40" stroke-linecap="round" opacity="0.55"/><path d="M250 640 L 320 640" stroke="#fff" stroke-width="40" stroke-linecap="round" opacity="0.35"/><circle cx="788" cy="338" r="86" fill="#16C47F" stroke="#fff" stroke-width="22"/></svg>`;
const BRAND = `<div class="brand">${LOGO}<span>Dari<span>lab</span></span></div>`;

function sh(a) {
  const r = spawnSync('ffmpeg', ['-y', '-loglevel', 'error', ...a], { stdio: ['ignore', 'inherit', 'inherit'] });
  if (r.status !== 0) throw new Error('ffmpeg a échoué');
}
function duration(file) {
  return parseFloat(spawnSync('ffprobe', ['-v', 'error', '-show_entries', 'format=duration', '-of', 'csv=p=0', file]).stdout.toString());
}

rmSync(WORK, { recursive: true, force: true });
mkdirSync(WORK, { recursive: true });
const browser = await chromium.launch({ args: ['--allow-file-access-from-files'] });
const page = await browser.newPage({ viewport: { width: W, height: H } });
async function still(file, body, { bare = false } = {}) {
  // Par un fichier : une page chargée par setContent ne peut pas lire les polices en file://.
  const html = file.replace(/\.png$/, '.html');
  writeFileSync(html, `<!doctype html><html><head><meta charset="utf-8"><style>${CSS}${bare ? 'html,body{background:#000}' : ''}</style></head><body>${body}</body></html>`);
  await page.goto(pathToFileURL(html).href);
  await page.evaluate(() => document.fonts.ready);
  await page.screenshot({ path: file });
}
const mask = async (file, w, h, r) => {
  await still(file, `<div style="position:absolute;left:0;top:0;width:${w}px;height:${h}px;border-radius:${r}px;background:#fff"></div>`, { bare: true });
  sh(['-i', file, '-vf', `crop=${w}:${h}:0:0,format=gray`, file.replace(/\.png$/, '-c.png')]);
  return file.replace(/\.png$/, '-c.png');
};
const maskWin = await mask(join(WORK, 'masque-ordinateur.png'), WIN.w, WIN.h, WIN.r);
const maskTel = await mask(join(WORK, 'masque-telephone.png'), SCREEN.w, SCREEN.h, SCREEN.r);

// Une carte fixe, avec un léger zoom avant pour qu'elle vive.
async function card(file, body, secs) {
  const png = file.replace(/\.mp4$/, '.png');
  await still(png, body);
  sh(['-loop', '1', '-i', png, '-filter_complex',
    `[0:v]scale=${W * 2}:${H * 2},zoompan=z='1+0.04*on/(${secs}*${FPS})':x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)':d=1:s=${W}x${H}:fps=${FPS},format=yuv420p[v]`,
    '-map', '[v]', '-t', String(secs), '-c:v', 'libx264', '-preset', 'veryfast', '-crf', '16', file]);
}

const segments = [];
const intro = join(WORK, '00-ouverture.mp4');
await card(intro, `<div class="center">${BRAND}<div class="title" style="margin-top:60px">Coachs de course à pied :<br><em>tout votre suivi, dans un seul outil.</em></div></div>`, 2.8);
segments.push(intro);

for (const [i, s] of STORY.entries()) {
  const out = join(WORK, `${String(i + 1).padStart(2, '0')}-${s.plan}.mp4`);
  const bg = join(WORK, `fond-${s.plan}.png`);
  const clip = join(PLANS, s.plan + '.mp4');
  const len = (s.to - s.from) / s.speed;
  const trim = `trim=${s.from}:${s.to},setpts=(PTS-STARTPTS)/${s.speed}`;
  if (s.kind === 'ordinateur') {
    await still(bg, `<div class="cap" style="top:250px"><div class="kicker">${s.kicker}</div><div class="title">${s.title}</div></div>
      <div style="position:absolute;left:${WIN.x}px;top:${WIN.y}px;width:${WIN.w}px;height:${WIN.h}px;border-radius:${WIN.r}px;background:#000;
        box-shadow:0 30px 90px rgba(0,0,0,.55),0 0 0 2px rgba(255,255,255,.08)"></div>`);
    const [x, y, w, h] = s.crop;
    sh(['-i', clip, '-loop', '1', '-i', bg, '-loop', '1', '-i', maskWin, '-filter_complex',
      `[0:v]${trim},crop=${w}:${h}:${x}:${y},scale=${WIN.w}:${WIN.h}:flags=lanczos,format=rgba[c];[2:v]format=gray[m];[c][m]alphamerge[cm];` +
      `[1:v][cm]overlay=${WIN.x}:${WIN.y},fps=${FPS},format=yuv420p[v]`,
      '-map', '[v]', '-t', len.toFixed(3), '-c:v', 'libx264', '-preset', 'veryfast', '-crf', '16', out]);
  } else {
    await still(bg, `<div class="cap" style="top:130px"><div class="kicker">${s.kicker}</div><div class="title">${s.title}</div></div>
      <div style="position:absolute;left:${SCREEN.x - BEZEL}px;top:${SCREEN.y - BEZEL}px;width:${SCREEN.w + 2 * BEZEL}px;height:${SCREEN.h + 2 * BEZEL}px;
        border-radius:${SCREEN.r + BEZEL}px;background:#000;box-shadow:0 40px 100px rgba(0,0,0,.6),0 0 0 2px rgba(255,255,255,.1)"></div>`);
    sh(['-i', clip, '-loop', '1', '-i', bg, '-loop', '1', '-i', maskTel, '-filter_complex',
      `[0:v]${trim},scale=${SCREEN.w}:${SCREEN.h}:flags=lanczos,format=rgba[c];[2:v]format=gray[m];[c][m]alphamerge[cm];` +
      `[1:v][cm]overlay=${SCREEN.x}:${SCREEN.y},fps=${FPS},format=yuv420p[v]`,
      '-map', '[v]', '-t', len.toFixed(3), '-c:v', 'libx264', '-preset', 'veryfast', '-crf', '16', out]);
  }
  segments.push(out);
  console.log(`plan : ${s.plan} (${duration(out).toFixed(1)} s)`);
}

const fin = join(WORK, '99-fin.mp4');
await card(fin, `<div class="center">${BRAND}<div class="title" style="margin-top:70px">Créez votre espace coach,<br><em>gratuitement.</em></div>
  <div class="cta">Créer mon espace</div><div class="url">darilab.app</div></div>`, 4.2);
segments.push(fin);
await browser.close();

const durs = segments.map(duration);
let chain = '', prev = '[0:v]', offset = 0;
for (let i = 1; i < segments.length; i++) {
  offset += durs[i - 1] - FADE;
  const label = i === segments.length - 1 ? '[v]' : `[x${i}]`;
  chain += `${prev}[${i}:v]xfade=transition=fade:duration=${FADE}:offset=${offset.toFixed(3)}${label};`;
  prev = label;
}
const total = offset + durs.at(-1);
const a = [...segments.flatMap((f) => ['-i', f])];
if (music) a.push('-ss', String(MUSIC_FROM), '-i', music);
a.push('-filter_complex', chain.slice(0, -1), '-map', '[v]');
if (music) {
  a.push('-map', `${segments.length}:a`, '-af', `afade=t=in:d=0.3,afade=t=out:st=${(total - 2).toFixed(2)}:d=2`,
    '-t', total.toFixed(3), '-c:a', 'aac', '-b:a', '160k');
}
a.push('-c:v', 'libx264', '-preset', 'slow', '-crf', '20', '-pix_fmt', 'yuv420p', '-movflags', '+faststart', OUT);
sh(a);
console.log(`Vidéo courte : ${OUT} (${total.toFixed(1)} s)`);
