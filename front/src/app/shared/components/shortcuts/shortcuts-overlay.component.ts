import { A11yModule } from '@angular/cdk/a11y';
import { ChangeDetectionStrategy, Component, HostListener, input, model } from '@angular/core';
import { IconComponent } from '../icon/icon.component';
import { ShortcutGroup, displayKey } from './shortcuts';

/**
 * Aide-mémoire des raccourcis, ouvert par `?` (convention Linear / GitHub / Superhuman).
 *
 * Un raccourci que personne ne découvre n'existe pas : c'est la contrepartie obligatoire de
 * tout accélérateur clavier. L'écran hôte déclare ses groupes, cette feuille se charge de les
 * présenter et de traduire `mod` en ⌘ ou Ctrl selon la plateforme.
 *
 * @example <app-shortcuts-overlay [(open)]="shortcutsOpen" [groups]="shortcutGroups" />
 */
@Component({
  selector: 'app-shortcuts-overlay',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [A11yModule, IconComponent],
  template: `
    @if (open()) {
      <div class="sc-backdrop" (click)="open.set(false)" aria-hidden="true"></div>
      <div class="sc-panel" cdkTrapFocus [cdkTrapFocusAutoCapture]="true"
           role="dialog" aria-modal="true" aria-label="Raccourcis clavier">
        <header class="sc-head">
          <h3>Raccourcis</h3>
          <button type="button" class="sc-close" aria-label="Fermer" (click)="open.set(false)">
            <app-icon name="x" [size]="16" />
          </button>
        </header>

        <div class="sc-body">
          @for (g of groups(); track g.title) {
            <section class="sc-group">
              <h4 class="sc-group__t">{{ g.title }}</h4>
              @for (s of g.items; track s.label) {
                <div class="sc-row">
                  <span class="sc-row__label">{{ s.label }}</span>
                  <span class="sc-keys">
                    @for (k of s.keys; track $index) { <kbd>{{ key(k) }}</kbd> }
                  </span>
                </div>
              }
            </section>
          }
        </div>

        <footer class="sc-foot field-hint">Rouvre cette liste avec <kbd>?</kbd>.</footer>
      </div>
    }
  `,
  styles: [`
    .sc-backdrop { position: fixed; inset: 0; z-index: 600; background: var(--bg-overlay); }
    .sc-panel {
      position: fixed; z-index: 601; top: 50%; left: 50%; transform: translate(-50%, -50%);
      width: min(640px, 92vw); max-height: 82vh; display: flex; flex-direction: column;
      background: var(--paper); border: 1px solid var(--hairline);
      border-radius: var(--radius-lg); box-shadow: var(--shadow-lg);
    }
    .sc-head {
      display: flex; align-items: center; justify-content: space-between; gap: var(--sp-3);
      padding: var(--sp-4) var(--sp-5); border-bottom: 1px solid var(--hairline);
    }
    .sc-head h3 { margin: 0; font-size: var(--text-lg); }
    .sc-close {
      width: 32px; height: 32px; border-radius: var(--radius-full); border: none;
      background: var(--paper-sunk); color: var(--ink-2); cursor: pointer;
      display: inline-flex; align-items: center; justify-content: center;
    }
    .sc-body {
      overflow-y: auto; padding: var(--sp-4) var(--sp-5);
      display: grid; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); gap: var(--sp-5);
      align-content: start;
    }
    .sc-group__t {
      margin: 0 0 var(--sp-2); font-size: var(--text-2xs); font-weight: 800;
      text-transform: uppercase; letter-spacing: 0.05em; color: var(--ink-3);
    }
    .sc-row {
      display: flex; align-items: center; justify-content: space-between; gap: var(--sp-3);
      padding: var(--sp-1) 0; font-size: var(--text-sm); color: var(--ink-2);
    }
    .sc-row__label { min-width: 0; }
    .sc-keys { display: inline-flex; gap: 3px; flex: none; }
    .sc-foot { padding: var(--sp-3) var(--sp-5); border-top: 1px solid var(--hairline); margin: 0; }

    kbd {
      display: inline-flex; align-items: center; justify-content: center;
      min-width: 22px; padding: 2px 6px;
      font-family: var(--font-data); font-size: var(--text-2xs); font-weight: 700;
      color: var(--ink); background: var(--paper-sunk);
      border: 1px solid var(--hairline); border-bottom-width: 2px;
      border-radius: var(--radius-sm);
    }
  `],
})
export class ShortcutsOverlayComponent {
  readonly open = model(false);
  readonly groups = input<readonly ShortcutGroup[]>([]);

  protected key(k: string): string { return displayKey(k); }

  @HostListener('document:keydown.escape')
  protected onEscape(): void { if (this.open()) this.open.set(false); }
}
