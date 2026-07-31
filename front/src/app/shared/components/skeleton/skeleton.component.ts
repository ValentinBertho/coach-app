import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/** Forme du contenu attendu : le squelette doit ressembler à ce qui va s'afficher. */
export type SkeletonShape = 'list' | 'card' | 'table' | 'text';

/**
 * Squelette de chargement, à la forme du contenu attendu.
 *
 * Remplace les « Chargement… » textuels : un mot qui apparaît puis disparaît fait sauter la
 * page et ne dit rien de ce qui arrive, là où un squelette de la bonne forme réserve la place
 * et rend l'attente lisible. Le libellé reste annoncé aux lecteurs d'écran, qui eux ne voient
 * pas la forme.
 *
 * @example
 * <app-skeleton shape="list" [rows]="5" />
 * <app-skeleton shape="table" [rows]="6" [columns]="4" />
 */
@Component({
  selector: 'app-skeleton',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="sk" role="status" aria-live="polite">
      @switch (shape()) {
        @case ('list') {
          @for (r of items(); track r) {
            <div class="sk-row">
              <span class="skeleton sk-thumb"></span>
              <span class="sk-lines">
                <span class="skeleton sk-line sk-line--title"></span>
                <span class="skeleton sk-line sk-line--meta"></span>
              </span>
            </div>
          }
        }
        @case ('table') {
          @for (r of items(); track r) {
            <div class="sk-tr" [style.--cols]="columns()">
              @for (c of cells(); track c) { <span class="skeleton sk-cell"></span> }
            </div>
          }
        }
        @case ('card') {
          @for (r of items(); track r) {
            <div class="sk-card">
              <span class="skeleton sk-line sk-line--title"></span>
              <span class="skeleton sk-block"></span>
            </div>
          }
        }
        @case ('text') {
          @for (r of items(); track r) { <span class="skeleton sk-line"></span> }
        }
      }
      <span class="sk-sr">Chargement…</span>
    </div>
  `,
  styles: [`
    .sk { display: flex; flex-direction: column; gap: var(--sp-3); width: 100%; }

    /* Annoncé aux lecteurs d'écran uniquement : à l'œil, c'est la forme qui informe. */
    .sk-sr {
      position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px;
      overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; border: 0;
    }

    .sk-row { display: flex; align-items: center; gap: var(--sp-3); }
    .sk-thumb { width: 38px; height: 38px; border-radius: var(--radius-full); flex: none; }
    .sk-lines { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: var(--sp-2); }

    .sk-line { display: block; height: 12px; border-radius: var(--radius-sm); }
    .sk-line--title { width: 45%; height: 14px; }
    .sk-line--meta { width: 72%; height: 10px; }

    .sk-tr { display: grid; grid-template-columns: repeat(var(--cols, 4), 1fr); gap: var(--sp-3); }
    .sk-cell { height: 14px; border-radius: var(--radius-sm); }

    .sk-card { display: flex; flex-direction: column; gap: var(--sp-3); }
    .sk-block { display: block; height: 72px; border-radius: var(--radius); }
  `],
})
export class SkeletonComponent {
  readonly shape = input<SkeletonShape>('list');
  /** Nombre de lignes / cartes esquissées. */
  readonly rows = input(3);
  /** Colonnes de la forme « table ». */
  readonly columns = input(4);

  protected readonly items = computed(() => Array.from({ length: this.rows() }, (_, i) => i));
  protected readonly cells = computed(() => Array.from({ length: this.columns() }, (_, i) => i));
}
