import { expect, test } from '@playwright/test';
import { ATHLETE, login } from '../tests/support';
import { shoot } from './support';

/** L'athlète, sur son téléphone. */

test('athlète : sa journée, son calendrier, ses messages, ses progrès', async ({ page }) => {
  await login(page, ATHLETE, /\/athlete/);
  await page.goto('/athlete/today');
  await expect(page.getByText(/Bonjour Chloé/)).toBeVisible();
  await shoot(page, 'telephone/01-aujourdhui.jpg');

  await page.goto('/athlete/calendar');
  await shoot(page, 'telephone/02-calendrier.jpg');

  await page.goto('/athlete/messages');
  await page.getByText('Claire Vasseur').first().click();
  await expect(page.getByText(/Parfait, merci Claire/).last()).toBeVisible();
  await shoot(page, 'telephone/03-messages.jpg');

  await page.goto('/athlete/progress');
  await shoot(page, 'telephone/04-progres.jpg');
});
