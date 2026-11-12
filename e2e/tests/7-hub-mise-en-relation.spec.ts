import { expect, test } from '@playwright/test';
import { login, skipInstallGuide } from './support';

/**
 * Le hub, de bout en bout, sur les comptes du jeu de démonstration (`DemoSeedService.seedHub`) :
 * un visiteur trouve une coach dans l'annuaire public, la coach accepte la demande qui l'attend,
 * et l'athlète bascule dans l'espace d'entraînement.
 *
 * Tom (tom@coachrun.fr) a une demande en attente chez Sarah (sarah@coachrun.fr). L'acceptation
 * est à usage unique : comme les autres parcours, ce test suppose une base neuve.
 */
const SOLO_COACH = 'sarah@coachrun.fr';
const HUB_ATHLETE = 'tom@coachrun.fr';

test('un visiteur trouve une coach dans l\'annuaire public, sans compte', async ({ page }) => {
  await skipInstallGuide(page);
  await page.goto('/coachs');
  await expect(page.getByRole('heading', { level: 1, name: 'Trouver un coach' })).toBeVisible();
  await page.getByText('Sarah Lemoine').first().click();
  await expect(page).toHaveURL(/\/coachs\/sarah-lemoine/);
  await expect(page.getByRole('heading', { level: 1, name: 'Sarah Lemoine' })).toBeVisible();
  // Aucune coordonnée n'est publiée : la seule porte est la demande de coaching.
  await expect(page.getByText('sarah@coachrun.fr')).toHaveCount(0);
  await expect(page.getByText('Demander à être coaché').first()).toBeVisible();
});

test('la recherche libre et le tri de l\'annuaire répondent', async ({ page }) => {
  await skipInstallGuide(page);
  await page.goto('/coachs');
  await expect(page.getByText('Sarah Lemoine').first()).toBeVisible();

  // « grenoble », sans majuscule : seul le coach de Grenoble reste.
  await page.getByRole('searchbox', { name: 'Rechercher un coach' }).fill('grenoble');
  await expect(page.getByText('Malik Fournier').first()).toBeVisible();
  await expect(page.getByText('Sarah Lemoine')).toHaveCount(0);
  await expect(page.getByText('1 coach', { exact: true })).toBeVisible();

  // Recherche effacée, tri par tarif : la formule à 90 € passe devant celle à 110 €.
  await page.getByRole('searchbox', { name: 'Rechercher un coach' }).fill('');
  await expect(page.getByText('Sarah Lemoine').first()).toBeVisible();
  await page.getByLabel('Trier par').selectOption('PRICE');
  await expect(page.locator('.dir__name').first()).toHaveText('Sarah Lemoine');
});

test('la coach accepte la demande, et l\'athlète entre dans son espace d\'entraînement', async ({ page, browser }) => {
  // Avant : sans coach, l'athlète n'a que l'annuaire et ses demandes.
  const athlete = await (await browser.newContext()).newPage();
  await login(athlete, HUB_ATHLETE, /\/athlete|\/coachs/);
  await athlete.goto('/athlete/demandes');
  await expect(athlete.getByRole('link', { name: 'Mes demandes' })).toBeVisible();
  await expect(athlete.getByRole('link', { name: 'Calendrier' })).toHaveCount(0);

  await login(page, SOLO_COACH, /\/app/);
  await page.goto('/app/demandes');
  await expect(page.getByRole('heading', { level: 1, name: 'Demandes de coaching' })).toBeVisible();
  await expect(page.getByText('Tom Bertrand')).toBeVisible();
  await page.getByRole('button', { name: 'Accepter' }).first().click();
  await page.locator('.dialog').getByRole('button', { name: 'Accepter' }).click();
  await expect(page.getByText(/Tom Bertrand fait maintenant partie de vos athlètes/)).toBeVisible();

  // Après : nouvelle connexion, la navigation complète est là.
  const after = await (await browser.newContext()).newPage();
  await login(after, HUB_ATHLETE, /\/athlete/);
  await expect(after.getByRole('link', { name: 'Calendrier' })).toBeVisible();
});
