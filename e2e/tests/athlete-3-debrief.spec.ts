import { expect, test } from '@playwright/test';
import { ATHLETE, COACH, login } from './support';

test('l\'athlète note sa séance et le coach reçoit le retour', async ({ page, browser }) => {
  const comment = `Bonnes jambes sur la fin (e2e ${Date.now()})`;

  await login(page, ATHLETE, /\/athlete/);
  await page.goto('/athlete/today');
  await page.getByRole('button', { name: 'Noter mon retour' }).click();

  await page.getByRole('radio', { name: 'Sensation bonne' }).click();
  await page.getByRole('button', { name: /^RPE 6/ }).click();
  await page.getByPlaceholder('Tu peux écrire un mot à ton entraîneur ici.').fill(comment);
  await page.getByRole('button', { name: /^Séance réalisée/ }).click();
  await expect(page.getByPlaceholder('Tu peux écrire un mot à ton entraîneur ici.')).toBeHidden();

  // Côté coach, dans une autre session : le retour est dans sa boîte de tri, « Ma journée ».
  // L'ancienne adresse de la file y mène toujours.
  const coach = await (await browser.newContext()).newPage();
  await login(coach, COACH, /\/app/);
  await coach.goto('/app/feedback');
  await expect(coach).toHaveURL(/\/app\/journee/);
  const row = coach.getByRole('listitem').filter({ hasText: comment });
  await expect(row).toBeVisible();

  // Le coach le coche et le marque traité : il sort de la file.
  await row.getByRole('checkbox').check();
  await coach.getByRole('button', { name: 'Marquer traité (1)' }).click();
  await expect(coach.getByText(comment)).toBeHidden();
});
