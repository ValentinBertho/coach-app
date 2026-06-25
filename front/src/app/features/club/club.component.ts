import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { FormsModule } from '@angular/forms';
import { AthleteService } from '../../core/services/athlete.service';
import { AuthService } from '../../core/services/auth.service';
import {
  AthleteAccess,
  ClubMember,
  ClubService,
  PermissionLevel,
} from '../../core/services/club.service';
import { ToastService } from '../../core/services/toast.service';
import { AthleteSummary } from '../../core/models/athlete.model';

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

  ngOnInit(): void {
    this.clubService.members().subscribe({
      next: (m) => { this.members.set(m); this.loadingMembers.set(false); },
      error: () => this.loadingMembers.set(false),
    });
    this.athletes.list({ status: 'ACTIVE' }).subscribe((p) => this.athleteList.set(p.content));
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
