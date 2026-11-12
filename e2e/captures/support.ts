import type { Page } from '@playwright/test';
import { mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';

/** Dossier de sortie : `docs/captures/<appareil>/`. */
export const OUT = resolve(__dirname, '..', '..', 'docs', 'captures');

/**
 * Photographie la page une fois qu'elle est calme : réseau au repos, polices chargées, notifications
 * éphémères (toasts) masquées, curseur de saisie éteint.
 */
export async function shoot(page: Page, file: string, opts: { fullPage?: boolean } = {}): Promise<void> {
  await page.waitForLoadState('networkidle').catch(() => undefined);
  await page.evaluate(() => document.fonts.ready);
  await page.addStyleTag({
    content: `
      .toast, .toasts, .toast-container, [class*="toast"] { display: none !important; }
      * { caret-color: transparent !important; }
      *, *::before, *::after { animation-duration: 0s !important; transition-duration: 0s !important; }
    `,
  });
  await page.waitForTimeout(400);
  const path = resolve(OUT, file);
  mkdirSync(dirname(path), { recursive: true });
  await page.screenshot({ path, fullPage: opts.fullPage ?? false, type: 'jpeg', quality: 85 });
}
