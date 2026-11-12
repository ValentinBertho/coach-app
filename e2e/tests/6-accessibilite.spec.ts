import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';
import { ATHLETE, COACH, login, skipInstallGuide } from './support';

/**
 * Contrôle d'accessibilité automatique (axe-core, règles WCAG 2.1 A et AA) des écrans clés.
 *
 * Bloquant : toute violation, sauf le contraste des couleurs. Celui-ci dépend des jetons du
 * thème (variables CSS partagées par toute l'application) et se corrige dans le système de
 * design, pas écran par écran. Il est relevé dans le rapport (annotation « contraste ») pour
 * être suivi, sans faire échouer la CI tant que ce chantier n'est pas fait.
 */
const WCAG = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'];
const NOT_BLOCKING = ['color-contrast'];

async function audit(page: Page, screen: string): Promise<void> {
  // Laisse les données arriver : un écran vide passerait tous les contrôles.
  await page.waitForLoadState('networkidle');
  const result = await new AxeBuilder({ page }).withTags(WCAG).analyze();

  const contrast = result.violations.filter(v => NOT_BLOCKING.includes(v.id));
  const nodes = contrast.reduce((n, v) => n + v.nodes.length, 0);
  if (nodes) {
    test.info().annotations.push({ type: 'contraste', description: `${screen} : ${nodes} élément(s)` });
  }

  const blocking = result.violations
    .filter(v => !NOT_BLOCKING.includes(v.id))
    .map(v => `${v.impact} ${v.id} (${v.nodes.length}) : ${v.help} — ${v.nodes.slice(0, 3).map(n => n.html.slice(0, 160)).join(' | ')}`);
  expect(blocking, `Violations d'accessibilité sur « ${screen} »`).toEqual([]);
}

test.describe('Accessibilité', () => {
  test('connexion', async ({ page }) => {
    await skipInstallGuide(page);
    await page.goto('/login');
    await audit(page, 'connexion');
  });

  for (const [screen, path] of [
    ['tableau de bord du coach', '/app'],
    ['calendrier du coach', '/app/calendar'],
    ['liste des athlètes', '/app/athletes'],
    ['ma journée du coach (boîte de tri)', '/app/journee'],
  ] as const) {
    test(screen, async ({ page }) => {
      await login(page, COACH, /\/app/);
      await page.goto(path);
      await audit(page, screen);
    });
  }

  for (const [screen, path] of [
    ['séance du jour de l\'athlète', '/athlete/today'],
    ['calendrier de l\'athlète', '/athlete/calendar'],
    ['messagerie de l\'athlète', '/athlete/messages'],
  ] as const) {
    test(screen, async ({ page }) => {
      await login(page, ATHLETE, /\/athlete/);
      await page.goto(path);
      await audit(page, screen);
    });
  }
});
