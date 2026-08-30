import { TestBed } from '@angular/core/testing';
import { ComponentRef } from '@angular/core';
import { importProvidersFrom } from '@angular/core';
import { LucideAngularModule } from 'lucide-angular';
import { ICONS } from '../../../app.config';
import { TrainingZone, ZoneRule } from '../../../core/models/training-zone.model';
import { ZonePickerComponent } from './zone-picker.component';

/**
 * Régler les % d'une zone depuis le sélecteur, en pleine construction de séance.
 *
 * <p>C'est là qu'un coach s'aperçoit qu'un seuil est deux points trop haut : en lisant l'allure
 * qu'il s'apprête à prescrire. Ce que ces tests tiennent : le sélecteur <b>propose</b> le réglage
 * et le remonte, sans jamais écrire lui-même — c'est l'écran qui possède les données qui décide.</p>
 */
describe('zone-picker — réglage des %', () => {
  let component: ZonePickerComponent;
  let ref: ComponentRef<ZonePickerComponent>;

  function rule(over: Partial<ZoneRule> = {}): ZoneRule {
    return { metricTypeId: 'm-pace', anchor: 'LT2', highAnchor: null, lowPct: 93, highPct: 97, model: 'CUSTOM', ...over };
  }

  function zone(over: Partial<TrainingZone> = {}): TrainingZone {
    return {
      id: 'z1', name: 'Tempo', color: '#eab308', description: null, sortOrder: 3,
      scope: 'CLUB', discipline: null, builtin: false,
      metricTypeIds: ['m-pace'], rules: [rule()], ...over,
    };
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [importProvidersFrom(LucideAngularModule.pick(ICONS))],
    });
    const fixture = TestBed.createComponent(ZonePickerComponent);
    component = fixture.componentInstance;
    ref = fixture.componentRef;
    ref.setInput('zones', [zone()]);
    ref.setInput('metrics', []);
    ref.setInput('values', []);
  });

  it('lit les deux bornes et rappelle leur ancre', () => {
    const b = component.bounds(zone());
    expect(b).toEqual({ low: 93, high: 97, refLabel: 'LT2' });
  });

  it('nomme les deux ancres quand la zone enjambe la frontière', () => {
    const b = component.bounds(zone({ rules: [rule({ anchor: 'LT1', highAnchor: 'LT2', lowPct: 100, highPct: 93 })] }));
    expect(b?.refLabel).toBe('LT1 → LT2');
  });

  it('ne propose rien pour une zone sans règle', () => {
    expect(component.bounds(zone({ rules: [] }))).toBeNull();
  });

  it('remonte le réglage plutôt que de l’écrire lui-même', () => {
    const seen: { zoneId: string; lowPct: number; highPct: number }[] = [];
    component.pctChange.subscribe((c) => seen.push(c));

    component.openTune('z1');
    component.applyTune('z1', '95', '99');

    expect(seen).toEqual([{ zoneId: 'z1', lowPct: 95, highPct: 99 }]);
    // Le panneau se referme : le réglage est parti, le menu redevient un menu.
    expect(component.tuneId()).toBeNull();
  });

  it('accepte la virgule décimale et refuse le reste', () => {
    const seen: unknown[] = [];
    component.pctChange.subscribe((c) => seen.push(c));

    component.applyTune('z1', '92,5', '97');
    expect(seen).toEqual([{ zoneId: 'z1', lowPct: 92.5, highPct: 97 }]);

    component.applyTune('z1', 'abc', '97');
    expect(seen.length).toBe(1);
  });

  it('referme le réglage en refermant le menu', () => {
    component.openTune('z1');
    component.close();
    expect(component.tuneId()).toBeNull();
  });
});
