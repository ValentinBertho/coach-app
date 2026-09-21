import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Subject, debounceTime } from 'rxjs';
import {
  AdminAuditAction,
  AdminAuditEntry,
  AdminAuditScopeOption,
  AuditTargetType,
} from '../../core/models/admin.model';
import { AdminService } from '../../core/services/admin.service';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state.component';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { PaginatorComponent } from '../../shared/components/paginator/paginator.component';
import { SkeletonComponent } from '../../shared/components/skeleton/skeleton.component';

/**
 * Journal des actions d'administration.
 *
 * <p><b>Ce qu'il remplace.</b> Rien. Aucune trace en base ne disait qui avait supprimé un compte,
 * changé un rôle, suspendu un club ou ouvert une session au nom d'un utilisateur. La seule ligne
 * existante était un {@code WARN} applicatif pour l'impersonation — invisible depuis le produit,
 * et perdue à la rotation des journaux.</p>
 *
 * <p><b>Lecture seule.</b> Aucun bouton n'écrit ni ne supprime : un journal qu'on peut amender
 * depuis l'interface qu'il surveille ne prouve rien.</p>
 *
 * <p><b>Il ne montre plus seulement le back-office.</b> Connexions, mots de passe, consentements
 * santé, exports RGPD, athlètes créés ou archivés y figurent désormais. Le filtre de <b>portée</b>
 * est ce qui rend l'écran encore lisible : une journée d'usage produit plus de lignes de sécurité
 * qu'une année de gestes d'administration, et sans lui les secondes seraient introuvables.</p>
 */
@Component({
  selector: 'app-admin-audit',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule, PaginatorComponent, SkeletonComponent, EmptyStateComponent, IconComponent,
    RouterLink, DatePipe,
  ],
  templateUrl: './admin-audit.component.html',
  // La barre de filtres est celle des autres listes : une seule feuille pour cinq écrans.
  styleUrls: ['./admin-list.scss', './admin-audit.component.scss'],
})
export class AdminAuditComponent implements OnInit {
  private readonly admin = inject(AdminService);
  private readonly route = inject(ActivatedRoute);
  private readonly searchInput$ = new Subject<void>();

  readonly entries = signal<AdminAuditEntry[]>([]);
  readonly actions = signal<AdminAuditAction[]>([]);
  readonly scopes = signal<AdminAuditScopeOption[]>([]);
  readonly loading = signal(true);
  readonly failed = signal(false);
  readonly page = signal(0);
  readonly totalPages = signal(1);
  readonly total = signal(0);

  readonly targetTypes: { value: AuditTargetType; label: string }[] = [
    { value: 'USER', label: 'Utilisateur' },
    { value: 'CLUB', label: 'Club' },
    { value: 'ATHLETE', label: 'Athlète' },
    { value: 'INVITATION', label: 'Invitation' },
    { value: 'TRAINING_PLAN', label: "Plan d'entraînement" },
    { value: 'PLATFORM', label: 'Plateforme' },
  ];

  /**
   * Les actions regroupées par famille, pour les `<optgroup>` de la liste déroulante.
   *
   * <p>Quarante actions à plat ne se parcourent pas. Un back antérieur ne sert pas la portée :
   * ces actions atterrissent alors dans un groupe unique sans libellé, ce qui redonne exactement
   * la liste plate d'avant plutôt qu'un écran cassé (§4 bis).</p>
   */
  get actionGroups(): { label: string; actions: AdminAuditAction[] }[] {
    const groups = new Map<string, { label: string; actions: AdminAuditAction[] }>();
    for (const a of this.actions()) {
      const key = a.scope ?? '';
      if (!groups.has(key)) groups.set(key, { label: a.scopeLabel ?? '', actions: [] });
      groups.get(key)!.actions.push(a);
    }
    return [...groups.values()];
  }

  search = '';
  filterAction = '';
  filterScope = '';
  filterTarget = '';
  /** 0 = sans limite de temps. 30 jours par défaut : le journal se lit d'abord au présent. */
  filterDays = 30;
  /** Posé par un lien « historique » venant d'une fiche : le journal d'une seule ressource. */
  targetId = '';

  ngOnInit(): void {
    this.searchInput$.pipe(debounceTime(300)).subscribe(() => {
      this.page.set(0);
      this.load();
    });
    this.admin.auditActions().subscribe({
      next: (a) => this.actions.set(a),
      error: () => this.actions.set([]),
    });
    this.admin.auditScopes().subscribe({
      next: (s) => this.scopes.set(s),
      error: () => this.scopes.set([]),
    });
    const qp = this.route.snapshot.queryParamMap;
    this.targetId = qp.get('targetId') ?? '';
    this.filterAction = qp.get('action') ?? '';
    this.filterScope = qp.get('scope') ?? '';
    this.load();
  }

  onSearchChange(): void {
    this.searchInput$.next();
  }

  onFilterChange(): void {
    this.page.set(0);
    this.load();
  }

  resetFilters(): void {
    this.search = '';
    this.filterAction = '';
    this.filterScope = '';
    this.filterTarget = '';
    this.filterDays = 30;
    this.targetId = '';
    this.onFilterChange();
  }

  get hasFilters(): boolean {
    return !!(
      this.search ||
      this.filterAction ||
      this.filterScope ||
      this.filterTarget ||
      this.targetId ||
      this.filterDays !== 30
    );
  }

  goToPage(p: number): void {
    this.page.set(p);
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.failed.set(false);
    this.admin
      .audit({
        action: this.filterAction || undefined,
        scope: this.filterScope || undefined,
        targetType: this.filterTarget || undefined,
        targetId: this.targetId || undefined,
        days: this.filterDays || undefined,
        q: this.search || undefined,
        page: this.page(),
      })
      .subscribe({
        next: (p) => {
          this.entries.set(p.content);
          this.totalPages.set(p.totalPages);
          this.total.set(p.totalElements);
          this.loading.set(false);
        },
        error: () => {
          this.failed.set(true);
          this.loading.set(false);
        },
      });
  }

  /** Libellés des rôles, pour ne pas afficher un SCREAMING_SNAKE_CASE dans un tableau français. */
  private static readonly ROLE_LABELS: Record<string, string> = {
    PLATFORM_ADMIN: 'Admin plateforme',
    HEAD_COACH: 'Head coach',
    COACH: 'Coach',
    ATHLETE: 'Athlète',
  };

  /** Un rôle inconnu s'affiche tel quel plutôt que de disparaître : le journal ne cache rien. */
  roleLabel(role: string): string {
    return AdminAuditComponent.ROLE_LABELS[role] ?? role;
  }

  /**
   * Le chemin, raccourci par la gauche. Les routes du produit sont longues et toutes préfixées
   * de la même façon (`/clubs/{uuid}/athletes/{uuid}/…`) : affichées en entier, elles poussent la
   * colonne hors de l'écran et se ressemblent toutes. Le chemin complet reste en infobulle.
   */
  shortPath(path: string | null | undefined): string {
    if (!path) return '';
    return path.length <= 32 ? path : '…' + path.slice(-31);
  }

  /**
   * Le navigateur, en un mot.
   *
   * <p>L'agent utilisateur est enregistré depuis l'origine et n'était affiché nulle part. Brut,
   * il fait cent caractères illisibles ; ce qu'on en cherche dans un journal, c'est « est-ce le
   * même poste que d'habitude ». Le détail complet reste en infobulle.</p>
   */
  browserOf(ua: string | null | undefined): string {
    if (!ua) return '';
    // L'ordre compte : Edge et Chrome se déclarent tous deux « Chrome », Chrome se déclare
    // « Safari ». On teste donc du plus spécifique au plus générique.
    if (ua.includes('Edg/')) return 'Edge';
    if (ua.includes('OPR/') || ua.includes('Opera')) return 'Opera';
    if (ua.includes('Firefox/')) return 'Firefox';
    if (ua.includes('Chrome/')) return 'Chrome';
    if (ua.includes('Safari/')) return 'Safari';
    return 'Autre';
  }

  /** La fiche correspondante, quand la cible en a une. */
  targetRoute(e: AdminAuditEntry): string[] | null {
    if (!e.targetId) return null;
    if (e.targetType === 'USER') return ['/admin/users', e.targetId];
    if (e.targetType === 'CLUB') return ['/admin/clubs', e.targetId];
    if (e.targetType === 'ATHLETE' || e.targetType === 'INVITATION') {
      return ['/admin/athletes', e.targetId, 'edit'];
    }
    // TRAINING_PLAN : les plans n'ont pas de fiche dans le back-office. La ligne reste lisible
    // (le nom du plan est figé dans `targetLabel`), elle n'est simplement pas cliquable.
    return null;
  }
}
