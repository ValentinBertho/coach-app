import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { Meta, Title } from '@angular/platform-browser';
import { RouterLink } from '@angular/router';
import {
  CoachDirectoryService,
  CoachFacets,
  CoachSummary,
  DirectoryFilters,
  FacetValue,
} from '../../core/services/coach-directory.service';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { LogoComponent } from '../../shared/components/logo/logo.component';

/**
 * L'annuaire des coachs, vu par un visiteur qui n'a pas de compte.
 *
 * <h2>Le problème que cet écran doit éviter</h2>
 *
 * <p>L'ouverture est prévue à dix coachs. Croisés avec discipline, spécialité, langue, ville et
 * distanciel, la plupart des combinaisons rendent zéro — et une liste vide fait conclure au
 * visiteur que la <b>plateforme</b> est vide, pas que son filtre est trop étroit. C'est la pire
 * première impression possible, et elle est irrattrapable : il ne revient pas.</p>
 *
 * <p>Deux garde-fous, et ils sont la raison d'être de la moitié de ce composant :</p>
 * <ol>
 *   <li>chaque filtre porte son <b>nombre de coachs</b> et se désactive quand il rendrait zéro —
 *       on ne propose jamais un chemin qui ne mène nulle part ;</li>
 *   <li>quand une recherche ne rend rien malgré tout (une <em>combinaison</em> peut être vide sans
 *       qu'aucun filtre ne le soit), l'écran montre les coachs qui prennent des athlètes <b>en
 *       disant pourquoi</b>, plutôt qu'un vide.</li>
 * </ol>
 */
@Component({
  selector: 'app-coach-directory',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, IconComponent, LogoComponent],
  template: `
    <div class="dir">
      <header class="dir__head">
        <a routerLink="/" class="dir__brand" aria-label="Accueil">
          <app-logo [size]="36" [showText]="true" />
        </a>
        <a routerLink="/login" class="btn btn-ghost btn-sm">Connexion</a>
      </header>

      <section class="dir__hero">
        <h1 class="display-md">Trouver un coach</h1>
        <p class="lead">
          Des coachs de course à pied et de préparation physique, avec leurs spécialités et leurs
          tarifs. Vous choisissez, vous demandez ; c'est le coach qui accepte.
        </p>
      </section>

      @if (facets(); as f) {
        <section class="dir__filters" aria-label="Filtres">
          <div class="dir__filter-row">
            <span class="dir__legend">Spécialité</span>
            <div class="dir__chips">
              @for (s of f.specialties; track s.value) {
                <button type="button" class="dir__chip"
                        [class.selected]="filters().specialty === s.value"
                        [disabled]="s.count === 0"
                        [attr.aria-pressed]="filters().specialty === s.value"
                        [title]="chipTitle(s)"
                        (click)="toggleFilter('specialty', s.value)">
                  {{ s.label }} <span class="dir__count">{{ s.count }}</span>
                </button>
              }
            </div>
          </div>

          @if (f.languages.length > 1) {
            <div class="dir__filter-row">
              <span class="dir__legend">Langue</span>
              <div class="dir__chips">
                @for (l of f.languages; track l.value) {
                  <button type="button" class="dir__chip"
                          [class.selected]="filters().language === l.value"
                          [attr.aria-pressed]="filters().language === l.value"
                          (click)="toggleFilter('language', l.value)">
                    {{ l.label }} <span class="dir__count">{{ l.count }}</span>
                  </button>
                }
              </div>
            </div>
          }

          @if (f.cities.length > 1) {
            <div class="dir__filter-row">
              <span class="dir__legend">Ville</span>
              <div class="dir__chips">
                @for (c of f.cities; track c.value) {
                  <button type="button" class="dir__chip"
                          [class.selected]="filters().city === c.value"
                          [attr.aria-pressed]="filters().city === c.value"
                          (click)="toggleFilter('city', c.value)">
                    {{ c.label }} <span class="dir__count">{{ c.count }}</span>
                  </button>
                }
              </div>
            </div>
          }

          @if (hasFilters()) {
            <button type="button" class="btn btn-ghost btn-sm dir__clear" (click)="clearFilters()">
              Tout afficher ({{ f.total }})
            </button>
          }
        </section>
      }

      @if (loading()) {
        <p class="dir__loading">Recherche…</p>
      } @else {
        <!-- Le repli. Il ne se confond jamais avec un résultat : l'écran dit d'abord que la
             recherche n'a rien donné, et seulement ensuite ce qu'il propose à la place. -->
        @if (empty()) {
          <div class="card dir__empty">
            <app-icon name="search" [size]="28" />
            <div>
              <strong>Aucun coach ne correspond à cette combinaison.</strong>
              <p class="field-hint">
                Nous démarrons : l'annuaire compte encore peu de coachs. Voici ceux qui prennent
                des athlètes en ce moment.
              </p>
            </div>
            <button type="button" class="btn btn-secondary btn-sm" (click)="clearFilters()">
              Effacer les filtres
            </button>
          </div>
        }

        @if (visible().length) {
          <ul class="dir__grid">
            @for (c of visible(); track c.slug) {
              <li class="card dir__card">
                <a [routerLink]="['/coachs', c.slug]" class="dir__card-link">
                  <div class="dir__avatar">
                    @if (photo(c); as src) {
                      <img [src]="src" [alt]="'Photo de ' + c.name" loading="lazy" />
                    } @else {
                      <app-icon name="user" [size]="28" />
                    }
                  </div>
                  <div class="dir__body">
                    <strong class="dir__name">{{ c.name }}</strong>
                    @if (c.headline) { <p class="dir__headline">{{ c.headline }}</p> }
                    <p class="dir__meta">
                      @if (c.city) { {{ c.city }} }
                      @if (c.city && c.remote) { · }
                      @if (c.remote) { à distance }
                      @if (c.experienceYears) { · {{ c.experienceYears }} ans d'expérience }
                    </p>
                    @if (c.specialtyLabels.length) {
                      <p class="dir__tags">
                        @for (s of c.specialtyLabels.slice(0, 3); track s) {
                          <span class="badge badge-neutral">{{ s }}</span>
                        }
                      </p>
                    }
                    <p class="dir__price">
                      {{ price(c) ?? 'Tarif sur demande' }}
                      @if (!c.acceptingAthletes) {
                        <span class="badge badge-neutral">Ne prend plus d'athlètes</span>
                      }
                    </p>
                  </div>
                </a>
              </li>
            }
          </ul>
        } @else if (!empty()) {
          <div class="card dir__empty">
            <app-icon name="inbox" [size]="28" />
            <div>
              <strong>L'annuaire ouvre bientôt.</strong>
              <p class="field-hint">Aucun coach n'y est encore publié.</p>
            </div>
          </div>
        }
      }
    </div>
  `,
  styles: [`
    .dir { max-width: 1040px; margin: 0 auto; padding: var(--sp-5) var(--sp-4) var(--sp-12); }
    .dir__head { display: flex; align-items: center; justify-content: space-between; margin-bottom: var(--sp-8); }
    .dir__brand { text-decoration: none; }
    .dir__hero { margin-bottom: var(--sp-8); }
    .dir__hero .lead { color: var(--ink-2); max-width: 60ch; }
    .dir__filters { display: flex; flex-direction: column; gap: var(--sp-4); margin-bottom: var(--sp-6); }
    .dir__legend { display: block; font-size: var(--text-2xs); font-weight: 700; text-transform: uppercase; letter-spacing: 0.03em; color: var(--ink-3); margin-bottom: var(--sp-2); }
    .dir__chips { display: flex; flex-wrap: wrap; gap: var(--sp-2); }
    .dir__chip { display: inline-flex; align-items: center; gap: var(--sp-2); padding: var(--sp-2) var(--sp-3); border: 1px solid var(--hairline); border-radius: var(--radius-full); background: var(--paper); color: var(--ink-2); font-size: var(--text-sm); cursor: pointer; min-height: 36px; }
    .dir__chip:hover:not(:disabled) { border-color: var(--primary-light); }
    .dir__chip.selected { border-color: var(--primary); background: var(--primary-wash); color: var(--primary); font-weight: 600; }
    /* Grisé et non masqué : une facette qui disparaît laisse croire qu'elle n'existe pas. */
    .dir__chip:disabled { opacity: 0.45; cursor: not-allowed; }
    .dir__count { font-family: var(--font-mono); font-size: var(--text-2xs); color: var(--ink-3); }
    .dir__chip.selected .dir__count { color: var(--primary); }
    .dir__clear { align-self: flex-start; }
    .dir__loading { color: var(--ink-3); padding: var(--sp-6) 0; }
    .dir__empty { display: flex; align-items: center; gap: var(--sp-4); padding: var(--sp-5); margin-bottom: var(--sp-5); flex-wrap: wrap; }
    .dir__empty > div { flex: 1; min-width: 220px; }
    .dir__empty p { margin: var(--sp-1) 0 0; }
    .dir__grid { list-style: none; margin: 0; padding: 0; display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: var(--sp-3); }
    .dir__card { padding: 0; overflow: hidden; }
    .dir__card-link { display: flex; gap: var(--sp-4); padding: var(--sp-4); text-decoration: none; color: inherit; }
    .dir__card-link:hover { background: var(--primary-wash); }
    .dir__avatar { flex: 0 0 auto; width: 72px; height: 72px; border-radius: var(--radius-lg); background: var(--paper-sunk); display: flex; align-items: center; justify-content: center; overflow: hidden; color: var(--ink-4); }
    .dir__avatar img { width: 100%; height: 100%; object-fit: cover; }
    .dir__body { min-width: 0; flex: 1; }
    .dir__name { display: block; font-size: var(--text-lg); color: var(--ink); }
    .dir__headline { margin: 2px 0 var(--sp-2); color: var(--ink-2); font-size: var(--text-sm); }
    .dir__meta { margin: 0 0 var(--sp-2); color: var(--ink-3); font-size: var(--text-xs); }
    .dir__tags { display: flex; flex-wrap: wrap; gap: var(--sp-1); margin: 0 0 var(--sp-2); }
    .dir__price { margin: 0; font-size: var(--text-sm); color: var(--ink); display: flex; align-items: center; gap: var(--sp-2); flex-wrap: wrap; }
  `],
})
export class CoachDirectoryComponent implements OnInit {
  private readonly service = inject(CoachDirectoryService);
  private readonly title = inject(Title);
  private readonly meta = inject(Meta);

  readonly facets = signal<CoachFacets | null>(null);
  readonly results = signal<CoachSummary[]>([]);
  readonly suggestions = signal<CoachSummary[]>([]);
  readonly loading = signal(true);
  readonly filters = signal<DirectoryFilters>({});

  /** La recherche n'a rien rendu alors qu'un filtre est posé : c'est le cas du repli. */
  readonly empty = computed(() => this.hasFilters() && this.results().length === 0);

  /** Les résultats, ou le repli — jamais les deux mélangés. */
  readonly visible = computed(() => (this.empty() ? this.suggestions() : this.results()));

  readonly hasFilters = computed(() =>
    Object.values(this.filters()).some((v) => v !== null && v !== undefined && v !== ''));

  ngOnInit(): void {
    // Cf. la note de la fiche coach : ces balises servent les robots qui exécutent le JavaScript,
    // pas les aperçus de partage. Elles ne remplacent pas un pré-rendu.
    this.title.setTitle('Trouver un coach de course à pied — Darilab');
    this.meta.updateTag({
      name: 'description',
      content: 'Des coachs de course à pied et de préparation physique, avec leurs spécialités et '
        + "leurs tarifs. Vous choisissez, vous demandez ; c'est le coach qui accepte.",
    });
    this.service.facets().subscribe({ next: (f) => this.facets.set(f) });
    this.load();
  }

  toggleFilter(key: keyof DirectoryFilters, value: string): void {
    const current = this.filters();
    this.filters.set({ ...current, [key]: current[key] === value ? null : value });
    this.load();
  }

  clearFilters(): void {
    this.filters.set({});
    this.load();
  }

  /** Ce que dit un filtre désactivé : pourquoi il l'est, et non qu'il n'existe pas. */
  chipTitle(f: FacetValue): string {
    return f.count === 0 ? `Aucun coach sur « ${f.label} » pour l'instant` : `${f.count} coach(s)`;
  }

  photo(c: CoachSummary): string | null {
    return this.service.photoSrc(c.photoUrl);
  }

  price(c: CoachSummary): string | null {
    return this.service.fromPrice(c.fromMonthlyCents);
  }

  private load(): void {
    this.loading.set(true);
    this.service.search(this.filters()).subscribe({
      next: (page) => {
        this.results.set(page.content);
        this.loading.set(false);
        // Le repli n'est chargé que s'il va servir : une requête de plus sur chaque recherche
        // fructueuse n'apporterait rien.
        if (page.content.length === 0 && this.hasFilters() && this.suggestions().length === 0) {
          this.service.suggestions().subscribe({ next: (s) => this.suggestions.set(s.content) });
        }
      },
      error: () => {
        this.results.set([]);
        this.loading.set(false);
      },
    });
  }
}
