import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { AthletePortalService } from '../../core/services/athlete-portal.service';
import { AuthService } from '../../core/services/auth.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { ToastService } from '../../core/services/toast.service';
import { LogoComponent } from '../../shared/components/logo/logo.component';
import { DataOriginTagComponent } from '../../shared/components/physiology';
import { PhysioProfile, Vdot } from '../../core/models/physio.model';
import { RaceObjective } from '../../core/models/race.model';

/**
 * Profil & confidentialité athlète (mobile-first). Phase 1 « Me connaître » :
 * mon profil physio (lecture), mes allures d'entraînement (VDOT) et mes objectifs,
 * câblés sur /me/physio, /me/vdot, /me/races — puis export RGPD + suppression de compte.
 */
@Component({
  selector: 'app-athlete-profile',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, LogoComponent, DataOriginTagComponent, DatePipe, DecimalPipe],
  template: `
    <div class="shell">
      <header class="top">
        <app-logo [size]="28" [showText]="true" />
        <a routerLink="/athlete/today" class="btn btn-ghost btn-sm">← Aujourd'hui</a>
      </header>
      <main class="wrap">
        <h1 class="display-sm">Profil & confidentialité</h1>
        <p class="subtitle">{{ user()?.fullName }}</p>

        <!-- Mon profil physio (lecture seule) -->
        @if (physio(); as p) {
          <section class="card">
            <div class="card-hd">
              <h2>Mon profil physio</h2>
              <app-data-origin-tag origin="calcule" label="Calculé" />
            </div>
            @if (hasPhysio(p)) {
              <div class="grid">
                @if (p.vdot != null) {
                  <div class="kpi"><span class="kpi-l">VDOT</span><span class="kpi-v">{{ p.vdot | number: '1.0-1' }}</span></div>
                }
                @if (p.lt1Kmh != null) {
                  <div class="kpi"><span class="kpi-l">LT1 (seuil aéro)</span><span class="kpi-v">{{ p.lt1Kmh | number: '1.1-1' }}<small> km/h</small></span></div>
                }
                @if (p.lt2Kmh != null) {
                  <div class="kpi"><span class="kpi-l">LT2 (seuil anaéro)</span><span class="kpi-v">{{ p.lt2Kmh | number: '1.1-1' }}<small> km/h</small></span></div>
                }
                @if (p.vcKmh != null) {
                  <div class="kpi"><span class="kpi-l">Vitesse critique</span><span class="kpi-v">{{ p.vcKmh | number: '1.1-1' }}<small> km/h</small></span></div>
                }
                @if (p.fcMax != null) {
                  <div class="kpi"><span class="kpi-l">FC max</span><span class="kpi-v">{{ p.fcMax }}<small> bpm</small></span></div>
                }
                @if (p.fcLt1 != null) {
                  <div class="kpi"><span class="kpi-l">FC LT1</span><span class="kpi-v">{{ p.fcLt1 }}<small> bpm</small></span></div>
                }
                @if (p.fcLt2 != null) {
                  <div class="kpi"><span class="kpi-l">FC LT2</span><span class="kpi-v">{{ p.fcLt2 }}<small> bpm</small></span></div>
                }
              </div>
            } @else {
              <p class="field-hint">Ton profil sera renseigné par ton coach après tes premiers tests.</p>
            }
          </section>
        }

        <!-- Mes allures d'entraînement (VDOT) -->
        @if (vdot(); as v) {
          @if (v.paces.length > 0) {
            <section class="card">
              <div class="card-hd">
                <h2>Mes allures d'entraînement</h2>
                <app-data-origin-tag origin="calcule" label="Calculé" />
              </div>
              <ul class="paces">
                @for (pace of v.paces; track pace.distance) {
                  <li>
                    <span class="pace-d">{{ pace.distance }}</span>
                    <span class="pace-p metric">{{ pace.paceLabel }}<small> /km</small></span>
                    @if (pace.speedKmh != null) {
                      <span class="pace-s field-hint">{{ pace.speedKmh | number: '1.1-1' }} km/h</span>
                    }
                  </li>
                }
              </ul>
            </section>
          }
        }

        <!-- Mes objectifs -->
        @if (races(); as rs) {
          @if (rs.length > 0) {
            <section class="card">
              <h2>Mes objectifs</h2>
              <ul class="races">
                @for (r of rs; track r.id) {
                  <li>
                    <span class="prio prio-{{ r.priority }}">{{ r.priority }}</span>
                    <div class="race-id">
                      <strong>{{ r.name }}</strong>
                      <span class="field-hint">{{ r.raceDate | date: 'EEE d MMM yyyy' }}</span>
                    </div>
                    <span class="race-j" [class.past]="r.daysUntil < 0">
                      {{ r.daysUntil > 0 ? 'J−' + r.daysUntil : (r.daysUntil === 0 ? "Jour J" : 'Passé') }}
                    </span>
                  </li>
                }
              </ul>
            </section>
          }
        }

        <div class="card">
          <h2>Mes données (RGPD)</h2>
          <p class="field-hint">Téléchargez l'ensemble de vos données personnelles (portabilité).</p>
          <button type="button" class="btn btn-ghost" (click)="exportData()">Exporter mes données (JSON)</button>
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
    .card-hd { display: flex; align-items: center; justify-content: space-between; gap: var(--sp-2); margin-bottom: var(--sp-3); }
    .card-hd h2 { margin: 0; }

    .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); gap: var(--sp-3); }
    .kpi { display: flex; flex-direction: column; gap: 2px; padding: var(--sp-2) var(--sp-3); background: var(--canvas); border-radius: var(--radius-md); }
    .kpi-l { font-size: var(--text-sm); color: var(--ink-3); }
    .kpi-v { font-size: var(--text-xl); font-weight: 800; color: var(--ink); font-variant-numeric: tabular-nums; }
    .kpi-v small { font-size: var(--text-sm); font-weight: 600; color: var(--ink-3); }

    .paces { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; }
    .paces li { display: flex; align-items: baseline; gap: var(--sp-3); padding: var(--sp-2) 0; border-top: 1px solid var(--hairline); }
    .paces li:first-child { border-top: none; }
    .pace-d { flex: 1; color: var(--ink); font-weight: 600; }
    .pace-p { font-weight: 800; color: var(--ink); font-variant-numeric: tabular-nums; }
    .pace-p small { font-weight: 600; color: var(--ink-3); }
    .pace-s { width: 88px; text-align: right; }

    .races { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; }
    .races li { display: flex; align-items: center; gap: var(--sp-3); padding: var(--sp-2) 0; border-top: 1px solid var(--hairline); }
    .races li:first-child { border-top: none; }
    .race-id { display: flex; flex-direction: column; gap: 2px; min-width: 0; flex: 1; }
    .race-id strong { color: var(--ink); }
    .prio { display: inline-flex; align-items: center; justify-content: center; width: 26px; height: 26px; border-radius: 50%; font-size: var(--text-sm); font-weight: 800; flex-shrink: 0; }
    .prio-A { background: var(--danger-bg); color: var(--danger-text); }
    .prio-B { background: var(--warn-bg, var(--canvas)); color: var(--warn-text, var(--ink)); }
    .prio-C { background: var(--canvas); color: var(--ink-3); }
    .race-j { font-weight: 800; color: var(--dari-violet); font-variant-numeric: tabular-nums; white-space: nowrap; }
    .race-j.past { color: var(--ink-4); }
  `],
})
export class AthleteProfileComponent implements OnInit {
  private readonly portal = inject(AthletePortalService);
  private readonly auth = inject(AuthService);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);
  readonly user = this.auth.currentUser;

  readonly physio = signal<PhysioProfile | null>(null);
  readonly vdot = signal<Vdot | null>(null);
  readonly races = signal<RaceObjective[] | null>(null);

  ngOnInit(): void {
    this.portal.physio().subscribe({ next: (p) => this.physio.set(p), error: () => this.physio.set(null) });
    this.portal.vdot().subscribe({ next: (v) => this.vdot.set(v), error: () => this.vdot.set(null) });
    this.portal.races().subscribe({ next: (r) => this.races.set(r), error: () => this.races.set(null) });
  }

  /** Au moins une donnée physio renseignée (sinon on affiche un message d'attente). */
  hasPhysio(p: PhysioProfile): boolean {
    return p.vdot != null || p.lt1Kmh != null || p.lt2Kmh != null || p.vcKmh != null
      || p.fcMax != null || p.fcLt1 != null || p.fcLt2 != null;
  }

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
