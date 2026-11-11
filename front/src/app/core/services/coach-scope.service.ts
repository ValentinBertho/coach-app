import { Injectable, computed, signal } from '@angular/core';

/** Périmètre de lecture d'un coach : tout le club, ses athlètes, ses privés, ses athlètes club. */
export type CoachScope = 'all' | 'mine' | 'private' | 'club';

export const COACH_SCOPE_LABELS: Record<CoachScope, string> = {
  mine: 'Mes athlètes',
  all: 'Tout le club',
  private: 'Mes privés',
  club: 'Mes athlètes club',
};

/** Clé de stockage. L'ancienne clé de « Ma journée » est relue une fois, pour ne rien perdre. */
const KEY = 'coach-scope';
const LEGACY_KEY = 'coach-day-scope';

/**
 * Le périmètre choisi par le coach, partagé par tous ses écrans.
 *
 * <p>Chaque écran avait le sien : le tableau de bord lisait tout le club, « Ma journée » mes
 * athlètes, la pastille du menu tout le club encore, sur une autre fenêtre de temps. Le même
 * compteur « retours à traiter » affichait donc trois chiffres (115, 57, « 9+ ») pour une seule
 * question. Un seul périmètre, retenu entre deux sessions, et le chiffre est le même partout.</p>
 */
@Injectable({ providedIn: 'root' })
export class CoachScopeService {
  readonly scope = signal<CoachScope>(readScope());
  readonly label = computed(() => COACH_SCOPE_LABELS[this.scope()]);

  set(value: CoachScope): void {
    this.scope.set(value);
    try {
      localStorage.setItem(KEY, value);
    } catch {
      /* préférence non persistée, sans gravité */
    }
  }
}

function isScope(v: string | null): v is CoachScope {
  return v === 'all' || v === 'mine' || v === 'private' || v === 'club';
}

function readScope(): CoachScope {
  try {
    const v = localStorage.getItem(KEY) ?? localStorage.getItem(LEGACY_KEY);
    if (isScope(v)) return v;
  } catch {
    /* stockage indisponible */
  }
  // « Mes athlètes » : une file doit être la sienne pour valoir la peine d'être lue. Pour un
  // coach seul dans son club, c'est de toute façon tout le club.
  return 'mine';
}
