import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { TrainingZone } from '../../core/models/training-zone.model';
import { SessionEditorComponent } from './session-editor.component';

/**
 * Blocs rapides de l'éditeur course : chacun doit tomber sur la zone qu'il annonce, quel que soit
 * le modèle de zones du club.
 *
 * <p>Ils cherchaient un nom exact (« VO2 », « Récupération ») absent du modèle par défaut :
 * « 6×1000 m R2' » posait alors ses 1000 m en Footing facile, la première zone de la liste, et
 * l'athlète recevait une allure de footing pour un fractionné.</p>
 */
describe('session-editor — zones des blocs rapides', () => {
  let component: SessionEditorComponent;

  function zones(...names: string[]): TrainingZone[] {
    return names.map((name, i) => ({
      id: 'z-' + name, name, color: null, description: null, sortOrder: i, scope: 'CLUB',
      discipline: null, builtin: true, metricTypeIds: [], rules: [],
    }));
  }

  /** Modèle « seuils lactiques » par défaut, tel que le club de démonstration le porte. */
  const PAR_DEFAUT = zones(
    'Footing facile', 'EF', 'Steady', 'Seuil 1', 'Tempo', 'Seuil 2 bas', 'Seuil 2 haut',
    '10 km', '5 km', '3 km', '1500 m', '800 m', 'Endurance aérobie', 'Seuil', 'VO2max', 'Anaérobie',
  );

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    component = TestBed.createComponent(SessionEditorComponent).componentInstance;
  });

  function preset(label: string) {
    const p = component.presetsFor('main').find((x) => x.label === label);
    expect(p).withContext(label).toBeDefined();
    return p!.block;
  }

  it('pose le 6 × 1000 m à allure 5 km et sa récupération en zone basse', () => {
    component.zones.set(PAR_DEFAUT);
    const b = preset('6 × 1000 m, récup 2 min');
    expect(b.prescription?.zoneId).toBe('z-5 km');
    expect(b.recovery?.prescription?.zoneId).toBe('z-Footing facile');
  });

  it('pose le 10 × 400 m à allure 3 km, le seuil et le tempo sur leurs zones', () => {
    component.zones.set(PAR_DEFAUT);
    expect(preset('10 × 400 m, récup 1 min').prescription?.zoneId).toBe('z-3 km');
    expect(preset('Seuil 20 min').prescription?.zoneId).toBe('z-Seuil 2 bas');
    expect(preset('Tempo 4 km').prescription?.zoneId).toBe('z-Tempo');
  });

  it('retombe sur VO2max quand le modèle ne porte pas de zones d’allure de course', () => {
    component.zones.set(zones('Régénération', 'Endurance aérobie', 'Seuil', 'VO2max', 'Anaérobie'));
    const b = preset('6 × 1000 m, récup 2 min');
    expect(b.prescription?.zoneId).toBe('z-VO2max');
    expect(b.recovery?.prescription?.zoneId).toBe('z-Régénération');
    expect(preset('Seuil 20 min').prescription?.zoneId).toBe('z-Seuil');
  });
});
