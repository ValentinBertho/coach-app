/**
 * Tournage des plans de la vidéo, sur le jeu de démonstration (base FRAÎCHE en profil dev, front
 * et back démarrés) :
 *   NODE_PATH=../../../../e2e/node_modules node branding/video/film/shoot.mjs [dossier] [plan…]
 * Sans nom de plan, tout est tourné. Le montage : montage.mjs.
 */
import { mkdirSync } from 'node:fs';
import { resolve } from 'node:path';
import { chromium, newStage, login, click, drag, pause, moveTo, scroll, type, film, BASE } from './recorder.mjs';

const OUT = resolve(process.argv[2] ?? '/tmp/darilab-film');
const ONLY = process.argv.slice(3);
mkdirSync(OUT, { recursive: true });
const COACH = 'demo@coachrun.fr', ATHLETE = 'athlete@coachrun.fr';

const PLANS = {
  // Le matin de la coach : qui a besoin d'elle.
  async 'coach-journee'(b) {
    const p = await newStage(b); await login(p, COACH);
    await p.goto(BASE + '/app'); await pause(p, 1500);
    await film(p, OUT + '/coach-journee.mp4', async (p) => {
      await moveTo(p, p.getByText('À surveiller aujourd\'hui'), { ms: 900 });
      await pause(p, 500);
      await moveTo(p, p.locator('text=Julie Durand').first(), { ms: 700 });
      await pause(p, 700);
      await click(p, p.getByRole('link', { name: /Ma journée/ }).first(), { after: 1400 });
      const first = p.getByRole('listitem').filter({ hasText: 'RPE' }).first();
      await moveTo(p, first, { ms: 700 });
      await pause(p, 600);
      await click(p, first.getByRole('button', { name: /Vu/ }), { after: 1200 });
    });
    await p.context().close();
  },

  // Planifier : glisser une séance type, puis l'ouvrir.
  async 'coach-planifie'(b) {
    const p = await newStage(b); await login(p, COACH);
    await p.goto(BASE + '/app/athletes'); await pause(p, 1000);
    await p.getByText('Chloé Dubois').first().click();
    await p.waitForURL(/resume/);
    await p.goto(p.url().replace(/resume.*/, 'programme')); await pause(p, 2000);
    await film(p, OUT + '/coach-planifie.mp4', async (p) => {
      const item = p.locator('.lib-item', { hasText: '6 × 1000 m au seuil' }).first();
      const target = p.locator('.day-body').nth(11);
      await drag(p, item, target, { ms: 1400 });
      await pause(p, 1400);
      await click(p, target.locator('.workout-card').first(), { after: 1800 });
      await moveTo(p, p.getByText('Intervalles').first(), { ms: 900 });
      await pause(p, 1600);
    });
    await p.context().close();
  },

  // Construire une séance : l'éditeur de structure.
  async 'coach-seance'(b) {
    const p = await newStage(b); await login(p, COACH);
    await p.goto(BASE + '/app/library/course'); await pause(p, 1500);
    await film(p, OUT + '/coach-seance.mp4', async (p) => {
      const card = p.locator('.card.tpl').filter({ hasText: '10 × 400 m VMA' }).first();
      await moveTo(p, card, { ms: 800 });
      await pause(p, 400);
      const edit = card.locator('a[title="Modifier"]');
      await click(p, edit, { after: 2000 });
      await moveTo(p, { x: 900, y: 500, width: 1, height: 1 }, { ms: 900 });
      await scroll(p, 350, { ms: 1400 });
      await pause(p, 1500);
    });
    await p.context().close();
  },

  // La charge de l'athlète.
  async 'coach-charge'(b) {
    const p = await newStage(b); await login(p, COACH);
    await p.goto(BASE + '/app/athletes'); await pause(p, 1000);
    await p.getByText('Chloé Dubois').first().click();
    await p.waitForURL(/resume/); await pause(p, 1500);
    await film(p, OUT + '/coach-charge.mp4', async (p) => {
      await moveTo(p, p.getByText('Profil physiologique').first(), { ms: 900 });
      await pause(p, 1200);
      await click(p, p.getByRole('link', { name: 'Charge', exact: true }).first(), { after: 1800 });
      const chart = p.locator('svg, canvas').filter({ has: p.locator('*') }).nth(2);
      await moveTo(p, { x: 1500, y: 720, width: 1, height: 1 }, { ms: 1200 });
      await moveTo(p, { x: 1720, y: 700, width: 1, height: 1 }, { ms: 1200 });
      await pause(p, 1200);
      void chart;
    });
    await p.context().close();
  },

  // Répondre à une athlète.
  async 'coach-messages'(b) {
    const p = await newStage(b); await login(p, COACH);
    await p.goto(BASE + '/app/messages'); await pause(p, 1500);
    await film(p, OUT + '/coach-messages.mp4', async (p) => {
      await click(p, p.getByText('Romain Blanc').first(), { after: 1000 });
      const box = p.getByPlaceholder(/message/i).last();
      await type(p, box, 'Bien fait de me prévenir. Demain, 40 min de vélo tranquille à la place, et tu me redis jeudi 👍');
      await pause(p, 400);
      await click(p, p.getByRole('button', { name: 'Envoyer' }), { after: 1500 });
    });
    await p.context().close();
  },

  // Une demande arrive de l'annuaire.
  async 'coach-demande'(b) {
    const p = await newStage(b); await login(p, COACH);
    await p.goto(BASE + '/app/demandes'); await pause(p, 1500);
    await film(p, OUT + '/coach-demande.mp4', async (p) => {
      await moveTo(p, p.getByText(/Bonjour Claire/).first(), { ms: 900, dx: 0.2 });
      await pause(p, 1500);
      await click(p, p.getByRole('button', { name: 'Accepter' }).first(), { after: 900 });
      await click(p, p.locator('.dialog').getByRole('button', { name: 'Accepter' }), { after: 2200 });
    });
    await p.context().close();
  },

  // Un visiteur cherche un coach.
  async 'annuaire'(b) {
    const p = await newStage(b);
    await p.goto(BASE + '/coachs'); await pause(p, 1800);
    await film(p, OUT + '/annuaire.mp4', async (p) => {
      const q = p.getByRole('searchbox', { name: 'Rechercher un coach' });
      await type(p, q, 'lyon', { delay: 120 });
      await pause(p, 1200);
      await click(p, p.getByText('Claire Vasseur').first(), { after: 1600 });
      await moveTo(p, p.getByText('Formules').first(), { ms: 900 });
      await scroll(p, 300, { ms: 1000 });
      await pause(p, 1000);
    });
    await p.context().close();
  },

  // L'athlète, sur son téléphone : forme du matin, séance du jour.
  async 'athlete-matin'(b) {
    const p = await newStage(b, { mobile: true }); await login(p, ATHLETE);
    await p.goto(BASE + '/athlete/today'); await pause(p, 1800);
    await film(p, OUT + '/athlete-matin.mp4', async (p) => {
      for (const [i, v] of [[0, 0.75], [1, 0.3], [2, 0.08]]) {
        const s = p.locator('input[type=range]').nth(i);
        const box = await s.boundingBox();
        await p.touchscreen.tap(box.x + box.width * v, box.y + box.height / 2);
        await pause(p, 450);
      }
      await click(p, p.getByRole('button', { name: 'Enregistrer' }), { after: 1400 });
      await scroll(p, 500, { ms: 1000 });
      await pause(p, 1800);
    });
    await p.context().close();
  },

  // Le retour de séance.
  async 'athlete-retour'(b) {
    const p = await newStage(b, { mobile: true }); await login(p, ATHLETE);
    await p.goto(BASE + '/athlete/today'); await pause(p, 1800);
    await film(p, OUT + '/athlete-retour.mp4', async (p) => {
      await click(p, p.getByRole('button', { name: 'Noter mon retour' }), { after: 900 });
      await click(p, p.locator('[aria-label="Sensation bonne"]'), { after: 400 });
      await click(p, p.locator('[aria-label^="RPE 6"]'), { after: 400 });
      const c = p.getByPlaceholder('Tu peux écrire un mot à ton entraîneur ici.');
      await c.scrollIntoViewIfNeeded(); await pause(p, 300);
      await click(p, c, { after: 200 });
      await c.pressSequentially('Bonnes jambes, le dernier 1000 était le plus rapide !', { delay: 40 });
      await pause(p, 500);
      const done = p.getByRole('button', { name: /^Séance réalisée/ });
      await done.scrollIntoViewIfNeeded(); await pause(p, 300);
      await click(p, done, { after: 1800 });
    });
    await p.context().close();
  },

  // Son mois.
  async 'athlete-mois'(b) {
    const p = await newStage(b, { mobile: true }); await login(p, ATHLETE);
    await p.goto(BASE + '/athlete/calendar'); await pause(p, 1800);
    await film(p, OUT + '/athlete-mois.mp4', async (p) => {
      await pause(p, 800);
      const day = p.getByText('19', { exact: true }).first();
      await click(p, day, { after: 2200 });
    });
    await p.context().close();
  },
};

const browser = await chromium.launch();
for (const [name, plan] of Object.entries(PLANS)) {
  if (ONLY.length && !ONLY.includes(name)) continue;
  try { await plan(browser); } catch (e) { console.error(`✗ ${name} : ${e.message.split("\n").slice(0,6).join(" / ")}`); }
}
await browser.close();
