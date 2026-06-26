import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { CalculatedBlockEntry, CourseDrill, WorkoutPrescription } from '../../../core/models/course.model';
import { RangePrescriptionPillComponent } from '../range-prescription-pill/range-prescription-pill.component';

interface Section { key: 'warmup' | 'main' | 'cooldown'; label: string; }

/**
 * Affichage en lecture seule de la prescription course en **fourchettes** (CDC §1 :
 * jamais de valeur sèche). Rend le snapshot figé + les cibles calculées pour l'athlète
 * (allure min–max, FC, RPE) par bloc, échauffement / corps / retour au calme.
 *
 * Partagé entre la séance du jour (athlète) et la fiche séance (coach) pour garantir
 * un rendu identique des cibles. Si aucune cible n'est calculable (pas de snapshot
 * structuré), le parent retombe sur l'ancien rendu par zones.
 */
@Component({
  selector: 'app-course-prescription-view',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RangePrescriptionPillComponent],
  template: `
    @if (hasContent()) {
      <div class="cpv">
        @for (sec of sections; track sec.key) {
          @if (entries(sec.key).length) {
            <section class="cpv__sec">
              <h4 class="cpv__sec-title">{{ sec.label }}</h4>
              @for (e of entries(sec.key); track e.block.id) {
                <div class="cpv__block">
                  <div class="cpv__head">
                    <span class="cpv__type">{{ blockTitle(e) }}</span>
                    @if (volume(e); as v) { <span class="cpv__vol">{{ v }}</span> }
                  </div>
                  <div class="cpv__pills">
                    @if (e.calc?.computable && e.calc?.paceMinLabel) {
                      <app-range-prescription-pill
                        label="Allure"
                        [min]="e.calc!.paceMinSecPerKm"
                        [max]="e.calc!.paceMaxSecPerKm"
                        [format]="paceFmt" unit="/km" />
                      @if (e.calc?.paceEstimated) {
                        <span class="cpv__est" title="Allure estimée à partir du VDOT (pas de test lactate)">estimée</span>
                      }
                    }
                    @if (e.calc?.hrMin != null) {
                      <app-range-prescription-pill label="FC" [min]="e.calc!.hrMin" [max]="e.calc!.hrMax" unit="bpm" />
                    }
                    @if (e.calc?.rpeMin != null) {
                      <app-range-prescription-pill label="RPE" [min]="e.calc!.rpeMin" [max]="e.calc!.rpeMax" />
                    }
                  </div>
                  @if (e.block.recovery && e.recoveryCalc?.paceMinLabel) {
                    <div class="cpv__rec">
                      Récup : {{ recoveryVol(e) }}
                      @if (e.recoveryCalc?.computable) {
                        <span class="cpv__rec-pace">{{ e.recoveryCalc!.paceMinLabel }}–{{ e.recoveryCalc!.paceMaxLabel }} /km</span>
                      }
                    </div>
                  }
                  @if (drillsOf(e); as ds) {
                    @if (ds.length) {
                      <div class="cpv__drills">
                        <span class="cpv__drills-lb">Éducatifs</span>
                        @for (d of ds; track d.id) {
                          @if (d.videoUrl) {
                            <a class="cpv__drill" [href]="d.videoUrl" target="_blank" rel="noopener">▶ {{ d.name }}</a>
                          } @else {
                            <span class="cpv__drill">{{ d.name }}</span>
                          }
                        }
                      </div>
                    }
                  }
                  @if (e.block.note) { <p class="cpv__note">{{ e.block.note }}</p> }
                </div>
              }
            </section>
          }
        }
        @if (hasEstimated()) {
          <p class="cpv__legend">⚠︎ Allures <strong>estimées</strong> à partir du VDOT (aucun test lactate).
            Un test lactate affinera tes seuils.</p>
        }
      </div>
    }
  `,
  styles: [`
    .cpv { display: flex; flex-direction: column; gap: var(--sp-3); }
    .cpv__sec { display: flex; flex-direction: column; gap: var(--sp-2); }
    .cpv__sec-title { margin: 0; font-size: var(--text-xs); text-transform: uppercase; letter-spacing: .05em; color: var(--ink-3); }
    .cpv__block { display: flex; flex-direction: column; gap: 6px; padding: var(--sp-2) var(--sp-3); border: 1px solid var(--hairline); border-radius: var(--radius-md); background: var(--surface, transparent); }
    .cpv__head { display: flex; align-items: baseline; justify-content: space-between; gap: var(--sp-2); }
    .cpv__type { font-weight: 700; color: var(--ink); }
    .cpv__vol { font-variant-numeric: tabular-nums; color: var(--ink-2); font-size: var(--text-sm); }
    .cpv__pills { display: flex; flex-wrap: wrap; gap: 6px; }
    .cpv__rec { font-size: var(--text-sm); color: var(--ink-3); }
    .cpv__rec-pace { font-variant-numeric: tabular-nums; color: var(--ink-2); }
    .cpv__note { margin: 0; font-size: var(--text-sm); color: var(--ink-3); font-style: italic; }
    .cpv__drills { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; }
    .cpv__drills-lb { font-size: var(--text-xs); text-transform: uppercase; letter-spacing: .05em; color: var(--ink-3); }
    .cpv__drill { font-size: var(--text-sm); color: var(--primary); text-decoration: none; padding: 2px 8px; border: 1px solid var(--hairline); border-radius: var(--radius-full); }
    a.cpv__drill:hover { background: var(--paper-sunk); }
    .cpv__est { align-self: center; font-size: var(--text-xs); font-weight: 700; text-transform: uppercase; letter-spacing: .04em; color: var(--warning-text, var(--ink-2)); background: var(--warning-bg); padding: 2px 8px; border-radius: var(--radius-full); }
    .cpv__legend { margin: 0; font-size: var(--text-xs); color: var(--ink-3); }
  `],
})
export class CoursePrescriptionViewComponent {
  readonly prescription = input.required<WorkoutPrescription | null>();

  readonly sections: Section[] = [
    { key: 'warmup', label: 'Échauffement' },
    { key: 'main', label: 'Corps de séance' },
    { key: 'cooldown', label: 'Retour au calme' },
  ];

  readonly paceFmt = (secPerKm: number): string => {
    const m = Math.floor(secPerKm / 60);
    const s = Math.round(secPerKm % 60);
    return `${m}:${s.toString().padStart(2, '0')}`;
  };

  /** Au moins un bloc dans le snapshot calculé : sinon le parent garde le rendu zones. */
  readonly hasContent = computed(() => {
    const c = this.prescription()?.calculated;
    return !!c && (c.warmup.length + c.main.length + c.cooldown.length) > 0;
  });

  /** Au moins une allure estimée (VDOT, sans seuil mesuré) → affiche la légende. */
  readonly hasEstimated = computed(() => {
    const c = this.prescription()?.calculated;
    if (!c) return false;
    return [...c.warmup, ...c.main, ...c.cooldown].some((e) => e.calc?.paceEstimated);
  });

  /** Index id → éducatif, pour résoudre les drills attachés à un bloc. */
  readonly drillsById = computed(() => {
    const map = new Map<string, CourseDrill>();
    for (const d of this.prescription()?.drills ?? []) { map.set(d.id, d); }
    return map;
  });

  entries(key: Section['key']): CalculatedBlockEntry[] {
    return this.prescription()?.calculated?.[key] ?? [];
  }

  drillsOf(e: CalculatedBlockEntry): CourseDrill[] {
    const ids = e.block.drillIds ?? [];
    const map = this.drillsById();
    return ids.map((id) => map.get(id)).filter((d): d is CourseDrill => !!d);
  }

  blockTitle(e: CalculatedBlockEntry): string {
    const t = e.block.type ?? 'Bloc';
    return t.charAt(0).toUpperCase() + t.slice(1);
  }

  volume(e: CalculatedBlockEntry): string | null {
    const b = e.block;
    const unit = this.distOrTime(b.distanceM, b.durationS);
    if (!unit) return null;
    return b.reps && b.reps > 1 ? `${b.reps} × ${unit}` : unit;
  }

  recoveryVol(e: CalculatedBlockEntry): string {
    const r = e.block.recovery!;
    return this.distOrTime(r.distanceM ?? null, r.durationS ?? null) ?? '';
  }

  private distOrTime(distanceM: number | null | undefined, durationS: number | null | undefined): string | null {
    if (distanceM) return distanceM >= 1000 ? `${(distanceM / 1000).toFixed(distanceM % 1000 ? 1 : 0)} km` : `${distanceM} m`;
    if (durationS) {
      const m = Math.floor(durationS / 60);
      const s = durationS % 60;
      return s ? `${m}:${s.toString().padStart(2, '0')}` : `${m} min`;
    }
    return null;
  }
}
