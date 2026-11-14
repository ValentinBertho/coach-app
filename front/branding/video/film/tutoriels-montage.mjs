/**
 * Monte les deux tutoriels de prise en main à partir des plans de tutoriels-tournage.mjs :
 *   node tutoriels-montage.mjs coach   [plans] [sortie.mp4] [--musique]
 *   node tutoriels-montage.mjs athlete [plans] [sortie.mp4] [--musique]
 *
 * - coach : 1920 × 1080, l'écran d'ordinateur dans une fenêtre, l'étape et son titre au-dessus,
 *   une phrase d'explication dessous ;
 * - athlète : 1080 × 1920 (vertical, regardé sur téléphone), le téléphone en grand sous le texte ;
 * - une barre d'étapes en bas situe chaque plan dans le tutoriel ;
 * - `--musique` : compose la musique du projet (../musique/compose.py, synthétisée, libre de
 *   droits) à la durée exacte de la vidéo, plus discrète que dans la vidéo de présentation.
 *
 * Les décors sont dessinés en HTML et photographiés par Playwright, ffmpeg fait le reste.
 * Playwright : NODE_PATH vers un node_modules qui le contient.
 */
import { createRequire } from 'node:module';
import { spawnSync } from 'node:child_process';
import { mkdirSync, rmSync, writeFileSync, existsSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const require = createRequire(import.meta.url);
const { chromium } = require('playwright');

const here = dirname(fileURLToPath(import.meta.url));
const args = process.argv.slice(2);
const WHO = args[0];
if (WHO !== 'coach' && WHO !== 'athlete') throw new Error('usage : tutoriels-montage.mjs coach|athlete [plans] [sortie.mp4] [--musique]');
const music = args.includes('--musique');
const pos = args.slice(1).filter((a) => !a.startsWith('--'));
const PLANS = resolve(pos[0] ?? '/tmp/darilab-tutoriels');
const OUT = resolve(pos[1] ?? `/tmp/tutoriel-${WHO}.mp4`);
const WORK = OUT + '.montage';
const FPS = 30, FADE = 0.45;

const COACH = {
  W: 1920, H: 1080, speed: 1.25,
  intro: { kicker: 'Tutoriel · Coach', title: 'Tout ce que vous pouvez faire<br><em>avec Darilab.</em>',
    sub: 'Quatorze étapes, de la journée du coach à l’annuaire. Démonstration : Claire Vasseur, Running Club Lyon.' },
  outro: { title: 'À vous de jouer.', sub: 'Le guide complet est dans l’aide de l’application, ou sur darilab.app/aide.' },
  story: [
    { plan: 'tc-tableau', kicker: 'Tableau de bord', title: 'Qui a besoin de vous <em>aujourd’hui.</em>',
      sub: 'Fatigue, douleur, silence : les athlètes à surveiller remontent en haut.' },
    { plan: 'tc-journee', kicker: 'Ma journée', title: 'Traitez les retours <em>un par un.</em>',
      sub: 'Ouvrir la séance, répondre, ou un simple « Vu » pour dire à l’athlète que vous avez lu.' },
    { plan: 'tc-athletes', kicker: 'Vos athlètes', title: 'Ajoutez un athlète, <em>puis invitez-le.</em>',
      sub: 'Il reçoit un lien par e-mail et rejoint votre espace depuis son téléphone.' },
    { plan: 'tc-fiche', kicker: 'La fiche athlète', title: 'Son profil physiologique, <em>ses allures.</em>',
      sub: 'VDOT, seuils, fréquences cardiaques : les allures d’entraînement en découlent.' },
    { plan: 'tc-zones', kicker: 'Zones et records', title: 'Un nouveau chrono <em>recalcule tout.</em>',
      sub: 'Records, allures d’équivalence et zones se mettent à jour à chaque test.' },
    { plan: 'tc-bibliotheque', kicker: 'Bibliothèque', title: 'Vos séances types, <em>en fourchettes.</em>',
      sub: 'Prescrivez en % du seuil ou de la VMA : chaque athlète reçoit ses propres allures.' },
    { plan: 'tc-prepa', kicker: 'Prépa physique et éducatifs', title: 'Renforcement et gammes, <em>au même endroit.</em>',
      sub: 'Exercices avec matériel, vidéo et progression, à planifier comme une séance de course.' },
    { plan: 'tc-programme', kicker: 'Planifier', title: 'Glissez une séance <em>sur le calendrier.</em>',
      sub: 'Dupliquez une semaine d’un clic droit. Garmin et COROS (en bêta) envoient la séance sur la montre.' },
    { plan: 'tc-groupe', kicker: 'Vue groupe', title: 'Une séance pour le groupe, <em>des allures pour chacun.</em>',
      sub: 'Déposez-la sur « Tout le groupe » : chaque athlète la reçoit, calculée pour lui.' },
    { plan: 'tc-adapter', kicker: 'Adapter', title: 'Ajustez la séance <em>d’un seul athlète.</em>',
      sub: 'Structure, notes, effort attendu : seule cette séance change, le modèle reste intact.' },
    { plan: 'tc-charge', kicker: 'Charge', title: 'Voyez venir la fatigue <em>avant la blessure.</em>',
      sub: 'Douze semaines de charge et le ratio aigu / chronique (ACWR).' },
    { plan: 'tc-messages', kicker: 'Messagerie', title: 'Une conversation <em>par athlète.</em>',
      sub: 'Plus un fil par groupe et un fil pour les annonces du club.' },
    { plan: 'tc-vitrine', kicker: 'Ma fiche publique', title: 'Présentez-vous <em>dans l’annuaire.</em>',
      sub: 'Approche, spécialités, tarifs, diplômes : la fiche est relue avant publication.' },
    { plan: 'tc-demande', kicker: 'Demandes de coaching', title: 'Un athlète vous écrit ? <em>Acceptez en un clic.</em>',
      sub: 'Il entre dans votre espace, prêt à être planifié. Vos coordonnées restent privées.' },
  ],
};

const ATHLETE = {
  W: 1080, H: 1920, speed: 1,
  intro: { kicker: 'Tutoriel · Athlète', title: 'Ton entraînement,<br><em>dans ta poche.</em>',
    sub: 'Pour l’installer : dans le navigateur du téléphone, « Ajouter à l’écran d’accueil ».' },
  outro: { title: 'À toi de courir.', sub: 'Le guide complet : Profil, puis Aide & guide.' },
  story: [
    { plan: 'ta-matin', kicker: 'Chaque matin', title: 'Ta forme<br><em>en dix secondes.</em>',
      sub: 'Sommeil, fatigue, douleur : ton coach le sait avant la séance.' },
    { plan: 'ta-seance', kicker: 'Aujourd’hui', title: 'Ta séance du jour,<br><em>à tes allures.</em>',
      sub: 'Chaque bloc avec sa zone, calculée pour toi.' },
    { plan: 'ta-retour', kicker: 'Après la séance', title: 'Ton retour<br><em>en trente secondes.</em>',
      sub: 'Sensation, effort, un mot pour ton coach.' },
    { plan: 'ta-mois', kicker: 'Mon mois', title: 'Ton programme,<br><em>prévu et réalisé.</em>',
      sub: 'Touche un jour pour l’ouvrir, ou déplace une séance.' },
    { plan: 'ta-messages', kicker: 'Messages', title: 'Écris à ton coach<br><em>à tout moment.</em>',
      sub: 'Et suis les fils de ton groupe et de ton club.' },
    { plan: 'ta-progres', kicker: 'Mes progrès', title: 'Ta charge, tes zones,<br><em>tes documents.</em>',
      sub: 'Historique, activités, programme et bilan à télécharger.' },
    { plan: 'ta-chrono', kicker: 'Mes performances', title: 'Un nouveau chrono,<br><em>de nouvelles allures.</em>',
      sub: 'Ton VDOT et tes zones se recalculent.' },
    { plan: 'ta-objectifs', kicker: 'Mes objectifs', title: 'Tes courses cibles,<br><em>priorité A, B ou C.</em>',
      sub: 'Le compte à rebours s’affiche sur ta page du jour.' },
    { plan: 'ta-montre', kicker: 'Ta montre', title: 'Relie Strava,<br><em>Garmin ou COROS.</em>',
      sub: 'Tes sorties arrivent seules. Garmin et COROS (en bêta) reçoivent aussi tes séances.' },
    { plan: 'ta-aide', kicker: 'Besoin d’aide ?', title: 'Le guide,<br><em>toujours sous la main.</em>',
      sub: 'Profil, puis Aide & guide : chaque écran expliqué.' },
  ],
};

const T = WHO === 'coach' ? COACH : ATHLETE;
const { W, H } = T;
// Coach : fenêtre 16:9 au centre. Athlète : écran du téléphone (rapport du Pixel 7 filmé).
const WIN = { x: 232, y: 140, w: 1456, h: 819, r: 18 };
const SCREEN = { x: 256, y: 590, w: 567, h: 1260, r: 44 };
const BEZEL = 14;

const FONTS = pathToFileURL(resolve(here, '..', '..', '..', 'src', 'assets', 'fonts')).href;
const CSS = `
  @font-face { font-family: 'Archivo'; src: url('${FONTS}/archivo-latin.woff2') format('woff2'); font-weight: 100 900; }
  @font-face { font-family: 'IBM Plex Sans'; src: url('${FONTS}/ibm-plex-sans-latin.woff2') format('woff2'); font-weight: 100 700; }
  html, body { margin: 0; width: ${W}px; height: ${H}px; overflow: hidden; position: relative; color: #eef6f6;
    font-family: 'IBM Plex Sans', sans-serif;
    background:
      radial-gradient(60% 60% at 10% 0%, rgba(18, 133, 145, 0.34) 0%, transparent 60%),
      radial-gradient(60% 60% at 100% 100%, rgba(47, 179, 192, 0.26) 0%, transparent 62%),
      linear-gradient(165deg, #0f2125 0%, #081316 78%); }
  em { font-style: normal; background: linear-gradient(120deg, #8fdbe3 0%, #2fb3c0 55%, #6ee7d0 100%);
    -webkit-background-clip: text; background-clip: text; color: transparent; }
  .kicker { font-family: 'Archivo'; font-weight: 700; letter-spacing: 0.08em; text-transform: uppercase; color: #2fb3c0; }
  .title { font-family: 'Archivo'; font-weight: 800; letter-spacing: -0.02em; }
  .sub { color: rgba(238, 246, 246, 0.78); line-height: 1.4; }
  .step { display: inline-flex; align-items: center; justify-content: center; border-radius: 999px;
    background: #128591; color: #fff; font-family: 'Archivo'; font-weight: 800; }
  .brand { display: flex; align-items: center; gap: 22px; font-family: 'Archivo'; font-weight: 800; }
  .brand span span { color: #2fb3c0; }
  .center { position: absolute; inset: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; text-align: center; padding: 0 90px; }
  .bar { position: absolute; display: flex; gap: 8px; }
  .bar i { flex: 1; height: 6px; border-radius: 3px; background: rgba(238, 246, 246, 0.16); }
  .bar i.done { background: rgba(47, 179, 192, 0.55); }
  .bar i.on { background: #2fb3c0; }
`;
const LOGO = (s) => `<svg width="${s}" height="${s}" viewBox="0 0 1024 1024" fill="none"><defs><linearGradient id="g" x1="0" y1="0" x2="1024" y2="1024" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="#12808B"/><stop offset="1" stop-color="#0A4F58"/></linearGradient></defs><rect width="1024" height="1024" rx="232" fill="url(#g)"/><path d="M250 720 C 420 700, 540 580, 770 360" stroke="#fff" stroke-width="76" stroke-linecap="round"/><path d="M250 560 L 360 560" stroke="#fff" stroke-width="40" stroke-linecap="round" opacity="0.55"/><path d="M250 640 L 320 640" stroke="#fff" stroke-width="40" stroke-linecap="round" opacity="0.35"/><circle cx="788" cy="338" r="86" fill="#16C47F" stroke="#fff" stroke-width="22"/></svg>`;
const BRAND = (s) => `<div class="brand" style="font-size:${Math.round(s * 0.76)}px">${LOGO(s)}<span>Dari<span>lab</span></span></div>`;

function sh(a) {
  const r = spawnSync('ffmpeg', ['-y', '-loglevel', 'error', ...a], { stdio: ['ignore', 'inherit', 'inherit'] });
  if (r.status !== 0) throw new Error('ffmpeg a échoué');
}
function duration(file) {
  return parseFloat(spawnSync('ffprobe', ['-v', 'error', '-show_entries', 'format=duration', '-of', 'csv=p=0', file]).stdout.toString());
}

rmSync(WORK, { recursive: true, force: true });
mkdirSync(WORK, { recursive: true });
for (const s of T.story) {
  if (!existsSync(join(PLANS, s.plan + '.mp4'))) throw new Error(`plan manquant : ${s.plan}.mp4 (tutoriels-tournage.mjs)`);
}

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
async function mask(file, w, h, r) {
  await still(file, `<div style="position:absolute;left:0;top:0;width:${w}px;height:${h}px;border-radius:${r}px;background:#fff"></div>`, { bare: true });
  const out = file.replace(/\.png$/, '-c.png');
  sh(['-i', file, '-vf', `crop=${w}:${h}:0:0,format=gray`, out]);
  return out;
}
/** Une carte fixe, avec un léger zoom avant pour qu'elle vive. */
async function card(file, body, secs) {
  const png = file.replace(/\.mp4$/, '.png');
  await still(png, body);
  sh(['-loop', '1', '-i', png, '-filter_complex',
    `[0:v]scale=${W * 2}:${H * 2},zoompan=z='1+0.035*on/(${secs}*${FPS})':x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)':d=1:s=${W}x${H}:fps=${FPS},format=yuv420p[v]`,
    '-map', '[v]', '-t', String(secs), '-c:v', 'libx264', '-preset', 'veryfast', '-crf', '16', file]);
}
const n = T.story.length;
const bar = (i, style) => `<div class="bar" style="${style}">${T.story.map((_, k) => `<i class="${k < i ? 'done' : k === i ? 'on' : ''}"></i>`).join('')}</div>`;

const segments = [];
const intro = join(WORK, '00-ouverture.mp4');
if (WHO === 'coach') {
  await card(intro, `<div class="center">${BRAND(96)}
    <div class="kicker" style="font-size:28px; margin-top:70px">${T.intro.kicker}</div>
    <div class="title" style="font-size:84px; line-height:1.06; margin-top:20px">${T.intro.title}</div>
    <div class="sub" style="font-size:32px; margin-top:34px; max-width:1300px">${T.intro.sub}</div></div>`, 4.5);
} else {
  await card(intro, `<div class="center">${BRAND(110)}
    <div class="kicker" style="font-size:34px; margin-top:90px">${T.intro.kicker}</div>
    <div class="title" style="font-size:96px; line-height:1.06; margin-top:24px">${T.intro.title}</div>
    <div class="sub" style="font-size:40px; margin-top:44px">${T.intro.sub}</div></div>`, 4.5);
}
segments.push(intro);

const maskFile = WHO === 'coach'
  ? await mask(join(WORK, 'masque.png'), WIN.w, WIN.h, WIN.r)
  : await mask(join(WORK, 'masque.png'), SCREEN.w, SCREEN.h, SCREEN.r);

for (const [i, s] of T.story.entries()) {
  const out = join(WORK, `${String(i + 1).padStart(2, '0')}-${s.plan}.mp4`);
  const bg = join(WORK, `fond-${s.plan}.png`);
  const clip = join(PLANS, s.plan + '.mp4');
  const num = String(i + 1).padStart(2, '0');
  let geo;
  if (WHO === 'coach') {
    geo = WIN;
    await still(bg, `
      <div style="position:absolute; left:${WIN.x}px; top:38px; display:flex; align-items:center; gap:22px; white-space:nowrap">
        <span class="step" style="width:58px; height:58px; font-size:26px">${num}</span>
        <span class="kicker" style="font-size:22px">${s.kicker}</span>
        <span class="title" style="font-size:44px">${s.title}</span></div>
      <div style="position:absolute; left:${WIN.x}px; top:${WIN.y}px; width:${WIN.w}px; height:${WIN.h}px; border-radius:${WIN.r}px; background:#000;
        box-shadow: 0 30px 90px rgba(0,0,0,0.55), 0 0 0 2px rgba(255,255,255,0.08)"></div>
      <div class="sub" style="position:absolute; left:${WIN.x}px; width:${WIN.w}px; top:${WIN.y + WIN.h + 26}px; font-size:30px; text-align:center">${s.sub}</div>
      ${bar(i, `left:${WIN.x}px; width:${WIN.w}px; top:${H - 24}px`)}`);
  } else {
    geo = SCREEN;
    await still(bg, `
      <div style="position:absolute; left:80px; right:80px; top:96px">
        <div style="display:flex; align-items:center; gap:22px">
          <span class="step" style="width:66px; height:66px; font-size:30px">${num}</span>
          <span class="kicker" style="font-size:32px">${s.kicker}</span></div>
        <div class="title" style="font-size:74px; line-height:1.06; margin-top:24px">${s.title}</div>
        <div class="sub" style="font-size:36px; margin-top:22px">${s.sub}</div></div>
      <div style="position:absolute; left:${SCREEN.x - BEZEL}px; top:${SCREEN.y - BEZEL}px; width:${SCREEN.w + 2 * BEZEL}px; height:${SCREEN.h + 2 * BEZEL}px;
        border-radius:${SCREEN.r + BEZEL}px; background:#000; box-shadow:0 40px 100px rgba(0,0,0,.6),0 0 0 2px rgba(255,255,255,.1)"></div>
      ${bar(i, `left:80px; right:80px; top:${H - 30}px`)}`);
  }
  const len = duration(clip) / T.speed;
  sh(['-i', clip, '-loop', '1', '-i', bg, '-loop', '1', '-i', maskFile, '-filter_complex',
    `[0:v]setpts=PTS/${T.speed},scale=${geo.w}:${geo.h}:flags=lanczos,format=rgba[c];[2:v]format=gray[m];[c][m]alphamerge[cm];` +
    `[1:v][cm]overlay=${geo.x}:${geo.y}:shortest=1,fps=${FPS},format=yuv420p[v]`,
    '-map', '[v]', '-t', len.toFixed(3), '-c:v', 'libx264', '-preset', 'veryfast', '-crf', '16', out]);
  segments.push(out);
  console.log(`étape ${num} : ${s.plan} (${len.toFixed(1)} s)`);
}

const fin = join(WORK, '99-fin.mp4');
const big = WHO === 'coach' ? 1 : 1.2;
await card(fin, `<div class="center">${BRAND(Math.round(96 * big))}
  <div class="title" style="font-size:${Math.round(88 * big)}px; margin-top:${Math.round(70 * big)}px">${T.outro.title}</div>
  <div class="sub" style="font-size:${Math.round(34 * big)}px; margin-top:34px">${T.outro.sub}</div></div>`, 4);
segments.push(fin);
await browser.close();

// Fondus enchaînés, puis la musique.
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
if (music) {
  const wav = join(WORK, 'musique.wav');
  const r = spawnSync('python3', [resolve(here, '..', 'musique', 'compose.py'), wav, String(Math.ceil(total + 2))], { stdio: 'inherit' });
  if (r.status !== 0) throw new Error('compose.py a échoué');
  a.push('-i', wav);
}
a.push('-filter_complex', chain.slice(0, -1) + (music
  ? `;[${segments.length}:a]aecho=0.8:0.6:180|360:0.25|0.12,loudnorm=I=-22:TP=-2:LRA=11,afade=t=in:d=0.5,afade=t=out:st=${(total - 3).toFixed(2)}:d=3[a]`
  : ''), '-map', '[v]');
if (music) a.push('-map', '[a]', '-t', total.toFixed(3), '-c:a', 'aac', '-b:a', '160k');
a.push('-c:v', 'libx264', '-preset', 'slow', '-crf', '19', '-pix_fmt', 'yuv420p', '-movflags', '+faststart', OUT);
sh(a);
console.log(`Tutoriel ${WHO} : ${OUT} (${total.toFixed(1)} s)`);
