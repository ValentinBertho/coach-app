import { ChangeDetectionStrategy, Component, model, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  INJURY_AREAS, INJURY_AREA_LABELS, INJURY_KINDS, INJURY_KIND_LABELS, INJURY_SIDE_LABELS,
  Injury, InjuryArea, InjuryKind, InjurySide, hasSide, injuryLabel,
} from '../../../core/models/injury.model';
import { IconComponent } from '../icon/icon.component';

/**
 * Déclaration de blessure au débrief : nature en une pastille, puis zone et côté.
 *
 * <p><b>Pourquoi.</b> Le retour d'après séance ne portait qu'un curseur de douleur 0–10. Il dit
 * combien, jamais quoi ni où — et un « 7/10 » sans localisation n'est pas actionnable : le coach
 * ne peut ni adapter la séance du lendemain, ni décider d'un renvoi. Nolio pose la question à
 * l'endroit exact où elle se pose, dans le débrief, avec des pastilles pré-remplies ; c'est ce
 * geste-là qui est repris ici.</p>
 *
 * <p>Rien n'est ouvert par défaut : la très grande majorité des séances se débriefent sans
 * blessure, et une déclaration ne doit pas être un champ de plus à franchir. Le premier tap sur
 * une nature ouvre le reste — la zone est obligatoire, le côté ne se propose que là où il a un
 * sens (pas de « gauche / droite » sur un abdomen).</p>
 *
 * @example <app-injury-picker [(injuries)]="injuries" />
 */
@Component({
  selector: 'app-injury-picker',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, IconComponent],
  template: `
    <div class="inj">
      <div class="inj__head">
        <span class="inj__label">Blessure</span>
        @if (injuries().length) {
          <span class="inj__count">{{ injuries().length }} déclarée{{ injuries().length > 1 ? 's' : '' }}</span>
        }
      </div>

      <!-- Déjà déclarées : lisibles en toutes lettres, retirables d'un tap. -->
      @if (injuries().length) {
        <ul class="inj__list">
          @for (i of injuries(); track $index) {
            <li class="inj__item">
              <span class="inj__item-txt">
                {{ label(i) }}
                @if (i.note) { <em class="inj__note">« {{ i.note }} »</em> }
              </span>
              <button type="button" class="inj__rm" (click)="remove($index)"
                      [attr.aria-label]="'Retirer ' + label(i)">
                <app-icon name="x" [size]="15" />
              </button>
            </li>
          }
        </ul>
      }

      @if (draft(); as d) {
        <div class="inj__form">
          <p class="inj__q">{{ kindLabels[d.kind] }} — où ?</p>
          <div class="inj__chips">
            @for (a of areas; track a) {
              <button type="button" class="chip" [class.chip--on]="d.area === a"
                      (click)="setArea(a)">{{ areaLabels[a] }}</button>
            }
          </div>

          @if (d.area && sided(d.area)) {
            <div class="inj__chips inj__chips--sides">
              @for (s of sides; track s) {
                <button type="button" class="chip" [class.chip--on]="d.side === s"
                        (click)="setSide(s)">{{ sideLabels[s] }}</button>
              }
            </div>
          }

          <input class="form-control" maxlength="200" placeholder="Une précision ? (optionnel)"
                 [ngModel]="d.note" (ngModelChange)="setNote($event)" />

          <div class="inj__actions">
            <button type="button" class="btn btn-primary btn-sm" [disabled]="!d.area" (click)="confirm()">
              <app-icon name="check" [size]="15" /> Ajouter
            </button>
            <button type="button" class="btn btn-ghost btn-sm" (click)="draft.set(null)">Annuler</button>
          </div>
        </div>
      } @else if (canAdd()) {
        <!-- Les natures les plus courantes en accès direct : « Ajouter » puis un menu aurait
             coûté deux taps pour l'entorse, qui est le cas nº 1. -->
        <div class="inj__chips">
          @for (k of kinds; track k) {
            <button type="button" class="chip chip--add" (click)="start(k)">
              <app-icon name="plus" [size]="14" /> {{ kindLabels[k] }}
            </button>
          }
        </div>
      } @else {
        <p class="field-hint">Cinq blessures déclarées : c'est le maximum pour une séance.</p>
      }
    </div>
  `,
  styles: [`
    .inj { display: flex; flex-direction: column; gap: var(--sp-2); }
    .inj__head { display: flex; align-items: baseline; justify-content: space-between; gap: var(--sp-2); }
    .inj__label { font-size: var(--text-sm); color: var(--ink-2); font-weight: 700; }
    .inj__count { font-size: var(--text-xs); font-weight: 700; color: var(--danger, var(--form-red)); }

    .inj__list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: var(--sp-1); }
    .inj__item {
      display: flex; align-items: center; gap: var(--sp-2);
      padding: var(--sp-2) var(--sp-3); border-radius: var(--radius);
      background: color-mix(in srgb, var(--form-red) 10%, var(--paper));
      border: 1px solid color-mix(in srgb, var(--form-red) 35%, transparent);
    }
    .inj__item-txt { flex: 1; min-width: 0; font-size: var(--text-sm); font-weight: 700; color: var(--ink); }
    .inj__note { display: block; font-weight: 500; color: var(--ink-2); font-style: italic; }
    .inj__rm {
      flex-shrink: 0; min-width: 32px; min-height: 32px;
      display: flex; align-items: center; justify-content: center;
      border: none; background: transparent; color: var(--ink-3); cursor: pointer;
    }

    .inj__form { display: flex; flex-direction: column; gap: var(--sp-2); }
    .inj__q { margin: 0; font-weight: 700; color: var(--ink); font-size: var(--text-sm); }
    .inj__chips { display: flex; flex-wrap: wrap; gap: var(--sp-1); }
    .inj__chips--sides { padding-top: 2px; }
    .chip {
      min-height: 40px; padding: var(--sp-1) var(--sp-3);
      display: inline-flex; align-items: center; gap: 4px;
      border-radius: var(--radius-full); border: 1px solid var(--hairline);
      background: var(--paper); color: var(--ink-2);
      font-size: var(--text-sm); font-weight: 600; cursor: pointer;
      white-space: nowrap;
    }
    .chip:active { transform: scale(0.96); }
    .chip--on { background: var(--primary-wash); border-color: var(--primary); color: var(--primary); font-weight: 700; }
    .chip--add { color: var(--ink-2); }
    .inj__actions { display: flex; gap: var(--sp-2); }
  `],
})
export class InjuryPickerComponent {
  /** Blessures déclarées (two-way) — liste vide = aucune. */
  readonly injuries = model<Injury[]>([]);

  /** Aligné sur la borne serveur : au-delà, ce n'est plus un débrief de séance. */
  private static readonly MAX = 5;

  protected readonly kinds = INJURY_KINDS;
  protected readonly areas = INJURY_AREAS;
  protected readonly sides: InjurySide[] = ['LEFT', 'RIGHT', 'BOTH'];
  protected readonly kindLabels = INJURY_KIND_LABELS;
  protected readonly areaLabels = INJURY_AREA_LABELS;
  protected readonly sideLabels = INJURY_SIDE_LABELS;

  /** Déclaration en cours de saisie ; `null` = rien d'ouvert (l'état par défaut). */
  protected readonly draft = signal<Injury | null>(null);

  protected canAdd(): boolean { return this.injuries().length < InjuryPickerComponent.MAX; }
  protected label(i: Injury): string { return injuryLabel(i); }
  protected sided(area: InjuryArea): boolean { return hasSide(area); }

  protected start(kind: InjuryKind): void {
    this.draft.set({ kind, area: null as unknown as InjuryArea, side: null, note: null });
  }

  protected setArea(area: InjuryArea): void {
    const d = this.draft();
    if (!d) return;
    // Changer de zone remet le côté à zéro : « cheville droite » ne doit pas devenir
    // silencieusement « bas du dos droit » quand l'athlète corrige son choix.
    this.draft.set({ ...d, area, side: hasSide(area) ? d.side : null });
  }

  protected setSide(side: InjurySide): void {
    const d = this.draft();
    if (!d) return;
    this.draft.set({ ...d, side: d.side === side ? null : side });
  }

  protected setNote(note: string): void {
    const d = this.draft();
    if (!d) return;
    this.draft.set({ ...d, note: note?.trim() ? note : null });
  }

  protected confirm(): void {
    const d = this.draft();
    if (!d?.area) return;
    this.injuries.set([...this.injuries(), d]);
    this.draft.set(null);
  }

  protected remove(index: number): void {
    this.injuries.set(this.injuries().filter((_, i) => i !== index));
  }
}
