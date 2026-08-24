import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { GroupVisibility, TrainingGroup } from '../../core/models/training-group.model';
import { ClubMember, ClubService } from '../../core/services/club.service';
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
  private readonly clubService = inject(ClubService);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);

  readonly groups = signal<TrainingGroup[]>([]);
  readonly loading = signal(true);
  /** Coachs du club : ceux qu'on peut convier dans un groupe privé. */
  readonly members = signal<ClubMember[]>([]);
  /** Groupe dont on règle les invitations (un seul panneau ouvert à la fois). */
  readonly sharingId = signal<string | null>(null);
  newName = '';
  newVisibility: GroupVisibility = 'CLUB';

  ngOnInit(): void {
    this.load();
    this.clubService.members().subscribe({
      next: (m) => this.members.set(m),
      error: () => this.members.set([]),
    });
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
    this.groupService.create(this.newName.trim(), this.newVisibility).subscribe(() => {
      this.toast.success(this.newVisibility === 'PRIVATE'
        ? 'Groupe privé créé — toi seul le vois.'
        : 'Groupe créé.');
      this.newName = '';
      this.newVisibility = 'CLUB';
      this.load();
    });
  }

  /** Enregistre l'état voulu du groupe : nom, visibilité et coachs conviés d'un seul tenant. */
  save(g: TrainingGroup): void {
    this.groupService.save(g.id, g.name, g.visibility, g.invitedCoachIds)
      .subscribe(() => this.toast.success('Groupe mis à jour.'));
  }

  /** Ouvre ou referme un groupe. Refermer ne retire aucun athlète : cela change qui le voit. */
  toggleVisibility(g: TrainingGroup): void {
    g.visibility = g.visibility === 'PRIVATE' ? 'CLUB' : 'PRIVATE';
    this.save(g);
  }

  toggleInvited(g: TrainingGroup, coachId: string): void {
    g.invitedCoachIds = g.invitedCoachIds.includes(coachId)
      ? g.invitedCoachIds.filter((id) => id !== coachId)
      : [...g.invitedCoachIds, coachId];
    this.save(g);
  }

  /** Les coachs conviables : tous sauf le créateur du groupe, qui le voit déjà. */
  invitable(g: TrainingGroup): ClubMember[] {
    return this.members().filter((m) => m.coachId !== g.ownerCoachId);
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
