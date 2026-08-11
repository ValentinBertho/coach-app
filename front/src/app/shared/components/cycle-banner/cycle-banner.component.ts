import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { CalendarNote } from '../../../core/models/calendar-note.model';
import { cyclePosition, currentCycle, cyclesOverlapping, daysLeft } from '../../../core/utils/cycle';
import { IconComponent } from '../icon/icon.component';

interface CycleRow {
  id: string;
  text: string;
  /** « Semaine 2 sur 4 » quand la période courante s'y trouve, sinon les dates. */
  detail: string;
  current: boolean;
}

/**
 * Le cycle dans lequel l'athlète se situe.
 *
 * <p><b>Pourquoi cet écran manquait.</b> Le coach pose des périodes sur le calendrier — « bloc
 * spécifique », « affûtage », « reprise » — et raisonne avec. L'athlète, lui, ne recevait que des
 * séances : trois semaines de côtes sans savoir qu'elles formaient un bloc, ni quand il finit.
 * Les mêmes séances lues comme une phase se vivent autrement, surtout la dernière semaine.</p>
 *
 * <p>Le composant n'affiche que des <b>cycles</b>. Les notes d'un seul jour posées par le coach
 * sur le même calendrier sont ses notes de travail — elles restent chez lui, et le serveur ne les
 * envoie pas ici.</p>
 */
@Component({
  selector: 'app-cycle-banner',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    @if (rows().length > 0) {
      <section class="cyc" aria-label="Mes cycles d'entraînement">
        @for (row of rows(); track row.id) {
          <div class="cyc__row" [class.cyc__row--now]="row.current">
            <app-icon name="blocks" [size]="15" />
            <span class="cyc__text">{{ row.text }}</span>
            <span class="cyc__detail">{{ row.detail }}</span>
          </div>
        }
      </section>
    }
  `,
  styles: [`
    .cyc { display: flex; flex-direction: column; gap: var(--sp-1); }
    .cyc__row {
      display: flex; align-items: center; gap: var(--sp-2);
      padding: var(--sp-2) var(--sp-3);
      border-radius: var(--radius);
      background: var(--paper-sunk); color: var(--ink-3);
      font-size: var(--text-sm);
    }
    .cyc__row--now {
      background: color-mix(in srgb, var(--dari-teal) 12%, transparent);
      color: var(--ink-1); font-weight: 600;
    }
    .cyc__text { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .cyc__detail { flex-shrink: 0; font-variant-numeric: tabular-nums; color: var(--ink-3); font-weight: 500; }
  `],
})
export class CycleBannerComponent {
  /** Cycles connus, tels que le serveur les a rendus pour la plage consultée. */
  readonly cycles = input.required<CalendarNote[]>();
  /** Date de référence (ISO) : aujourd'hui sur l'écran du jour, un jour de la plage sur l'agenda. */
  readonly today = input.required<string>();
  /**
   * Plage affichée. Absente, seul le cycle courant est montré — c'est le cas de l'écran
   * « Aujourd'hui », qui répond à une seule question : où j'en suis maintenant.
   */
  readonly from = input<string | null>(null);
  readonly to = input<string | null>(null);

  protected readonly rows = computed<CycleRow[]>(() => {
    const all = this.cycles();
    const today = this.today();
    const from = this.from();
    const to = this.to();

    const shown = from && to ? cyclesOverlapping(all, from, to) : listOf(currentCycle(all, today));
    const now = currentCycle(all, today);
    return shown.map((c) => ({
      id: c.id,
      text: c.text,
      detail: c.id === now?.id ? this.progressOf(c, today) : this.rangeOf(c),
      current: c.id === now?.id,
    }));
  });

  /** « Semaine 2 sur 4 », complété de « dernier jour » quand le cycle s'achève. */
  private progressOf(cycle: CalendarNote, today: string): string {
    const pos = cyclePosition(cycle, today);
    if (!pos) return this.rangeOf(cycle);
    const left = daysLeft(cycle, today);
    if (left === 0) return `Semaine ${pos.week}/${pos.total} · dernier jour`;
    return `Semaine ${pos.week}/${pos.total}`;
  }

  /** « 31 août → 13 sept. » pour un cycle qui n'est pas celui du jour. */
  private rangeOf(cycle: CalendarNote): string {
    const fmt = new Intl.DateTimeFormat('fr-FR', { day: 'numeric', month: 'short' });
    const start = fmt.format(new Date(`${cycle.noteDate}T00:00:00`));
    const end = cycle.endDate ? fmt.format(new Date(`${cycle.endDate}T00:00:00`)) : '';
    return end ? `${start} → ${end}` : start;
  }
}

function listOf<T>(value: T | null): T[] {
  return value ? [value] : [];
}
