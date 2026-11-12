/**
 * Rend branding/video/presentation.html en MP4 (1920×1080, 30 i/s, H.264) :
 *   node branding/video/render-video.mjs [sortie.mp4] [--musique piste.mp3]
 *
 * Image par image : render(t) pose l'état exact de l'instant t, Playwright photographie, ffmpeg
 * assemble. Plus lent qu'un enregistrement d'écran, mais sans saccade et identique à chaque rendu.
 * Les captures doivent exister (docs/captures, script e2e/captures).
 *
 * Playwright n'est pas une dépendance du front : NODE_PATH=../e2e/node_modules.
 */
import { createRequire } from 'node:module';
import { spawn } from 'node:child_process';
import { dirname, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const require = createRequire(import.meta.url);
const { chromium } = require('playwright');

const here = dirname(fileURLToPath(import.meta.url));
const args = process.argv.slice(2);
const music = args.includes('--musique') ? args[args.indexOf('--musique') + 1] : null;
const out = resolve(args.find((a, i) => !a.startsWith('--') && args[i - 1] !== '--musique')
  ?? resolve(here, '..', '..', 'dist', 'presentation-coachs.mp4'));
const FPS = 30;

const browser = await chromium.launch({ args: ['--allow-file-access-from-files'] });
const page = await browser.newPage({ viewport: { width: 1920, height: 1080 } });
await page.goto(pathToFileURL(resolve(here, 'presentation.html')).href);
await page.evaluate(async () => {
  await document.fonts.ready;
  await Promise.all([...document.images].map((i) => i.decode().catch(() => undefined)));
});
const duration = await page.evaluate(() => window.DURATION);
const frames = Math.round(duration * FPS);

const ffArgs = ['-y', '-loglevel', 'error', '-f', 'image2pipe', '-framerate', String(FPS), '-i', '-'];
if (music) {
  ffArgs.push('-i', music, '-af', `afade=t=out:st=${(duration - 2.5).toFixed(2)}:d=2.5`, '-shortest',
    '-c:a', 'aac', '-b:a', '192k');
}
ffArgs.push('-c:v', 'libx264', '-preset', 'slow', '-crf', '18', '-pix_fmt', 'yuv420p', '-movflags', '+faststart', out);
const ff = spawn('ffmpeg', ffArgs, { stdio: ['pipe', 'inherit', 'inherit'] });

for (let f = 0; f < frames; f++) {
  await page.evaluate((t) => window.render(t), f / FPS);
  const buf = await page.screenshot({ type: 'jpeg', quality: 92 });
  if (!ff.stdin.write(buf)) await new Promise((r) => ff.stdin.once('drain', r));
  if (f % 150 === 0) process.stdout.write(`${f}/${frames}\n`);
}
ff.stdin.end();
await new Promise((r) => ff.on('close', r));
await browser.close();
console.log(`Vidéo : ${out} (${duration.toFixed(1)} s)`);
