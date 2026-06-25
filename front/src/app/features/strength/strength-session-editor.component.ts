import { CdkDragDrop, DragDropModule, moveItemInArray } from '@angular/cdk/drag-drop';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AthleteService } from '../../core/services/athlete.service';
import { StrengthService } from '../../core/services/strength.service';
import { ToastService } from '../../core/services/toast.service';
import { AthleteSummary } from '../../core/models/athlete.model';
import {
  BlockFormat,
  BlockType,
  ChargeRefType,
  ChargeTarget,
  EffortRefType,
  PpExercise,
  SetType,
  StrengthBlock,
  StrengthExerciseItem,
  StrengthPrescription,
} from '../../core/models/strength.model';
import {
  EffortBadgeComponent,
  type EffortKind,
  RangePrescriptionPillComponent,
} from '../../shared/components/physiology';
import { SegmentedControlComponent, type SegmentOption, SidePanelComponent } from '../../shared/components/ui';

/**
 * Éditeur de structure d'une séance de force (cf. DARI Lab) : blocs typés, formats avancés
 * (EMOM / AMRAP / Circuit / Isométrie / Pliométrie), exercices avec type de série et prescription
 * (charge + effort indépendants, volume, tempo, repos).
 */
@Component({
  selector: 'app-strength-session-editor',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, 
    FormsModule, RouterLink, DragDropModule,
    SegmentedControlComponent, RangePrescriptionPillComponent, EffortBadgeComponent,
    SidePanelComponent,
  ],
  templateUrl: './strength-session-editor.component.html',
  styleUrl: './strength-session-editor.component.scss',
})
export class StrengthSessionEditorComponent implements OnInit {
  readonly sessionId = input.required<string>();

  private readonly strength = inject(StrengthService);
  private readonly athletes = inject(AthleteService);
  private readonly toast = inject(ToastService);

  readonly name = signal('');
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly blocks = signal<StrengthBlock[]>([]);
  readonly exercises = signal<PpExercise[]>([]);
  readonly exercisePick: Record<string, string> = {};

  // Aperçu live des charges
  readonly athleteList = signal<AthleteSummary[]>([]);
  readonly previewAthlete = signal('');
  readonly chargePreview = signal<Record<string, ChargeTarget>>({});
  private recomputeTimer?: ReturnType<typeof setTimeout>;

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

  // --- Mode édition / aperçu athlète ---
  readonly viewMode = signal<'edit' | 'preview'>('edit');
  readonly viewOptions: SegmentOption[] = [
    { value: 'edit', label: 'Édition' },
    { value: 'preview', label: 'Aperçu athlète' },
  ];

  // --- Panneau latéral d'édition d'un exercice ---
  readonly panelOpen = signal(false);
  readonly editingBlock = signal<StrengthBlock | null>(null);
  readonly editingItem = signal<StrengthExerciseItem | null>(null);

  openEditor(block: StrengthBlock, item: StrengthExerciseItem): void {
    this.editingBlock.set(block);
    this.editingItem.set(item);
    this.panelOpen.set(true);
  }

  blockTypeLabel(t: BlockType): string { return this.blockTypes.find((x) => x.value === t)?.label ?? t; }
  formatLabel(f: BlockFormat): string { return this.formats.find((x) => x.value === f)?.label ?? f; }

  /** Fourchette de charge à afficher (lecture seule), ou null si non renseignée. */
  chargePill(p: StrengthPrescription): { min: number; max: number; unit: string } | null {
    switch (p.chargeRefType) {
      case 'KG_RANGE': return p.chargeKgMin != null ? { min: p.chargeKgMin, max: p.chargeKgMax ?? p.chargeKgMin, unit: 'kg' } : null;
      case 'KG_FIXE': return p.chargeKgMin != null ? { min: p.chargeKgMin, max: p.chargeKgMin, unit: 'kg' } : null;
      case 'PCT_RM_RANGE': return p.chargePctRmMin != null ? { min: p.chargePctRmMin, max: p.chargePctRmMax ?? p.chargePctRmMin, unit: '% RM' } : null;
      case 'PCT_RM': return p.chargePctRmMin != null ? { min: p.chargePctRmMin, max: p.chargePctRmMin, unit: '% RM' } : null;
      default: return null;
    }
  }

  /** Volume (reps ou durée d'isométrie) à afficher. */
  repsInfo(block: StrengthBlock, p: StrengthPrescription): { label: string; min: number; max: number; unit: string } | null {
    if (block.format === 'ISOMETRIE') {
      return p.durationSec != null ? { label: 'Durée', min: p.durationSec, max: p.durationSec, unit: 's' } : null;
    }
    if (p.repsFixed != null) return { label: 'Reps', min: p.repsFixed, max: p.repsFixed, unit: '' };
    if (p.repsMin != null) return { label: 'Reps', min: p.repsMin, max: p.repsMax ?? p.repsMin, unit: '' };
    return null;
  }

  /** Effort prescrit (RPE/RIR) à afficher, ou null. */
  effortInfo(p: StrengthPrescription): { kind: EffortKind; min: number; max: number } | null {
    switch (p.effortRefType) {
      case 'RIR_RANGE': return p.rirMin != null ? { kind: 'RIR', min: p.rirMin, max: p.rirMax ?? p.rirMin } : null;
      case 'RIR': return p.rirMin != null ? { kind: 'RIR', min: p.rirMin, max: p.rirMin } : null;
      case 'RPE_RANGE': return p.rpeMin != null ? { kind: 'RPE', min: p.rpeMin, max: p.rpeMax ?? p.rpeMin } : null;
      case 'RPE': return p.rpeMin != null ? { kind: 'RPE', min: p.rpeMin, max: p.rpeMin } : null;
      default: return null;
    }
  }

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
    this.athletes.list({ page: 0 }).subscribe((p) => this.athleteList.set(p.content));
  }

  // --- Aperçu live des charges ---
  onAthleteChange(id: string): void {
    this.previewAthlete.set(id);
    this.recompute();
  }

  /** Recalcule les charges de la structure courante pour l'athlète sélectionné (débounce). */
  private recompute(): void {
    const a = this.previewAthlete();
    if (!a) {
      this.chargePreview.set({});
      return;
    }
    clearTimeout(this.recomputeTimer);
    this.recomputeTimer = setTimeout(() => {
      this.strength.calculatePreview(a, { blocks: this.blocks() }).subscribe((res) => {
        const map: Record<string, ChargeTarget> = {};
        res.blocks.forEach((b, bi) =>
          b.exercises.forEach((ex, ei) => { map[`${bi}:${ei}`] = ex.charge; }));
        this.chargePreview.set(map);
      });
    }, 350);
  }

  chargeAt(bi: number, ei: number): ChargeTarget | undefined {
    return this.chargePreview()[`${bi}:${ei}`];
  }

  // --- Blocs ---
  drop(event: CdkDragDrop<StrengthBlock[]>): void {
    const list = [...this.blocks()];
    moveItemInArray(list, event.previousIndex, event.currentIndex);
    this.blocks.set(list);
    this.recompute();
  }

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

  /** Force la propagation du signal après mutation interne d'un bloc + rafraîchit l'aperçu. */
  touch(): void {
    this.blocks.set([...this.blocks()]);
    this.recompute();
  }

  save(): void {
    this.saving.set(true);
    this.strength.putStructure(this.sessionId(), { blocks: this.blocks() }).subscribe({
      next: () => {
        this.toast.success('Structure enregistrée');
        this.saving.set(false);
      },
      error: () => this.saving.set(false),
    });
  }

  label(value: string): string {
    return value.replace(/_/g, ' ').toLowerCase().replace(/^\w/, (c) => c.toUpperCase());
  }
}
