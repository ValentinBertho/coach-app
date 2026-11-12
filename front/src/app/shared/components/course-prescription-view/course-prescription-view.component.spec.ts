import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { CoursePrescriptionViewComponent } from './course-prescription-view.component';
import { WorkoutPrescription } from '../../../core/models/course.model';

/**
 * Prescription illisible.
 *
 * <p>Quand le serveur ne parvient pas à relire le contenu enregistré d'une séance, il renvoie
 * un contenu vide. Sans avertissement, l'écran montrait une séance sans rien dedans : l'athlète
 * comme le coach y lisaient « rien de prévu ». Le test verrouille l'avertissement.</p>
 */
describe('CoursePrescriptionViewComponent — prescription illisible', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [CoursePrescriptionViewComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
  });

  function render(rx: WorkoutPrescription | null): HTMLElement {
    const fixture = TestBed.createComponent(CoursePrescriptionViewComponent);
    fixture.componentRef.setInput('prescription', rx);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  const empty = { warmup: [], main: [], cooldown: [] };

  it('avertit au lieu de montrer une séance vide', () => {
    const el = render({
      title: 'Seuil',
      snapshot: null,
      calculated: empty,
      unreadable: true,
    } as unknown as WorkoutPrescription);

    const alert = el.querySelector('[role="alert"]');
    expect(alert).not.toBeNull();
    expect(alert!.textContent).toContain('n\'a pas pu être relu');
  });

  it('ne dit rien quand la séance n’a simplement pas de contenu structuré', () => {
    const el = render({
      title: 'Footing',
      snapshot: null,
      calculated: empty,
      unreadable: false,
    } as unknown as WorkoutPrescription);

    expect(el.querySelector('[role="alert"]')).toBeNull();
  });
});
