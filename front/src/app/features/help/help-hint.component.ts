import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { HelpService } from './help.service';

/**
 * Petit bouton « ? » contextuel : ouvre le centre d'aide directement sur la
 * section pertinente pour l'écran courant. À poser à côté d'un titre.
 *
 * @example <app-help-hint section="seance-jour" />
 */
@Component({
  selector: 'app-help-hint',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    <button type="button" class="help-hint" (click)="open()" [attr.aria-label]="label()" [title]="label()">
      <app-icon name="circle-help" [size]="size()" />
    </button>
  `,
  styles: [`
    .help-hint {
      display: inline-flex; align-items: center; justify-content: center;
      width: 30px; height: 30px; border-radius: 50%;
      border: 1px solid var(--hairline); background: var(--paper); color: var(--ink-3);
      cursor: pointer; flex-shrink: 0; padding: 0;
      transition: color var(--duration-fast, 160ms) var(--ease, ease), border-color var(--duration-fast, 160ms) var(--ease, ease);
    }
    .help-hint:hover { color: var(--primary); border-color: var(--primary); }
    .help-hint:active { transform: scale(0.94); }
  `],
})
export class HelpHintComponent {
  private readonly help = inject(HelpService);
  readonly section = input.required<string>();
  readonly size = input(15);
  readonly label = input('Aide sur cette section');

  open(): void { this.help.goToSection(this.section()); }
}
