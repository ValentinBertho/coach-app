import { expect, test } from '@playwright/test';
import { ADMIN, PASSWORD, skipInstallGuide, totp } from './support';

test('l\'administrateur s\'enrôle en double authentification, puis se reconnecte avec un code de secours', async ({ page, context }) => {
  await skipInstallGuide(page);
  await page.goto('/login');
  await page.locator('#email').fill(ADMIN);
  await page.locator('#password').fill(PASSWORD);
  await page.getByRole('button', { name: 'Se connecter' }).click();

  // Premier passage sur une base neuve : enrôlement, le secret est affiché.
  const secret = (await page.getByTestId('mfa-secret').textContent())!.trim();
  expect(secret).toMatch(/^[A-Z2-7 ]+$/);

  await page.locator('#code').fill('000000');
  await page.getByRole('button', { name: 'Valider' }).click();
  await expect(page.locator('.auth-error')).toBeVisible();
  await expect(page).toHaveURL(/\/login/);

  await page.locator('#code').fill(totp(secret));
  await page.getByRole('button', { name: 'Valider' }).click();
  const codeCells = page.getByTestId('recovery-codes').locator('code');
  await expect(codeCells).toHaveCount(10);
  const codes = await codeCells.allTextContents();
  await page.getByRole('button', { name: 'C\'est noté, continuer' }).click();
  await expect(page).toHaveURL(/\/admin/);

  // Nouvelle connexion, téléphone perdu : un code de secours ouvre la session.
  await context.clearCookies();
  await page.evaluate(() => localStorage.clear());
  await skipInstallGuide(page);
  await page.goto('/login');
  await page.locator('#email').fill(ADMIN);
  await page.locator('#password').fill(PASSWORD);
  await page.getByRole('button', { name: 'Se connecter' }).click();
  await expect(page.getByTestId('mfa-secret')).toHaveCount(0);
  await page.locator('#code').fill(codes[0]);
  await page.getByRole('button', { name: 'Valider' }).click();
  await expect(page).toHaveURL(/\/admin/);
});
