import { defineConfig, devices } from '@playwright/test';

/**
 * Captures d'écran de présentation (documentation, page d'accueil, supports de démo).
 *
 * Ce ne sont pas des tests : un script qui parcourt l'application et photographie les écrans clés,
 * sur ordinateur et sur téléphone. À lancer sur une base FRAÎCHE en profil dev (le jeu de démo et
 * sa couche « présentation »), front et back démarrés :
 *
 *   npx playwright test -c playwright.captures.config.ts
 *
 * Les images sortent dans `docs/captures/` (voir captures/README.md).
 */
export default defineConfig({
  testDir: './captures',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 120_000,
  expect: { timeout: 15_000 },
  reporter: 'list',
  use: {
    baseURL: process.env['E2E_BASE_URL'] ?? 'http://localhost:4200',
    locale: 'fr-FR',
    timezoneId: 'Europe/Paris',
    trace: 'off',
    launchOptions: process.env['E2E_CHROMIUM']
      ? { executablePath: process.env['E2E_CHROMIUM'] }
      : {},
  },
  projects: [
    {
      name: 'ordinateur',
      use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 900 }, deviceScaleFactor: 2,
        colorScheme: 'light' },
      testMatch: /ordinateur\.spec\.ts/,
    },
    {
      name: 'telephone',
      use: { ...devices['Pixel 7'], colorScheme: 'dark' },
      testMatch: /telephone\.spec\.ts/,
    },
  ],
});
