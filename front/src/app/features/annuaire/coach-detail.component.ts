import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Meta, Title } from '@angular/platform-browser';
import { AuthService } from '../../core/services/auth.service';
import { CoachingRequestService } from '../../core/services/coaching-request.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { ToastService } from '../../core/services/toast.service';
import { CoachDetail, CoachDirectoryService, REPORT_REASONS } from '../../core/services/coach-directory.service';
import { CoachOffer } from '../../core/services/coach-profile.service';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { LogoComponent } from '../../shared/components/logo/logo.component';

/**
 * La fiche publique d'un coach.
 *
 * <p>C'est la page sur laquelle un athlète décide de confier son entraînement à quelqu'un. Elle
 * doit donc dire deux choses sans détour : ce que ce coach propose et à quel prix, et ce que la
 * plateforme <b>ne garantit pas</b>. Les diplômes sont déclaratifs, et la page l'écrit à côté
 * d'eux plutôt que dans des conditions générales que personne n'ouvre.</p>
 *
 * <p>Deux gestes y vivent, et leur poids visuel n'est pas le même. Demander un coaching est
 * l'action de la page ; signaler la fiche est un recours, discret et en bas — un bouton
 * « signaler » mis en évidence à côté d'un nom jette un soupçon sur tous ceux qui n'ont rien
 * fait.</p>
 */
@Component({
  selector: 'app-coach-detail',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, IconComponent, LogoComponent],
  template: `
    <div class="cd">
      <header class="cd__head">
        <a routerLink="/" class="cd__brand" aria-label="Accueil">
          <app-logo [size]="36" [showText]="true" />
        </a>
        <a routerLink="/coachs" class="btn btn-ghost btn-sm">
          <app-icon name="arrow-left" [size]="16" /> Tous les coachs
        </a>
      </header>

      @if (coach(); as c) {
        <article>
          <section class="card cd__hero">
            <div class="cd__avatar">
              @if (photo(); as src) {
                <img [src]="src" [alt]="'Photo de ' + c.name" />
              } @else {
                <app-icon name="user" [size]="40" />
              }
            </div>
            <div class="cd__ident">
              <h1 class="display-sm">{{ c.name }}</h1>
              @if (c.headline) { <p class="cd__headline">{{ c.headline }}</p> }
              <p class="cd__meta">
                @if (c.city) { {{ c.city }} }
                @if (c.city && (c.remote || c.inPerson)) { · }
                @if (c.remote && c.inPerson) { à distance et en présentiel }
                @else if (c.remote) { à distance }
                @else if (c.inPerson) { en présentiel }
                @if (c.experienceYears) { · {{ c.experienceYears }} ans d'expérience }
              </p>
              @if (!c.acceptingAthletes) {
                <p class="badge badge-neutral">Ne prend plus de nouveaux athlètes</p>
              }

              <!-- Les signaux factuels, qui tiennent lieu d'avis tant qu'il n'y en a pas. Mesurés,
                   non déclarés, et absents tant que l'échantillon ne les fonde pas : mieux vaut ne
                   rien dire qu'annoncer « très réactif » sur une seule demande. -->
              @if (c.medianResponseHours !== null || c.responseRatePercent !== null) {
                <p class="cd__signals">
                  @if (c.medianResponseHours !== null) {
                    <span>Répond en {{ responseDelay(c.medianResponseHours) }}</span>
                  }
                  @if (c.responseRatePercent !== null) {
                    <span>{{ c.responseRatePercent }} % de demandes traitées</span>
                  }
                </p>
              }
            </div>
          </section>

          @if (c.specialtyLabels.length) {
            <section class="card cd__block">
              <h2 class="cd__title">Spécialités</h2>
              <p class="cd__tags">
                @for (s of c.specialtyLabels; track s) {
                  <span class="badge badge-neutral">{{ s }}</span>
                }
              </p>
            </section>
          }

          @if (c.bio) {
            <section class="card cd__block">
              <h2 class="cd__title">Présentation</h2>
              <p class="cd__bio">{{ c.bio }}</p>
            </section>
          }

          @if (c.offers.length) {
            <section class="card cd__block">
              <h2 class="cd__title">Formules</h2>
              <ul class="cd__offers">
                @for (o of c.offers; track o.id) {
                  <li class="cd__offer">
                    <div>
                      <strong>{{ o.name }}</strong>
                      @if (o.description) { <p class="field-hint">{{ o.description }}</p> }
                    </div>
                    <span class="cd__price">{{ price(o) }}</span>
                  </li>
                }
              </ul>
              <p class="field-hint">
                Le paiement se règle directement avec le coach : aucune transaction ne passe par
                la plateforme.
              </p>
            </section>
          }

          @if (c.certifications.length) {
            <section class="card cd__block">
              <h2 class="cd__title">Diplômes et certifications</h2>
              <ul class="cd__certs">
                @for (cert of c.certifications; track cert.id) {
                  <li>
                    <strong>{{ cert.label }}</strong>
                    @if (cert.organisation) { — {{ cert.organisation }} }
                    @if (cert.obtainedYear) { ({{ cert.obtainedYear }}) }
                  </li>
                }
              </ul>
              <!-- Écrit à côté des diplômes, pas dans des conditions générales : c'est ici que
                   l'athlète se fait une opinion, c'est ici que la réserve doit se lire. -->
              <p class="field-hint cd__declared">
                <app-icon name="info" [size]="14" />
                Ces diplômes sont <strong>déclarés par le coach</strong>. La plateforme ne les a pas
                vérifiés auprès des organismes qui les délivrent.
              </p>
            </section>
          }

          <section class="card cd__cta">
            <div>
              <strong>Demander à être coaché</strong>
              <p class="field-hint">
                @if (!c.acceptingAthletes) {
                  {{ c.name }} ne prend pas de nouveaux athlètes en ce moment.
                } @else {
                  Vous envoyez une demande ; {{ c.name }} l'accepte ou la refuse. Rien n'est engagé
                  tant qu'il n'a pas répondu.
                }
              </p>
            </div>
            <button type="button" class="btn btn-primary"
                    [disabled]="!c.acceptingAthletes || sending()"
                    (click)="requestCoaching(c)">
              {{ sending() ? 'Envoi…' : 'Demander à être coaché' }}
            </button>
          </section>

          <!-- Discret, et en bas : c'est un recours, pas une invitation. Un bouton « signaler »
               mis en évidence à côté du nom d'un coach jette un soupçon sur tous ceux qui n'ont
               rien fait. -->
          <details class="cd__report" [open]="reportOpen()">
            <summary (click)="reportOpen.set(!reportOpen())">
              Signaler cette fiche
            </summary>
            @if (reported()) {
              <p class="field-hint cd__reported">
                <app-icon name="info" [size]="14" />
                Signalement transmis. Il sera lu par l'équipe ; vous ne recevrez pas de réponse
                individuelle.
              </p>
            } @else {
              <p class="field-hint">
                Les diplômes affichés ici sont déclarés par le coach et ne sont pas vérifiés par la
                plateforme. Si quelque chose vous paraît inexact ou déplacé, dites-le.
              </p>
              <div class="form-group">
                <label for="report-reason">Motif</label>
                <select id="report-reason" class="form-control" [value]="reportReason()"
                        (change)="reportReason.set($any($event.target).value)">
                  @for (r of reasons; track r.value) {
                    <option [value]="r.value">{{ r.label }}</option>
                  }
                </select>
              </div>
              <div class="form-group">
                <label for="report-details">Ce que vous avez constaté</label>
                <textarea id="report-details" class="form-control" rows="3" [value]="reportDetails()"
                          (input)="reportDetails.set($any($event.target).value)"
                          [placeholder]="detailsPlaceholder"></textarea>
              </div>
              <button type="button" class="btn btn-secondary btn-sm"
                      [disabled]="reporting() || reportDetails().trim().length < 20"
                      (click)="submitReport(c)">
                {{ reporting() ? 'Envoi…' : 'Envoyer le signalement' }}
              </button>
            }
          </details>
        </article>
      } @else if (loading()) {
        <p class="cd__loading">Chargement…</p>
      } @else {
        <div class="card cd__empty">
          <app-icon name="search" [size]="28" />
          <div>
            <strong>Cette fiche n'existe pas ou n'est plus publiée.</strong>
            <p class="field-hint">Elle a peut-être été retirée par son coach.</p>
          </div>
          <a routerLink="/coachs" class="btn btn-secondary btn-sm">Voir les autres coachs</a>
        </div>
      }
    </div>
  `,
  styles: [`
    .cd { max-width: 760px; margin: 0 auto; padding: var(--sp-5) var(--sp-4) var(--sp-12); }
    .cd__head { display: flex; align-items: center; justify-content: space-between; margin-bottom: var(--sp-6); }
    .cd__brand { text-decoration: none; }
    .cd__loading { color: var(--ink-3); padding: var(--sp-6) 0; }
    .cd__hero { display: flex; gap: var(--sp-5); padding: var(--sp-5); margin-bottom: var(--sp-4); align-items: center; }
    @media (max-width: 560px) { .cd__hero { flex-direction: column; align-items: flex-start; } }
    .cd__avatar { flex: 0 0 auto; width: 112px; height: 112px; border-radius: var(--radius-lg); background: var(--paper-sunk); display: flex; align-items: center; justify-content: center; overflow: hidden; color: var(--ink-4); }
    .cd__avatar img { width: 100%; height: 100%; object-fit: cover; }
    .cd__ident { min-width: 0; }
    .cd__ident h1 { margin: 0 0 var(--sp-1); }
    .cd__headline { margin: 0 0 var(--sp-2); color: var(--ink-2); font-style: italic; }
    .cd__meta { margin: 0 0 var(--sp-2); color: var(--ink-3); font-size: var(--text-sm); }
    .cd__signals { display: flex; flex-wrap: wrap; gap: var(--sp-1) var(--sp-4); margin: var(--sp-2) 0 0; font-size: var(--text-sm); color: var(--ink-2); }
    .cd__block { padding: var(--sp-5); margin-bottom: var(--sp-4); }
    .cd__title { font-size: var(--text-lg); margin: 0 0 var(--sp-3); }
    .cd__bio { margin: 0; white-space: pre-wrap; color: var(--ink-2); }
    .cd__tags { display: flex; flex-wrap: wrap; gap: var(--sp-2); margin: 0; }
    .cd__offers { list-style: none; margin: 0 0 var(--sp-3); padding: 0; display: flex; flex-direction: column; gap: var(--sp-2); }
    .cd__offer { display: flex; align-items: center; justify-content: space-between; gap: var(--sp-3); padding: var(--sp-3); border: 1px solid var(--hairline); border-radius: var(--radius); }
    .cd__offer p { margin: 2px 0 0; }
    .cd__price { font-family: var(--font-mono); font-size: var(--text-sm); white-space: nowrap; }
    .cd__certs { margin: 0 0 var(--sp-3); padding-left: var(--sp-5); color: var(--ink-2); }
    .cd__declared { display: flex; align-items: flex-start; gap: var(--sp-2); }
    .cd__cta { display: flex; align-items: center; justify-content: space-between; gap: var(--sp-4); padding: var(--sp-5); flex-wrap: wrap; }
    .cd__cta p { margin: var(--sp-1) 0 0; max-width: 48ch; }
    .cd__report { margin-top: var(--sp-6); padding: var(--sp-4); border: 1px solid var(--hairline); border-radius: var(--radius); }
    .cd__report summary { cursor: pointer; color: var(--ink-3); font-size: var(--text-sm); }
    .cd__report .form-group { margin-top: var(--sp-3); }
    .cd__reported { display: flex; align-items: flex-start; gap: var(--sp-2); margin-top: var(--sp-3); }
    .cd__empty { display: flex; align-items: center; gap: var(--sp-4); padding: var(--sp-5); flex-wrap: wrap; }
    .cd__empty > div { flex: 1; min-width: 220px; }
  `],
})
export class CoachDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly service = inject(CoachDirectoryService);
  private readonly requests = inject(CoachingRequestService);
  private readonly auth = inject(AuthService);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);
  private readonly title = inject(Title);
  private readonly meta = inject(Meta);

  readonly coach = signal<CoachDetail | null>(null);
  readonly loading = signal(true);
  readonly sending = signal(false);

  readonly reasons = REPORT_REASONS;
  /** Hors du gabarit : une apostrophe dans une interpolation Angular casse la compilation. */
  readonly detailsPlaceholder =
    'Quelques phrases suffisent, mais elles sont nécessaires : sans détail, un signalement ne peut pas être traité.';
  readonly reportOpen = signal(false);
  readonly reportReason = signal(REPORT_REASONS[0].value);
  readonly reportDetails = signal('');
  readonly reporting = signal(false);
  readonly reported = signal(false);

  ngOnInit(): void {
    const slug = this.route.snapshot.paramMap.get('slug');
    if (!slug) {
      this.loading.set(false);
      return;
    }
    this.service.bySlug(slug).subscribe({
      next: (c) => {
        this.coach.set(c);
        this.loading.set(false);
        this.describe(c);
      },
      error: () => this.loading.set(false),
    });
  }

  /**
   * Envoie une demande à ce coach.
   *
   * <p>Un visiteur non connecté est renvoyé vers l'inscription athlète en gardant l'adresse de la
   * fiche : le faire s'inscrire puis le laisser chercher à nouveau le coach qu'il venait de
   * choisir est le meilleur moyen de le perdre en route.</p>
   */
  async requestCoaching(c: CoachDetail): Promise<void> {
    if (!this.auth.isAuthenticated()) {
      this.router.navigate(['/inscription-athlete'], { queryParams: { coach: c.slug } });
      return;
    }
    if (this.auth.currentUser()?.role !== 'ATHLETE') {
      this.toast.error('Seul un compte athlète peut demander un coaching.');
      return;
    }

    const message = await this.confirm.prompt({
      title: `Demander à ${c.name} de vous coacher`,
      message: 'Dites-lui votre objectif et votre contexte : c\'est sur ce mot qu\'il décidera.',
      confirmLabel: 'Envoyer ma demande',
      promptLabel: 'Votre message',
    });
    if (message === null || message.trim().length < 20) {
      if (message !== null) {
        this.toast.error('Quelques lignes au moins : une demande sans contexte ne peut être que refusée.');
      }
      return;
    }

    this.sending.set(true);
    this.requests.submit(c.slug, message.trim()).subscribe({
      next: () => {
        this.sending.set(false);
        this.toast.success('Demande envoyée — vous serez prévenu de sa réponse');
        this.router.navigate(['/athlete/demandes']);
      },
      error: (err) => {
        this.sending.set(false);
        this.toast.error(err?.error?.message ?? "La demande n'a pas pu être envoyée");
      },
    });
  }

  /**
   * Renseigne titre, description et Open Graph de la page.
   *
   * <p><b>Ce que ça fait, et ce que ça ne fait pas.</b> Le produit est une application Angular sans
   * rendu serveur : ces balises sont posées <em>après</em> le chargement du script. Les robots
   * modernes exécutent le JavaScript et les liront ; les aperçus de partage — messageries, Slack,
   * là où circulent réellement les recommandations de coach — ne l'exécutent pas et verront la
   * page d'accueil générique.</p>
   *
   * <p>Autrement dit : ceci est une moitié. L'autre est un pré-rendu des fiches à la compilation,
   * qui relève de l'infrastructure et non d'un composant. Poser ces balises coûte peu et servira
   * dès qu'un pré-rendu existera.</p>
   */
  private describe(c: CoachDetail): void {
    const specialties = c.specialtyLabels.slice(0, 2).join(', ');
    const title = `${c.name} — coach ${specialties || 'course à pied'}`;
    const description = c.headline
      ?? `${c.name} accompagne des coureurs${c.city ? ` à ${c.city}` : ''}. Ses spécialités, ses `
        + 'tarifs, et comment lui demander de vous coacher.';
    this.title.setTitle(title);
    this.meta.updateTag({ name: 'description', content: description });
    this.meta.updateTag({ property: 'og:description', content: description });
    this.meta.updateTag({ property: 'og:title', content: title });
    this.meta.updateTag({ property: 'og:type', content: 'profile' });
    const photo = this.photo();
    if (photo) {
      this.meta.updateTag({ property: 'og:image', content: photo });
    }
  }

  /**
   * Envoie un signalement.
   *
   * <p>Aucune connexion n'est demandée : celui qui reconnaît un diplôme faux n'a pas de raison
   * d'avoir un compte, et l'exiger reviendrait à n'écouter que les clients.</p>
   *
   * <p>Le formulaire disparaît après l'envoi, et le message ne promet pas de réponse : l'arbitrage
   * d'un signalement porte sur une personne nommée, et ce qu'il établit n'appartient pas à celui
   * qui a signalé.</p>
   */
  submitReport(c: CoachDetail): void {
    const details = this.reportDetails().trim();
    if (details.length < 20) {
      return;
    }
    this.reporting.set(true);
    this.service.report(c.slug, this.reportReason(), details).subscribe({
      next: () => {
        this.reporting.set(false);
        this.reported.set(true);
        this.reportDetails.set('');
      },
      error: (err) => {
        this.reporting.set(false);
        this.toast.error(err?.error?.message ?? "Le signalement n'a pas pu être envoyé");
      },
    });
  }

  photo(): string | null {
    return this.service.photoSrc(this.coach()?.photoUrl ?? null);
  }

  /**
   * Le délai de réponse, dit comme on le dirait à voix haute.
   *
   * <p>« Répond en 36 h » se comprend moins vite que « en moins de deux jours ». Et la formulation
   * reste prudente — c'est une médiane observée, pas un engagement du coach.</p>
   */
  responseDelay(hours: number): string {
    if (hours <= 1) {
      return "moins d'une heure";
    }
    if (hours < 24) {
      return `moins de ${hours} h`;
    }
    const days = Math.ceil(hours / 24);
    return days === 1 ? "moins d'un jour" : `moins de ${days} jours`;
  }

  price(o: CoachOffer): string {
    return `${(o.amountCents / 100).toLocaleString('fr-FR')} € ${o.suffix}`;
  }
}
