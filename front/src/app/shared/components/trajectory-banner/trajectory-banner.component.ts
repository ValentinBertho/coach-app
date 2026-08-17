import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { Outlook, Trajectory } from '../../../core/models/decision.model';
import { IconComponent } from '../icon/icon.component';

const OUTLOOK_TONE: Record<Outlook, string> = {
  AHEAD: 'good',
  ON_TRACK: 'good',
  AT_RISK: 'watch',
  OUT_OF_REACH: 'alert',
  UNKNOWN: 'neutral',
};

const OUTLOOK_LABEL: Record<Outlook, string> = {
  AHEAD: 'Objectif dépassé',
  ON_TRACK: 'Sur la trajectoire',
  AT_RISK: 'Trajectoire serrée',
  OUT_OF_REACH: 'Objectif à revoir',
  UNKNOWN: 'Projection',
};

/**
 * La trajectoire vers la prochaine course.
 *
 * <p><b>Le manque qu'il comble.</b> L'objectif de course était un post-it : une date, une distance,
 * un chrono visé, et aucun calcul. Le produit connaissait pourtant le VDOT de l'athlète et savait
 * inverser Daniels — rien ne reliait l'effort du jour au but de la saison.</p>
 *
 * <p><b>Trois lignes, dans cet ordre.</b> Le compte à rebours, parce que c'est ce qu'on regarde en
 * premier ; l'écart en minutes, parce que c'est la seule unité qu'un coureur ressent ; puis ce qui
 * l'explique — la pente de progression, et la charge quand elle a quelque chose à dire. Un
 * pourcentage aurait tenu sur une ligne de moins et n'aurait rien voulu dire.</p>
 */
@Component({
  selector: 'app-trajectory-banner',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    @if (data(); as t) {
      <section class="tb card" [class]="'tb--' + tone()">
        <header class="tb__hd">
          <app-icon name="flag" [size]="18" />
          <strong class="tb__race">{{ t.raceName }}</strong>
          <span class="tb__countdown metric">{{ countdown() }}</span>
        </header>

        <p class="tb__headline">{{ t.headline }}</p>

        @if (t.targetTimeS !== null) {
          <div class="tb__badges">
            <span class="tb__badge">{{ label() }}</span>
            @if (t.vdotGap !== null && t.vdotGap > 0) {
              <span class="tb__badge tb__badge--soft metric">
                VDOT {{ t.currentVdot }} → {{ t.requiredVdot }}
              </span>
            }
          </div>
        }

        @if (t.detail) { <p class="tb__detail">{{ t.detail }}</p> }
        @if (t.loadNote) {
          <p class="tb__load">
            <app-icon name="triangle-alert" [size]="15" />
            <span>{{ t.loadNote }}</span>
          </p>
        }
      </section>
    }
  `,
  styles: [`
    .tb {
      display: flex; flex-direction: column; gap: var(--sp-2);
      border-left: 4px solid var(--tc, var(--ink-4));
    }
    .tb--good    { --tc: var(--success); }
    .tb--watch   { --tc: var(--warning); }
    .tb--alert   { --tc: var(--danger); }
    .tb--neutral { --tc: var(--ink-4); }

    .tb__hd { display: flex; align-items: center; gap: var(--sp-2); color: var(--tc); }
    .tb__race { color: var(--ink); flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; }
    .tb__countdown {
      font-weight: 700; color: var(--tc); white-space: nowrap;
      font-variant-numeric: tabular-nums;
    }

    .tb__headline {
      margin: 0; font-family: var(--font-display); font-weight: 700;
      font-size: var(--text-lg); line-height: 1.3; color: var(--ink);
    }
    .tb__badges { display: flex; gap: var(--sp-2); flex-wrap: wrap; }
    .tb__badge {
      font-size: var(--text-xs); font-weight: 700; letter-spacing: .02em;
      padding: 2px var(--sp-2); border-radius: var(--radius-full);
      border: 1px solid var(--tc); color: var(--tc);
    }
    .tb__badge--soft { border-color: var(--hairline); color: var(--ink-3); }

    .tb__detail { margin: 0; font-size: var(--text-sm); color: var(--ink-2); }
    .tb__load {
      margin: 0; display: flex; align-items: flex-start; gap: var(--sp-2);
      font-size: var(--text-sm); color: var(--warning); font-weight: 600;
    }
  `],
})
export class TrajectoryBannerComponent {
  readonly data = input<Trajectory | null>(null);

  readonly tone = computed(() => {
    const t = this.data();
    return t ? OUTLOOK_TONE[t.outlook] : 'neutral';
  });

  readonly label = computed(() => {
    const t = this.data();
    return t ? OUTLOOK_LABEL[t.outlook] : '';
  });

  /** « J-63 », ou « Aujourd'hui » le jour dit : un J-0 se lit mal. */
  readonly countdown = computed(() => {
    const days = this.data()?.daysLeft ?? 0;
    if (days <= 0) return "Aujourd'hui";
    if (days === 1) return 'Demain';
    return `J-${days}`;
  });
}
