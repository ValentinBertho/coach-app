import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { CalculatedBlock, CourseBlock } from '../../core/models/course.model';
import { SessionEditorComponent } from './session-editor.component';

/**
 * Totaux de séance : le temps de récupération compte autant de fois qu'il y a de répétitions.
 *
 * <p>Le compte s'arrêtait à l'avant-dernière : « 10 × 20 s côtes, récup 1'15 » n'en portait que
 * neuf, et la durée annoncée était plus courte de 1'15 que la séance réellement courue — l'écart
 * se répétant à chaque bloc fractionné. Une côte se termine en haut : la descente est due, y
 * compris après la dixième.</p>
 */
describe('session-editor — récupérations dans les totaux', () => {
  let component: SessionEditorComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    component = TestBed.createComponent(SessionEditorComponent).componentInstance;
  });

  /** Cible calculée minimale : seul le volume estimé entre dans les totaux. */
  function calc(durationS: number): CalculatedBlock {
    return {
      computable: true, basePaceSecPerKm: null, paceMinSecPerKm: null, paceMaxSecPerKm: null,
      paceMinLabel: null, paceMaxLabel: null, speedMinKmh: null, speedMaxKmh: null,
      hrMin: null, hrMax: null, rpeMin: null, rpeMax: null,
      estimatedDurationS: durationS, estimatedDistanceM: null,
    };
  }

  /** Pose un unique bloc de corps de séance, avec sa cible et celle de sa récupération. */
  function withMain(block: CourseBlock, blockDurationS: number, recoveryDurationS: number): void {
    component.structure.set({ warmup: [], main: [block], cooldown: [] });
    component.calc.set({ [block.id]: calc(blockDurationS) });
    component.recCalc.set({ [block.id]: calc(recoveryDurationS) });
  }

  /** Durée totale en minutes, telle que les totaux l'affichent (séances de moins d'une heure). */
  function totalMinutes(): number {
    return parseInt(component.sessionTotals()!.durationLabel!.replace(' min', ''), 10);
  }

  it('compte une récupération par répétition, la dernière comprise', () => {
    const hills: CourseBlock = {
      id: 'b1', type: 'intervals', reps: 10, durationS: 20,
      prescription: { zoneId: 'z-vo2' },
      recovery: { type: 'jog', durationS: 75, distanceM: null, prescription: { zoneId: 'z-rec' } },
    };
    withMain(hills, 10 * 20, 75);

    // 10 × 20 s de côte + 10 × 1'15 de descente = 950 s, soit 16 min arrondies.
    expect(totalMinutes()).toBe(Math.round((10 * 20 + 10 * 75) / 60));
  });

  /**
   * Entre deux séries, la récup de série remplace celle de la répétition : « 2 × (6 × 1000 m)
   * r90 s R5' » compte 11 récups de 90 s, pas 12 — la douzième est justement le R5'.
   */
  it('laisse la récup de série prendre la place de celle de la dernière répétition', () => {
    const sets: CourseBlock = {
      id: 'b2', type: 'intervals', reps: 6, distanceM: 1000, sets: 2,
      prescription: { zoneId: 'z-vo2' },
      recovery: { type: 'jog', durationS: 90, distanceM: null, prescription: { zoneId: 'z-rec' } },
      setRecovery: { type: 'jog', durationS: 300, distanceM: null, prescription: { zoneId: 'z-rec' } },
    };
    withMain(sets, 12 * 100, 90);

    expect(totalMinutes()).toBe(Math.round((12 * 100 + 11 * 90 + 300) / 60));
  });
});
