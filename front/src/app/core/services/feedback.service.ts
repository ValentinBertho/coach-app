import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export type FeedbackKind = 'BUG' | 'IDEA' | 'QUESTION';

export interface FeedbackDraft {
  kind: FeedbackKind;
  message: string;
}

/**
 * Canal de retour de la bêta.
 *
 * <p>Remplace le lien `mailto:` : sur une PWA mobile il ouvre au mieux un client mail non
 * configuré, et il ne laissait aucune trace exploitable — ni file à traiter, ni statut, ni moyen
 * de relier un retour à une erreur serveur.</p>
 *
 * <p>Le service retient aussi le dernier identifiant de corrélation affiché à l'utilisateur. Le
 * serveur en produit un à chaque erreur interne et le renvoie dans la réponse ; personne ne le
 * récupérait. Joint au retour, il transforme « ça a planté quand j'ai enregistré » en une trace
 * précise, sans avoir à deviner.</p>
 */
@Injectable({ providedIn: 'root' })
export class FeedbackService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/feedback`;

  /** Ouverture du panneau de retour, pilotée depuis n'importe quel écran. */
  readonly panelOpen = signal(false);

  /** Dernier identifiant de corrélation d'erreur serveur vu par cet utilisateur. */
  readonly lastCorrelationId = signal<string | null>(null);

  open(): void {
    this.panelOpen.set(true);
  }

  close(): void {
    this.panelOpen.set(false);
  }

  /** Mémorise l'identifiant de corrélation d'une erreur 500 (appelé par l'intercepteur). */
  rememberCorrelation(id: string | null | undefined): void {
    if (id) this.lastCorrelationId.set(id);
  }

  send(draft: FeedbackDraft): Observable<void> {
    return this.http.post<void>(this.base, {
      kind: draft.kind,
      message: draft.message,
      page: location?.pathname ?? '',
      appVersion: environment.appVersion,
      correlationId: this.lastCorrelationId(),
    });
  }
}
