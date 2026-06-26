import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AthleteSummary } from '../../core/models/athlete.model';
import { PlanItem, PlanItemKind, TrainingPlan } from '../../core/models/training-plan.model';
import { TrainingGroup } from '../../core/models/training-group.model';
import { WorkoutTemplate } from '../../core/models/workout-template.model';
import { StrengthSession } from '../../core/models/strength.model';
import { MesocycleTemplate } from '../../core/models/mesocycle-template.model';
import { AthleteService } from '../../core/services/athlete.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { MesocycleTemplateService } from '../../core/services/mesocycle-template.service';
import { StrengthService } from '../../core/services/strength.service';
import { ToastService } from '../../core/services/toast.service';
import { TrainingGroupService } from '../../core/services/training-group.service';
import { TrainingPlanService } from '../../core/services/training-plan.service';
import { WorkoutTemplateService } from '../../core/services/workout-template.service';
import { EmptyStateComponent, LoaderComponent } from '../../shared/components/ui';
import { IconComponent } from '../../shared/components/icon/icon.component';

const DAYS = ['', 'Lun', 'Mar', 'Mer', 'Jeu', 'Ven', 'Sam', 'Dim'];

@Component({
  selector: 'app-plan-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, EmptyStateComponent, LoaderComponent, IconComponent],
  templateUrl: './plan-list.component.html',
  styleUrl: './plan-list.component.scss',
})
export class PlanListComponent implements OnInit {
  private readonly planService = inject(TrainingPlanService);
  private readonly templateService = inject(WorkoutTemplateService);
  private readonly athleteService = inject(AthleteService);
  private readonly groupService = inject(TrainingGroupService);
  private readonly strengthService = inject(StrengthService);
  private readonly mesocycleService = inject(MesocycleTemplateService);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);

  readonly days = DAYS;
  readonly plans = signal<TrainingPlan[]>([]);
  readonly templates = signal<WorkoutTemplate[]>([]);
  readonly strengthSessions = signal<StrengthSession[]>([]);
  readonly mesocycles = signal<MesocycleTemplate[]>([]);
  readonly athletes = signal<AthleteSummary[]>([]);
  readonly groups = signal<TrainingGroup[]>([]);
  readonly loading = signal(true);
  readonly showForm = signal(false);
  /** Plan en cours d'édition (null = création). */
  readonly editingId = signal<string | null>(null);

  draft: { name: string; description: string; durationWeeks: number; mesocycleTemplateId: string; items: PlanItem[] } = {
    name: '', description: '', durationWeeks: 4, mesocycleTemplateId: '', items: [],
  };
  itemDraft: { weekIndex: number; dayOfWeek: number; kind: PlanItemKind; templateId: string } =
    { weekIndex: 0, dayOfWeek: 1, kind: 'COURSE', templateId: '' };
  applyFor: Record<string, { target: 'athlete' | 'group'; athleteId: string; groupId: string; startDate: string }> = {};

  ngOnInit(): void {
    this.load();
    this.templateService.list(undefined, 0).subscribe((p) => this.templates.set(p.content));
    this.strengthService.listSessions(undefined, 0).subscribe((p) => this.strengthSessions.set(p.content));
    this.mesocycleService.list().subscribe((m) => this.mesocycles.set(m));
    this.athleteService.list({ status: 'ACTIVE' }).subscribe((p) => this.athletes.set(p.content));
    this.groupService.list().subscribe((g) => this.groups.set(g));
  }

  /** Bascule de nature : réinitialise la sélection de modèle. */
  onKindChange(): void {
    this.itemDraft.templateId = '';
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

  /** Nom d'un item selon sa nature (modèle course ou séance de force). */
  itemName(item: PlanItem): string {
    if (item.kind === 'STRENGTH') {
      return this.strengthSessions().find((s) => s.id === item.templateId)?.name ?? '?';
    }
    return this.templates().find((t) => t.id === item.templateId)?.name ?? '?';
  }

  /** Nom du mésocycle porté par un plan (badge « charge progressive »). */
  mesoName(id: string | null | undefined): string | null {
    if (!id) return null;
    return this.mesocycles().find((m) => m.id === id)?.name ?? 'Mésocycle';
  }

  /** Items planifiés sur une cellule (semaine 0-based × jour 1..7) — aperçu grille. */
  cellItems(weekIndex: number, dayOfWeek: number): PlanItem[] {
    return this.draft.items.filter((it) => it.weekIndex === weekIndex && it.dayOfWeek === dayOfWeek);
  }

  /** Bornes de semaines pour l'itération de la grille (0..durationWeeks-1). */
  weekIndexes(): number[] {
    return Array.from({ length: Math.max(1, this.draft.durationWeeks) }, (_, i) => i);
  }

  addItem(): void {
    if (!this.itemDraft.templateId) { this.toast.warning('Choisissez une séance.'); return; }
    this.draft.items = [...this.draft.items, { ...this.itemDraft }];
  }
  removeItem(i: number): void {
    this.draft.items = this.draft.items.filter((_, idx) => idx !== i);
  }
  /** Retire un item depuis la grille (par semaine/jour/modèle). */
  removeCellItem(item: PlanItem): void {
    const i = this.draft.items.indexOf(item);
    if (i >= 0) this.removeItem(i);
  }

  newPlan(): void {
    this.resetForm();
    this.showForm.set(true);
  }

  edit(plan: TrainingPlan): void {
    this.editingId.set(plan.id);
    this.draft = {
      name: plan.name,
      description: plan.description ?? '',
      durationWeeks: plan.durationWeeks,
      mesocycleTemplateId: plan.mesocycleTemplateId ?? '',
      items: plan.items.map((it) => ({
        weekIndex: it.weekIndex, dayOfWeek: it.dayOfWeek,
        kind: it.kind ?? 'COURSE', templateId: it.templateId,
      })),
    };
    this.showForm.set(true);
  }

  cancelForm(): void {
    this.resetForm();
    this.showForm.set(false);
  }

  private resetForm(): void {
    this.editingId.set(null);
    this.draft = { name: '', description: '', durationWeeks: 4, mesocycleTemplateId: '', items: [] };
    this.itemDraft = { weekIndex: 0, dayOfWeek: 1, kind: 'COURSE', templateId: '' };
  }

  save(): void {
    if (!this.draft.name || this.draft.items.length === 0) {
      this.toast.warning('Nom et au moins un item requis.');
      return;
    }
    const payload = { ...this.draft, mesocycleTemplateId: this.draft.mesocycleTemplateId || null };
    const id = this.editingId();
    const op = id ? this.planService.update(id, payload) : this.planService.create(payload);
    op.subscribe(() => {
      this.toast.success(id ? 'Plan mis à jour' : 'Plan créé');
      this.resetForm();
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
