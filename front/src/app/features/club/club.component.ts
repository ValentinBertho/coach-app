import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { AthleteService } from '../../core/services/athlete.service';
import { AuthService } from '../../core/services/auth.service';
import { Ref } from '../../core/models/athlete.model';

/** Écran Club (s-club) : coachs du club, rôles et modèle de permissions DARI Lab. */
@Component({
  selector: 'app-club',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="page-header">
      <div>
        <h1 class="display-sm">{{ user()?.clubName || 'Mon club' }}</h1>
        <p class="subtitle">Coachs, rôles et permissions (modèle multi-coach DARI Lab).</p>
      </div>
    </section>

    <div class="card">
      <h2>Coachs du club</h2>
      <table class="data-table">
        <thead><tr><th>Coach</th><th>Rôle</th><th>Accès</th></tr></thead>
        <tbody>
          @for (c of coaches(); track c.id; let first = $first) {
            <tr>
              <td><span class="avatar">{{ c.name[0] }}</span> {{ c.name }}</td>
              <td><span class="badge" [class.badge-info]="first" [class.badge-neutral]="!first">{{ first ? 'Coach principal' : 'Coach assistant' }}</span></td>
              <td class="field-hint">Athlètes du club + ses athlètes privés</td>
            </tr>
          } @empty {
            <tr><td colspan="3" class="field-hint">Aucun coach.</td></tr>
          }
        </tbody>
      </table>
    </div>

    <div class="rel-grid">
      <div class="card">
        <h2>Niveaux de permission</h2>
        <ul class="perm-list">
          <li><span class="badge badge-neutral">Lecture</span> Dashboard, calendrier, profil physio, analyses</li>
          <li><span class="badge badge-info">Commentaire</span> + messagerie et retours de séance</li>
          <li><span class="badge badge-success">Écriture</span> + prescrire, modifier, remplacer le référent</li>
        </ul>
      </div>
      <div class="card">
        <h2>Privé vs Club</h2>
        <p><span class="badge" style="background:#ede9fe;color:#6d28d9">🔒 Privé</span> visible du seul coach référent — invisible des autres, même l'Owner.</p>
        <p><span class="badge badge-success">🏛️ Club</span> visible par les coachs du club selon les permissions.</p>
      </div>
    </div>
  `,
  styles: [`
    .perm-list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: var(--sp-3); }
    .rel-grid { display: grid; grid-template-columns: 1fr 1fr; gap: var(--sp-4); margin-top: var(--sp-4); }
    .avatar { width: 28px; height: 28px; font-size: var(--text-xs); margin-right: var(--sp-2); }
    @media (max-width: 768px) { .rel-grid { grid-template-columns: 1fr; } }
  `],
})
export class ClubComponent implements OnInit {
  private readonly athletes = inject(AthleteService);
  private readonly auth = inject(AuthService);
  readonly user = this.auth.currentUser;
  readonly coaches = signal<Ref[]>([]);

  ngOnInit(): void {
    this.athletes.assignableCoaches().subscribe((c) => this.coaches.set(c));
  }
}
