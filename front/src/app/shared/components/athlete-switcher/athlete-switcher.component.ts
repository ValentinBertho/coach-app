import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AthleteSummary } from '../../../core/models/athlete.model';
import { IconComponent } from '../icon/icon.component';

/**
 * Sélecteur d'athlète filtrable — déclencheur + menu avec recherche instantanée.
 *
 * Un `<select>` natif est inutilisable dès quelques dizaines d'athlètes : on ne peut pas
 * chercher, on ne voit ni le groupe ni l'avatar, et sur 100 noms le déroulé couvre l'écran.
 * Ce composant existait déjà, prisonnier de la coquille athlète ; il est extrait ici pour
 * servir partout où l'on choisit un athlète.
 *
 * Deux allures d'affichage : `title` (le nom EST le titre de l'écran, comme dans la coquille)
 * et `control` (un contrôle de barre d'outils, comme au calendrier).
 *
 * @example
 * <app-athlete-switcher [athletes]="athletes()" [selectedId]="id" (selectedIdChange)="load()" />
 */
@Component({
  selector: 'app-athlete-switcher',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, IconComponent],
  template: `
    <div class="sw" [class.sw--open]="open()" [class.sw--title]="variant() === 'title'">
      <button type="button" class="sw__trigger" (click)="toggle()"
              [attr.aria-expanded]="open()" aria-haspopup="listbox"
              [attr.aria-label]="'Athlète : ' + (selectedLabel() || 'aucun')">
        @if (variant() === 'title') {
          <h1 class="display-sm">{{ selectedLabel() }}</h1>
        } @else {
          @if (selected(); as a) {
            <span class="avatar avatar-sm">{{ a.firstName[0] }}{{ a.lastName[0] }}</span>
          }
          <span class="sw__label">{{ selectedLabel() || placeholder() }}</span>
        }
        <app-icon name="chevron-down" [size]="16" />
      </button>

      @if (open()) {
        <div class="sw__backdrop" (click)="close()"></div>
        <div class="sw__menu" role="listbox">
          <input class="form-control sw__search" [ngModel]="query()"
                 (ngModelChange)="query.set($event)" placeholder="Rechercher un athlète…"
                 aria-label="Rechercher un athlète" />
          @for (o of matches(); track o.id) {
            <button type="button" class="sw__opt" role="option"
                    [class.sel]="o.id === selectedId()" (click)="pick(o)">
              <span class="avatar">{{ o.firstName[0] }}{{ o.lastName[0] }}</span>
              <span class="sw__name">{{ o.firstName }} {{ o.lastName }}</span>
              <!-- Lecture seule visible dès le choix : mieux vaut le savoir avant de planifier. -->
              @if (o.canWrite === false) { <app-icon class="sw__lock" name="lock" [size]="13" /> }
              @if (o.groupName) { <span class="sw__grp">{{ o.groupName }}</span> }
            </button>
          } @empty {
            <p class="sw__empty field-hint">Aucun athlète pour cette recherche.</p>
          }
        </div>
      }
    </div>
  `,
  styles: [`
    .sw { position: relative; }
    .sw__trigger {
      display: inline-flex; align-items: center; gap: var(--sp-2);
      background: none; border: none; padding: 2px 4px; margin-left: -4px; cursor: pointer;
      color: var(--ink); border-radius: var(--radius-sm); font: inherit;
    }
    .sw__trigger:hover { background: var(--paper-sunk); }
    .sw__trigger:focus-visible { outline: 2px solid var(--primary); outline-offset: 2px; }
    .sw__trigger app-icon { color: var(--ink-3); }
    .sw--open .sw__trigger app-icon { color: var(--primary); }
    .sw--title .sw__trigger h1 { margin: 0; }

    /* Allure « contrôle » : même hauteur et même cadre que les autres contrôles de barre. */
    .sw:not(.sw--title) .sw__trigger {
      min-height: 40px; padding: 0 var(--sp-3); margin-left: 0;
      border: 1px solid var(--hairline); border-radius: var(--radius);
      background: var(--paper); font-weight: 600; max-width: 220px;
    }
    .sw__label { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

    .sw__backdrop { position: fixed; inset: 0; z-index: 40; }
    .sw__menu {
      position: absolute; z-index: 41; top: calc(100% + 4px); left: 0;
      width: min(320px, 80vw); max-height: 380px; overflow-y: auto;
      background: var(--paper); border: 1px solid var(--hairline);
      border-radius: var(--radius-md); box-shadow: var(--shadow-lg); padding: var(--sp-2);
      display: flex; flex-direction: column; gap: 2px;
    }
    .sw__search { margin-bottom: var(--sp-1); }
    .sw__opt {
      display: flex; align-items: center; gap: var(--sp-2); width: 100%;
      background: none; border: none; cursor: pointer; text-align: left;
      padding: var(--sp-2); border-radius: var(--radius-sm); color: var(--ink); font: inherit;
    }
    .sw__opt:hover { background: var(--paper-sunk); }
    .sw__opt.sel { background: var(--primary-wash); color: var(--primary); }
    .sw__opt .avatar { width: 26px; height: 26px; font-size: var(--text-xs); flex: none; }
    .sw__name { font-weight: 600; font-size: var(--text-sm); }
    .sw__lock { color: var(--ink-3); }
    .sw__grp { margin-left: auto; font-size: var(--text-xs); color: var(--ink-3); }
    .sw__empty { padding: var(--sp-2); margin: 0; }
  `],
})
export class AthleteSwitcherComponent {
  readonly athletes = input<AthleteSummary[]>([]);
  readonly selectedId = input<string>('');
  readonly variant = input<'title' | 'control'>('control');
  readonly placeholder = input('Choisir un athlète…');

  /** Athlète choisi (l'appelant décide s'il navigue ou s'il recharge sa vue). */
  readonly selectedIdChange = output<string>();

  protected readonly open = signal(false);
  protected readonly query = signal('');

  protected readonly selected = computed(() =>
    this.athletes().find((a) => a.id === this.selectedId()) ?? null);

  protected readonly selectedLabel = computed(() => {
    const a = this.selected();
    return a ? `${a.firstName} ${a.lastName}` : '';
  });

  protected readonly matches = computed(() => {
    const q = this.query().trim().toLowerCase();
    const all = this.athletes();
    if (!q) return all;
    return all.filter((a) => `${a.firstName} ${a.lastName} ${a.groupName ?? ''}`.toLowerCase().includes(q));
  });

  protected toggle(): void {
    this.open.update((v) => !v);
    // Rouvrir sur une recherche précédente donnerait une liste tronquée sans raison visible.
    if (this.open()) this.query.set('');
  }

  protected close(): void { this.open.set(false); }

  protected pick(a: AthleteSummary): void {
    this.close();
    if (a.id === this.selectedId()) return;
    this.selectedIdChange.emit(a.id);
  }
}
