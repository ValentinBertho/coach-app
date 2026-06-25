import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { WORKOUT_TYPE_LABELS, Workout } from '../../core/models/workout.model';
import { ScheduledStrength } from '../../core/models/strength.model';
import { Unavailability, UnavailabilityReason } from '../../core/models/unavailability.model';
import { AthletePortalService } from '../../core/services/athlete-portal.service';
import { ToastService } from '../../core/services/toast.service';
import { IntensityZoneBadgeComponent, type IntensityZone as ZoneNum } from '../../shared/components/physiology';
import { BottomSheetComponent } from '../../shared/components/ui';

interface DayRow {
  date: string;
  weekday: string;
  dayNum: number;
  isToday: boolean;
  workouts: Workout[];
  strength: ScheduledStrength[];
  unavailability: Unavailability | null;
}
interface MoveTarget { kind: 'run' | 'strength'; id: string; title: string; date: string; }
interface PickDay { date: string; label: string; isCurrent: boolean; }

function toIso(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}
function mondayOf(d: Date): Date {
  const date = new Date(d);
  const day = (date.getDay() + 6) % 7;
  date.setDate(date.getDate() - day);
  date.setHours(0, 0, 0, 0);
  return date;
}

const REASON_ICON: Record<UnavailabilityReason, string> = {
  INJURY: '🩹', ILLNESS: '🤒', VACATION: '🏖️', PERSONAL: '📌', OTHER: '🚫',
};

/**
 * Mon calendrier (athlète, mobile-first) — agenda vertical de la semaine.
 * L'athlète peut DÉPLACER une séance (date) mais jamais la modifier ni la
 * supprimer (invariant CDC §10). Lecture seule + action « Déplacer ».
 */
@Component({
  selector: 'app-athlete-calendar',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IntensityZoneBadgeComponent, BottomSheetComponent],
  template: `
    <header class="cal-top">
      <h1 class="display-sm">Mon calendrier</h1>
      <div class="week-nav">
        <button type="button" class="btn btn-ghost btn-sm" (click)="shift(-1)" aria-label="Semaine précédente">←</button>
        <button type="button" class="btn btn-ghost btn-sm" (click)="goThisWeek()">Cette semaine</button>
        <button type="button" class="btn btn-ghost btn-sm" (click)="shift(1)" aria-label="Semaine suivante">→</button>
      </div>
      <p class="subtitle">{{ periodLabel() }}</p>
    </header>

    <main class="agenda">
      @for (day of days(); track day.date) {
        <section class="day" [class.day--today]="day.isToday" [class.day--unavailable]="day.unavailability">
          <div class="day-hd">
            <span class="day-wd">{{ day.weekday }}</span>
            <span class="day-n metric">{{ day.dayNum }}</span>
            @if (day.unavailability; as u) {
              <span class="day-unavail">{{ reasonIcon[u.reason] }} indispo</span>
            }
          </div>

          <div class="day-items">
            @for (w of day.workouts; track w.id) {
              <button type="button" class="ses ses--run" (click)="openMove('run', w.id, w.title, w.scheduledDate)">
                <span class="ses-main">
                  <span class="ses-title">{{ typeLabels[w.type] }} · {{ w.title }}</span>
                  @if (w.targetDistanceM) { <span class="ses-km metric">{{ (w.targetDistanceM / 1000).toFixed(1) }} km</span> }
                </span>
                <span class="ses-zones">
                  @for (z of zonesOf(w); track z) { <app-intensity-zone-badge [zone]="z" /> }
                </span>
                <span class="ses-move" aria-hidden="true">⤺ déplacer</span>
              </button>
            }
            @for (s of day.strength; track s.id) {
              <button type="button" class="ses ses--strength" (click)="openMove('strength', s.id, s.title, s.scheduledDate)">
                <span class="ses-main">
                  <span class="ses-title">💪 {{ s.title }}</span>
                  @if (s.completed) { <span class="badge badge-success">Réalisé</span> }
                </span>
                <span class="ses-move" aria-hidden="true">⤺ déplacer</span>
              </button>
            }
            @if (day.workouts.length === 0 && day.strength.length === 0) {
              <p class="day-empty">—</p>
            }
          </div>
        </section>
      }
    </main>

    <!-- Déplacer une séance -->
    <app-bottom-sheet [(open)]="moveOpen" title="Déplacer la séance">
      @if (moveTarget(); as t) {
        <p class="move-lead">{{ t.title }}<br /><span class="field-hint">Le contenu de la séance reste inchangé.</span></p>
        <div class="pick-grid">
          @for (p of pickDays(); track p.date) {
            <button type="button" class="pick" [class.pick--current]="p.isCurrent" [disabled]="p.isCurrent" (click)="confirmMove(p.date)">
              {{ p.label }}
            </button>
          }
        </div>
      }
    </app-bottom-sheet>
  `,
  styles: [`
    :host { display: block; }
    .cal-top { padding: var(--sp-4) var(--sp-4) 0; max-width: 560px; margin-inline: auto; }
    .cal-top h1 { margin: 0; }
    .week-nav { display: flex; align-items: center; gap: var(--sp-2); margin: var(--sp-2) 0 var(--sp-1); }
    .subtitle { color: var(--ink-3); margin: 0; text-transform: capitalize; }

    .agenda { max-width: 560px; margin-inline: auto; padding: var(--sp-4); display: flex; flex-direction: column; gap: var(--sp-3); }
    .day { background: var(--paper); border: 1px solid var(--hairline); border-radius: var(--radius-lg); padding: var(--sp-3); }
    .day--today { border-color: var(--primary); box-shadow: 0 0 0 1px var(--primary); }
    .day--unavailable { background: repeating-linear-gradient(135deg, var(--paper) 0 10px, var(--paper-sunk) 10px 20px); }
    .day-hd { display: flex; align-items: baseline; gap: var(--sp-2); margin-bottom: var(--sp-2); }
    .day-wd { font-weight: 700; text-transform: capitalize; color: var(--ink-2); }
    .day-n { color: var(--ink-3); font-weight: 700; }
    .day-unavail { margin-left: auto; font-size: var(--text-xs); font-weight: 700; color: var(--ink-3); }
    .day-items { display: flex; flex-direction: column; gap: var(--sp-2); }
    .day-empty { color: var(--ink-4); margin: 0; padding: var(--sp-1) 0; }

    .ses {
      display: flex; flex-direction: column; gap: var(--sp-1); text-align: left;
      width: 100%; padding: var(--sp-3); border-radius: var(--radius);
      border: 1px solid var(--hairline); border-left: 4px solid var(--type-c, var(--primary));
      background: var(--paper); cursor: pointer;
    }
    .ses:active { transform: scale(0.99); }
    .ses--run { --type-c: var(--dari-teal); }
    .ses--strength { --type-c: var(--dari-violet); }
    .ses-main { display: flex; align-items: center; gap: var(--sp-2); flex-wrap: wrap; }
    .ses-title { font-weight: 700; color: var(--ink); flex: 1; }
    .ses-km { color: var(--ink-2); font-weight: 700; font-size: var(--text-sm); }
    .ses-zones { display: flex; flex-wrap: wrap; gap: var(--sp-1); }
    .ses-move { font-size: var(--text-xs); color: var(--ink-3); font-weight: 600; }

    .move-lead { font-weight: 700; color: var(--ink); margin: 0 0 var(--sp-4); }
    .pick-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: var(--sp-2); }
    .pick {
      min-height: 48px; border: 1px solid var(--hairline); background: var(--paper);
      border-radius: var(--radius); font-weight: 700; color: var(--ink-2); cursor: pointer;
      text-transform: capitalize;
    }
    .pick:active { transform: scale(0.97); }
    .pick--current { background: var(--paper-sunk); color: var(--ink-4); cursor: default; }
  `],
})
export class AthleteCalendarComponent implements OnInit {
  private readonly portal = inject(AthletePortalService);
  private readonly toast = inject(ToastService);

  readonly typeLabels = WORKOUT_TYPE_LABELS;
  readonly reasonIcon = REASON_ICON;
  private readonly weekdays = ['Lun', 'Mar', 'Mer', 'Jeu', 'Ven', 'Sam', 'Dim'];

  readonly anchor = signal<Date>(new Date());
  readonly workouts = signal<Workout[]>([]);
  readonly strength = signal<ScheduledStrength[]>([]);
  readonly unavailabilities = signal<Unavailability[]>([]);

  readonly moveOpen = signal(false);
  readonly moveTarget = signal<MoveTarget | null>(null);

  readonly days = computed<DayRow[]>(() => {
    const today = toIso(new Date());
    const start = mondayOf(this.anchor());
    const ws = this.workouts();
    const ss = this.strength();
    const un = this.unavailabilities();
    return Array.from({ length: 7 }, (_, i) => {
      const d = new Date(start);
      d.setDate(start.getDate() + i);
      const iso = toIso(d);
      return {
        date: iso,
        weekday: this.weekdays[i],
        dayNum: d.getDate(),
        isToday: iso === today,
        workouts: ws.filter((w) => w.scheduledDate === iso),
        strength: ss.filter((s) => s.scheduledDate === iso),
        unavailability: un.find((u) => iso >= u.startDate && iso <= u.endDate) ?? null,
      };
    });
  });

  readonly periodLabel = computed(() => {
    const start = mondayOf(this.anchor());
    const end = new Date(start);
    end.setDate(start.getDate() + 6);
    const fmt = new Intl.DateTimeFormat('fr-FR', { day: 'numeric', month: 'long' });
    return `${fmt.format(start)} – ${fmt.format(end)}`;
  });

  /** Jours proposés pour le déplacement : 14 jours à partir du lundi courant. */
  readonly pickDays = computed<PickDay[]>(() => {
    const t = this.moveTarget();
    const start = mondayOf(this.anchor());
    const fmt = new Intl.DateTimeFormat('fr-FR', { weekday: 'short', day: 'numeric', month: 'short' });
    return Array.from({ length: 14 }, (_, i) => {
      const d = new Date(start);
      d.setDate(start.getDate() + i);
      const iso = toIso(d);
      return { date: iso, label: fmt.format(d), isCurrent: !!t && iso === t.date };
    });
  });

  ngOnInit(): void { this.load(); }

  load(): void {
    const start = mondayOf(this.anchor());
    const end = new Date(start);
    end.setDate(start.getDate() + 13); // couvre la fenêtre de déplacement
    const from = toIso(start);
    const to = toIso(end);
    this.portal.workouts(from, to).subscribe({ next: (w) => this.workouts.set(w), error: () => this.workouts.set([]) });
    this.portal.ppScheduled(from, to).subscribe({ next: (s) => this.strength.set(s), error: () => this.strength.set([]) });
    this.portal.unavailabilities().subscribe({ next: (u) => this.unavailabilities.set(u), error: () => this.unavailabilities.set([]) });
  }

  shift(step: number): void {
    const d = new Date(this.anchor());
    d.setDate(d.getDate() + step * 7);
    this.anchor.set(d);
    this.load();
  }
  goThisWeek(): void { this.anchor.set(new Date()); this.load(); }

  openMove(kind: 'run' | 'strength', id: string, title: string, date: string): void {
    this.moveTarget.set({ kind, id, title, date });
    this.moveOpen.set(true);
  }

  confirmMove(targetDate: string): void {
    const t = this.moveTarget();
    if (!t || targetDate === t.date) return;
    this.moveOpen.set(false);

    if (t.kind === 'run') {
      const prev = this.workouts();
      this.workouts.set(prev.map((w) => (w.id === t.id ? { ...w, scheduledDate: targetDate } : w)));
      this.portal.moveWorkout(t.id, targetDate).subscribe({
        next: () => this.toast.success('Séance déplacée ✅'),
        error: () => { this.workouts.set(prev); this.toast.error('Déplacement impossible.'); },
      });
    } else {
      const prev = this.strength();
      this.strength.set(prev.map((s) => (s.id === t.id ? { ...s, scheduledDate: targetDate } : s)));
      this.portal.ppMove(t.id, targetDate).subscribe({
        next: () => this.toast.success('Séance déplacée ✅'),
        error: () => { this.strength.set(prev); this.toast.error('Déplacement impossible.'); },
      });
    }
  }

  zonesOf(w: Workout): ZoneNum[] {
    const present = new Set<number>();
    for (const s of w.steps) {
      if (s.zone) { const n = Number(s.zone.replace(/\D/g, '')); if (n >= 1 && n <= 5) present.add(n); }
    }
    return [...present].sort() as ZoneNum[];
  }
}
