/**
 * Outils de tournage : un navigateur piloté comme par une personne (curseur visible, gestes
 * lents), filmé image par image par le protocole de Chrome (Page.startScreencast).
 *
 * Chaque plan sort en MP4 à cadence fixe (30 i/s) : ffmpeg rejoue les images reçues avec leur
 * horodatage, une image restant à l'écran tant que la page ne change pas.
 */
import { createRequire } from 'node:module';
import { spawnSync } from 'node:child_process';
import { mkdirSync, rmSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const require = createRequire(import.meta.url);
export const { chromium, devices } = require('playwright');

export const BASE = process.env.FILM_BASE_URL ?? 'http://localhost:4200';
export const PASSWORD = 'password1234';

/** Curseur dessiné dans la page (le vrai n'apparaît pas à l'écran filmé), avec une onde au clic. */
const CURSOR_SCRIPT = `
(() => {
  try { localStorage.setItem('darilab.install-guide.snoozedAt', String(Date.now())); } catch {}
  const install = () => {
    if (document.getElementById('film-cursor')) return;
    const st = document.createElement('style');
    st.textContent = \`
      #film-cursor { position: fixed; left: 0; top: 0; width: 26px; height: 26px; z-index: 2147483647;
        pointer-events: none; transform: translate(-200px, -200px); transition: none; }
      #film-cursor svg { filter: drop-shadow(0 2px 3px rgba(0,0,0,.35)); }
      .film-ripple { position: fixed; z-index: 2147483646; pointer-events: none; width: 44px; height: 44px;
        margin: -22px 0 0 -22px; border-radius: 50%; background: rgba(18,133,145,.35);
        border: 2px solid rgba(18,133,145,.8); animation: film-rip .5s ease-out forwards; }
      @keyframes film-rip { from { transform: scale(.3); opacity: 1 } to { transform: scale(1.4); opacity: 0 } }
      .toast, [class*="toast"] { }
    \`;
    document.documentElement.appendChild(st);
    const c = document.createElement('div');
    c.id = 'film-cursor';
    c.innerHTML = '<svg width="26" height="26" viewBox="0 0 26 26"><path d="M3 2 L3 21 L8.2 16.2 L11.6 24 L15 22.6 L11.7 15 L19 15 Z" fill="#fff" stroke="#0b1416" stroke-width="1.6" stroke-linejoin="round"/></svg>';
    document.documentElement.appendChild(c);
    addEventListener('mousemove', (e) => { c.style.transform = 'translate(' + (e.clientX - 3) + 'px,' + (e.clientY - 2) + 'px)'; }, true);
    addEventListener('mousedown', (e) => {
      const r = document.createElement('div'); r.className = 'film-ripple';
      r.style.left = e.clientX + 'px'; r.style.top = e.clientY + 'px';
      document.documentElement.appendChild(r); setTimeout(() => r.remove(), 600);
    }, true);
    // Au toucher (téléphone) : seulement l'onde, pas de flèche.
    addEventListener('touchstart', (e) => {
      const t = e.touches[0]; if (!t) return;
      const r = document.createElement('div'); r.className = 'film-ripple';
      r.style.left = t.clientX + 'px'; r.style.top = t.clientY + 'px';
      document.documentElement.appendChild(r); setTimeout(() => r.remove(), 600);
    }, true);
  };
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', install); else install();
})();
`;

export async function newStage(browser, { mobile = false } = {}) {
  const ctx = await browser.newContext(mobile
    ? { ...devices['Pixel 7'], colorScheme: 'dark', locale: 'fr-FR', timezoneId: 'Europe/Paris' }
    : { viewport: { width: 1920, height: 1080 }, deviceScaleFactor: 1, colorScheme: 'light', locale: 'fr-FR', timezoneId: 'Europe/Paris' });
  await ctx.addInitScript(mobile ? CURSOR_SCRIPT.replace("c.id = 'film-cursor';", "c.id = 'film-cursor'; c.style.display = 'none';") : CURSOR_SCRIPT);
  const page = await ctx.newPage();
  page.mobile = mobile;
  page.mx = 960; page.my = 540;
  return page;
}

export async function login(page, email) {
  await page.goto(BASE + '/login');
  await page.locator('#email').fill(email);
  await page.locator('#password').fill(PASSWORD);
  await page.getByRole('button', { name: 'Se connecter' }).click();
  await page.waitForURL(/\/(app|athlete)/);
  await page.waitForTimeout(1500);
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
export const pause = (page, ms) => page.waitForTimeout(ms);

/** Déplacement du curseur en courbe adoucie, à vitesse humaine. */
export async function moveTo(page, target, { ms = 650, dx = 0.5, dy = 0.5 } = {}) {
  const box = typeof target.boundingBox === 'function' ? await target.boundingBox() : target;
  if (!box) throw new Error('élément introuvable à l\'écran');
  const x = box.x + box.width * dx, y = box.y + box.height * dy;
  const steps = Math.max(8, Math.round(ms / 16));
  const x0 = page.mx, y0 = page.my;
  for (let i = 1; i <= steps; i++) {
    const t = i / steps, e = t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
    await page.mouse.move(x0 + (x - x0) * e, y0 + (y - y0) * e);
    await sleep(ms / steps);
  }
  page.mx = x; page.my = y;
}

/** Amène `target` au milieu de l'écran par un défilement doux s'il est hors champ. */
export async function reveal(page, target) {
  if (!target.boundingBox) return;
  const box = await target.boundingBox();
  const vh = page.viewportSize().height;
  if (box && box.y >= 60 && box.y + box.height <= vh - 60) return;
  await target.evaluate((el) => el.scrollIntoView({ behavior: 'smooth', block: 'center' }));
  await pause(page, 900);
}

export async function click(page, target, opts = {}) {
  await reveal(page, target);
  if (page.mobile) {
    const box = await target.boundingBox();
    await pause(page, 250);
    await page.touchscreen.tap(box.x + box.width / 2, box.y + box.height / 2);
    await pause(page, opts.after ?? 500);
    return;
  }
  await moveTo(page, target, opts);
  await pause(page, 180);
  await page.mouse.down(); await pause(page, 90); await page.mouse.up();
  await pause(page, opts.after ?? 450);
}

/** Glisser-déposer lent, compatible avec le glisser du CDK d'Angular. */
export async function drag(page, from, to, { ms = 1200 } = {}) {
  await moveTo(page, from);
  await pause(page, 200);
  await page.mouse.down();
  await pause(page, 150);
  // Un premier petit mouvement déclenche le glisser.
  await page.mouse.move(page.mx + 8, page.my + 8, { steps: 4 }); page.mx += 8; page.my += 8;
  await moveTo(page, to, { ms });
  await pause(page, 250);
  await page.mouse.up();
  await pause(page, 700);
}

export async function type(page, locator, text, { delay = 45 } = {}) {
  await click(page, locator);
  await locator.pressSequentially(text, { delay });
}

/** Défilement doux de la page ou d'un conteneur, de `dy` pixels. */
export async function scroll(page, dy, { ms = 900 } = {}) {
  const steps = Math.round(ms / 16);
  for (let i = 0; i < steps; i++) { await page.mouse.wheel(0, dy / steps); await sleep(ms / steps); }
}

/**
 * Filme `action(page)` et écrit `out` (MP4, 30 i/s). Le plan commence et finit sur une image
 * immobile d'une demi-seconde, pour les fondus du montage.
 */
export async function film(page, out, action) {
  const dir = out + '.frames';
  rmSync(dir, { recursive: true, force: true });
  mkdirSync(dir, { recursive: true });
  const cdp = await page.context().newCDPSession(page);
  const frames = [];
  cdp.on('Page.screencastFrame', async (f) => {
    const file = join(dir, String(frames.length).padStart(6, '0') + '.jpg');
    writeFileSync(file, Buffer.from(f.data, 'base64'));
    frames.push({ file, t: f.metadata.timestamp });
    try { await cdp.send('Page.screencastFrameAck', { sessionId: f.sessionId }); } catch {}
  });
  const vp = page.viewportSize();
  const scale = page.mobile ? 2.625 : 1;
  await cdp.send('Page.startScreencast', { format: 'jpeg', quality: 92,
    maxWidth: Math.round(vp.width * scale), maxHeight: Math.round(vp.height * scale), everyNthFrame: 1 });
  await pause(page, 500);
  // Une interaction forcée au départ : sans changement, le screencast n'envoie aucune image.
  await page.mouse.move(page.mx + 1, page.my); await page.mouse.move(page.mx, page.my);
  const start = Date.now() / 1000;
  await action(page);
  await pause(page, 600);
  const end = Date.now() / 1000;
  await cdp.send('Page.stopScreencast');
  await cdp.detach();
  if (!frames.length) throw new Error('aucune image filmée pour ' + out);
  // Liste ffmpeg : chaque image dure jusqu'à la suivante ; la dernière, jusqu'à la fin du plan.
  const lines = [];
  for (let i = 0; i < frames.length; i++) {
    const next = i + 1 < frames.length ? frames[i + 1].t : Math.max(end, frames[i].t + 0.5);
    lines.push(`file '${frames[i].file}'`, `duration ${Math.max(0.001, next - frames[i].t).toFixed(4)}`);
  }
  lines.push(`file '${frames[frames.length - 1].file}'`);
  writeFileSync(dir + '/list.txt', lines.join('\n'));
  const w = page.mobile ? 1080 : 1920, h = page.mobile ? 2400 : 1080;
  const r = spawnSync('ffmpeg', ['-y', '-loglevel', 'error', '-f', 'concat', '-safe', '0', '-i', dir + '/list.txt',
    '-vf', `scale=${w}:${h}:force_original_aspect_ratio=decrease,pad=${w}:${h}:(ow-iw)/2:(oh-ih)/2,fps=30,format=yuv420p`,
    '-c:v', 'libx264', '-preset', 'medium', '-crf', '16', out], { stdio: 'inherit' });
  if (r.status !== 0) throw new Error('ffmpeg a échoué pour ' + out);
  rmSync(dir, { recursive: true, force: true });
  console.log(`plan : ${out} (${(end - start).toFixed(1)} s, ${frames.length} images)`);
}
