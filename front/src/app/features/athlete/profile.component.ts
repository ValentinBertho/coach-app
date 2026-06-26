import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { AthletePortalService } from '../../core/services/athlete-portal.service';
import { AuthService } from '../../core/services/auth.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { ToastService } from '../../core/services/toast.service';
import { LogoComponent } from '../../shared/components/logo/logo.component';
import { DataOriginTagComponent } from '../../shared/components/physiology';
import { PhysioProfile, Performance, Vdot } from '../../core/models/physio.model';
import { LactateTest } from '../../core/models/lactate.model';
import { RaceObjective } from '../../core/models/race.model';

interface TrendPoint { date: string; value: number; }
interface LtPoint { date: string; lt1: number | null; lt2: number | null; }

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

        <!-- Ma progression physio (tendance VDOT + seuils) -->
        @if (vdotPoints().length >= 2 || ltPoints().length >= 2) {
          <section class="card">
            <div class="card-hd">
              <h2>Ma progression</h2>
              <app-data-origin-tag origin="mesure" label="Mesuré" />
            </div>

            @if (vdotPoints().length >= 2) {
              <div class="trend">
                <div class="trend-hd">
                  <span class="field-hint">VDOT</span>
                  <span class="trend-delta" [class.up]="vdotDelta() >= 0" [class.down]="vdotDelta() < 0">
                    {{ vdotDelta() >= 0 ? '▲' : '▼' }} {{ absVdot() | number: '1.0-1' }}
                  </span>
                </div>
                <svg viewBox="0 0 300 90" class="trend-svg" preserveAspectRatio="none" role="img" aria-label="Évolution du VDOT">
                  <polyline [attr.points]="vdotLine()" fill="none" stroke="var(--dari-violet)" stroke-width="2.5" />
                </svg>
                <div class="trend-x field-hint">
                  <span>{{ vdotPoints()[0].date | date: 'MM/yy' }}</span>
                  <span>{{ vdotPoints()[vdotPoints().length - 1].date | date: 'MM/yy' }}</span>
                </div>
              </div>
            }

            @if (ltPoints().length >= 2) {
              <div class="trend">
                <div class="trend-hd">
                  <span class="field-hint">Seuils (km/h)</span>
                  <span class="legend"><i class="sw sw-1"></i>LT1 <i class="sw sw-2"></i>LT2</span>
                </div>
                <svg viewBox="0 0 300 90" class="trend-svg" preserveAspectRatio="none" role="img" aria-label="Évolution des seuils">
                  @if (lt1Line()) { <polyline [attr.points]="lt1Line()" fill="none" stroke="var(--form-green, #11c08b)" stroke-width="2.5" /> }
                  @if (lt2Line()) { <polyline [attr.points]="lt2Line()" fill="none" stroke="var(--form-orange, #ff8a3c)" stroke-width="2.5" /> }
                </svg>
                <div class="trend-x field-hint">
                  <span>{{ ltPoints()[0].date | date: 'MM/yy' }}</span>
                  <span>{{ ltPoints()[ltPoints().length - 1].date | date: 'MM/yy' }}</span>
                </div>
              </div>
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

    .trend { margin-top: var(--sp-3); }
    .trend:first-of-type { margin-top: var(--sp-2); }
    .trend-hd { display: flex; align-items: center; justify-content: space-between; gap: var(--sp-2); }
    .trend-svg { width: 100%; height: 90px; }
    .trend-delta { font-weight: 800; font-size: var(--text-sm); font-variant-numeric: tabular-nums; }
    .trend-delta.up { color: var(--success-text); }
    .trend-delta.down { color: var(--danger-text); }
    .trend-x { display: flex; justify-content: space-between; }
    .legend { display: inline-flex; align-items: center; gap: 4px; font-size: var(--text-xs); color: var(--ink-3); }
    .legend .sw { width: 12px; height: 0; border-top: 2px solid; display: inline-block; vertical-align: middle; }
    .legend .sw-1 { border-color: var(--form-green, #11c08b); }
    .legend .sw-2 { border-color: var(--form-orange, #ff8a3c); }
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
  readonly performances = signal<Performance[]>([]);
  readonly lactateTests = signal<LactateTest[]>([]);

  /** Points VDOT datés (depuis les performances), ordonnés dans le temps. */
  readonly vdotPoints = computed<TrendPoint[]>(() =>
    this.performances()
      .filter((p) => p.vdot != null && p.dateSet)
      .map((p) => ({ date: p.dateSet as string, value: p.vdot as number }))
      .sort((a, b) => a.date.localeCompare(b.date)));

  /** Points seuils datés (depuis les tests lactate), en km/h, ordonnés dans le temps. */
  readonly ltPoints = computed<LtPoint[]>(() =>
    this.lactateTests()
      .filter((t) => t.testDate && (t.lt1Ms != null || t.lt2Ms != null))
      .map((t) => ({
        date: t.testDate,
        lt1: t.lt1Ms != null ? Math.round(t.lt1Ms * 3.6 * 10) / 10 : null,
        lt2: t.lt2Ms != null ? Math.round(t.lt2Ms * 3.6 * 10) / 10 : null,
      }))
      .sort((a, b) => a.date.localeCompare(b.date)));

  readonly vdotDelta = computed(() => {
    const p = this.vdotPoints();
    return p.length >= 2 ? p[p.length - 1].value - p[0].value : 0;
  });
  absVdot(): number { return Math.abs(this.vdotDelta()); }

  readonly vdotLine = computed(() => {
    const vals = this.vdotPoints().map((p) => p.value);
    return this.poly(vals, Math.min(...vals), Math.max(...vals));
  });

  readonly lt1Line = computed(() => this.ltLine((p) => p.lt1));
  readonly lt2Line = computed(() => this.ltLine((p) => p.lt2));

  private ltLine(pick: (p: LtPoint) => number | null): string {
    const pts = this.ltPoints();
    const all = pts.flatMap((p) => [p.lt1, p.lt2]).filter((v): v is number => v != null);
    if (all.length === 0) return '';
    const min = Math.min(...all);
    const max = Math.max(...all);
    return this.poly(pts.map(pick), min, max);
  }

  /** Polyligne SVG (viewBox 300×90) d'une série, valeurs nulles omises. */
  private poly(vals: (number | null)[], min: number, max: number): string {
    const W = 300, H = 90, PT = 8, PB = 8;
    const span = max - min || 1;
    const n = vals.length;
    return vals
      .map((v, i) => {
        if (v == null) return null;
        const x = n === 1 ? W / 2 : (i / (n - 1)) * W;
        const y = (H - PB) - ((v - min) / span) * (H - PT - PB);
        return `${x.toFixed(1)},${y.toFixed(1)}`;
      })
      .filter((s): s is string => s != null)
      .join(' ');
  }

  ngOnInit(): void {
    this.portal.physio().subscribe({ next: (p) => this.physio.set(p), error: () => this.physio.set(null) });
    this.portal.vdot().subscribe({ next: (v) => this.vdot.set(v), error: () => this.vdot.set(null) });
    this.portal.races().subscribe({ next: (r) => this.races.set(r), error: () => this.races.set(null) });
    this.portal.performances().subscribe({ next: (p) => this.performances.set(p), error: () => this.performances.set([]) });
    this.portal.lactateTests().subscribe({ next: (t) => this.lactateTests.set(t), error: () => this.lactateTests.set([]) });
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
