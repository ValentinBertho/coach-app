import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import {
  BlockType,
  BlockFormat,
  StrengthBlock,
  StrengthExerciseItem,
  StrengthPrescriptionView,
} from '../../../core/models/strength.model';
import { EffortBadgeComponent, type EffortKind } from '../effort-badge/effort-badge.component';
import { RangePrescriptionPillComponent } from '../range-prescription-pill/range-prescription-pill.component';

const BLOCK_LABELS: Record<BlockType, string> = {
  ECHAUFFEMENT: 'Échauffement',
  ACTIVATION: 'Activation',
  PRINCIPAL: 'Bloc principal',
  ACCESSOIRE: 'Accessoire',
  CALME: 'Retour au calme',
};

const FORMAT_LABELS: Record<BlockFormat, string> = {
  CLASSIQUE: '',
  EMOM: 'EMOM',
  AMRAP: 'AMRAP',
  FOR_TIME: 'For time',
  CIRCUIT: 'Circuit',
  ISOMETRIE: 'Isométrie',
  PLIOMETRIE: 'Pliométrie',
};

/** Ligne d'exercice aplatie : item prescrit + charge calculée pour l'athlète. */
interface ExerciseRow {
  item: StrengthExerciseItem;
  kgMin: number | null;
  kgMax: number | null;
}
interface BlockRow {
  id: string;
  title: string;
  format: string;
  volume: string;
  exercises: ExerciseRow[];
}

/**
 * Affichage en lecture seule de la prescription d'une séance de **force** planifiée, en
 * fourchettes (CDC §1 : jamais de valeur sèche) : blocs, exercices, charge calculée pour
 * l'athlète, reps, effort (RPE/RIR), tempo et récupération.
 *
 * Rend le snapshot figé au moment de l'assignation ; les charges viennent du calcul
 * backend (e1RM de l'athlète). Partagé entre le calendrier coach et les écrans force.
 */
@Component({
  selector: 'app-strength-prescription-view',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RangePrescriptionPillComponent, EffortBadgeComponent],
  template: `
    @if (blocks().length) {
      <div class="spv">
        @for (b of blocks(); track b.id) {
          <section class="spv__block">
            <header class="spv__head">
              <span class="spv__type">{{ b.title }}</span>
              @if (b.format) { <span class="badge badge-neutral">{{ b.format }}</span> }
              @if (b.volume) { <span class="spv__vol metric">{{ b.volume }}</span> }
            </header>

            @for (ex of b.exercises; track $index) {
              <div class="spv__ex">
                <div class="spv__ex-name">
                  <strong>{{ ex.item.exerciseName }}</strong>
                  @if (setsLabel(ex); as s) { <span class="spv__sets metric">{{ s }}</span> }
                </div>
                <div class="spv__pills">
                  @if (ex.kgMin != null) {
                    <app-range-prescription-pill label="Charge" [min]="ex.kgMin" [max]="ex.kgMax ?? ex.kgMin" unit="kg" />
                  } @else if (ex.item.prescription.chargePctRmMin != null) {
                    <app-range-prescription-pill label="Charge"
                      [min]="ex.item.prescription.chargePctRmMin"
                      [max]="ex.item.prescription.chargePctRmMax ?? ex.item.prescription.chargePctRmMin"
                      unit="% 1RM" />
                  }
                  @if (repsMin(ex) != null) {
                    <app-range-prescription-pill label="Reps" [min]="repsMin(ex)" [max]="repsMax(ex)" />
                  }
                  @if (effortKind(ex); as kind) {
                    @if (effortMin(ex); as emin) {
                      <app-effort-badge [kind]="kind" [min]="emin" [max]="effortMax(ex) ?? emin" />
                    }
                  }
                  @if (ex.item.prescription.restSecMin != null) {
                    <app-range-prescription-pill label="Récup"
                      [min]="ex.item.prescription.restSecMin"
                      [max]="ex.item.prescription.restSecMax ?? ex.item.prescription.restSecMin"
                      unit="s" />
                  }
                  @if (ex.item.prescription.tempo) {
                    <span class="spv__tempo">Tempo {{ ex.item.prescription.tempo }}</span>
                  }
                </div>
                @if (ex.item.coachNotes) { <p class="spv__notes">{{ ex.item.coachNotes }}</p> }
              </div>
            }
          </section>
        }
      </div>
    } @else {
      <p class="field-hint">Aucun contenu détaillé pour cette séance.</p>
    }
  `,
  styles: [`
    .spv { display: flex; flex-direction: column; gap: var(--sp-4); }
    .spv__block { display: flex; flex-direction: column; gap: var(--sp-2); }
    .spv__head { display: flex; align-items: center; gap: var(--sp-2); flex-wrap: wrap; }
    .spv__type { font-weight: 700; color: var(--ink); }
    .spv__vol { color: var(--ink-3); font-size: var(--text-sm); }
    .spv__ex {
      display: flex; flex-direction: column; gap: var(--sp-2);
      padding: var(--sp-3); border: 1px solid var(--hairline); border-radius: var(--radius-md);
      background: var(--paper-sunk);
    }
    .spv__ex-name { display: flex; align-items: baseline; gap: var(--sp-2); justify-content: space-between; }
    .spv__sets { color: var(--ink-3); font-size: var(--text-sm); }
    .spv__pills { display: flex; flex-wrap: wrap; gap: var(--sp-2); align-items: center; }
    .spv__tempo {
      font-family: var(--font-data); font-size: var(--text-xs); color: var(--ink-2);
      border: 1px solid var(--hairline); border-radius: var(--radius-full); padding: 2px var(--sp-2);
    }
    .spv__notes { margin: 0; font-size: var(--text-sm); color: var(--ink-2); }
  `],
})
export class StrengthPrescriptionViewComponent {
  /** Prescription figée + charges calculées (endpoint `/scheduled/{id}/prescription`). */
  readonly prescription = input<StrengthPrescriptionView | null>(null);

  /**
   * Source d'affichage : le calcul backend quand il existe (il porte l'item prescrit ET la
   * charge en kg pour cet athlète), sinon le snapshot figé seul (charges en % 1RM).
   */
  readonly blocks = computed<BlockRow[]>(() => {
    const p = this.prescription();
    if (!p) return [];
    const calculated = p.calculated?.blocks ?? [];
    if (calculated.length) {
      return calculated.map((e) => ({
        ...this.blockHeader(e.block),
        exercises: e.exercises.map((x) => ({
          item: x.item, kgMin: x.charge.kgMin, kgMax: x.charge.kgMax,
        })),
      }));
    }
    return (p.snapshot?.blocks ?? []).map((b) => ({
      ...this.blockHeader(b),
      exercises: b.exercises.map((item) => ({ item, kgMin: null, kgMax: null })),
    }));
  });

  private blockHeader(b: StrengthBlock): Omit<BlockRow, 'exercises'> {
    return {
      id: b.id,
      title: BLOCK_LABELS[b.blockType] ?? b.blockType,
      format: FORMAT_LABELS[b.format] ?? '',
      volume: this.blockVolume(b.rounds, b.durationSec, b.workSec, b.restSec),
    };
  }

  private blockVolume(rounds?: number | null, durationSec?: number | null,
                      workSec?: number | null, restSec?: number | null): string {
    const parts: string[] = [];
    if (rounds) parts.push(`${rounds} tours`);
    if (durationSec) parts.push(`${Math.round(durationSec / 60)} min`);
    if (workSec) parts.push(`${workSec} s d'effort`);
    if (restSec) parts.push(`${restSec} s de récup`);
    return parts.join(' · ');
  }

  setsLabel(ex: ExerciseRow): string {
    const s = ex.item.prescription.sets;
    return s ? `${s} série${s > 1 ? 's' : ''}` : '';
  }

  repsMin(ex: ExerciseRow): number | null {
    const p = ex.item.prescription;
    return p.repsFixed ?? p.repsMin ?? null;
  }
  repsMax(ex: ExerciseRow): number | null {
    const p = ex.item.prescription;
    return p.repsFixed ?? p.repsMax ?? p.repsMin ?? null;
  }

  effortKind(ex: ExerciseRow): EffortKind | null {
    const t = ex.item.prescription.effortRefType;
    if (!t) return null;
    return t === 'RIR' || t === 'RIR_RANGE' ? 'RIR' : 'RPE';
  }
  effortMin(ex: ExerciseRow): number | null {
    const p = ex.item.prescription;
    return this.effortKind(ex) === 'RIR' ? p.rirMin ?? null : p.rpeMin ?? null;
  }
  effortMax(ex: ExerciseRow): number | null {
    const p = ex.item.prescription;
    return this.effortKind(ex) === 'RIR' ? p.rirMax ?? p.rirMin ?? null : p.rpeMax ?? p.rpeMin ?? null;
  }
}
