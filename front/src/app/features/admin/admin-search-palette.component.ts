import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, ViewChild, computed, inject, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Subject, debounceTime, switchMap } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { of } from 'rxjs';
import { AdminSearchHit, AdminSearchResult, ROLE_LABELS, USER_STATUS_LABELS } from '../../core/models/admin.model';
import { AdminService } from '../../core/services/admin.service';
import { IconComponent } from '../../shared/components/icon/icon.component';

/** Une famille de résultats, aplatie pour que la navigation au clavier reste linéaire. */
interface Section {
  key: string;
  label: string;
  icon: string;
  hits: AdminSearchHit[];
  total: number;
  /** Écran dédié, quand il y a plus de résultats que la palette n'en montre. */
  moreRoute: string;
}

/**
 * Recherche globale du back-office : une saisie, trois familles de ressources.
 *
 * <p><b>Le geste qu'elle remplace.</b> Un ticket de support arrive avec une adresse e-mail et
 * rien d'autre. Il fallait ouvrir « Utilisateurs », chercher, ne rien trouver, ouvrir
 * « Athlètes », chercher à nouveau, puis se demander si le nom saisi était celui du club.</p>
 *
 * <p>Ouverte par le bouton de l'en-tête ou par <kbd>Ctrl/⌘ K</kbd>, fermée par <kbd>Échap</kbd>.
 * Les flèches parcourent les résultats, <kbd>Entrée</kbd> ouvre la fiche : on ne quitte pas le
 * clavier entre l'adresse collée et l'écran voulu.</p>
 */
@Component({
  selector: 'app-admin-search-palette',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, IconComponent],
  templateUrl: './admin-search-palette.component.html',
  styleUrl: './admin-search-palette.component.scss',
})
export class AdminSearchPaletteComponent implements AfterViewInit {
  private readonly admin = inject(AdminService);
  private readonly router = inject(Router);
  private readonly typed$ = new Subject<string>();

  readonly closed = output<void>();

  @ViewChild('input') private input?: ElementRef<HTMLInputElement>;

  readonly query = signal('');
  readonly loading = signal(false);
  readonly result = signal<AdminSearchResult | null>(null);
  readonly cursor = signal(0);

  readonly roleLabels = ROLE_LABELS;
  readonly statusLabels = USER_STATUS_LABELS;

  readonly sections = computed<Section[]>(() => {
    const r = this.result();
    if (!r) return [];
    return [
      { key: 'users', label: 'Comptes', icon: 'user', hits: r.users, total: r.usersTotal, moreRoute: '/admin/users' },
      { key: 'clubs', label: 'Clubs', icon: 'building-2', hits: r.clubs, total: r.clubsTotal, moreRoute: '/admin/clubs' },
      { key: 'athletes', label: 'Athlètes', icon: 'footprints', hits: r.athletes, total: r.athletesTotal, moreRoute: '/admin/athletes' },
    ].filter((s) => s.hits.length > 0);
  });

  /** Résultats à plat : c'est cette liste que les flèches parcourent. */
  readonly flat = computed<AdminSearchHit[]>(() => this.sections().flatMap((s) => s.hits));

  readonly empty = computed(
    () => !this.loading() && this.query().trim().length >= 2 && this.flat().length === 0,
  );

  constructor() {
    this.typed$
      .pipe(
        debounceTime(220),
        switchMap((q) => {
          // En deçà de deux caractères le serveur rend une réponse vide : ne pas l'appeler
          // évite un aller-retour par lettre tapée, et un état « chargement » qui clignote.
          if (q.trim().length < 2) {
            this.loading.set(false);
            return of(null);
          }
          this.loading.set(true);
          return this.admin.search(q.trim());
        }),
        takeUntilDestroyed(),
      )
      .subscribe({
        next: (r) => {
          this.result.set(r);
          this.cursor.set(0);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }

  /**
   * La palette prend le clavier dès qu'elle apparaît.
   *
   * <p>C'est elle qui le fait, pas le parent : le composant n'existe qu'une fois le {@code @if}
   * résolu, et un appel depuis l'ouverture viserait une vue pas encore créée — le champ restait
   * alors vide et sans curseur, ce qui vide la palette de son intérêt.</p>
   */
  ngAfterViewInit(): void {
    this.input?.nativeElement.focus();
  }

  onInput(value: string): void {
    this.query.set(value);
    this.typed$.next(value);
  }

  onKeydown(event: KeyboardEvent): void {
    const items = this.flat();
    if (event.key === 'Escape') {
      event.preventDefault();
      this.close();
      return;
    }
    if (!items.length) return;

    if (event.key === 'ArrowDown') {
      event.preventDefault();
      this.cursor.set((this.cursor() + 1) % items.length);
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      this.cursor.set((this.cursor() - 1 + items.length) % items.length);
    } else if (event.key === 'Enter') {
      event.preventDefault();
      this.open(items[this.cursor()]);
    }
  }

  /** Index global d'un résultat, pour surligner celui que les flèches ont atteint. */
  indexOf(hit: AdminSearchHit): number {
    return this.flat().indexOf(hit);
  }

  badgeLabel(section: string, badge: string | null): string {
    if (!badge) return '';
    if (section === 'users') return this.roleLabels[badge as keyof typeof this.roleLabels] ?? badge;
    if (badge === 'ACTIVE') return 'Actif';
    if (badge === 'SUSPENDED') return 'Suspendu';
    if (badge === 'PAUSED') return 'En pause';
    if (badge === 'ARCHIVED') return 'Archivé';
    return badge;
  }

  open(hit: AdminSearchHit): void {
    void this.router.navigateByUrl(hit.route);
    this.close();
  }

  openList(route: string): void {
    void this.router.navigate([route], { queryParams: { q: this.query().trim() } });
    this.close();
  }

  close(): void {
    this.closed.emit();
  }
}
