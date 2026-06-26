import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { FormsModule } from '@angular/forms';
import { AthleteService } from '../../core/services/athlete.service';
import { AuthService } from '../../core/services/auth.service';
import {
  AthleteAccess,
  ClubMember,
  ClubRole,
  ClubService,
  PermissionLevel,
} from '../../core/services/club.service';
import { ToastService } from '../../core/services/toast.service';
import { AthleteSummary } from '../../core/models/athlete.model';
import { WorkoutTemplateService } from '../../core/services/workout-template.service';
import { StrengthService } from '../../core/services/strength.service';
import { WorkoutTemplate } from '../../core/models/workout-template.model';
import { StrengthSession } from '../../core/models/strength.model';

/** Écran Club (s-club) interactif : coachs, rôles, statut privé/club et permissions graduées. */
@Component({
  selector: 'app-club',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, FormsModule],
  templateUrl: './club.component.html',
  styleUrl: './club.component.scss',
})
export class ClubComponent implements OnInit {
  private readonly clubService = inject(ClubService);
  private readonly athletes = inject(AthleteService);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);
  private readonly templateService = inject(WorkoutTemplateService);
  private readonly strengthService = inject(StrengthService);

  // Bibliothèques partagées (scopées club → accessibles à tous les coachs du club).
  readonly courseTemplates = signal<WorkoutTemplate[]>([]);
  readonly strengthSessions = signal<StrengthSession[]>([]);

  readonly user = this.auth.currentUser;
  readonly members = signal<ClubMember[]>([]);
  readonly loadingMembers = signal(true);
  readonly athleteList = signal<AthleteSummary[]>([]);
  readonly selectedAthlete = signal('');
  readonly access = signal<AthleteAccess | null>(null);

  readonly levels: PermissionLevel[] = ['READ', 'COMMENT', 'WRITE'];
  readonly levelLabels: Record<PermissionLevel, string> = { READ: 'Lecture', COMMENT: 'Commentaire', WRITE: 'Écriture' };
  readonly roleLabels: Record<string, string> = {
    OWNER: 'Owner', COACH_PRINCIPAL: 'Coach principal', COACH_ASSISTANT: 'Coach assistant',
  };

  // Ajout / invitation d'un coach au club.
  newCoachEmail = '';
  newCoachName = '';
  newCoachRole: ClubRole = 'COACH_ASSISTANT';
  readonly addableRoles: ClubRole[] = ['COACH_PRINCIPAL', 'COACH_ASSISTANT'];
  readonly addingCoach = signal(false);
  readonly lastInviteUrl = signal<string | null>(null);

  ngOnInit(): void {
    this.clubService.members().subscribe({
      next: (m) => { this.members.set(m); this.loadingMembers.set(false); },
      error: () => this.loadingMembers.set(false),
    });
    this.athletes.list({ status: 'ACTIVE' }).subscribe((p) => this.athleteList.set(p.content));
    this.templateService.list().subscribe((p) => this.courseTemplates.set(p.content));
    this.strengthService.listSessions().subscribe((p) => this.strengthSessions.set(p.content));
  }

  addCoach(): void {
    const email = this.newCoachEmail.trim();
    if (!email || this.addingCoach()) return;
    this.addingCoach.set(true);
    this.lastInviteUrl.set(null);
    this.clubService.addCoach(email, this.newCoachRole, this.newCoachName.trim() || undefined).subscribe({
      next: (r) => {
        this.addingCoach.set(false);
        this.newCoachEmail = '';
        this.newCoachName = '';
        if (r.invited) {
          this.lastInviteUrl.set(r.inviteUrl);
          this.toast.success(`${r.name} invité·e — lien d'activation envoyé par e-mail`);
        } else {
          this.toast.success(`${r.name} ajouté au club`);
        }
        // Rafraîchir la liste (statut en attente, rôle).
        this.clubService.members().subscribe((m) => this.members.set(m));
      },
      error: (e) => {
        this.addingCoach.set(false);
        this.toast.warning(e?.error?.message ?? 'Ajout impossible (déjà membre, ou compte non-coach).');
      },
    });
  }

  removeMember(m: ClubMember): void {
    if (m.clubRole === 'OWNER') return;
    this.clubService.removeCoach(m.coachId).subscribe({
      next: () => {
        this.members.update((list) => list.filter((x) => x.coachId !== m.coachId));
        this.toast.info(`${m.name} retiré du club.`);
      },
      error: () => this.toast.error('Retrait impossible.'),
    });
  }

  onAthleteChange(id: string): void {
    this.selectedAthlete.set(id);
    this.access.set(null);
    if (id) this.clubService.access(id).subscribe((a) => this.access.set(a));
  }

  toggleOwnership(): void {
    const id = this.selectedAthlete();
    const a = this.access();
    if (!id || !a) return;
    const next = a.ownership === 'CLUB' ? 'PRIVATE' : 'CLUB';
    this.clubService.setOwnership(id, next).subscribe({
      next: (res) => { this.access.set(res); this.toast.success(`Athlète ${next === 'CLUB' ? 'rattaché au club' : 'passé en privé'}`); },
      error: () => this.toast.warning('Impossible : des permissions actives existent.'),
    });
  }

  grant(coachId: string, level: PermissionLevel): void {
    const id = this.selectedAthlete();
    if (!id) return;
    this.clubService.grant(id, coachId, level).subscribe({
      next: (res) => { this.access.set(res); this.toast.success('Permission accordée'); },
      error: () => this.toast.warning('Athlète privé : permission impossible.'),
    });
  }

  revoke(coachId: string): void {
    const id = this.selectedAthlete();
    if (!id) return;
    this.clubService.revoke(id, coachId).subscribe((res) => { this.access.set(res); this.toast.info('Permission retirée.'); });
  }

  permFor(coachId: string): PermissionLevel | null {
    return this.access()?.permissions.find((p) => p.coachId === coachId)?.permission ?? null;
  }

  isReferent(coachId: string): boolean {
    return this.access()?.referentCoachId === coachId;
  }
}
