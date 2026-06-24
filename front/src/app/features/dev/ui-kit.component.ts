import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import {
  AcwrIndicatorComponent,
  DataOriginTagComponent,
  EffortBadgeComponent,
  IntensityZoneBadgeComponent,
  PainFatigueSelectorComponent,
  RangePrescriptionPillComponent,
  ReadinessGaugeComponent,
} from '../../shared/components/physiology';
import {
  BottomSheetComponent,
  MetricCardComponent,
  SegmentedControlComponent,
  StickyActionBarComponent,
  type SegmentOption,
} from '../../shared/components/ui';

/**
 * Living styleguide des primitives (cf. docs/ux-redesign-blueprint.md).
 * Sert de banc de validation visuelle ET de référence d'usage pour l'équipe.
 * Route dev : /dev/ui-kit.
 */
@Component({
  selector: 'app-ui-kit',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    AcwrIndicatorComponent,
    DataOriginTagComponent,
    EffortBadgeComponent,
    IntensityZoneBadgeComponent,
    PainFatigueSelectorComponent,
    RangePrescriptionPillComponent,
    ReadinessGaugeComponent,
    BottomSheetComponent,
    MetricCardComponent,
    SegmentedControlComponent,
    StickyActionBarComponent,
  ],
  template: `
    <div class="container-standard" style="padding-block: var(--sp-8);">
      <header class="page-header">
        <div>
          <h1>UI Kit — Primitives physiologie</h1>
          <p class="subtitle">Composants standalone qui encodent les invariants métier DARI Lab.</p>
        </div>
      </header>

      <!-- Origine de la donnée -->
      <section class="card" style="margin-bottom: var(--sp-6);">
        <h3>Origine de la donnée</h3>
        <div class="row">
          <app-data-origin-tag origin="saisi" />
          <app-data-origin-tag origin="calcule" />
          <app-data-origin-tag origin="estime" />
          <app-data-origin-tag origin="mesure" />
          <app-data-origin-tag origin="alerte" />
          <app-data-origin-tag origin="fait" />
          <app-data-origin-tag origin="calcule" [compact]="true" />
        </div>
      </section>

      <!-- Zones d'intensité -->
      <section class="card" style="margin-bottom: var(--sp-6);">
        <h3>Zones d'intensité (couleur + forme + « Zx »)</h3>
        <div class="row">
          <app-intensity-zone-badge [zone]="1" />
          <app-intensity-zone-badge [zone]="2" [showName]="true" />
          <app-intensity-zone-badge [zone]="3" range="4:10–4:20 /km" />
          <app-intensity-zone-badge [zone]="4" [showName]="true" range="158–168 bpm" />
          <app-intensity-zone-badge [zone]="5" />
        </div>
      </section>

      <!-- Prescriptions en fourchette -->
      <section class="card" style="margin-bottom: var(--sp-6);">
        <h3>Prescription en fourchette (jamais valeur sèche)</h3>
        <div class="row">
          <app-range-prescription-pill label="Allure" [min]="250" [max]="260" unit="/km" [format]="paceFmt" />
          <app-range-prescription-pill label="Charge" [min]="70" [max]="75" unit="% 1RM" [target]="72" [actual]="74" />
          <app-range-prescription-pill label="Reps" [min]="8" [max]="10" [actual]="11" />
          <app-range-prescription-pill label="FC" [min]="148" [max]="158" unit="bpm" />
          <!-- anti-pattern volontaire : déclenche le mode dégradé visible -->
          <app-range-prescription-pill label="Charge" [min]="80" unit="% 1RM" />
        </div>
      </section>

      <!-- État de forme -->
      <section class="card" style="margin-bottom: var(--sp-6);">
        <h3>État de forme (jamais dérivé du RPE seul)</h3>
        <div class="gauges">
          <app-readiness-gauge level="green" [fatigue]="3" [pain]="0" [acwr]="1.05" />
          <app-readiness-gauge level="orange" [fatigue]="6" [pain]="2" [acwr]="1.35"
            note="Charge aiguë en hausse — surveiller la récupération." />
          <app-readiness-gauge level="red" [fatigue]="8" [pain]="6" [acwr]="1.6" />
        </div>
      </section>

      <!-- ACWR -->
      <section class="card" style="margin-bottom: var(--sp-6);">
        <h3>ACWR (bande de sécurité annotée)</h3>
        <div class="gauges">
          <app-acwr-indicator [value]="0.7" />
          <app-acwr-indicator [value]="1.05" />
          <app-acwr-indicator [value]="1.35" />
          <app-acwr-indicator [value]="1.62" />
        </div>
      </section>

      <!-- Effort RPE / RIR -->
      <section class="card" style="margin-bottom: var(--sp-6);">
        <h3>Effort prescrit vs ressenti</h3>
        <div class="row">
          <app-effort-badge kind="RPE" [min]="6" [max]="7" />
          <app-effort-badge kind="RPE" [min]="6" [max]="7" [actual]="7" />
          <app-effort-badge kind="RPE" [min]="6" [max]="7" [actual]="9" />
          <app-effort-badge kind="RIR" [min]="1" [max]="2" />
          <app-effort-badge kind="RIR" [min]="1" [max]="2" [actual]="0" />
        </div>
      </section>

      <!-- Retour athlète : check 10 s -->
      <section class="card" style="margin-bottom: var(--sp-6);">
        <h3>Retour athlète (check 10 s)</h3>
        <div class="feedback">
          <app-pain-fatigue-selector kind="fatigue" [(value)]="fatigue" />
          <app-pain-fatigue-selector kind="pain" [(value)]="pain" />
        </div>
      </section>

      <!-- Primitives UI transverses -->
      <section class="card" style="margin-bottom: var(--sp-6);">
        <h3>Cartes métriques</h3>
        <div class="metrics">
          <app-metric-card label="VDOT" [value]="54" origin="estime" [delta]="2" deltaLabel="vs M-1" />
          <app-metric-card label="CTL (forme)" [value]="78" unit="UA" origin="calcule" [delta]="4" />
          <app-metric-card label="Charge aiguë" [value]="412" unit="UA" tone="alert" [delta]="9" goodWhen="down" />
          <app-metric-card label="Compliance" [loading]="true" />
        </div>
      </section>

      <section class="card" style="margin-bottom: var(--sp-6);">
        <h3>Contrôle segmenté</h3>
        <div class="row">
          <app-segmented-control [options]="viewOptions" [(value)]="calendarView" />
          <span class="subtitle">Vue : {{ calendarView() }}</span>
        </div>
      </section>

      <section class="card">
        <h3>Bottom sheet &amp; barre d'action</h3>
        <button class="btn btn-primary" (click)="sheetOpen.set(true)">Ouvrir le bottom sheet</button>
        <app-sticky-action-bar [floating]="true" style="margin-top: var(--sp-4);">
          <div slot="info">Séance du jour · Tempo 8 km</div>
          <button class="btn btn-ghost btn-sm">Reporter</button>
          <button class="btn btn-primary btn-sm">Démarrer</button>
        </app-sticky-action-bar>
      </section>
    </div>

    <app-bottom-sheet [(open)]="sheetOpen" title="Déplacer la séance">
      <p class="subtitle">Choisissez un nouveau jour. Le contenu de la séance reste inchangé.</p>
      <app-segmented-control [options]="dayOptions" [(value)]="targetDay" [ariaLabel]="'Jour cible'" />
    </app-bottom-sheet>
  `,
  styles: [`
    .row { display: flex; flex-wrap: wrap; align-items: center; gap: var(--sp-3); margin-top: var(--sp-3); }
    .gauges { display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: var(--sp-4); margin-top: var(--sp-3); }
    .feedback { display: flex; flex-direction: column; gap: var(--sp-5); margin-top: var(--sp-3); max-width: 520px; }
    .metrics { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: var(--sp-4); margin-top: var(--sp-3); }
    h3 { margin: 0; }
  `],
})
export class UiKitComponent {
  /** Exemple de formateur : secondes/km → m:ss. */
  protected readonly paceFmt = (sec: number): string => {
    const m = Math.floor(sec / 60);
    const s = Math.round(sec % 60);
    return `${m}:${s.toString().padStart(2, '0')}`;
  };

  /** État local du check 10 s (démo two-way). */
  protected readonly fatigue = signal<number | null>(5);
  protected readonly pain = signal<number | null>(null);

  protected readonly viewOptions: SegmentOption[] = [
    { value: 'day', label: 'Jour' },
    { value: 'week', label: 'Semaine' },
    { value: 'month', label: 'Mois' },
  ];
  protected readonly calendarView = signal('week');

  protected readonly sheetOpen = signal(false);
  protected readonly dayOptions: SegmentOption[] = [
    { value: 'mon', label: 'Lun' },
    { value: 'tue', label: 'Mar' },
    { value: 'wed', label: 'Mer' },
    { value: 'thu', label: 'Jeu' },
    { value: 'fri', label: 'Ven' },
  ];
  protected readonly targetDay = signal('wed');
}
