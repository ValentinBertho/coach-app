import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { SessionLibraryPanelComponent } from '../../shared/components/session-library-panel/session-library-panel.component';
import { SessionDetailModalComponent } from '../../shared/components/session-detail-modal/session-detail-modal.component';
import { WorkoutTemplateService } from '../../core/services/workout-template.service';
import { StrengthService } from '../../core/services/strength.service';
import { RunDrillService } from '../../core/services/run-drill.service';
import { SessionCategoryService } from '../../core/services/session-category.service';
import { WorkoutTemplate } from '../../core/models/workout-template.model';
import { StrengthSession } from '../../core/models/strength.model';
import { RunDrill } from '../../core/models/run-drill.model';
import { SessionCategory } from '../../core/models/session-category.model';

/**
 * Bibliothèque unifiée (course · prépa physique · éducatifs), réutilisant le panneau partagé
 * `<app-session-library-panel>` du calendrier — vue de consultation/recherche (résidu QA2).
 */
@Component({
  selector: 'app-library',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [SessionLibraryPanelComponent, SessionDetailModalComponent],
  template: `
    <section class="page-header">
      <div>
        <h1 class="display-sm">Bibliothèque</h1>
        <p class="subtitle">Toutes vos séances course, prépa physique et éducatifs, regroupés par catégorie. Cliquez une séance course pour la consulter.</p>
      </div>
    </section>

    <div class="card library-card">
      <app-session-library-panel
        [courseTemplates]="courseTemplates()"
        [strengthSessions]="strengthSessions()"
        [drills]="drills()"
        [categories]="categories()"
        (courseSelect)="detail.set({ id: $event.id, name: $event.name })"
        (favoriteToggle)="toggleFavorite($event)" />
    </div>

    @if (detail(); as d) {
      <app-session-detail-modal [templateId]="d.id" [name]="d.name"
                               [categories]="categories()" [categoryId]="detailCategoryId()"
                               (categoryChange)="assignCategory(d.id, $event)"
                               (closed)="detail.set(null)" />
    }
  `,
  styles: [`
    .library-card { padding: var(--sp-3); }
  `],
})
export class LibraryComponent implements OnInit {
  private readonly templateService = inject(WorkoutTemplateService);
  private readonly strengthService = inject(StrengthService);
  private readonly drillService = inject(RunDrillService);
  private readonly categoryService = inject(SessionCategoryService);

  readonly courseTemplates = signal<WorkoutTemplate[]>([]);
  readonly strengthSessions = signal<StrengthSession[]>([]);
  readonly drills = signal<RunDrill[]>([]);
  readonly categories = signal<SessionCategory[]>([]);
  /** Séance course dont on consulte le détail (modale). */
  readonly detail = signal<{ id: string; name: string } | null>(null);

  ngOnInit(): void {
    this.templateService.list().subscribe((p) => this.courseTemplates.set(p.content));
    this.strengthService.listSessions().subscribe((p) => this.strengthSessions.set(p.content));
    this.drillService.list().subscribe((d) => this.drills.set(d));
    this.categoryService.list().subscribe({ next: (c) => this.categories.set(c), error: () => this.categories.set([]) });
  }

  /** Catégorie courante de la séance consultée (menu déroulant de la modale). */
  detailCategoryId(): string {
    const d = this.detail();
    return (d && this.courseTemplates().find((x) => x.id === d.id)?.categoryId) || '';
  }

  /** (Ré)affecte la catégorie d'une séance depuis la modale de consultation. */
  assignCategory(templateId: string, categoryId: string): void {
    const t = this.courseTemplates().find((x) => x.id === templateId);
    if (!t) return;
    this.templateService.update(t.id, {
      name: t.name, type: t.type, title: t.title, notes: t.notes,
      targetDistanceM: t.targetDistanceM, targetDurationS: t.targetDurationS,
      categoryId: categoryId || null, steps: t.steps,
    }).subscribe({
      next: (updated) => this.courseTemplates.update((l) => l.map((x) => (x.id === t.id ? updated : x))),
    });
  }

  /** Épingle / dé-épingle (optimiste) une séance course. */
  toggleFavorite(t: WorkoutTemplate): void {
    const next = !t.favorite;
    this.courseTemplates.update((l) => l.map((x) => (x.id === t.id ? { ...x, favorite: next } : x)));
    this.templateService.setFavorite(t.id, next).subscribe({
      next: (updated) => this.courseTemplates.update((l) => l.map((x) => (x.id === t.id ? updated : x))),
      error: () => this.courseTemplates.update((l) => l.map((x) => (x.id === t.id ? { ...x, favorite: !next } : x))),
    });
  }
}
