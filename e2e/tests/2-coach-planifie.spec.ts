import { expect, test } from '@playwright/test';
import { COACH, login } from './support';

test('le coach planifie une séance de la bibliothèque dans le calendrier', async ({ page }) => {
  await login(page, COACH, /\/app/);
  await page.goto('/app/calendar');

  // Une carte de séance porte le résumé de sa structure : « 6×1000 m, récup 90 s 11,6 km ».
  const planned = page.getByRole('button', { name: /^6×1000 m, récup/ });
  const plan = page.getByRole('button', { name: 'Planifier une séance' });
  await expect(plan.first()).toBeVisible();
  const before = await planned.count();

  // Dernier jour affiché : vide dans le jeu de démonstration.
  await plan.last().click();
  const dialog = page.getByRole('dialog', { name: /Planifier une séance —/ });
  await expect(dialog).toBeVisible();

  // Sur grand écran, « Bibliothèque » ouvre la colonne latérale au lieu d'une liste dans la
  // modale : on y prend la séance et on la glisse sur le jour.
  await dialog.getByRole('button', { name: /^Bibliothèque/ }).click();
  await expect(dialog).toBeHidden();
  const library = page.getByRole('complementary', { name: 'Bibliothèque de séances' });
  await expect(library).toBeVisible();

  const source = library.getByText('6 × 1000 m au seuil').first();
  const target = page.locator('.day-body').last();
  await source.scrollIntoViewIfNeeded();
  await target.scrollIntoViewIfNeeded();
  const from = (await source.boundingBox())!;
  const to = (await target.boundingBox())!;
  // Le glisser-déposer du CDK suit la souris pas à pas : un saut direct ne déclenche rien.
  await page.mouse.move(from.x + from.width / 2, from.y + from.height / 2);
  await page.mouse.down();
  await page.mouse.move(from.x + from.width / 2 + 10, from.y + from.height / 2 + 10, { steps: 5 });
  await page.mouse.move(to.x + to.width / 2, to.y + to.height / 2, { steps: 20 });
  await page.mouse.up();
  await expect(planned).toHaveCount(before + 1);

  // Enregistrée côté serveur : elle survit à un rechargement.
  await page.reload();
  await expect(planned).toHaveCount(before + 1);
});
