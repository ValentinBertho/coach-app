import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, computed, effect, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { Athlete, AthleteLevel, AthleteStatus } from '../../core/models/athlete.model';
import { AthleteService } from '../../core/services/athlete.service';
import { BreadcrumbService } from '../../core/services/breadcrumb.service';
import { ToastService } from '../../core/services/toast.service';

const STATUS_LABELS: Record<AthleteStatus, string> = { ACTIVE: 'Actif', PAUSED: 'En pause', ARCHIVED: 'Archivé' };
const STATUS_BADGES: Record<AthleteStatus, string> = { ACTIVE: 'badge-success', PAUSED: 'badge-warning', ARCHIVED: 'badge-neutral' };
const LEVEL_LABELS: Record<AthleteLevel, string> = { BEGINNER: 'Débutant', INTERMEDIATE: 'Intermédiaire', ADVANCED: 'Avancé', ELITE: 'Élite' };

/** Onglets de l'athlète : segment d'URL + libellé. L'ordre suit le geste du coach. */
const TABS: { path: string; label: string }[] = [
  { path: 'resume', label: 'Résumé' },
  { path: 'load', label: 'Charge' },
  { path: 'zones', label: 'Zones' },
  { path: 'tests', label: 'Tests' },
  { path: 'races', label: 'Objectifs' },
  { path: 'activities', label: 'Activités' },
  { path: 'messages', label: 'Messages' },
];

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
  imports: [RouterOutlet, RouterLink, RouterLinkActive, IconComponent],
  template: `
    @if (athlete(); as a) {
      <header class="shell-head">
        <div class="shell-id">
          <span class="avatar avatar-lg">{{ a.firstName[0] }}{{ a.lastName[0] }}</span>
          <div class="shell-id__text">
            <div class="shell-id__title">
              <h1 class="display-sm">{{ a.firstName }} {{ a.lastName }}</h1>
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
    .shell-id .subtitle { margin: 2px 0 0; color: var(--ink-3); font-size: var(--text-sm); }
    .shell-actions { margin-left: auto; display: flex; gap: var(--sp-2); flex-wrap: wrap; }

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
      const tab = TABS.find((t) => t.path === this.segment());
      this.breadcrumb.set([
        { label: 'Athlètes', link: ['/app/athletes'] },
        a
          ? { label: `${a.firstName} ${a.lastName}`, link: ['/app/athletes', this.athleteId(), 'resume'] }
          : { label: '…' },
        ...(tab && tab.path !== 'resume' ? [{ label: tab.label }] : []),
      ]);
    }, { allowSignalWrites: true });
  }

  private lastSegment(url: string): string {
    return url.split('?')[0].split('/').filter(Boolean).pop() ?? '';
  }

  ngOnInit(): void {
    this.athleteService.get(this.athleteId()).subscribe({
      next: (a) => { this.athlete.set(a); this.loading.set(false); },
      error: () => { this.loading.set(false); this.router.navigate(['/app/athletes']); },
    });
  }

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
