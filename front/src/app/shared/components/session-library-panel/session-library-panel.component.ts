import { DragDropModule } from '@angular/cdk/drag-drop';
import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { IconComponent } from '../icon/icon.component';
import { WorkoutTemplate } from '../../../core/models/workout-template.model';
import { StrengthSession } from '../../../core/models/strength.model';
import { RunDrill } from '../../../core/models/run-drill.model';
import { SessionCategory } from '../../../core/models/session-category.model';

/** Groupe de séances course sous une catégorie (accordéon). */
interface CourseGroup {
  id: string;
  name: string;
  items: WorkoutTemplate[];
}

/**
 * Panneau bibliothèque réutilisable (colonne gauche du calendrier + future page `/library`).
 * Course rangé en accordéons par catégorie personnalisée ; force et éducatifs en groupes dédiés.
 * Recherche instantanée transverse ; repli d'accordéon mémorisé. Chaque item est draggable
 * (`cdkDrag` + `[cdkDragData]`) et participe au `cdkDropListGroup` du composant hôte.
 */
@Component({
  selector: 'app-session-library-panel',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, DragDropModule, IconComponent],
  templateUrl: './session-library-panel.component.html',
  styleUrl: './session-library-panel.component.scss',
})
export class SessionLibraryPanelComponent {
  readonly courseTemplates = input<WorkoutTemplate[]>([]);
  readonly strengthSessions = input<StrengthSession[]>([]);
  readonly drills = input<RunDrill[]>([]);
  readonly categories = input<SessionCategory[]>([]);

  /** Émis au clic sur une séance course (consultation). Non lié dans le calendrier (glisser-déposer). */
  readonly courseSelect = output<WorkoutTemplate>();
  /** Émis au clic sur l'étoile (épingler / dé-épingler un favori). */
  readonly favoriteToggle = output<WorkoutTemplate>();

  /** Favoris (épinglés) qui matchent la recherche — remontés en tête. */
  readonly favorites = computed(() =>
    this.courseTemplates().filter((t) => t.favorite && this.matches(t.name)));

  /** Séances les plus utilisées (hors favoris), top 6 — accès rapide « récents/fréquents ». */
  readonly frequent = computed(() =>
    this.courseTemplates()
      .filter((t) => !t.favorite && (t.useCount ?? 0) > 0 && this.matches(t.name))
      .sort((a, b) => (b.useCount ?? 0) - (a.useCount ?? 0))
      .slice(0, 6));

  /** Identifiants de groupe réservés (course sans catégorie, force, éducatifs). */
  static readonly UNCATEGORIZED = '__none__';
  static readonly STRENGTH = '__strength__';
  static readonly DRILLS = '__drills__';
  readonly STRENGTH_ID = SessionLibraryPanelComponent.STRENGTH;
  readonly DRILLS_ID = SessionLibraryPanelComponent.DRILLS;

  readonly search = signal('');

  private matches(name: string): boolean {
    const q = this.search().trim().toLowerCase();
    return !q || name.toLowerCase().includes(q);
  }

  /** Course groupé par catégorie (dans l'ordre des catégories), « Sans catégorie » en dernier. */
  readonly courseGroups = computed<CourseGroup[]>(() => {
    const templates = this.courseTemplates().filter((t) => this.matches(t.name));
    const byCat = new Map<string, WorkoutTemplate[]>();
    for (const t of templates) {
      const key = t.categoryId ?? SessionLibraryPanelComponent.UNCATEGORIZED;
      const arr = byCat.get(key) ?? byCat.set(key, []).get(key)!;
      arr.push(t);
    }
    const groups: CourseGroup[] = [];
    for (const c of this.categories()) {
      const items = byCat.get(c.id);
      if (items?.length) groups.push({ id: c.id, name: c.name, items });
    }
    const none = byCat.get(SessionLibraryPanelComponent.UNCATEGORIZED);
    if (none?.length) groups.push({ id: SessionLibraryPanelComponent.UNCATEGORIZED, name: 'Sans catégorie', items: none });
    return groups;
  });

  readonly filteredStrength = computed(() => this.strengthSessions().filter((s) => this.matches(s.name)));
  readonly filteredDrills = computed(() => this.drills().filter((d) => this.matches(d.name)));

  readonly resultCount = computed(() =>
    this.courseGroups().reduce((n, g) => n + g.items.length, 0)
    + this.filteredStrength().length + this.filteredDrills().length);

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

  isCollapsed(id: string): boolean { return this.collapsed().has(id); }

  toggleGroup(id: string): void {
    const s = new Set(this.collapsed());
    if (s.has(id)) s.delete(id); else s.add(id);
    this.collapsed.set(s);
    try { localStorage.setItem(SessionLibraryPanelComponent.KEY, JSON.stringify([...s])); }
    catch { /* préférence non persistée, sans gravité */ }
  }

  /** Drop de retour dans la bibliothèque : aucune action, l'élément reprend sa place. */
  onNoop(): void { /* no-op */ }
}
