import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { InviteLinkService, InviteOutcome, InvitePreview } from '../../core/services/invite-link.service';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { LogoComponent } from '../../shared/components/logo/logo.component';

/**
 * « Rejoindre » : la page qu'ouvre un lien d'invitation reçu par SMS, WhatsApp ou e-mail.
 *
 * <p>Elle dit qui invite et ce qui se passera, puis propose le seul geste utile selon la
 * situation : créer son compte, se connecter, ou rejoindre. Un compte coach, un lien expiré ou un
 * athlète déjà suivi ailleurs reçoivent une explication plutôt qu'une erreur.</p>
 */
@Component({
  selector: 'app-join-invite',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, FormsModule, IconComponent, LogoComponent],
  template: `
    <main class="ji">
      <div class="card ji__card">
        <app-logo [size]="40" [showText]="false" />
        @if (loading()) {
          <p class="field-hint">Chargement de l'invitation…</p>
        } @else if (!preview()) {
          <h1 class="display-sm">Invitation introuvable</h1>
          <p class="field-hint">Ce lien n'existe pas ou a été mal copié. Demandez-en un nouveau à votre coach.</p>
          <a class="btn btn-ghost" routerLink="/coachs">Parcourir les coachs</a>
        } @else {
          @if (preview(); as p) {
            <div class="ji__who">
              @if (photo(); as src) {
                <img class="ji__photo" [src]="src" alt="" />
              } @else {
                <span class="ji__initials" aria-hidden="true">{{ initials() }}</span>
              }
              <div>
                <h1 class="display-sm">{{ title() }}</h1>
                <p class="ji__sub">{{ subtitle() }}</p>
              </div>
            </div>

            @if (!p.valid) {
              <p class="ji__warn"><app-icon name="info" [size]="16" /> {{ p.reason }}</p>
              <p class="field-hint">Demandez un nouveau lien à {{ p.coachName }}.</p>
            } @else if (outcome(); as o) {
              @switch (o) {
                @case ('JOINED') {
                  <p class="ji__ok"><app-icon name="check" [size]="16" /> C'est fait : {{ p.coachName }} vous suit désormais.</p>
                  <a class="btn btn-primary btn-lg" routerLink="/athlete/calendar">Ouvrir mon calendrier</a>
                }
                @case ('REQUESTED') {
                  <p class="ji__ok"><app-icon name="check" [size]="16" /> Demande envoyée à {{ p.coachName }}. Vous serez prévenu de sa réponse.</p>
                  <a class="btn btn-primary btn-lg" routerLink="/athlete/demandes">Suivre ma demande</a>
                }
                @case ('VERIFY_EMAIL') {
                  <p class="ji__ok"><app-icon name="mail" [size]="16" /> Dernière étape : confirmez votre adresse e-mail.</p>
                  <p class="field-hint">Cliquez sur le lien que nous venons de vous envoyer. L'invitation sera prise en compte automatiquement.</p>
                  <a class="btn btn-ghost" routerLink="/athlete/today">Découvrir mon espace en attendant</a>
                }
              }
            } @else {
              <ul class="ji__what">
                <li><app-icon name="calendar-days" [size]="16" /> Vos séances arrivent dans votre calendrier, et sur votre montre.</li>
                <li><app-icon name="message-square" [size]="16" /> Vous échangez avec {{ p.coachName }} au même endroit.</li>
                <li><app-icon name="shield-check" [size]="16" /> Vous pouvez mettre fin au suivi à tout moment depuis votre profil.</li>
              </ul>

              @if (!auth.isAuthenticated()) {
                <a class="btn btn-primary btn-lg" [routerLink]="['/inscription-athlete']" [queryParams]="{ invite: token() }">
                  Créer mon compte
                </a>
                <a class="btn btn-ghost" [routerLink]="['/login']" [queryParams]="{ next: '/rejoindre/' + token() }">
                  J'ai déjà un compte
                </a>
              } @else if (!isAthlete()) {
                <p class="ji__warn"><app-icon name="info" [size]="16" /> Vous êtes connecté avec un compte coach. Ouvrez ce lien depuis le compte athlète à inviter.</p>
              } @else {
                @if (!p.directEntry) {
                  <label class="form-label" for="ji-msg">Un mot pour {{ p.coachName }} (facultatif)</label>
                  <textarea id="ji-msg" class="form-control" rows="3" maxlength="2000" [(ngModel)]="message"
                            placeholder="Votre objectif, votre niveau, vos disponibilités…"></textarea>
                }
                @if (error(); as e) { <p class="ji__warn"><app-icon name="info" [size]="16" /> {{ e }}</p> }
                <button type="button" class="btn btn-primary btn-lg" [disabled]="busy()" (click)="join()">
                  {{ busy() ? 'Un instant…' : (p.directEntry ? 'Rejoindre ' + target() : 'Demander à rejoindre') }}
                </button>
              }
              @if (p.coachSlug) {
                <a class="ji__profile" [routerLink]="['/coachs', p.coachSlug]">Voir la fiche de {{ p.coachName }}</a>
              }
            }
          }
        }
      </div>
    </main>
  `,
  styles: [`
    .ji { min-height: 100dvh; display: flex; align-items: center; justify-content: center;
      padding: var(--sp-4); background: var(--canvas); }
    .ji__card { width: 100%; max-width: 460px; padding: var(--sp-6) var(--sp-5);
      display: flex; flex-direction: column; gap: var(--sp-3); }
    .ji__who { display: flex; gap: var(--sp-3); align-items: center; margin-top: var(--sp-2); }
    .ji__who h1 { margin: 0; font-size: var(--text-xl); }
    .ji__sub { margin: 2px 0 0; color: var(--ink-3); }
    .ji__photo, .ji__initials { flex: 0 0 64px; width: 64px; height: 64px; border-radius: 50%; object-fit: cover; }
    .ji__initials { display: inline-flex; align-items: center; justify-content: center; font-weight: 700;
      background: var(--primary); color: #fff; font-size: var(--text-lg); }
    .ji__what { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: var(--sp-2); }
    .ji__what li { display: flex; gap: var(--sp-2); align-items: flex-start; color: var(--ink-2); }
    .ji__warn, .ji__ok { display: flex; gap: var(--sp-2); align-items: flex-start; margin: 0;
      padding: var(--sp-3); border-radius: var(--radius-md, 12px); }
    .ji__warn { background: var(--warning-bg); color: var(--warning-text); }
    .ji__ok { background: var(--success-bg, var(--surface-2)); color: var(--success-text, var(--ink)); }
    .ji__profile { text-align: center; color: var(--primary); }
    .btn-lg { width: 100%; justify-content: center; }
  `],
})
export class JoinInviteComponent implements OnInit {
  readonly auth = inject(AuthService);
  private readonly invites = inject(InviteLinkService);

  readonly token = input.required<string>();

  readonly loading = signal(true);
  readonly busy = signal(false);
  readonly preview = signal<InvitePreview | null>(null);
  readonly outcome = signal<InviteOutcome | null>(null);
  readonly error = signal<string | null>(null);
  message = '';

  readonly isAthlete = computed(() => this.auth.currentUser()?.role === 'ATHLETE');
  readonly photo = computed(() => this.invites.photoSrc(this.preview()?.coachPhotoUrl ?? null));
  readonly target = computed(() => {
    const p = this.preview();
    if (!p) return '';
    return p.clubMembership && !p.independentCoach ? p.clubName : p.coachName;
  });
  readonly title = computed(() => {
    const p = this.preview();
    if (!p) return '';
    return p.clubMembership && !p.independentCoach
      ? `${p.clubName} vous invite`
      : `${p.coachName} vous invite`;
  });
  readonly subtitle = computed(() => {
    const p = this.preview();
    if (!p) return '';
    const parts = [p.clubMembership && !p.independentCoach ? `avec ${p.coachName}` : (p.independentCoach ? 'Coach indépendant' : p.clubName)];
    if (p.groupName) parts.push(`groupe ${p.groupName}`);
    return parts.join(' · ');
  });
  readonly initials = computed(() =>
    (this.preview()?.coachName ?? '?').split(/\s+/).map((w) => w[0] ?? '').join('').slice(0, 2).toUpperCase());

  ngOnInit(): void {
    this.invites.preview(this.token()).subscribe({
      next: (p) => { this.preview.set(p); this.loading.set(false); },
      error: () => { this.preview.set(null); this.loading.set(false); },
    });
  }

  join(): void {
    this.busy.set(true);
    this.error.set(null);
    this.invites.join(this.token(), this.message.trim() || null).subscribe({
      next: (res) => {
        this.busy.set(false);
        this.outcome.set(res.outcome);
        if (res.outcome === 'JOINED') {
          // La fiche vient de changer : la session porte l'ancienne. On la rafraîchit pour que le
          // portail s'ouvre sur l'espace du coach.
          this.auth.refresh().subscribe({ error: () => undefined });
        }
      },
      error: (err) => {
        this.busy.set(false);
        this.error.set(err?.error?.message ?? 'Impossible de rejoindre pour le moment.');
      },
    });
  }
}
