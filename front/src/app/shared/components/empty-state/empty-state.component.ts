import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { IconComponent } from '../icon/icon.component';

/**
 * État vide réutilisable, ton « instrument au repos » : un cadran discret, un titre,
 * une explication, et un emplacement d'action projeté (<ng-content>). Remplace les
 * blocs vides ad hoc pour une voix cohérente sur tous les écrans.
 *
 * @example
 * <app-empty-state icon="flask-conical" title="Aucun test"
 *   hint="Enregistrez un test de lactate pour calibrer les zones.">
 *   <button class="btn btn-primary">Nouveau test</button>
 * </app-empty-state>
 */
@Component({
  selector: 'app-empty-state',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    <div class="es" role="status">
      <span class="es__dial" aria-hidden="true">
        <app-icon [name]="icon()" [size]="26" />
      </span>
      <h3 class="es__title">{{ title() }}</h3>
      @if (hint()) { <p class="es__hint">{{ hint() }}</p> }
      <div class="es__action"><ng-content /></div>
    </div>
  `,
  styles: [`
    .es {
      display: flex; flex-direction: column; align-items: center; text-align: center;
      gap: var(--sp-3); padding: var(--sp-12) var(--sp-6);
    }
    .es__dial {
      display: inline-flex; align-items: center; justify-content: center;
      width: 64px; height: 64px; border-radius: 50%;
      color: var(--primary);
      background:
        radial-gradient(circle at center, color-mix(in srgb, var(--primary) 12%, transparent) 0%, transparent 70%);
      border: 1px dashed color-mix(in srgb, var(--primary) 40%, var(--hairline));
    }
    .es__title { font-family: var(--font-display); font-size: var(--text-xl); margin: 0; color: var(--ink); }
    .es__hint { color: var(--ink-3); font-size: var(--text-sm); margin: 0; max-width: 42ch; line-height: 1.5; }
    .es__action:empty { display: none; }
    .es__action { margin-top: var(--sp-2); }
  `],
})
export class EmptyStateComponent {
  readonly icon = input('file-text');
  readonly title = input.required<string>();
  readonly hint = input<string>('');
}
