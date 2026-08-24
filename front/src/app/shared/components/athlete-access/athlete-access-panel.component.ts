import { ChangeDetectionStrategy, Component, effect, inject, input, signal } from '@angular/core';
import { IconComponent } from '../icon/icon.component';
import {
  AthleteAccess,
  ClubMember,
  ClubService,
  PermissionLevel,
} from '../../../core/services/club.service';
import { ToastService } from '../../../core/services/toast.service';

/**
 * Accès & permissions d'<b>un</b> athlète : statut privé/club, coach référent, et niveau accordé à
 * chaque coach du club.
 *
 * <p>Ce bloc n'existait que sur l'écran Club, derrière un sélecteur d'athlète : pour ouvrir un
 * accès à un collègue, un coach devait quitter la fiche qu'il avait sous les yeux, aller au club,
 * et y retrouver le même athlète dans une liste déroulante. Le voici posé là où la question se
 * pose — sur la fiche — sans dupliquer une ligne : l'écran Club monte le même composant.</p>
 */
@Component({
  selector: 'app-athlete-access-panel',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    @if (access(); as acc) {
      <div class="ownership">
        <span>Statut :</span>
        @if (acc.ownership === 'CLUB') {
          <span class="badge badge-success"><app-icon name="building-2" [size]="13" /> Club</span>
        } @else {
          <span class="badge badge-private"><app-icon name="lock" [size]="13" /> Privé</span>
        }
        <button type="button" class="btn btn-ghost btn-sm" (click)="toggleOwnership()">
          Passer en {{ acc.ownership === 'CLUB' ? 'privé' : 'club' }}
        </button>
        @if (acc.referentName) { <span class="field-hint">Référent : {{ acc.referentName }}</span> }
      </div>

      <table class="data-table perm-table">
        <thead><tr><th>Coach</th><th>Accès actuel</th><th>Accorder</th></tr></thead>
        <tbody>
          @for (m of members(); track m.coachId) {
            <tr>
              <td>{{ m.name }} @if (isReferent(m.coachId)) { <span class="badge badge-info">Référent</span> }</td>
              <td>
                @if (isReferent(m.coachId)) {
                  <span class="badge badge-success">Écriture (référent)</span>
                } @else {
                  <!-- L'alias « as » n'existe que sur @if : d'où ce second bloc, un
                       @else if (… ; as p) ne compilant pas. -->
                  @if (permFor(m.coachId); as p) {
                    <span class="badge badge-info">{{ levelLabels[p] }}</span>
                    <button type="button" class="chip-x" (click)="revoke(m.coachId)"
                            title="Retirer" aria-label="Retirer"><app-icon name="x" [size]="13" /></button>
                  } @else {
                    <span class="field-hint">—</span>
                  }
                }
              </td>
              <td>
                @if (!isReferent(m.coachId) && acc.ownership === 'CLUB') {
                  <div class="grant-btns">
                    @for (l of levels; track l) {
                      <button type="button" class="btn btn-ghost btn-sm" (click)="grant(m.coachId, l)">
                        {{ levelLabels[l] }}
                      </button>
                    }
                  </div>
                } @else if (acc.ownership === 'PRIVATE') {
                  <span class="field-hint">Athlète privé</span>
                }
              </td>
            </tr>
          }
        </tbody>
      </table>
    } @else if (loading()) {
      <p class="field-hint">Chargement des accès…</p>
    } @else {
      <p class="field-hint">Accès indisponibles pour cet athlète.</p>
    }
  `,
  styles: [`
    .ownership { display: flex; align-items: center; gap: var(--sp-2); flex-wrap: wrap; margin-bottom: var(--sp-3); }
    .badge-private { background: #ede9fe; color: #6d28d9; }
    .perm-table { width: 100%; }
    .grant-btns { display: flex; gap: var(--sp-1); flex-wrap: wrap; }
    .chip-x { border: 0; background: none; cursor: pointer; color: var(--ink-3); }
  `],
})
export class AthleteAccessPanelComponent {
  readonly athleteId = input.required<string>();

  private readonly clubService = inject(ClubService);
  private readonly toast = inject(ToastService);

  readonly members = signal<ClubMember[]>([]);
  readonly access = signal<AthleteAccess | null>(null);
  readonly loading = signal(true);

  readonly levels: PermissionLevel[] = ['READ', 'COMMENT', 'WRITE'];
  readonly levelLabels: Record<PermissionLevel, string> =
    { READ: 'Lecture', COMMENT: 'Commentaire', WRITE: 'Écriture' };

  /**
   * Rechargé quand l'athlète change : le panneau est monté sous des écrans qui changent de sujet
   * sans être détruits (le sélecteur du club, la coquille d'un athlète).
   */
  private readonly load = effect((onCleanup) => {
    const id = this.athleteId();
    this.access.set(null);
    this.loading.set(true);
    if (!id) {
      this.loading.set(false);
      return;
    }
    const members = this.clubService.members().subscribe({
      next: (m) => this.members.set(m),
      error: () => this.members.set([]),
    });
    const access = this.clubService.access(id).subscribe({
      next: (a) => { this.access.set(a); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
    onCleanup(() => { members.unsubscribe(); access.unsubscribe(); });
  }, { allowSignalWrites: true });

  toggleOwnership(): void {
    const current = this.access();
    if (!current) return;
    const next = current.ownership === 'CLUB' ? 'PRIVATE' : 'CLUB';
    this.clubService.setOwnership(this.athleteId(), next).subscribe({
      next: (res) => {
        this.access.set(res);
        this.toast.success(`Athlète ${next === 'CLUB' ? 'rattaché au club' : 'passé en privé'}`);
      },
      error: () => this.toast.warning('Impossible : des permissions actives existent.'),
    });
  }

  grant(coachId: string, level: PermissionLevel): void {
    this.clubService.grant(this.athleteId(), coachId, level).subscribe({
      next: (res) => { this.access.set(res); this.toast.success('Permission accordée'); },
      error: () => this.toast.warning('Athlète privé : permission impossible.'),
    });
  }

  revoke(coachId: string): void {
    this.clubService.revoke(this.athleteId(), coachId).subscribe({
      next: (res) => { this.access.set(res); this.toast.info('Permission retirée.'); },
    });
  }

  permFor(coachId: string): PermissionLevel | null {
    return this.access()?.permissions.find((p) => p.coachId === coachId)?.permission ?? null;
  }

  isReferent(coachId: string): boolean {
    return this.access()?.referentCoachId === coachId;
  }
}
