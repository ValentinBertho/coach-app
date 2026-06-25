import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AthletePortalService } from '../../core/services/athlete-portal.service';
import { AuthService } from '../../core/services/auth.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { ToastService } from '../../core/services/toast.service';
import { LogoComponent } from '../../shared/components/logo/logo.component';

/** Profil & confidentialité athlète : export RGPD + suppression de compte. */
@Component({
  selector: 'app-athlete-profile',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, LogoComponent],
  template: `
    <div class="shell">
      <header class="top">
        <app-logo [size]="28" [showText]="true" />
        <a routerLink="/athlete/today" class="btn btn-ghost btn-sm">← Aujourd'hui</a>
      </header>
      <main class="wrap">
        <h1 class="display-sm">Profil & confidentialité</h1>
        <p class="subtitle">{{ user()?.fullName }}</p>

        <div class="card">
          <h2>Mes données (RGPD)</h2>
          <p class="field-hint">Téléchargez l'ensemble de vos données personnelles (portabilité).</p>
          <button type="button" class="btn btn-ghost" (click)="exportData()">⬇ Exporter mes données (JSON)</button>
        </div>

        <div class="card danger-zone">
          <div>
            <strong>Supprimer mon compte</strong>
            <p class="field-hint">Efface définitivement votre profil, vos séances et activités.</p>
          </div>
          <button type="button" class="btn btn-danger" (click)="deleteAccount()">Supprimer</button>
        </div>
      </main>
    </div>
  `,
  styles: [`
    .shell { min-height: 100dvh; background: var(--canvas); }
    .top { display: flex; align-items: center; justify-content: space-between; padding: var(--sp-3) var(--sp-4); padding-top: max(var(--sp-3), env(safe-area-inset-top)); background: var(--paper); border-bottom: 1px solid var(--hairline); position: sticky; top: 0; }
    .wrap { max-width: 560px; margin-inline: auto; padding: var(--sp-5) var(--sp-4) var(--sp-12); display: flex; flex-direction: column; gap: var(--sp-4); }
    .subtitle { color: var(--ink-3); margin: 0; }
    .card h2 { margin: 0 0 var(--sp-2); font-size: var(--text-lg); }
  `],
})
export class AthleteProfileComponent {
  private readonly portal = inject(AthletePortalService);
  private readonly auth = inject(AuthService);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);
  readonly user = this.auth.currentUser;

  exportData(): void {
    this.portal.export().subscribe((data) => {
      const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'darilab-mes-donnees.json';
      a.click();
      URL.revokeObjectURL(url);
      this.toast.success('Export téléchargé.');
    });
  }

  async deleteAccount(): Promise<void> {
    const ok = await this.confirm.ask({
      title: 'Supprimer mon compte',
      message: 'Cette action est irréversible : profil, séances et activités seront effacés. Continuer ?',
      confirmLabel: 'Supprimer définitivement',
      danger: true,
    });
    if (!ok) return;
    this.portal.deleteAccount().subscribe(() => {
      this.auth.logout();
      this.toast.info('Votre compte a été supprimé.');
      this.router.navigate(['/']);
    });
  }
}
