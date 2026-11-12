import { DOCUMENT } from '@angular/common';
import { Injectable, inject, signal } from '@angular/core';

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

  /**
   * L'utilisateur a-t-il <b>choisi</b> son thème, ou n'a-t-il simplement jamais rien dit ?
   *
   * <p>La distinction compte pour le portail athlète, dont la peau sombre est un parti pris
   * d'immersion et non un défaut hérité du système. Tant que rien n'a été choisi, ce parti pris
   * tient ; dès qu'un athlète exprime une préférence, c'est la sienne qui s'applique — y compris
   * « Système ». Sans ce drapeau, ouvrir le choix aux athlètes aurait éclairci du jour au
   * lendemain le portail de tous ceux qui n'ont rien demandé.</p>
   */
  readonly chosen = signal(this.hasStored());

  private readonly media = typeof matchMedia !== 'undefined'
    ? matchMedia('(prefers-color-scheme: dark)') : null;

  /**
   * Le document par injection plutôt que le global `document`.
   *
   * <p>Les fiches coachs sont pré-rendues dans Node à la compilation, où le global n'existe pas :
   * le service levait là-bas avant que quoi que ce soit ne soit rendu. Le jeton, lui, est fourni
   * des deux côtés — Angular donne au rendu serveur son propre DOM. Le thème est donc réellement
   * posé sur la page fabriquée, au lieu d'être simplement sauté.</p>
   *
   * <p>C'est la même prudence que `matchMedia` et `localStorage` juste au-dessus, qui étaient déjà
   * gainés ; seul `document` ne l'était pas.</p>
   */
  private readonly document = inject(DOCUMENT);

  /** À appeler une fois au démarrage de l'app. */
  init(): void {
    this.apply(this.preference());
    this.media?.addEventListener('change', () => {
      if (this.preference() === 'system') this.apply('system');
    });
  }

  set(pref: ThemePref): void {
    this.preference.set(pref);
    this.chosen.set(true);
    try { localStorage.setItem(KEY, pref); } catch { /* stockage indisponible */ }
    this.apply(pref);
  }

  private apply(pref: ThemePref): void {
    const dark = pref === 'dark' || (pref === 'system' && !!this.media?.matches);
    this.effective.set(dark ? 'dark' : 'light');
    const root = this.document.documentElement;
    if (dark) root.setAttribute('data-theme', 'dark');
    else root.removeAttribute('data-theme');
  }

  private hasStored(): boolean {
    try {
      const v = localStorage.getItem(KEY);
      return v === 'light' || v === 'dark' || v === 'system';
    } catch { return false; }
  }

  private readStored(): ThemePref {
    try {
      const v = localStorage.getItem(KEY);
      if (v === 'light' || v === 'dark' || v === 'system') return v;
    } catch { /* ignore */ }
    return 'system';
  }
}
