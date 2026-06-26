import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TrainingGroup } from '../../core/models/training-group.model';
import { ConfirmService } from '../../core/services/confirm.service';
import { ToastService } from '../../core/services/toast.service';
import { TrainingGroupService } from '../../core/services/training-group.service';
import { EmptyStateComponent, LoaderComponent } from '../../shared/components/ui';

@Component({
  selector: 'app-group-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink, EmptyStateComponent, LoaderComponent],
  templateUrl: './group-list.component.html',
})
export class GroupListComponent implements OnInit {
  private readonly groupService = inject(TrainingGroupService);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);

  readonly groups = signal<TrainingGroup[]>([]);
  readonly loading = signal(true);
  newName = '';

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.groupService.list().subscribe({
      next: (g) => { this.groups.set(g); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }

  create(): void {
    if (!this.newName.trim()) return;
    this.groupService.create(this.newName.trim()).subscribe(() => {
      this.toast.success('Groupe créé.');
      this.newName = '';
      this.load();
    });
  }

  rename(g: TrainingGroup): void {
    this.groupService.rename(g.id, g.name).subscribe(() => this.toast.success('Groupe mis à jour.'));
  }

  async remove(g: TrainingGroup): Promise<void> {
    const ok = await this.confirm.ask({
      title: 'Supprimer le groupe',
      message: `Supprimer « ${g.name} » ? Les athlètes ne seront pas supprimés (juste détachés).`,
      confirmLabel: 'Supprimer', danger: true,
    });
    if (!ok) return;
    this.groupService.delete(g.id).subscribe(() => { this.toast.success('Groupe supprimé.'); this.load(); });
  }
}
