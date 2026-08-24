import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { FormsModule } from '@angular/forms';
import { AthleteService } from '../../core/services/athlete.service';
import { AuthService } from '../../core/services/auth.service';
import { ClubMember, ClubRole, ClubService } from '../../core/services/club.service';
import { AthleteAccessPanelComponent } from '../../shared/components/athlete-access/athlete-access-panel.component';
import { ToastService } from '../../core/services/toast.service';
import { AthleteSummary } from '../../core/models/athlete.model';
import { WorkoutTemplateService } from '../../core/services/workout-template.service';
import { StrengthService } from '../../core/services/strength.service';
import { WorkoutTemplate } from '../../core/models/workout-template.model';
import { StrengthSession } from '../../core/models/strength.model';
import { SkeletonComponent } from '../../shared/components/skeleton/skeleton.component';

/** Écran Club (s-club) interactif : coachs, rôles, statut privé/club et permissions graduées. */
@Component({
  selector: 'app-club',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [SkeletonComponent, IconComponent, FormsModule, AthleteAccessPanelComponent],
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
  /** Coach dont l'invitation est en cours de renvoi (un seul à la fois). */
  readonly resending = signal<string | null>(null);

  ngOnInit(): void {
    this.clubService.members().subscribe({
      next: (m) => { this.members.set(m); this.loadingMembers.set(false); },
      error: () => this.loadingMembers.set(false),
    });
    this.athletes.list({ status: 'ACTIVE' }).subscribe((p) => this.athleteList.set(p.content));
    this.templateService.listAll().subscribe((t) => this.courseTemplates.set(t));
    this.strengthService.listAllSessions().subscribe((s) => this.strengthSessions.set(s));
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

  /**
   * Renvoie l'invitation d'un coach resté « en attente ». L'e-mail se perd, le lien expire au bout
   * de quatorze jours, et il n'existait aucun moyen de le renvoyer : l'adresse étant déjà connue,
   * « Ajouter / inviter » répondait « déjà membre du club ».
   */
  resendInvite(m: ClubMember): void {
    if (this.resending()) return;
    this.resending.set(m.coachId);
    this.lastInviteUrl.set(null);
    this.clubService.resendInvite(m.coachId).subscribe({
      next: (r) => {
        this.resending.set(null);
        this.lastInviteUrl.set(r.inviteUrl);
        this.toast.success(`Invitation renvoyée à ${r.name}`);
      },
      error: (e) => {
        this.resending.set(null);
        this.toast.warning(e?.error?.message ?? 'Renvoi impossible.');
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
  }
}
