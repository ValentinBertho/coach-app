import { DragDropModule } from '@angular/cdk/drag-drop';
import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { IconComponent } from '../icon/icon.component';
import { WorkoutTemplate } from '../../../core/models/workout-template.model';
import { StrengthSession } from '../../../core/models/strength.model';
import { RunDrill } from '../../../core/models/run-drill.model';
import {
  CategoryDomain, SessionCategory, categoryOptions, categoryPath, categoryWithDescendants,
} from '../../../core/models/session-category.model';

/** Les trois familles de la bibliothèque, plus le « tout » qui n'en exclut aucune. */
export type LibraryFamily = 'all' | 'course' | 'strength' | 'drill';

/** Une famille et son domaine de catégories — la correspondance est faite une seule fois. */
const FAMILY_DOMAIN: Record<'course' | 'strength' | 'drill', CategoryDomain> = {
  course: 'COURSE', strength: 'STRENGTH', drill: 'DRILL',
};

const FAMILY_LABEL: Record<'course' | 'strength' | 'drill', string> = {
  course: 'Course', strength: 'Prépa physique', drill: 'Éducatifs',
};

const FAMILY_ICON: Record<'course' | 'strength' | 'drill', string> = {
  course: 'footprints', strength: 'dumbbell', drill: 'graduation-cap',
};

/**
 * Un élément de bibliothèque, quelle que soit sa famille.
 *
 * <p>Course, force et éducatifs étaient filtrés, comptés et rendus par trois chemins parallèles.
 * Toute règle de tri devait donc être écrite trois fois — et l'a été deux fois et demie : la
 * recherche ignorait les accents nulle part, le repli d'accordéon masquait les résultats, et la
 * force n'avait ni catégorie ni compte. Une seule forme, un seul pipeline.</p>
 */
interface LibItem {
  readonly id: string;
  readonly name: string;
  readonly kind: 'course' | 'strength' | 'drill';
  readonly favorite: boolean;
  readonly useCount: number;
  readonly categoryId: string | null;
  /** L'objet d'origine : c'est lui qui voyage dans le `cdkDragData` et dans les sorties. */
  readonly data: WorkoutTemplate | StrengthSession | RunDrill;
}

/** Un accordéon : une catégorie d'une famille, et ce qu'elle contient après filtrage. */
interface LibGroup {
  readonly key: string;
  readonly name: string;
  readonly kind: 'course' | 'strength' | 'drill';
  readonly icon: string;
  readonly items: LibItem[];
}

/** Une entrée du menu déroulant des catégories, avec ce qu'elle rapporterait. */
interface CategoryChoice {
  readonly id: string;
  readonly label: string;
  readonly count: number;
}

/**
 * Panneau bibliothèque réutilisable (colonne gauche du calendrier, et sélecteur du « + »).
 *
 * <h2>Premier tri, puis liste</h2>
 *
 * <p>Retour d'un coach bêta : « quand la bibliothèque contient énormément de séances il faut
 * beaucoup scroller ». Le panneau n'offrait qu'une recherche par nom : pour <b>parcourir</b> —
 * ce qu'on fait quand on ne sait pas encore quelle séance on veut — il fallait dérouler tout ce
 * que le club possède, course, force et éducatifs mêlés.</p>
 *
 * <p>Deux filtres en tête, dans l'ordre où l'on choisit : la <b>famille</b> (course · prépa
 * physique · éducatifs), puis la <b>catégorie</b> de cette famille. Chacun porte son compte, si
 * bien qu'on voit ce qu'on obtiendra avant de cliquer. Et tant qu'un filtre ou une recherche est
 * actif, les accordéons s'ouvrent : un résultat de recherche caché dans une catégorie repliée
 * était le pire des deux mondes — le compte s'affichait, la séance non.</p>
 *
 * <p>Chaque item reste draggable (`cdkDrag` + `[cdkDragData]`) dans un `cdkDropList` — condition
 * du glisser-déposer vers le calendrier, vérifiée par les tests de ce composant.</p>
 */
@Component({
  selector: 'app-session-library-panel',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink, DragDropModule, IconComponent],
  templateUrl: './session-library-panel.component.html',
  styleUrl: './session-library-panel.component.scss',
})
export class SessionLibraryPanelComponent {
  readonly courseTemplates = input<WorkoutTemplate[]>([]);
  readonly strengthSessions = input<StrengthSession[]>([]);
  readonly drills = input<RunDrill[]>([]);
  readonly categories = input<SessionCategory[]>([]);

  /**
   * Mode « choisir pour planifier » : le panneau sert de sélecteur (picker « + » d'un jour)
   * plutôt que de réserve à glisser. Le clic devient l'action principale sur les trois familles,
   * au lieu d'ouvrir la consultation.
   */
  readonly pickMode = input(false);

  /** Émis au clic sur une séance course (consultation, ou planification en `pickMode`). */
  readonly courseSelect = output<WorkoutTemplate>();
  /** Émis au clic sur une séance de force — utile seulement en `pickMode`. */
  readonly strengthSelect = output<StrengthSession>();
  /** Émis au clic sur un éducatif — utile seulement en `pickMode`. */
  readonly drillSelect = output<RunDrill>();
  /** Émis au clic sur l'étoile (épingler / dé-épingler un favori). */
  readonly favoriteToggle = output<WorkoutTemplate>();

  /** Infobulle des items : le clic ne fait pas la même chose selon le mode. */
  readonly itemHint = computed(() =>
    this.pickMode() ? 'Planifier cette séance' : 'Consulter la séance');

  // --- Filtres ---------------------------------------------------------------

  readonly search = signal('');
  readonly family = signal<LibraryFamily>('all');
  /** '' = toutes les catégories ; {@link NO_CATEGORY} = celles qui n'en ont pas. */
  readonly categoryId = signal('');

  static readonly NO_CATEGORY = '__none__';
  readonly NO_CATEGORY_ID = SessionLibraryPanelComponent.NO_CATEGORY;
  readonly DRILLS_KEY = 'drill:' + SessionLibraryPanelComponent.NO_CATEGORY;

  readonly families: { value: LibraryFamily; label: string; icon: string }[] = [
    { value: 'all', label: 'Tout', icon: 'library' },
    { value: 'course', label: FAMILY_LABEL.course, icon: FAMILY_ICON.course },
    { value: 'strength', label: FAMILY_LABEL.strength, icon: FAMILY_ICON.strength },
    { value: 'drill', label: FAMILY_LABEL.drill, icon: FAMILY_ICON.drill },
  ];

  setFamily(value: LibraryFamily): void {
    this.family.set(value);
    // La catégorie appartenait à l'ancienne famille : la garder ne filtrerait plus rien, et le
    // menu afficherait un choix absent de ses propres options.
    this.categoryId.set('');
  }

  clearFilters(): void {
    this.search.set('');
    this.family.set('all');
    this.categoryId.set('');
  }

  /** Un filtre est posé (recherche comprise) : c'est ce qui ouvre les accordéons. */
  readonly filtersActive = computed(() =>
    !!this.search().trim() || this.family() !== 'all' || !!this.categoryId());

  // --- Pipeline : tous les items → recherche → famille → catégorie -----------

  private readonly allItems = computed<LibItem[]>(() => [
    ...this.courseTemplates().map((t) => ({
      id: t.id, name: t.name, kind: 'course' as const, favorite: !!t.favorite,
      useCount: t.useCount ?? 0, categoryId: t.categoryId ?? null, data: t,
    })),
    ...this.strengthSessions().map((s) => ({
      id: s.id, name: s.name, kind: 'strength' as const, favorite: !!s.favorite,
      useCount: s.useCount ?? 0, categoryId: s.categoryId ?? null, data: s,
    })),
    ...this.drills().map((d) => ({
      id: d.id, name: d.name, kind: 'drill' as const, favorite: false,
      useCount: 0, categoryId: d.categoryId ?? null, data: d,
    })),
  ]);

  /** Ce que la recherche laisse passer, toutes familles confondues. */
  private readonly searched = computed(() => {
    const q = normalize(this.search());
    return q ? this.allItems().filter((i) => normalize(i.name).includes(q)) : this.allItems();
  });

  /** Puis la famille : c'est le premier tri, celui qui divise la bibliothèque par trois. */
  private readonly scoped = computed(() => {
    const f = this.family();
    return f === 'all' ? this.searched() : this.searched().filter((i) => i.kind === f);
  });

  /** Et enfin la catégorie, sous-catégories comprises — « Seuil » contient « Seuil long ». */
  private readonly visible = computed(() => {
    const cat = this.categoryId();
    if (!cat) return this.scoped();
    if (cat === SessionLibraryPanelComponent.NO_CATEGORY) {
      return this.scoped().filter((i) => !i.categoryId);
    }
    const ids = categoryWithDescendants(this.categories(), cat);
    return this.scoped().filter((i) => !!i.categoryId && ids.has(i.categoryId));
  });

  /** Compte par famille, après recherche : ce que chaque puce rapporterait si on la cliquait. */
  readonly familyCount = computed<Record<LibraryFamily, number>>(() => {
    const items = this.searched();
    return {
      all: items.length,
      course: items.filter((i) => i.kind === 'course').length,
      strength: items.filter((i) => i.kind === 'strength').length,
      drill: items.filter((i) => i.kind === 'drill').length,
    };
  });

  /**
   * Les catégories qui ont réellement quelque chose à montrer dans le périmètre courant.
   *
   * <p>Proposer les catégories vides ferait un menu plus long que la liste qu'il est censé
   * raccourcir — et chaque choix vide est un aller-retour pour rien. Le compte est celui de la
   * catégorie <b>et de ses descendantes</b> : c'est ce que le choix affichera.</p>
   */
  readonly categoryChoices = computed<CategoryChoice[]>(() => {
    const items = this.scoped();
    if (!items.length) return [];
    const all = this.categories();
    const kinds = this.family() === 'all'
      ? (['course', 'strength', 'drill'] as const)
      : ([this.family()] as ('course' | 'strength' | 'drill')[]);

    const out: CategoryChoice[] = [];
    for (const kind of kinds) {
      const ofKind = items.filter((i) => i.kind === kind);
      if (!ofKind.length) continue;
      const domain = FAMILY_DOMAIN[kind];
      for (const opt of categoryOptions(all.filter((c) => c.domain === domain))) {
        const ids = categoryWithDescendants(all, opt.category.id);
        const count = ofKind.filter((i) => !!i.categoryId && ids.has(i.categoryId)).length;
        if (!count) continue;
        // Une même racine peut exister dans deux arbres (« Force » côté course et côté prépa
        // physique) : sans le préfixe, le menu proposerait deux fois la même ligne.
        const name = opt.depth ? categoryPath(all, opt.category.id) : opt.category.name;
        out.push({
          id: opt.category.id,
          label: kinds.length > 1 ? `${FAMILY_LABEL[kind]} · ${name}` : name,
          count,
        });
      }
    }
    const none = items.filter((i) => !i.categoryId).length;
    if (none) {
      out.push({ id: SessionLibraryPanelComponent.NO_CATEGORY, label: 'Sans catégorie', count: none });
    }
    return out;
  });

  /** Libellé du filtre actif, pour le bandeau qui dit ce qu'on regarde. */
  readonly activeCategoryLabel = computed(() =>
    this.categoryChoices().find((c) => c.id === this.categoryId())?.label ?? '');

  // --- Groupes affichés ------------------------------------------------------

  /**
   * Les accordéons, dans l'ordre des familles puis de l'arbre de catégories. Une sous-catégorie
   * apparaît sous son parent avec son chemin en titre (« Seuil › Seuil long ») ; « Sans
   * catégorie » ferme la marche de chaque famille.
   */
  readonly groups = computed<LibGroup[]>(() => {
    const items = this.visible();
    const all = this.categories();
    const out: LibGroup[] = [];

    for (const kind of ['course', 'strength', 'drill'] as const) {
      const ofKind = items.filter((i) => i.kind === kind);
      if (!ofKind.length) continue;
      const byCat = new Map<string, LibItem[]>();
      for (const i of ofKind) {
        const key = i.categoryId ?? SessionLibraryPanelComponent.NO_CATEGORY;
        byCat.set(key, [...(byCat.get(key) ?? []), i]);
      }
      const domain = FAMILY_DOMAIN[kind];
      for (const opt of categoryOptions(all.filter((c) => c.domain === domain))) {
        const inCat = byCat.get(opt.category.id);
        if (!inCat?.length) continue;
        out.push({
          key: `${kind}:${opt.category.id}`,
          name: opt.depth ? categoryPath(all, opt.category.id) : opt.category.name,
          kind, icon: FAMILY_ICON[kind], items: inCat,
        });
      }
      const none = byCat.get(SessionLibraryPanelComponent.NO_CATEGORY);
      if (none?.length) {
        out.push({
          key: `${kind}:${SessionLibraryPanelComponent.NO_CATEGORY}`,
          // Un groupe unique n'a pas à s'appeler « Sans catégorie » : tant que la famille n'est
          // pas rangée, c'est simplement sa bibliothèque.
          name: byCat.size === 1 ? FAMILY_LABEL[kind] : `${FAMILY_LABEL[kind]} · sans catégorie`,
          kind, icon: FAMILY_ICON[kind], items: none,
        });
      }
    }
    return out;
  });

  /** Favoris (épinglés) visibles — course uniquement, c'est là que l'épingle existe. */
  readonly favorites = computed(() =>
    this.visible().filter((i) => i.kind === 'course' && i.favorite));

  /** Séances les plus utilisées (hors favoris), top 6 — l'accès « je refais la même ». */
  readonly frequent = computed(() =>
    this.visible()
      .filter((i) => i.kind === 'course' && !i.favorite && i.useCount > 0)
      .sort((a, b) => b.useCount - a.useCount)
      .slice(0, 6));

  readonly resultCount = computed(() => this.visible().length);
  readonly totalCount = computed(() => this.allItems().length);

  /** Le panneau est vide parce qu'on a filtré, et non parce que la bibliothèque l'est. */
  readonly filteredToNothing = computed(() => this.filtersActive() && this.resultCount() === 0);

  // --- Émission du clic, selon la famille de l'item --------------------------

  select(item: LibItem): void {
    if (item.kind === 'course') this.courseSelect.emit(item.data as WorkoutTemplate);
    else if (item.kind === 'strength') this.strengthSelect.emit(item.data as StrengthSession);
    else this.drillSelect.emit(item.data as RunDrill);
  }

  toggleFavorite(item: LibItem): void {
    if (item.kind === 'course') this.favoriteToggle.emit(item.data as WorkoutTemplate);
  }

  // --- Repli des accordéons (mémorisé entre sessions) -----------------------
  private static readonly KEY = 'coach-lib-collapsed';
  readonly collapsed = signal<Set<string>>(this.readCollapsed());

  private readCollapsed(): Set<string> {
    try {
      const raw = localStorage.getItem(SessionLibraryPanelComponent.KEY);
      if (raw) return new Set<string>(JSON.parse(raw));
    } catch { /* stockage indisponible : tout déplié par défaut */ }
    return new Set();
  }

  /**
   * Un groupe est replié seulement quand on <b>parcourt</b>.
   *
   * <p>Dès qu'un filtre est posé, tout s'ouvre : chercher « bulgare » dans une catégorie repliée
   * n'affichait que l'en-tête et son compte — la séance trouvée restait cachée derrière le clic
   * qu'on venait précisément d'économiser.</p>
   */
  isCollapsed(key: string): boolean {
    return !this.filtersActive() && this.collapsed().has(key);
  }

  toggleGroup(key: string): void {
    const s = new Set(this.collapsed());
    if (s.has(key)) s.delete(key); else s.add(key);
    this.persistCollapsed(s);
  }

  /** Tout replier : la vue d'ensemble d'une grosse bibliothèque tient alors dans un écran. */
  readonly allCollapsed = computed(() => {
    const keys = this.groups().map((g) => g.key);
    return keys.length > 0 && keys.every((k) => this.collapsed().has(k));
  });

  toggleAll(): void {
    const s = new Set(this.collapsed());
    const keys = this.groups().map((g) => g.key);
    if (this.allCollapsed()) for (const k of keys) s.delete(k);
    else for (const k of keys) s.add(k);
    this.persistCollapsed(s);
  }

  private persistCollapsed(s: Set<string>): void {
    this.collapsed.set(s);
    try { localStorage.setItem(SessionLibraryPanelComponent.KEY, JSON.stringify([...s])); }
    catch { /* préférence non persistée, sans gravité */ }
  }

  /** Drop de retour dans la bibliothèque : aucune action, l'élément reprend sa place. */
  onNoop(): void { /* no-op */ }
}

/** Comparaison de recherche : sans accents ni casse — « fractionne » doit trouver « Fractionné ». */
function normalize(value: string): string {
  return value.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().trim();
}
