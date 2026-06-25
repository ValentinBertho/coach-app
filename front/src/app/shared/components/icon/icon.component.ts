import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { LucideAngularModule } from 'lucide-angular';

/**
 * Icône applicative (wrapper Lucide). Remplace les emojis par un jeu d'icônes
 * filaires cohérent et professionnel. Hérite de la couleur courante.
 *
 * @example <app-icon name="dumbbell" [size]="16" />
 */
@Component({
  selector: 'app-icon',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [LucideAngularModule],
  template: `<lucide-icon [name]="name()" [size]="size()" [strokeWidth]="strokeWidth()" />`,
  styles: [`
    :host { display: inline-flex; align-items: center; justify-content: center; line-height: 0; vertical-align: -0.125em; }
  `],
})
export class IconComponent {
  /** Nom kebab-case de l'icône Lucide (ex. "trending-up"). */
  readonly name = input.required<string>();
  readonly size = input(18);
  readonly strokeWidth = input(2);
}
