import { Injectable, signal } from '@angular/core';

export interface ConfirmRequest {
  title: string;
  message: string;
  confirmLabel?: string;
  danger?: boolean;
  /**
   * Mot à recopier pour débloquer la confirmation (ex. `SUPPRIMER`). Absent = confirmation
   * simple, en un clic.
   *
   * <p>Réservé aux actions irréversibles et sans recours — la suppression d'un compte efface en
   * cascade profil, séances, activités, ressentis et messages, et la seule récupération possible
   * est la restauration sélective d'une sauvegarde. Un tap sur un écran mobile est trop court
   * pour ça ; recopier un mot rend le geste délibéré sans le rendre pénible.</p>
   */
  requiredText?: string;
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

  /** Confirmation à recopie : le bouton ne s'active qu'une fois `requiredText` saisi. */
  askForText(request: ConfirmRequest & { requiredText: string }): Promise<boolean> {
    return this.ask(request);
  }

  answer(ok: boolean): void {
    const p = this.pending();
    if (p) {
      p.resolve(ok);
      this.pending.set(null);
    }
  }
}
