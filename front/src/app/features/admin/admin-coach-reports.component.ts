import { DatePipe } from '@angular/common';
import { HttpClient, HttpParams } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { environment } from '../../../environments/environment';
import { ConfirmService } from '../../core/services/confirm.service';
import { ToastService } from '../../core/services/toast.service';
import { IconComponent } from '../../shared/components/icon/icon.component';

type ReportStatus = 'OPEN' | 'ACTED_UPON' | 'DISMISSED';

interface CoachReport {
  id: string;
  profileId: string;
  coachSlug: string;
  coachName: string;
  reason: string;
  reasonLabel: string;
  details: string;
  status: ReportStatus;
  statusLabel: string;
  createdAt: string;
  handledAt: string | null;
  moderatorNote: string | null;
  /** Faux quand le signalement est anonyme : il ne se pèse pas comme un signalement nominatif. */
  fromKnownUser: boolean;
  /** Signalements encore ouverts sur la même fiche : la première question qu'on se pose. */
  openReportsOnProfile: number;
}

const STATUS_BADGES: Record<ReportStatus, string> = {
  OPEN: 'badge-warning',
  ACTED_UPON: 'badge-success',
  DISMISSED: 'badge-neutral',
};

/**
 * La file des signalements de fiches coachs.
 *
 * <p>C'est le pendant de la décision 4 : les diplômes sont publiés comme déclarés, sans
 * vérification, et cet écran est l'endroit où la plateforme tient l'autre moitié du marché —
 * écouter ce qu'on lui rapporte.</p>
 *
 * <p><b>Clore n'est pas sanctionner.</b> Les deux gestes sont séparés, et volontairement : le
 * bouton qui retire une fiche de l'annuaire vit sur l'écran des fiches. Les réunir ici ferait
 * d'un clic de tri une suspension, ce qui est exactement l'automatisme que le dispositif
 * refuse.</p>
 *
 * <p>Le nombre de signalements ouverts sur la même fiche est affiché sur chaque ligne : un
 * signalement isolé et le cinquième sur le même coach ne se lisent pas de la même façon, et
 * l'arbitre ne devrait pas avoir à le reconstituer en parcourant la liste.</p>
 */
@Component({
  selector: 'app-admin-coach-reports',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, IconComponent, RouterLink],
  template: `
    <header class="page-head">
      <div>
        <h1 class="display-sm">Signalements</h1>
        <p class="field-hint">
          La plateforme ne vérifie pas les diplômes qu'elle publie ; elle lit ce qu'on lui
          rapporte. Aucun signalement ne retire une fiche tout seul.
        </p>
      </div>
      <div class="filters">
        @for (f of filters; track f.value) {
          <button type="button" class="btn btn-sm"
                  [class.btn-primary]="filter() === f.value"
                  [class.btn-ghost]="filter() !== f.value"
                  (click)="setFilter(f.value)">{{ f.label }}</button>
        }
      </div>
    </header>

    @if (loading()) {
      <p class="acr-loading">Chargement…</p>
    } @else if (!items().length) {
      <div class="empty">
        <app-icon name="inbox" [size]="28" />
        <p>Aucun signalement dans cette file.</p>
      </div>
    } @else {
      <ul class="acr-list">
        @for (r of items(); track r.id) {
          <li class="card acr">
            <div class="acr__head">
              <strong class="acr__name">{{ r.coachName }}</strong>
              <span class="badge" [class]="badges[r.status]">{{ r.statusLabel }}</span>
              <span class="badge badge-neutral">{{ r.reasonLabel }}</span>
              @if (r.status === 'OPEN' && r.openReportsOnProfile > 1) {
                <span class="badge badge-danger">
                  {{ r.openReportsOnProfile }} signalements ouverts sur cette fiche
                </span>
              }
              <span class="acr__when">reçu le {{ r.createdAt | date: 'dd/MM/yyyy' }}</span>
            </div>

            <p class="acr__origin field-hint">
              @if (r.fromKnownUser) {
                Signalement déposé depuis un compte.
              } @else {
                Signalement anonyme — recevable, mais à peser comme tel.
              }
              <a [routerLink]="['/coachs', r.coachSlug]" target="_blank" rel="noopener">
                Voir la fiche publique
              </a>
            </p>

            <p class="acr__details">{{ r.details }}</p>

            @if (r.status === 'OPEN') {
              <div class="acr__actions">
                <button type="button" class="btn btn-ghost btn-sm" [disabled]="busy() === r.id"
                        (click)="close(r, 'dismiss')">Sans suite</button>
                <button type="button" class="btn btn-secondary btn-sm" [disabled]="busy() === r.id"
                        (click)="close(r, 'act')">Suite donnée</button>
              </div>
              <p class="field-hint acr__hint">
                Pour retirer la fiche de l'annuaire, passez par
                <a routerLink="/admin/coach-profiles">Fiches coachs</a> : clore un signalement ne
                touche pas à la fiche.
              </p>
            } @else if (r.handledAt) {
              <p class="acr__verdict field-hint">
                Clos le {{ r.handledAt | date: 'dd/MM/yyyy' }}
                @if (r.moderatorNote) { — « {{ r.moderatorNote }} » }
              </p>
            }
          </li>
        }
      </ul>
    }
  `,
  styles: [`
    .page-head { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--sp-4); flex-wrap: wrap; margin-bottom: var(--sp-5); }
    .filters { display: flex; gap: var(--sp-2); flex-wrap: wrap; }
    .acr-loading { color: var(--ink-3); padding: var(--sp-6) 0; }
    .empty { display: flex; flex-direction: column; align-items: center; gap: var(--sp-2); padding: var(--sp-7); color: var(--ink-3); text-align: center; }
    .empty p { margin: 0; }
    .acr-list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: var(--sp-3); }
    .acr { padding: var(--sp-4); }
    .acr__head { display: flex; align-items: center; gap: var(--sp-2); flex-wrap: wrap; margin-bottom: var(--sp-2); }
    .acr__name { font-size: var(--text-lg); color: var(--ink); margin-right: var(--sp-1); }
    .acr__when { color: var(--ink-3); font-size: var(--text-sm); margin-left: auto; }
    .acr__origin { display: flex; gap: var(--sp-2); flex-wrap: wrap; margin: 0 0 var(--sp-3); }
    .acr__details { margin: 0 0 var(--sp-3); white-space: pre-wrap; color: var(--ink-2); font-size: var(--text-sm); }
    .acr__actions { display: flex; justify-content: flex-end; gap: var(--sp-2); padding-top: var(--sp-3); border-top: 1px solid var(--hairline); }
    .acr__hint { margin: var(--sp-2) 0 0; text-align: right; }
    .acr__verdict { padding-top: var(--sp-3); margin: var(--sp-2) 0 0; border-top: 1px solid var(--hairline); }
  `],
})
export class AdminCoachReportsComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly toast = inject(ToastService);
  private readonly confirm = inject(ConfirmService);
  private readonly base = `${environment.apiUrl}/admin/coach-reports`;

  readonly badges = STATUS_BADGES;
  readonly items = signal<CoachReport[]>([]);
  readonly loading = signal(true);
  readonly busy = signal<string | null>(null);

  /** « À traiter » par défaut : c'est la question qu'on se pose en ouvrant l'écran. */
  readonly filter = signal<ReportStatus>('OPEN');

  readonly filters: { value: ReportStatus; label: string }[] = [
    { value: 'OPEN', label: 'À traiter' },
    { value: 'ACTED_UPON', label: 'Suite donnée' },
    { value: 'DISMISSED', label: 'Sans suite' },
  ];

  ngOnInit(): void {
    this.load();
  }

  setFilter(value: ReportStatus): void {
    this.filter.set(value);
    this.load();
  }

  load(): void {
    this.loading.set(true);
    const params = new HttpParams().set('status', this.filter());
    this.http.get<CoachReport[]>(this.base, { params }).subscribe({
      next: (list) => {
        this.items.set(list);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.toast.error('La file des signalements est injoignable');
      },
    });
  }

  /**
   * Clôt un signalement, avec ou sans suite.
   *
   * <p>La note reste interne : elle peut contenir ce que le coach a répondu, un diplôme qu'il a
   * effectivement produit. Elle n'est jamais renvoyée au signalant, à qui rien de tout cela
   * n'appartient.</p>
   */
  async close(r: CoachReport, action: 'act' | 'dismiss'): Promise<void> {
    const wording = action === 'act'
      ? { title: 'Suite donnée à ce signalement ?', label: 'Clore', prompt: 'Ce qui a été fait (note interne)' }
      : { title: 'Clore sans suite ?', label: 'Clore', prompt: 'Ce qui a été constaté (note interne)' };

    const note = await this.confirm.prompt({
      title: wording.title,
      message: `Fiche de ${r.coachName}. Clore ne retire pas la fiche de l'annuaire.`,
      confirmLabel: wording.label,
      promptLabel: wording.prompt,
    });
    if (note === null) {
      return;
    }

    this.busy.set(r.id);
    this.http.post<CoachReport>(`${this.base}/${r.id}/${action}`,
      { note: note.trim() || null }).subscribe({
      next: () => {
        this.busy.set(null);
        this.toast.success('Signalement clos');
        this.load();
      },
      error: (err) => {
        this.busy.set(null);
        this.toast.error(err?.error?.message ?? "Le signalement n'a pas pu être clos");
      },
    });
  }
}
