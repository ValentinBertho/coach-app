import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { Injury, injuryLabel } from '../../../core/models/injury.model';
import { FEEL_COLORS, feelLabel } from '../feel-scale';
import { rpeLabel } from '../rpe-scale';
import { IconComponent } from '../icon/icon.component';

/**
 * Récapitulatif du débrief d'une séance, en lecture : sensation, effort perçu, fatigue, douleur,
 * blessures, commentaire.
 *
 * <p><b>Pourquoi un composant partagé.</b> Le retour de l'athlète se relit à trois endroits — sa
 * propre fiche de séance, la fiche du coach, le fil des retours à traiter — et il y était rendu
 * différemment à chaque fois : le coach voyait le RPE, l'athlète ne revoyait rien du tout. Une
 * donnée déclarée doit se relire à l'identique de part et d'autre, sans quoi coach et athlète ne
 * parlent pas du même débrief.</p>
 *
 * <p>Les cases sans réponse restent affichées, marquées « Pas de réponse » comme chez Nolio :
 * l'absence de réponse est elle-même une information pour le coach, et la masquer ferait croire
 * à une question jamais posée.</p>
 */
@Component({
  selector: 'app-feedback-recap',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    <div class="fr">
      <div class="fr-grid">
        <div class="fr-cell">
          <span class="fr-lb">Sensation</span>
          @if (feel() != null) {
            <span class="fr-v" [style.color]="feelColor()">{{ feelWord() }}</span>
          } @else {
            <span class="fr-none">Pas de réponse</span>
          }
        </div>

        <div class="fr-cell">
          <span class="fr-lb">Perception de l'effort</span>
          @if (rpe() != null) {
            <span class="fr-v metric">{{ rpe() }}<small>/10</small></span>
            <span class="fr-sub">{{ rpeWord() }}</span>
          } @else {
            <span class="fr-none">Pas de réponse</span>
          }
        </div>

        <!-- Fatigue et douleur ne s'affichent que si elles ont été enregistrées : sans
             consentement actif elles ne le sont pas, et une case vide laisserait croire à un
             athlète silencieux plutôt qu'à une donnée non collectée. -->
        @if (fatigue() != null) {
          <div class="fr-cell">
            <span class="fr-lb">Fatigue</span>
            <span class="fr-v metric">{{ fatigue() }}<small>/10</small></span>
          </div>
        }
        @if (pain() != null) {
          <div class="fr-cell">
            <span class="fr-lb">Douleur</span>
            <span class="fr-v metric">{{ pain() }}<small>/10</small></span>
          </div>
        }
      </div>

      @if (injuries().length) {
        <div class="fr-inj">
          <span class="fr-inj-hd"><app-icon name="alert-triangle" [size]="14" /> Blessure déclarée</span>
          <ul class="fr-inj-list">
            @for (i of injuries(); track $index) {
              <li>
                {{ label(i) }}
                @if (i.note) { <em>« {{ i.note }} »</em> }
              </li>
            }
          </ul>
        </div>
      }

      @if (comment()) {
        <blockquote class="fr-comment">{{ comment() }}</blockquote>
      }
    </div>
  `,
  styles: [`
    .fr { display: flex; flex-direction: column; gap: var(--sp-3); }
    .fr-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: var(--sp-2); }
    .fr-cell {
      display: flex; flex-direction: column; gap: 2px;
      padding: var(--sp-3); border: 1px solid var(--hairline); border-radius: var(--radius);
    }
    .fr-lb { font-size: var(--text-sm); font-weight: 700; color: var(--ink); }
    .fr-v { font-size: var(--text-lg); font-weight: 800; color: var(--ink); }
    .fr-v small { font-size: var(--text-sm); color: var(--ink-4); font-weight: 600; }
    .fr-sub { font-size: var(--text-xs); color: var(--ink-3); }
    .fr-none { font-size: var(--text-sm); color: var(--ink-3); }

    .fr-inj {
      display: flex; flex-direction: column; gap: var(--sp-1);
      padding: var(--sp-3); border-radius: var(--radius);
      background: color-mix(in srgb, var(--form-red) 10%, var(--paper));
      border: 1px solid color-mix(in srgb, var(--form-red) 35%, transparent);
    }
    .fr-inj-hd {
      display: inline-flex; align-items: center; gap: var(--sp-1);
      font-size: var(--text-xs); font-weight: 700; text-transform: uppercase;
      letter-spacing: 0.04em; color: var(--form-red);
    }
    .fr-inj-list { margin: 0; padding-left: var(--sp-4); color: var(--ink); font-size: var(--text-sm); font-weight: 700; }
    .fr-inj-list em { display: block; font-weight: 500; color: var(--ink-2); }

    .fr-comment {
      margin: 0; padding: var(--sp-3);
      border-left: 3px solid var(--primary); background: var(--paper-sunk);
      border-radius: 0 var(--radius) var(--radius) 0;
      color: var(--ink); font-style: italic;
    }
  `],
})
export class FeedbackRecapComponent {
  readonly feel = input<number | null>(null);
  readonly rpe = input<number | null>(null);
  readonly fatigue = input<number | null>(null);
  readonly pain = input<number | null>(null);
  readonly injuries = input<Injury[]>([]);
  readonly comment = input<string | null>(null);

  /** Rien de déclaré du tout : l'appelant s'en sert pour afficher son propre message. */
  readonly empty = computed(() =>
    this.feel() == null && this.rpe() == null && this.fatigue() == null
    && this.pain() == null && !this.injuries().length && !this.comment());

  protected feelWord(): string { return capitalize(feelLabel(this.feel())); }
  protected rpeWord(): string { return rpeLabel(this.rpe()); }
  protected feelColor(): string { return FEEL_COLORS[this.feel() ?? 0] ?? 'var(--ink)'; }
  protected label(i: Injury): string { return injuryLabel(i); }
}

function capitalize(text: string): string {
  return text ? text.charAt(0).toUpperCase() + text.slice(1) : text;
}
