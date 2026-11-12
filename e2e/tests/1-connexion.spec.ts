import { expect, test } from '@playwright/test';
import { COACH, login, skipInstallGuide } from './support';

test.describe('Connexion', () => {
  test('un mauvais mot de passe est refusé sans ouvrir de session', async ({ page }) => {
    await skipInstallGuide(page);
    await page.goto('/login');
    await page.locator('#email').fill(COACH);
    await page.locator('#password').fill('pas-le-bon-mot-de-passe');
    await page.getByRole('button', { name: 'Se connecter' }).click();
    await expect(page.locator('.auth-error')).toBeVisible();
    await expect(page).toHaveURL(/\/login/);
  });

  test('le coach se connecte, arrive sur son tableau de bord et se déconnecte', async ({ page }) => {
    await login(page, COACH, /\/app/);
    await expect(page.getByRole('heading', { level: 1 }).first()).toBeVisible();
    await expect(page.getByRole('navigation').getByRole('link', { name: 'Calendrier' })).toBeVisible();
    // La déconnexion vit dans le menu du compte, en bas du menu latéral.
    await page.getByRole('button', { name: /Claire Vasseur/ }).click();
    await page.getByRole('button', { name: 'Déconnexion' }).click();
    await expect(page).toHaveURL(/\/login|\/$/);
    // Session vraiment fermée : l'espace coach renvoie à la connexion.
    await page.goto('/app');
    await expect(page).toHaveURL(/\/login/);
  });
});
