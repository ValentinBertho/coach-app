import { CdkDragDrop, DragDropModule, moveItemInArray } from '@angular/cdk/drag-drop';
import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { Router, RouterLink } from '@angular/router';
import { AthleteService } from '../../core/services/athlete.service';
import { CourseService } from '../../core/services/course.service';
import { WorkoutService } from '../../core/services/workout.service';
import { ToastService } from '../../core/services/toast.service';
import { rpeWithLabel } from '../../shared/components/rpe-scale';
import { RunDrillService } from '../../core/services/run-drill.service';
import { PhysioService } from '../../core/services/physio.service';
import { AthleteSummary } from '../../core/models/athlete.model';
import { CalculatedBlock, COURSE_BLOCK_TYPE_LABELS, CourseBlock, CourseBlockType, CourseRecovery, SessionStructure } from '../../core/models/course.model';
import { PhysioProfile } from '../../core/models/physio.model';
import { RunDrill } from '../../core/models/run-drill.model';
import { TrainingZone } from '../../core/models/training-zone.model';
import { TrainingZoneService } from '../../core/services/training-zone.service';
import { MetricType } from '../../core/models/metric-type.model';
import { MetricTypeService } from '../../core/services/metric-type.service';
import { CategoryOption, SessionCategory, categoryOptions } from '../../core/models/session-category.model';
import { SessionCategoryService } from '../../core/services/session-category.service';
import { AthleteZoneValue } from '../../core/models/athlete-zone-value.model';
import { AthleteZoneValueService } from '../../core/services/athlete-zone-value.service';
import { ZonePickerComponent } from '../../shared/components/zone-picker/zone-picker.component';
import { AutosaveBadgeComponent } from '../../shared/components/autosave-badge/autosave-badge.component';
import { Autosave } from '../../core/services/autosave';
import { HasAutosave } from '../../core/guards/unsaved-changes.guard';

/** Statut de complétude du profil pour la prescription course. */
export type ProfileStatus = 'measured' | 'estimated' | 'incomplete';

interface Section { key: keyof SessionStructure; label: string; }

/** Unité de saisie d'un volume : temps (s/min/h) ou distance (m/km). Stockage : secondes, mètres. */
export type VolumeUnit = 's' | 'min' | 'h' | 'm' | 'km';

/** Volume mesurable en temps ou en distance (bloc de séance, récupération). */
interface Measurable { durationS?: number | null; distanceM?: number | null; }

function isTime(unit: VolumeUnit): boolean {
  return unit === 's' || unit === 'min' || unit === 'h';
}

/** Nombre de secondes (ou de mètres) que vaut une unité. */
function factor(unit: VolumeUnit): number {
  switch (unit) {
    case 's': return 1;
    case 'min': return 60;
    case 'h': return 3600;
    case 'm': return 1;
    case 'km': return 1000;
  }
}

/** Unité la plus lisible pour une valeur donnée (1 h 30 plutôt que 5400 s, 12 km plutôt que 12 000 m). */
function naturalUnit(durationS: number | null | undefined, distanceM: number | null | undefined,
                     fallback: VolumeUnit): VolumeUnit {
  if (durationS != null && distanceM == null) {
    if (durationS >= 3600 && durationS % 900 === 0) return 'h';
    return durationS < 120 ? 's' : 'min';
  }
  if (distanceM != null) return distanceM >= 1000 && distanceM % 100 === 0 ? 'km' : 'm';
  return fallback;
}

/** Valeur stockée (secondes / mètres) exprimée dans l'unité demandée. */
function toUnit(durationS: number | null | undefined, distanceM: number | null | undefined,
                unit: VolumeUnit): number | null {
  const raw = isTime(unit) ? durationS : distanceM;
  if (raw == null) return null;
  return Math.round((raw / factor(unit)) * 100) / 100;
}

/** Écrit une valeur saisie dans l'unité donnée, en secondes ou en mètres selon la famille. */
function applyUnit(target: Measurable, unit: VolumeUnit, value: number | null): void {
  const stored = value == null ? null : Math.round(value * factor(unit));
  if (isTime(unit)) {
    target.durationS = stored;
    target.distanceM = null;
  } else {
    target.distanceM = stored;
    target.durationS = null;
  }
}

/**
 * Éditeur de structure de séance course (blocs prescrits en fourchettes + calculateur live).
 * Deux modes, selon les paramètres de route :
 *  - **modèle** (`templateId`) : édite la bibliothèque (course.getStructure/putStructure) ;
 *  - **séance planifiée** (`athleteId` + `workoutId`) : adapte une séance d'un athlète au
 *    calendrier (workout.prescription/updateStructure), athlète fixe.
 */
@Component({
  selector: 'app-session-editor',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink, DragDropModule, IconComponent, ZonePickerComponent, AutosaveBadgeComponent],
  templateUrl: './session-editor.component.html',
  styleUrl: './session-editor.component.scss',
})
export class SessionEditorComponent implements OnInit, HasAutosave {
  // Paramètres de route (component input binding). Un seul jeu est renseigné selon le mode.
  readonly templateId = input<string>('');
  readonly workoutId = input<string>('');
  readonly athleteId = input<string>('');

  private readonly course = inject(CourseService);
  private readonly zoneService = inject(TrainingZoneService);
  private readonly metricService = inject(MetricTypeService);
  private readonly categoryService = inject(SessionCategoryService);
  private readonly zoneValueService = inject(AthleteZoneValueService);
  private readonly athletes = inject(AthleteService);
  private readonly workoutService = inject(WorkoutService);
  private readonly drillService = inject(RunDrillService);
  private readonly physio = inject(PhysioService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);

  /**
   * Auto-sauvegarde : dix minutes de construction disparaissaient au premier clic sur
   * « Calendrier ». Toute modification du contenu passe par `touch()`.
   */
  readonly autosave = new Autosave(() => this.persist(), inject(DestroyRef));

  readonly drills = signal<RunDrill[]>([]);

  /** Profil physio de l'athlète du calculateur (null tant qu'aucun athlète n'est choisi). */
  readonly profile = signal<PhysioProfile | null>(null);

  /**
   * Complétude du profil pour la prescription course :
   *  - `measured`   : au moins un seuil mesuré (LT1/LT2/VC) → cibles fiables ;
   *  - `estimated`  : pas de seuil mais un VDOT (chrono saisi) → allures dérivées, « estimées » ;
   *  - `incomplete` : ni seuil ni VDOT → rien de calculable (proposer un chrono de référence).
   */
  readonly profileStatus = computed<ProfileStatus | null>(() => {
    const p = this.profile();
    if (!this.calcAthleteId() || !p) return null;
    if (p.lt1Ms != null || p.lt2Ms != null || p.vcMs != null) return 'measured';
    if (p.vdot != null) return 'estimated';
    return 'incomplete';
  });

  /** Mode édition d'une séance planifiée (vs modèle de bibliothèque). */
  readonly isWorkout = computed(() => !!this.workoutId());

  readonly name = signal('');
  /** Titre affiché à l'athlète ; le nom, lui, range la séance dans la bibliothèque. */
  readonly title = signal('');
  /** Catégorie de rangement de la séance ('' = sans catégorie). */
  readonly categoryId = signal('');
  readonly categories = signal<SessionCategory[]>([]);
  readonly loading = signal(true);
  /** Encart d'écriture libre du coach (intention, consignes) — enregistré avec la structure. */
  readonly notes = signal('');
  readonly structure = signal<SessionStructure>({ warmup: [], main: [], cooldown: [] });
  readonly calc = signal<Record<string, CalculatedBlock>>({});
  /** Cibles calculées des récupérations inter-répétitions (clé = id du bloc parent). */
  readonly recCalc = signal<Record<string, CalculatedBlock>>({});
  readonly athleteList = signal<AthleteSummary[]>([]);
  /** Athlète du calculateur live (sélectionnable en mode modèle, fixe en mode séance planifiée). */
  readonly calcAthleteId = signal('');
  /** Panneau « aperçu athlète » (sélecteur + statut profil) replié par défaut pour épurer. */
  readonly athletePanelOpen = signal(false);
  /** Blocs dont le volet « détails » (éducatifs, cible détaillée) est ouvert. */
  readonly detailsOpen = signal<Set<string>>(new Set());

  /** Types de récupération inter-répétitions. */
  readonly recoveryTypes: { value: string; label: string }[] = [
    { value: 'jog', label: 'Trot' },
    { value: 'walk', label: 'Marche' },
    { value: 'static', label: 'Statique' },
  ];

  readonly calcAthleteName = computed(() => {
    const a = this.athleteList().find((x) => x.id === this.calcAthleteId());
    return a ? `${a.firstName} ${a.lastName}` : '';
  });

  toggleDetails(id: string): void {
    this.detailsOpen.update((s) => {
      const next = new Set(s);
      next.has(id) ? next.delete(id) : next.add(id);
      return next;
    });
  }
  isDetailsOpen(id: string): boolean { return this.detailsOpen().has(id); }

  readonly sections: Section[] = [
    { key: 'warmup', label: 'Échauffement' },
    { key: 'main', label: 'Corps de séance' },
    { key: 'cooldown', label: 'Retour au calme' },
  ];

  /** Zones du club (authoring Z3 : un bloc = type + volume + zone). */
  readonly zones = signal<TrainingZone[]>([]);
  /** Catalogue de métriques (formatage des cibles dans le sélecteur de zone). */
  readonly metrics = signal<MetricType[]>([]);
  /** Valeurs de zones de l'athlète du calculateur (cibles concrètes montrées au choix de zone). */
  readonly zoneValues = signal<AthleteZoneValue[]>([]);

  /** Zone pré-sélectionnée selon le type de bloc (QA4). */
  private readonly DEFAULT_ZONE_BY_TYPE: Record<string, string> = {
    intervals: '3 km',
    tempo: 'Tempo',
    threshold: 'Seuil 2 bas',
    easy: 'EF',
    warmup: 'EF',
    cooldown: 'Footing facile',
    recovery: 'Footing facile',
    long: 'EF',
    run: 'EF',
  };

  private zoneIdByName(name: string): string | null {
    const found = this.zones().find((z) => z.name === name);
    return found?.id ?? this.zones()[0]?.id ?? null;
  }

  private defaultZoneIdForType(type: string): string | null {
    return this.zoneIdByName(this.DEFAULT_ZONE_BY_TYPE[type] ?? 'EF');
  }

  /** Choix de zone d'un bloc (via le sélecteur riche) → met à jour la prescription + recalcule. */
  onBlockZone(b: CourseBlock, zoneId: string): void {
    if (!b.prescription) b.prescription = { zoneId };
    else b.prescription.zoneId = zoneId;
    // Une zone cardio ne se double pas d'une seconde cible FC : elle en porte déjà une.
    if (b.prescription.hrZoneId && this.isCardioZone(zoneId)) b.prescription.hrZoneId = null;
    this.onBlockEdited(b);
  }

  /** Zones portant la métrique FC (échelle cardio) — celles qu'on peut ajouter en second. */
  readonly cardioZones = computed(() => {
    const hrIds = this.metricIds('HR', 'PCT_HRMAX');
    return this.zones().filter((z) => z.rules?.some((r) => hrIds.has(r.metricTypeId)));
  });

  private metricIds(...codes: string[]): Set<string> {
    return new Set(this.metrics().filter((m) => codes.includes(m.code)).map((m) => m.id));
  }

  /** Vrai si cette zone appartient à l'échelle cardio (elle porte déjà sa cible FC). */
  isCardioZone(zoneId: string | null | undefined): boolean {
    return !!zoneId && this.cardioZones().some((z) => z.id === zoneId);
  }

  /** Zone cardio proposée par défaut quand le coach ajoute une cible FC à un bloc. */
  defaultCardioZoneId(): string | null {
    return this.cardioZones()[0]?.id ?? null;
  }

  /** Ajoute, change ou retire ({@code null}) la zone cardio d'un bloc. */
  setBlockHrZone(b: CourseBlock, hrZoneId: string | null): void {
    if (!b.prescription) return;
    b.prescription.hrZoneId = hrZoneId;
    this.onBlockEdited(b);
  }

  ngOnInit(): void {
    this.drillService.list().subscribe((d) => this.drills.set(d));
    this.metricService.list().subscribe((m) => this.metrics.set(m));
    this.categoryService.list().subscribe({
      next: (c) => this.categories.set(c),
      error: () => this.categories.set([]),
    });

    if (this.isWorkout()) {
      // Mode séance planifiée : athlète fixe, zones de SON modèle, snapshot puis recalcul.
      this.zoneService.list({ athleteId: this.athleteId() }).subscribe((z) => this.zones.set(z));
      this.name.set('Adapter la séance');
      this.calcAthleteId.set(this.athleteId());
      this.loadProfile(this.athleteId());
      this.loadZoneValues(this.athleteId());
      this.workoutService.prescription(this.athleteId(), this.workoutId()).subscribe({
        next: (p) => {
          this.structure.set(p.snapshot ?? { warmup: [], main: [], cooldown: [] });
          this.loading.set(false);
          this.recalcAll();
        },
        error: () => this.loading.set(false),
      });
      return;
    }

    // Mode modèle de bibliothèque : on écrit sur l'échelle de référence du club.
    this.zoneService.list().subscribe((z) => this.zones.set(z));
    this.course.getStructure(this.templateId()).subscribe({
      next: (s) => {
        this.name.set(s.name);
        this.title.set(s.title ?? '');
        this.categoryId.set(s.categoryId ?? '');
        this.notes.set(s.notes ?? '');
        this.structure.set(s.structure ?? { warmup: [], main: [], cooldown: [] });
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
    this.athletes.list({ page: 0 }).subscribe((p) => this.athleteList.set(p.content));
  }

  /**
   * Séance « simple » (footing, sortie longue…) : aucun bloc d'échauffement ni de retour au calme.
   * On n'affiche alors qu'une liste unique, sans imposer les trois sections. Le coach passe en
   * séance structurée d'un clic dès qu'il veut un échauffement / retour au calme.
   */
  readonly structuredMode = signal(false);

  readonly isSimple = computed(() => {
    if (this.structuredMode()) return false;
    const s = this.structure();
    return s.warmup.length === 0 && s.cooldown.length === 0;
  });

  /** Passe en séance structurée (révèle échauffement + retour au calme). */
  enableStructured(): void { this.structuredMode.set(true); }

  /** Sections affichées : le corps seul en séance simple, les trois en séance structurée. */
  readonly visibleSections = computed<Section[]>(() =>
    this.isSimple() ? this.sections.filter((s) => s.key === 'main') : this.sections);

  /** Effort perçu (RPE 1–10) d'un bloc — propre au contenu de la séance. */
  setBlockRpe(b: CourseBlock, value: number | null): void {
    b.rpe = value == null ? null : Math.max(1, Math.min(10, Math.round(value)));
    this.touch();
  }

  /** Recalcule les cibles de tous les blocs pour l'athlète courant du calculateur. */
  private recalcAll(): void {
    if (!this.calcAthleteId()) return;
    for (const sec of this.sections) {
      for (const b of this.blocks(sec.key)) this.recalc(b);
    }
  }

  /** Le bloc référence-t-il cet éducatif ? */
  hasDrill(b: CourseBlock, id: string): boolean {
    return (b.drillIds ?? []).includes(id);
  }

  /** Attache / détache un éducatif (gamme) au bloc. */
  toggleDrill(b: CourseBlock, id: string): void {
    const cur = b.drillIds ?? [];
    b.drillIds = cur.includes(id) ? cur.filter((x) => x !== id) : [...cur, id];
    this.touch();
  }

  blocks(key: keyof SessionStructure): CourseBlock[] {
    return this.structure()[key];
  }

  /** Total estimé de la séance (durée + distance), agrégé depuis les cibles calculées par bloc. */
  readonly sessionTotals = computed(() => {
    const calc = this.calc();
    const recCalc = this.recCalc();
    let durationS = 0;
    let distanceM = 0;
    let hasAny = false;
    for (const sec of this.sections) {
      for (const b of this.structure()[sec.key]) {
        const c = calc[b.id];
        if (!c?.computable) continue;
        if (c.estimatedDurationS) { durationS += c.estimatedDurationS; hasAny = true; }
        if (c.estimatedDistanceM) { distanceM += c.estimatedDistanceM; hasAny = true; }
        // Récup entre répétitions : (reps - 1) × la récup.
        const rc = recCalc[b.id];
        const inter = b.reps && b.reps > 1 ? b.reps - 1 : 0;
        if (rc?.computable && inter) {
          if (rc.estimatedDurationS) durationS += rc.estimatedDurationS * inter;
          if (rc.estimatedDistanceM) distanceM += rc.estimatedDistanceM * inter;
        }
      }
    }
    if (!hasAny) return null;
    const min = Math.round(durationS / 60);
    const durationLabel = min >= 60 ? `${Math.floor(min / 60)}h${String(min % 60).padStart(2, '0')}` : `${min} min`;
    return { durationLabel: durationS ? durationLabel : null, distanceKm: distanceM ? distanceM / 1000 : null };
  });

  /** Types de bloc proposés (liste fermée plutôt qu'un champ libre, plus intuitif) ; libellés FR. */
  readonly blockTypes: { value: CourseBlockType; label: string }[] =
    (Object.keys(COURSE_BLOCK_TYPE_LABELS) as CourseBlockType[])
      .map((value) => ({ value, label: COURSE_BLOCK_TYPE_LABELS[value] }));

  /** Blocs pré-remplis en un clic, par section : type + volume + zone (la cible est lue, pas saisie). */
  presetsFor(key: keyof SessionStructure): { label: string; block: Partial<CourseBlock> }[] {
    const z = (name: string) => this.zoneIdByName(name);
    if (key === 'warmup') {
      return [
        { label: 'Échauffement 15 min', block: { type: 'warmup', durationS: 900, prescription: { zoneId: z('Endurance fondamentale') } } },
      ];
    }
    if (key === 'cooldown') {
      return [
        { label: 'Retour au calme 10 min', block: { type: 'cooldown', durationS: 600, prescription: { zoneId: z('Récupération') } } },
      ];
    }
    const rec = (durationS: number): CourseRecovery => ({ type: 'jog', durationS, distanceM: null, prescription: { zoneId: z('Récupération') } });
    return [
      { label: '10×400 m r1\'', block: { type: 'intervals', reps: 10, distanceM: 400, prescription: { zoneId: z('VO2') }, recovery: rec(60) } },
      { label: '6×1000 m R2\'', block: { type: 'intervals', reps: 6, distanceM: 1000, prescription: { zoneId: z('VO2') }, recovery: rec(120) } },
      { label: 'Seuil 20 min', block: { type: 'threshold', durationS: 1200, prescription: { zoneId: z('Seuil') } } },
      { label: 'Tempo 4 km', block: { type: 'tempo', distanceM: 4000, prescription: { zoneId: z('Marathon') } } },
    ];
  }

  addBlock(key: keyof SessionStructure, preset?: Partial<CourseBlock>): void {
    const isMain = key === 'main';
    // Un « bloc libre » est un effort continu (footing, tempo…) : 1 × 30 min. Les fractionnés
    // arrivent par les presets (10×400 m r1'…), qui portent leurs reps et leur récupération.
    const type = isMain ? 'easy' : (key === 'warmup' ? 'warmup' : 'cooldown');
    const base: CourseBlock = {
      id: 'b-' + Math.random().toString(36).slice(2, 9),
      type,
      reps: null,
      distanceM: null,
      durationS: isMain ? 1800 : 600,
      prescription: { zoneId: this.defaultZoneIdForType(type) },
    };
    const block: CourseBlock = { ...base, ...preset, id: base.id };
    // Un bloc se mesure soit en distance, soit en durée : on garde un seul des deux.
    if (preset?.durationS != null) { block.distanceM = null; }
    else if (preset?.distanceM != null) { block.durationS = null; }
    const s = this.structure();
    this.structure.set({ ...s, [key]: [...s[key], block] });
    this.onBlockEdited(block);
  }

  /** Mode de mesure d'un bloc : par distance ou par durée (jamais les deux). */
  measureOf(b: CourseBlock): 'distance' | 'duration' {
    return b.durationS != null && b.distanceM == null ? 'duration' : 'distance';
  }

  setMeasure(b: CourseBlock, mode: 'distance' | 'duration'): void {
    if (mode === 'duration') {
      b.distanceM = null;
      b.durationS = b.durationS ?? 600;
    } else {
      b.durationS = null;
      b.distanceM = b.distanceM ?? 1000;
    }
    this.onBlockEdited(b);
  }

  // --- Unités de volume : secondes / minutes / heures · mètres / kilomètres -----------------
  // Un bloc se prescrit indifféremment en « 45 min », « 1 h 30 » ou « 12 km » selon la séance.
  // Le stockage reste en secondes et en mètres ; seule la saisie change d'unité.

  readonly volumeUnits: { value: VolumeUnit; label: string }[] = [
    { value: 's', label: 'sec' },
    { value: 'min', label: 'min' },
    { value: 'h', label: 'h' },
    { value: 'm', label: 'm' },
    { value: 'km', label: 'km' },
  ];

  /** Unité choisie par bloc ; sans choix explicite, celle qui rend la valeur la plus lisible. */
  private readonly blockUnit = signal<Record<string, VolumeUnit>>({});
  private readonly recUnit = signal<Record<string, VolumeUnit>>({});

  unitOf(b: CourseBlock): VolumeUnit {
    return this.blockUnit()[b.id] ?? naturalUnit(b.durationS, b.distanceM, 'min');
  }

  recUnitOf(b: CourseBlock): VolumeUnit {
    const r = b.recovery;
    return this.recUnit()[b.id] ?? naturalUnit(r?.durationS ?? null, r?.distanceM ?? null, 's');
  }

  /** Valeur du bloc dans son unité d'affichage. */
  volumeValue(b: CourseBlock): number | null {
    return toUnit(b.durationS, b.distanceM, this.unitOf(b));
  }

  setVolumeValue(b: CourseBlock, value: number | null): void {
    // L'unité se fige dès la première frappe : sans cela, taper « 60 » en minutes ferait basculer
    // le champ en heures sous les doigts (1 h), et la valeur affichée changerait toute seule.
    const unit = this.unitOf(b);
    this.blockUnit.update((m) => ({ ...m, [b.id]: unit }));
    applyUnit(b, unit, value);
    this.onBlockEdited(b);
  }

  /**
   * Change l'unité d'un bloc. Au sein d'une même famille (temps ou distance) la quantité est
   * conservée — 600 s devient 10 min ; d'une famille à l'autre, la mesure bascule et repart
   * d'une valeur usuelle.
   */
  setUnit(b: CourseBlock, unit: VolumeUnit): void {
    this.blockUnit.update((m) => ({ ...m, [b.id]: unit }));
    if (isTime(unit) === (b.durationS != null && b.distanceM == null)) {
      return; // même famille : la valeur stockée ne bouge pas, seul l'affichage change
    }
    this.setMeasure(b, isTime(unit) ? 'duration' : 'distance');
  }

  recVolumeValue(b: CourseBlock): number | null {
    const r = b.recovery;
    return r ? toUnit(r.durationS ?? null, r.distanceM ?? null, this.recUnitOf(b)) : null;
  }

  setRecVolumeValue(b: CourseBlock, value: number | null): void {
    if (!b.recovery) return;
    const unit = this.recUnitOf(b);
    this.recUnit.update((m) => ({ ...m, [b.id]: unit }));
    applyUnit(b.recovery, unit, value);
    this.recalcRecovery(b);
    this.touch();
  }

  setRecUnit(b: CourseBlock, unit: VolumeUnit): void {
    this.recUnit.update((m) => ({ ...m, [b.id]: unit }));
    const r = b.recovery;
    if (!r || isTime(unit) === (r.durationS != null && r.distanceM == null)) return;
    this.setRecMeasure(b, isTime(unit) ? 'duration' : 'distance');
  }

  // --- Récupération inter-répétitions (fractionnés) --------------------------

  hasRecovery(b: CourseBlock): boolean { return !!b.recovery; }

  /** Ajoute une récupération par défaut (trot 1', zone Récupération). */
  addRecovery(b: CourseBlock): void {
    b.recovery = { type: 'jog', durationS: 60, distanceM: null, prescription: { zoneId: this.zoneIdByName('Récupération') } };
    this.recalcRecovery(b);
    this.touch();
  }

  removeRecovery(b: CourseBlock): void {
    b.recovery = null;
    this.recCalc.update((m) => { const c = { ...m }; delete c[b.id]; return c; });
    this.touch();
  }

  recMeasureOf(b: CourseBlock): 'distance' | 'duration' {
    const r = b.recovery;
    return r && r.durationS != null && r.distanceM == null ? 'duration' : 'distance';
  }

  setRecMeasure(b: CourseBlock, mode: 'distance' | 'duration'): void {
    const r = b.recovery;
    if (!r) return;
    if (mode === 'duration') { r.distanceM = null; r.durationS = r.durationS ?? 60; }
    else { r.durationS = null; r.distanceM = r.distanceM ?? 200; }
    this.recalcRecovery(b);
    this.touch();
  }

  setRecZone(b: CourseBlock, zoneId: string): void {
    if (b.recovery?.prescription) { b.recovery.prescription.zoneId = zoneId; this.recalcRecovery(b); this.touch(); }
  }
  setRecType(b: CourseBlock, type: string): void {
    if (b.recovery) { b.recovery.type = type; this.touch(); }
  }

  /** Recalcule la cible de la récupération d'un bloc (lecture depuis la zone de l'athlète). */
  recalcRecovery(b: CourseBlock): void {
    const a = this.calcAthleteId();
    const r = b.recovery;
    if (!a || !r?.prescription?.zoneId) return;
    this.course.sessionCalc(a, { zoneId: r.prescription.zoneId, distanceM: r.distanceM, durationS: r.durationS })
      .subscribe((c) => this.recCalc.update((map) => ({ ...map, [b.id]: c })));
  }

  /** Infobulle du RPE de bloc : « Effort perçu visé — 7 — très dur ». */
  rpeTitle(rpe: number | null | undefined): string {
    const label = rpeWithLabel(rpe);
    return label ? `Effort perçu visé — ${label}` : 'Effort perçu visé (RPE 1–10)';
  }

  removeBlock(key: keyof SessionStructure, id: string): void {
    const s = this.structure();
    this.structure.set({ ...s, [key]: s[key].filter((b) => b.id !== id) });
    this.touch();
  }

  /**
   * Duplique un bloc juste sous sa source, récupération et zone comprises. Sans ce geste, une
   * pyramide (5×400, 5×600, 5×800…) impose de tout ressaisir bloc par bloc.
   */
  duplicateBlock(key: keyof SessionStructure, id: string): void {
    const s = this.structure();
    const index = s[key].findIndex((b) => b.id === id);
    if (index < 0) return;
    const source = s[key][index];
    const copy: CourseBlock = {
      ...structuredClone(source),
      id: 'b-' + Math.random().toString(36).slice(2, 9),
    };
    const arr = [...s[key]];
    arr.splice(index + 1, 0, copy);
    this.structure.set({ ...s, [key]: arr });
    this.onBlockEdited(copy);
    this.toast.success('Bloc dupliqué');
  }

  /** Réordonne les blocs d'une section par glisser-déposer (cohérent avec l'éditeur de force). */
  dropBlock(key: keyof SessionStructure, event: CdkDragDrop<CourseBlock[]>): void {
    if (event.previousIndex === event.currentIndex) return;
    const s = this.structure();
    const arr = [...s[key]];
    moveItemInArray(arr, event.previousIndex, event.currentIndex);
    this.structure.set({ ...s, [key]: arr });
    this.touch();
  }

  onAthleteChange(id: string): void {
    this.calcAthleteId.set(id);
    this.calc.set({});
    this.profile.set(null);
    this.zoneValues.set([]);
    if (id) { this.loadProfile(id); this.loadZoneValues(id); }
    this.recalcAll();
  }

  /** Charge le profil physio de l'athlète du calculateur (pour le statut + le bootstrap chrono). */
  private loadProfile(athleteId: string): void {
    this.physio.profile(athleteId).subscribe({
      next: (p) => this.profile.set(p),
      error: () => this.profile.set(null),
    });
  }

  /** Charge les valeurs de zones de l'athlète (cibles concrètes affichées dans le sélecteur). */
  private loadZoneValues(athleteId: string): void {
    this.zoneValueService.list(athleteId).subscribe({
      next: (v) => this.zoneValues.set(v),
      error: () => this.zoneValues.set([]),
    });
  }

  // --- Bootstrap : saisir un chrono de référence quand le profil est incomplet -------------

  /** Distances proposées pour amorcer le VDOT (chronos de référence). */
  readonly bootstrapDistances: { value: string; label: string }[] = [
    { value: 'D1500', label: '1500 m' },
    { value: 'D3000', label: '3000 m' },
    { value: 'D5KM', label: '5 km' },
    { value: 'D10KM', label: '10 km' },
    { value: 'D15KM', label: '15 km' },
    { value: 'SEMI', label: 'Semi-marathon' },
    { value: 'MARATHON', label: 'Marathon' },
  ];
  bootstrapDistance = 'D10KM';
  bootstrapTime = '';
  readonly bootstrapBusy = signal(false);

  /** Parse « mm:ss » ou « hh:mm:ss » en secondes ; null si invalide. */
  private parseTime(input: string): number | null {
    const parts = input.trim().split(':').map((p) => Number(p));
    if (parts.some((n) => Number.isNaN(n) || n < 0)) return null;
    if (parts.length === 2) return parts[0] * 60 + parts[1];
    if (parts.length === 3) return parts[0] * 3600 + parts[1] * 60 + parts[2];
    return null;
  }

  /** Enregistre un chrono de référence pour l'athlète → débloque VDOT + allures, puis recalcule. */
  submitBootstrap(): void {
    const athleteId = this.calcAthleteId();
    const seconds = this.parseTime(this.bootstrapTime);
    if (!athleteId || seconds == null || seconds <= 0) {
      this.toast.warning('Renseigne un temps valide (mm:ss ou hh:mm:ss).');
      return;
    }
    this.bootstrapBusy.set(true);
    this.physio.addPerformance(athleteId, { distance: this.bootstrapDistance, timeSeconds: seconds }).subscribe({
      next: () => {
        this.bootstrapTime = '';
        this.toast.success('Chrono enregistré — allures estimées disponibles.');
        this.loadProfile(athleteId);
        this.loadZoneValues(athleteId);
        this.recalcAll();
        this.bootstrapBusy.set(false);
      },
      error: () => { this.bootstrapBusy.set(false); this.toast.error('Enregistrement impossible.'); },
    });
  }

  recalc(b: CourseBlock): void {
    const a = this.calcAthleteId();
    const p = b.prescription;
    if (!a || !p) return;
    // Chemin Z3 : cible lue depuis la zone de l'athlète. Repli legacy (ref + %) pour l'adaptation
    // d'anciens snapshots non encore migrés vers une zone.
    let body: Parameters<CourseService['sessionCalc']>[1] | null = null;
    if (p.zoneId) {
      body = {
        zoneId: p.zoneId, hrZoneId: p.hrZoneId ?? null,
        reps: b.reps, distanceM: b.distanceM, durationS: b.durationS,
      };
    } else if (p.ref && p.minPct != null && p.maxPct != null) {
      body = { ref: p.ref, minPct: p.minPct, maxPct: p.maxPct, reps: b.reps, distanceM: b.distanceM, durationS: b.durationS };
    }
    if (!body) return;
    this.course.sessionCalc(a, body).subscribe((c) => this.calc.update((map) => ({ ...map, [b.id]: c })));
    if (b.recovery) this.recalcRecovery(b);
  }

  /** Cible compacte d'un bloc (« 3:35–3:45/km · 178–185 bpm ») pour affichage en regard de la zone. */
  targetLabel(blockId: string): string | null {
    return this.compactTarget(this.calc()[blockId]);
  }
  recoveryTargetLabel(blockId: string): string | null {
    return this.compactTarget(this.recCalc()[blockId]);
  }
  private compactTarget(c: CalculatedBlock | undefined): string | null {
    if (!c?.computable) return null;
    const parts: string[] = [];
    if (c.paceMinLabel && c.paceMaxLabel) parts.push(`${c.paceMinLabel}–${c.paceMaxLabel}/km`);
    if (c.hrMin && c.hrMax) parts.push(`${c.hrMin}–${c.hrMax} bpm`);
    if (c.rpeMin && c.rpeMax) parts.push(c.rpeMin === c.rpeMax ? `RPE ${c.rpeMin}` : `RPE ${c.rpeMin}–${c.rpeMax}`);
    return parts.length ? parts.join(' · ') : null;
  }

  /**
   * Toute modification du contenu : recalcule la cible du bloc et arme l'auto-sauvegarde.
   * Séparé de `recalc()` seul, qui sert aussi au changement d'athlète d'aperçu — lequel ne
   * modifie rien de la séance.
   */
  onBlockEdited(b: CourseBlock): void {
    this.recalc(b);
    this.touch();
  }

  /** Point de passage unique des modifications : arme le debounce d'auto-sauvegarde. */
  touch(): void { this.autosave.markDirty(); }

  /** Notes de séance (écriture libre du coach) — enregistrées avec la structure. */
  setNotes(value: string): void {
    this.notes.set(value);
    this.touch();
  }

  // --- Identité de la séance (nom, titre, catégorie) ------------------------
  // Sans ces champs ici, une séance dupliquée restait « … (copie) » à vie : l'éditeur était le
  // seul écran de la vie d'un modèle, et il n'en montrait pas le nom.

  /** Catégories à plat, sous-catégories indentées sous leur parent. */
  readonly categoryChoices = computed<CategoryOption[]>(() => categoryOptions(this.categories()));

  setName(value: string): void { this.name.set(value); this.touch(); }
  setTitle(value: string): void { this.title.set(value); this.touch(); }
  setCategory(value: string): void { this.categoryId.set(value); this.touch(); }

  /** Écriture effective, selon le mode (modèle de bibliothèque ou séance planifiée). */
  private persist() {
    if (this.isWorkout()) {
      return this.workoutService.updateStructure(this.athleteId(), this.workoutId(), this.structure());
    }
    const categoryId = this.categoryId();
    return this.course.putStructure(this.templateId(), {
      name: this.name().trim() || null,
      title: this.title().trim() || null,
      // `clearCategory` distingue « sans catégorie » de « champ non transmis » : sans lui, chaque
      // auto-sauvegarde effaçait le rangement choisi à la création.
      categoryId: categoryId || null,
      clearCategory: !categoryId,
      notes: this.notes(),
      structure: this.structure(),
    });
  }

  // --- Verser une séance du calendrier dans la bibliothèque (mode séance planifiée) ---------
  // Une séance improvisée directement dans le calendrier n'avait aucune issue : pour en garder
  // un modèle, il fallait la reconstruire de zéro dans la bibliothèque.

  readonly saveAsOpen = signal(false);
  readonly saveAsBusy = signal(false);
  saveAsTitle = '';
  saveAsCategoryId = '';

  openSaveAs(): void {
    this.saveAsTitle = this.saveAsTitle || 'Séance du ' + new Date().toLocaleDateString('fr-FR');
    this.saveAsOpen.set(true);
  }

  closeSaveAs(): void { this.saveAsOpen.set(false); }

  /** Enregistre la structure courante, puis la verse dans la bibliothèque comme nouveau modèle. */
  saveAsTemplate(): void {
    const title = this.saveAsTitle.trim();
    if (!title) { this.toast.warning('Donne un titre à la séance.'); return; }
    if (this.saveAsBusy()) return;
    this.saveAsBusy.set(true);
    // On vide d'abord le debounce : sinon le modèle figerait la structure d'il y a dix secondes.
    this.autosave.flush().subscribe((ok) => {
      if (!ok) { this.saveAsBusy.set(false); this.toast.error('Enregistrement impossible.'); return; }
      this.course.saveWorkoutAsTemplate(this.athleteId(), this.workoutId(), {
        title, name: title, categoryId: this.saveAsCategoryId || null,
      }).subscribe({
        next: (created) => {
          this.saveAsBusy.set(false);
          this.saveAsOpen.set(false);
          this.toast.success(`« ${created.name} » ajoutée à la bibliothèque`);
        },
        error: () => { this.saveAsBusy.set(false); this.toast.error('Enregistrement dans la bibliothèque impossible.'); },
      });
    });
  }

  /**
   * Enregistrement explicite : ne fait qu'anticiper le debounce. En mode « adapter une séance »,
   * il vaut aussi « j'ai fini » et renvoie donc à la séance de l'athlète.
   */
  save(): void {
    this.autosave.flush().subscribe((ok) => {
      if (!ok) { this.toast.error('Enregistrement impossible.'); return; }
      if (this.isWorkout()) {
        this.toast.success('Séance adaptée pour l’athlète');
        this.router.navigate(['/app/athletes', this.athleteId(), 'workouts', this.workoutId()]);
      } else {
        this.toast.success('Séance enregistrée');
      }
    });
  }
}
