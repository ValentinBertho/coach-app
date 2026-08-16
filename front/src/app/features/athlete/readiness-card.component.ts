import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { Advice, Readiness } from '../../core/models/decision.model';
import { AthletePortalService } from '../../core/services/athlete-portal.service';
import { ToastService } from '../../core/services/toast.service';
import { IconComponent } from '../../shared/components/icon/icon.component';

/** Ce que dit le bouton, selon ce qui est conseillé. */
const ACTION_LABELS: Record<Advice, string> = {
  KEEP: '',
  LIGHTEN: 'Demander la version allégée',
  EASY: 'Demander un footing à la place',
  MOVE: 'Demander à décaler à demain',
};

/**
 * Le feu vert du matin — la réponse à « je fais quoi aujourd'hui ? ».
 *
 * <p><b>Le manque qu'il comble.</b> L'athlète déclarait fatigue 8 et douleur 4 à sept heures ; à
 * dix-huit heures, il lisait « 6 × 1000 m à 3:45 ». Le check-in produisait une pastille pour le
 * coach, une alerte si la douleur était haute, et rien pour celui qui l'avait rempli. C'est le
 * meilleur moyen de faire cesser une saisie quotidienne.</p>
 *
 * <p><b>Ce que cette carte ne fait pas.</b> Elle ne modifie aucune séance. Elle conseille, montre à
 * quoi ressemblerait l'adaptation — « 6 × 1000 » barré, « 4 × 1000 » à côté — et si l'athlète la
 * veut, elle envoie une <b>demande</b> à son coach. La séance ne change que lorsque le coach
 * accepte. Un plan est l'engagement d'un coach envers son athlète : ni l'application ni l'athlète
 * ne le réécrivent seuls.</p>
 *
 * <p>Silencieuse quand tout va bien : un feu vert n'a pas besoin d'occuper un écran. Elle
 * n'apparaît que lorsqu'il y a quelque chose à dire.</p>
 */
@Component({
  selector: 'app-readiness-card',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    @if (data(); as r) {
      @if (visible()) {
        <section class="rc card" [class]="'rc--' + r.severity.toLowerCase()">
          <header class="rc__hd">
            <span class="rc__dot" aria-hidden="true"></span>
            <strong class="rc__title">{{ r.title }}</strong>
          </header>

          @if (r.reason) { <p class="rc__reason">{{ r.reason }}</p> }

          <!-- Prévu / proposé, côte à côte : on n'accepte pas une adaptation qu'on ne voit pas. -->
          @if (r.adaptedSummary && r.plannedSummary) {
            <div class="rc__diff">
              <span class="rc__from">{{ r.plannedSummary }}</span>
              <app-icon name="arrow-right" [size]="15" />
              <span class="rc__to">{{ r.adaptedSummary }}</span>
            </div>
          } @else if (r.plannedSummary) {
            <p class="rc__planned">Prévu : <span class="metric">{{ r.plannedSummary }}</span></p>
          }

          @if (r.pendingRequest) {
            <p class="rc__sent">
              <app-icon name="check" [size]="15" />
              Demande envoyée à ton coach — ta séance ne change pas tant qu'il n'a pas répondu.
            </p>
          } @else if (actionLabel()) {
            <div class="rc__actions">
              <button type="button" class="btn btn-accent btn-sm" [disabled]="busy()"
                      (click)="request(r.advice)">
                {{ busy() ? 'Envoi…' : actionLabel() }}
              </button>
              <button type="button" class="btn btn-ghost btn-sm" (click)="dismissed.set(true)">
                Je fais comme prévu
              </button>
            </div>
            <p class="rc__note">C'est ton coach qui décide : cela lui envoie une demande.</p>
          }
        </section>
      }
    }
  `,
  styles: [`
    .rc {
      display: flex; flex-direction: column; gap: var(--sp-2);
      border-left: 4px solid var(--lc, var(--ink-4));
    }
    .rc--green  { --lc: var(--form-green, var(--success)); }
    .rc--orange { --lc: var(--form-orange, var(--warning)); }
    .rc--red    { --lc: var(--form-red, var(--danger)); }
    .rc--stale  { --lc: var(--ink-3); }

    .rc__hd { display: flex; align-items: center; gap: var(--sp-2); }
    .rc__dot {
      width: 10px; height: 10px; border-radius: 50%; background: var(--lc); flex-shrink: 0;
    }
    .rc__title { color: var(--ink); font-size: var(--text-lg); }
    .rc__reason { margin: 0; color: var(--ink-2); font-size: var(--text-sm); }

    .rc__diff {
      display: flex; align-items: center; gap: var(--sp-2); flex-wrap: wrap;
      padding: var(--sp-2) var(--sp-3); border-radius: var(--radius-sm);
      background: var(--paper-sunk, var(--canvas));
      font-family: var(--font-mono); font-size: var(--text-sm);
    }
    .rc__from { color: var(--ink-3); text-decoration: line-through; }
    .rc__to { color: var(--ink); font-weight: 600; }
    .rc__planned { margin: 0; font-size: var(--text-sm); color: var(--ink-2); }

    .rc__actions { display: flex; gap: var(--sp-2); flex-wrap: wrap; margin-top: var(--sp-1); }
    .rc__actions .btn { min-height: 44px; }
    .rc__note, .rc__sent {
      margin: 0; font-size: var(--text-xs); color: var(--ink-3);
      display: flex; align-items: center; gap: var(--sp-1);
    }
    .rc__sent { color: var(--success); font-weight: 600; }
  `],
})
export class ReadinessCardComponent implements OnInit {
  private readonly portal = inject(AthletePortalService);
  private readonly toast = inject(ToastService);

  /** Rechargement déclenché par le parent (après un check-in, par exemple). */
  readonly reloadKey = input<number>(0);

  /** Émis quand une demande part : le parent peut rafraîchir ce qui en dépend. */
  readonly requested = output<void>();

  readonly data = signal<Readiness | null>(null);
  readonly busy = signal(false);
  readonly dismissed = signal(false);

  /**
   * Un feu vert ne s'affiche pas.
   *
   * <p>La carte ne parle que lorsqu'il y a une conduite à tenir ou une gêne à accuser réception.
   * Afficher « tout va bien » chaque matin apprendrait à ne plus la lire — et le jour où elle dit
   * quelque chose, personne ne la verrait.</p>
   */
  readonly visible = computed(() => {
    const r = this.data();
    if (!r || this.dismissed()) return false;
    return r.advice !== 'KEEP' || !!r.reason;
  });

  readonly actionLabel = computed(() => {
    const r = this.data();
    return r ? ACTION_LABELS[r.advice] : '';
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.portal.readiness().subscribe({
      next: (r) => this.data.set(r),
      error: () => this.data.set(null),
    });
  }

  request(advice: Advice): void {
    if (this.busy()) return;
    this.busy.set(true);
    this.portal.requestAdaptation(advice).subscribe({
      next: (r) => {
        this.data.set(r);
        this.busy.set(false);
        this.toast.success('Demande envoyée à ton coach.');
        this.requested.emit();
      },
      error: () => {
        this.busy.set(false);
        this.toast.error('Envoi impossible pour le moment.');
      },
    });
  }
}
