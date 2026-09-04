import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { CoachDetail, CoachDirectoryService } from '../../core/services/coach-directory.service';
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
 * <p>Le bouton de demande n'existe pas encore : la mise en relation est le lot suivant. Plutôt
 * qu'un bouton qui ne fait rien, la page annonce ce qui arrive — un écran honnête vaut mieux
 * qu'une promesse muette.</p>
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
                La mise en relation ouvre très bientôt. Vous pourrez alors envoyer une demande à
                {{ c.name }}, qui l'accepte ou la refuse.
              </p>
            </div>
            <button type="button" class="btn btn-primary" disabled>Bientôt disponible</button>
          </section>
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
    .cd__empty { display: flex; align-items: center; gap: var(--sp-4); padding: var(--sp-5); flex-wrap: wrap; }
    .cd__empty > div { flex: 1; min-width: 220px; }
  `],
})
export class CoachDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly service = inject(CoachDirectoryService);

  readonly coach = signal<CoachDetail | null>(null);
  readonly loading = signal(true);

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
      },
      error: () => this.loading.set(false),
    });
  }

  photo(): string | null {
    return this.service.photoSrc(this.coach()?.photoUrl ?? null);
  }

  price(o: CoachOffer): string {
    return `${(o.amountCents / 100).toLocaleString('fr-FR')} € ${o.suffix}`;
  }
}
