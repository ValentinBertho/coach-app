import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { AthletePortalService, DailyCheckIn } from '../../core/services/athlete-portal.service';
import { CelebrationService } from '../../core/services/celebration.service';
import { ToastService } from '../../core/services/toast.service';
import { IconComponent } from '../../shared/components/icon/icon.component';

/** Un curseur du check-in. `invert` : 10 = « au mieux » plutôt que « au pire ». */
interface Slider {
  key: 'sleep' | 'fatigue' | 'pain';
  label: string;
  low: string;
  high: string;
  invert: boolean;
}

const SLIDERS: Slider[] = [
  { key: 'sleep', label: 'Sommeil', low: 'mauvais', high: 'excellent', invert: true },
  { key: 'fatigue', label: 'Fatigue', low: 'frais', high: 'épuisé', invert: false },
  { key: 'pain', label: 'Douleur', low: 'aucune', high: 'forte', invert: false },
];

/**
 * Check-in matinal — trois curseurs, moins de dix secondes.
 *
 * Le problème qu'il règle : l'état de forme d'un athlète ne se mettait à jour qu'au retour
 * **post-séance**. Un athlète qui court le soir et note le lendemain (ou pas du tout) restait
 * affiché « aucun retour récent » toute la journée, précisément quand le coach décide d'alléger
 * ou de maintenir. La forme doit exister *avant* la séance, pas seulement après.
 *
 * Invariant respecté : seules **fatigue et douleur** alimentent la forme. Le sommeil est un
 * contexte utile au coach — comme le RPE, il n'entre jamais dans le calcul.
 *
 * Facultatif par construction : replié une fois rempli, jamais bloquant, jamais rappelé deux
 * fois. Un check-in qu'on subit n'est pas rempli le lendemain.
 */
@Component({
  selector: 'app-morning-check-in',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    @if (!dismissed()) {
      <section class="ci card" [class.ci--done]="saved()">
        @if (saved() && !expanded()) {
          <!-- Rempli : une ligne de rappel, et la possibilité de se corriger. -->
          <button type="button" class="ci__recap" (click)="expanded.set(true)">
            <app-icon name="check" [size]="16" />
            <span class="ci__recap-txt">Check-in du matin enregistré</span>
            <span class="ci__recap-vals metric">
              @if (fatigue() !== null) { fatigue {{ fatigue() }} }
              @if (pain() !== null) { · douleur {{ pain() }} }
            </span>
            <app-icon name="chevron-right" [size]="16" />
          </button>
        } @else {
          <header class="ci__hd">
            <div>
              <strong class="ci__title">Comment tu te sens ce matin ?</strong>
              <span class="field-hint">Trois curseurs, dix secondes. Ton coach voit ta forme avant ta séance.</span>
            </div>
            @if (!saved()) {
              <button type="button" class="ci__close" aria-label="Masquer le check-in"
                      (click)="dismissed.set(true)"><app-icon name="x" [size]="16" /></button>
            }
          </header>

          <div class="ci__sliders">
            @for (s of sliders; track s.key) {
              <div class="ci__slider">
                <div class="ci__slider-hd">
                  <label [attr.for]="'ci-' + s.key">{{ s.label }}</label>
                  <output class="ci__val metric" [attr.for]="'ci-' + s.key">{{ valueOf(s.key) ?? '—' }}</output>
                </div>
                <input type="range" min="0" max="10" step="1" class="ci__range"
                       [id]="'ci-' + s.key"
                       [value]="valueOf(s.key) ?? 5"
                       [class.ci__range--inverted]="s.invert"
                       [attr.aria-valuetext]="(valueOf(s.key) ?? 5) + ' sur 10'"
                       (input)="onSlide(s.key, $event)" />
                <div class="ci__ends"><span>{{ s.low }}</span><span>{{ s.high }}</span></div>
              </div>
            }
          </div>

          <button type="button" class="btn btn-accent ci__submit" [disabled]="saving() || !touched()"
                  (click)="save()">
            {{ saving() ? 'Enregistrement…' : (saved() ? 'Mettre à jour' : 'Enregistrer') }}
          </button>
        }
      </section>
    }
  `,
  styles: [`
    .ci { display: flex; flex-direction: column; gap: var(--sp-3); }
    .ci--done { border-color: var(--success); }
    .ci__hd { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--sp-2); }
    .ci__title { display: block; color: var(--ink); }
    .ci__close {
      min-width: 44px; min-height: 44px; flex-shrink: 0;
      display: inline-flex; align-items: center; justify-content: center;
      border: none; background: transparent; color: var(--ink-3); cursor: pointer;
    }

    .ci__recap {
      display: flex; align-items: center; gap: var(--sp-2); width: 100%;
      min-height: 44px; padding: 0; text-align: left;
      border: none; background: transparent; color: var(--success); cursor: pointer;
      font-weight: 700; font-size: var(--text-sm);
    }
    .ci__recap-txt { flex: 1; min-width: 0; }
    .ci__recap-vals { color: var(--ink-3); font-weight: 600; white-space: nowrap; }

    .ci__sliders { display: flex; flex-direction: column; gap: var(--sp-4); }
    .ci__slider { display: flex; flex-direction: column; gap: var(--sp-1); }
    .ci__slider-hd { display: flex; align-items: baseline; justify-content: space-between; }
    .ci__slider-hd label { font-weight: 700; color: var(--ink); font-size: var(--text-sm); }
    .ci__val { font-weight: 800; color: var(--primary); font-variant-numeric: tabular-nums; }

    /* Pouce large : le curseur doit s'attraper à moitié réveillé, sans viser.
       appearance:none sur l'input lui-même est OBLIGATOIRE : sans lui, WebKit/Blink ignorent
       ::-webkit-slider-runnable-track et ::-webkit-slider-thumb et rendent le curseur natif
       (pouce d'environ 16 px, bleu système). C'est ce qui se passait sous Chrome, Edge et tout
       iOS ; Firefox, lui, applique ::-moz-range-* sans cette condition — d'où deux rendus
       différents pour le même composant. */
    .ci__range {
      appearance: none; -webkit-appearance: none;
      width: 100%; height: 44px; background: transparent; cursor: pointer;
    }
    .ci__range::-webkit-slider-runnable-track {
      height: 6px; border-radius: var(--radius-full); background: var(--paper-sunk);
    }
    .ci__range::-webkit-slider-thumb {
      -webkit-appearance: none; appearance: none;
      width: 28px; height: 28px; margin-top: -11px;
      border-radius: var(--radius-full); border: 2px solid var(--paper);
      background: var(--primary); box-shadow: var(--shadow-sm);
    }
    .ci__range::-moz-range-track {
      height: 6px; border-radius: var(--radius-full); background: var(--paper-sunk);
    }
    .ci__range::-moz-range-thumb {
      width: 28px; height: 28px;
      border-radius: var(--radius-full); border: 2px solid var(--paper);
      background: var(--primary); box-shadow: var(--shadow-sm);
    }

    /* Le sommeil se lit à l'envers des deux autres (10 = au mieux) : la couleur le dit,
       les libellés d'extrémités le confirment — jamais la couleur seule. */
    .ci__range--inverted::-webkit-slider-thumb { background: var(--accent); }
    .ci__range--inverted::-moz-range-thumb { background: var(--accent); }

    .ci__ends {
      display: flex; justify-content: space-between;
      font-size: var(--text-xs); color: var(--ink-3);
    }
    .ci__submit { width: 100%; }
  `],
})
export class MorningCheckInComponent implements OnInit {
  private readonly portal = inject(AthletePortalService);
  private readonly toast = inject(ToastService);
  private readonly celebration = inject(CelebrationService);

  protected readonly sliders = SLIDERS;

  protected readonly sleep = signal<number | null>(null);
  protected readonly fatigue = signal<number | null>(null);
  protected readonly pain = signal<number | null>(null);

  protected readonly saved = signal(false);
  protected readonly saving = signal(false);
  protected readonly expanded = signal(false);
  /** Masqué pour la journée (croix) : facultatif veut dire refusable. */
  protected readonly dismissed = signal(false);

  /** Rien à envoyer tant qu'aucun curseur n'a bougé : pas de 5/5/5 fantôme. */
  protected readonly touched = computed(
    () => this.sleep() !== null || this.fatigue() !== null || this.pain() !== null,
  );

  ngOnInit(): void {
    this.portal.checkIn().subscribe({
      next: (c) => this.apply(c),
      // Pas de check-in aujourd'hui (204) ou erreur réseau : on propose le formulaire vierge.
      error: () => undefined,
    });
  }

  private apply(c: DailyCheckIn | null): void {
    if (!c) return;
    this.sleep.set(c.sleep);
    this.fatigue.set(c.fatigue);
    this.pain.set(c.pain);
    this.saved.set(true);
    this.expanded.set(false);
  }

  protected valueOf(key: Slider['key']): number | null {
    return { sleep: this.sleep(), fatigue: this.fatigue(), pain: this.pain() }[key];
  }

  protected onSlide(key: Slider['key'], event: Event): void {
    const value = Number((event.target as HTMLInputElement).value);
    ({ sleep: this.sleep, fatigue: this.fatigue, pain: this.pain })[key].set(value);
  }

  protected save(): void {
    if (this.saving()) return;
    this.saving.set(true);
    this.portal.saveCheckIn({ sleep: this.sleep(), fatigue: this.fatigue(), pain: this.pain() })
      .subscribe({
        next: (c) => {
          this.saving.set(false);
          this.apply(c);
          this.celebration.celebrate({ title: 'Check-in enregistré', emoji: '☀️' });
        },
        error: () => {
          this.saving.set(false);
          this.toast.error('Check-in non enregistré. Réessaie dans un instant.');
        },
      });
  }
}
