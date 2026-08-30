import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { importProvidersFrom } from '@angular/core';
import { LucideAngularModule } from 'lucide-angular';
import { ICONS } from '../../app.config';
import { TrainingZone, ZoneRule } from '../../core/models/training-zone.model';
import { TrainingZonesComponent } from './training-zones.component';

/**
 * Régler les pourcentages d'une zone depuis sa ligne.
 *
 * <p>Le geste courant — décaler un seuil de deux points — demandait d'ouvrir l'engrenage, de
 * passer le paragraphe sur les métriques, puis d'éditer bornes et références. Ce que ces tests
 * tiennent : le réglage atteint <b>toutes</b> les unités d'une même définition physiologique, et
 * <b>seulement</b> celles-là.</p>
 */
describe('zones — réglage des %', () => {
  let component: TrainingZonesComponent;
  let http: HttpTestingController;

  const PACE = 'm-pace';
  const SPEED = 'm-speed';
  const HR = 'm-hr';

  function rule(over: Partial<ZoneRule>): ZoneRule {
    return { metricTypeId: PACE, anchor: 'LT2', highAnchor: null, lowPct: 93, highPct: 97, model: 'CUSTOM', ...over };
  }

  function zone(rules: ZoneRule[]): TrainingZone {
    return {
      id: 'z1', name: 'Tempo', color: '#eab308', description: null, sortOrder: 3,
      scope: 'CLUB', discipline: null, builtin: false,
      metricTypeIds: rules.map((r) => r.metricTypeId), rules,
    };
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]), provideHttpClient(), provideHttpClientTesting(),
        importProvidersFrom(LucideAngularModule.pick(ICONS)),
      ],
    });
    component = TestBed.createComponent(TrainingZonesComponent).componentInstance;
    http = TestBed.inject(HttpTestingController);
    // La ligne affiche la règle d'allure : le composant doit savoir quelle métrique est l'allure.
    component.metrics.set([
      { id: PACE, clubId: null, code: 'PACE', name: 'Allure', unit: 'S_PER_KM', format: 'MMSS', direction: 'LOWER_HARDER', sortOrder: 0, builtin: true },
      { id: SPEED, clubId: null, code: 'SPEED', name: 'Vitesse', unit: 'KMH', format: 'DEC1', direction: 'HIGHER_HARDER', sortOrder: 1, builtin: true },
      { id: HR, clubId: null, code: 'HR', name: 'FC', unit: 'BPM', format: 'INT', direction: 'HIGHER_HARDER', sortOrder: 2, builtin: true },
    ]);
  });

  it('lit les deux bornes et rappelle leur référence', () => {
    const b = component.paceBounds(zone([rule({})]));
    expect(b?.low).toBe(93);
    expect(b?.high).toBe(97);
    expect(b?.refLabel).toBe('LT2');
  });

  it('nomme les deux références quand la zone enjambe la frontière', () => {
    const b = component.paceBounds(zone([rule({ anchor: 'LT1', highAnchor: 'LT2', lowPct: 100, highPct: 93 })]));
    expect(b?.refLabel).toBe('LT1 → LT2');
  });

  it('ne fait qu’une requête, en gardant l’autre borne', () => {
    const z = zone([rule({ metricTypeId: PACE }), rule({ metricTypeId: SPEED })]);
    component.zones.set([z]);

    component.savePct(z, 'high', '99');

    // Une seule : c'est le serveur qui sait quelles unités partagent l'ancre. Une requête par
    // métrique déclenchait autant de resynchronisations concurrentes, et le réglage échouait sur
    // un verrou optimiste.
    const reqs = http.match((r) => r.url.includes('/training-zones/z1/percentages'));
    expect(reqs.length).toBe(1);
    expect(reqs[0].request.body).toEqual({ lowPct: 93, highPct: 99 });
    reqs[0].flush(z);
  });

  it('refuse une saisie qui n’est pas un pourcentage', () => {
    const z = zone([rule({})]);
    component.zones.set([z]);
    component.savePct(z, 'low', 'abc');
    http.expectNone((r) => r.url.includes('/training-zones/'));
  });
});
