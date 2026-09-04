import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CoachingRequest, CoachingRequestService } from '../../core/services/coaching-request.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { ToastService } from '../../core/services/toast.service';
import { IconComponent } from '../../shared/components/icon/icon.component';

/**
 * Les demandes reçues, côté coach.
 *
 * <h2>Ce que l'écran doit rendre facile, et ce qu'il doit rendre difficile</h2>
 *
 * <p>Facile : décider. Chaque ligne porte l'objectif de l'athlète en toutes lettres, son niveau,
 * sa ville, son âge et la formule qu'il vise — de quoi trancher sans rien ouvrir d'autre.</p>
 *
 * <p>Difficile : accepter par inadvertance. Accepter crée une fiche, une relation et un espace
 * d'entraînement chez quelqu'un qui attend ; c'est le geste le plus engageant du produit, et il
 * passe donc par une confirmation nommée.</p>
 *
 * <p>L'écran ne montre <b>aucune coordonnée</b> avant acceptation, parce que le serveur n'en envoie
 * aucune : si une demande livrait l'adresse, il suffirait d'en recevoir pour se constituer un
 * fichier, et refuser n'aurait plus d'effet.</p>
 */
@Component({
  selector: 'app-coach-requests',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, RouterLink, IconComponent],
  template: `
    <header class="page-head">
      <div>
        <h1 class="display-sm">Demandes de coaching</h1>
        <p class="field-hint">
          Des athlètes vous ont trouvé dans l'annuaire. Vous acceptez, vous refusez, ou vous posez
          une question avant de décider.
        </p>
      </div>
      <a routerLink="/app/vitrine" class="btn btn-ghost btn-sm">Ma fiche publique</a>
    </header>

    @if (loading()) {
      <p class="cr-loading">Chargement…</p>
    } @else if (!requests().length) {
      <div class="card cr-empty">
        <app-icon name="inbox" [size]="28" />
        <div>
          <strong>Aucune demande pour l'instant.</strong>
          <p class="field-hint">
            Les demandes arrivent depuis votre fiche publique : vérifiez qu'elle est publiée et
            qu'elle dit ce que vous savez faire.
          </p>
        </div>
      </div>
    } @else {
      @if (pending().length) {
        <h2 class="cr-section">À traiter ({{ pending().length }})</h2>
        <ul class="cr-list">
          @for (r of pending(); track r.id) {
            <li class="card cr">
              <div class="cr__head">
                <strong class="cr__name">{{ r.athleteName }}</strong>
                @if (r.athleteAge !== null) { <span class="cr__age">{{ r.athleteAge }} ans</span> }
                @if (r.athleteCity) { <span class="cr__age">{{ r.athleteCity }}</span> }
                <span class="cr__when">reçue le {{ r.createdAt | date: 'dd/MM/yyyy' }}</span>
              </div>

              @if (r.athleteGoal) {
                <p class="cr__goal"><strong>Son objectif :</strong> {{ r.athleteGoal }}</p>
              }
              @if (r.message) { <p class="cr__msg">« {{ r.message }} »</p> }
              @if (r.offerLabel) {
                <p class="cr__offer">
                  Formule visée : {{ r.offerLabel }}
                  @if (r.offerAmountCents !== null) { — {{ r.offerAmountCents / 100 }} € }
                </p>
              }

              @if (r.coachQuestion) {
                <div class="cr__q">
                  <strong>Votre question :</strong> {{ r.coachQuestion }}
                  @if (r.athleteAnswer) {
                    <p><strong>Sa réponse :</strong> {{ r.athleteAnswer }}</p>
                  } @else {
                    <p class="field-hint">En attente de sa réponse.</p>
                  }
                </div>
              }

              <div class="cr__actions">
                @if (!r.coachQuestion) {
                  <button type="button" class="btn btn-ghost btn-sm" [disabled]="busy() === r.id"
                          (click)="ask(r)">Poser une question</button>
                }
                <button type="button" class="btn btn-ghost btn-sm" [disabled]="busy() === r.id"
                        (click)="decline(r)">Refuser</button>
                <button type="button" class="btn btn-primary btn-sm" [disabled]="busy() === r.id"
                        (click)="accept(r)">Accepter</button>
              </div>
            </li>
          }
        </ul>
      }

      @if (settled().length) {
        <h2 class="cr-section">Historique</h2>
        <ul class="cr-list">
          @for (r of settled(); track r.id) {
            <li class="card cr cr--past">
              <div class="cr__head">
                <strong class="cr__name">{{ r.athleteName }}</strong>
                <span class="badge" [class]="badge(r.status)">{{ r.statusLabel }}</span>
                @if (r.decidedAt) {
                  <span class="cr__when">le {{ r.decidedAt | date: 'dd/MM/yyyy' }}</span>
                }
              </div>
              @if (r.declineReason) { <p class="cr__msg">Motif : {{ r.declineReason }}</p> }
            </li>
          }
        </ul>
      }
    }
  `,
  styles: [`
    .page-head { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--sp-4); flex-wrap: wrap; margin-bottom: var(--sp-5); }
    .cr-loading { color: var(--ink-3); padding: var(--sp-6) 0; }
    .cr-empty { display: flex; align-items: center; gap: var(--sp-4); padding: var(--sp-5); flex-wrap: wrap; }
    .cr-empty > div { flex: 1; min-width: 220px; }
    .cr-empty p { margin: var(--sp-1) 0 0; }
    .cr-section { font-size: var(--text-lg); margin: var(--sp-6) 0 var(--sp-3); }
    .cr-section:first-of-type { margin-top: 0; }
    .cr-list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: var(--sp-3); }
    .cr { padding: var(--sp-4); }
    .cr--past { opacity: 0.75; }
    .cr__head { display: flex; align-items: center; gap: var(--sp-3); flex-wrap: wrap; margin-bottom: var(--sp-2); }
    .cr__name { font-size: var(--text-lg); color: var(--ink); }
    .cr__age { color: var(--ink-3); font-size: var(--text-sm); }
    .cr__when { color: var(--ink-3); font-size: var(--text-sm); margin-left: auto; }
    .cr__goal { margin: 0 0 var(--sp-2); color: var(--ink); }
    .cr__msg { margin: 0 0 var(--sp-2); color: var(--ink-2); font-size: var(--text-sm); white-space: pre-wrap; }
    .cr__offer { margin: 0 0 var(--sp-2); font-family: var(--font-mono); font-size: var(--text-sm); color: var(--ink-2); }
    .cr__q { padding: var(--sp-3); border: 1px solid var(--hairline); border-radius: var(--radius); margin-bottom: var(--sp-3); font-size: var(--text-sm); }
    .cr__q p { margin: var(--sp-2) 0 0; }
    .cr__actions { display: flex; justify-content: flex-end; gap: var(--sp-2); padding-top: var(--sp-3); border-top: 1px solid var(--hairline); flex-wrap: wrap; }
  `],
})
export class CoachRequestsComponent implements OnInit {
  private readonly service = inject(CoachingRequestService);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);

  readonly requests = signal<CoachingRequest[]>([]);
  readonly loading = signal(true);
  readonly busy = signal<string | null>(null);

  readonly pending = computed(() => this.requests().filter((r) => r.status === 'PENDING'));
  readonly settled = computed(() => this.requests().filter((r) => r.status !== 'PENDING'));

  ngOnInit(): void {
    this.load();
  }

  badge(status: CoachingRequest['status']): string {
    return this.service.badge(status);
  }

  /**
   * Accepter crée une fiche, une relation et un espace d'entraînement chez quelqu'un qui attend :
   * c'est le geste le plus engageant du produit, et il ne doit pas partir d'un clic distrait.
   */
  async accept(r: CoachingRequest): Promise<void> {
    const ok = await this.confirm.ask({
      title: `Accepter ${r.athleteName} ?`,
      message: 'Sa fiche et son espace d\'entraînement seront créés, et vous pourrez commencer à '
        + 'lui prescrire des séances. Il en sera prévenu.',
      confirmLabel: 'Accepter',
    });
    if (!ok) {
      return;
    }
    this.act(r, this.service.accept(r.id), `${r.athleteName} fait maintenant partie de vos athlètes`);
  }

  async decline(r: CoachingRequest): Promise<void> {
    // Le motif part à l'athlète : sans lui, il redemande la semaine suivante sans savoir ce qui
    // n'allait pas. Facultatif, parce qu'on ne doit pas non plus forcer à se justifier.
    const note = await this.confirm.prompt({
      title: `Refuser la demande de ${r.athleteName} ?`,
      message: 'Votre motif lui sera transmis. Il n\'est jamais publié.',
      confirmLabel: 'Refuser',
      promptLabel: 'Motif (facultatif)',
    });
    if (note === null) {
      return;
    }
    this.act(r, this.service.decline(r.id, note.trim() || null), 'Réponse envoyée');
  }

  async ask(r: CoachingRequest): Promise<void> {
    const note = await this.confirm.prompt({
      title: `Une question à ${r.athleteName}`,
      message: 'Vous pouvez poser une question avant de décider. Une seule : la suite se passera '
        + 'dans la messagerie, si vous acceptez.',
      confirmLabel: 'Envoyer',
      promptLabel: 'Votre question',
    });
    if (note === null || !note.trim()) {
      return;
    }
    this.act(r, this.service.ask(r.id, note.trim()), 'Question envoyée');
  }

  private act(r: CoachingRequest, call: import('rxjs').Observable<CoachingRequest>, success: string): void {
    this.busy.set(r.id);
    call.subscribe({
      next: () => {
        this.busy.set(null);
        this.toast.success(success);
        this.load();
      },
      error: (err) => {
        this.busy.set(null);
        this.toast.error(err?.error?.message ?? "L'action n'a pas pu être enregistrée");
      },
    });
  }

  private load(): void {
    this.service.received().subscribe({
      next: (list) => {
        this.requests.set(list);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }
}
