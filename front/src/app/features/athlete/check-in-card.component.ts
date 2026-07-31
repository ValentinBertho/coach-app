import { ChangeDetectionStrategy, Component, OnInit, computed, inject, output, signal } from '@angular/core';
import { AthletePortalService } from '../../core/services/athlete-portal.service';
import { CelebrationService } from '../../core/services/celebration.service';
import { ToastService } from '../../core/services/toast.service';
import { WellnessCheckIn } from '../../core/models/wellness.model';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { PainFatigueSelectorComponent } from '../../shared/components/physiology';

/**
 * Check-in matinal — trois curseurs, moins de dix secondes.
 *
 * L'état de forme du coach ne se nourrissait que du retour post-séance : un athlète en coupure,
 * blessé ou simplement en semaine de repos affichait une pastille périmée, d'où les « aucun
 * retour récent » permanents. Ce check-in donne le même signal AVANT l'effort.
 *
 * Il reste **facultatif** et replié par défaut : un écran d'accueil qui réclame un formulaire
 * chaque matin serait exactement le travers qu'on vient de corriger côté force. Une fois
 * rempli, la carte se réduit à un récapitulatif d'une ligne.
 *
 * Invariant : fatigue + douleur font la forme ; le sommeil est du contexte, pas un facteur.
 */
@Component({
  selector: 'app-check-in-card',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, PainFatigueSelectorComponent],
  template: `
    <section class="ci card" [class.ci--done]="saved() && !open()">
      @if (saved() && !open()) {
        <!-- Déjà déclaré : un récapitulatif, pas un formulaire à re-remplir. -->
        <button type="button" class="ci__recap" (click)="open.set(true)">
          <app-icon name="check" [size]="16" />
          <span class="ci__recap-t">Check-in du matin</span>
          <span class="ci__recap-v metric">{{ recap() }}</span>
          <app-icon name="pencil" [size]="15" />
        </button>
      } @else if (!open()) {
        <button type="button" class="ci__prompt" (click)="open.set(true)">
          <app-icon name="heart-pulse" [size]="18" />
          <span class="ci__prompt-t">
            <strong>Comment tu te sens ce matin ?</strong>
            <span class="field-hint">3 curseurs, moins de 10 secondes — optionnel.</span>
          </span>
          <app-icon name="chevron-right" [size]="18" />
        </button>
      } @else {
        <div class="ci__form">
          <div class="ci__head">
            <strong>Comment tu te sens ce matin ?</strong>
            <button type="button" class="icon-btn" (click)="open.set(false)" aria-label="Replier">
              <app-icon name="chevron-down" [size]="16" />
            </button>
          </div>

          <!-- Sommeil : contexte pour le coach, jamais un facteur de l'état de forme. -->
          <div class="ci__sleep">
            <div class="ci__sleep-hd">
              <span class="ci__lb">Sommeil</span>
              <span class="ci__sleep-v metric">
                {{ sleep() ?? '—' }}<small>/10</small>
                @if (sleepWord(); as w) { <span class="ci__word">· {{ w }}</span> }
              </span>
            </div>
            <input type="range" class="ci__range" min="1" max="10" step="1"
                   [value]="sleep() ?? 6" (input)="onSleep($event)"
                   aria-label="Qualité de sommeil sur 10" />
          </div>

          <app-pain-fatigue-selector kind="fatigue" [(value)]="fatigue" />
          <app-pain-fatigue-selector kind="pain" [(value)]="pain" />

          <button type="button" class="btn btn-accent btn-lg ci__save"
                  [disabled]="saving() || nothingDeclared()" (click)="save()">
            {{ saving() ? 'Enregistrement…' : 'Envoyer mon check-in' }}
          </button>
        </div>
      }
    </section>
  `,
  styles: [`
    .ci { padding: 0; overflow: hidden; }
    .ci--done { background: var(--paper-sunk); }

    .ci__prompt, .ci__recap {
      display: flex; align-items: center; gap: var(--sp-3); width: 100%;
      padding: var(--sp-3) var(--sp-4); min-height: 56px;
      background: transparent; border: none; text-align: left; cursor: pointer; color: var(--ink);
    }
    .ci__prompt-t { flex: 1; display: flex; flex-direction: column; gap: 1px; min-width: 0; }
    .ci__recap-t { flex: 1; font-weight: 700; }
    .ci__recap-v { color: var(--ink-2); font-weight: 700; font-size: var(--text-sm); }
    .ci__recap app-icon:first-child { color: var(--success); }

    .ci__form { display: flex; flex-direction: column; gap: var(--sp-5); padding: var(--sp-4); }
    .ci__head { display: flex; align-items: center; justify-content: space-between; gap: var(--sp-2); }
    .ci__lb { font-weight: 700; color: var(--ink); }
    .ci__sleep { display: flex; flex-direction: column; gap: var(--sp-2); }
    .ci__sleep-hd { display: flex; align-items: baseline; justify-content: space-between; gap: var(--sp-2); }
    .ci__sleep-v { font-weight: 800; color: var(--ink); }
    .ci__sleep-v small { color: var(--ink-3); font-weight: 600; }
    .ci__word { font-family: var(--font-ui); font-weight: 700; color: var(--ink-3); font-size: var(--text-sm); }

    /* Cible tactile confortable sur la poignée : c'est un geste du pouce, au réveil. */
    .ci__range { width: 100%; height: 44px; accent-color: var(--primary); }
    .ci__save { width: 100%; }
  `],
})
export class CheckInCardComponent implements OnInit {
  private readonly portal = inject(AthletePortalService);
  private readonly toast = inject(ToastService);
  private readonly celebration = inject(CelebrationService);

  /** Émis après enregistrement : l'écran hôte peut rafraîchir ce qui dépend de la forme. */
  readonly saved$ = output<WellnessCheckIn>();

  readonly open = signal(false);
  readonly saving = signal(false);
  readonly saved = signal<WellnessCheckIn | null>(null);

  readonly sleep = signal<number | null>(null);
  readonly fatigue = signal<number | null>(null);
  readonly pain = signal<number | null>(null);

  /** Rien de déclaré : le bouton n'a rien à envoyer. */
  readonly nothingDeclared = computed(() =>
    this.sleep() == null && this.fatigue() == null && this.pain() == null);

  /** Récapitulatif d'une ligne une fois le check-in envoyé. */
  readonly recap = computed(() => {
    const c = this.saved();
    if (!c) return '';
    const parts: string[] = [];
    if (c.sleep != null) parts.push(`sommeil ${c.sleep}`);
    if (c.fatigue != null) parts.push(`fatigue ${c.fatigue}`);
    if (c.pain != null) parts.push(`douleur ${c.pain}`);
    return parts.join(' · ');
  });

  /** Repère verbal du sommeil : un chiffre nu ne dit pas s'il est bon ou mauvais. */
  readonly sleepWord = computed(() => {
    const v = this.sleep();
    if (v == null) return '';
    if (v <= 3) return 'mauvais';
    if (v <= 6) return 'moyen';
    return 'bon';
  });

  ngOnInit(): void {
    this.portal.checkIn().subscribe({
      next: (c) => {
        if (!c) return;
        this.saved.set(c);
        this.sleep.set(c.sleep);
        this.fatigue.set(c.fatigue);
        this.pain.set(c.pain);
      },
      // Le check-in est un bonus : son indisponibilité ne doit rien casser à l'écran.
      error: () => this.saved.set(null),
    });
  }

  protected onSleep(ev: Event): void {
    this.sleep.set(Number((ev.target as HTMLInputElement).value));
  }

  protected save(): void {
    if (this.saving() || this.nothingDeclared()) return;
    this.saving.set(true);
    this.portal.saveCheckIn({ sleep: this.sleep(), fatigue: this.fatigue(), pain: this.pain() }).subscribe({
      next: (c) => {
        this.saved.set(c);
        this.open.set(false);
        this.saving.set(false);
        this.saved$.emit(c);
        this.celebration.fire('Check-in envoyé', 'Ton coach voit ta forme du jour');
      },
      error: () => { this.saving.set(false); this.toast.error('Enregistrement impossible.'); },
    });
  }
}
