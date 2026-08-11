import { plannedVolume, plannedVolumeLabel } from './planned-volume';

/**
 * Le volume prévu d'une séance sans allure prescrite.
 *
 * <p>Une séance « footing 55' » dont aucun bloc n'est chiffrable ne totalisait que ce que le
 * calcul savait convertir — ses éducatifs — et s'affichait « 0,1 km » pour une heure de course.
 * Ces tests fixent les deux gardes : on écarte une distance que l'allure implicite dément, et on
 * estime depuis la durée avec l'allure de l'athlète, jamais avec une valeur par défaut.</p>
 */
describe('planned-volume — volume prévu d’une séance', () => {
  /** Allure d'endurance typique : 5:30/km. */
  const REF = 330;

  it('garde une distance crédible telle quelle', () => {
    // 12 km en 1 h → 5:00/km : c'est une séance.
    expect(plannedVolume(12000, 3600, REF)).toEqual({ distanceM: 12000, indicative: false });
  });

  it('garde une distance seule, sans durée pour la contredire', () => {
    expect(plannedVolume(10000, null, REF)).toEqual({ distanceM: 10000, indicative: false });
  });

  it('écarte un total trop petit pour être une séance, et estime depuis la durée', () => {
    // Le cas observé : une séance dont le total ne compte que ses éducatifs.
    const v = plannedVolume(100, 3300, REF);
    expect(v?.indicative).toBeTrue();
    expect(v?.distanceM).toBe(10000); // 3300 s à 5:30/km = 10 km
  });

  /**
   * Le résidu ne se reconnaît pas à son incohérence : 100 m en 20 s donne 3'20/km, une allure
   * parfaitement plausible pour une séance qui ne l'est pas du tout. C'est la taille qui tranche.
   */
  it('écarte un résidu même quand son allure implicite est plausible', () => {
    const v = plannedVolume(100, 20, REF);
    // Ni la distance ni la durée ne décrivent une séance : rien à estimer non plus.
    expect(v).toBeNull();
  });

  it('écarte une durée trop courte pour être une séance', () => {
    expect(plannedVolume(null, 120, REF)).toBeNull();
    expect(plannedVolume(null, 180, REF)).not.toBeNull();
  });

  it('estime le volume d’une séance qui n’a qu’une durée', () => {
    expect(plannedVolume(null, 1800, REF)).toEqual({ distanceM: 5500, indicative: true });
  });

  /** Sans référence propre à l'athlète, on ne dit rien : un repère inventé serait pire. */
  it('n’invente rien quand l’athlète n’a aucune allure de référence', () => {
    expect(plannedVolume(null, 3300, null)).toBeNull();
    expect(plannedVolume(100, 3300, null)).toBeNull();
  });

  it('ne dit rien d’une séance sans volume du tout', () => {
    expect(plannedVolume(null, null, REF)).toBeNull();
  });

  /** Le « ≈ » est la seule chose qui distingue une estimation d'une consigne. */
  it('marque l’estimation, jamais la mesure', () => {
    expect(plannedVolumeLabel({ distanceM: 10000, indicative: true })).toBe('≈ 10 km');
    expect(plannedVolumeLabel({ distanceM: 12000, indicative: false })).toBe('12 km');
    // La décimale ne s'affiche que si elle apprend quelque chose.
    expect(plannedVolumeLabel({ distanceM: 9700, indicative: false })).toBe('9,7 km');
    expect(plannedVolumeLabel(null)).toBe('');
  });
});
