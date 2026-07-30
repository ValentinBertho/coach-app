import { Injectable, signal } from '@angular/core';

export type ToastLevel = 'success' | 'error' | 'info' | 'warning';

/** Action optionnelle proposée dans le toast (ex. « Annuler » après un déplacement). */
export interface ToastAction {
  label: string;
  run: () => void;
}

export interface Toast {
  id: number;
  level: ToastLevel;
  message: string;
  action?: ToastAction;
}

/**
 * Feedback utilisateur centralisé (cf. Claude.md : toast sur chaque action).
 * Les composants consomment le signal `toasts` ; les services/intercepteurs émettent
 * via success()/error()/info()/warning().
 */
@Injectable({ providedIn: 'root' })
export class ToastService {
  private seq = 0;
  readonly toasts = signal<Toast[]>([]);

  private push(level: ToastLevel, message: string, ttlMs = 4000, action?: ToastAction): void {
    const id = ++this.seq;
    this.toasts.update((list) => [...list, { id, level, message, action }]);
    setTimeout(() => this.dismiss(id), ttlMs);
  }

  success(message: string): void { this.push('success', message); }
  error(message: string): void { this.push('error', message, 6000); }
  info(message: string): void { this.push('info', message); }
  warning(message: string): void { this.push('warning', message); }

  /**
   * Toast portant une action réparatrice (« Annuler »). Durée de vie allongée : l'utilisateur
   * doit avoir le temps de lire ET de cliquer, ce qui n'est pas le cas des 4 s par défaut.
   */
  withAction(message: string, label: string, run: () => void, level: ToastLevel = 'success'): void {
    this.push(level, message, 8000, { label, run });
  }

  /** Déclenche l'action d'un toast puis le referme (une annulation ne se joue qu'une fois). */
  runAction(id: number): void {
    const toast = this.toasts().find((t) => t.id === id);
    this.dismiss(id);
    toast?.action?.run();
  }

  dismiss(id: number): void {
    this.toasts.update((list) => list.filter((t) => t.id !== id));
  }
}
