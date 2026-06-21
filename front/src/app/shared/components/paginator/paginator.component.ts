import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

/** Pagination serveur réutilisable (page 0-based). */
@Component({
  selector: 'app-paginator',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (totalPages() > 1) {
      <nav class="paginator">
        <button type="button" class="btn btn-ghost btn-sm" [disabled]="page() === 0"
                (click)="go(page() - 1)">← Précédent</button>
        <span class="pg-label metric">{{ page() + 1 }} / {{ totalPages() }}</span>
        <button type="button" class="btn btn-ghost btn-sm" [disabled]="isLast()"
                (click)="go(page() + 1)">Suivant →</button>
      </nav>
    }
  `,
  styles: [`
    .paginator { display: flex; align-items: center; justify-content: center; gap: var(--sp-3); margin-top: var(--sp-5); }
    .pg-label { color: var(--ink-3); font-size: var(--text-sm); }
  `],
})
export class PaginatorComponent {
  readonly page = input(0);
  readonly totalPages = input(1);
  readonly pageChange = output<number>();

  readonly isLast = computed(() => this.page() >= this.totalPages() - 1);

  go(p: number): void {
    if (p >= 0 && p < this.totalPages()) {
      this.pageChange.emit(p);
    }
  }
}
