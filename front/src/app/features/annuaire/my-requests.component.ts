import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ConfirmService } from '../../core/services/confirm.service';
import { CoachingRequest, CoachingRequestService } from '../../core/services/coaching-request.service';
import { ToastService } from '../../core/services/toast.service';
import { IconComponent } from '../../shared/components/icon/icon.component';

/**
 * « Mes demandes », côté athlète.
 *
 * <p>Un athlète qui a envoyé une demande attend. Cet écran existe pour que l'attente soit lisible :
 * où en est chaque demande, jusqu'à quand elle court, et ce qu'on attend de lui — car il peut y
 * avoir une question à laquelle il n'a pas encore répondu, et c'est alors <b>lui</b> qui bloque.</p>
 *
 * <p>Les états sont distingués jusqu'au bout : « retirée » et « sans réponse » ne portent pas la
 * couleur d'un refus. Dire à quelqu'un qu'il a été refusé quand personne n'a répondu est faux, et
 * ce genre de faux décourage durablement.</p>
 */
@Component({
  selector: 'app-my-requests',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, RouterLink, IconComponent],
  template: `
    <header class="page-head">
      <div>
        <h1 class="display-sm">Mes demandes</h1>
        <p class="field-hint">Les coachs que vous avez sollicités, et où en est chaque demande.</p>
      </div>
      <a routerLink="/coachs" class="btn btn-secondary btn-sm">Trouver un coach</a>
    </header>

    @if (loading()) {
      <p class="mr-loading">Chargement…</p>
    } @else if (!requests().length) {
      <div class="card mr-empty">
        <app-icon name="search" [size]="28" />
        <div>
          <strong>Vous n'avez encore sollicité personne.</strong>
          <p class="field-hint">
            Parcourez l'annuaire et demandez au coach qui vous convient de vous suivre.
          </p>
        </div>
        <a routerLink="/coachs" class="btn btn-primary btn-sm">Voir les coachs</a>
      </div>
    } @else {
      <ul class="mr-list">
        @for (r of requests(); track r.id) {
          <li class="card mr">
            <div class="mr__head">
              <strong class="mr__coach">{{ r.coachName }}</strong>
              <span class="badge" [class]="badge(r.status)">{{ r.statusLabel }}</span>
              <span class="mr__when">envoyée le {{ r.createdAt | date: 'dd/MM/yyyy' }}</span>
            </div>

            @if (r.offerLabel) {
              <p class="mr__offer">{{ r.offerLabel }}
                @if (r.offerAmountCents !== null) { — {{ r.offerAmountCents / 100 }} € }
              </p>
            }

            @if (r.message) { <p class="mr__msg">« {{ r.message }} »</p> }

            <!-- La question du coach est mise en avant : tant qu'elle est sans réponse, c'est
                 l'athlète qui bloque, et il doit le comprendre sans avoir à le déduire. -->
            @if (r.coachQuestion) {
              <div class="mr__q">
                <strong>{{ r.coachName }} vous demande :</strong>
                <p>{{ r.coachQuestion }}</p>
                @if (r.athleteAnswer) {
                  <p class="mr__a">Votre réponse : {{ r.athleteAnswer }}</p>
                } @else if (r.status === 'PENDING') {
                  <button type="button" class="btn btn-primary btn-sm" [disabled]="busy() === r.id"
                          (click)="answer(r)">Répondre</button>
                }
              </div>
            }

            @if (r.status === 'DECLINED' && r.declineReason) {
              <p class="mr__reason">Motif : {{ r.declineReason }}</p>
            }

            @if (r.status === 'PENDING') {
              <p class="mr__foot field-hint">
                Sans réponse d'ici le {{ r.expiresAt | date: 'dd/MM/yyyy' }}, la demande expirera.
                <button type="button" class="btn btn-ghost btn-sm" [disabled]="busy() === r.id"
                        (click)="withdraw(r)">Retirer ma demande</button>
              </p>
            } @else if (r.status === 'EXPIRED') {
              <p class="mr__foot field-hint">
                Ce coach n'a pas répondu dans le délai. Ce n'est pas un refus — vous pouvez le
                solliciter à nouveau, ou en chercher un autre.
              </p>
            }
          </li>
        }
      </ul>
    }
  `,
  styles: [`
    .page-head { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--sp-4); flex-wrap: wrap; margin-bottom: var(--sp-5); }
    .mr-loading { color: var(--ink-3); padding: var(--sp-6) 0; }
    .mr-empty { display: flex; align-items: center; gap: var(--sp-4); padding: var(--sp-5); flex-wrap: wrap; }
    .mr-empty > div { flex: 1; min-width: 200px; }
    .mr-empty p { margin: var(--sp-1) 0 0; }
    .mr-list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: var(--sp-3); }
    .mr { padding: var(--sp-4); }
    .mr__head { display: flex; align-items: center; gap: var(--sp-3); flex-wrap: wrap; margin-bottom: var(--sp-2); }
    .mr__coach { font-size: var(--text-lg); color: var(--ink); }
    .mr__when { color: var(--ink-3); font-size: var(--text-sm); margin-left: auto; }
    .mr__offer { margin: 0 0 var(--sp-2); font-family: var(--font-mono); font-size: var(--text-sm); color: var(--ink-2); }
    .mr__msg { margin: 0 0 var(--sp-3); color: var(--ink-2); font-size: var(--text-sm); white-space: pre-wrap; }
    .mr__q { padding: var(--sp-3); border: 1px solid var(--primary-light); background: var(--primary-wash); border-radius: var(--radius); margin-bottom: var(--sp-3); }
    .mr__q p { margin: var(--sp-1) 0 var(--sp-2); }
    .mr__a { color: var(--ink-2); font-size: var(--text-sm); }
    .mr__reason { margin: 0 0 var(--sp-2); color: var(--ink-2); font-size: var(--text-sm); }
    .mr__foot { display: flex; align-items: center; justify-content: space-between; gap: var(--sp-3); margin: 0; padding-top: var(--sp-3); border-top: 1px solid var(--hairline); flex-wrap: wrap; }
  `],
})
export class MyRequestsComponent implements OnInit {
  private readonly service = inject(CoachingRequestService);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);

  readonly requests = signal<CoachingRequest[]>([]);
  readonly loading = signal(true);
  readonly busy = signal<string | null>(null);

  ngOnInit(): void {
    this.load();
  }

  badge(status: CoachingRequest['status']): string {
    return this.service.badge(status);
  }

  async answer(r: CoachingRequest): Promise<void> {
    const note = await this.confirm.prompt({
      title: 'Répondre au coach',
      message: r.coachQuestion ?? '',
      confirmLabel: 'Envoyer',
      promptLabel: 'Votre réponse',
    });
    if (note === null || !note.trim()) {
      return;
    }
    this.busy.set(r.id);
    this.service.answer(r.id, note.trim()).subscribe({
      next: () => {
        this.busy.set(null);
        this.load();
      },
      error: (err) => {
        this.busy.set(null);
        this.toast.error(err?.error?.message ?? "La réponse n'a pas pu être envoyée");
      },
    });
  }

  async withdraw(r: CoachingRequest): Promise<void> {
    const ok = await this.confirm.ask({
      title: 'Retirer votre demande ?',
      message: `${r.coachName} ne la verra plus. Vous pourrez en envoyer une nouvelle plus tard.`,
      confirmLabel: 'Retirer',
    });
    if (!ok) {
      return;
    }
    this.busy.set(r.id);
    this.service.withdraw(r.id).subscribe({
      next: () => {
        this.busy.set(null);
        this.load();
      },
      error: (err) => {
        this.busy.set(null);
        this.toast.error(err?.error?.message ?? "La demande n'a pas pu être retirée");
      },
    });
  }

  private load(): void {
    this.service.mine().subscribe({
      next: (list) => {
        this.requests.set(list);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }
}
