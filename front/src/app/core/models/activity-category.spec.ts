import { Activity, activityCategoryLabel } from './activity.model';

/**
 * Catégorie affichée d'une sortie.
 *
 * <p>La famille (`sport`) sert au rapprochement : elle doit être grossière pour ça — course,
 * vélo, natation, renforcement. Mais ranger, c'est perdre. Un coach qui lit « Vélo » là où son
 * athlète a fait du gravel lit une information appauvrie, et un « Pickleball » que la famille ne
 * sait pas ranger n'a aucune raison de disparaître de l'écran.</p>
 */
describe('activityCategoryLabel', () => {
  const base = { sport: null, sportDetail: null } as Pick<Activity, 'sport' | 'sportDetail'>;

  it('préfère la catégorie exacte à la famille', () => {
    expect(activityCategoryLabel({ ...base, sport: 'RIDE', sportDetail: 'GravelRide' }))
      .toBe('Vélo gravel');
    expect(activityCategoryLabel({ ...base, sport: 'RUN', sportDetail: 'TrailRun' }))
      .toBe('Trail');
  });

  /** Le FIT nomme en minuscules soulignées ; c'est la même catégorie, pas une deuxième entrée. */
  it('reconnaît la même catégorie quelle que soit son écriture', () => {
    expect(activityCategoryLabel({ ...base, sport: 'RUN', sportDetail: 'trail_running' }))
      .toBe('Trail');
    expect(activityCategoryLabel({ ...base, sport: 'SWIM', sportDetail: 'open_water' }))
      .toBe('Nage en eau libre');
  });

  /**
   * Strava ajoute des types au fil de l'eau : un type absent de la table s'affiche quand même,
   * mis en forme. Afficher l'anglais vaut mieux que masquer le fait.
   */
  it("met en forme un type qu'il ne traduit pas plutôt que de le taire", () => {
    expect(activityCategoryLabel({ ...base, sport: null, sportDetail: 'Rafting' })).toBe('Rafting');
    expect(activityCategoryLabel({ ...base, sport: null, sportDetail: 'WaterTubing' }))
      .toBe('Water tubing');
    expect(activityCategoryLabel({ ...base, sport: null, sportDetail: 'sky_diving' }))
      .toBe('Sky diving');
  });

  /** Sans catégorie exacte — saisie manuelle, historique — la famille reprend la main. */
  it('retombe sur la famille quand la source n’a rien nommé', () => {
    expect(activityCategoryLabel({ ...base, sport: 'STRENGTH', sportDetail: null }))
      .toBe('Renforcement');
    expect(activityCategoryLabel({ ...base, sport: 'RIDE', sportDetail: '   ' })).toBe('Vélo');
  });

  /** Ni l'une ni l'autre : rien à afficher, et rien à inventer. */
  it('ne dit rien quand la source n’a rien déclaré du tout', () => {
    expect(activityCategoryLabel(base)).toBe('');
  });
});
