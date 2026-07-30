import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, computed, effect, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { FormsModule } from '@angular/forms';
import { Athlete, AthleteLevel, AthleteStatus, AthleteSummary } from '../../core/models/athlete.model';
import { AthleteService } from '../../core/services/athlete.service';
import { BreadcrumbService } from '../../core/services/breadcrumb.service';
import { ToastService } from '../../core/services/toast.service';

const STATUS_LABELS: Record<AthleteStatus, string> = { ACTIVE: 'Actif', PAUSED: 'En pause', ARCHIVED: 'Archivé' };
const STATUS_BADGES: Record<AthleteStatus, string> = { ACTIVE: 'badge-success', PAUSED: 'badge-warning', ARCHIVED: 'badge-neutral' };
const LEVEL_LABELS: Record<AthleteLevel, string> = { BEGINNER: 'Débutant', INTERMEDIATE: 'Intermédiaire', ADVANCED: 'Avancé', ELITE: 'Élite' };

/** Onglets de l'athlète : segment d'URL + libellé. L'ordre suit le geste du coach. */
const TABS: { path: string; label: string }[] = [
  { path: 'resume', label: 'Résumé' },
  { path: 'programme', label: 'Programme' },
  { path: 'load', label: 'Charge' },
  { path: 'zones', label: 'Zones' },
  { path: 'tests', label: 'Tests' },
  { path: 'races', label: 'Objectifs' },
  { path: 'messages', label: 'Messages' },
];

/**
 * Libellés de fil d'Ariane, y compris les sections hors barre d'onglets : « Activités » est repliée
 * dans « Programme » (prévu/réalisé) mais son écran d'import reste joignable et doit se nommer.
 */
const SECTION_LABELS: Record<string, string> = {
  ...Object.fromEntries(TABS.map((t) => [t.path, t.label])),
  activities: 'Activités',
};

/**
 * Coquille d'un athlète : l'athlète est un <b>contexte dans lequel on reste</b>, pas une page qu'on
 * quitte. Cette route parente peint une fois pour toutes l'identité, les métriques de référence et
 * la barre d'onglets ; seul le contenu sous les onglets change (routes enfants).
 *
 * <p>Avant, les sous-écrans étaient des routes <i>sœurs</i> : cliquer un onglet détruisait la barre
 * d'onglets avec la fiche, donc passer de « Zones » à « Charge » imposait un aller-retour, aucun
 * sous-écran ne disait de quel athlète il s'agissait, et chacun repeignait son propre lien de
 * retour (quatre libellés différents). Les nommer enfants règle les trois d'un coup et rend le
 * retour navigateur prévisible : il ne défait plus qu'un changement d'onglet.</p>
 */
@Component({
  selector: 'app-athlete-shell',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, IconComponent, FormsModule],
  // Au défilement, le bandeau se réduit à une ligne : il ne mange pas l'écran, mais il ne
  // disparaît jamais — c'est tout l'intérêt d'un contexte persistant.
  host: { '(window:scroll)': 'onScroll()' },
  template: `
    @if (athlete(); as a) {
      <header class="shell-head" [class.shell-head--compact]="scrolled()">
        <div class="shell-id">
          <span class="avatar avatar-lg">{{ a.firstName[0] }}{{ a.lastName[0] }}</span>
          <div class="shell-id__text">
            <div class="shell-id__title">
              <!-- Le nom est un sélecteur : passer à un autre athlète SANS quitter l'onglet
                   courant, pour comparer plusieurs athlètes sur la même section. -->
              <div class="switcher" [class.switcher--open]="switcherOpen()">
                <button type="button" class="switcher__trigger" (click)="toggleSwitcher()"
                        [attr.aria-expanded]="switcherOpen()" aria-haspopup="listbox">
                  <h1 class="display-sm">{{ a.firstName }} {{ a.lastName }}</h1>
                  <app-icon name="chevron-down" [size]="16" />
                </button>
                @if (switcherOpen()) {
                  <div class="switcher__backdrop" (click)="closeSwitcher()"></div>
                  <div class="switcher__menu" role="listbox">
                    <input class="form-control switcher__search" [ngModel]="query()"
                           (ngModelChange)="query.set($event)" placeholder="Rechercher un athlète…"
                           aria-label="Rechercher un athlète" />
                    @for (o of matches(); track o.id) {
                      <button type="button" class="switcher__opt" role="option"
                              [class.sel]="o.id === athleteId()" (click)="goTo(o.id)">
                        <span class="avatar">{{ o.firstName[0] }}{{ o.lastName[0] }}</span>
                        <span class="switcher__name">{{ o.firstName }} {{ o.lastName }}</span>
                        @if (o.groupName) { <span class="switcher__grp">{{ o.groupName }}</span> }
                      </button>
                    } @empty {
                      <p class="switcher__empty field-hint">Aucun athlète pour cette recherche.</p>
                    }
                  </div>
                }
              </div>
              <!-- Athlète précédent / suivant : même ordre que la liste, onglet conservé. -->
              @if (siblings().length > 1) {
                <span class="stepper">
                  <button type="button" class="icon-btn" (click)="step(-1)" [disabled]="!prevId()"
                          title="Athlète précédent" aria-label="Athlète précédent">
                    <app-icon name="chevrons-left" [size]="15" />
                  </button>
                  <button type="button" class="icon-btn" (click)="step(1)" [disabled]="!nextId()"
                          title="Athlète suivant" aria-label="Athlète suivant">
                    <app-icon name="chevrons-right" [size]="15" />
                  </button>
                </span>
              }
              <span class="badge" [class]="statusBadges[a.status]">{{ statusLabels[a.status] }}</span>
              @if (isPrivate()) {
                <span class="badge badge-neutral" title="Athlète privé (hors club)"><app-icon name="lock" [size]="13" /> Privé</span>
              } @else {
                @for (club of a.clubs; track club.id) { <span class="badge badge-info">{{ club.name }}</span> }
              }
              @if (a.level) { <span class="badge badge-neutral">{{ levelLabels[a.level] }}</span> }
            </div>
            <p class="subtitle">{{ a.email || 'Pas d\\'email' }}</p>
          </div>

          <!-- Actions portant sur l'athlète entier : place fixe, quel que soit l'onglet. -->
          <div class="shell-actions">
            <button type="button" class="btn btn-ghost btn-sm" (click)="exportProgram()">
              <app-icon name="file-text" [size]="15" /> PDF
            </button>
            <a [routerLink]="['/app/athletes', athleteId(), 'edit']" class="btn btn-ghost btn-sm">Modifier</a>
            <button type="button" class="btn btn-primary btn-sm" (click)="invite()">Inviter</button>
          </div>
        </div>

        <dl class="stat-strip">
          <div class="stat-strip__item"><dt>FC max</dt><dd class="metric">{{ a.hrMax ?? '—' }}<small>{{ a.hrMax ? 'bpm' : '' }}</small></dd></div>
          <div class="stat-strip__item"><dt>FC repos</dt><dd class="metric">{{ a.hrRest ?? '—' }}<small>{{ a.hrRest ? 'bpm' : '' }}</small></dd></div>
          <div class="stat-strip__item"><dt>VMA</dt><dd class="metric">{{ a.vma ?? '—' }}<small>{{ a.vma ? 'km/h' : '' }}</small></dd></div>
          <div class="stat-strip__item"><dt>Poids</dt><dd class="metric">{{ a.weightKg ?? '—' }}<small>{{ a.weightKg ? 'kg' : '' }}</small></dd></div>
        </dl>

        <nav class="shell-tabs" aria-label="Sections de l'athlète">
          @for (t of tabs; track t.path) {
            <a [routerLink]="[t.path]" routerLinkActive="active">{{ t.label }}</a>
          }
        </nav>
      </header>

      @if (inviteUrl(); as url) {
        <div class="card invite-panel">
          <div>
            <strong>Lien d'invitation</strong>
            <p class="invite-url metric">{{ url }}</p>
          </div>
          <button type="button" class="btn btn-accent btn-sm" (click)="copyInvite()">Copier</button>
        </div>
      }

      <router-outlet />
    } @else if (loading()) {
      <div class="card"><div class="skeleton" style="height: 120px"></div></div>
    }
  `,
  styles: [`
    .shell-head {
      position: sticky; top: 0; z-index: 20;
      background: var(--canvas);
      border-bottom: 1px solid var(--hairline);
      margin: calc(var(--sp-5) * -1) calc(var(--sp-5) * -1) var(--sp-5);
      padding: var(--sp-5) var(--sp-5) 0;
      display: flex; flex-direction: column; gap: var(--sp-4);
    }
    .shell-id { display: flex; align-items: center; gap: var(--sp-4); flex-wrap: wrap; }
    .shell-id__text { min-width: 0; }
    .shell-id__title { display: flex; align-items: center; gap: var(--sp-2); flex-wrap: wrap; }
    .shell-id__title h1 { margin: 0; }

    /* Sélecteur d'athlète porté par le nom. */
    .switcher { position: relative; }
    .switcher__trigger {
      display: inline-flex; align-items: center; gap: var(--sp-2);
      background: none; border: none; padding: 2px 4px; margin-left: -4px; cursor: pointer;
      color: var(--ink); border-radius: var(--radius-sm); font: inherit;
    }
    .switcher__trigger:hover { background: var(--paper-sunk); }
    .switcher__trigger:focus-visible { outline: 2px solid var(--primary); outline-offset: 2px; }
    .switcher__trigger app-icon { color: var(--ink-3); }
    .switcher--open .switcher__trigger app-icon { color: var(--primary); }

    .switcher__backdrop { position: fixed; inset: 0; z-index: 40; }
    .switcher__menu {
      position: absolute; z-index: 41; top: calc(100% + 4px); left: 0;
      width: min(320px, 80vw); max-height: 380px; overflow-y: auto;
      background: var(--paper); border: 1px solid var(--line, var(--hairline));
      border-radius: var(--radius-md); box-shadow: var(--shadow-lg); padding: var(--sp-2);
      display: flex; flex-direction: column; gap: 2px;
    }
    .switcher__search { margin-bottom: var(--sp-1); }
    .switcher__opt {
      display: flex; align-items: center; gap: var(--sp-2); width: 100%;
      background: none; border: none; cursor: pointer; text-align: left;
      padding: var(--sp-2); border-radius: var(--radius-sm); color: var(--ink); font: inherit;
    }
    .switcher__opt:hover { background: var(--paper-sunk); }
    .switcher__opt.sel { background: var(--primary-wash); color: var(--primary); }
    .switcher__opt .avatar { width: 26px; height: 26px; font-size: var(--text-xs); flex: none; }
    .switcher__name { font-weight: 600; font-size: var(--text-sm); }
    .switcher__grp { margin-left: auto; font-size: var(--text-xs); color: var(--ink-3); }
    .switcher__empty { padding: var(--sp-2); margin: 0; }

    .stepper { display: inline-flex; gap: 2px; }
    .stepper .icon-btn {
      border: 1px solid var(--hairline); background: var(--paper); color: var(--ink-2);
      border-radius: var(--radius-sm); padding: 3px 5px; cursor: pointer; display: inline-flex;
    }
    .stepper .icon-btn:hover:not(:disabled) { border-color: var(--ink-4); color: var(--ink); }
    .stepper .icon-btn:disabled { opacity: .4; cursor: default; }
    .shell-id .subtitle { margin: 2px 0 0; color: var(--ink-3); font-size: var(--text-sm); }
    .shell-actions { margin-left: auto; display: flex; gap: var(--sp-2); flex-wrap: wrap; }

    /* Version compacte : on retire les métriques et les actions, on garde identité + onglets. */
    .shell-head--compact { gap: var(--sp-2); padding-top: var(--sp-3); box-shadow: var(--shadow-sm); }
    .shell-head--compact .stat-strip,
    .shell-head--compact .shell-actions,
    .shell-head--compact .subtitle { display: none; }
    .shell-head--compact .shell-id { gap: var(--sp-2); }
    .shell-head--compact .avatar-lg { width: 32px; height: 32px; font-size: var(--text-sm); }
    .shell-head--compact h1 { font-size: var(--text-lg); }
    @media (prefers-reduced-motion: no-preference) {
      .shell-head { transition: padding-top var(--duration, .18s) ease, gap var(--duration, .18s) ease; }
    }

    .stat-strip { display: flex; flex-wrap: wrap; gap: var(--sp-5); margin: 0; }
    .stat-strip__item { display: flex; flex-direction: column; }
    .stat-strip__item dt { font-size: var(--text-xs); text-transform: uppercase; letter-spacing: .05em; color: var(--ink-3); }
    .stat-strip__item dd { margin: 0; font-size: var(--text-lg); font-weight: 700; }
    .stat-strip__item small { font-size: var(--text-xs); color: var(--ink-3); margin-left: 3px; font-weight: 500; }

    /* Barre d'onglets : elle ne disparaît plus quand on change de section. */
    .shell-tabs { display: flex; gap: var(--sp-1); overflow-x: auto; }
    .shell-tabs a {
      padding: var(--sp-2) var(--sp-3);
      color: var(--ink-3); text-decoration: none; font-size: var(--text-sm); font-weight: 600;
      border-bottom: 2px solid transparent; white-space: nowrap;
    }
    .shell-tabs a:hover { color: var(--ink); }
    .shell-tabs a.active { color: var(--primary); border-bottom-color: var(--primary); }

    .invite-panel { display: flex; align-items: center; justify-content: space-between; gap: var(--sp-3); margin-bottom: var(--sp-5); }
    .invite-url { word-break: break-all; font-size: var(--text-sm); margin: 2px 0 0; }

    @media (max-width: 720px) {
      .shell-head { margin-left: calc(var(--sp-4) * -1); margin-right: calc(var(--sp-4) * -1); padding-left: var(--sp-4); padding-right: var(--sp-4); }
      .shell-actions { margin-left: 0; width: 100%; }
    }
  `],
})
export class AthleteShellComponent implements OnInit, OnDestroy {
  readonly athleteId = input.required<string>();

  private readonly athleteService = inject(AthleteService);
  private readonly breadcrumb = inject(BreadcrumbService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  readonly athlete = signal<Athlete | null>(null);
  readonly loading = signal(true);
  readonly inviteUrl = signal<string | null>(null);

  readonly tabs = TABS;
  readonly statusLabels = STATUS_LABELS;
  readonly statusBadges = STATUS_BADGES;
  readonly levelLabels = LEVEL_LABELS;

  readonly isPrivate = computed(() => (this.athlete()?.clubs ?? []).length === 0);

  // --- Sélecteur d'athlète (rester sur le même onglet en changeant de personne) ---
  readonly siblings = signal<AthleteSummary[]>([]);
  readonly switcherOpen = signal(false);
  readonly query = signal('');

  readonly matches = computed(() => {
    const q = this.query().trim().toLowerCase();
    const all = this.siblings();
    if (!q) return all;
    return all.filter((a) => `${a.firstName} ${a.lastName}`.toLowerCase().includes(q));
  });

  private readonly index = computed(() => this.siblings().findIndex((a) => a.id === this.athleteId()));
  readonly prevId = computed(() => {
    const i = this.index();
    return i > 0 ? this.siblings()[i - 1].id : null;
  });
  readonly nextId = computed(() => {
    const i = this.index();
    return i >= 0 && i < this.siblings().length - 1 ? this.siblings()[i + 1].id : null;
  });

  /** Vrai dès qu'on a défilé : le bandeau passe en version compacte. */
  readonly scrolled = signal(false);

  onScroll(): void {
    const past = (window.scrollY ?? 0) > 96;
    if (past !== this.scrolled()) this.scrolled.set(past);
  }

  toggleSwitcher(): void {
    this.switcherOpen.update((v) => !v);
    if (this.switcherOpen()) this.query.set('');
  }

  closeSwitcher(): void {
    this.switcherOpen.set(false);
  }

  /** Ouvre un autre athlète sur la <b>même section</b> — le geste central du coach. */
  goTo(id: string): void {
    this.closeSwitcher();
    if (id === this.athleteId()) return;
    const section = SECTION_LABELS[this.segment()] ? this.segment() : 'resume';
    this.router.navigate(['/app/athletes', id, section]);
  }

  step(delta: number): void {
    const id = delta < 0 ? this.prevId() : this.nextId();
    if (id) this.goTo(id);
  }

  /** Segment d'URL courant, tenu à jour à chaque navigation (Router.url n'est pas réactif). */
  private readonly segment = signal('');

  constructor() {
    this.segment.set(this.lastSegment(this.router.url));
    this.router.events
      .pipe(filter((e): e is NavigationEnd => e instanceof NavigationEnd), takeUntilDestroyed())
      .subscribe((e) => this.segment.set(this.lastSegment(e.urlAfterRedirects)));

    // Le fil d'Ariane suit le nom dès qu'il est connu, et l'onglet courant à chaque navigation.
    // Il écrit dans le signal du service : `allowSignalWrites` est requis.
    effect(() => {
      const a = this.athlete();
      const segment = this.segment();
      const label = segment === 'resume' ? null : SECTION_LABELS[segment] ?? null;
      this.breadcrumb.set([
        { label: 'Athlètes', link: ['/app/athletes'] },
        a
          ? { label: `${a.firstName} ${a.lastName}`, link: ['/app/athletes', this.athleteId(), 'resume'] }
          : { label: '…' },
        ...(label ? [{ label }] : []),
      ]);
    }, { allowSignalWrites: true });
  }

  private lastSegment(url: string): string {
    return url.split('?')[0].split('/').filter(Boolean).pop() ?? '';
  }

  ngOnInit(): void {
    // Voisins pour le sélecteur et le pas à pas : même ordre que la liste d'athlètes.
    this.athleteService.list({ status: 'ACTIVE' }).subscribe({
      next: (page) => this.siblings.set(page.content),
      error: () => this.siblings.set([]),
    });
  }

  /**
   * Recharge l'athlète quand l'identifiant change. Passer d'un athlète à l'autre ne détruit pas la
   * coquille (le routeur réutilise le composant) : un chargement dans {@code ngOnInit} resterait
   * sur le premier athlète ouvert.
   */
  private readonly loadAthlete = effect((onCleanup) => {
    const id = this.athleteId();
    this.loading.set(true);
    const sub = this.athleteService.get(id).subscribe({
      next: (a) => { this.athlete.set(a); this.loading.set(false); },
      error: () => { this.loading.set(false); this.router.navigate(['/app/athletes']); },
    });
    onCleanup(() => sub.unsubscribe());
  }, { allowSignalWrites: true });

  ngOnDestroy(): void {
    this.breadcrumb.clear();
  }

  invite(): void {
    this.athleteService.invite(this.athleteId()).subscribe({
      next: (res) => {
        this.inviteUrl.set(res.inviteUrl);
        this.toast.success("Lien d'invitation généré");
      },
    });
  }

  copyInvite(): void {
    const url = this.inviteUrl();
    if (url) {
      navigator.clipboard?.writeText(url);
      this.toast.info('Lien copié dans le presse-papier.');
    }
  }

  /** Télécharge le programme PDF des 4 prochaines semaines. */
  exportProgram(): void {
    const today = new Date();
    const from = today.toISOString().slice(0, 10);
    const to = new Date(today.getTime() + 28 * 86400000).toISOString().slice(0, 10);
    this.athleteService.exportProgram(this.athleteId(), from, to).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'programme.pdf';
        a.click();
        URL.revokeObjectURL(url);
        this.toast.success('Programme exporté (PDF)');
      },
      error: () => this.toast.error('Export impossible.'),
    });
  }
}
