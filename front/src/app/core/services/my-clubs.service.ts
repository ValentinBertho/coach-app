import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export type ClubRole = 'OWNER' | 'COACH_PRINCIPAL' | 'COACH_ASSISTANT';

export interface MyClub {
  id: string;
  name: string;
  /** L'espace où le coach a été créé : celui qui sert de défaut. */
  primary: boolean;
  role: ClubRole | null;
  roleLabel: string;
  soloPractice: boolean;
}

/**
 * Les espaces de travail du coach connecté.
 *
 * La pièce qui manquait au modèle multi-club : tout existait côté serveur — l'adhésion, le rôle, le
 * validateur qui accepte les clubs additionnels — sauf le moyen, pour l'interface, de savoir qu'un
 * second espace existe. Un coach invité ailleurs voyait son accès autorisé et ce club jamais.
 */
@Injectable({ providedIn: 'root' })
export class MyClubsService {
  private readonly http = inject(HttpClient);

  myClubs(): Observable<MyClub[]> {
    return this.http.get<MyClub[]>(`${environment.apiUrl}/me/clubs`);
  }
}
