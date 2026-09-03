import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { importProvidersFrom } from '@angular/core';
import { LucideAngularModule } from 'lucide-angular';
import { ICONS } from '../../app.config';
import { Workout } from '../../core/models/workout.model';
import { CalendarComponent } from './calendar.component';

/**
 * Un clic gauche sur une séance du calendrier ouvre sa fiche.
 *
 * <p>Le clic passe par un vrai bouton, dans un vrai navigateur : c'est le seul moyen de voir
 * ce que voit le coach. Le `cdkDrag` posé sur la même vignette, le menu contextuel et la
 * sélection au lasso partagent tous ce bouton — un test qui appellerait `onChipClick()`
 * directement ne dirait rien de leur cohabitation.</p>
 */
describe('calendrier — ouverture d\'une séance au clic', () => {
  let fixture: ComponentFixture<CalendarComponent>;
  let component: CalendarComponent;
  let navigate: jasmine.Spy;

  function today(): string {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]), provideHttpClient(), provideHttpClientTesting(),
        importProvidersFrom(LucideAngularModule.pick(ICONS)),
      ],
    });
    fixture = TestBed.createComponent(CalendarComponent);
    component = fixture.componentInstance;
    navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);

    fixture.componentRef.setInput('athleteId', 'ath-1');
    component.workouts.set([
      {
        id: 'w1', athleteId: 'ath-1', scheduledDate: today(), title: 'Seuil 3x8',
        type: 'THRESHOLD', status: 'PLANNED', steps: [],
      } as unknown as Workout,
    ]);
    fixture.detectChanges();
  });

  it('rend la séance comme une vignette cliquable', () => {
    const card = fixture.nativeElement.querySelector('.workout-card') as HTMLElement | null;
    expect(card).withContext('la vignette de séance doit être dans le DOM').not.toBeNull();
  });

  it('navigue vers la fiche de séance sur un clic gauche', () => {
    const card = fixture.nativeElement.querySelector('.workout-card') as HTMLElement;
    card.click();
    fixture.detectChanges();

    expect(navigate).toHaveBeenCalledWith(['/app/athletes', 'ath-1', 'workouts', 'w1']);
  });

  /** Le geste réel : le bouton est pressé puis relâché, comme sous une vraie souris. */
  it('navigue aussi sur une séquence pointeur complète', () => {
    const card = fixture.nativeElement.querySelector('.workout-card') as HTMLElement;
    const at = { clientX: 10, clientY: 10, button: 0, bubbles: true } as PointerEventInit;
    card.dispatchEvent(new PointerEvent('pointerdown', { ...at, pointerType: 'mouse' }));
    card.dispatchEvent(new MouseEvent('mousedown', at));
    card.dispatchEvent(new PointerEvent('pointerup', { ...at, pointerType: 'mouse' }));
    card.dispatchEvent(new MouseEvent('mouseup', at));
    card.dispatchEvent(new MouseEvent('click', at));
    fixture.detectChanges();

    expect(navigate).toHaveBeenCalledWith(['/app/athletes', 'ath-1', 'workouts', 'w1']);
  });
});
