import { CdkDragDrop, DragDropModule, moveItemInArray } from '@angular/cdk/drag-drop';
import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, input, signal } from '@angular/core';
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
  StrengthPrescription,
  VolumeType,
} from '../../core/models/strength.model';
import { SessionCategory } from '../../core/models/session-category.model';
import { SessionCategoryService } from '../../core/services/session-category.service';
import {
  SIDE_MODE_LABELS, VOLUME_TYPE_LABELS, effectiveVolumeType, restPill, sideLabel, volumePill,
  type VolumePill,
} from '../../core/utils/strength-volume';
import {
  EffortBadgeComponent,
  type EffortKind,
  RangePrescriptionPillComponent,
} from '../../shared/components/physiology';
import { SegmentedControlComponent, type SegmentOption, SidePanelComponent } from '../../shared/components/ui';
import { AutosaveBadgeComponent } from '../../shared/components/autosave-badge/autosave-badge.component';
import { Autosave } from '../../core/services/autosave';
import { HasAutosave } from '../../core/guards/unsaved-changes.guard';
import { SkeletonComponent } from '../../shared/components/skeleton/skeleton.component';

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
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [SkeletonComponent, IconComponent, 
    FormsModule, RouterLink, DragDropModule,
    SegmentedControlComponent, RangePrescriptionPillComponent, EffortBadgeComponent,
    SidePanelComponent, AutosaveBadgeComponent,
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
    if (this.isScheduled()) {
      this.loadScheduled();
    } else {
      this.loadLibrarySession();
    }
    this.strength.listAllExercises().subscribe((e) => this.exercises.set(e));
    this.categoryService.list('STRENGTH').subscribe({
      next: (c) => this.categories.set(c),
      // Sans catégories, le choix d'exercices se range par type — il reste utilisable.
      error: () => this.categories.set([]),
    });
    // Le sélecteur d'aperçu n'a de sens que sur un modèle : une séance planifiée a déjà son
    // athlète, et lui en proposer un autre inviterait à lire des charges qui ne sont pas les siennes.
    if (!this.isScheduled()) {
      this.athletes.list({ page: 0 }).subscribe((p) => this.athleteList.set(p.content));
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
    this.strength.scheduledPrescription(this.athleteId(), this.scheduledId()).subscribe({
      next: (rx) => {
        this.name.set(rx.title ?? 'Séance de renforcement');
        this.blocks.set(rx.snapshot?.blocks ?? []);
        this.loading.set(false);
        this.refreshCharges();
      },
      error: () => { this.loading.set(false); this.toast.error('Séance introuvable.'); },
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
      this.strength.calculatePreview(a, { blocks: this.blocks() }).subscribe((res) => {
        const map: Record<string, ChargeTarget> = {};
        res.blocks.forEach((b, bi) =>
          b.exercises.forEach((ex, ei) => { map[`${bi}:${ei}`] = ex.charge; }));
        this.chargePreview.set(map);
      });
    }, 350);
  }

  /** Relance le calcul des charges (mode séance planifiée : l'athlète ne change jamais). */
  private refreshCharges(): void { this.recompute(); }

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
    this.touch();
  }

  removeBlock(id: string): void {
    this.blocks.update((list) => list.filter((b) => b.id !== id));
    this.touch();
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

  // --- Choix d'un exercice (panneau rangé par catégorie) ---------------------
  // Le choix se faisait dans une liste déroulante à plat : une bibliothèque de club y aligne
  // cent exercices sans ordre ni recherche, et retrouver « Fente bulgare » demandait de la
  // parcourir en entier. Le panneau range la même bibliothèque par catégorie du coach — à
  // défaut, par type d'exercice — et se cherche au clavier.

  readonly pickerOpen = signal(false);
  readonly pickerQuery = signal('');
  private readonly pickerBlockId = signal<string | null>(null);

  /** Bibliothèque filtrée par la recherche, rangée par catégorie. */
  readonly pickerGroups = computed<{ key: string; title: string; exercises: PpExercise[] }[]>(() => {
    const query = normalize(this.pickerQuery());
    const names = new Map(this.categories().map((c) => [c.id, c.name] as const));
    const groups = new Map<string, { key: string; title: string; exercises: PpExercise[] }>();

    for (const ex of this.exercises()) {
      if (query && !normalize(ex.name).includes(query)) continue;
      // Catégorie du coach si elle est renseignée ; sinon le type de l'exercice, qui l'est
      // toujours — aucun exercice ne doit tomber dans un « divers » où on ne le cherchera pas.
      const key = ex.categoryId && names.has(ex.categoryId) ? ex.categoryId : `type:${ex.category}`;
      const title = ex.categoryId && names.has(ex.categoryId)
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
  });

  /** Nombre d'exercices proposés, toutes catégories confondues (état vide de la recherche). */
  readonly pickerCount = computed(() => this.pickerGroups().reduce((n, g) => n + g.exercises.length, 0));

  openPicker(block: StrengthBlock): void {
    this.pickerBlockId.set(block.id);
    this.pickerQuery.set('');
    this.pickerOpen.set(true);
  }

  /**
   * Ajoute l'exercice au bloc visé. Le panneau reste ouvert : on compose un bloc en enchaînant
   * plusieurs exercices, et le refermer à chaque fois ferait rouvrir, rechercher, re-cliquer.
   */
  addExercise(ex: PpExercise): void {
    const block = this.blocks().find((b) => b.id === this.pickerBlockId());
    if (!block) return;
    const item: StrengthExerciseItem = {
      exerciseId: ex.id,
      exerciseName: ex.name,
      setType: 'STANDARD',
      prescription: {
        chargeRefType: 'PCT_RM_RANGE', chargePctRmMin: 70, chargePctRmMax: 80,
        effortRefType: 'RIR_RANGE', rirMin: 1, rirMax: 3,
        sets: 4,
        // Un bloc d'isométrie se tient en secondes ; partout ailleurs on compte des répétitions.
        ...(block.format === 'ISOMETRIE'
          ? { volumeType: 'DUREE' as VolumeType, durationSec: 30 }
          : { volumeType: 'REPS' as VolumeType, repsFixed: 6 }),
        sideMode: 'BILATERAL',
        restSecMin: 90, restSecMax: 120,
      },
    };
    block.exercises = [...block.exercises, item];
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
        this.athleteId(), this.scheduledId(), { blocks: this.blocks() });
      if (!this.nameDirty || !name) return structure$;
      return structure$.pipe(
        switchMap(() => this.strength.renameScheduled(this.athleteId(), this.scheduledId(), name)),
        tap(() => { this.nameDirty = false; }),
      );
    }
    const structure$ = this.strength.putStructure(this.sessionId(), { blocks: this.blocks() });
    if (!this.nameDirty || !name) return structure$;
    return structure$.pipe(
      switchMap(() => this.strength.updateSession(this.sessionId(), { name, notes: this.notes() })),
      tap(() => { this.nameDirty = false; }),
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
      if (!ok) { this.toast.error('Enregistrement impossible.'); return; }
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

  closeSaveAs(): void { this.saveAsOpen.set(false); }

  /** Enregistre la structure courante, puis la verse en bibliothèque comme nouveau modèle. */
  saveAsLibrarySession(): void {
    const name = this.saveAsName.trim();
    if (!name) { this.toast.warning('Donne un nom au modèle.'); return; }
    if (this.saveAsBusy()) return;
    this.saveAsBusy.set(true);
    // On vide d'abord le debounce : sinon le modèle figerait la structure d'il y a dix secondes.
    this.autosave.flush().subscribe((ok) => {
      if (!ok) { this.saveAsBusy.set(false); this.toast.error('Enregistrement impossible.'); return; }
      this.strength.saveScheduledAsSession(this.athleteId(), this.scheduledId(), { name }).subscribe({
        next: (created) => {
          this.saveAsBusy.set(false);
          this.saveAsOpen.set(false);
          this.toast.success(`« ${created.name} » ajoutée à ta bibliothèque`);
        },
        error: () => { this.saveAsBusy.set(false); this.toast.error('Enregistrement dans la bibliothèque impossible.'); },
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
  setVolumeType(block: StrengthBlock, item: StrengthExerciseItem, type: VolumeType): void {
    const p = item.prescription;
    p.volumeType = type;
    if (type !== 'REPS') { p.repsFixed = null; p.repsMin = null; p.repsMax = null; }
    if (type !== 'DUREE') { p.durationSec = null; p.durationSecMax = null; }
    if (type !== 'DISTANCE') { p.distanceM = null; p.distanceMMax = null; }
    if (type === 'REPS' && p.repsFixed == null && p.repsMin == null) p.repsFixed = 8;
    if (type === 'DUREE' && p.durationSec == null) p.durationSec = 30;
    if (type === 'DISTANCE' && p.distanceM == null) p.distanceM = 20;
    this.touch();
  }

  /** Le volume est-il prescrit en fourchette (« 8 à 10 ») plutôt qu'en valeur exacte ? */
  volumeRanged(block: StrengthBlock, item: StrengthExerciseItem): boolean {
    const p = item.prescription;
    switch (this.volumeTypeOf(block, item)) {
      case 'DUREE': return p.durationSecMax != null;
      case 'DISTANCE': return p.distanceMMax != null;
      default: return p.repsFixed == null;
    }
  }

  /** Bascule valeur exacte ↔ fourchette en conservant ce qui était déjà saisi comme borne basse. */
  setVolumeRanged(block: StrengthBlock, item: StrengthExerciseItem, ranged: boolean): void {
    const p = item.prescription;
    switch (this.volumeTypeOf(block, item)) {
      case 'DUREE':
        p.durationSecMax = ranged ? p.durationSecMax ?? bumped(p.durationSec, 15) : null;
        break;
      case 'DISTANCE':
        p.distanceMMax = ranged ? p.distanceMMax ?? bumped(p.distanceM, 10) : null;
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

  /** Le repos est-il laissé en fourchette (« 90 à 120 s ») plutôt que strict (« 90 s ») ? */
  restRanged(item: StrengthExerciseItem): boolean {
    return item.prescription.restSecMax != null;
  }

  /** Bascule repos strict ↔ fourchette ; la borne basse saisie reste la borne basse. */
  setRestRanged(item: StrengthExerciseItem, ranged: boolean): void {
    const p = item.prescription;
    p.restSecMax = ranged ? p.restSecMax ?? bumped(p.restSecMin, 30) : null;
    this.touch();
  }

  /**
   * Durée d'un bloc chronométré, **en minutes** : un AMRAP se dit « 12 minutes », un EMOM
   * « 10 minutes ». Le stockage reste en secondes (le reste de l'application y compte), mais
   * plus personne ne saisit 720 pour prescrire douze minutes.
   */
  blockMinutes(block: StrengthBlock): number | null {
    return block.durationSec == null ? null : Math.round((block.durationSec / 60) * 10) / 10;
  }

  setBlockMinutes(block: StrengthBlock, minutes: number | null): void {
    block.durationSec = minutes == null || !Number.isFinite(minutes)
      ? null
      : Math.max(0, Math.round(minutes * 60));
    this.touch();
  }

  label(value: string): string {
    return value.replace(/_/g, ' ').toLowerCase().replace(/^\w/, (c) => c.toUpperCase());
  }
}

/** Borne haute par défaut d'une fourchette qu'on vient d'ouvrir : la basse, augmentée d'un cran. */
function bumped(low: number | null | undefined, step: number): number {
  return (low ?? 0) + step;
}

/** Comparaison de recherche : sans accents ni casse — « fente » doit trouver « Fente arrière ». */
function normalize(value: string): string {
  return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().trim();
}
