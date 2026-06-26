import { Injectable, signal } from '@angular/core';

export type ThemePref = 'light' | 'dark' | 'system';

const KEY = 'darilab.theme';

/**
 * Thème clair / sombre (« instrument de jour » / « night-track »). La préférence est
 * persistée ; le mode `system` suit `prefers-color-scheme`. Le thème effectif est posé
 * sur <html data-theme="dark"> ; tout le reste passe par les tokens CSS.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  readonly preference = signal<ThemePref>(this.readStored());
  /** Thème réellement appliqué (résout `system`). */
  readonly effective = signal<'light' | 'dark'>('light');

  private readonly media = typeof matchMedia !== 'undefined'
    ? matchMedia('(prefers-color-scheme: dark)') : null;

  /** À appeler une fois au démarrage de l'app. */
  init(): void {
    this.apply(this.preference());
    this.media?.addEventListener('change', () => {
      if (this.preference() === 'system') this.apply('system');
    });
  }

  set(pref: ThemePref): void {
    this.preference.set(pref);
    try { localStorage.setItem(KEY, pref); } catch { /* stockage indisponible */ }
    this.apply(pref);
  }

  private apply(pref: ThemePref): void {
    const dark = pref === 'dark' || (pref === 'system' && !!this.media?.matches);
    this.effective.set(dark ? 'dark' : 'light');
    const root = document.documentElement;
    if (dark) root.setAttribute('data-theme', 'dark');
    else root.removeAttribute('data-theme');
  }

  private readStored(): ThemePref {
    try {
      const v = localStorage.getItem(KEY);
      if (v === 'light' || v === 'dark' || v === 'system') return v;
    } catch { /* ignore */ }
    return 'system';
  }
}
