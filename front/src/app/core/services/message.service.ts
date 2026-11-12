import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, map, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Message } from '../models/message.model';
import { AuthService } from './auth.service';
import { SseStream, StreamTokenService } from './stream-token.service';

/** Messagerie : fil coach (scopé club/athlète) et fil athlète (/me/messages). */
@Injectable({ providedIn: 'root' })
export class MessageService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly streamTokens = inject(StreamTokenService);

  /** Total de non-lus coach, partagé (badge de la navigation latérale). */
  readonly unread = signal(0);

  /**
   * Total de non-lus, tous fils confondus. La boîte de réception elle-même vit dans
   * {@link ConversationService} : ce service ne connaît plus que le fil d'un binôme, ouvert
   * depuis la fiche d'un athlète.
   */
  refreshUnread(): Observable<number> {
    return this.http
      .get<{ count: number }>(`${environment.apiUrl}/clubs/${this.auth.clubId()}/messages/unread-count`)
      .pipe(map((r) => r.count), tap((c) => this.unread.set(c)));
  }

  /** Accusé de lecture à l'ouverture d'un fil : les non-lus de cet athlète repassent à zéro. */
  markThreadRead(athleteId: string): Observable<void> {
    return this.http.post<void>(
      `${environment.apiUrl}/clubs/${this.auth.clubId()}/athletes/${athleteId}/messages/read`, {});
  }

  // Coach
  coachThread(athleteId: string): Observable<Message[]> {
    return this.http.get<Message[]>(
      `${environment.apiUrl}/clubs/${this.auth.clubId()}/athletes/${athleteId}/messages`
    );
  }
  coachSend(athleteId: string, body: string, workoutId?: string): Observable<Message> {
    return this.http.post<Message>(
      `${environment.apiUrl}/clubs/${this.auth.clubId()}/athletes/${athleteId}/messages`,
      { body, workoutId }
    );
  }

  // --- Fil d'une séance -------------------------------------------------------------------
  // Les mêmes messages que la messagerie, lus par leur rattachement à une séance. Pas un second
  // canal : la question du coach et la réponse de l'athlète se lisent là où elles ont été
  // écrites, et l'échange reste entier dans la boîte de réception.

  /** Fil d'une séance, vu du coach. */
  coachWorkoutThread(athleteId: string, workoutId: string): Observable<Message[]> {
    return this.http.get<Message[]>(
      `${environment.apiUrl}/clubs/${this.auth.clubId()}/athletes/${athleteId}/workouts/${workoutId}/thread`);
  }

  /** Fil d'une séance, vu de l'athlète. */
  myWorkoutThread(workoutId: string): Observable<Message[]> {
    return this.http.get<Message[]>(`${environment.apiUrl}/me/workouts/${workoutId}/thread`);
  }

  // Athlète
  myThread(): Observable<Message[]> {
    return this.http.get<Message[]>(`${environment.apiUrl}/me/messages`);
  }
  /**
   * Envoie un message au coach. `workoutId` rattache le message à une séance — c'est ce qui
   * permet de répondre à un mot du coach sans ouvrir un second canal parallèle : la réponse
   * arrive dans le fil où la conversation vit déjà, mais avec le contexte de la séance.
   */
  mySend(body: string, workoutId?: string): Observable<Message> {
    return this.http.post<Message>(`${environment.apiUrl}/me/messages`, { body, workoutId });
  }

  // --- Pièces jointes ---
  coachSendAttachment(athleteId: string, file: File, body?: string): Observable<Message> {
    const form = new FormData();
    form.append('file', file);
    if (body) form.append('body', body);
    return this.http.post<Message>(
      `${environment.apiUrl}/clubs/${this.auth.clubId()}/athletes/${athleteId}/messages/attachment`, form);
  }

  mySendAttachment(file: File, body?: string): Observable<Message> {
    const form = new FormData();
    form.append('file', file);
    if (body) form.append('body', body);
    return this.http.post<Message>(`${environment.apiUrl}/me/messages/attachment`, form);
  }

  private attachmentBase(athleteId: string | undefined, messageId: string): string {
    return athleteId
      ? `${environment.apiUrl}/clubs/${this.auth.clubId()}/athletes/${athleteId}/messages/${messageId}/attachment`
      : `${environment.apiUrl}/me/messages/${messageId}/attachment`;
  }

  /**
   * Contenu d'une pièce jointe, chargé par le code applicatif — donc avec l'en-tête
   * `Authorization`, et sans le moindre jeton dans l'URL.
   *
   * C'est ce qui alimente les vignettes du fil. Le jeton de session y transitait jusqu'ici en
   * paramètre d'URL, où il finissait dans les journaux d'accès du relais et dans l'historique de
   * navigation. Ici, il n'y a plus d'URL à faire fuir : l'appelant reçoit les octets et fabrique
   * une URL locale (`URL.createObjectURL`), comme le fait déjà l'export PDF.
   */
  attachmentBlob(athleteId: string | undefined, messageId: string): Observable<Blob> {
    return this.http.get(this.attachmentBase(athleteId, messageId), { responseType: 'blob' });
  }

  /**
   * URL ouvrable dans un onglet, portant un jeton **à usage unique** valable une minute.
   *
   * C'est le seul cas où le navigateur va chercher la pièce jointe lui-même : on ne peut donc pas
   * lui poser d'en-tête. Le jeton qu'il porte n'ouvre que les pièces jointes, ne sert qu'une fois,
   * et est demandé au moment du clic — pas à l'affichage du fil, où il aurait le temps de périmer.
   */
  attachmentUrl(athleteId: string | undefined, messageId: string): Observable<string> {
    return this.streamTokens.urlWithToken(
      this.attachmentBase(athleteId, messageId), 'ATTACHMENT');
  }

  /**
   * Flux temps réel (SSE) des nouveaux messages. Côté coach si `athleteId` est fourni, sinon côté
   * athlète. `EventSource` ne sait pas poser d'en-tête : l'authentification passe par un jeton de
   * flux à usage unique, renouvelé à chaque (re)connexion. Retourne le flux ouvert ; l'appelant
   * doit le fermer.
   */
  stream(athleteId: string | undefined, onMessage: (m: Message) => void): SseStream {
    const url = athleteId
      ? `${environment.apiUrl}/clubs/${this.auth.clubId()}/athletes/${athleteId}/messages/stream`
      : `${environment.apiUrl}/me/messages/stream`;
    return this.streamTokens.openSse(url, {
      message: (ev) => {
        try {
          onMessage(JSON.parse(ev.data) as Message);
        } catch {
          /* événement malformé : ignoré */
        }
      },
    });
  }
}
