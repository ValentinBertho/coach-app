/* eslint-disable @typescript-eslint/no-explicit-any */
import { importProvidersFrom } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { of } from 'rxjs';
import { ICONS } from '../../app.config';
import { CoachDashboardService, FeedbackQueueItem } from '../../core/services/coach-dashboard.service';
import { ActivityService } from '../../core/services/activity.service';
import { MessageService } from '../../core/services/message.service';
import { StrengthService } from '../../core/services/strength.service';
import { ToastService } from '../../core/services/toast.service';
import { WorkoutService } from '../../core/services/workout.service';
import { FeedbackQueueComponent } from './feedback-queue.component';

function item(n: number, kind: 'COURSE' | 'STRENGTH' = 'COURSE'): FeedbackQueueItem {
  return {
    kind, sessionId: `s${n}`, athleteId: `a${n}`, athleteName: `Athlète ${n}`, title: `Séance ${n}`,
    sessionDate: '2026-11-10', rpe: 5, fatigue: null, pain: null, feel: null, injuries: [], comment: 'ok',
  };
}

/**
 * Lecture en série : répondre traite le retour et passe au suivant, sans quitter l'écran.
 * Le mot d'une séance course va SUR la séance ; celui d'une prépa physique part en message.
 */
describe('FeedbackQueueComponent — lecture en série', () => {
  let workouts: jasmine.SpyObj<WorkoutService>;
  let strength: jasmine.SpyObj<StrengthService>;
  let messages: jasmine.SpyObj<MessageService>;

  function create(list: FeedbackQueueItem[]) {
    const dashboard = jasmine.createSpyObj<CoachDashboardService>('CoachDashboardService', [
      'feedbackQueue', 'refreshPendingReviews',
    ]);
    dashboard.feedbackQueue.and.returnValue(of(list));
    dashboard.refreshPendingReviews.and.returnValue(of(0));
    workouts = jasmine.createSpyObj<WorkoutService>('WorkoutService', ['get', 'setCoachComment', 'markReviewed', 'acknowledge']);
    workouts.get.and.returnValue(of({ targetDistanceM: 9000, targetDurationS: 3000, coachComment: null } as any));
    workouts.setCoachComment.and.returnValue(of({} as any));
    workouts.markReviewed.and.returnValue(of({} as any));
    strength = jasmine.createSpyObj<StrengthService>('StrengthService', ['markScheduledReviewed']);
    strength.markScheduledReviewed.and.returnValue(of({} as any));
    messages = jasmine.createSpyObj<MessageService>('MessageService', ['coachSend']);
    messages.coachSend.and.returnValue(of({} as any));
    const activities = jasmine.createSpyObj<ActivityService>('ActivityService', ['forWorkout']);
    activities.forWorkout.and.returnValue(of(null));

    TestBed.configureTestingModule({
      imports: [FeedbackQueueComponent],
      providers: [
        provideRouter([]),
        importProvidersFrom(LucideAngularModule.pick(ICONS)),
        { provide: CoachDashboardService, useValue: dashboard },
        { provide: WorkoutService, useValue: workouts },
        { provide: StrengthService, useValue: strength },
        { provide: MessageService, useValue: messages },
        { provide: ActivityService, useValue: activities },
        { provide: ToastService, useValue: jasmine.createSpyObj('ToastService', ['success', 'error', 'info']) },
      ],
    });
    const fixture = TestBed.createComponent(FeedbackQueueComponent);
    fixture.componentRef.setInput('scope', 'mine');
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('envoie le mot sur la séance, traite le retour et passe au suivant', () => {
    const c = create([item(1), item(2)]);
    c.openReader(0);
    expect(c.plannedText()).toBe('9,0 km · 50 min');
    c.draft = 'Bien géré';
    c.sendAndNext();
    expect(workouts.setCoachComment).toHaveBeenCalledWith('a1', 's1', 'Bien géré');
    expect(workouts.markReviewed).toHaveBeenCalledWith('a1', 's1', true);
    expect(c.items().length).toBe(1);
    expect(c.current()?.sessionId).toBe('s2');
  });

  it('répond en message pour une séance de prépa physique, et ferme quand la file est vide', () => {
    const c = create([item(3, 'STRENGTH')]);
    c.openReader(0);
    c.draft = 'Charge ok';
    c.sendAndNext();
    expect(messages.coachSend).toHaveBeenCalledWith('a3', 'À propos de « Séance 3 » : Charge ok');
    expect(strength.markScheduledReviewed).toHaveBeenCalledWith('a3', 's3', true);
    expect(c.readerIndex()).toBeNull();
  });
});
