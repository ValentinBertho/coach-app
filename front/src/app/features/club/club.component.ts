import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { AthleteService } from '../../core/services/athlete.service';
import { AuthService } from '../../core/services/auth.service';
import {
  ClubMember,
  ClubRole,
  ClubService,
} from '../../core/services/club.service';
import { AthleteAccessPanelComponent } from '../../shared/components/athlete-access/athlete-access-panel.component';
import { ToastService } from '../../core/services/toast.service';
import { DepartureImpact } from '../../core/services/club.service';
import { AthleteSummary } from '../../core/models/athlete.model';
import { WorkoutTemplateService } from '../../core/services/workout-template.service';
import { StrengthService } from '../../core/services/strength.service';
import { WorkoutTemplate } from '../../core/models/workout-template.model';
import { StrengthSession } from '../../core/models/strength.model';
import { SkeletonComponent } from '../../shared/components/skeleton/skeleton.component';

/** Écran Club (s-club) interactif : coachs, rôles, statut privé/club et permissions graduées. */
@Component({
  selector: 'app-club',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    SkeletonComponent,
    IconComponent,
    FormsModule,
    AthleteAccessPanelComponent,
    RouterLink,
  ],
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
    OWNER: 'Responsable du club',
    COACH_PRINCIPAL: 'Coach principal',
    COACH_ASSISTANT: 'Coach assistant',
  };

  // Ajout / invitation d'un coach au club.
  newCoachEmail = '';
  newCoachName = '';
  newCoachRole: ClubRole = 'COACH_ASSISTANT';
  readonly addableRoles: ClubRole[] = ['COACH_PRINCIPAL', 'COACH_ASSISTANT'];
  readonly addingCoach = signal(false);
  readonly addCoachOpen = signal(false);
  readonly lastInviteUrl = signal<string | null>(null);
  /** Coach dont l'invitation est en cours de renvoi (un seul à la fois). */
  readonly resending = signal<string | null>(null);

  ngOnInit(): void {
    this.clubService.members().subscribe({
      next: (m) => {
        this.members.set(m);
        this.loadingMembers.set(false);
      },
      error: () => this.loadingMembers.set(false),
    });
    this.athletes
      .list({ status: 'ACTIVE' })
      .subscribe((p) => this.athleteList.set(p.content));
    this.templateService
      .listAll()
      .subscribe((t) => this.courseTemplates.set(t));
    this.strengthService
      .listAllSessions()
      .subscribe((s) => this.strengthSessions.set(s));
  }

  addCoach(): void {
    const email = this.newCoachEmail.trim();
    if (!email || this.addingCoach()) return;
    this.addingCoach.set(true);
    this.lastInviteUrl.set(null);
    this.clubService
      .addCoach(email, this.newCoachRole, this.newCoachName.trim() || undefined)
      .subscribe({
        next: (r) => {
          this.addingCoach.set(false);
          this.newCoachEmail = '';
          this.newCoachName = '';
          if (r.invited) {
            this.lastInviteUrl.set(r.inviteUrl);
            this.toast.success(
              `${r.name} invité·e — lien d'activation envoyé par e-mail`,
            );
          } else {
            this.toast.success(`${r.name} ajouté au club`);
          }
          // Rafraîchir la liste (statut en attente, rôle).
          this.clubService.members().subscribe((m) => this.members.set(m));
        },
        error: (e) => {
          this.addingCoach.set(false);
          this.toast.warning(
            e?.error?.message ??
              'Ajout impossible (déjà membre, ou compte non-coach).',
          );
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

  /** Le coach connecté : « Quitter le club » sur sa ligne, « Retirer » sur les autres s'il est propriétaire. */
  readonly meId = computed(() => this.auth.currentUser()?.id ?? null);
  readonly iAmOwner = computed(() =>
    this.members().some((m) => m.coachId === this.meId() && m.clubRole === 'OWNER'));
  /** Départ en cours de préparation : on montre ce qu'il produira avant de le confirmer. */
  readonly departure = signal<{ member: ClubMember; impact: DepartureImpact; self: boolean } | null>(null);
  successorId: string | null = null;
  readonly leaving = signal(false);

  removeMember(m: ClubMember): void {
    if (m.clubRole === 'OWNER') return;
    this.clubService.departureImpact(m.coachId).subscribe({
      next: (impact) => {
        this.successorId = impact.successors.find((s) => s.owner)?.coachId ?? impact.successors[0]?.coachId ?? null;
        this.departure.set({ member: m, impact, self: m.coachId === this.meId() });
      },
      error: () => this.toast.error('Impossible de préparer ce départ.'),
    });
  }

  names(list: { name: string }[]): string {
    return list.map((p) => p.name).join(', ');
  }

  confirmDeparture(): void {
    const d = this.departure();
    if (!d) return;
    this.leaving.set(true);
    const call = d.self
      ? this.clubService.leaveClub(this.successorId)
      : this.clubService.removeCoach(d.member.coachId, this.successorId);
    call.subscribe({
      next: () => {
        this.leaving.set(false);
        this.departure.set(null);
        if (d.self) {
          // Plus de club dans la session : on se reconnecte dans son espace.
          this.toast.info('Vous avez quitté le club. Reconnectez-vous pour ouvrir votre espace.');
          this.auth.logout();
          return;
        }
        this.members.update((list) => list.filter((x) => x.coachId !== d.member.coachId));
        this.toast.info(`${d.member.name} a quitté le club.`);
      },
      error: (err) => {
        this.leaving.set(false);
        this.toast.error(err?.error?.message ?? 'Départ impossible.');
      },
    });
  }

  onAthleteChange(id: string): void {
    this.selectedAthlete.set(id);
  }
}
