import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { ThemeService, type ThemePref } from '../../../core/services/theme.service';
import { SegmentedControlComponent, type SegmentOption } from '../segmented-control/segmented-control.component';

/**
 * Choix du thème de l'application — clair, sombre, ou celui de l'appareil.
 *
 * <p><b>Pourquoi un composant partagé.</b> Le réglage n'existait que dans les paramètres du
 * coach, sur un écran de bureau. Or c'est sur un téléphone qu'un thème se choisit vraiment :
 * on lit sa séance dehors en plein soleil, ou dans une salle mal éclairée, ou le soir au lit.
 * L'athlète, lui, n'avait aucun choix du tout — son portail était sombre, point. Le même
 * réglage sert donc les deux, écrit une fois.</p>
 *
 * <p>« Système » suit <code>prefers-color-scheme</code> et reste le comportement par défaut du
 * coach ; le portail athlète, lui, garde sa peau sombre tant que rien n'a été choisi
 * (cf. {@link ThemeService.chosen}).</p>
 */
@Component({
  selector: 'app-theme-picker',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [SegmentedControlComponent],
  template: `
    <div class="tpick">
      @if (label()) { <strong class="tpick__lb">{{ label() }}</strong> }
      @if (hint()) { <span class="field-hint">{{ hint() }}</span> }
      <app-segmented-control
        [options]="options"
        [value]="theme.preference()"
        (valueChange)="set($event)"
        ariaLabel="Thème de l'application" />
    </div>
  `,
  styles: [`
    .tpick { display: flex; flex-direction: column; gap: var(--sp-1); align-items: flex-start; }
    .tpick__lb { font-size: var(--text-sm); }
    /* Cibles tactiles : le contrôle segmenté est calibré pour la souris (36 px), ce qui passe
       sous le plancher du design system dès qu'on le touche du pouce. */
    .tpick ::ng-deep .seg { width: 100%; }
    .tpick ::ng-deep .seg__btn { min-height: 44px; flex: 1; justify-content: center; }
  `],
})
export class ThemePickerComponent {
  readonly label = input<string>('');
  readonly hint = input<string>('« Système » suit les réglages de ton appareil.');

  readonly theme = inject(ThemeService);

  readonly options: SegmentOption[] = [
    { value: 'light', label: 'Clair' },
    { value: 'dark', label: 'Sombre' },
    { value: 'system', label: 'Système' },
  ];

  set(value: string): void {
    this.theme.set(value as ThemePref);
  }
}
