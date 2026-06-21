import { Injectable, signal } from '@angular/core';

export interface ConfirmRequest {
  title: string;
  message: string;
  confirmLabel?: string;
  danger?: boolean;
}

interface PendingConfirm extends ConfirmRequest {
  resolve: (ok: boolean) => void;
}

/**
 * Confirmation via modale (remplace confirm() natif, cf. Claude.md). Le composant
 * app-confirm-dialog consomme `pending` et appelle resolve().
 */
@Injectable({ providedIn: 'root' })
export class ConfirmService {
  readonly pending = signal<PendingConfirm | null>(null);

  ask(request: ConfirmRequest): Promise<boolean> {
    return new Promise<boolean>((resolve) => {
      this.pending.set({ ...request, resolve });
    });
  }

  answer(ok: boolean): void {
    const p = this.pending();
    if (p) {
      p.resolve(ok);
      this.pending.set(null);
    }
  }
}
