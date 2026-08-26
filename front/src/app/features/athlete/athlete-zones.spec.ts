import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { importProvidersFrom } from '@angular/core';
import { LucideAngularModule } from 'lucide-angular';
import { ICONS } from '../../app.config';
import { AthleteZoneMetric, AthleteZoneSheet } from '../../core/models/athlete-zone-sheet.model';
import { PhysioProfile } from '../../core/models/physio.model';
import { AthleteZonesComponent } from './athlete-zones.component';

/**
 * Mes zones, côté athlète.
 *
 * <p>Ce que cet écran doit dire, et que l'athlète n'avait nulle part : la fourchette, son unité,
 * et d'où elle sort. Un chiffre d'allure sans sa règle ne se rattache à rien — et une valeur que
 * le coach a fixée à la main ne doit pas se présenter comme dérivée d'un seuil.</p>
 */
describe('athlete-zones — mon échelle de travail', () => {
  function metric(over: Partial<AthleteZoneMetric> = {}): AthleteZoneMetric {
    return {
      metricTypeId: 'm-pace', code: 'PACE', name: 'Allure',
      unit: 'S_PER_KM', format: 'MMSS',
      valueMin: 275, valueMax: 290, source: 'AUTO',
      anchor: 'LT2', highAnchor: null, lowPct: 88, highPct: 92, ...over,
    };
  }

  function zone(over: Partial<AthleteZoneSheet> = {}): AthleteZoneSheet {
    return {
      zoneId: 'z1', name: 'Endurance', color: '#11c08b', description: null,
      sortOrder: 0, metrics: [metric()], ...over,
    };
  }

  const physio: PhysioProfile = {
    discipline: 'ROUTE', lt1Ms: null, lt2Ms: null, vcMs: null,
    lt1Kmh: null, lt2Kmh: 15.24, vcKmh: null,
    fcMax: 190, fcLt1: null, fcLt2: 172,
    vcDomain1Pct: null, vcDomain2Pct: null, fcDomain1Pct: null, fcDomain2Pct: null, vdot: 52,
  };

  let component: AthleteZonesComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]), provideHttpClient(), provideHttpClientTesting(),
        importProvidersFrom(LucideAngularModule.pick(ICONS)),
      ],
    });
    component = TestBed.createComponent(AthleteZonesComponent).componentInstance;
  });

  it('écrit la fourchette dans l’unité de la métrique', () => {
    expect(component.range(metric())).toBe('4:35 – 4:50/km');
    expect(component.range(metric({ unit: 'BPM', format: 'INT', valueMin: 148, valueMax: 162 })))
      .toBe('148 – 162 bpm');
  });

  it('dit d’où sort la fourchette', () => {
    expect(component.rule(metric())).toBe('88–92 % · Seuil lactique (LT2)');
  });

  it('nomme les deux ancres quand la zone enjambe une frontière', () => {
    expect(component.rule(metric({ anchor: 'LT1', highAnchor: 'LT2', lowPct: 96, highPct: 90 })))
      .toBe('96 % LT1 → 90 % LT2');
  });

  it('n’invente pas de règle pour une valeur fixée par le coach', () => {
    expect(component.rule(metric({ source: 'MANUAL' }))).toBeNull();
  });

  it('masque une métrique déclarée mais jamais mesurée', () => {
    const z = zone({
      metrics: [
        metric(),
        metric({ metricTypeId: 'm-w', code: 'POWER', name: 'Puissance', unit: 'W', format: 'INT', valueMin: null, valueMax: null }),
      ],
    });
    expect(component.valued(z).map((m) => m.code)).toEqual(['PACE']);
  });

  it('ne dit la règle qu’une fois quand toutes les métriques partagent la même', () => {
    const z = zone({
      metrics: [metric(), metric({ metricTypeId: 'm-kmh', code: 'SPEED', name: 'Vitesse', unit: 'KMH', format: 'DEC1', valueMin: 12.4, valueMax: 13.1 })],
    });
    expect(component.sharedRule(z)).toBe('88–92 % · Seuil lactique (LT2)');
  });

  it('redescend la règle au niveau de la ligne dès que les métriques divergent', () => {
    const z = zone({
      metrics: [
        metric(),
        metric({ metricTypeId: 'm-hr', code: 'HR', name: 'FC', unit: 'BPM', format: 'INT', valueMin: 148, valueMax: 162, anchor: 'FCMAX', lowPct: 80, highPct: 90 }),
      ],
    });
    expect(component.sharedRule(z)).toBeNull();
  });

  it('ne hisse rien pour une zone à une seule métrique', () => {
    expect(component.sharedRule(zone())).toBeNull();
  });

  it('ne rappelle que les ancres dont l’échelle se sert', () => {
    component.zones.set([zone()]);
    component.physio.set(physio);
    // La zone n'est ancrée que sur LT2 : afficher FC max et le reste du profil serait un mur de
    // chiffres qui n'explique rien de ce qui est en dessous.
    expect(component.anchors()).toEqual([{ label: 'Seuil lactique (LT2)', value: '15,2 km/h' }]);
  });

  it('reste muet sur les ancres tant que le profil n’est pas chargé', () => {
    component.zones.set([zone()]);
    expect(component.anchors()).toEqual([]);
  });
});
