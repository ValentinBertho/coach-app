import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { PhysioService } from '../../core/services/physio.service';
import { ToastService } from '../../core/services/toast.service';
import { AuthService } from '../../core/services/auth.service';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { PhysioProfile } from '../../core/models/physio.model';
import { SegmentedControlComponent, type SegmentOption } from '../../shared/components/ui';
import { AthleteRecordsPanelComponent } from './athlete-records-panel.component';

/** Un effort du test de Vitesse Critique : distance couverte, temps, FC moyenne relevée. */
interface VcTrial {
  distanceM: number | null;
  timeS: number | null;
  avgHr: number | null;
}

/**
 * Onglet « Tests & seuils » d'un athlète : les trois valeurs qui ancrent les zones (LT1, LT2,
 * vitesse critique) se saisissent directement, et le VDOT se déduit des records.
 *
 * <p>Le tableau de paliers lactate a été retiré : la détection Dmax supposait un test en
 * laboratoire que peu de coachs font passer, alors que la valeur de seuil, elle, est connue.
 * Le test de Vitesse Critique reste, complété de la FC moyenne de chaque effort.</p>
 */
@Component({
  selector: 'app-thresholds',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink, DecimalPipe, IconComponent, SegmentedControlComponent,
    AthleteRecordsPanelComponent],
  template: `
    <section class="page-header">
      <div>
        <h1 class="display-sm">Tests &amp; seuils</h1>
        <p class="subtitle">
          Records (→ VDOT), seuils LT1 / LT2 et vitesse critique : les valeurs qui ancrent les
          zones de l'athlète.
        </p>
      </div>
      <app-segmented-control [options]="speedOptions" [value]="speedUnit()"
                             (valueChange)="speedUnit.set($any($event))" [ariaLabel]="'Unité de vitesse'" />
    </section>

    <!-- Records : saisir un chrono recalcule le VDOT et les allures dérivées. -->
    <app-athlete-records-panel [athleteId]="athleteId()" />

    <!-- Seuils saisis à la main : LT1, LT2, VC (+ FC associées). -->
    <section class="card thresholds">
      <header class="th-head">
        <div>
          <h2>Seuils de l'athlète</h2>
          <p class="field-hint">
            Saisis les valeurs que tu connais ; les zones se recalculent aussitôt
            (<a [routerLink]="['/app/athletes', athleteId(), 'zones']">voir les zones</a>).
          </p>
        </div>
        @if (vdot(); as v) { <span class="th-vdot">VDOT <strong class="metric">{{ v | number: '1.0-1' }}</strong></span> }
      </header>

      <div class="th-grid">
        @for (f of fields; track f.key) {
          <div class="th-field">
            <label [for]="'th-' + f.key">{{ f.label }}</label>
            <input [id]="'th-' + f.key" class="form-control" inputmode="decimal"
                   [ngModel]="speedInput(f.key)" (ngModelChange)="setSpeed(f.key, $event)"
                   [placeholder]="speedPlaceholder()" />
            <span class="th-hint">{{ f.hint }}</span>
          </div>
        }
        @for (f of hrFields; track f.key) {
          <div class="th-field">
            <label [for]="'th-' + f.key">{{ f.label }}</label>
            <input [id]="'th-' + f.key" type="number" class="form-control" min="20" max="260"
                   [ngModel]="hrInput(f.key)" (ngModelChange)="setHr(f.key, $event)" placeholder="bpm" />
            <span class="th-hint">{{ f.hint }}</span>
          </div>
        }
      </div>

      <div class="th-actions">
        <button type="button" class="btn btn-primary" (click)="saveProfile()" [disabled]="saving()">
          {{ saving() ? 'Enregistrement…' : 'Enregistrer les seuils' }}
        </button>
      </div>
    </section>

    <!-- Test de Vitesse Critique (conservé) : deux efforts maximaux suffisent. -->
    <section class="card vc-card">
      <h2>Test de Vitesse Critique</h2>
      <p class="field-hint">
        Au moins deux efforts maximaux (distance parcourue / temps). VC = pente de la régression
        distance–temps. La FC moyenne de chaque effort est facultative : renseignée, elle donne la
        FC tenue à la VC, appliquée comme FC de seuil.
      </p>
      <table class="data-table">
        <thead><tr><th>Distance (m)</th><th>Temps (s)</th><th>FC moy (bpm)</th><th></th></tr></thead>
        <tbody>
          @for (tr of vcTrials; track $index) {
            <tr>
              <td><input type="number" class="form-control" [(ngModel)]="tr.distanceM" /></td>
              <td><input type="number" class="form-control" [(ngModel)]="tr.timeS" /></td>
              <td><input type="number" class="form-control" min="20" max="260" [(ngModel)]="tr.avgHr" placeholder="—" /></td>
              <td><button type="button" class="chip-x" (click)="removeVcTrial($index)" aria-label="Retirer"><app-icon name="x" [size]="13" /></button></td>
            </tr>
          }
        </tbody>
      </table>
      <div class="vc-actions">
        <button type="button" class="btn btn-ghost btn-sm" (click)="addVcTrial()">+ Effort</button>
        <button type="button" class="btn btn-ghost btn-sm" (click)="computeVc(false)">Calculer</button>
        <button type="button" class="btn btn-accent btn-sm" (click)="computeVc(true)">Appliquer au profil</button>
      </div>
      @if (vcResult(); as r) {
        <div class="vc-result">
          <span class="vc-val metric">{{ fmtSpeed(r.vcKmh) }}</span>
          <span class="field-hint">VC · D' ≈ {{ r.dPrimeM | number: '1.0-0' }} m</span>
          @if (r.avgHr) { <span class="field-hint metric">FC moy {{ r.avgHr }} bpm</span> }
        </div>
      }
    </section>
  `,
  styles: [`
    .page-header { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--sp-3); flex-wrap: wrap; }

    .thresholds { margin-bottom: var(--sp-4); }
    .th-head { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--sp-3); flex-wrap: wrap; }
    .th-head h2 { margin: 0 0 var(--sp-1); }
    .th-vdot { font-size: var(--text-sm); color: var(--ink-3); }
    .th-vdot strong { font-size: var(--text-lg); color: var(--dari-teal); margin-left: var(--sp-1); }

    .th-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(170px, 1fr)); gap: var(--sp-3); margin-top: var(--sp-4); }
    .th-field { display: flex; flex-direction: column; gap: 4px; }
    .th-field label { font-size: var(--text-xs); text-transform: uppercase; letter-spacing: 0.04em; color: var(--ink-3); font-weight: 700; }
    .th-hint { font-size: var(--text-xs); color: var(--ink-4); }
    .th-actions { display: flex; justify-content: flex-end; margin-top: var(--sp-4); }

    .vc-card h2 { margin-top: 0; }
    .vc-actions { display: flex; gap: var(--sp-2); justify-content: flex-end; margin-top: var(--sp-3); flex-wrap: wrap; }
    .vc-result { display: flex; align-items: baseline; gap: var(--sp-3); margin-top: var(--sp-3); flex-wrap: wrap; }
    .vc-val { font-size: var(--text-xl); font-weight: 800; color: var(--dari-teal); }
    .chip-x { border: none; background: none; color: var(--ink-3); cursor: pointer; padding: var(--sp-1); }
  `],
})
export class ThresholdsComponent implements OnInit {
  readonly athleteId = input.required<string>();

  private readonly physio = inject(PhysioService);
  private readonly toast = inject(ToastService);

  readonly profile = signal<PhysioProfile | null>(null);
  readonly saving = signal(false);
  readonly vdot = computed(() => this.profile()?.vdot ?? null);

  /** Les trois seuils qui ancrent les zones — exactement ce que le coach a à renseigner. */
  readonly fields = [
    { key: 'lt1Ms' as const, label: 'LT1', hint: 'Seuil aérobie' },
    { key: 'lt2Ms' as const, label: 'LT2', hint: 'Seuil lactique' },
    { key: 'vcMs' as const, label: 'Vitesse critique', hint: 'VC' },
  ];

  readonly hrFields = [
    { key: 'fcLt1' as const, label: 'FC LT1', hint: 'FC au seuil aérobie' },
    { key: 'fcLt2' as const, label: 'FC LT2', hint: 'FC au seuil lactique' },
    { key: 'fcMax' as const, label: 'FC max', hint: 'Fréquence cardiaque maximale' },
  ];

  /** Unité d'affichage/saisie des vitesses, initialisée sur la préférence du compte. */
  readonly speedUnit = signal<'kmh' | 'pace'>(
    inject(AuthService).paceUnit() === 'SPEED' ? 'kmh' : 'pace');
  readonly speedOptions: SegmentOption[] = [
    { value: 'kmh', label: 'km/h' },
    { value: 'pace', label: 'min/km' },
  ];

  /** Saisies en cours, dans l'unité choisie (le stockage reste en m/s). */
  private readonly edits = signal<Record<string, string>>({});
  private readonly hrEdits = signal<Record<string, number | null>>({});

  ngOnInit(): void { this.load(); }

  private load(): void {
    this.physio.profile(this.athleteId()).subscribe({
      next: (p) => { this.profile.set(p); this.edits.set({}); this.hrEdits.set({}); },
      error: () => this.profile.set(null),
    });
  }

  speedPlaceholder(): string {
    return this.speedUnit() === 'kmh' ? 'km/h' : 'm:ss';
  }

  /** Valeur affichée d'un seuil : la saisie en cours, sinon la valeur du profil convertie. */
  speedInput(key: 'lt1Ms' | 'lt2Ms' | 'vcMs'): string {
    const edited = this.edits()[key];
    if (edited !== undefined) return edited;
    const ms = this.profile()?.[key] ?? null;
    return ms == null ? '' : this.fromMs(ms);
  }

  setSpeed(key: 'lt1Ms' | 'lt2Ms' | 'vcMs', value: string): void {
    this.edits.update((e) => ({ ...e, [key]: value }));
  }

  hrInput(key: 'fcLt1' | 'fcLt2' | 'fcMax'): number | null {
    const edited = this.hrEdits()[key];
    return edited !== undefined ? edited : (this.profile()?.[key] ?? null);
  }

  setHr(key: 'fcLt1' | 'fcLt2' | 'fcMax', value: number | null): void {
    this.hrEdits.update((e) => ({ ...e, [key]: value }));
  }

  /**
   * Enregistre les seuils. Le profil est envoyé en entier : le contrat serveur remplace les
   * valeurs, un champ omis serait effacé.
   */
  saveProfile(): void {
    if (this.saving()) return;
    const parsed: Record<string, number | null> = {};
    for (const f of this.fields) {
      const raw = this.speedInput(f.key);
      const ms = this.toMs(raw);
      if (raw.trim() && ms == null) {
        this.toast.warning(`${f.label} : valeur illisible (${this.speedPlaceholder()}).`);
        return;
      }
      parsed[f.key] = ms;
    }
    const p = this.profile();
    this.saving.set(true);
    this.physio.updateProfile(this.athleteId(), {
      discipline: p?.discipline ?? null,
      lt1Ms: parsed['lt1Ms'], lt2Ms: parsed['lt2Ms'], vcMs: parsed['vcMs'],
      fcLt1: this.hrInput('fcLt1'), fcLt2: this.hrInput('fcLt2'), fcMax: this.hrInput('fcMax'),
    }).subscribe({
      next: (updated) => {
        this.profile.set(updated);
        this.edits.set({});
        this.hrEdits.set({});
        this.saving.set(false);
        this.toast.success('Seuils enregistrés — zones resynchronisées.');
      },
      error: () => { this.saving.set(false); this.toast.error('Enregistrement impossible.'); },
    });
  }

  // --- Test de Vitesse Critique --------------------------------------------

  vcTrials: VcTrial[] = [
    { distanceM: 1200, timeS: 240, avgHr: null },
    { distanceM: 3000, timeS: 720, avgHr: null },
  ];
  readonly vcResult = signal<{ vcKmh: number; dPrimeM: number; avgHr: number | null } | null>(null);

  addVcTrial(): void { this.vcTrials = [...this.vcTrials, { distanceM: null, timeS: null, avgHr: null }]; }
  removeVcTrial(i: number): void { this.vcTrials = this.vcTrials.filter((_, idx) => idx !== i); }

  computeVc(apply: boolean): void {
    const trials = this.vcTrials
      .filter((t) => t.distanceM && t.timeS)
      .map((t) => ({ distanceM: t.distanceM as number, timeS: t.timeS as number, avgHr: t.avgHr ?? null }));
    if (trials.length < 2) { this.toast.warning('Renseigne au moins deux efforts.'); return; }
    this.physio.vcTest(this.athleteId(), { trials, applyToProfile: apply }).subscribe({
      next: (r) => {
        this.vcResult.set({ vcKmh: r.vcKmh, dPrimeM: r.dPrimeM, avgHr: r.avgHr ?? null });
        if (apply) {
          this.toast.success(`VC ${r.vcKmh.toFixed(1)} km/h appliquée au profil`);
          this.load();
        }
      },
      error: () => this.toast.error('Calcul impossible (efforts incohérents ?).'),
    });
  }

  // --- Conversions vitesse ↔ unité d'affichage ------------------------------

  /** Formate une vitesse (km/h) selon l'unité choisie. */
  fmtSpeed(kmh: number | null | undefined): string {
    if (kmh == null) return '—';
    return this.speedUnit() === 'kmh' ? `${kmh.toFixed(1)} km/h` : `${this.paceOf(kmh)} /km`;
  }

  private paceOf(kmh: number): string {
    const secPerKm = 3600 / kmh;
    const m = Math.floor(secPerKm / 60);
    const s = Math.round(secPerKm % 60);
    return `${m}:${s.toString().padStart(2, '0')}`;
  }

  /** m/s → texte dans l'unité courante. */
  private fromMs(ms: number): string {
    const kmh = ms * 3.6;
    return this.speedUnit() === 'kmh' ? (Math.round(kmh * 10) / 10).toString() : this.paceOf(kmh);
  }

  /** « 16 » (km/h) ou « 3:45 » (min/km) → m/s ; null si vide ou illisible. */
  private toMs(raw: string): number | null {
    const t = raw.trim().replace(',', '.');
    if (!t) return null;
    if (this.speedUnit() === 'kmh') {
      const kmh = Number(t);
      return Number.isFinite(kmh) && kmh > 0 ? Math.round((kmh / 3.6) * 1000) / 1000 : null;
    }
    const [mm, ss] = t.split(':');
    const secPerKm = ss === undefined ? Number(mm) * 60 : Number(mm) * 60 + Number(ss);
    if (!Number.isFinite(secPerKm) || secPerKm <= 0) return null;
    return Math.round((1000 / secPerKm) * 1000) / 1000;
  }
}
