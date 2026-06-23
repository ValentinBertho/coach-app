import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { StrengthService } from '../../core/services/strength.service';
import { ToastService } from '../../core/services/toast.service';
import {
  BlockFormat,
  BlockType,
  ChargeRefType,
  EffortRefType,
  PpExercise,
  SetType,
  StrengthBlock,
  StrengthExerciseItem,
} from '../../core/models/strength.model';

/**
 * Éditeur de structure d'une séance de force (cf. DARI Lab) : blocs typés, formats avancés
 * (EMOM / AMRAP / Circuit / Isométrie / Pliométrie), exercices avec type de série et prescription
 * (charge + effort indépendants, volume, tempo, repos).
 */
@Component({
  selector: 'app-strength-session-editor',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink],
  templateUrl: './strength-session-editor.component.html',
  styleUrl: './strength-session-editor.component.scss',
})
export class StrengthSessionEditorComponent implements OnInit {
  readonly sessionId = input.required<string>();

  private readonly strength = inject(StrengthService);
  private readonly toast = inject(ToastService);

  readonly name = signal('');
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly blocks = signal<StrengthBlock[]>([]);
  readonly exercises = signal<PpExercise[]>([]);
  readonly exercisePick: Record<string, string> = {};

  readonly blockTypes: { value: BlockType; label: string }[] = [
    { value: 'ECHAUFFEMENT', label: 'Échauffement' },
    { value: 'ACTIVATION', label: 'Activation' },
    { value: 'PRINCIPAL', label: 'Principal' },
    { value: 'ACCESSOIRE', label: 'Accessoire' },
    { value: 'CALME', label: 'Retour au calme' },
  ];
  readonly formats: { value: BlockFormat; label: string }[] = [
    { value: 'CLASSIQUE', label: 'Classique (séries)' },
    { value: 'EMOM', label: 'EMOM' },
    { value: 'AMRAP', label: 'AMRAP' },
    { value: 'FOR_TIME', label: 'For Time' },
    { value: 'CIRCUIT', label: 'Circuit' },
    { value: 'ISOMETRIE', label: 'Isométrie' },
    { value: 'PLIOMETRIE', label: 'Pliométrie' },
  ];
  readonly setTypes: { value: SetType; label: string }[] = [
    { value: 'STANDARD', label: 'Standard' },
    { value: 'DROP_SET', label: 'Drop set' },
    { value: 'SUPER_SET', label: 'Super set' },
    { value: 'MYO_REPS', label: 'Myo-reps' },
    { value: 'CLUSTER', label: 'Cluster' },
    { value: 'ISO_OVERCOMING', label: 'Iso (overcoming)' },
    { value: 'ISO_YIELDING', label: 'Iso (yielding)' },
  ];
  readonly chargeRefs: { value: ChargeRefType; label: string }[] = [
    { value: 'PCT_RM_RANGE', label: '% RM (fourchette)' },
    { value: 'PCT_RM', label: '% RM' },
    { value: 'KG_RANGE', label: 'kg (fourchette)' },
    { value: 'KG_FIXE', label: 'kg fixe' },
    { value: 'RM_CIBLE', label: 'RM cible' },
    { value: 'RM_ESTIME', label: 'RM estimé' },
  ];
  readonly effortRefs: { value: EffortRefType; label: string }[] = [
    { value: 'RIR_RANGE', label: 'RIR (fourchette)' },
    { value: 'RIR', label: 'RIR' },
    { value: 'RPE_RANGE', label: 'RPE (fourchette)' },
    { value: 'RPE', label: 'RPE' },
  ];

  readonly empty = computed(() => this.blocks().length === 0);

  ngOnInit(): void {
    this.strength.getSession(this.sessionId()).subscribe({
      next: (s) => {
        this.name.set(s.name);
        this.blocks.set(s.structure?.blocks ?? []);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
    this.strength.listExercises({ page: 0 }).subscribe((p) => this.exercises.set(p.content));
  }

  // --- Blocs ---
  addBlock(): void {
    const block: StrengthBlock = {
      id: 'b-' + Math.random().toString(36).slice(2, 9),
      blockType: 'PRINCIPAL',
      format: 'CLASSIQUE',
      durationSec: null,
      rounds: null,
      workSec: null,
      restSec: null,
      exercises: [],
    };
    this.blocks.update((list) => [...list, block]);
  }

  removeBlock(id: string): void {
    this.blocks.update((list) => list.filter((b) => b.id !== id));
  }

  moveBlock(id: string, dir: -1 | 1): void {
    const list = [...this.blocks()];
    const i = list.findIndex((b) => b.id === id);
    const j = i + dir;
    if (i < 0 || j < 0 || j >= list.length) return;
    [list[i], list[j]] = [list[j], list[i]];
    this.blocks.set(list);
  }

  onFormatChange(block: StrengthBlock): void {
    // Valeurs par défaut sensées selon le format.
    block.durationSec = ['EMOM', 'AMRAP', 'FOR_TIME'].includes(block.format) ? 600 : null;
    block.rounds = block.format === 'CIRCUIT' ? 3 : null;
    block.workSec = block.format === 'CIRCUIT' ? 40 : null;
    block.restSec = block.format === 'CIRCUIT' ? 20 : null;
    this.touch();
  }

  showDuration(block: StrengthBlock): boolean {
    return ['EMOM', 'AMRAP', 'FOR_TIME'].includes(block.format);
  }
  showCircuit(block: StrengthBlock): boolean {
    return block.format === 'CIRCUIT';
  }

  // --- Exercices d'un bloc ---
  addExercise(block: StrengthBlock): void {
    const exId = this.exercisePick[block.id];
    if (!exId) return;
    const ex = this.exercises().find((e) => e.id === exId);
    const item: StrengthExerciseItem = {
      exerciseId: exId,
      exerciseName: ex?.name ?? '',
      setType: 'STANDARD',
      prescription: {
        chargeRefType: 'PCT_RM_RANGE', chargePctRmMin: 70, chargePctRmMax: 80,
        effortRefType: 'RIR_RANGE', rirMin: 1, rirMax: 3,
        sets: 4, repsFixed: 6, restSecMin: 90, restSecMax: 120,
      },
    };
    block.exercises = [...block.exercises, item];
    this.exercisePick[block.id] = '';
    this.touch();
  }

  removeExercise(block: StrengthBlock, idx: number): void {
    block.exercises = block.exercises.filter((_, i) => i !== idx);
    this.touch();
  }

  /** Force la propagation du signal après mutation interne d'un bloc. */
  touch(): void {
    this.blocks.set([...this.blocks()]);
  }

  save(): void {
    this.saving.set(true);
    this.strength.putStructure(this.sessionId(), { blocks: this.blocks() }).subscribe({
      next: () => {
        this.toast.success('Structure enregistrée ✅');
        this.saving.set(false);
      },
      error: () => this.saving.set(false),
    });
  }

  label(value: string): string {
    return value.replace(/_/g, ' ').toLowerCase().replace(/^\w/, (c) => c.toUpperCase());
  }
}
