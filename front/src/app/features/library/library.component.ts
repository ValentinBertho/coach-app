import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { SessionLibraryPanelComponent } from '../../shared/components/session-library-panel/session-library-panel.component';
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
  imports: [SessionLibraryPanelComponent],
  template: `
    <section class="page-header">
      <div>
        <h1 class="display-sm">Bibliothèque</h1>
        <p class="subtitle">Toutes vos séances course, prépa physique et éducatifs, regroupés par catégorie.</p>
      </div>
    </section>

    <div class="card library-card">
      <app-session-library-panel
        [courseTemplates]="courseTemplates()"
        [strengthSessions]="strengthSessions()"
        [drills]="drills()"
        [categories]="categories()" />
    </div>
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

  ngOnInit(): void {
    this.templateService.list().subscribe((p) => this.courseTemplates.set(p.content));
    this.strengthService.listSessions().subscribe((p) => this.strengthSessions.set(p.content));
    this.drillService.list().subscribe((d) => this.drills.set(d));
    this.categoryService.list().subscribe({ next: (c) => this.categories.set(c), error: () => this.categories.set([]) });
  }
}
