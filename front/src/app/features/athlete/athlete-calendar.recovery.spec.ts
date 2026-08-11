import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { CalculatedBlockEntry, CourseRecovery } from '../../core/models/course.model';
import { AthleteCalendarComponent } from './athlete-calendar.component';

/**
 * Affichage de la récupération dans le détail de séance de l'agenda athlète.
 *
 * <p>Le détail n'affichait que l'<em>allure</em> de récupération, et seulement quand elle était
 * calculable. Un athlète lisait donc « 5 × 2000 m · 3:48–3:59/km » puis « récup · 4:48–5:50/km »
 * sans jamais savoir <b>combien de temps</b> il récupérait entre les répétitions — l'information
 * qui fait la différence entre une séance de seuil et une séance de VMA. Et quand la récupération
 * n'avait pas de cible d'allure — le cas courant, on trotte — la ligne disparaissait entièrement.</p>
 */
describe('athlete-calendar — volume de récupération', () => {
  let component: AthleteCalendarComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    component = TestBed.createComponent(AthleteCalendarComponent).componentInstance;
  });

  function entry(recovery: CourseRecovery | null): CalculatedBlockEntry {
    return {
      block: { id: 'b1', type: 'interval', reps: 5, distanceM: 2000, recovery },
      calc: null,
      recoveryCalc: null,
    } as unknown as CalculatedBlockEntry;
  }

  it('affiche une récupération en minutes et secondes', () => {
    expect(component.recoveryVol(entry({ type: 'jog', durationS: 90 }))).toBe("1'30");
  });

  it('garde les secondes seules sous la minute', () => {
    expect(component.recoveryVol(entry({ type: 'jog', durationS: 45 }))).toBe('45 s');
  });

  it('affiche les minutes rondes sans secondes', () => {
    expect(component.recoveryVol(entry({ type: 'jog', durationS: 180 }))).toBe('3 min');
  });

  /**
   * Le décimal s'écrit avec une virgule depuis que tous les écrans partagent la même écriture
   * (`prescription-format`) : le reste de l'application — tours d'une sortie, volume prévu —
   * l'écrivait déjà ainsi, et deux conventions dans la même langue sur le même écran, c'est une
   * de trop.
   */
  it('préfère la distance quand le coach l’a prescrite ainsi', () => {
    expect(component.recoveryVol(entry({ type: 'jog', distanceM: 400 }))).toBe('400 m');
    expect(component.recoveryVol(entry({ type: 'jog', distanceM: 1000 }))).toBe('1 km');
    expect(component.recoveryVol(entry({ type: 'jog', distanceM: 1500 }))).toBe('1,5 km');
  });

  /** Récup libre : rien à afficher, mais la ligne « récup » reste rendue par le gabarit. */
  it('renvoie une chaîne vide quand aucun volume n’est prescrit', () => {
    expect(component.recoveryVol(entry({ type: 'jog' }))).toBe('');
    expect(component.recoveryVol(entry(null))).toBe('');
  });
});
