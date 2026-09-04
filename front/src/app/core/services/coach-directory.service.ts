import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CoachCertification, CoachOffer } from './coach-profile.service';

export interface CoachSummary {
  slug: string;
  name: string;
  headline: string | null;
  disciplines: string[];
  specialties: string[];
  specialtyLabels: string[];
  languages: string[];
  city: string | null;
  remote: boolean;
  inPerson: boolean;
  experienceYears: number | null;
  /** Le moins cher ramené au mois ; nul si aucune formule n'a d'équivalent mensuel. */
  fromMonthlyCents: number | null;
  photoUrl: string | null;
  medianResponseHours: number | null;
  /** Faux quand le coach a fermé sa fiche : elle se consulte, elle ne se sollicite plus. */
  acceptingAthletes: boolean;
}

export interface CoachDetail extends CoachSummary {
  bio: string | null;
  levels: string[];
  country: string | null;
  capacityMax: number | null;
  certifications: CoachCertification[];
  /** Toujours vrai : les diplômes sont déclaratifs, et l'écran doit le dire. */
  certificationsDeclared: boolean;
  offers: CoachOffer[];
}

export interface FacetValue {
  value: string;
  label: string;
  count: number;
}

export interface CoachFacets {
  disciplines: FacetValue[];
  specialties: FacetValue[];
  languages: FacetValue[];
  cities: FacetValue[];
  total: number;
  accepting: number;
}

export interface DirectoryFilters {
  discipline?: string | null;
  specialty?: string | null;
  language?: string | null;
  city?: string | null;
  remote?: boolean | null;
  maxMonthlyCents?: number | null;
  acceptingOnly?: boolean | null;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/**
 * L'annuaire public. Aucune de ces routes ne demande de compte : c'est la vitrine, et la mettre
 * derrière une inscription reviendrait à demander un compte pour savoir s'il y a une raison d'en
 * créer un.
 */
@Injectable({ providedIn: 'root' })
export class CoachDirectoryService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  search(filters: DirectoryFilters, page = 0, size = 12): Observable<Page<CoachSummary>> {
    let params = new HttpParams().set('page', page).set('size', size);
    for (const [key, value] of Object.entries(filters)) {
      if (value !== null && value !== undefined && value !== '') {
        params = params.set(key, String(value));
      }
    }
    return this.http.get<Page<CoachSummary>>(`${this.base}/public/coaches`, { params });
  }

  /**
   * Le repli d'une recherche sans résultat.
   *
   * Une route à part, appelée sciemment : glisser ces coachs dans les résultats ferait croire que
   * le filtre a fonctionné.
   */
  suggestions(size = 12): Observable<Page<CoachSummary>> {
    return this.http.get<Page<CoachSummary>>(`${this.base}/public/coach-suggestions`, {
      params: new HttpParams().set('size', size),
    });
  }

  facets(): Observable<CoachFacets> {
    return this.http.get<CoachFacets>(`${this.base}/public/coach-facets`);
  }

  bySlug(slug: string): Observable<CoachDetail> {
    return this.http.get<CoachDetail>(`${this.base}/public/coaches/${slug}`);
  }

  photoSrc(photoUrl: string | null): string | null {
    return photoUrl ? `${this.base}${photoUrl}` : null;
  }

  /** « 90 € / mois », ou rien à afficher quand le coach n'annonce pas de tarif mensuel. */
  fromPrice(cents: number | null): string | null {
    return cents === null ? null : `à partir de ${(cents / 100).toLocaleString('fr-FR')} € / mois`;
  }
}
