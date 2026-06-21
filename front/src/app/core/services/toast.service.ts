import { Injectable, signal } from '@angular/core';

export type ToastLevel = 'success' | 'error' | 'info' | 'warning';

export interface Toast {
  id: number;
  level: ToastLevel;
  message: string;
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

  private push(level: ToastLevel, message: string, ttlMs = 4000): void {
    const id = ++this.seq;
    this.toasts.update((list) => [...list, { id, level, message }]);
    setTimeout(() => this.dismiss(id), ttlMs);
  }

  success(message: string): void { this.push('success', message); }
  error(message: string): void { this.push('error', message, 6000); }
  info(message: string): void { this.push('info', message); }
  warning(message: string): void { this.push('warning', message); }

  dismiss(id: number): void {
    this.toasts.update((list) => list.filter((t) => t.id !== id));
  }
}
