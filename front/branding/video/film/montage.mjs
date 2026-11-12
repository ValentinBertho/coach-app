/**
 * Monte la vidéo de présentation à partir des plans filmés par shoot.mjs :
 *   node montage.mjs [plans] [sortie.mp4] [--musique piste.mp3]
 *
 * - ouverture et fin : les deux cartes animées de ../presentation.html ;
 * - plans ordinateur : l'écran dans une fenêtre aux coins arrondis, titre au-dessus ;
 * - plans téléphone : le téléphone à droite, le texte à gauche ;
 * - fondus enchaînés d'une demi-seconde entre les plans.
 *
 * Les décors (fonds, titres, masques) sont dessinés en HTML et photographiés par Playwright,
 * ffmpeg fait le reste. Playwright : NODE_PATH vers un node_modules qui le contient.
 */
import { createRequire } from 'node:module';
import { spawn, spawnSync } from 'node:child_process';
import { mkdirSync, rmSync, writeFileSync, existsSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const require = createRequire(import.meta.url);
const { chromium } = require('playwright');

const here = dirname(fileURLToPath(import.meta.url));
const args = process.argv.slice(2);
const music = args.includes('--musique') ? args[args.indexOf('--musique') + 1] : null;
const pos = args.filter((a, i) => !a.startsWith('--') && args[i - 1] !== '--musique');
const PLANS = resolve(pos[0] ?? join(here, '..', '..', '..', 'dist', 'film'));
const OUT = resolve(pos[1] ?? join(here, '..', '..', '..', 'dist', 'presentation-coachs.mp4'));
const WORK = OUT + '.montage';
const FPS = 30;
const FADE = 0.5;
/** Les plans ordinateur passent un peu plus vite que nature : le montage reste vif. */
const SPEED_DESKTOP = 1.2;

/** Le fil de la vidéo. `kind` : ordinateur ou telephone. */
const STORY = [
  { plan: 'coach-journee', kind: 'ordinateur', kicker: 'Votre journée',
    title: 'Voyez d’un coup d’œil <em>qui a besoin de vous.</em>' },
  { plan: 'coach-planifie', kind: 'ordinateur', kicker: 'La planification',
    title: 'Glissez une séance type <em>sur le calendrier.</em>' },
  { plan: 'coach-seance', kind: 'ordinateur', kicker: 'Vos séances types',
    title: 'Construisez vos séances <em>bloc par bloc.</em>' },
  { plan: 'coach-charge', kind: 'ordinateur', kicker: 'La charge',
    title: 'Voyez venir la fatigue <em>avant la blessure.</em>' },
  { plan: 'athlete-matin', kind: 'telephone', kicker: 'Côté athlète',
    title: 'Chaque matin, <em>sa forme en trois gestes.</em>',
    sub: 'Fatigue, douleur, sommeil : vous le savez avant la séance.' },
  { plan: 'athlete-retour', kind: 'telephone', kicker: 'Après la séance',
    title: 'Son retour <em>en trente secondes.</em>',
    sub: 'Sensation, effort, un mot pour vous. Il arrive aussitôt dans votre journée.' },
  { plan: 'athlete-mois', kind: 'telephone', kicker: 'Son calendrier',
    title: 'Tout son programme <em>dans la poche.</em>',
    sub: 'Chaque séance avec ses allures, calculées pour lui.' },
  { plan: 'coach-messages', kind: 'ordinateur', kicker: 'La messagerie',
    title: 'Une conversation <em>par athlète.</em>' },
  { plan: 'annuaire', kind: 'ordinateur', kicker: 'L’annuaire',
    title: 'De nouveaux athlètes <em>vous trouvent.</em>' },
  { plan: 'coach-demande', kind: 'ordinateur', kicker: 'Les demandes',
    title: 'Une demande arrive ? <em>Acceptez en un clic.</em>' },
];

// Géométrie (en pixels de la vidéo 1920×1080).
const WIN = { x: 160, y: 128, w: 1600, h: 900, r: 18 };          // fenêtre ordinateur (16:9)
const SCREEN = { x: 1196, y: 72, w: 423, h: 940, r: 40 };       // écran du téléphone
const BEZEL = 13;

const FONTS = pathToFileURL(resolve(here, '..', '..', '..', 'src', 'assets', 'fonts')).href;
const CSS = `
  @font-face { font-family: 'Archivo'; src: url('${FONTS}/archivo-latin.woff2') format('woff2'); font-weight: 100 900; }
  @font-face { font-family: 'IBM Plex Sans'; src: url('${FONTS}/ibm-plex-sans-latin.woff2') format('woff2'); font-weight: 100 700; }
  html, body { margin: 0; width: 1920px; height: 1080px; overflow: hidden; position: relative; }
  .bg { position: absolute; inset: 0;
    background:
      radial-gradient(60% 70% at 12% 0%, rgba(18, 133, 145, 0.32) 0%, transparent 60%),
      radial-gradient(55% 70% at 100% 100%, rgba(47, 179, 192, 0.26) 0%, transparent 62%),
      linear-gradient(165deg, #0f2125 0%, #081316 78%); }
  body { color: #eef6f6; font-family: 'IBM Plex Sans', sans-serif; }
  em { font-style: normal; background: linear-gradient(120deg, #8fdbe3 0%, #2fb3c0 55%, #6ee7d0 100%);
    -webkit-background-clip: text; background-clip: text; color: transparent; }
  .kicker { font-family: 'Archivo', sans-serif; font-weight: 700; letter-spacing: 0.08em; text-transform: uppercase; color: #2fb3c0; }
  .title { font-family: 'Archivo', sans-serif; font-weight: 800; letter-spacing: -0.02em; }
`;

function sh(cmd, a) {
  const r = spawnSync(cmd, a, { stdio: ['ignore', 'inherit', 'inherit'] });
  if (r.status !== 0) throw new Error(`${cmd} a échoué`);
}
function duration(file) {
  const r = spawnSync('ffprobe', ['-v', 'error', '-show_entries', 'format=duration', '-of', 'csv=p=0', file]);
  return parseFloat(r.stdout.toString());
}

rmSync(WORK, { recursive: true, force: true });
mkdirSync(WORK, { recursive: true });
for (const s of STORY) {
  if (!existsSync(join(PLANS, s.plan + '.mp4'))) throw new Error(`plan manquant : ${s.plan}.mp4 (shoot.mjs)`);
}

const browser = await chromium.launch({ args: ['--allow-file-access-from-files'] });
const page = await browser.newPage({ viewport: { width: 1920, height: 1080 } });

/** Photographie un décor HTML ; `transparent` : sans fond. */
async function still(file, body, { transparent = false } = {}) {
  await page.setContent(`<!doctype html><html><head><meta charset="utf-8"><style>${CSS}</style></head><body${transparent ? ' style="background:transparent"' : ''}>${body}</body></html>`);
  await page.evaluate(() => document.fonts.ready);
  await page.screenshot({ path: file, omitBackground: transparent });
}

// 1. Les décors communs : fonds avec l'ombre de la fenêtre ou du téléphone, masques arrondis.
await still(join(WORK, 'fond-ordinateur.png'), `<div class="bg"></div>
  <div style="position:absolute; left:${WIN.x}px; top:${WIN.y}px; width:${WIN.w}px; height:${WIN.h}px;
    border-radius:${WIN.r}px; background:#000; box-shadow: 0 30px 90px rgba(0,0,0,0.55), 0 0 0 2px rgba(255,255,255,0.08)"></div>`);
await still(join(WORK, 'masque-ordinateur.png'),
  `<div style="position:absolute; inset:0; background:#000"></div>
   <div style="position:absolute; left:0; top:0; width:${WIN.w}px; height:${WIN.h}px; border-radius:${WIN.r}px; background:#fff"></div>`);
await still(join(WORK, 'masque-telephone.png'),
  `<div style="position:absolute; inset:0; background:#000"></div>
   <div style="position:absolute; left:0; top:0; width:${SCREEN.w}px; height:${SCREEN.h}px; border-radius:${SCREEN.r}px; background:#fff"></div>`);
sh('ffmpeg', ['-y', '-loglevel', 'error', '-i', join(WORK, 'masque-ordinateur.png'),
  '-vf', `crop=${WIN.w}:${WIN.h}:0:0,format=gray`, join(WORK, 'masque-ordinateur-c.png')]);
sh('ffmpeg', ['-y', '-loglevel', 'error', '-i', join(WORK, 'masque-telephone.png'),
  '-vf', `crop=${SCREEN.w}:${SCREEN.h}:0:0,format=gray`, join(WORK, 'masque-telephone-c.png')]);

// 2. Les deux cartes animées de presentation.html (première et dernière scène).
async function card(file, which) {
  await page.goto(pathToFileURL(resolve(here, '..', 'presentation.html')).href);
  await page.evaluate(async () => {
    await document.fonts.ready;
    document.querySelector('.progress')?.remove();
  });
  // SCENES est une constante du script de la page : visible par son nom, pas sur window.
  const [from, to] = await page.evaluate((w) => {
    const s = w === 'first' ? SCENES[0] : SCENES.at(-1); // eslint-disable-line no-undef
    return [s.start, s.start + s.dur];
  }, which);
  const ff = spawn('ffmpeg', ['-y', '-loglevel', 'error', '-f', 'image2pipe', '-framerate', String(FPS), '-i', '-',
    '-c:v', 'libx264', '-preset', 'veryfast', '-crf', '14', '-pix_fmt', 'yuv420p', file], { stdio: ['pipe', 'inherit', 'inherit'] });
  for (let t = from; t < to - 1e-6; t += 1 / FPS) {
    // Petit décalage : la scène voisine n'apparaît pas dans la dernière image.
    await page.evaluate((x) => window.render(x), Math.min(t, to - 0.05));
    const buf = await page.screenshot({ type: 'jpeg', quality: 92 });
    if (!ff.stdin.write(buf)) await new Promise((r) => ff.stdin.once('drain', r));
  }
  ff.stdin.end();
  await new Promise((r) => ff.on('close', r));
}

// 3. Chaque plan filmé, habillé.
const segments = [join(WORK, '00-ouverture.mp4')];
await card(segments[0], 'first');

for (const [i, s] of STORY.entries()) {
  const out = join(WORK, `${String(i + 1).padStart(2, '0')}-${s.plan}.mp4`);
  const titre = join(WORK, `titre-${s.plan}.png`);
  const clip = join(PLANS, s.plan + '.mp4');
  if (s.kind === 'ordinateur') {
    await still(titre, `<div style="position:absolute; left:${WIN.x}px; top:34px; display:flex; align-items:baseline; gap:26px">
      <span class="kicker" style="font-size:24px">${s.kicker}</span>
      <span class="title" style="font-size:46px">${s.title}</span></div>`, { transparent: true });
    sh('ffmpeg', ['-y', '-loglevel', 'error',
      '-i', clip, '-loop', '1', '-i', join(WORK, 'fond-ordinateur.png'),
      '-loop', '1', '-i', join(WORK, 'masque-ordinateur-c.png'), '-loop', '1', '-i', titre,
      '-filter_complex',
      `[0:v]setpts=PTS/${SPEED_DESKTOP},scale=${WIN.w}:${WIN.h}:flags=lanczos,format=rgba[c];` +
      `[2:v]format=gray[m];[c][m]alphamerge[cm];` +
      `[1:v][cm]overlay=${WIN.x}:${WIN.y}:shortest=1[b];` +
      `[3:v]format=rgba,fade=t=in:st=0.15:d=0.5:alpha=1[t];[b][t]overlay=0:0:shortest=1,fps=${FPS},format=yuv420p[v]`,
      '-map', '[v]', '-t', (duration(clip) / SPEED_DESKTOP).toFixed(3),
      '-c:v', 'libx264', '-preset', 'veryfast', '-crf', '14', out]);
  } else {
    await still(titre, `<div class="bg"></div>
      <div style="position:absolute; left:${SCREEN.x - BEZEL}px; top:${SCREEN.y - BEZEL}px; width:${SCREEN.w + 2 * BEZEL}px;
        height:${SCREEN.h + 2 * BEZEL}px; border-radius:${SCREEN.r + BEZEL}px; background:#000;
        box-shadow: 0 40px 100px rgba(0,0,0,0.6), 0 0 0 2px rgba(255,255,255,0.10)"></div>
      <div style="position:absolute; left:200px; top:0; bottom:0; width:820px; display:flex; flex-direction:column; justify-content:center">
        <div class="kicker" style="font-size:28px">${s.kicker}</div>
        <div class="title" style="font-size:72px; line-height:1.08; margin-top:22px">${s.title}</div>
        <div style="margin-top:30px; font-size:32px; line-height:1.4; color:rgba(238,246,246,0.72)">${s.sub}</div></div>`);
    sh('ffmpeg', ['-y', '-loglevel', 'error',
      '-i', clip, '-loop', '1', '-i', titre, '-loop', '1', '-i', join(WORK, 'masque-telephone-c.png'),
      '-filter_complex',
      `[0:v]scale=${SCREEN.w}:${SCREEN.h}:flags=lanczos,format=rgba[c];[2:v]format=gray[m];[c][m]alphamerge[cm];` +
      `[1:v][cm]overlay=${SCREEN.x}:${SCREEN.y}:shortest=1,fps=${FPS},format=yuv420p[v]`,
      '-map', '[v]', '-t', duration(clip).toFixed(3),
      '-c:v', 'libx264', '-preset', 'veryfast', '-crf', '14', out]);
  }
  segments.push(out);
  console.log(`habillé : ${s.plan} (${duration(out).toFixed(1)} s)`);
}

const fin = join(WORK, '99-fin.mp4');
await card(fin, 'last');
segments.push(fin);
await browser.close();

// 4. Fondus enchaînés.
const durs = segments.map(duration);
const inputs = segments.flatMap((f) => ['-i', f]);
let chain = '';
let prev = '[0:v]';
let offset = 0;
for (let i = 1; i < segments.length; i++) {
  offset += durs[i - 1] - FADE;
  const label = i === segments.length - 1 ? '[v]' : `[x${i}]`;
  chain += `${prev}[${i}:v]xfade=transition=fade:duration=${FADE}:offset=${offset.toFixed(3)}${label};`;
  prev = label;
}
const total = offset + durs.at(-1);
const ffArgs = ['-y', '-loglevel', 'error', ...inputs];
if (music) ffArgs.push('-i', music);
ffArgs.push('-filter_complex', chain.slice(0, -1), '-map', '[v]');
if (music) {
  ffArgs.push('-map', `${segments.length}:a`, '-af', `afade=t=out:st=${(total - 2.5).toFixed(2)}:d=2.5`, '-shortest',
    '-c:a', 'aac', '-b:a', '192k');
}
ffArgs.push('-c:v', 'libx264', '-preset', 'slow', '-crf', '18', '-pix_fmt', 'yuv420p', '-movflags', '+faststart', OUT);
sh('ffmpeg', ffArgs);
writeFileSync(join(WORK, 'segments.txt'), segments.map((f, i) => `${f} ${durs[i].toFixed(2)}`).join('\n'));
console.log(`Vidéo : ${OUT} (${total.toFixed(1)} s)`);
