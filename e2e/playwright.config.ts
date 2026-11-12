import { defineConfig, devices } from '@playwright/test';

/**
 * Tests de bout en bout des parcours clés.
 *
 * Ils tournent contre une application complète déjà démarrée : le back (profil dev, jeu de
 * démonstration) et le front. En CI, c'est le job « E2E » qui les lance ; en local, voir
 * README.md. Les tests modifient les données de démonstration : ils s'exécutent un par un,
 * dans l'ordre, sur une base fraîche.
 */
export default defineConfig({
  testDir: './tests',
  fullyParallel: false,
  workers: 1,
  // Pas de nouvel essai : un parcours qui ne passe qu'au second coup est un parcours cassé.
  retries: 0,
  forbidOnly: !!process.env['CI'],
  timeout: 60_000,
  expect: { timeout: 10_000 },
  reporter: process.env['CI'] ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: process.env['E2E_BASE_URL'] ?? 'http://localhost:4200',
    locale: 'fr-FR',
    timezoneId: 'Europe/Paris',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    launchOptions: process.env['E2E_CHROMIUM']
      ? { executablePath: process.env['E2E_CHROMIUM'] }
      : {},
  },
  projects: [
    { name: 'desktop', use: { ...devices['Desktop Chrome'] }, testIgnore: /athlete-.*\.spec\.ts/ },
    // L'athlète utilise l'application sur son téléphone.
    { name: 'mobile', use: { ...devices['Pixel 7'] }, testMatch: /athlete-.*\.spec\.ts/ },
  ],
});
