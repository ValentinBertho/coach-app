import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { PROPOSAL_TYPE_LABELS, Proposal } from '../../../core/models/decision.model';
import { DecisionService } from '../../../core/services/decision.service';
import { ToastService } from '../../../core/services/toast.service';
import { IconComponent } from '../icon/icon.component';

/**
 * La file de propositions du coach : ce que le produit suggère, et les deux seuls gestes possibles.
 *
 * <p><b>Pourquoi deux boutons et pas un.</b> « Appliquer » sans « Refuser » ferait de la
 * proposition une notification qu'on subit ; refuser est une décision qui compte, et le produit la
 * retient — la même suggestion ne reviendra pas. C'est aussi ce qui rend la file vidable, donc
 * lisible le lendemain.</p>
 *
 * <p>Les demandes d'athlète passent devant : elles attendent une réponse, alors qu'une détection
 * attend seulement un avis.</p>
 */
@Component({
  selector: 'app-proposal-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    @if (items().length > 0) {
      <section class="pl card">
        <header class="pl__hd">
          <h2>À décider</h2>
          <span class="pl__count metric">{{ items().length }}</span>
        </header>
        <p class="field-hint pl__intro">
          Le produit propose, tu décides. Rien n'est appliqué tant que tu n'as pas tranché.
        </p>

        <ul class="pl__list">
          @for (p of items(); track p.id) {
            <li class="pl__item" [class.pl__item--asked]="p.requestedByAthlete">
              <div class="pl__row">
                <span class="pl__tag">{{ typeLabel(p) }}</span>
                @if (p.athleteName) { <span class="pl__who">{{ p.athleteName }}</span> }
                @if (p.requestedByAthlete) {
                  <span class="pl__asked"><app-icon name="hand" [size]="13" /> demande</span>
                }
              </div>
              <strong class="pl__title">{{ p.title }}</strong>
              @if (p.reason) { <span class="pl__reason">{{ p.reason }}</span> }

              <div class="pl__actions">
                <button type="button" class="btn btn-accent btn-sm"
                        [disabled]="busy().has(p.id)" (click)="accept(p)">
                  Appliquer
                </button>
                <button type="button" class="btn btn-ghost btn-sm"
                        [disabled]="busy().has(p.id)" (click)="dismiss(p)">
                  Refuser
                </button>
              </div>
            </li>
          }
        </ul>
      </section>
    }
  `,
  styles: [`
    .pl { display: flex; flex-direction: column; gap: var(--sp-3); }
    .pl__hd { display: flex; align-items: baseline; gap: var(--sp-2); }
    .pl__hd h2 { margin: 0; }
    .pl__count {
      font-weight: 800; color: var(--primary); font-variant-numeric: tabular-nums;
    }
    .pl__intro { margin: 0; }

    .pl__list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; }
    .pl__item {
      display: flex; flex-direction: column; gap: var(--sp-1);
      padding: var(--sp-3) 0; border-top: 1px solid var(--hairline);
    }
    .pl__item:first-child { border-top: none; }
    .pl__item--asked { border-left: 3px solid var(--primary); padding-left: var(--sp-3); }

    .pl__row { display: flex; align-items: center; gap: var(--sp-2); flex-wrap: wrap; }
    .pl__tag {
      font-size: var(--text-2xs); font-weight: 700; letter-spacing: .08em; text-transform: uppercase;
      padding: 2px var(--sp-2); border-radius: var(--radius-full);
      background: var(--paper-sunk, var(--canvas)); color: var(--ink-3);
    }
    .pl__who { font-size: var(--text-sm); font-weight: 600; color: var(--ink-2); }
    .pl__asked {
      display: inline-flex; align-items: center; gap: 3px;
      font-size: var(--text-2xs); font-weight: 700; color: var(--primary);
    }
    .pl__title { color: var(--ink); }
    .pl__reason { font-size: var(--text-sm); color: var(--ink-3); }

    .pl__actions { display: flex; gap: var(--sp-2); margin-top: var(--sp-1); }
    .pl__actions .btn { min-height: 40px; }
  `],
})
export class ProposalListComponent {
  private readonly decisions = inject(DecisionService);
  private readonly toast = inject(ToastService);

  readonly proposals = input<Proposal[]>([]);

  /** Émis après chaque décision : le parent recharge ce que l'application a changé. */
  readonly decided = output<Proposal>();

  readonly busy = signal(new Set<string>());

  /** Les demandes d'athlète d'abord : elles attendent une réponse, pas un avis. */
  readonly items = computed(() =>
    [...this.proposals()].sort((a, b) => Number(b.requestedByAthlete) - Number(a.requestedByAthlete)));

  typeLabel(p: Proposal): string {
    return PROPOSAL_TYPE_LABELS[p.type] ?? p.type;
  }

  accept(p: Proposal): void {
    this.run(p, this.decisions.accept(p.id), 'Appliqué.');
  }

  dismiss(p: Proposal): void {
    this.run(p, this.decisions.dismiss(p.id), 'Proposition écartée.');
  }

  private run(p: Proposal, call: ReturnType<DecisionService['accept']>, done: string): void {
    if (this.busy().has(p.id)) return;
    this.busy.update((s) => new Set(s).add(p.id));
    call.subscribe({
      next: () => {
        this.busy.update((s) => {
          const next = new Set(s);
          next.delete(p.id);
          return next;
        });
        this.toast.success(done);
        this.decided.emit(p);
      },
      error: () => {
        this.busy.update((s) => {
          const next = new Set(s);
          next.delete(p.id);
          return next;
        });
        this.toast.error('Action impossible. La séance visée a peut-être changé.');
        this.decided.emit(p);
      },
    });
  }
}
