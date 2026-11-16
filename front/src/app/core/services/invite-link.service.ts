import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

/** Un lien d'invitation, vu par le coach qui l'a créé. */
export interface InviteLink {
  id: string;
  token: string;
  url: string;
  label: string | null;
  clubMembership: boolean;
  directEntry: boolean;
  groupId: string | null;
  groupName: string | null;
  maxUses: number | null;
  uses: number;
  expiresAt: string | null;
  revokedAt: string | null;
  /** Pourquoi le lien ne sert plus ; null s'il est actif. */
  inactiveReason: string | null;
  createdAt: string;
}

export interface InviteLinkRequest {
  label?: string | null;
  clubMembership: boolean;
  directEntry: boolean;
  groupId?: string | null;
  maxUses?: number | null;
  validDays?: number | null;
}

/** Ce que la page « Rejoindre » montre avant toute connexion. */
export interface InvitePreview {
  valid: boolean;
  reason: string | null;
  coachName: string;
  coachSlug: string | null;
  coachPhotoUrl: string | null;
  clubName: string;
  independentCoach: boolean;
  clubMembership: boolean;
  groupName: string | null;
  directEntry: boolean;
}

export type InviteOutcome = 'JOINED' | 'REQUESTED' | 'VERIFY_EMAIL';

@Injectable({ providedIn: 'root' })
export class InviteLinkService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  mine(): Observable<InviteLink[]> {
    return this.http.get<InviteLink[]>(`${this.base}/me/invite-links`);
  }

  create(req: InviteLinkRequest): Observable<InviteLink> {
    return this.http.post<InviteLink>(`${this.base}/me/invite-links`, req);
  }

  revoke(id: string): Observable<InviteLink> {
    return this.http.delete<InviteLink>(`${this.base}/me/invite-links/${id}`);
  }

  preview(token: string): Observable<InvitePreview> {
    return this.http.get<InvitePreview>(`${this.base}/public/invites/${token}`);
  }

  join(token: string, message?: string | null): Observable<{ outcome: InviteOutcome; coachName: string }> {
    return this.http.post<{ outcome: InviteOutcome; coachName: string }>(
      `${this.base}/me/invites/${token}/join`, { message: message || null });
  }

  photoSrc(path: string | null): string | null {
    return path ? `${this.base}${path}` : null;
  }
}
