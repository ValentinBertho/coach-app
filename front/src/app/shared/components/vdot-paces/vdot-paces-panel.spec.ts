import { importProvidersFrom } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { ICONS } from '../../../app.config';
import { Performance, Vdot } from '../../../core/models/physio.model';
import { VdotPacesPanelComponent } from './vdot-paces-panel.component';

/**
 * Le panneau que lisent le coach et l'athlète.
 *
 * <p>Ce qu'il doit tenir : traduire les codes du serveur en distances lisibles, ne montrer qu'un
 * chrono par distance — le meilleur —, et dire quoi faire quand il n'y a encore rien plutôt que
 * d'afficher un tableau vide qui se lirait comme une panne.</p>
 */
describe('records et allures', () => {
  let fixture: ComponentFixture<VdotPacesPanelComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [VdotPacesPanelComponent],
      providers: [provideRouter([]), importProvidersFrom(LucideAngularModule.pick(ICONS))],
    });
    fixture = TestBed.createComponent(VdotPacesPanelComponent);
  });

  function render(vdot: Vdot | null, performances: Performance[] | null = null): HTMLElement {
    fixture.componentRef.setInput('vdot', vdot);
    fixture.componentRef.setInput('performances', performances);
    fixture.componentRef.setInput('manageLink', ['/app/athletes', 'a1', 'tests']);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  const vdot: Vdot = {
    vdot: 50,
    paces: [
      { distance: '5km', paceSecPerKm: 234, paceLabel: '3:54', speedKmh: 15.4 },
      { distance: 'semi', paceSecPerKm: 253, paceLabel: '4:13', speedKmh: 14.2 },
      { distance: 'marathon', paceSecPerKm: 266, paceLabel: '4:26', speedKmh: 13.5 },
    ],
    trainingPaces: [
      { distance: 'EASY', paceSecPerKm: 335, paceLabel: '5:35', speedKmh: 10.7 },
      { distance: 'THRESHOLD', paceSecPerKm: 255, paceLabel: '4:15', speedKmh: 14.1 },
    ],
  };

  function perf(distance: string, timeSeconds: number, id = distance): Performance {
    return { id, distance, distanceCode: distance, timeSeconds, dateSet: '2026-05-01', vdot: 50 };
  }

  it('traduit les codes du serveur en distances lisibles', () => {
    const text = render(vdot).textContent ?? '';
    expect(text).withContext('« semi » brut ne se lit pas').toContain('Semi');
    expect(text).toContain('Marathon');
    expect(text).toContain('5 km');
  });

  it('nomme les allures d’entraînement plutôt que leurs codes', () => {
    const text = render(vdot).textContent ?? '';
    expect(text).toContain('Endurance fondamentale');
    expect(text).toContain('Seuil');
    expect(text).withContext('le code brut ne doit pas fuir dans l’écran').not.toContain('THRESHOLD');
  });

  /**
   * Un athlète court plusieurs fois la même distance au fil des saisons. Le panneau montre son
   * record ; l'historique complet appartient à l'écran de saisie.
   */
  it('ne garde que le meilleur chrono par distance', () => {
    const host = render(vdot, [
      perf('10km', 2520, 'lent'),
      perf('10km', 2400, 'record'),
      perf('5km', 1140),
    ]);
    const records = Array.from(host.querySelectorAll('.vpp-records li')).map((li) => li.textContent ?? '');
    expect(records.length).withContext('une ligne par distance, pas une par course').toBe(2);
    expect(records.join(' ')).toContain('40:00');
    expect(records.join(' ')).not.toContain('42:00');
  });

  it('affiche les chronos au format h:mm:ss au-delà de l’heure', () => {
    const host = render(vdot, [perf('marathon', 10_800)]);
    expect(host.textContent).toContain('3:00:00');
  });

  /** Sans chrono, un tableau vide se lirait comme une panne : on dit quoi faire. */
  it('invite à saisir un record quand il n’y a rien', () => {
    const host = render({ vdot: null, paces: [], trainingPaces: [] });
    expect(host.textContent).toContain('Aucun chrono de référence');
    expect(host.querySelector('a[href]')).withContext('un chemin vers la saisie').not.toBeNull();
    expect(host.querySelector('.vpp-paces')).withContext('aucune table à montrer').toBeNull();
  });

  /** L'API injoignable ne doit pas casser l'écran qui héberge le panneau. */
  it('tient sans données du tout', () => {
    expect(() => render(null)).not.toThrow();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Aucun chrono de référence');
  });
});
