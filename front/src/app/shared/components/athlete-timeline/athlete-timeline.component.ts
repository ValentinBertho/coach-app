import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TIMELINE_ICONS, Timeline, TimelineEvent } from '../../../core/models/decision.model';
import { IconComponent } from '../icon/icon.component';

/**
 * Le fil de l'athlète — six mois de suivi rassemblés en une frise.
 *
 * <p><b>Le manque qu'il comble.</b> Tout existait, réparti sur six écrans : les tests d'un côté,
 * les records de l'autre, les courses ailleurs, les blessures déclarées au débrief enfouies dans
 * les séances, les coupures dans la fiche. Aucun endroit ne racontait l'histoire — et le coach qui
 * reprend un athlète après l'été n'avait rien à lire.</p>
 *
 * <p>Repliée au-delà de dix événements : une frise qui déroule six mois d'un coup n'est plus une
 * frise, c'est un journal.</p>
 */
@Component({
  selector: 'app-athlete-timeline',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, RouterLink, IconComponent],
  template: `
    <section class="tl card">
      <header class="tl__hd">
        <h2>Le fil</h2>
        <span class="field-hint">Tests, records, courses, blessures et coupures.</span>
      </header>

      @if (events().length === 0) {
        <p class="empty-hint">Rien d'enregistré sur cette période.</p>
      } @else {
        <ol class="tl__list">
          @for (e of shown(); track e.date + e.title) {
            <li class="tl__item" [class]="'tl__item--' + e.tone">
              <span class="tl__marker" aria-hidden="true">
                <app-icon [name]="icon(e)" [size]="14" />
              </span>
              <div class="tl__body">
                <span class="tl__date metric">{{ e.date | date: 'd MMM yyyy' }}</span>
                @if (e.link) {
                  <a class="tl__title" [routerLink]="e.link">{{ e.title }}</a>
                } @else {
                  <span class="tl__title">{{ e.title }}</span>
                }
                @if (e.detail) { <span class="tl__detail">{{ e.detail }}</span> }
              </div>
            </li>
          }
        </ol>

        @if (events().length > collapsedCount) {
          <button type="button" class="btn btn-ghost btn-sm" (click)="expanded.set(!expanded())">
            {{ expanded() ? 'Réduire' : 'Voir les ' + events().length + ' événements' }}
          </button>
        }
      }
    </section>
  `,
  styles: [`
    .tl { display: flex; flex-direction: column; gap: var(--sp-3); }
    .tl__hd { display: flex; flex-direction: column; gap: 2px; }

    .tl__list { list-style: none; margin: 0; padding: 0; position: relative; }
    /* Le trait vertical de la frise : une seule ligne, tracée derrière les pastilles. */
    .tl__list::before {
      content: ''; position: absolute; left: 11px; top: 12px; bottom: 12px;
      width: 2px; background: var(--hairline);
    }

    .tl__item {
      position: relative; display: flex; gap: var(--sp-3);
      padding: var(--sp-2) 0;
    }
    .tl__marker {
      position: relative; z-index: 1; flex-shrink: 0;
      width: 24px; height: 24px; border-radius: 50%;
      display: inline-flex; align-items: center; justify-content: center;
      background: var(--paper); border: 2px solid var(--mc, var(--ink-4)); color: var(--mc, var(--ink-3));
    }
    .tl__item--good    { --mc: var(--success); }
    .tl__item--watch   { --mc: var(--warning); }
    .tl__item--alert   { --mc: var(--danger); }
    .tl__item--neutral { --mc: var(--primary); }

    .tl__body { display: flex; flex-direction: column; gap: 1px; min-width: 0; }
    .tl__date {
      font-size: var(--text-xs); color: var(--ink-4); text-transform: lowercase;
      font-variant-numeric: tabular-nums;
    }
    .tl__title { font-weight: 600; color: var(--ink); }
    a.tl__title { color: var(--primary); text-decoration: none; }
    a.tl__title:hover { text-decoration: underline; }
    .tl__detail { font-size: var(--text-sm); color: var(--ink-3); }
  `],
})
export class AthleteTimelineComponent {
  readonly data = input<Timeline | null>(null);

  /** Au-delà, la frise devient un journal : on la replie. */
  readonly collapsedCount = 10;
  readonly expanded = signal(false);

  readonly events = computed(() => this.data()?.events ?? []);
  readonly shown = computed(() =>
    this.expanded() ? this.events() : this.events().slice(0, this.collapsedCount));

  icon(e: TimelineEvent): string {
    return TIMELINE_ICONS[e.kind] ?? 'circle';
  }
}
