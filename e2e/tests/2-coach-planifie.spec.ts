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
  await dialog.getByText('6 × 1000 m au seuil').first().click();
  await expect(dialog).toBeHidden();
  await expect(planned).toHaveCount(before + 1);

  // Enregistrée côté serveur : elle survit à un rechargement.
  await page.reload();
  await expect(planned).toHaveCount(before + 1);
});
