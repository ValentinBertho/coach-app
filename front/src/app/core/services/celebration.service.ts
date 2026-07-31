import { Injectable, signal } from '@angular/core';

export interface Celebration {
  /** Ligne principale, ex. « Séance validée ». */
  title: string;
  /** Ligne secondaire, ex. « 12e retour d'affilée 🔥 ». Optionnelle. */
  detail?: string;
  /** Emoji affiché en grand. Par défaut 🎉, conformément à Design.md. */
  emoji?: string;
}

/** Durée d'affichage : assez pour être vue, trop courte pour bloquer un geste suivant. */
const VISIBLE_MS = 1600;

/**
 * Célébration à la validation (séance notée, record battu, objectif atteint).
 *
 * Le token `.celebration` était spécifié dans `Design.md` depuis le début et n'avait jamais été
 * utilisé : l'athlète validait son ressenti et il ne se passait rien. Or ce ressenti ne lui sert
 * à rien directement — il alimente le cockpit du coach. Sans accusé de réception ni série
 * visible, remplir devient une corvée sans contrepartie, et le taux de retour s'effondre.
 *
 * Trois canaux, tous facultatifs et dégradables :
 * - visuel : overlay court (≤ 320 ms d'animation), neutralisé par `prefers-reduced-motion` ;
 * - haptique : `navigator.vibrate`, ignoré silencieusement là où il n'existe pas (iOS) ;
 * - texte : la série (« 12e retour d'affilée »), qui est le vrai moteur d'habitude.
 */
@Injectable({ providedIn: 'root' })
export class CelebrationService {
  /** Célébration en cours, ou `null`. Rendue par `<app-celebration-overlay>`. */
  readonly current = signal<Celebration | null>(null);

  private timer?: ReturnType<typeof setTimeout>;

  celebrate(celebration: Celebration): void {
    this.current.set({ emoji: '🎉', ...celebration });
    this.vibrate();

    clearTimeout(this.timer);
    this.timer = setTimeout(() => this.current.set(null), VISIBLE_MS);
  }

  dismiss(): void {
    clearTimeout(this.timer);
    this.current.set(null);
  }

  /**
   * Motif court « tap-tap » : un accusé de réception, pas une alarme. La vibration ne
   * fonctionne qu'après une interaction utilisateur et n'existe pas sur iOS — l'échec est
   * normal et ne doit jamais remonter.
   */
  private vibrate(): void {
    if (typeof navigator === 'undefined' || typeof navigator.vibrate !== 'function') return;
    try {
      navigator.vibrate([14, 40, 22]);
    } catch {
      /* motif refusé par la plateforme : la célébration visuelle suffit */
    }
  }
}
