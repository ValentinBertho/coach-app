import { expect, test } from '@playwright/test';
import { ATHLETE, COACH, login } from './support';

test('l\'athlète écrit à son coach, qui lit le message', async ({ page, browser }) => {
  const text = `Je peux décaler la sortie longue à dimanche ? (e2e ${Date.now()})`;

  await login(page, ATHLETE, /\/athlete/);
  await page.goto('/athlete/messages');
  // Sur une base neuve, aucun fil n'existe encore avec le coach : on passe par « Nouveau message »,
  // qui rouvre le fil s'il existe déjà.
  await page.getByRole('button', { name: 'Nouveau message' }).click();
  await page.getByRole('dialog', { name: 'Nouveau message' })
    .getByRole('button', { name: /Claire Vasseur/ }).first().click();
  await page.getByPlaceholder('Ton message…').fill(text);
  await page.getByRole('button', { name: 'Envoyer' }).click();
  await expect(page.getByText(text, { exact: true })).toBeVisible();

  const coach = await (await browser.newContext()).newPage();
  await login(coach, COACH, /\/app/);
  await coach.goto('/app/messages');
  await coach.getByText(/Chloé Dubois/).first().click();
  await expect(coach.getByText(text, { exact: true })).toBeVisible();
});
