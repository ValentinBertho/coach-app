import { expect, test } from '@playwright/test';
import { COACH, login, skipInstallGuide } from '../tests/support';
import { shoot } from './support';

/** Le parcours d'une coach sur ordinateur, et ce qu'en voit un visiteur. */

test('visiteur : accueil, annuaire, fiche publique', async ({ page }) => {
  await skipInstallGuide(page);
  await page.goto('/');
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
  await shoot(page, 'ordinateur/01-accueil.jpg');

  await page.goto('/coachs');
  await expect(page.getByText('Claire Vasseur').first()).toBeVisible();
  await shoot(page, 'ordinateur/02-annuaire.jpg');

  await page.goto('/coachs/claire-vasseur');
  await expect(page.getByRole('heading', { level: 1, name: 'Claire Vasseur' })).toBeVisible();
  await shoot(page, 'ordinateur/03-fiche-publique.jpg');
});

test('coach : de la journée au plan de chaque athlète', async ({ page }) => {
  await login(page, COACH, /\/app/);
  await expect(page.getByText(/Bonjour Claire/)).toBeVisible();
  await shoot(page, 'ordinateur/10-tableau-de-bord.jpg');

  await page.goto('/app/journee');
  await expect(page.getByRole('heading', { name: 'Ma journée' })).toBeVisible();
  await shoot(page, 'ordinateur/11-ma-journee.jpg');

  await page.goto('/app/athletes');
  await expect(page.getByText('Chloé Dubois').first()).toBeVisible();
  await shoot(page, 'ordinateur/12-athletes.jpg');

  await page.getByText('Chloé Dubois').first().click();
  await expect(page).toHaveURL(/\/app\/athletes\/[0-9a-f-]+\/resume/);
  const athleteUrl = page.url().replace(/\/resume.*$/, '');
  await shoot(page, 'ordinateur/13-fiche-athlete.jpg');

  await page.goto(athleteUrl + '/programme');
  await shoot(page, 'ordinateur/14-programme-athlete.jpg');

  await page.goto(athleteUrl + '/load');
  await shoot(page, 'ordinateur/15-charge-athlete.jpg');

  await page.goto(athleteUrl + '/zones');
  await shoot(page, 'ordinateur/16-zones-athlete.jpg');

  await page.goto('/app/calendar');
  await expect(page.getByRole('heading', { name: 'Calendrier' })).toBeVisible();
  await shoot(page, 'ordinateur/17-calendrier.jpg');

  await page.goto('/app/messages');
  await page.getByText('Chloé Dubois').first().click();
  await expect(page.getByText(/Parfait, merci Claire/).last()).toBeVisible();
  await shoot(page, 'ordinateur/18-messages.jpg');

  await page.goto('/app/library/course');
  await expect(page.getByText('10 × 400 m VMA').first()).toBeVisible();
  await shoot(page, 'ordinateur/19-bibliotheque.jpg');

  await page.goto('/app/demandes');
  await expect(page.getByText('Léa Garnier')).toBeVisible();
  await shoot(page, 'ordinateur/20-demandes.jpg');

  await page.goto('/app/vitrine');
  await shoot(page, 'ordinateur/21-ma-fiche-publique.jpg');
});
