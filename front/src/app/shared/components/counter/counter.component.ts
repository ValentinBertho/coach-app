import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnDestroy,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';

/**
 * Compteur animé « mise sous tension d'un instrument » : la valeur s'incrémente
 * depuis l'affichage précédent jusqu'à la cible (rAF + easing), en mono tabulaire
 * pour éviter tout saut de largeur. Respecte `prefers-reduced-motion` (saut direct)
 * et n'anime qu'au premier affichage / quand la valeur change vraiment.
 *
 * Une valeur non numérique (null, tiret, texte) est rendue telle quelle.
 *
 * @example
 * <app-counter [value]="412" />
 * <app-counter [value]="1.35" [decimals]="2" />
 * <app-counter [value]="form.score" [decimals]="0" [duration]="500" />
 */
@Component({
  selector: 'app-counter',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span class="metric">{{ display() }}</span>`,
  styles: [`
    :host { display: inline; }
    .metric { font-variant-numeric: tabular-nums; font-feature-settings: 'tnum'; }
  `],
})
export class CounterComponent implements OnDestroy {
  readonly value = input<string | number | null>(null);
  readonly decimals = input(0);
  /** Durée d'animation (ms). */
  readonly duration = input(650);
  /** Texte affiché quand la valeur est nulle. */
  readonly placeholder = input('—');

  private readonly host = inject(ElementRef<HTMLElement>);
  private readonly current = signal<string>('');
  protected readonly display = this.current;

  private frame = 0;
  private from = 0;

  constructor() {
    effect(() => {
      const v = this.value();
      if (v === null || v === undefined || v === '' || typeof v === 'string') {
        this.cancel();
        this.current.set(v == null || v === '' ? this.placeholder() : String(v));
        this.from = typeof v === 'number' ? v : this.from;
        return;
      }
      this.animateTo(v);
    });
  }

  private animateTo(target: number): void {
    this.cancel();
    const reduce = typeof matchMedia !== 'undefined'
      && matchMedia('(prefers-reduced-motion: reduce)').matches;
    const start = this.from;
    if (reduce || start === target || this.duration() <= 0) {
      this.set(target);
      return;
    }
    const t0 = performance.now();
    const span = target - start;
    const dur = this.duration();
    const tick = (now: number) => {
      const p = Math.min(1, (now - t0) / dur);
      // easeOutCubic : démarre vif, ralentit en fin de course (sensation d'instrument).
      const eased = 1 - Math.pow(1 - p, 3);
      this.current.set(this.format(start + span * eased));
      if (p < 1) {
        this.frame = requestAnimationFrame(tick);
      } else {
        this.set(target);
      }
    };
    this.frame = requestAnimationFrame(tick);
  }

  private set(v: number): void {
    this.from = v;
    this.current.set(this.format(v));
  }

  private format(v: number): string {
    return v.toFixed(this.decimals());
  }

  private cancel(): void {
    if (this.frame) {
      cancelAnimationFrame(this.frame);
      this.frame = 0;
    }
  }

  ngOnDestroy(): void {
    this.cancel();
  }
}
