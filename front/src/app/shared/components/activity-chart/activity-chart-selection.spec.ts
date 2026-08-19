import { ActivityStreamPoint } from '../../../core/models/activity.model';
import { selectionStats } from './activity-chart-selection';

/** Un échantillon de flux de montre. */
function pt(distanceM: number, elapsedS: number, hr: number | null = null): ActivityStreamPoint {
  return { distanceM, elapsedS, hr, paceSPerKm: null };
}

/**
 * Moyennes d'une portion de courbe.
 *
 * <p>L'arithmétique est simple ; ce sont les séries incomplètes qui produisent des chiffres faux —
 * un capteur muet, un arrêt au feu rouge, des échantillons irrégulièrement espacés. Chacun a sa
 * façon de rendre un nombre plausible et faux plutôt qu'une absence de nombre.</p>
 */
describe('selectionStats', () => {
  it('donne distance, durée et allure de la portion, bornes comprises', () => {
    const points = [pt(0, 0), pt(1000, 300), pt(2000, 600), pt(3000, 900)];

    const st = selectionStats(points, 1000, 3000)!;
    expect(st.distanceM).toBe(2000);
    expect(st.durationS).toBe(600);
    expect(st.paceSPerKm).withContext('600 s pour 2 km').toBe(300);
  });

  it('accepte des bornes données dans n’importe quel ordre', () => {
    const points = [pt(0, 0), pt(1000, 300), pt(2000, 600)];
    expect(selectionStats(points, 2000, 0)).toEqual(selectionStats(points, 0, 2000));
  });

  /**
   * Le piège central : moyenner les allures instantanées donne un poids égal à chaque
   * échantillon, si bien qu'une marche de quelques secondes pèse autant qu'un kilomètre couru.
   * On divise le temps par la distance, comme un coureur lit son chrono.
   */
  it('calcule l’allure sur le temps et la distance, pas en moyennant des allures', () => {
    // 1 km à 4'00, puis 200 m de marche à 12'00/km. Moyenner les deux allures donnerait 8'00.
    const points = [pt(0, 0), pt(1000, 240), pt(1200, 384)];

    const st = selectionStats(points, 0, 1200)!;
    expect(st.paceSPerKm).withContext('384 s pour 1,2 km = 320 s/km').toBe(320);
  });

  /**
   * La FC est pondérée par le temps : un flux de montre n'échantillonne pas régulièrement, et la
   * moyenne simple surpondère les portions denses — c'est-à-dire les plus lentes.
   */
  it('pondère la FC par le temps passé à chaque valeur', () => {
    // 60 s à 140 bpm, puis 240 s à 170 bpm. Moyenne simple : 155. Pondérée : 164.
    const points = [pt(0, 0, 140), pt(200, 60, 170), pt(1000, 300, 170)];

    const st = selectionStats(points, 0, 1000)!;
    expect(st.avgHr).toBe(164);
  });

  /** Un capteur qui décroche ne vaut pas zéro battement : les trous sortent du calcul. */
  it('ignore les échantillons sans FC au lieu de les compter pour zéro', () => {
    const points = [pt(0, 0, 160), pt(500, 150, null), pt(1000, 300, 160)];

    const st = selectionStats(points, 0, 1000)!;
    expect(st.avgHr).toBe(160);
  });

  it('rend une FC nulle quand la portion n’en porte aucune', () => {
    const st = selectionStats([pt(0, 0), pt(1000, 300)], 0, 1000)!;
    expect(st.avgHr).toBeNull();
    expect(st.paceSPerKm).toBe(300);
  });

  /** Une portion sans distance n'a pas d'allure — on ne divise pas par zéro pour en fabriquer une. */
  it('se tait sur l’allure quand la portion n’avance pas', () => {
    const stopped = [pt(1000, 0, 120), pt(1000, 30, 118), pt(1000, 60, 116)];

    const st = selectionStats(stopped, 900, 1100)!;
    expect(st.distanceM).toBe(0);
    expect(st.durationS).toBe(60);
    expect(st.paceSPerKm).withContext('à l’arrêt, pas d’allure').toBeNull();
    expect(st.avgHr).withContext('la FC, elle, reste lisible').toBe(119);
  });

  it('ne rend rien quand la sélection ne couvre pas deux points', () => {
    const points = [pt(0, 0), pt(1000, 300), pt(2000, 600)];
    expect(selectionStats(points, 1200, 1400)).withContext('aucun point dedans').toBeNull();
    expect(selectionStats(points, 950, 1050)).withContext('un seul point dedans').toBeNull();
    expect(selectionStats([], 0, 1000)).toBeNull();
  });
});
