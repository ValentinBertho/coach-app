import {
  ChangeDetectionStrategy, Component, ElementRef, HostListener, computed, effect, inject, signal, viewChild,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { HelpService } from './help.service';
import { HELP_GUIDES, HelpSection } from './help-content';

/**
 * Recherche globale du centre d'aide — overlay app-level monté une fois dans
 * AppComponent et déclenché depuis n'importe quel en-tête (`HelpService.openSearch`).
 * Cherche dans le guide du profil connecté et saute directement à la section choisie.
 */
@Component({
  selector: 'app-help-search-overlay',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, IconComponent],
  template: `
    @if (help.searchOpen()) {
      <div class="ovl" (click)="onBackdrop($event)">
        <div class="ovl-panel" role="dialog" aria-modal="true" aria-label="Recherche dans l'aide" (click)="$event.stopPropagation()">
          <div class="ovl-search">
            <app-icon name="search" [size]="18" />
            <input #box type="search" class="ovl-input" placeholder="Rechercher dans l'aide…"
                   [ngModel]="query()" (ngModelChange)="query.set($event)"
                   (keydown.enter)="openFirst()" aria-label="Rechercher dans l'aide" />
            <button type="button" class="ovl-close" (click)="help.closeSearch()" aria-label="Fermer">
              <app-icon name="x" [size]="18" />
            </button>
          </div>

          <div class="ovl-results">
            @for (s of results(); track s.id) {
              <button type="button" class="ovl-row" (click)="go(s.id)">
                <span class="ovl-ic"><app-icon [name]="s.icon" [size]="18" /></span>
                <span class="ovl-txt">
                  <strong>{{ s.title }}</strong>
                  <span class="ovl-sum">{{ s.summary }}</span>
                </span>
                <app-icon name="chevron-right" [size]="16" />
              </button>
            } @empty {
              <p class="ovl-empty">Aucune rubrique pour « {{ query() }} ».</p>
            }
          </div>

          <p class="ovl-foot">Entrée pour ouvrir le premier résultat · Échap pour fermer</p>
        </div>
      </div>
    }
  `,
  styles: [`
    .ovl { position: fixed; inset: 0; z-index: 1000; background: rgba(10, 12, 24, 0.55); backdrop-filter: blur(3px); display: flex; align-items: flex-start; justify-content: center; padding: clamp(var(--sp-4), 10vh, 120px) var(--sp-4) var(--sp-4); }
    .ovl-panel { width: 100%; max-width: 560px; background: var(--paper); border: 1px solid var(--hairline); border-radius: var(--radius-xl, 18px); box-shadow: var(--shadow-lg); overflow: hidden; display: flex; flex-direction: column; max-height: 80vh; }

    .ovl-search { display: flex; align-items: center; gap: var(--sp-2); padding: var(--sp-3) var(--sp-4); border-bottom: 1px solid var(--hairline); color: var(--ink-3); }
    .ovl-input { flex: 1; border: none; background: transparent; min-height: 40px; font-size: var(--text-md); color: var(--ink); }
    .ovl-input:focus { outline: none; box-shadow: none; }
    .ovl-close { display: inline-flex; align-items: center; justify-content: center; width: 32px; height: 32px; border: none; background: transparent; color: var(--ink-3); cursor: pointer; border-radius: var(--radius-md); }
    .ovl-close:hover { color: var(--ink); background: var(--canvas); }

    .ovl-results { overflow-y: auto; padding: var(--sp-2); display: flex; flex-direction: column; gap: 2px; }
    .ovl-row { display: flex; align-items: center; gap: var(--sp-3); width: 100%; text-align: left; background: transparent; border: none; cursor: pointer; padding: var(--sp-2) var(--sp-3); border-radius: var(--radius-md); color: var(--ink); }
    .ovl-row:hover { background: var(--canvas); }
    .ovl-ic { display: inline-flex; align-items: center; justify-content: center; width: 34px; height: 34px; flex-shrink: 0; border-radius: var(--radius-md); background: var(--canvas); color: var(--primary); }
    .ovl-txt { display: flex; flex-direction: column; gap: 1px; flex: 1; min-width: 0; }
    .ovl-txt strong { color: var(--ink); font-size: var(--text-md); }
    .ovl-sum { color: var(--ink-3); font-size: var(--text-sm); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .ovl-row app-icon:last-child { color: var(--ink-4); }

    .ovl-empty { padding: var(--sp-5); text-align: center; color: var(--ink-3); margin: 0; }
    .ovl-foot { padding: var(--sp-2) var(--sp-4); margin: 0; border-top: 1px solid var(--hairline); color: var(--ink-4); font-size: var(--text-xs); text-align: center; }
  `],
})
export class HelpSearchOverlayComponent {
  readonly help = inject(HelpService);
  private readonly box = viewChild<ElementRef<HTMLInputElement>>('box');

  readonly query = signal('');

  /** Sections du guide du profil connecté, filtrées par la recherche. */
  readonly results = computed<HelpSection[]>(() => {
    const all = HELP_GUIDES[this.help.audience()].sections;
    const q = this.query().trim().toLowerCase();
    if (!q) return all;
    return all.filter((s) => this.haystack(s).includes(q));
  });

  constructor() {
    // Réinitialise + met le focus à chaque ouverture.
    effect(() => {
      if (this.help.searchOpen()) {
        this.query.set('');
        setTimeout(() => this.box()?.nativeElement.focus(), 30);
      }
    }, { allowSignalWrites: true });
  }

  private haystack(s: HelpSection): string {
    const blocks = s.blocks.map((b) => `${b.text ?? ''} ${(b.items ?? []).join(' ')}`).join(' ');
    return `${s.title} ${s.summary} ${blocks}`.toLowerCase();
  }

  go(sectionId: string): void { this.help.goToSection(sectionId); }

  openFirst(): void {
    const first = this.results()[0];
    if (first) this.go(first.id);
  }

  onBackdrop(_: MouseEvent): void { this.help.closeSearch(); }

  @HostListener('document:keydown.escape')
  onEscape(): void { if (this.help.searchOpen()) this.help.closeSearch(); }
}
