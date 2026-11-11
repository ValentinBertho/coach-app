import { importProvidersFrom } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { of } from 'rxjs';
import { ICONS } from '../../app.config';
import {
  CoachDashboardService,
  FeedbackQueueItem,
} from '../../core/services/coach-dashboard.service';
import { StrengthService } from '../../core/services/strength.service';
import { ToastService } from '../../core/services/toast.service';
import { WorkoutService } from '../../core/services/workout.service';
import { FeedbackQueueComponent } from './feedback-queue.component';

function item(n: number): FeedbackQueueItem {
  return {
    kind: 'COURSE',
    sessionId: `s${n}`,
    athleteId: `a${n}`,
    athleteName: `Athlète ${n}`,
    title: `Séance ${n}`,
    sessionDate: '2026-11-10',
    rpe: 5,
    fatigue: null,
    pain: null,
    feel: null,
    injuries: [],
    comment: null,
  };
}

/**
 * La boîte de tri : une action principale sur la sélection, pas un bouton plein par ligne.
 */
describe('FeedbackQueueComponent — sélection et « Marquer traité »', () => {
  let fixture: ComponentFixture<FeedbackQueueComponent>;
  let dashboard: jasmine.SpyObj<CoachDashboardService>;
  const items = [1, 2, 3, 4, 5, 6, 7].map(item);

  beforeEach(() => {
    dashboard = jasmine.createSpyObj<CoachDashboardService>('CoachDashboardService', [
      'feedbackQueue',
      'markFeedbackReviewed',
      'refreshPendingReviews',
    ]);
    dashboard.feedbackQueue.and.returnValue(of(items));
    dashboard.markFeedbackReviewed.and.returnValue(of({ marked: 2 }));
    dashboard.refreshPendingReviews.and.returnValue(of(5));

    TestBed.configureTestingModule({
      imports: [FeedbackQueueComponent],
      providers: [
        provideRouter([]),
        importProvidersFrom(LucideAngularModule.pick(ICONS)),
        { provide: CoachDashboardService, useValue: dashboard },
        { provide: WorkoutService, useValue: {} },
        { provide: StrengthService, useValue: {} },
        { provide: ToastService, useValue: jasmine.createSpyObj('ToastService', ['success', 'error']) },
      ],
    });
    fixture = TestBed.createComponent(FeedbackQueueComponent);
    fixture.componentRef.setInput('scope', 'mine');
    fixture.detectChanges();
  });

  it('lit la file sur le périmètre de l’écran et sur quatorze jours', () => {
    expect(dashboard.feedbackQueue).toHaveBeenCalledWith('mine', 14);
  });

  it('montre cinq retours, et déplie le reste sur place', () => {
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelectorAll('.fq__row').length).toBe(5);
    (el.querySelector('.fq__more') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(el.querySelectorAll('.fq__row').length).toBe(7);
  });

  it('marque la sélection en un seul appel et la retire de la file', () => {
    const c = fixture.componentInstance;
    c.toggle(items[0]);
    c.toggle(items[2]);
    c.markSelected();
    expect(dashboard.markFeedbackReviewed).toHaveBeenCalledOnceWith(
      [items[0], items[2]],
      'mine',
      14,
    );
    expect(c.items().map((i) => i.sessionId)).toEqual(['s2', 's4', 's5', 's6', 's7']);
    expect(c.selected().size).toBe(0);
    expect(dashboard.refreshPendingReviews).toHaveBeenCalled();
  });

  it('« Tout sélectionner » porte sur toute la file, lignes repliées comprises', () => {
    const c = fixture.componentInstance;
    c.toggleAll();
    expect(c.selected().size).toBe(7);
    c.toggleAll();
    expect(c.selected().size).toBe(0);
  });

  it('sans sélection, le bouton est inactif', () => {
    const btn = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('button'),
    ).find((b) => b.textContent?.includes('Marquer traité')) as HTMLButtonElement;
    expect(btn.disabled).toBeTrue();
  });
});
