/**
 * Tournage des plans des deux tutoriels de prise en main (coach et athlète), sur le jeu de
 * démonstration : base FRAÎCHE en profil dev, front et back démarrés, avec des identifiants
 * Strava/Garmin factices et COROS ouvert pour que les cartes « Connecter » s'affichent (voir
 * README) :
 *   NODE_PATH=../../../../e2e/node_modules node tutoriels-tournage.mjs [dossier] [plan…]
 * Sans nom de plan, tout est tourné. Le montage : tutoriels-montage.mjs.
 *
 * Les plans coach se filment sur ordinateur (1920 × 1080), les plans athlète sur téléphone
 * (Pixel 7). L'ordre de tournage compte : certains plans modifient les données (retour noté,
 * demande acceptée, message envoyé), tous sont pensés pour une base neuve.
 */
import { mkdirSync } from 'node:fs';
import { resolve } from 'node:path';
import { chromium, newStage, login, click, drag, pause, moveTo, scroll, type, film, reveal, BASE } from './recorder.mjs';

const OUT = resolve(process.argv[2] ?? '/tmp/darilab-tutoriels');
const ONLY = process.argv.slice(3);
mkdirSync(OUT, { recursive: true });
const COACH = 'demo@coachrun.fr', ATHLETE = 'athlete@coachrun.fr';

/** Ouvre la fiche de Chloé Dubois et renvoie son adresse (sans l'onglet). */
async function ficheChloe(p) {
  await p.goto(BASE + '/app/athletes'); await pause(p, 1000);
  await p.getByText('Chloé Dubois').first().click();
  await p.waitForURL(/resume/);
  return p.url().replace(/\/resume.*/, '');
}

/** Un plan = un nouvel onglet connecté, une préparation hors champ, puis le geste filmé. */
function plan(who, prepare, action, { mobile = false } = {}) {
  return async (b, name) => {
    const p = await newStage(b, { mobile }); await login(p, who);
    await prepare(p);
    await film(p, `${OUT}/${name}.mp4`, action);
    await p.context().close();
  };
}
const coach = (prepare, action) => plan(COACH, prepare, action);
const athlete = (prepare, action) => plan(ATHLETE, prepare, action, { mobile: true });

const PLANS = {
  // ------------------------------------------------------------------ coach
  'tc-tableau': coach(async (p) => { await p.goto(BASE + '/app'); await pause(p, 1800); }, async (p) => {
    await moveTo(p, p.getByText('À surveiller aujourd\'hui'), { ms: 900 });
    await pause(p, 600);
    await moveTo(p, p.locator('text=Julie Durand').first(), { ms: 700 });
    await pause(p, 900);
    await moveTo(p, p.getByText('Vue d\'ensemble').first(), { ms: 800 });
    await pause(p, 700);
    await moveTo(p, p.getByText(/retours à traiter/i).first(), { ms: 700 });
    await pause(p, 1200);
  }),

  'tc-journee': coach(async (p) => { await p.goto(BASE + '/app/journee'); await pause(p, 1800); }, async (p) => {
    await moveTo(p, p.getByText('À traiter maintenant').first(), { ms: 800 });
    await pause(p, 900);
    const first = p.getByRole('listitem').filter({ hasText: 'RPE' }).first();
    await moveTo(p, first, { ms: 700 });
    await pause(p, 900);
    await click(p, first.getByRole('button', { name: /Vu/ }), { after: 1500 });
  }),

  'tc-athletes': coach(async (p) => { await p.goto(BASE + '/app/athletes'); await pause(p, 1800); }, async (p) => {
    await moveTo(p, p.getByText('Chloé Dubois').first(), { ms: 800 });
    await pause(p, 700);
    await click(p, p.getByRole('link', { name: /Nouvel athlète/ }).or(p.getByRole('button', { name: /Nouvel athlète/ })).first(), { after: 1300 });
    await type(p, p.getByLabel(/Prénom/).first(), 'Lucas', { delay: 60 });
    await type(p, p.getByLabel(/^Nom/).first(), 'Martin', { delay: 60 });
    await type(p, p.getByLabel(/^Email/).first(), 'lucas.martin@exemple.fr', { delay: 35 });
    await pause(p, 400);
    await moveTo(p, p.getByText('Données physiologiques').first(), { ms: 800 });
    await pause(p, 1200);
  }),

  'tc-fiche': coach(async (p) => { await ficheChloe(p); await pause(p, 1800); }, async (p) => {
    await moveTo(p, p.getByText('Profil physiologique').first(), { ms: 900 });
    await pause(p, 900);
    await moveTo(p, p.getByText('VDOT').first(), { ms: 600 });
    await pause(p, 700);
    await scroll(p, 420, { ms: 1400 });
    await moveTo(p, { x: 1100, y: 560, width: 1, height: 1 }, { ms: 800 });
    await pause(p, 1200);
  }),

  'tc-zones': coach(async (p) => { const u = await ficheChloe(p); await p.goto(u + '/zones'); await pause(p, 1800); }, async (p) => {
    await moveTo(p, p.getByText('Records & allures').first(), { ms: 900 });
    await pause(p, 800);
    await moveTo(p, p.getByText('10 km', { exact: true }).first(), { ms: 700 });
    await pause(p, 700);
    await scroll(p, 380, { ms: 1300 });
    await pause(p, 900);
    await moveTo(p, p.getByRole('button', { name: /Resynchroniser/ }).first(), { ms: 900 });
    await pause(p, 1000);
  }),

  'tc-bibliotheque': coach(async (p) => { await p.goto(BASE + '/app/library/course'); await pause(p, 1800); }, async (p) => {
    const card = p.locator('.card.tpl').filter({ hasText: '6 × 1000 m au seuil' }).first();
    await moveTo(p, card, { ms: 800 });
    await pause(p, 500);
    await click(p, card.locator('a[title="Modifier"]'), { after: 2200 });
    await moveTo(p, { x: 900, y: 520, width: 1, height: 1 }, { ms: 900 });
    await scroll(p, 380, { ms: 1400 });
    await pause(p, 1600);
  }),

  'tc-prepa': coach(async (p) => { await p.goto(BASE + '/app/library/course'); await pause(p, 1800); }, async (p) => {
    await click(p, p.getByRole('link', { name: /Prépa physique/ }).first(), { after: 1800 });
    await moveTo(p, p.locator('.card').nth(1), { ms: 800 });
    await pause(p, 900);
    await click(p, p.getByRole('link', { name: /Éducatifs/ }).first(), { after: 1800 });
    await moveTo(p, p.locator('.card').nth(1), { ms: 800 });
    await pause(p, 900);
  }),

  'tc-programme': coach(async (p) => { const u = await ficheChloe(p); await p.goto(u + '/programme'); await pause(p, 2200); }, async (p) => {
    const item = p.locator('.lib-item', { hasText: '6 × 1000 m au seuil' }).first();
    const target = p.locator('.day-body').nth(11);
    await drag(p, item, target, { ms: 1400 });
    await pause(p, 1400);
    // Le menu d'une semaine (clic droit sur sa colonne de totaux) : la dupliquer ailleurs.
    const total = p.locator('.week-total').nth(1);
    await moveTo(p, total, { ms: 800 });
    await p.mouse.down({ button: 'right' }); await p.mouse.up({ button: 'right' });
    await pause(p, 900);
    await moveTo(p, p.getByText(/Dupliquer cette semaine/).first(), { ms: 700 });
    await pause(p, 1500);
    await p.keyboard.press('Escape');
    await pause(p, 400);
  }),

  'tc-adapter': coach(async (p) => { const u = await ficheChloe(p); await p.goto(u + '/programme'); await pause(p, 2200); }, async (p) => {
    // La séance de seuil de la semaine prochaine.
    const card = p.locator('.workout-card', { hasText: /Tempo/ }).nth(1);
    await click(p, card, { after: 2000 });
    await click(p, p.getByRole('link', { name: /Adapter/ }).first(), { after: 2200 });
    const notes = p.locator('#sess-notes');
    await reveal(p, notes);
    await type(p, notes, 'Semaine chargée au travail : reste en bas de fourchette, et coupe à 4 km si les jambes sont lourdes.', { delay: 28 });
    await pause(p, 300);
    const rpe = p.locator('#sess-rpe');
    await click(p, rpe, { dx: 0.02, after: 200 });
    for (let i = 0; i < 6; i++) { await p.keyboard.press('ArrowRight'); await pause(p, 140); }
    await pause(p, 600);
    await click(p, p.getByRole('button', { name: 'Enregistrer', exact: true }).first(), { after: 1800 });
  }),

  'tc-groupe': coach(async (p) => {
    await p.goto(BASE + '/app/calendar'); await pause(p, 1500);
    await p.getByRole('button', { name: /^Vue/ }).click(); await pause(p, 400);
    await p.locator('.ctx-item', { hasText: 'Groupe' }).click(); await pause(p, 800);
    await p.locator('select[aria-label=Groupe]').selectOption({ index: 1 });
    await pause(p, 2200);
  }, async (p) => {
    await moveTo(p, p.getByText('Tout le groupe').first(), { ms: 800 });
    await pause(p, 500);
    const item = p.locator('.lib-item', { hasText: '10 × 400 m VMA' }).first();
    const target = p.locator('.gg-cell--all').last();
    await drag(p, item, target, { ms: 1500 });
    await pause(p, 2200);
    // Chaque athlète a reçu ses propres allures : on ouvre la séance d'une athlète.
    const chip = p.locator('.gg-chip', { hasText: /400/ }).last();
    await moveTo(p, chip, { ms: 900 });
    await pause(p, 700);
    await click(p, chip, { after: 2600 });
  }),

  'tc-charge': coach(async (p) => { const u = await ficheChloe(p); await p.goto(u + '/load'); await pause(p, 2200); }, async (p) => {
    await moveTo(p, p.getByText('ACWR').first(), { ms: 900 });
    await pause(p, 1000);
    await moveTo(p, { x: 1500, y: 720, width: 1, height: 1 }, { ms: 1200 });
    await moveTo(p, { x: 1760, y: 690, width: 1, height: 1 }, { ms: 1200 });
    await pause(p, 1200);
  }),

  'tc-messages': coach(async (p) => { await p.goto(BASE + '/app/messages'); await pause(p, 1800); }, async (p) => {
    await moveTo(p, p.getByText('Running Club Lyon').first(), { ms: 800 });
    await pause(p, 500);
    await moveTo(p, p.getByText('Marathon').first(), { ms: 500 });
    await pause(p, 500);
    await click(p, p.getByText('Romain Blanc').first(), { after: 1000 });
    const box = p.getByPlaceholder(/message/i).last();
    await type(p, box, 'Bien fait de me prévenir. Demain, 40 min de vélo tranquille à la place 👍', { delay: 40 });
    await pause(p, 300);
    await click(p, p.getByRole('button', { name: 'Envoyer' }), { after: 1500 });
  }),

  'tc-vitrine': coach(async (p) => { await p.goto(BASE + '/app/vitrine'); await pause(p, 1800); }, async (p) => {
    await moveTo(p, p.getByText('Votre accroche').first(), { ms: 900 });
    await pause(p, 600);
    await scroll(p, 600, { ms: 1600 });
    await moveTo(p, { x: 1100, y: 600, width: 1, height: 1 }, { ms: 800 });
    await scroll(p, 500, { ms: 1400 });
    await pause(p, 900);
  }),

  'tc-demande': coach(async (p) => { await p.goto(BASE + '/app/demandes'); await pause(p, 1800); }, async (p) => {
    await moveTo(p, p.getByText(/Bonjour Claire/).first(), { ms: 900, dx: 0.2 });
    await pause(p, 1600);
    await click(p, p.getByRole('button', { name: 'Accepter' }).first(), { after: 900 });
    await click(p, p.locator('.dialog').getByRole('button', { name: 'Accepter' }), { after: 2200 });
  }),

  // ---------------------------------------------------------------- athlète
  'ta-matin': athlete(async (p) => { await p.goto(BASE + '/athlete/today'); await pause(p, 1800); }, async (p) => {
    await pause(p, 600);
    for (const [i, v] of [[0, 0.75], [1, 0.3], [2, 0.08]]) {
      const s = p.locator('input[type=range]').nth(i);
      const box = await s.boundingBox();
      await p.touchscreen.tap(box.x + box.width * v, box.y + box.height / 2);
      await pause(p, 500);
    }
    await click(p, p.getByRole('button', { name: 'Enregistrer' }), { after: 1600 });
  }),

  'ta-seance': athlete(async (p) => { await p.goto(BASE + '/athlete/today'); await pause(p, 1800); }, async (p) => {
    await scroll(p, 900, { ms: 1400 });
    await pause(p, 600);
    await scroll(p, 700, { ms: 1200 });
    await pause(p, 2200);
  }),

  'ta-retour': athlete(async (p) => { await p.goto(BASE + '/athlete/today'); await pause(p, 1800); }, async (p) => {
    await click(p, p.getByRole('button', { name: 'Noter mon retour' }), { after: 900 });
    await click(p, p.locator('[aria-label="Sensation bonne"]'), { after: 400 });
    await click(p, p.locator('[aria-label^="RPE 6"]'), { after: 400 });
    const c = p.getByPlaceholder('Tu peux écrire un mot à ton entraîneur ici.');
    await c.scrollIntoViewIfNeeded(); await pause(p, 300);
    await click(p, c, { after: 200 });
    await c.pressSequentially('Bonnes jambes, la fin était facile !', { delay: 45 });
    await pause(p, 500);
    const done = p.getByRole('button', { name: /^Séance réalisée/ });
    await done.scrollIntoViewIfNeeded(); await pause(p, 300);
    await click(p, done, { after: 1800 });
  }),

  'ta-mois': athlete(async (p) => { await p.goto(BASE + '/athlete/calendar'); await pause(p, 1800); }, async (p) => {
    await click(p, p.getByRole('radio', { name: 'Prévu', exact: true }).first(), { after: 1000 });
    await click(p, p.getByRole('radio', { name: 'Les deux', exact: true }).first(), { after: 900 });
    await click(p, p.getByText('19', { exact: true }).first(), { after: 1500 });
    await moveTo(p, p.getByText('déplacer').first(), { ms: 300 });
    await pause(p, 1000);
    await click(p, p.locator('.sheet, [role=dialog]').getByText(/Tempo/).first(), { after: 2400 });
  }),

  'ta-messages': athlete(async (p) => { await p.goto(BASE + '/athlete/messages'); await pause(p, 1800); }, async (p) => {
    await click(p, p.getByText('Claire Vasseur').first(), { after: 1400 });
    const box = p.getByPlaceholder(/message/i).last();
    await type(p, box, 'Je pars en déplacement jeudi, on peut décaler le seuil ?', { delay: 45 });
    await pause(p, 300);
    await click(p, p.getByRole('button', { name: 'Envoyer' }), { after: 1600 });
  }),

  'ta-progres': athlete(async (p) => { await p.goto(BASE + '/athlete/progress'); await pause(p, 1800); }, async (p) => {
    await pause(p, 600);
    await scroll(p, 700, { ms: 1400 });
    await pause(p, 800);
    await scroll(p, -700, { ms: 1000 });
    await click(p, p.getByText('Mes zones').first(), { after: 1600 });
    await scroll(p, 500, { ms: 1200 });
    await pause(p, 1000);
  }),

  'ta-chrono': athlete(async (p) => { await p.goto(BASE + '/athlete/performances'); await pause(p, 1800); }, async (p) => {
    const t = p.getByPlaceholder(/0:42:30|hh:mm/).first();
    await type(p, t, '0:41:50', { delay: 110 });
    await pause(p, 400);
    await click(p, p.getByRole('button', { name: /Ajouter la performance/ }), { after: 1800 });
    await scroll(p, 400, { ms: 900 });
    await pause(p, 1000);
  }),

  'ta-objectifs': athlete(async (p) => { await p.goto(BASE + '/athlete/races'); await pause(p, 1800); }, async (p) => {
    await pause(p, 700);
    await click(p, p.getByRole('button', { name: /Nouvel objectif/ }), { after: 1600 });
    await scroll(p, 300, { ms: 900 });
    await pause(p, 1400);
  }),

  'ta-montre': athlete(async (p) => { await p.goto(BASE + '/athlete/profile'); await pause(p, 1500); }, async (p) => {
    await p.goto(BASE + '/athlete/sync'); await pause(p, 1800);
    await moveTo(p, p.getByText('Garmin Connect').first(), { ms: 300 });
    await pause(p, 1400);
    await scroll(p, 400, { ms: 1000 });
    await pause(p, 1800);
  }),

  'ta-aide': athlete(async (p) => { await p.goto(BASE + '/athlete/profile'); await pause(p, 1800); }, async (p) => {
    await pause(p, 600);
    await click(p, p.getByText('Aide & guide').first(), { after: 1800 });
    await scroll(p, 500, { ms: 1200 });
    await pause(p, 1200);
  }),
};

const browser = await chromium.launch();
let failed = 0;
for (const [name, shoot] of Object.entries(PLANS)) {
  if (ONLY.length && !ONLY.includes(name)) continue;
  try { await shoot(browser, name); } catch (e) { failed++; console.error(`✗ ${name} : ${e.message.split('\n').slice(0, 6).join(' / ')}`); }
}
await browser.close();
process.exit(failed ? 1 : 0);
