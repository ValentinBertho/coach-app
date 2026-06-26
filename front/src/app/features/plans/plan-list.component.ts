import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AthleteSummary } from '../../core/models/athlete.model';
import { PlanItem, TrainingPlan } from '../../core/models/training-plan.model';
import { TrainingGroup } from '../../core/models/training-group.model';
import { WorkoutTemplate } from '../../core/models/workout-template.model';
import { AthleteService } from '../../core/services/athlete.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { ToastService } from '../../core/services/toast.service';
import { TrainingGroupService } from '../../core/services/training-group.service';
import { TrainingPlanService } from '../../core/services/training-plan.service';
import { WorkoutTemplateService } from '../../core/services/workout-template.service';
import { EmptyStateComponent, LoaderComponent } from '../../shared/components/ui';

const DAYS = ['', 'Lun', 'Mar', 'Mer', 'Jeu', 'Ven', 'Sam', 'Dim'];

@Component({
  selector: 'app-plan-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, EmptyStateComponent, LoaderComponent],
  templateUrl: './plan-list.component.html',
  styleUrl: './plan-list.component.scss',
})
export class PlanListComponent implements OnInit {
  private readonly planService = inject(TrainingPlanService);
  private readonly templateService = inject(WorkoutTemplateService);
  private readonly athleteService = inject(AthleteService);
  private readonly groupService = inject(TrainingGroupService);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);

  readonly days = DAYS;
  readonly plans = signal<TrainingPlan[]>([]);
  readonly templates = signal<WorkoutTemplate[]>([]);
  readonly athletes = signal<AthleteSummary[]>([]);
  readonly groups = signal<TrainingGroup[]>([]);
  readonly loading = signal(true);
  readonly showForm = signal(false);

  draft: { name: string; description: string; durationWeeks: number; items: PlanItem[] } = {
    name: '', description: '', durationWeeks: 4, items: [],
  };
  itemDraft = { weekIndex: 0, dayOfWeek: 1, templateId: '' };
  applyFor: Record<string, { target: 'athlete' | 'group'; athleteId: string; groupId: string; startDate: string }> = {};

  ngOnInit(): void {
    this.load();
    this.templateService.list(undefined, 0).subscribe((p) => this.templates.set(p.content));
    this.athleteService.list({ status: 'ACTIVE' }).subscribe((p) => this.athletes.set(p.content));
    this.groupService.list().subscribe((g) => this.groups.set(g));
  }

  load(): void {
    this.loading.set(true);
    this.planService.list().subscribe({
      next: (p) => {
        this.plans.set(p);
        p.forEach((pl) => (this.applyFor[pl.id] ??= { target: 'athlete', athleteId: '', groupId: '', startDate: '' }));
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  templateName(id: string): string {
    return this.templates().find((t) => t.id === id)?.name ?? '?';
  }

  addItem(): void {
    if (!this.itemDraft.templateId) { this.toast.warning('Choisissez un modèle.'); return; }
    this.draft.items = [...this.draft.items, { ...this.itemDraft }];
  }
  removeItem(i: number): void {
    this.draft.items = this.draft.items.filter((_, idx) => idx !== i);
  }

  save(): void {
    if (!this.draft.name || this.draft.items.length === 0) {
      this.toast.warning('Nom et au moins un item requis.');
      return;
    }
    this.planService.create(this.draft).subscribe(() => {
      this.toast.success('Plan créé');
      this.draft = { name: '', description: '', durationWeeks: 4, items: [] };
      this.showForm.set(false);
      this.load();
    });
  }

  apply(plan: TrainingPlan): void {
    const sel = this.applyFor[plan.id];
    if (!sel?.startDate) { this.toast.warning('Date de départ requise.'); return; }
    if (sel.target === 'group') {
      if (!sel.groupId) { this.toast.warning('Choisissez un groupe.'); return; }
      this.planService.applyGroup(plan.id, sel.groupId, sel.startDate).subscribe((r) => {
        const skip = r.skipped ? `, ${r.skipped} ignoré(s)` : '';
        this.toast.success(`${r.created} séance(s) sur ${r.athletes} athlète(s)${skip}`);
        this.applyFor[plan.id] = { target: 'group', athleteId: '', groupId: '', startDate: '' };
        this.load();
      });
      return;
    }
    if (!sel.athleteId) { this.toast.warning('Choisissez un athlète.'); return; }
    this.planService.apply(plan.id, sel.athleteId, sel.startDate).subscribe((r) => {
      this.toast.success(`${r.created} séance(s) planifiée(s)`);
      this.applyFor[plan.id] = { target: 'athlete', athleteId: '', groupId: '', startDate: '' };
      this.load();
    });
  }

  async remove(plan: TrainingPlan): Promise<void> {
    const ok = await this.confirm.ask({ title: 'Supprimer le plan', message: `Supprimer « ${plan.name} » ?`, confirmLabel: 'Supprimer', danger: true });
    if (!ok) return;
    this.planService.delete(plan.id).subscribe(() => { this.toast.success('Supprimé.'); this.load(); });
  }
}
