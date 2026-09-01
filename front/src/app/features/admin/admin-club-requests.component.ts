import { DatePipe } from '@angular/common';
import { HttpClient, HttpParams } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { environment } from '../../../environments/environment';
import { ConfirmService } from '../../core/services/confirm.service';
import { ToastService } from '../../core/services/toast.service';
import { IconComponent } from '../../shared/components/icon/icon.component';

type ClubRequestStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

interface ClubCreationRequest {
  id: string;
  clubName: string;
  fullName: string;
  email: string;
  phone: string | null;
  message: string | null;
  status: ClubRequestStatus;
  createdAt: string;
  reviewedAt: string | null;
  reviewedByEmail: string | null;
  reviewNote: string | null;
  createdClubId: string | null;
  createdUserId: string | null;
}

interface ApprovalResult {
  request: ClubCreationRequest;
  /** Lien à usage unique donnant au coach l'accès à son club tout neuf. */
  activationUrl: string;
  mailSent: boolean;
}

interface Page<T> {
  content: T[];
  totalElements: number;
}

const STATUS_LABELS: Record<ClubRequestStatus, string> = {
  PENDING: 'En attente',
  APPROVED: 'Validée',
  REJECTED: 'Refusée',
};
const STATUS_BADGES: Record<ClubRequestStatus, string> = {
  PENDING: 'badge-warning',
  APPROVED: 'badge-success',
  REJECTED: 'badge-neutral',
};

/**
 * La file d'arbitrage des demandes de création de club.
 *
 * <p>C'est la contrepartie du formulaire public : sans cet écran, le régime « sur demande » n'en
 * serait pas un — les demandes tomberaient dans une table que personne n'ouvre, et les candidats
 * attendraient une réponse qui ne viendrait jamais.</p>
 *
 * <p>La vue par défaut est « en attente », parce que c'est la question du matin. Le message du
 * candidat est affiché en entier plutôt que replié : c'est exactement ce sur quoi on décide.</p>
 */
@Component({
  selector: 'app-admin-club-requests',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, IconComponent],
  template: `
    <header class="page-head">
      <div>
        <h1>Demandes de club</h1>
        <p class="subtitle">
          Qui demande à entrer, et ce qu'on en fait. La validation ouvre le club et envoie au
          coach son lien d'accès.
        </p>
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

    <!-- Le lien n'est montré qu'une fois, juste après la validation : l'envoi d'e-mails peut
         être éteint, ou l'adresse rebondir, et le coach qu'on vient d'accepter resterait alors
         devant une porte fermée sans que personne ne le sache. -->
    @if (lastApproval(); as a) {
      <div class="card alert alert-success acr-approved">
        <div>
          <strong>{{ a.request.clubName }} est ouvert.</strong>
          <p class="field-hint">
            {{ a.mailSent
              ? 'Le lien d’accès est parti à ' + a.request.email + '. Copiez-le ci-dessous si l’e-mail n’arrive pas.'
              : 'L’envoi d’e-mails est désactivé sur cette instance : transmettez ce lien vous-même à ' + a.request.email + '.' }}
          </p>
          <code class="acr-link">{{ a.activationUrl }}</code>
        </div>
        <button type="button" class="btn btn-ghost btn-sm" (click)="copyLink(a.activationUrl)">
          <app-icon name="copy" [size]="15" /> Copier
        </button>
      </div>
    }

    @if (loading()) {
      <p class="field-hint">Chargement…</p>
    } @else if (failed()) {
      <!-- Une liste vidée par une erreur se lisait « aucune demande » : on nomme l'échec. -->
      <div class="card alert alert-danger acr-error">
        <div>
          <strong>Les demandes n'ont pas pu être chargées.</strong>
          <p class="field-hint">Rien n'est affiché : un écran vide ferait croire à zéro demande.</p>
        </div>
        <button type="button" class="btn btn-primary btn-sm" (click)="load()">Réessayer</button>
      </div>
    } @else if (items().length === 0) {
      <div class="card empty">
        <app-icon name="inbox" [size]="28" />
        <p><strong>Aucune demande {{ filter() === 'PENDING' ? 'en attente' : '' }}.</strong></p>
        <p class="field-hint">Les demandes déposées depuis la page « Créer mon club » arrivent ici.</p>
      </div>
    } @else {
      <ul class="acr-list">
        @for (r of items(); track r.id) {
          <li class="card acr">
            <div class="acr__head">
              <strong class="acr__club">{{ r.clubName }}</strong>
              <span class="badge" [class]="statusBadges[r.status]">{{ statusLabels[r.status] }}</span>
              <span class="acr__when">{{ r.createdAt | date: 'dd/MM/yyyy HH:mm' }}</span>
            </div>

            <dl class="acr__who">
              <div><dt>Demandeur</dt><dd>{{ r.fullName }}</dd></div>
              <div><dt>E-mail</dt><dd>{{ r.email }}</dd></div>
              @if (r.phone) { <div><dt>Téléphone</dt><dd>{{ r.phone }}</dd></div> }
            </dl>

            @if (r.message) {
              <p class="acr__msg">{{ r.message }}</p>
            }

            @if (r.status === 'PENDING') {
              <div class="acr__actions">
                <button type="button" class="btn btn-ghost btn-sm" [disabled]="busy() === r.id"
                        (click)="reject(r)">Refuser</button>
                <button type="button" class="btn btn-primary btn-sm" [disabled]="busy() === r.id"
                        (click)="approve(r)">
                  {{ busy() === r.id ? 'Traitement…' : 'Valider et ouvrir le club' }}
                </button>
              </div>
            } @else {
              <p class="field-hint acr__verdict">
                {{ statusLabels[r.status] }}
                @if (r.reviewedAt) { le {{ r.reviewedAt | date: 'dd/MM/yyyy' }} }
                @if (r.reviewedByEmail) { par {{ r.reviewedByEmail }} }
                @if (r.reviewNote) { — « {{ r.reviewNote }} » }
              </p>
            }
          </li>
        }
      </ul>
    }
  `,
  styles: [`
    .page-head { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--sp-4); flex-wrap: wrap; margin-bottom: var(--sp-5); }
    .filters { display: flex; gap: var(--sp-2); }
    .acr-error, .acr-approved { display: flex; align-items: center; justify-content: space-between; gap: var(--sp-4); margin-bottom: var(--sp-4); }
    .acr-link { display: block; margin-top: var(--sp-2); font-family: var(--font-mono); font-size: var(--text-sm); overflow-wrap: anywhere; }
    .empty { display: flex; flex-direction: column; align-items: center; gap: var(--sp-2); padding: var(--sp-7); color: var(--ink-3); text-align: center; }
    .empty p { margin: 0; }
    .acr-list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: var(--sp-3); }
    .acr { padding: var(--sp-4); }
    .acr__head { display: flex; align-items: center; gap: var(--sp-3); flex-wrap: wrap; margin-bottom: var(--sp-3); }
    .acr__club { font-size: var(--text-lg); color: var(--ink); }
    .acr__when { color: var(--ink-3); font-size: var(--text-sm); margin-left: auto; }
    .acr__who { display: flex; flex-wrap: wrap; gap: var(--sp-2) var(--sp-5); margin: 0 0 var(--sp-3); }
    .acr__who div { display: flex; flex-direction: column; gap: 1px; min-width: 0; }
    .acr__who dt { color: var(--ink-3); font-size: var(--text-2xs); font-weight: 700; text-transform: uppercase; letter-spacing: 0.03em; }
    .acr__who dd { margin: 0; font-size: var(--text-sm); color: var(--ink-2); overflow-wrap: anywhere; }
    .acr__msg { margin: 0 0 var(--sp-3); white-space: pre-wrap; color: var(--ink); }
    .acr__actions { display: flex; justify-content: flex-end; gap: var(--sp-2); padding-top: var(--sp-3); border-top: 1px solid var(--hairline); }
    .acr__verdict { padding-top: var(--sp-3); border-top: 1px solid var(--hairline); margin: 0; }
  `],
})
export class AdminClubRequestsComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly toast = inject(ToastService);
  private readonly confirm = inject(ConfirmService);
  private readonly base = `${environment.apiUrl}/admin/club-requests`;

  readonly statusLabels = STATUS_LABELS;
  readonly statusBadges = STATUS_BADGES;

  readonly items = signal<ClubCreationRequest[]>([]);
  readonly loading = signal(true);
  readonly failed = signal(false);
  /** Identifiant de la demande en cours de traitement, pour ne bloquer que ses boutons. */
  readonly busy = signal<string | null>(null);
  /** La dernière validation, tant qu'on est sur l'écran : elle porte le lien d'accès. */
  readonly lastApproval = signal<ApprovalResult | null>(null);

  /** « En attente » par défaut : c'est la question qu'on se pose en ouvrant l'écran. */
  readonly filter = signal<ClubRequestStatus | 'ALL'>('PENDING');

  readonly filters: { value: ClubRequestStatus | 'ALL'; label: string }[] = [
    { value: 'PENDING', label: 'En attente' },
    { value: 'APPROVED', label: 'Validées' },
    { value: 'REJECTED', label: 'Refusées' },
    { value: 'ALL', label: 'Toutes' },
  ];

  ngOnInit(): void {
    this.load();
  }

  setFilter(value: ClubRequestStatus | 'ALL'): void {
    this.filter.set(value);
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.failed.set(false);
    const f = this.filter();
    const params = f === 'ALL' ? new HttpParams() : new HttpParams().set('status', f);
    this.http.get<Page<ClubCreationRequest>>(this.base, { params }).subscribe({
      next: (p) => {
        this.items.set(p.content ?? []);
        this.loading.set(false);
      },
      error: () => {
        this.items.set([]);
        this.failed.set(true);
        this.loading.set(false);
      },
    });
  }

  async approve(r: ClubCreationRequest): Promise<void> {
    const ok = await this.confirm.ask({
      title: 'Valider ' + r.clubName,
      message:
        `Le club sera créé et ${r.fullName} recevra un lien pour choisir son mot de passe `
        + `à l'adresse ${r.email}. Continuer ?`,
      confirmLabel: 'Valider et ouvrir',
    });
    if (!ok) return;

    this.busy.set(r.id);
    this.http.post<ApprovalResult>(`${this.base}/${r.id}/approve`, {}).subscribe({
      next: (result) => {
        this.lastApproval.set(result);
        this.toast.success(`${r.clubName} est ouvert.`);
        this.busy.set(null);
        this.load();
      },
      error: () => this.busy.set(null),
    });
  }

  async reject(r: ClubCreationRequest): Promise<void> {
    // Un motif, et non un simple oui/non : il part au demandeur. Un refus sans un mot est une
    // porte close sans explication, et le candidat redépose la même demande la semaine suivante.
    const note = await this.confirm.prompt({
      title: 'Refuser ' + r.clubName,
      message:
        `Ce motif sera envoyé à ${r.email}. Laissez vide pour refuser sans explication — `
        + `le demandeur ne saura alors pas quoi corriger.`,
      confirmLabel: 'Refuser',
      promptLabel: 'Motif du refus',
      danger: true,
    });
    if (note === null) return;

    this.busy.set(r.id);
    this.http.post<ClubCreationRequest>(`${this.base}/${r.id}/reject`, { note }).subscribe({
      next: () => {
        this.toast.success('Demande refusée.');
        this.busy.set(null);
        this.load();
      },
      error: () => this.busy.set(null),
    });
  }

  copyLink(url: string): void {
    navigator.clipboard?.writeText(url).then(
      () => this.toast.success('Lien copié.'),
      // Le presse-papiers peut être refusé (contexte non sécurisé, permission) : le lien reste
      // affiché à l'écran, il est toujours sélectionnable à la main.
      () => this.toast.error('Copie refusée par le navigateur — sélectionnez le lien à la main.'),
    );
  }
}
