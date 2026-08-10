import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivityLap } from '../../../core/models/activity.model';
import { ActivityLapsComponent } from './activity-laps.component';

/**
 * Lecture d'une séance tour par tour.
 *
 * <p>L'écran des activités ne montrait que des moyennes — le seul chiffre qui ne dit rien d'un
 * 10 × 400 : ni l'allure des répétitions, ni leur dérive, ni ce qui se passe pendant la récup.
 * Ces tests fixent l'échelle des barres, qui est ce qui rend la séance lisible d'un coup d'œil.</p>
 */
describe('activity-laps — échelle de lecture', () => {
  let component: ActivityLapsComponent;

  function lap(index: number, paceSPerKm: number | null, distanceM = 400): ActivityLap {
    return {
      index, distanceM, durationS: paceSPerKm ? Math.round((paceSPerKm * distanceM) / 1000) : null,
      paceSPerKm, avgHr: null, maxHr: null, avgCadence: null, elevationGainM: null,
    };
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const fixture = TestBed.createComponent(ActivityLapsComponent);
    fixture.componentRef.setInput('activityId', 'a1');
    component = fixture.componentInstance;
  });

  /** Fractionné : trois efforts à 3'20 et deux récupérations à 5'40. */
  function fractionne(): void {
    component.data.set({
      kind: 'DEVICE',
      laps: [lap(1, 200), lap(2, 340, 200), lap(3, 205), lap(4, 340, 200), lap(5, 198)],
    });
  }

  it('nomme les tours de la montre pour ce qu’ils sont', () => {
    fractionne();
    expect(component.isDevice()).toBeTrue();

    component.data.set({ kind: 'SPLIT', laps: [lap(1, 300, 1000)] });
    expect(component.isDevice()).toBeFalse();
  });

  /**
   * L'échelle part du tour le plus rapide, pas de zéro : entre 3'20 et 5'40 il n'y a que 40 %
   * d'écart, mais c'est toute la séance. Une échelle absolue les rendrait presque identiques.
   */
  it('donne la barre pleine au tour le plus rapide et la plus courte au plus lent', () => {
    fractionne();

    expect(component.fastest()).toBe(198);
    expect(component.barWidth(lap(5, 198))).toBe(100);
    expect(component.barWidth(lap(2, 340, 200))).toBe(25);
    // Un effort légèrement moins bon reste visiblement proche des autres efforts.
    expect(component.barWidth(lap(3, 205))).toBeGreaterThan(90);
  });

  it('reste stable quand tous les tours sont à la même allure', () => {
    component.data.set({ kind: 'SPLIT', laps: [lap(1, 300, 1000), lap(2, 300, 1000)] });
    expect(component.barWidth(lap(1, 300, 1000))).toBe(100);
  });

  it('n’affiche pas de barre pour un tour sans allure', () => {
    fractionne();
    expect(component.barWidth(lap(6, null))).toBe(0);
  });

  /**
   * L'allure moyenne se calcule sur les totaux, jamais en moyennant les allures des tours :
   * sinon une récupération de 200 m pèse autant qu'une répétition d'un kilomètre, et la ligne
   * de moyennes annonce une allure que personne n'a courue.
   */
  it('moyenne l’allure sur les totaux, pas sur les tours', () => {
    component.data.set({
      kind: 'DEVICE',
      laps: [lap(1, 200, 1000), lap(2, 400, 200)],
    });

    const avg = component.averages();
    expect(avg?.distanceM).toBe(1200);
    // 200 s sur 1000 m + 80 s sur 200 m = 280 s pour 1200 m, soit 233 s/km — et non 300,
    // la moyenne naïve des deux allures.
    expect(avg?.paceSPerKm).toBe(233);
  });

  /** Les onglets ne s'affichent que sur ce que la sortie sait produire. */
  it('n’annonce que les découpes disponibles', () => {
    component.data.set({ kind: 'DEVICE', laps: [lap(1, 200)], availableKinds: ['DEVICE', 'SPLIT'] });
    expect(component.kinds()).toEqual(['DEVICE', 'SPLIT']);

    // Réponse ancienne, sans le champ : une seule découpe, la sienne.
    component.data.set({ kind: 'SPLIT', laps: [lap(1, 300, 1000)] });
    expect(component.kinds()).toEqual(['SPLIT']);
  });

  it('formate distances, allures et temps comme le reste de l’application', () => {
    expect(component.distLabel(lap(1, 200, 400))).toBe('400 m');
    expect(component.distLabel(lap(1, 300, 1000))).toBe('1 km');
    expect(component.distLabel(lap(1, 300, 1250))).toBe('1,25 km');
    expect(component.distLabel(lap(1, 300, 1200))).toBe('1,2 km');
    expect(component.paceLabel(lap(1, 200))).toBe("3'20");
    expect(component.paceLabel(lap(1, null))).toBe('—');
    expect(component.durLabel(lap(1, 300, 1000))).toBe('5:00');
  });
});
