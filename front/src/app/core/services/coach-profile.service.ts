import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export type CoachProfileStatus = 'DRAFT' | 'PENDING' | 'PUBLISHED' | 'SUSPENDED' | 'CLOSED';
export type OfferPeriodicity = 'MONTHLY' | 'QUARTERLY' | 'YEARLY' | 'PER_SESSION' | 'ONE_OFF';

export interface CoachCertification {
  id: string;
  label: string;
  organisation: string | null;
  obtainedYear: number | null;
}

export interface CoachOffer {
  id: string;
  name: string;
  description: string | null;
  amountCents: number;
  currency: string;
  periodicity: OfferPeriodicity;
  /** « / mois », « le forfait »… composé par le serveur pour s'écrire partout pareil. */
  suffix: string;
  /** Nul quand la formule n'a pas d'équivalent mensuel honnête (à la séance, forfait unique). */
  monthlyEquivalentCents: number | null;
  active: boolean;
  position: number;
}

export interface CoachProfile {
  id: string;
  slug: string;
  status: CoachProfileStatus;
  statusLabel: string;
  coachName: string | null;
  headline: string | null;
  bio: string | null;
  disciplines: string[];
  specialties: string[];
  levels: string[];
  languages: string[];
  city: string | null;
  country: string | null;
  remote: boolean;
  inPerson: boolean;
  experienceYears: number | null;
  capacityMax: number | null;
  submittedAt: string | null;
  publishedAt: string | null;
  reviewedAt: string | null;
  /** Motif d'un refus, écrit par un administrateur pour être lu par le coach. */
  reviewNote: string | null;
  medianResponseHours: number | null;
  /**
   * Chemin de la photo servi par l'API (`/public/coach-photos/…`), ou nul.
   *
   * Un chemin et non une adresse absolue : l'API n'a pas à connaître le domaine sous lequel elle
   * est servie. `photoSrc()` le compose.
   */
  photoUrl: string | null;
  certifications: CoachCertification[];
  offers: CoachOffer[];
  /** Ce qui manque pour soumettre. Vide = la fiche est prête. */
  missing: string[];
}

export interface CoachProfileForm {
  headline: string | null;
  bio: string | null;
  disciplines: string[];
  specialties: string[];
  levels: string[];
  languages: string[];
  city: string | null;
  country: string | null;
  remote: boolean;
  inPerson: boolean;
  experienceYears: number | null;
  capacityMax: number | null;
}

export interface VocabularyEntry {
  value: string;
  label: string;
}

export interface Vocabulary {
  disciplines: VocabularyEntry[];
  specialties: VocabularyEntry[];
  levels: VocabularyEntry[];
  periodicities: VocabularyEntry[];
}

/**
 * La fiche publique du coach connecté.
 *
 * Sous `/me` et non `/clubs/{clubId}/…` : cette vitrine appartient à une personne, pas à un club.
 * Un coach qui intervient dans deux clubs n'en a qu'une, et un indépendant n'a pas de club à
 * mettre dans l'adresse.
 */
@Injectable({ providedIn: 'root' })
export class CoachProfileService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/me/coach-profile`;

  /** La fiche du coach ; le serveur la crée en brouillon si elle n'existe pas encore. */
  get(): Observable<CoachProfile> {
    return this.http.get<CoachProfile>(this.base);
  }

  save(form: CoachProfileForm): Observable<CoachProfile> {
    return this.http.put<CoachProfile>(this.base, form);
  }

  submit(): Observable<CoachProfile> {
    return this.http.post<CoachProfile>(`${this.base}/submit`, {});
  }

  setAccepting(accepting: boolean): Observable<CoachProfile> {
    return this.http.post<CoachProfile>(`${this.base}/accepting`, {}, {
      params: new HttpParams().set('accepting', accepting),
    });
  }

  /**
   * Le vocabulaire servi par le serveur plutôt que codé ici : ce sont les mêmes valeurs qui
   * deviendront les facettes de l'annuaire, et deux listes qui divergent produisent un filtre
   * qui ne rend rien.
   */
  vocabulary(): Observable<Vocabulary> {
    return this.http.get<Vocabulary>(`${this.base}/vocabulary`);
  }

  addCertification(body: Omit<CoachCertification, 'id'>): Observable<CoachCertification> {
    return this.http.post<CoachCertification>(`${this.base}/certifications`, body);
  }

  deleteCertification(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/certifications/${id}`);
  }

  addOffer(body: OfferForm): Observable<CoachOffer> {
    return this.http.post<CoachOffer>(`${this.base}/offers`, body);
  }

  updateOffer(id: string, body: OfferForm): Observable<CoachOffer> {
    return this.http.put<CoachOffer>(`${this.base}/offers/${id}`, body);
  }

  /**
   * Remplace la photo.
   *
   * Le serveur ne garde jamais le fichier envoyé : il le décode, le réduit et le ré-encode en
   * JPEG — ce qui efface au passage les métadonnées EXIF, coordonnées GPS comprises.
   */
  uploadPhoto(file: File): Observable<CoachProfile> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<CoachProfile>(`${this.base}/photo`, form);
  }

  deletePhoto(): Observable<CoachProfile> {
    return this.http.delete<CoachProfile>(`${this.base}/photo`);
  }

  /** L'adresse complète d'une photo, à partir du chemin rendu par l'API. */
  photoSrc(photoUrl: string | null): string | null {
    return photoUrl ? `${environment.apiUrl}${photoUrl}` : null;
  }

  /** Retire la formule de la fiche : le serveur la désactive, il ne la supprime pas. */
  deactivateOffer(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/offers/${id}`);
  }
}

export interface OfferForm {
  name: string;
  description: string | null;
  amountCents: number;
  periodicity: OfferPeriodicity;
  active: boolean;
  position: number;
}
