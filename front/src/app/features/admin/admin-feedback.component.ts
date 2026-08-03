import { HttpClient } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { environment } from '../../../environments/environment';
import { ToastService } from '../../core/services/toast.service';
import { IconComponent } from '../../shared/components/icon/icon.component';

type FeedbackKind = 'BUG' | 'IDEA' | 'QUESTION';
type FeedbackStatus = 'NEW' | 'HANDLED';

interface BetaFeedback {
  id: string;
  kind: FeedbackKind;
  message: string;
  page: string | null;
  appVersion: string | null;
  userAgent: string | null;
  correlationId: string | null;
  status: FeedbackStatus;
  authorName: string | null;
  createdAt: string;
}

interface Page<T> { content: T[]; totalElements: number; }

const KIND_LABELS: Record<FeedbackKind, string> = {
  BUG: 'Bug', IDEA: 'Idée', QUESTION: 'Question',
};
const KIND_BADGES: Record<FeedbackKind, string> = {
  BUG: 'badge-danger', IDEA: 'badge-info', QUESTION: 'badge-neutral',
};

/**
 * File des retours de bêta (back-office plateforme).
 *
 * <p>C'est la contrepartie du panneau « Signaler un problème ». Sans écran de lecture, le canal
 * de retour n'en est pas un : les messages tomberaient dans une table que personne n'ouvre.</p>
 *
 * <p>La vue par défaut est « à traiter », parce que c'est la question du matin. Le contexte
 * technique est affiché sous chaque message plutôt que replié : c'est précisément ce qu'on
 * cherche en le lisant, et il est court.</p>
 */
@Component({
  selector: 'app-admin-feedback',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, IconComponent],
  template: `
    <header class="page-head">
      <div>
        <h1>Retours de bêta</h1>
        <p class="subtitle">Ce que les testeurs signalent depuis l'application.</p>
      </div>
      <div class="filters" role="group" aria-label="Filtrer par statut">
        @for (f of filters; track f.value) {
          <button type="button" class="btn btn-sm" [class.btn-primary]="filter() === f.value"
                  [class.btn-ghost]="filter() !== f.value" (click)="setFilter(f.value)">
            {{ f.label }}
          </button>
        }
      </div>
    </header>

    @if (loading()) {
      <p class="field-hint">Chargement…</p>
    } @else if (items().length === 0) {
      <div class="card empty">
        <app-icon name="inbox" [size]="28" />
        <p><strong>Aucun retour {{ filter() === 'NEW' ? 'à traiter' : '' }}.</strong></p>
        <p class="field-hint">Les retours envoyés depuis l'application arrivent ici.</p>
      </div>
    } @else {
      <ul class="fb-list">
        @for (f of items(); track f.id) {
          <li class="card fb">
            <div class="fb__head">
              <span class="badge" [class]="kindBadges[f.kind]">{{ kindLabels[f.kind] }}</span>
              <span class="fb__who">{{ f.authorName || 'Compte supprimé' }}</span>
              <span class="fb__when">{{ f.createdAt | date: 'dd/MM/yyyy HH:mm' }}</span>
              @if (f.status === 'NEW') {
                <button type="button" class="btn btn-sm btn-ghost" (click)="markHandled(f)">
                  Marquer traité
                </button>
              } @else {
                <span class="badge badge-success">Traité</span>
              }
            </div>
            <p class="fb__msg">{{ f.message }}</p>
            <dl class="fb__ctx">
              @if (f.page) { <div><dt>Page</dt><dd>{{ f.page }}</dd></div> }
              @if (f.appVersion) { <div><dt>Version</dt><dd>{{ f.appVersion }}</dd></div> }
              @if (f.correlationId) { <div><dt>Erreur</dt><dd class="mono">{{ f.correlationId }}</dd></div> }
              @if (f.userAgent) { <div class="ua"><dt>Navigateur</dt><dd>{{ f.userAgent }}</dd></div> }
            </dl>
          </li>
        }
      </ul>
    }
  `,
  styles: [`
    .page-head { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--sp-4); flex-wrap: wrap; margin-bottom: var(--sp-5); }
    .filters { display: flex; gap: var(--sp-2); }
    .empty { display: flex; flex-direction: column; align-items: center; gap: var(--sp-2); padding: var(--sp-7); color: var(--ink-3); text-align: center; }
    .empty p { margin: 0; }
    .fb-list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: var(--sp-3); }
    .fb { padding: var(--sp-4); }
    .fb__head { display: flex; align-items: center; gap: var(--sp-3); flex-wrap: wrap; margin-bottom: var(--sp-3); }
    .fb__who { font-weight: 600; color: var(--ink); }
    .fb__when { color: var(--ink-3); font-size: var(--text-sm); margin-right: auto; }
    .fb__msg { margin: 0 0 var(--sp-3); white-space: pre-wrap; color: var(--ink); }
    .fb__ctx { display: flex; flex-wrap: wrap; gap: var(--sp-2) var(--sp-5); margin: 0; padding-top: var(--sp-3); border-top: 1px solid var(--line); }
    .fb__ctx div { display: flex; flex-direction: column; gap: 1px; min-width: 0; }
    .fb__ctx dt { color: var(--ink-3); font-size: var(--text-2xs); font-weight: 700; text-transform: uppercase; letter-spacing: 0.03em; }
    .fb__ctx dd { margin: 0; font-size: var(--text-sm); color: var(--ink-2); }
    .fb__ctx .ua { flex-basis: 100%; }
    .fb__ctx .ua dd { overflow-wrap: anywhere; }
    .mono { font-family: var(--font-mono); font-variant-numeric: tabular-nums; }
  `],
})
export class AdminFeedbackComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly toast = inject(ToastService);
  private readonly base = `${environment.apiUrl}/feedback`;

  readonly kindLabels = KIND_LABELS;
  readonly kindBadges = KIND_BADGES;

  readonly items = signal<BetaFeedback[]>([]);
  readonly loading = signal(true);
  /** « À traiter » par défaut : c'est la question qu'on se pose en ouvrant l'écran. */
  readonly filter = signal<FeedbackStatus | 'ALL'>('NEW');

  readonly filters: { value: FeedbackStatus | 'ALL'; label: string }[] = [
    { value: 'NEW', label: 'À traiter' },
    { value: 'HANDLED', label: 'Traités' },
    { value: 'ALL', label: 'Tous' },
  ];

  ngOnInit(): void {
    this.load();
  }

  setFilter(value: FeedbackStatus | 'ALL'): void {
    this.filter.set(value);
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    const f = this.filter();
    const url = f === 'ALL' ? this.base : `${this.base}?status=${f}`;
    this.http.get<Page<BetaFeedback>>(url).subscribe({
      next: (p) => { this.items.set(p.content ?? []); this.loading.set(false); },
      error: () => { this.items.set([]); this.loading.set(false); },
    });
  }

  markHandled(f: BetaFeedback): void {
    this.http.patch<void>(`${this.base}/${f.id}?status=HANDLED`, {}).subscribe({
      next: () => {
        this.toast.success('Retour marqué comme traité.');
        this.load();
      },
    });
  }
}
