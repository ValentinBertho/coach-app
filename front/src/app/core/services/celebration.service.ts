import { Injectable, signal } from '@angular/core';

/** Ce qu'il y a à fêter : un titre court et, si on en a un, le chiffre qui donne envie. */
export interface Celebration {
  readonly id: number;
  readonly title: string;
  /** Ligne secondaire : « 12e retour d'affilée », « nouveau record »… */
  readonly detail?: string;
}

/**
 * Célébrations de validation — le token `.celebration` était spécifié dans `Design.md` mais
 * jamais utilisé nulle part.
 *
 * Remplir un ressenti est un effort sans récompense immédiate : l'athlète donne une donnée, et
 * il ne se passe rien. Une micro-animation, une vibration et un compteur de série changent ce
 * rapport — c'est ce qui fait la différence entre « je dois remplir » et « j'ai envie de tenir
 * ma série ». Le geste reste discret : ≤ 280 ms, et rien du tout si l'utilisateur a demandé
 * moins d'animations.
 */
@Injectable({ providedIn: 'root' })
export class CelebrationService {
  private seq = 0;
  /** Célébration en cours ; l'overlay la consomme et la laisse expirer. */
  readonly current = signal<Celebration | null>(null);

  /** Motif court de la vibration : un accusé de réception, pas une alarme. */
  private static readonly HAPTIC_PATTERN = [12, 40, 18];
  private static readonly DURATION_MS = 1600;

  fire(title: string, detail?: string): void {
    const id = ++this.seq;
    this.current.set({ id, title, detail });
    this.vibrate();
    setTimeout(() => {
      // Une célébration plus récente a pris la place : on ne l'efface pas.
      if (this.current()?.id === id) this.current.set(null);
    }, CelebrationService.DURATION_MS);
  }

  dismiss(): void { this.current.set(null); }

  /**
   * Retour haptique quand la plateforme le propose. `vibrate` est refusé sur iOS et par
   * certains navigateurs si le geste n'est pas utilisateur : l'échec est normal, pas une erreur.
   */
  private vibrate(): void {
    if (this.reducedMotion()) return;
    try { navigator.vibrate?.(CelebrationService.HAPTIC_PATTERN); } catch { /* non supporté */ }
  }

  reducedMotion(): boolean {
    return typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches;
  }
}
