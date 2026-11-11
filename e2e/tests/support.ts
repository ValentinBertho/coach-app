import { createHmac } from 'node:crypto';
import { expect, type Page } from '@playwright/test';

export const PASSWORD = 'password1234';
export const COACH = 'demo@coachrun.fr';
export const ATHLETE = 'athlete@coachrun.fr';
export const ADMIN = 'admin@coachrun.fr';

/** Masque la feuille d'installation de l'application, qui recouvre l'écran au premier passage. */
export async function skipInstallGuide(page: Page): Promise<void> {
  await page.addInitScript(() => {
    try {
      localStorage.setItem('darilab.install-guide.snoozedAt', String(Date.now()));
    } catch {
      /* stockage indisponible : la feuille s'affichera, le test la fermera */
    }
  });
}

export async function login(page: Page, email: string, landing: RegExp): Promise<void> {
  await skipInstallGuide(page);
  await page.goto('/login');
  await page.locator('#email').fill(email);
  await page.locator('#password').fill(PASSWORD);
  await page.getByRole('button', { name: 'Se connecter' }).click();
  await expect(page).toHaveURL(landing);
}

/** Code TOTP (RFC 6238, SHA-1, 30 s, 6 chiffres) pour un secret en base32. */
export function totp(secret: string, at = Date.now()): string {
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
  let bits = 0;
  let value = 0;
  const bytes: number[] = [];
  for (const c of secret.replace(/[\s=]/g, '').toUpperCase()) {
    value = (value << 5) | alphabet.indexOf(c);
    bits += 5;
    if (bits >= 8) {
      bytes.push((value >>> (bits - 8)) & 0xff);
      bits -= 8;
    }
  }
  const counter = Buffer.alloc(8);
  counter.writeBigUInt64BE(BigInt(Math.floor(at / 1000 / 30)));
  const h = createHmac('sha1', Buffer.from(bytes)).update(counter).digest();
  const o = h[h.length - 1] & 0x0f;
  const n = ((h[o] & 0x7f) << 24) | (h[o + 1] << 16) | (h[o + 2] << 8) | h[o + 3];
  return String(n % 1_000_000).padStart(6, '0');
}
