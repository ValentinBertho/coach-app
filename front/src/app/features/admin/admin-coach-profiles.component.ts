import { DatePipe } from '@angular/common';
import { HttpClient, HttpParams } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { environment } from '../../../environments/environment';
import { ConfirmService } from '../../core/services/confirm.service';
import { ToastService } from '../../core/services/toast.service';
import { IconComponent } from '../../shared/components/icon/icon.component';

type CoachProfileStatus = 'DRAFT' | 'PENDING' | 'PUBLISHED' | 'SUSPENDED' | 'CLOSED';

interface Certification {
  id: string;
  label: string;
  organisation: string | null;
  obtainedYear: number | null;
}

interface Offer {
  id: string;
  name: string;
  amountCents: number;
  suffix: string;
  active: boolean;
}

interface AdminCoachProfile {
  id: string;
  coachId: string;
  coachName: string;
  coachEmail: string;
  slug: string;
  status: CoachProfileStatus;
  statusLabel: string;
  headline: string | null;
  bio: string | null;
  specialties: string[];
  city: string | null;
  experienceYears: number | null;
  submittedAt: string | null;
  reviewedAt: string | null;
  reviewedByEmail: string | null;
  reviewNote: string | null;
  /** Chemin servi par l'API ; une fiche se valide aussi sur sa photo. */
  photoUrl: string | null;
  certifications: Certification[];
  offers: Offer[];
}

interface Page<T> {
  content: T[];
  totalElements: number;
}

const STATUS_BADGES: Record<CoachProfileStatus, string> = {
  DRAFT: 'badge-neutral',
  PENDING: 'badge-warning',
  PUBLISHED: 'badge-success',
  SUSPENDED: 'badge-danger',
  CLOSED: 'badge-neutral',
};

/**
 * La file d'arbitrage des fiches coachs.
 *
 * <p>Même forme que la file des demandes de création de club, et volontairement : c'est le même
 * geste — regarder, décider, motiver — et l'équipe n'a pas deux écrans à apprendre.</p>
 *
 * <p>Chaque ligne porte de quoi <b>décider sans rien ouvrir d'autre</b> : la présentation en
 * entier, les diplômes déclarés, les tarifs. Une file qui n'affiche qu'un nom oblige à ouvrir
 * chaque dossier, et une file qu'on n'arbitre pas en trois minutes ne s'arbitre pas du tout.</p>
 */
@Component({
  selector: 'app-admin-coach-profiles',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, IconComponent],
  template: `
    <header class="page-head">
      <div>
        <h1 class="display-sm">Fiches coachs</h1>
        <p class="field-hint">
          Une place de marché se juge à son pire profil : chaque fiche est relue avant de paraître
          dans l'annuaire.
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
      <p class="acp-loading">Chargement…</p>
    } @else if (!items().length) {
      <div class="empty">
        <app-icon name="inbox" [size]="28" />
        <p>Aucune fiche dans cette file.</p>
      </div>
    } @else {
      <ul class="acp-list">
        @for (p of items(); track p.id) {
          <li class="card acp">
            <div class="acp__head">
              @if (photoSrc(p); as src) {
                <img class="acp__photo" [src]="src" [alt]="'Photo de ' + p.coachName" />
              }
              <strong class="acp__name">{{ p.coachName }}</strong>
              <span class="badge" [class]="badges[p.status]">{{ p.statusLabel }}</span>
              @if (p.submittedAt) {
                <span class="acp__when">déposée le {{ p.submittedAt | date: 'dd/MM/yyyy' }}</span>
              }
            </div>

            <dl class="acp__who">
              <div><dt>E-mail</dt><dd>{{ p.coachEmail }}</dd></div>
              @if (p.city) { <div><dt>Ville</dt><dd>{{ p.city }}</dd></div> }
              @if (p.experienceYears !== null) {
                <div><dt>Expérience</dt><dd>{{ p.experienceYears }} ans</dd></div>
              }
              <div><dt>Adresse</dt><dd>/coachs/{{ p.slug }}</dd></div>
            </dl>

            @if (p.headline) { <p class="acp__headline">« {{ p.headline }} »</p> }
            @if (p.bio) { <p class="acp__bio">{{ p.bio }}</p> }

            @if (p.specialties.length) {
              <p class="acp__tags">
                @for (s of p.specialties; track s) { <span class="badge badge-neutral">{{ s }}</span> }
              </p>
            }

            @if (p.certifications.length) {
              <div class="acp__sub">
                <!-- Déclarés, jamais vérifiés par la plateforme : l'écran d'arbitrage le rappelle
                     à celui-là même qui pourrait être tenté de les valider. -->
                <span class="acp__sub-title">Diplômes déclarés (non vérifiés)</span>
                <ul>
                  @for (c of p.certifications; track c.id) {
                    <li>{{ c.label }}@if (c.organisation) { — {{ c.organisation }} }@if (c.obtainedYear) { ({{ c.obtainedYear }}) }</li>
                  }
                </ul>
              </div>
            }

            @if (p.offers.length) {
              <div class="acp__sub">
                <span class="acp__sub-title">Formules</span>
                <ul>
                  @for (o of p.offers; track o.id) {
                    @if (o.active) { <li>{{ o.name }} — {{ o.amountCents / 100 }} € {{ o.suffix }}</li> }
                  }
                </ul>
              </div>
            }

            @if (p.status === 'PENDING') {
              <div class="acp__actions">
                <button type="button" class="btn btn-ghost btn-sm" [disabled]="busy() === p.id"
                        (click)="decide(p, 'reject')">Renvoyer pour correction</button>
                <button type="button" class="btn btn-primary btn-sm" [disabled]="busy() === p.id"
                        (click)="decide(p, 'approve')">Publier</button>
              </div>
            } @else if (p.status === 'PUBLISHED' || p.status === 'CLOSED') {
              <div class="acp__actions">
                <button type="button" class="btn btn-ghost btn-sm" [disabled]="busy() === p.id"
                        (click)="decide(p, 'suspend')">Retirer de l'annuaire</button>
              </div>
            } @else if (p.status === 'SUSPENDED') {
              <div class="acp__actions">
                <button type="button" class="btn btn-secondary btn-sm" [disabled]="busy() === p.id"
                        (click)="decide(p, 'reinstate')">Lever la suspension</button>
              </div>
            }

            @if (p.reviewedAt) {
              <p class="acp__verdict field-hint">
                Arbitrée le {{ p.reviewedAt | date: 'dd/MM/yyyy' }}
                @if (p.reviewedByEmail) { par {{ p.reviewedByEmail }} }
                @if (p.reviewNote) { — « {{ p.reviewNote }} » }
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
    .acp-loading { color: var(--ink-3); padding: var(--sp-6) 0; }
    .empty { display: flex; flex-direction: column; align-items: center; gap: var(--sp-2); padding: var(--sp-7); color: var(--ink-3); text-align: center; }
    .empty p { margin: 0; }
    .acp-list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: var(--sp-3); }
    .acp { padding: var(--sp-4); }
    .acp__head { display: flex; align-items: center; gap: var(--sp-3); flex-wrap: wrap; margin-bottom: var(--sp-3); }
    .acp__photo { width: 44px; height: 44px; border-radius: var(--radius-full); object-fit: cover; background: var(--paper-sunk); }
    .acp__name { font-size: var(--text-lg); color: var(--ink); }
    .acp__when { color: var(--ink-3); font-size: var(--text-sm); margin-left: auto; }
    .acp__who { display: flex; flex-wrap: wrap; gap: var(--sp-2) var(--sp-5); margin: 0 0 var(--sp-3); }
    .acp__who div { display: flex; flex-direction: column; gap: 1px; min-width: 0; }
    .acp__who dt { color: var(--ink-3); font-size: var(--text-2xs); font-weight: 700; text-transform: uppercase; letter-spacing: 0.03em; }
    .acp__who dd { margin: 0; font-size: var(--text-sm); color: var(--ink-2); overflow-wrap: anywhere; }
    .acp__headline { margin: 0 0 var(--sp-2); color: var(--ink); font-style: italic; }
    .acp__bio { margin: 0 0 var(--sp-3); white-space: pre-wrap; color: var(--ink-2); font-size: var(--text-sm); }
    .acp__tags { display: flex; flex-wrap: wrap; gap: var(--sp-1); margin: 0 0 var(--sp-3); }
    .acp__sub { margin-bottom: var(--sp-3); }
    .acp__sub-title { display: block; color: var(--ink-3); font-size: var(--text-2xs); font-weight: 700; text-transform: uppercase; letter-spacing: 0.03em; margin-bottom: var(--sp-1); }
    .acp__sub ul { margin: 0; padding-left: var(--sp-5); font-size: var(--text-sm); color: var(--ink-2); }
    .acp__actions { display: flex; justify-content: flex-end; gap: var(--sp-2); padding-top: var(--sp-3); border-top: 1px solid var(--hairline); }
    .acp__verdict { padding-top: var(--sp-3); margin: var(--sp-2) 0 0; }
  `],
})
export class AdminCoachProfilesComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly toast = inject(ToastService);
  private readonly confirm = inject(ConfirmService);
  private readonly base = `${environment.apiUrl}/admin/coach-profiles`;

  readonly badges = STATUS_BADGES;
  readonly items = signal<AdminCoachProfile[]>([]);
  readonly loading = signal(true);
  /** Identifiant de la fiche en cours de traitement, pour ne bloquer que ses boutons. */
  readonly busy = signal<string | null>(null);

  /** « En validation » par défaut : c'est la question qu'on se pose en ouvrant l'écran. */
  readonly filter = signal<CoachProfileStatus | 'ALL'>('PENDING');

  readonly filters: { value: CoachProfileStatus | 'ALL'; label: string }[] = [
    { value: 'PENDING', label: 'En validation' },
    { value: 'PUBLISHED', label: 'Publiées' },
    { value: 'SUSPENDED', label: 'Suspendues' },
    { value: 'ALL', label: 'Toutes' },
  ];

  ngOnInit(): void {
    this.load();
  }

  /** L'API rend un chemin ; le domaine se compose ici. */
  photoSrc(p: AdminCoachProfile): string | null {
    return p.photoUrl ? `${environment.apiUrl}${p.photoUrl}` : null;
  }

  setFilter(value: CoachProfileStatus | 'ALL'): void {
    this.filter.set(value);
    this.load();
  }

  load(): void {
    this.loading.set(true);
    const f = this.filter();
    const params = f === 'ALL' ? new HttpParams() : new HttpParams().set('status', f);
    this.http.get<Page<AdminCoachProfile>>(this.base, { params }).subscribe({
      next: (page) => {
        this.items.set(page.content);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.toast.error('La file des fiches coachs est injoignable');
      },
    });
  }

  /**
   * Toute décision passe par une confirmation qui demande un mot.
   *
   * <p>Le motif n'est pas une formalité : sur un renvoi, il part au coach et c'est la seule chose
   * qui lui dise quoi corriger. Sans lui, il resoumet la même fiche la semaine suivante.</p>
   */
  async decide(p: AdminCoachProfile, action: 'approve' | 'reject' | 'suspend' | 'reinstate'): Promise<void> {
    const wording = {
      approve: { title: 'Publier cette fiche ?', label: 'Publier', prompt: 'Note interne (facultative)' },
      reject: { title: 'Renvoyer pour correction ?', label: 'Renvoyer', prompt: 'Ce que le coach doit corriger' },
      suspend: { title: "Retirer de l'annuaire ?", label: 'Retirer', prompt: 'Motif du retrait' },
      reinstate: { title: 'Lever la suspension ?', label: 'Lever', prompt: 'Note interne (facultative)' },
    }[action];

    // `prompt` et non `ask` : la modale résout le texte saisi, et c'est ce texte qui part au
    // coach sur un renvoi. Annuler résout `null`, qu'on distingue d'une note laissée vide.
    const note = await this.confirm.prompt({
      title: wording.title,
      message: `Fiche de ${p.coachName}.`,
      confirmLabel: wording.label,
      danger: action === 'suspend',
      promptLabel: wording.prompt,
    });
    if (note === null) {
      return;
    }

    this.busy.set(p.id);
    this.http.post<AdminCoachProfile>(`${this.base}/${p.id}/${action}`,
      { note: note.trim() || null }).subscribe({
      next: () => {
        this.busy.set(null);
        this.toast.success('Décision enregistrée');
        this.load();
      },
      error: (err) => {
        this.busy.set(null);
        this.toast.error(err?.error?.message ?? "La décision n'a pas pu être enregistrée");
      },
    });
  }
}
