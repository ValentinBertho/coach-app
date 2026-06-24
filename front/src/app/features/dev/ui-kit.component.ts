import { ChangeDetectionStrategy, Component } from '@angular/core';
import {
  DataOriginTagComponent,
  IntensityZoneBadgeComponent,
  RangePrescriptionPillComponent,
  ReadinessGaugeComponent,
} from '../../shared/components/physiology';

/**
 * Living styleguide des primitives physiologie (cf. docs/ux-redesign-blueprint.md).
 * Sert de banc de validation visuelle ET de référence d'usage pour l'équipe.
 * Route dev : /dev/ui-kit.
 */
@Component({
  selector: 'app-ui-kit',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DataOriginTagComponent,
    IntensityZoneBadgeComponent,
    RangePrescriptionPillComponent,
    ReadinessGaugeComponent,
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
      <section class="card">
        <h3>État de forme (jamais dérivé du RPE seul)</h3>
        <div class="gauges">
          <app-readiness-gauge level="green" [fatigue]="3" [pain]="0" [acwr]="1.05" />
          <app-readiness-gauge level="orange" [fatigue]="6" [pain]="2" [acwr]="1.35"
            note="Charge aiguë en hausse — surveiller la récupération." />
          <app-readiness-gauge level="red" [fatigue]="8" [pain]="6" [acwr]="1.6" />
        </div>
      </section>
    </div>
  `,
  styles: [`
    .row { display: flex; flex-wrap: wrap; align-items: center; gap: var(--sp-3); margin-top: var(--sp-3); }
    .gauges { display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: var(--sp-4); margin-top: var(--sp-3); }
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
}
