import {
  CdkDragDrop,
  DragDropModule,
  moveItemInArray,
} from '@angular/cdk/drag-drop';
import { BreakpointObserver } from '@angular/cdk/layout';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Observable, switchMap, tap } from 'rxjs';
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
  SideMode,
  StrengthBlock,
  StrengthExerciseItem,
  SetTarget,
  StrengthPrescription,
  StrengthSession,
  VolumeType,
} from '../../core/models/strength.model';
import { SessionCategory } from '../../core/models/session-category.model';
import { SessionCategoryService } from '../../core/services/session-category.service';
import {
  SIDE_MODE_LABELS,
  VOLUME_TYPE_LABELS,
  applySetRows,
  effectiveVolumeType,
  setRowsOf,
  setTargetsOf,
  restPill,
  sideLabel,
  volumePill,
  type VolumePill,
} from '../../core/utils/strength-volume';
import {
  EffortBadgeComponent,
  type EffortKind,
  RangePrescriptionPillComponent,
} from '../../shared/components/physiology';
import {
  SegmentedControlComponent,
  type SegmentOption,
  SidePanelComponent,
} from '../../shared/components/ui';
import { AutosaveBadgeComponent } from '../../shared/components/autosave-badge/autosave-badge.component';
import { Autosave } from '../../core/services/autosave';
import { HasAutosave } from '../../core/guards/unsaved-changes.guard';
import { SkeletonComponent } from '../../shared/components/skeleton/skeleton.component';
import { VideoEmbedComponent } from '../../shared/components/video-embed/video-embed.component';
import {
  SET_METHODS,
  SetMethod,
  SetMethodConfig,
  describeSetMethod,
  setMethod,
  setMethodConfig,
} from '../../core/utils/set-method';

/** Plafond de séries d'un exercice dans l'éditeur — au-delà, c'est une faute de frappe. */
const MAX_SET_ROWS = 20;

/**
 * Éditeur de structure d'une séance de force (cf. Darilab) : blocs typés, formats avancés
 * (EMOM / AMRAP / Circuit / Isométrie / Pliométrie), exercices avec type de série et prescription
 * (charge + effort indépendants, volume, tempo, repos).
 *
 * <p>Deux modes, selon les paramètres de route — comme l'éditeur course :</p>
 * <ul>
 *   <li><b>modèle</b> ({@code sessionId}) : édite la bibliothèque du club ;</li>
 *   <li><b>séance planifiée</b> ({@code athleteId} + {@code scheduledId}) : réécrit le contenu
 *       d'une séance <b>posée au calendrier</b>, pour cet athlète seul.</li>
 * </ul>
 *
 * <p>Le second mode manquait, et c'était le manque le plus coûteux de la prépa physique :
 * changer une série sur une séance déjà planifiée supposait de la déprogrammer, retoucher le
 * modèle — qui sert d'autres athlètes — puis replanifier. En pratique, les coachs créaient
 * <b>une séance de plus</b> à chaque ajustement.</p>
 */
@Component({
  selector: 'app-strength-session-editor',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    SkeletonComponent,
    IconComponent,
    FormsModule,
    RouterLink,
    DragDropModule,
    SegmentedControlComponent,
    RangePrescriptionPillComponent,
    EffortBadgeComponent,
    SidePanelComponent,
    AutosaveBadgeComponent,
    VideoEmbedComponent,
  ],
  templateUrl: './strength-session-editor.component.html',
  styleUrl: './strength-session-editor.component.scss',
})
export class StrengthSessionEditorComponent implements OnInit, HasAutosave {
  // Paramètres de route (component input binding). Un seul jeu est renseigné selon le mode.
  readonly sessionId = input<string>('');
  readonly scheduledId = input<string>('');
  readonly athleteId = input<string>('');

  /** Mode « séance planifiée » : on réécrit la séance d'un athlète, pas un modèle de club. */
  readonly isScheduled = computed(() => !!this.scheduledId());

  private readonly strength = inject(StrengthService);
  private readonly athletes = inject(AthleteService);
  private readonly categoryService = inject(SessionCategoryService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);

  /**
   * Écran assez large pour garder la bibliothèque d'exercices ouverte à côté de la séance.
   *
   * <p>Composer une séance de renfo, c'est enchaîner une dizaine d'ajouts : rouvrir un panneau
   * à chaque exercice coûtait un clic, une recherche et un regard perdu. Sur grand écran, la
   * bibliothèque reste à gauche et chaque exercice s'ajoute d'un « + » ou d'un glisser. Sur
   * téléphone et tablette, la place manque : le panneau latéral reste le geste.</p>
   */
  readonly wide = toSignal(
    inject(BreakpointObserver)
      .observe('(min-width: 1100px)')
      .pipe(map((r) => r.matches)),
    { initialValue: false },
  );

  /**
   * Auto-sauvegarde : construire une séance de force prend de longues minutes, et rien
   * n'avertissait avant de quitter l'écran. Chaque mutation passe par `touch()`.
   */
  readonly autosave = new Autosave(() => this.persist(), inject(DestroyRef));

  readonly name = signal('');
  /** Notes de la séance : relues à l'enregistrement du nom, que le contrat serveur remplace. */
  private readonly notes = signal<string | null>(null);
  /** Le nom a changé depuis la dernière écriture : sans ce drapeau, chaque sauvegarde de
   *  structure enverrait aussi un renommage inutile. */
  private nameDirty = false;
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly blocks = signal<StrengthBlock[]>([]);
  readonly exercises = signal<PpExercise[]>([]);
  /** Le catalogue est arrivé : avant, un sélecteur ouvert mentirait sur une bibliothèque vide. */
  readonly exercisesLoaded = signal(false);
  /** Bloc dont le sélecteur doit s'ouvrir dès que le catalogue est là. */
  private pendingPickerBlockId: string | null = null;
  /** Catégories de prépa physique du coach : elles rangent le choix des exercices. */
  readonly categories = signal<SessionCategory[]>([]);

  // Aperçu live des charges
  readonly athleteList = signal<AthleteSummary[]>([]);
  readonly previewAthlete = signal('');
  /** Athlète dont on modifie la séance (mode séance planifiée) : affiché, jamais choisi. */
  readonly athleteName = signal('');
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
    { value: 'SUPERSET', label: 'Superset' },
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
  readonly volumeTypes: { value: VolumeType; label: string }[] = [
    { value: 'REPS', label: VOLUME_TYPE_LABELS.REPS },
    { value: 'DUREE', label: VOLUME_TYPE_LABELS.DUREE },
    { value: 'DISTANCE', label: VOLUME_TYPE_LABELS.DISTANCE },
  ];
  readonly sideModes: { value: SideMode; label: string }[] = [
    { value: 'BILATERAL', label: SIDE_MODE_LABELS.BILATERAL },
    { value: 'ALTERNE', label: SIDE_MODE_LABELS.ALTERNE },
    { value: 'PAR_COTE', label: SIDE_MODE_LABELS.PAR_COTE },
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

  blockTypeLabel(t: BlockType): string {
    return this.blockTypes.find((x) => x.value === t)?.label ?? t;
  }
  formatLabel(f: BlockFormat): string {
    return this.formats.find((x) => x.value === f)?.label ?? f;
  }

  /** Fourchette de charge à afficher (lecture seule), ou null si non renseignée. */
  chargePill(
    p: StrengthPrescription,
  ): { min: number; max: number; unit: string } | null {
    switch (p.chargeRefType) {
      case 'KG_RANGE':
        return p.chargeKgMin != null
          ? {
              min: p.chargeKgMin,
              max: p.chargeKgMax ?? p.chargeKgMin,
              unit: 'kg',
            }
          : null;
      case 'KG_FIXE':
        return p.chargeKgMin != null
          ? { min: p.chargeKgMin, max: p.chargeKgMin, unit: 'kg' }
          : null;
      case 'PCT_RM_RANGE':
        return p.chargePctRmMin != null
          ? {
              min: p.chargePctRmMin,
              max: p.chargePctRmMax ?? p.chargePctRmMin,
              unit: '% RM',
            }
          : null;
      case 'PCT_RM':
        return p.chargePctRmMin != null
          ? { min: p.chargePctRmMin, max: p.chargePctRmMin, unit: '% RM' }
          : null;
      default:
        return null;
    }
  }

  /** Volume prescrit (reps, durée ou distance) à afficher — règle partagée avec l'athlète. */
  volumeInfo(block: StrengthBlock, p: StrengthPrescription): VolumePill | null {
    return volumePill(p, block.format);
  }

  /** Repos entre séries à afficher : strict ou fourchette, selon ce qui est prescrit. */
  restInfo(p: StrengthPrescription): VolumePill | null {
    return restPill(p);
  }

  /** « Par côté », « Alterné G / D » — rien en bilatéral. */
  sideInfo(p: StrengthPrescription): string | null {
    return sideLabel(p);
  }

  /** Effort prescrit (RPE/RIR) à afficher, ou null. */
  effortInfo(
    p: StrengthPrescription,
  ): { kind: EffortKind; min: number; max: number } | null {
    switch (p.effortRefType) {
      case 'RIR_RANGE':
        return p.rirMin != null
          ? { kind: 'RIR', min: p.rirMin, max: p.rirMax ?? p.rirMin }
          : null;
      case 'RIR':
        return p.rirMin != null
          ? { kind: 'RIR', min: p.rirMin, max: p.rirMin }
          : null;
      case 'RPE_RANGE':
        return p.rpeMin != null
          ? { kind: 'RPE', min: p.rpeMin, max: p.rpeMax ?? p.rpeMin }
          : null;
      case 'RPE':
        return p.rpeMin != null
          ? { kind: 'RPE', min: p.rpeMin, max: p.rpeMin }
          : null;
      default:
        return null;
    }
  }

  ngOnInit(): void {
    if (this.isScheduled()) {
      this.loadScheduled();
    } else {
      this.loadLibrarySession();
    }
    this.strength.listAllExercises().subscribe((e) => {
      this.exercises.set(e);
      this.exercisesLoaded.set(true);
      this.openPendingPicker();
    });
    this.loadStarterModels();
    this.categoryService.list('STRENGTH').subscribe({
      next: (c) => this.categories.set(c),
      // Sans catégories, le choix d'exercices se range par type — il reste utilisable.
      error: () => this.categories.set([]),
    });
    // Le sélecteur d'aperçu n'a de sens que sur un modèle : une séance planifiée a déjà son
    // athlète, et lui en proposer un autre inviterait à lire des charges qui ne sont pas les siennes.
    if (!this.isScheduled()) {
      this.athletes
        .list({ page: 0 })
        .subscribe((p) => this.athleteList.set(p.content));
    }
  }

  private loadLibrarySession(): void {
    this.strength.getSession(this.sessionId()).subscribe({
      next: (s) => {
        this.name.set(s.name);
        this.notes.set(s.notes);
        this.blocks.set(s.structure?.blocks ?? []);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  /**
   * Ouvre le choix des exercices pour ce bloc, dès que le catalogue est là.
   *
   * <p>L'attente n'est pas du confort : un sélecteur ouvert sur une bibliothèque pas encore
   * chargée annonce « aucun exercice — crée-les d'abord », ce qui est faux, et décourageant au
   * pire moment.</p>
   */
  private requestPicker(block: StrengthBlock): void {
    // Bibliothèque déjà ouverte à gauche : la section devient la cible des prochains ajouts,
    // sans panneau par-dessus.
    if (this.wide()) {
      this.pickerBlockId.set(block.id);
      return;
    }
    this.pendingPickerBlockId = block.id;
    this.openPendingPicker();
  }

  private openPendingPicker(): void {
    const id = this.pendingPickerBlockId;
    if (!id || !this.exercisesLoaded()) return;
    const block = this.blocks().find((b) => b.id === id);
    this.pendingPickerBlockId = null;
    if (block) this.openPicker(block);
  }

  /**
   * Charge la séance posée au calendrier : c'est son <b>snapshot</b> qu'on édite, pas le modèle
   * dont elle est issue. L'aperçu des charges est fixé sur l'athlète concerné — les kilos affichés
   * sont ceux qu'il soulèvera.
   */
  private loadScheduled(): void {
    this.previewAthlete.set(this.athleteId());
    this.athletes.get(this.athleteId()).subscribe({
      next: (a) => this.athleteName.set(`${a.firstName} ${a.lastName}`),
      error: () => this.athleteName.set(''),
    });
    this.strength
      .scheduledPrescription(this.athleteId(), this.scheduledId())
      .subscribe({
        next: (rx) => {
          this.name.set(rx.title ?? 'Séance de renforcement');
          this.blocks.set(rx.snapshot?.blocks ?? []);
          this.loading.set(false);
          this.refreshCharges();
        },
        error: () => {
          this.loading.set(false);
          this.toast.error('Séance introuvable.');
        },
      });
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
      this.strength
        .calculatePreview(a, { blocks: this.blocks() })
        .subscribe((res) => {
          const map: Record<string, ChargeTarget> = {};
          res.blocks.forEach((b, bi) =>
            b.exercises.forEach((ex, ei) => {
              map[`${bi}:${ei}`] = ex.charge;
            }),
          );
          this.chargePreview.set(map);
        });
    }, 350);
  }

  /** Relance le calcul des charges (mode séance planifiée : l'athlète ne change jamais). */
  private refreshCharges(): void {
    this.recompute();
  }

  chargeAt(bi: number, ei: number): ChargeTarget | undefined {
    return this.chargePreview()[`${bi}:${ei}`];
  }

  // --- Blocs ---
  drop(event: CdkDragDrop<StrengthBlock[]>): void {
    const list = [...this.blocks()];
    moveItemInArray(list, event.previousIndex, event.currentIndex);
    this.blocks.set(list);
    this.touch();
  }

  /**
   * Les sections proposées en un clic, dans l'ordre où une séance se déroule.
   *
   * <p>Ajouter un bloc, c'était choisir un type puis un format dans deux listes déroulantes —
   * dont l'une propose sept formats alors que la quasi-totalité des séances tient en séries
   * classiques. On pose maintenant la section voulue d'un geste ; le format reste réglable, mais
   * il a cessé d'être la première question posée à quelqu'un qui veut écrire « squat 4 × 6 ».</p>
   */
  readonly sections: { type: BlockType; label: string }[] = [
    { type: 'ECHAUFFEMENT', label: 'Échauffement' },
    { type: 'ACTIVATION', label: 'Activation' },
    { type: 'PRINCIPAL', label: 'Principal' },
    { type: 'ACCESSOIRE', label: 'Accessoires' },
    { type: 'CALME', label: 'Retour au calme' },
  ];

  /**
   * Pose une section et ouvre aussitôt le choix des exercices : c'est la suite du même geste.
   *
   * <p>C'est par là qu'une séance vide commence — l'écran propose les cinq sections plutôt que
   * « ajoute un premier bloc », et le clic qui choisit la section est aussi celui qui amène les
   * exercices. La section choisie décide ensuite des valeurs par défaut de ce qu'on y pose.</p>
   */
  addSection(type: BlockType): void {
    const block = this.blankBlock(type);
    this.blocks.update((list) => [...list, block]);
    this.touch();
    this.requestPicker(block);
  }

  addBlock(): void {
    this.blocks.update((list) => [...list, this.blankBlock('PRINCIPAL')]);
    this.touch();
  }

  /**
   * Duplique un bloc entier — exercices et prescriptions compris.
   *
   * <p>« Le même circuit une seconde fois » se reconstruisait exercice par exercice. La copie est
   * profonde : sans quoi retoucher la prescription du double changerait aussi celle de
   * l'original, les deux partageant le même objet.</p>
   */
  duplicateBlock(block: StrengthBlock): void {
    const copy: StrengthBlock = {
      ...block,
      id: 'b-' + Math.random().toString(36).slice(2, 9),
      exercises: block.exercises.map((ex) => this.copyItem(ex)),
    };
    const index = this.blocks().findIndex((b) => b.id === block.id);
    this.blocks.update((list) => [
      ...list.slice(0, index + 1),
      copy,
      ...list.slice(index + 1),
    ]);
    this.touch();
  }

  /** Duplique un exercice dans son bloc, juste sous l'original — « la même, en plus lourd ». */
  duplicateExercise(block: StrengthBlock, index: number): void {
    const source = block.exercises[index];
    if (!source) return;
    block.exercises = [
      ...block.exercises.slice(0, index + 1),
      this.copyItem(source),
      ...block.exercises.slice(index + 1),
    ];
    this.touch();
  }

  /** Copie profonde d'un exercice prescrit : prescription et configuration de série comprises. */
  private copyItem(item: StrengthExerciseItem): StrengthExerciseItem {
    return {
      ...item,
      prescription: { ...item.prescription },
      setConfig: item.setConfig
        ? JSON.parse(JSON.stringify(item.setConfig))
        : item.setConfig,
    };
  }

  private blankBlock(type: BlockType): StrengthBlock {
    return {
      id: 'b-' + Math.random().toString(36).slice(2, 9),
      blockType: type,
      format: 'CLASSIQUE',
      durationSec: null,
      rounds: null,
      workSec: null,
      restSec: null,
      exercises: [],
    };
  }

  removeBlock(id: string): void {
    this.blocks.update((list) => list.filter((b) => b.id !== id));
    this.touch();
  }

  onFormatChange(block: StrengthBlock): void {
    // Valeurs par défaut sensées selon le format.
    block.durationSec = ['EMOM', 'AMRAP', 'FOR_TIME'].includes(block.format)
      ? 600
      : null;
    block.rounds = block.format === 'CIRCUIT' || block.format === 'SUPERSET' ? 3 : null;
    block.workSec = block.format === 'CIRCUIT' ? 40 : null;
    block.restSec =
      block.format === 'CIRCUIT' ? 20 : block.format === 'SUPERSET' ? 120 : null;
    this.touch();
  }

  /**
   * Pose un superset : des exercices enchaînés sans pause, répétés en tours, la récup se prenant
   * entre les tours. Trois tours et deux minutes de récup, la forme la plus courante en renfo
   * coureur ; les consignes restent à écrire, avec un exemple sous les yeux.
   */
  addSuperset(): void {
    const block: StrengthBlock = {
      ...this.blankBlock('PRINCIPAL'),
      format: 'SUPERSET',
      rounds: 3,
      restSec: 120,
    };
    this.blocks.update((list) => [...list, block]);
    this.touch();
    this.requestPicker(block);
  }

  /** Numéro du superset parmi les supersets de la séance (« Superset 2 »). */
  supersetNumber(block: StrengthBlock): number {
    return this.blocks().filter((b) => b.format === 'SUPERSET').indexOf(block) + 1;
  }

  /** Consignes du bloc : texte libre du coach, lu tel quel par l'athlète. */
  setInstructions(block: StrengthBlock, value: string): void {
    block.instructions = value?.trim() ? value : null;
    this.touch();
  }

  // --- Partir d'un modèle ------------------------------------------------------

  /**
   * Séances de la bibliothèque proposées comme point de départ d'une séance vide : les favorites
   * d'abord (un coach y épingle ses niveaux 1, 2, 3), puis les plus servies.
   */
  readonly starterModels = signal<StrengthSession[]>([]);

  private loadStarterModels(): void {
    this.strength.listAllSessions().subscribe({
      next: (list) => {
        const own = this.sessionId();
        this.starterModels.set(
          list
            .filter((s) => s.id !== own && !s.archived)
            .sort((a, b) =>
              a.favorite !== b.favorite
                ? a.favorite ? -1 : 1
                : b.useCount - a.useCount || a.name.localeCompare(b.name, 'fr'),
            )
            .slice(0, 6),
        );
      },
      // Sans modèles, l'état vide propose toujours les sections : rien de bloquant.
      error: () => this.starterModels.set([]),
    });
  }

  /**
   * Recopie la structure du modèle dans la séance (vide) : on part de là, on ajuste ensuite.
   * La liste de la bibliothèque ne porte pas les structures : on relit le modèle complet.
   */
  pickModel(model: StrengthSession): void {
    this.strength.getSession(model.id).subscribe({
      next: (full) => this.startFromModel(full),
      error: () => this.toast.error('Modèle illisible, réessaie.'),
    });
  }

  startFromModel(model: StrengthSession): void {
    const blocks = (model.structure?.blocks ?? []).map((b) => ({
      ...b,
      id: 'b-' + Math.random().toString(36).slice(2, 9),
      exercises: b.exercises.map((ex) => this.copyItem(ex)),
    }));
    if (!blocks.length) {
      this.toast.info(`« ${model.name} » est vide : compose la séance à partir des sections.`);
      return;
    }
    this.blocks.set(blocks);
    this.pickerBlockId.set(blocks[blocks.length - 1]?.id ?? null);
    this.toast.success(`Séance partie de « ${model.name} »`);
    this.touch();
  }

  /**
   * Le format d'un bloc est-il déplié ?
   *
   * <p>Replié par défaut : sept formats en tête de chaque bloc donnaient à choisir avant même
   * d'avoir un exercice, alors que « Classique » convient à la quasi-totalité des séances. Un
   * bloc déjà réglé sur autre chose s'ouvre de lui-même — on ne cache pas un choix fait.</p>
   */
  readonly optionsFor = signal<string | null>(null);

  toggleOptions(block: StrengthBlock): void {
    this.optionsFor.update((id) => (id === block.id ? null : block.id));
  }

  optionsOpen(block: StrengthBlock): boolean {
    return this.optionsFor() === block.id || block.format !== 'CLASSIQUE';
  }

  /**
   * Le volume se règle-t-il directement sur la carte ?
   *
   * <p>Oui pour les deux cas courants — un nombre de répétitions, une durée en secondes — qui
   * représentent l'essentiel de ce qu'on retouche. Les fourchettes et les distances gardent le
   * panneau : y mettre deux champs de plus sur la carte la rendrait illisible pour un cas rare.</p>
   */
  inlineVolume(
    block: StrengthBlock,
    item: StrengthExerciseItem,
  ): 'REPS' | 'DUREE' | null {
    const p = item.prescription;
    // Séries réglées une à une : deux champs ne sauraient les dire, la carte montre le résumé.
    if (setTargetsOf(p)) return null;
    const type = this.volumeTypeOf(block, item);
    if (type === 'REPS' && p.repsFixed != null) return 'REPS';
    if (type === 'DUREE' && p.durationSecMax == null) return 'DUREE';
    return null;
  }

  setInlineSets(item: StrengthExerciseItem, value: number | null): void {
    item.prescription.sets =
      value == null || !Number.isFinite(value)
        ? null
        : Math.max(1, Math.round(value));
    // Séries réglées une à une : on garde chacune, la dernière sert de modèle aux nouvelles.
    const targets = setTargetsOf(item.prescription);
    if (targets && item.prescription.sets != null) {
      const n = Math.min(item.prescription.sets, MAX_SET_ROWS);
      const rows = Array.from({ length: n }, (_, i) => ({ ...(targets[i] ?? targets[targets.length - 1]) }));
      item.prescription.setTargets = rows;
    }
    this.touch();
  }

  // --- Séries une à une : « Travail » en reps, durée ou distance --------------

  /** Une ligne par série, comme dans le tableau Travail du panneau. */
  setRows(block: StrengthBlock, item: StrengthExerciseItem): SetTarget[] {
    return setRowsOf(item.prescription, block.format);
  }

  /** Unité affichée à droite de chaque valeur de travail. */
  workUnit(block: StrengthBlock, item: StrengthExerciseItem): string {
    switch (this.volumeTypeOf(block, item)) {
      case 'DUREE': return 's';
      case 'DISTANCE': return 'm';
      default: return 'reps';
    }
  }

  setRowValue(
    block: StrengthBlock, item: StrengthExerciseItem, index: number,
    key: 'value' | 'valueMax', raw: unknown,
  ): void {
    const rows = this.setRows(block, item);
    if (!rows[index]) return;
    const v = StrengthSessionEditorComponent.num(raw);
    rows[index] = { ...rows[index], [key]: v == null ? null : Math.max(0, Math.round(v)) };
    applySetRows(item.prescription, block.format, rows);
    this.touch();
  }

  /** Nouvelle série, copie de la dernière : on ajoute le plus souvent « une de plus pareille ». */
  addSetRow(block: StrengthBlock, item: StrengthExerciseItem): void {
    const rows = this.setRows(block, item);
    if (rows.length >= MAX_SET_ROWS) return;
    rows.push({ ...rows[rows.length - 1] });
    applySetRows(item.prescription, block.format, rows);
    this.touch();
  }

  removeSetRow(block: StrengthBlock, item: StrengthExerciseItem, index: number): void {
    const rows = this.setRows(block, item);
    if (rows.length <= 1) return;
    rows.splice(index, 1);
    applySetRows(item.prescription, block.format, rows);
    this.touch();
  }

  setInlineReps(item: StrengthExerciseItem, value: number | null): void {
    item.prescription.repsFixed =
      value == null || !Number.isFinite(value)
        ? null
        : Math.max(1, Math.round(value));
    this.touch();
  }

  setInlineDuration(item: StrengthExerciseItem, value: number | null): void {
    item.prescription.durationSec =
      value == null || !Number.isFinite(value)
        ? null
        : Math.max(1, Math.round(value));
    this.touch();
  }

  showDuration(block: StrengthBlock): boolean {
    return ['EMOM', 'AMRAP', 'FOR_TIME'].includes(block.format);
  }
  showCircuit(block: StrengthBlock): boolean {
    return block.format === 'CIRCUIT';
  }

  showSuperset(block: StrengthBlock): boolean {
    return block.format === 'SUPERSET';
  }

  // --- Choix d'un exercice (panneau rangé par catégorie) ---------------------
  // Le choix se faisait dans une liste déroulante à plat : une bibliothèque de club y aligne
  // cent exercices sans ordre ni recherche, et retrouver « Fente bulgare » demandait de la
  // parcourir en entier. Le panneau range la même bibliothèque par catégorie du coach — à
  // défaut, par type d'exercice — et se cherche au clavier.

  readonly pickerOpen = signal(false);
  readonly pickerQuery = signal('');
  /**
   * L'exercice dont la démonstration est dépliée dans le sélecteur.
   *
   * <p>Un seul à la fois : le panneau sert à choisir vite, et empiler les lecteurs y ferait
   * défiler la liste plutôt que la parcourir.</p>
   */
  readonly videoFor = signal<string | null>(null);

  toggleVideo(exerciseId: string): void {
    this.videoFor.update((current) =>
      current === exerciseId ? null : exerciseId,
    );
  }
  private readonly pickerBlockId = signal<string | null>(null);

  /**
   * Section qui reçoit les ajouts de la bibliothèque : celle qu'on a désignée en dernier, et à
   * défaut la dernière de la séance — c'est là qu'on écrit quand on n'a rien choisi.
   */
  readonly targetBlock = computed<StrengthBlock | null>(() => {
    const list = this.blocks();
    return list.find((b) => b.id === this.pickerBlockId()) ?? list[list.length - 1] ?? null;
  });

  /** Désigne la section qui recevra les prochains exercices (clic sur sa carte). */
  targetSection(block: StrengthBlock): void {
    this.pickerBlockId.set(block.id);
  }

  /** Libellé de la cible, numéroté : deux sections « Principal » ne se confondent pas. */
  readonly targetLabel = computed(() => {
    const block = this.targetBlock();
    if (!block) return null;
    const index = this.blocks().indexOf(block) + 1;
    return `${index}. ${this.blockTypeLabel(block.blockType)}`;
  });

  // --- Bibliothèque ouverte à gauche (grand écran) ---------------------------

  readonly libTab = signal<'all' | 'favorites' | 'frequent'>('all');
  readonly libQuery = signal('');
  readonly libCategory = signal('');

  readonly favoriteCount = computed(
    () => this.exercises().filter((e) => e.favorite).length,
  );
  /** Les exercices déjà prescrits, du plus servi au moins servi — vingt au plus. */
  private readonly frequentExercises = computed(() =>
    this.exercises()
      .filter((e) => e.useCount > 0)
      .sort((a, b) => b.useCount - a.useCount)
      .slice(0, 20),
  );
  readonly frequentCount = computed(() => this.frequentExercises().length);

  private readonly libSource = computed(() => {
    switch (this.libTab()) {
      case 'favorites':
        return this.exercises().filter((e) => e.favorite);
      case 'frequent':
        return this.frequentExercises();
      default:
        return this.exercises();
    }
  });

  /** Toutes les catégories de l'onglet, avant le filtre : la liste déroulante ne se vide pas. */
  readonly libCategoryChoices = computed(() =>
    this.groupExercises(this.libSource(), '').map((g) => ({
      key: g.key,
      title: g.title,
      count: g.exercises.length,
    })),
  );

  readonly libGroups = computed(() => {
    const groups = this.groupExercises(this.libSource(), this.libQuery());
    const cat = this.libCategory();
    return cat ? groups.filter((g) => g.key === cat) : groups;
  });

  readonly libCount = computed(() =>
    this.libGroups().reduce((n, g) => n + g.exercises.length, 0),
  );

  setLibTab(tab: 'all' | 'favorites' | 'frequent'): void {
    this.libTab.set(tab);
    this.libCategory.set('');
  }

  /** Ajoute depuis la bibliothèque à la section visée ; une séance vide reçoit d'abord un « Principal ». */
  addFromLibrary(ex: PpExercise): void {
    if (!this.blocks().length) {
      const block = this.blankBlock('PRINCIPAL');
      this.blocks.set([block]);
      this.pickerBlockId.set(block.id);
    }
    const block = this.targetBlock();
    if (block) this.addExerciseTo(block, ex);
  }

  /** Épingle un exercice : il rejoint l'onglet Favoris, ici et sur tous les écrans du club. */
  toggleExerciseFavorite(ex: PpExercise): void {
    const favorite = !ex.favorite;
    const apply = (value: boolean) =>
      this.exercises.update((list) =>
        list.map((e) => (e.id === ex.id ? { ...e, favorite: value } : e)),
      );
    apply(favorite);
    this.strength.setExerciseFavorite(ex.id, favorite).subscribe({
      error: () => {
        apply(!favorite);
        this.toast.error('Favori non enregistré, réessaie.');
      },
    });
  }

  /** La bibliothèque donne, elle ne reçoit pas : un exercice glissé hors d'une section n'y retourne pas. */
  readonly noEnter = () => false;

  /** Identifiants des sections, pour relier la bibliothèque à chacune d'elles. */
  readonly blockDropIds = computed(() => this.blocks().map((b) => 'blk-' + b.id));

  /**
   * Dépôt dans une section : un exercice glissé depuis la bibliothèque y est ajouté à l'endroit
   * du dépôt ; un exercice déjà présent change de place dans sa section ou passe dans une autre.
   */
  dropExercise(event: CdkDragDrop<StrengthBlock, StrengthBlock | undefined>, block: StrengthBlock): void {
    const data = event.item.data as { kind: 'library'; exercise: PpExercise } | { kind: 'item' } | undefined;
    if (data && 'exercise' in data) {
      this.pickerBlockId.set(block.id);
      this.addExerciseTo(block, data.exercise, event.currentIndex);
      return;
    }
    const from = event.previousContainer.data;
    if (from === block) {
      const list = [...block.exercises];
      moveItemInArray(list, event.previousIndex, event.currentIndex);
      block.exercises = list;
    } else if (from) {
      const moved = from.exercises[event.previousIndex];
      if (!moved) return;
      from.exercises = from.exercises.filter((_, i) => i !== event.previousIndex);
      const list = [...block.exercises];
      list.splice(event.currentIndex, 0, moved);
      block.exercises = list;
    }
    this.touch();
  }

  /** Bibliothèque filtrée par la recherche, rangée par catégorie. */
  readonly pickerGroups = computed(() =>
    this.groupExercises(this.exercises(), this.pickerQuery()),
  );

  private groupExercises(
    list: PpExercise[],
    rawQuery: string,
  ): { key: string; title: string; exercises: PpExercise[] }[] {
    const query = normalize(rawQuery);
    const names = new Map(
      this.categories().map((c) => [c.id, c.name] as const),
    );
    const groups = new Map<
      string,
      { key: string; title: string; exercises: PpExercise[] }
    >();

    for (const ex of list) {
      if (query && !normalize(ex.name).includes(query)) continue;
      // Catégorie du coach si elle est renseignée ; sinon le type de l'exercice, qui l'est
      // toujours — aucun exercice ne doit tomber dans un « divers » où on ne le cherchera pas.
      const key =
        ex.categoryId && names.has(ex.categoryId)
          ? ex.categoryId
          : `type:${ex.category}`;
      const title =
        ex.categoryId && names.has(ex.categoryId)
          ? names.get(ex.categoryId)!
          : this.label(ex.category);
      const group = groups.get(key) ?? { key, title, exercises: [] };
      group.exercises.push(ex);
      groups.set(key, group);
    }

    // Catégories du coach dans son ordre, puis les types — ses catégories passent avant.
    const order = new Map(this.categories().map((c, i) => [c.id, i] as const));
    return [...groups.values()].sort((a, b) => {
      const ra = order.get(a.key) ?? Number.MAX_SAFE_INTEGER;
      const rb = order.get(b.key) ?? Number.MAX_SAFE_INTEGER;
      return ra === rb ? a.title.localeCompare(b.title, 'fr') : ra - rb;
    });
  }

  /** Nombre d'exercices proposés, toutes catégories confondues (état vide de la recherche). */
  readonly pickerCount = computed(() =>
    this.pickerGroups().reduce((n, g) => n + g.exercises.length, 0),
  );

  openPicker(block: StrengthBlock): void {
    this.pickerBlockId.set(block.id);
    if (this.wide()) {
      // La bibliothèque est déjà là : on vise la section et on rend la main au clavier.
      document.getElementById('ex-lib-search')?.focus();
      return;
    }
    this.pickerQuery.set('');
    this.pickerOpen.set(true);
  }

  /**
   * Ce qu'un exercice vaut par défaut <b>selon la section où on le pose</b>.
   *
   * <p>Tout exercice ajouté naissait « 4 × 6 à 70–80 % du 1RM, RIR 1–3, repos 90–120 s », y
   * compris dans un échauffement — une prescription fausse, qu'il fallait corriger à la main à
   * chaque fois. Un échauffement se fait léger et court, des accessoires se font en volume, un
   * retour au calme se tient en secondes. La section porte donc son intention.</p>
   */
  private static readonly SECTION_DEFAULTS: Record<
    BlockType,
    StrengthPrescription
  > = {
    ECHAUFFEMENT: {
      effortRefType: 'RIR_RANGE',
      rirMin: 4,
      rirMax: 5,
      sets: 2,
      volumeType: 'REPS',
      repsFixed: 10,
      restSecMin: 30,
      restSecMax: 45,
    },
    ACTIVATION: {
      effortRefType: 'RIR_RANGE',
      rirMin: 3,
      rirMax: 4,
      sets: 2,
      volumeType: 'REPS',
      repsFixed: 12,
      restSecMin: 30,
      restSecMax: 45,
    },
    PRINCIPAL: {
      chargeRefType: 'PCT_RM_RANGE',
      chargePctRmMin: 70,
      chargePctRmMax: 80,
      effortRefType: 'RIR_RANGE',
      rirMin: 1,
      rirMax: 3,
      sets: 4,
      volumeType: 'REPS',
      repsFixed: 6,
      restSecMin: 90,
      restSecMax: 120,
    },
    ACCESSOIRE: {
      chargeRefType: 'PCT_RM_RANGE',
      chargePctRmMin: 55,
      chargePctRmMax: 70,
      effortRefType: 'RIR_RANGE',
      rirMin: 2,
      rirMax: 3,
      sets: 3,
      volumeType: 'REPS',
      repsFixed: 10,
      restSecMin: 60,
      restSecMax: 90,
    },
    CALME: {
      effortRefType: 'RIR_RANGE',
      rirMin: 4,
      rirMax: 5,
      sets: 2,
      volumeType: 'DUREE',
      durationSec: 45,
      restSecMin: 30,
    },
  };

  /**
   * Ajoute l'exercice au bloc visé. Le panneau reste ouvert : on compose un bloc en enchaînant
   * plusieurs exercices, et le refermer à chaque fois ferait rouvrir, rechercher, re-cliquer.
   */
  addExercise(ex: PpExercise): void {
    const block = this.blocks().find((b) => b.id === this.pickerBlockId());
    if (!block) return;
    this.addExerciseTo(block, ex);
  }

  /** Pose l'exercice dans la section, à la fin ou à la place du dépôt. */
  private addExerciseTo(block: StrengthBlock, ex: PpExercise, at?: number): void {
    const item: StrengthExerciseItem = {
      exerciseId: ex.id,
      exerciseName: ex.name,
      setType: 'STANDARD',
      prescription: {
        ...StrengthSessionEditorComponent.SECTION_DEFAULTS[block.blockType],
        // Un bloc d'isométrie se tient en secondes, quelle que soit la section qui le porte.
        ...(block.format === 'ISOMETRIE'
          ? {
              volumeType: 'DUREE' as VolumeType,
              durationSec: 30,
              repsFixed: null,
            }
          : {}),
        // Un superset s'enchaîne : une série par tour, sans pause entre les exercices. Les tours
        // et la récup entre les tours sont portés par le bloc.
        ...(block.format === 'SUPERSET'
          ? { sets: 1, restSecMin: null, restSecMax: null }
          : {}),
        sideMode: 'BILATERAL',
      },
    };
    const list = [...block.exercises];
    list.splice(at ?? list.length, 0, item);
    block.exercises = list;
    this.toast.success(`${ex.name} ajouté`);
    this.touch();
  }

  // --- Exercices d'un bloc ---

  removeExercise(block: StrengthBlock, idx: number): void {
    block.exercises = block.exercises.filter((_, i) => i !== idx);
    this.touch();
  }

  /**
   * Force la propagation du signal après mutation interne d'un bloc, rafraîchit l'aperçu et
   * arme l'auto-sauvegarde. Point de passage unique de toute modification de la structure.
   */
  touch(): void {
    this.blocks.set([...this.blocks()]);
    this.recompute();
    this.autosave.markDirty();
  }

  // --- Identité de la séance -------------------------------------------------
  // Une séance dupliquée restait « … (copie) » à vie : l'éditeur est le seul écran de sa vie, et
  // il n'en montrait pas le nom.

  /** Renomme la séance (le nom part avec la prochaine auto-sauvegarde). */
  setName(value: string): void {
    this.name.set(value);
    this.nameDirty = true;
    this.touch();
  }

  /**
   * Écriture effective : la structure, et le nom s'il a changé. Un nom vidé n'est pas envoyé
   * (le serveur l'exige non blanc) : la séance garde alors celui qu'elle avait.
   */
  private persist(): Observable<unknown> {
    const name = this.name().trim();
    if (this.isScheduled()) {
      const structure$ = this.strength.updateScheduledStructure(
        this.athleteId(),
        this.scheduledId(),
        { blocks: this.blocks() },
      );
      if (!this.nameDirty || !name) return structure$;
      return structure$.pipe(
        switchMap(() =>
          this.strength.renameScheduled(
            this.athleteId(),
            this.scheduledId(),
            name,
          ),
        ),
        tap(() => {
          this.nameDirty = false;
        }),
      );
    }
    const structure$ = this.strength.putStructure(this.sessionId(), {
      blocks: this.blocks(),
    });
    if (!this.nameDirty || !name) return structure$;
    return structure$.pipe(
      switchMap(() =>
        this.strength.updateSession(this.sessionId(), {
          name,
          notes: this.notes(),
        }),
      ),
      tap(() => {
        this.nameDirty = false;
      }),
    );
  }

  /**
   * Enregistrement explicite : ne fait qu'anticiper le debounce, mais vaut aussi « j'ai fini ».
   * Sur une séance planifiée, il renvoie donc au calendrier — rester sur l'éditeur laissait le
   * coach sans issue évidente alors qu'il venait de dire qu'il avait terminé.
   */
  save(): void {
    this.saving.set(true);
    this.autosave.flush().subscribe((ok) => {
      this.saving.set(false);
      if (!ok) {
        this.toast.error('Enregistrement impossible.');
        return;
      }
      if (this.isScheduled()) {
        this.toast.success('Séance modifiée pour l’athlète');
        // Le programme de l'athlète : c'est le même calendrier, cadré sur lui — donc à coup sûr
        // la séance qu'on vient de modifier, là où l'écran Calendrier global aurait pu rouvrir
        // sur un tout autre athlète.
        this.router.navigate(['/app/athletes', this.athleteId(), 'programme']);
      } else {
        this.toast.success('Structure enregistrée');
      }
    });
  }

  // --- Verser une séance du calendrier dans la bibliothèque (mode séance planifiée) ---------
  // Une séance improvisée pour un athlète puis affinée n'avait pas d'issue : la garder supposait
  // de la reconstruire bloc par bloc dans la bibliothèque.

  readonly saveAsOpen = signal(false);
  readonly saveAsBusy = signal(false);
  saveAsName = '';

  openSaveAs(): void {
    this.saveAsName = this.saveAsName || this.name().trim();
    this.saveAsOpen.set(true);
  }

  closeSaveAs(): void {
    this.saveAsOpen.set(false);
  }

  /** Enregistre la structure courante, puis la verse en bibliothèque comme nouveau modèle. */
  saveAsLibrarySession(): void {
    const name = this.saveAsName.trim();
    if (!name) {
      this.toast.warning('Donne un nom au modèle.');
      return;
    }
    if (this.saveAsBusy()) return;
    this.saveAsBusy.set(true);
    // On vide d'abord le debounce : sinon le modèle figerait la structure d'il y a dix secondes.
    this.autosave.flush().subscribe((ok) => {
      if (!ok) {
        this.saveAsBusy.set(false);
        this.toast.error('Enregistrement impossible.');
        return;
      }
      this.strength
        .saveScheduledAsSession(this.athleteId(), this.scheduledId(), { name })
        .subscribe({
          next: (created) => {
            this.saveAsBusy.set(false);
            this.saveAsOpen.set(false);
            this.toast.success(`« ${created.name} » ajoutée à ta bibliothèque`);
          },
          error: () => {
            this.saveAsBusy.set(false);
            this.toast.error('Enregistrement dans la bibliothèque impossible.');
          },
        });
    });
  }

  // --- Prescription d'un exercice : volume, latéralité, repos ----------------

  /** Unité de volume de cet exercice (répétitions par défaut, durée en bloc d'isométrie). */
  volumeTypeOf(block: StrengthBlock, item: StrengthExerciseItem): VolumeType {
    return effectiveVolumeType(item.prescription, block.format);
  }

  /**
   * Change l'unité du volume. Les champs des autres unités sont vidés : laisser « 8 reps »
   * derrière une prescription passée en durée, c'est une valeur fantôme que le prochain
   * changement d'unité ferait ressurgir à la place de celle qu'on vient de saisir.
   */
  setVolumeType(
    block: StrengthBlock,
    item: StrengthExerciseItem,
    type: VolumeType,
  ): void {
    const p = item.prescription;
    // Changer d'unité garde le nombre de séries, mais pas leurs valeurs : « 12 / 10 / 8 » reps
    // ne veut rien dire en secondes. Chaque série repart du volume par défaut de l'unité.
    const sets = Math.max(1, p.sets ?? 1);
    p.setTargets = null;
    p.sets = sets;
    p.volumeType = type;
    if (type !== 'REPS') {
      p.repsFixed = null;
      p.repsMin = null;
      p.repsMax = null;
    }
    if (type !== 'DUREE') {
      p.durationSec = null;
      p.durationSecMax = null;
    }
    if (type !== 'DISTANCE') {
      p.distanceM = null;
      p.distanceMMax = null;
    }
    if (type === 'REPS' && p.repsFixed == null && p.repsMin == null)
      p.repsFixed = 8;
    if (type === 'DUREE' && p.durationSec == null) p.durationSec = 30;
    if (type === 'DISTANCE' && p.distanceM == null) p.distanceM = 20;
    this.touch();
  }

  /** Le volume est-il prescrit en fourchette (« 8 à 10 ») plutôt qu'en valeur exacte ? */
  volumeRanged(block: StrengthBlock, item: StrengthExerciseItem): boolean {
    const p = item.prescription;
    switch (this.volumeTypeOf(block, item)) {
      case 'DUREE':
        return p.durationSecMax != null;
      case 'DISTANCE':
        return p.distanceMMax != null;
      default:
        return p.repsFixed == null;
    }
  }

  /** Bascule valeur exacte ↔ fourchette en conservant ce qui était déjà saisi comme borne basse. */
  setVolumeRanged(
    block: StrengthBlock,
    item: StrengthExerciseItem,
    ranged: boolean,
  ): void {
    const p = item.prescription;
    switch (this.volumeTypeOf(block, item)) {
      case 'DUREE':
        p.durationSecMax = ranged
          ? (p.durationSecMax ?? bumped(p.durationSec, 15))
          : null;
        break;
      case 'DISTANCE':
        p.distanceMMax = ranged
          ? (p.distanceMMax ?? bumped(p.distanceM, 10))
          : null;
        break;
      default:
        if (ranged) {
          p.repsMin = p.repsMin ?? p.repsFixed ?? 8;
          p.repsMax = p.repsMax ?? bumped(p.repsMin, 2);
          p.repsFixed = null;
        } else {
          p.repsFixed = p.repsFixed ?? p.repsMin ?? 8;
          p.repsMin = null;
          p.repsMax = null;
        }
    }
    this.touch();
  }

  /** Latéralité : un exercice prescrit avant ce choix vaut bilatéral, et s'affiche comme tel. */
  setSideMode(item: StrengthExerciseItem, mode: SideMode): void {
    item.prescription.sideMode = mode;
    this.touch();
  }

  // --- Panneau d'édition allégé : borne max facultative = fourchette -----------

  private static num(v: unknown): number | null {
    if (v === '' || v == null) return null;
    const n = Number(v);
    return Number.isFinite(n) ? n : null;
  }

  volLow(block: StrengthBlock, item: StrengthExerciseItem): number | null {
    const p = item.prescription;
    switch (this.volumeTypeOf(block, item)) {
      case 'DUREE': return p.durationSec ?? null;
      case 'DISTANCE': return p.distanceM ?? null;
      default: return p.repsFixed ?? p.repsMin ?? null;
    }
  }

  volHigh(block: StrengthBlock, item: StrengthExerciseItem): number | null {
    const p = item.prescription;
    switch (this.volumeTypeOf(block, item)) {
      case 'DUREE': return p.durationSecMax ?? null;
      case 'DISTANCE': return p.distanceMMax ?? null;
      default: return p.repsFixed != null ? null : (p.repsMax ?? null);
    }
  }

  setVolLow(block: StrengthBlock, item: StrengthExerciseItem, raw: unknown): void {
    const v = StrengthSessionEditorComponent.num(raw);
    const p = item.prescription;
    switch (this.volumeTypeOf(block, item)) {
      case 'DUREE': p.durationSec = v; break;
      case 'DISTANCE': p.distanceM = v; break;
      default:
        if (p.repsFixed == null && p.repsMax != null) p.repsMin = v;
        else { p.repsFixed = v; p.repsMin = null; p.repsMax = null; }
    }
    this.touch();
  }

  setVolHigh(block: StrengthBlock, item: StrengthExerciseItem, raw: unknown): void {
    const v = StrengthSessionEditorComponent.num(raw);
    const p = item.prescription;
    switch (this.volumeTypeOf(block, item)) {
      case 'DUREE': p.durationSecMax = v; break;
      case 'DISTANCE': p.distanceMMax = v; break;
      default: {
        const low = p.repsFixed ?? p.repsMin ?? null;
        if (v == null) { p.repsFixed = low; p.repsMin = null; p.repsMax = null; }
        else { p.repsMin = low; p.repsFixed = null; p.repsMax = v; }
      }
    }
    this.touch();
  }

  chargeMode(item: StrengthExerciseItem): 'PCT' | 'KG' | 'NONE' | 'OTHER' {
    const t = item.prescription.chargeRefType;
    if (!t) return 'NONE';
    if (t === 'PCT_RM' || t === 'PCT_RM_RANGE') return 'PCT';
    if (t === 'KG_FIXE' || t === 'KG_RANGE') return 'KG';
    return 'OTHER';
  }

  chargeRefLabel(item: StrengthExerciseItem): string {
    return this.chargeRefs.find((c) => c.value === item.prescription.chargeRefType)?.label ?? '';
  }

  setChargeMode(item: StrengthExerciseItem, mode: 'PCT' | 'KG' | 'NONE' | 'OTHER'): void {
    const p = item.prescription;
    if (mode === 'OTHER') return;
    if (mode === 'NONE') {
      p.chargeRefType = undefined;
    } else if (mode === 'PCT') {
      p.chargePctRmMin = p.chargePctRmMin ?? 70;
      p.chargeRefType = p.chargePctRmMax != null ? 'PCT_RM_RANGE' : 'PCT_RM';
    } else {
      p.chargeKgMin = p.chargeKgMin ?? 20;
      p.chargeRefType = p.chargeKgMax != null ? 'KG_RANGE' : 'KG_FIXE';
    }
    this.touch();
  }

  chargeLow(item: StrengthExerciseItem): number | null {
    const p = item.prescription;
    return this.chargeMode(item) === 'PCT' ? (p.chargePctRmMin ?? null) : (p.chargeKgMin ?? null);
  }

  chargeHigh(item: StrengthExerciseItem): number | null {
    const p = item.prescription;
    switch (p.chargeRefType) {
      case 'PCT_RM_RANGE': return p.chargePctRmMax ?? null;
      case 'KG_RANGE': return p.chargeKgMax ?? null;
      default: return null;
    }
  }

  setChargeLow(item: StrengthExerciseItem, raw: unknown): void {
    const v = StrengthSessionEditorComponent.num(raw);
    if (this.chargeMode(item) === 'PCT') item.prescription.chargePctRmMin = v;
    else item.prescription.chargeKgMin = v;
    this.touch();
  }

  setChargeHigh(item: StrengthExerciseItem, raw: unknown): void {
    const v = StrengthSessionEditorComponent.num(raw);
    const p = item.prescription;
    if (this.chargeMode(item) === 'PCT') {
      p.chargePctRmMax = v;
      p.chargeRefType = v == null ? 'PCT_RM' : 'PCT_RM_RANGE';
    } else {
      p.chargeKgMax = v;
      p.chargeRefType = v == null ? 'KG_FIXE' : 'KG_RANGE';
    }
    this.touch();
  }

  effortMode(item: StrengthExerciseItem): 'RIR' | 'RPE' | 'NONE' {
    const t = item.prescription.effortRefType;
    if (t === 'RIR' || t === 'RIR_RANGE') return 'RIR';
    if (t === 'RPE' || t === 'RPE_RANGE') return 'RPE';
    return 'NONE';
  }

  setEffortMode(item: StrengthExerciseItem, mode: 'RIR' | 'RPE' | 'NONE'): void {
    const p = item.prescription;
    if (mode === 'NONE') {
      p.effortRefType = undefined;
    } else if (mode === 'RIR') {
      p.rirMin = p.rirMin ?? 2;
      p.effortRefType = p.rirMax != null ? 'RIR_RANGE' : 'RIR';
    } else {
      p.rpeMin = p.rpeMin ?? 7;
      p.effortRefType = p.rpeMax != null ? 'RPE_RANGE' : 'RPE';
    }
    this.touch();
  }

  effortLow(item: StrengthExerciseItem): number | null {
    const p = item.prescription;
    return this.effortMode(item) === 'RIR' ? (p.rirMin ?? null) : (p.rpeMin ?? null);
  }

  effortHigh(item: StrengthExerciseItem): number | null {
    const p = item.prescription;
    switch (p.effortRefType) {
      case 'RIR_RANGE': return p.rirMax ?? null;
      case 'RPE_RANGE': return p.rpeMax ?? null;
      default: return null;
    }
  }

  setEffortLow(item: StrengthExerciseItem, raw: unknown): void {
    const v = StrengthSessionEditorComponent.num(raw);
    if (this.effortMode(item) === 'RIR') item.prescription.rirMin = v;
    else item.prescription.rpeMin = v;
    this.touch();
  }

  setEffortHigh(item: StrengthExerciseItem, raw: unknown): void {
    const v = StrengthSessionEditorComponent.num(raw);
    const p = item.prescription;
    if (this.effortMode(item) === 'RIR') {
      p.rirMax = v;
      p.effortRefType = v == null ? 'RIR' : 'RIR_RANGE';
    } else {
      p.rpeMax = v;
      p.effortRefType = v == null ? 'RPE' : 'RPE_RANGE';
    }
    this.touch();
  }

  setRestHigh(item: StrengthExerciseItem, raw: unknown): void {
    item.prescription.restSecMax = StrengthSessionEditorComponent.num(raw);
    this.touch();
  }

  // --- Méthodes de série (drop set, myo-reps, cluster…) ----------------------

  readonly setMethods = SET_METHODS;

  methodOf(item: StrengthExerciseItem): SetMethod {
    return setMethod(item.setType);
  }

  /** Change de méthode : ses réglages par défaut remplacent ceux de la précédente. */
  setSetMethod(item: StrengthExerciseItem, type: SetType): void {
    if (item.setType === type) return;
    item.setType = type;
    const defaults = setMethod(type).defaults;
    item.setConfig = Object.keys(defaults).length ? { ...defaults } : null;
    // Une iso se tient en secondes : on aligne le volume pour ne pas prescrire « 6 répétitions ».
    if (type === 'ISO_OVERCOMING' || type === 'ISO_YIELDING') {
      item.prescription.volumeType = 'DUREE';
      item.prescription.durationSec = defaults.holdSec ?? 30;
      item.prescription.repsFixed = null;
      item.prescription.repsMin = null;
      item.prescription.repsMax = null;
    }
    this.touch();
  }

  methodValue(item: StrengthExerciseItem, key: keyof SetMethodConfig): number | null {
    return setMethodConfig(item.setType, item.setConfig)[key] ?? null;
  }

  setMethodValue(item: StrengthExerciseItem, key: keyof SetMethodConfig, raw: unknown): void {
    item.setConfig = {
      ...setMethodConfig(item.setType, item.setConfig),
      [key]: StrengthSessionEditorComponent.num(raw),
    };
    this.touch();
  }

  methodSummary(item: StrengthExerciseItem): string {
    return describeSetMethod(item.setType, item.setConfig);
  }

  /** Le repos est-il laissé en fourchette (« 90 à 120 s ») plutôt que strict (« 90 s ») ? */
  restRanged(item: StrengthExerciseItem): boolean {
    return item.prescription.restSecMax != null;
  }

  /** Bascule repos strict ↔ fourchette ; la borne basse saisie reste la borne basse. */
  setRestRanged(item: StrengthExerciseItem, ranged: boolean): void {
    const p = item.prescription;
    p.restSecMax = ranged ? (p.restSecMax ?? bumped(p.restSecMin, 30)) : null;
    this.touch();
  }

  /**
   * Durée d'un bloc chronométré, **en minutes** : un AMRAP se dit « 12 minutes », un EMOM
   * « 10 minutes ». Le stockage reste en secondes (le reste de l'application y compte), mais
   * plus personne ne saisit 720 pour prescrire douze minutes.
   */
  blockMinutes(block: StrengthBlock): number | null {
    return block.durationSec == null
      ? null
      : Math.round((block.durationSec / 60) * 10) / 10;
  }

  setBlockMinutes(block: StrengthBlock, minutes: number | null): void {
    block.durationSec =
      minutes == null || !Number.isFinite(minutes)
        ? null
        : Math.max(0, Math.round(minutes * 60));
    this.touch();
  }

  label(value: string): string {
    return value
      .replace(/_/g, ' ')
      .toLowerCase()
      .replace(/^\w/, (c) => c.toUpperCase());
  }
}

/** Borne haute par défaut d'une fourchette qu'on vient d'ouvrir : la basse, augmentée d'un cran. */
function bumped(low: number | null | undefined, step: number): number {
  return (low ?? 0) + step;
}

/** Comparaison de recherche : sans accents ni casse — « fente » doit trouver « Fente arrière ». */
function normalize(value: string): string {
  return value
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .trim();
}
