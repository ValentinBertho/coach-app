import { CdkDragDrop, DragDropModule, moveItemInArray } from '@angular/cdk/drag-drop';
import { switchMap, tap } from 'rxjs';
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
import {
  CalculatedBlock, COURSE_BLOCK_TYPE_LABELS, CourseBlock, CourseBlockType, CoursePrescription,
  CourseRecovery, PRESCRIPTION_REF_LABELS, PRESCRIPTION_REF_SHORT, PrescriptionRef, SessionStructure,
} from '../../core/models/course.model';
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
import { formatMinSec, parseMinSec } from '../../core/utils/duration';
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

/**
 * Texte à afficher dans un champ de volume.
 *
 * <p>Une durée qui ne tombe pas sur la minute s'écrit en <b>m:ss</b> : « 1:30 », pas « 1.5 ».
 * C'est la lecture d'un coureur, et c'est celle qui manquait — une récupération de 90 s affichée
 * « 1.5 min » (ou arrondie à 2 min plus loin dans l'application) n'est pas la même séance.</p>
 */
export function volumeText(durationS: number | null | undefined,
                           distanceM: number | null | undefined, unit: VolumeUnit): string {
  if (unit === 'min' && durationS != null && distanceM == null && durationS % 60 !== 0) {
    return formatMinSec(durationS);
  }
  const value = toUnit(durationS, distanceM, unit);
  return value == null ? '' : String(value);
}

/**
 * Nombre saisi dans un champ de volume : la virgule décimale française est acceptée, une saisie
 * vide efface la valeur. `undefined` signale une saisie illisible — à ignorer, pas à écrire.
 */
export function parseNumber(raw: string): number | null | undefined {
  const text = raw.trim().replace(',', '.');
  if (!text) return null;
  const value = Number(text);
  return Number.isFinite(value) && value >= 0 ? value : undefined;
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
  /**
   * Effort perçu attendu pour la séance entière (1–10), ou `null` quand rien n'est annoncé.
   *
   * <p>Nul plutôt que zéro : « aucune annonce » et « effort nul » ne sont pas la même chose, et
   * l'athlète ne doit pas voir « effort attendu : 0 » sur une séance dont le coach n'a
   * simplement rien dit.</p>
   */
  readonly targetRpe = signal<number | null>(null);
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

  // --- Allure sur mesure : fourchette en % d'un référentiel ------------------
  // Les zones du club sont une échelle de référence, pas un réglage de séance : on ne les
  // retouche pas pour un fractionné particulier. Un « 6×1000 à 102–106 % de VC » n'avait donc
  // aucun endroit où s'écrire, alors que le moteur sait le calculer depuis toujours.

  /** Bornes acceptées par le serveur pour un pourcentage prescrit. */
  private static readonly PCT_MIN = 30;
  private static readonly PCT_MAX = 150;

  /** Référentiels proposés : seuils physiologiques d'abord, allures de course ensuite. */
  readonly prescriptionRefs: { value: PrescriptionRef; label: string }[] =
    (Object.keys(PRESCRIPTION_REF_LABELS) as PrescriptionRef[])
      .map((value) => ({ value, label: PRESCRIPTION_REF_LABELS[value] }));

  /**
   * Fourchette de départ par type de bloc, pour que le premier clic donne déjà une prescription
   * plausible plutôt qu'un « 100–100 % » à retoucher entièrement.
   */
  private readonly PCT_DEFAULTS: Record<string, { ref: PrescriptionRef; minPct: number; maxPct: number }> = {
    intervals: { ref: 'PCT_VC', minPct: 102, maxPct: 108 },
    threshold: { ref: 'PCT_LT2', minPct: 96, maxPct: 100 },
    tempo: { ref: 'PCT_LT2', minPct: 88, maxPct: 94 },
    easy: { ref: 'PCT_LT1', minPct: 80, maxPct: 90 },
    long: { ref: 'PCT_LT1', minPct: 85, maxPct: 92 },
    run: { ref: 'PCT_LT1', minPct: 85, maxPct: 95 },
    warmup: { ref: 'PCT_LT1', minPct: 70, maxPct: 80 },
    cooldown: { ref: 'PCT_LT1', minPct: 70, maxPct: 80 },
    recovery: { ref: 'PCT_LT1', minPct: 65, maxPct: 78 },
  };

  /** Zone d'origine d'un bloc passé en % : retrouvée telle quelle si le coach fait marche arrière. */
  private readonly zoneBeforePct = signal<Record<string, string | null>>({});

  /** Le bloc est-il prescrit en fourchette de % plutôt que par une zone ? */
  usesPct(b: CourseBlock): boolean {
    return !!b.prescription?.custom && !!b.prescription?.ref;
  }

  /** Référentiel court affiché à côté des bornes (« % de VC »). */
  pctRefShort(b: CourseBlock): string {
    const ref = b.prescription?.ref;
    return ref ? PRESCRIPTION_REF_SHORT[ref] : '';
  }

  /** Passe le bloc en allure sur mesure, en partant d'une fourchette usuelle pour son type. */
  switchToPct(b: CourseBlock): void {
    const d = this.PCT_DEFAULTS[b.type] ?? { ref: 'PCT_LT2' as PrescriptionRef, minPct: 90, maxPct: 100 };
    this.zoneBeforePct.update((m) => ({ ...m, [b.id]: b.prescription?.zoneId ?? null }));
    // La zone disparaît : elle primerait sur le %. La zone cardio aussi — le chemin en %
    // estime la FC par interpolation depuis les seuils, il n'en lit aucune.
    b.prescription = { zoneId: null, hrZoneId: null, ref: d.ref, minPct: d.minPct, maxPct: d.maxPct, custom: true };
    this.onBlockEdited(b);
  }

  /** Revient à une prescription par zone (celle d'avant le passage en %, si elle existait). */
  switchToZone(b: CourseBlock): void {
    const previous = this.zoneBeforePct()[b.id] ?? this.defaultZoneIdForType(b.type);
    b.prescription = { zoneId: previous };
    this.onBlockEdited(b);
  }

  setPctRef(b: CourseBlock, ref: PrescriptionRef): void {
    if (!b.prescription) return;
    b.prescription.ref = ref;
    this.onBlockEdited(b);
  }

  /**
   * Borne d'une fourchette en %, écrite <b>à la validation du champ</b> (sortie ou Entrée).
   *
   * <p>Elle l'était à chaque frappe, en repliant aussitôt la valeur dans [30, 150]. Taper « 102 »
   * était donc impossible : le « 1 » devenait 30 sous les doigts, la suite de la frappe se collait
   * derrière (« 305 »), repliée à 150 — et la borne haute, poussée par la basse, suivait. Le champ
   * se battait contre celui qui le remplissait, ce qui explique le « des fois ça déconne ».</p>
   *
   * <p>Les deux bornes continuent de se pousser l'une l'autre, mais une seule fois, quand la
   * saisie est finie : une borne basse au-dessus de la haute ferait refuser le calcul par le
   * serveur, et le coach n'aurait pour tout retour qu'une cible disparue.</p>
   */
  setPctBoundText(b: CourseBlock, which: 'min' | 'max', input: HTMLInputElement): void {
    if (b.prescription && this.applyPctBound(b.prescription, which, input.value)) {
      this.onBlockEdited(b);
    }
    input.value = String((which === 'min' ? b.prescription?.minPct : b.prescription?.maxPct) ?? '');
  }

  /**
   * Écrit une borne dans une prescription. Une saisie vide ou illisible ne remplace <b>rien</b> :
   * on ne remplace pas une prescription par une valeur inventée parce que le champ a été vidé.
   *
   * @return vrai si la prescription a changé
   */
  private applyPctBound(p: CoursePrescription, which: 'min' | 'max', raw: string): boolean {
    const value = parseNumber(raw);
    if (value == null || value === undefined) return false;
    const pct = Math.round(Math.min(SessionEditorComponent.PCT_MAX,
      Math.max(SessionEditorComponent.PCT_MIN, value)));
    if (which === 'min') {
      p.minPct = pct;
      if (p.maxPct == null || p.maxPct < pct) p.maxPct = pct;
    } else {
      p.maxPct = pct;
      if (p.minPct == null || p.minPct > pct) p.minPct = pct;
    }
    return true;
  }

  // --- Récupération : prescrite par zone, ou à la main en % d'un référentiel ------------------
  // La récupération n'acceptait qu'une zone du club, quand le bloc au-dessus d'elle acceptait déjà
  // une fourchette écrite pour la séance. Or c'est le même besoin : « récup à 65–70 % de LT1 » ne
  // rentre dans aucune bande nommée, et les zones ne se retouchent pas séance par séance.

  recUsesPct(b: CourseBlock): boolean {
    const p = b.recovery?.prescription;
    return !!p?.custom && !!p?.ref;
  }

  recPctRefShort(b: CourseBlock): string {
    const ref = b.recovery?.prescription?.ref;
    return ref ? PRESCRIPTION_REF_SHORT[ref] : '';
  }

  /** Passe la récup en allure sur mesure, en partant d'une fourchette de récupération usuelle. */
  switchRecToPct(b: CourseBlock): void {
    const r = b.recovery;
    if (!r) return;
    this.recZoneBeforePct.update((m) => ({ ...m, [b.id]: r.prescription?.zoneId ?? null }));
    r.prescription = { zoneId: null, hrZoneId: null, ref: 'PCT_LT1', minPct: 55, maxPct: 70, custom: true };
    this.recalcRecovery(b);
    this.touch();
  }

  /** Revient à une récup prescrite par zone (celle d'avant, si elle existait). */
  switchRecToZone(b: CourseBlock): void {
    const r = b.recovery;
    if (!r) return;
    r.prescription = { zoneId: this.recZoneBeforePct()[b.id] ?? this.zoneIdByName('Récupération') };
    this.recalcRecovery(b);
    this.touch();
  }

  setRecPctRef(b: CourseBlock, ref: PrescriptionRef): void {
    const p = b.recovery?.prescription;
    if (!p) return;
    p.ref = ref;
    this.recalcRecovery(b);
    this.touch();
  }

  setRecPctBoundText(b: CourseBlock, which: 'min' | 'max', input: HTMLInputElement): void {
    const p = b.recovery?.prescription;
    if (p && this.applyPctBound(p, which, input.value)) {
      this.recalcRecovery(b);
      this.touch();
    }
    input.value = String((which === 'min' ? p?.minPct : p?.maxPct) ?? '');
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
          this.title.set(p.title ?? '');
          this.persistedTitle = this.title();
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
        this.targetRpe.set(s.targetRpe ?? null);
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
        // Récup entre répétitions : (reps - 1) × la récup, et autant de fois qu'il y a de séries.
        const sets = this.setCount(b);
        const rc = recCalc[b.id];
        const inter = (b.reps && b.reps > 1 ? b.reps - 1 : 0) * sets;
        if (rc?.computable && inter) {
          if (rc.estimatedDurationS) durationS += rc.estimatedDurationS * inter;
          if (rc.estimatedDistanceM) distanceM += rc.estimatedDistanceM * inter;
        }
        // Récup entre séries : (séries - 1). Elle n'a pas de cible calculée — c'est un temps,
        // pris tel quel, comme le fait le serveur pour le total de la séance.
        if (sets > 1 && b.setRecovery?.durationS) {
          durationS += b.setRecovery.durationS * (sets - 1);
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
  /** Zone de la récup avant son passage en %, pour pouvoir y revenir sans la retrouver à la main. */
  private readonly recZoneBeforePct = signal<Record<string, string | null>>({});

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

  /** Texte du champ de volume : « 1:30 » dès que la durée ne tombe pas sur la minute. */
  volumeText(b: CourseBlock): string {
    return volumeText(b.durationS, b.distanceM, this.unitOf(b));
  }

  /**
   * Écrit le volume saisi. Deux écritures acceptées : un nombre dans l'unité du champ, ou un
   * temps en <b>min:sec</b> — « 1:30 » comme l'écrit un coach, sans avoir à convertir en 90 s
   * puis à basculer l'unité.
   *
   * <p>Le champ est renormalisé à la sortie : une saisie illisible ne doit pas rester affichée
   * comme si elle avait été prise en compte.</p>
   */
  setVolumeText(b: CourseBlock, input: HTMLInputElement): void {
    const seconds = parseMinSec(input.value);
    if (seconds != null) {
      // Une écriture sexagésimale est un temps, quelle que soit l'unité affichée : on bascule le
      // champ en minutes, seule unité où « 1:30 » se relit tel quel.
      this.blockUnit.update((m) => ({ ...m, [b.id]: 'min' }));
      applyUnit(b, 's', seconds);
      this.onBlockEdited(b);
    } else {
      const value = parseNumber(input.value);
      if (value !== undefined) {
        // L'unité se fige dès la première saisie : sans cela, taper « 60 » en minutes ferait
        // basculer le champ en heures, et la valeur affichée changerait toute seule.
        const unit = this.unitOf(b);
        this.blockUnit.update((m) => ({ ...m, [b.id]: unit }));
        applyUnit(b, unit, value);
        this.onBlockEdited(b);
      }
    }
    input.value = this.volumeText(b);
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

  recVolumeText(b: CourseBlock): string {
    const r = b.recovery;
    return r ? volumeText(r.durationS ?? null, r.distanceM ?? null, this.recUnitOf(b)) : '';
  }

  /** Même contrat que {@link setVolumeText}, pour la récupération entre répétitions. */
  setRecVolumeText(b: CourseBlock, input: HTMLInputElement): void {
    if (!b.recovery) return;
    const seconds = parseMinSec(input.value);
    if (seconds != null) {
      this.recUnit.update((m) => ({ ...m, [b.id]: 'min' }));
      applyUnit(b.recovery, 's', seconds);
      this.recalcRecovery(b);
      this.touch();
    } else {
      const value = parseNumber(input.value);
      if (value !== undefined) {
        const unit = this.recUnitOf(b);
        this.recUnit.update((m) => ({ ...m, [b.id]: unit }));
        applyUnit(b.recovery, unit, value);
        this.recalcRecovery(b);
        this.touch();
      }
    }
    input.value = this.recVolumeText(b);
  }

  setRecUnit(b: CourseBlock, unit: VolumeUnit): void {
    this.recUnit.update((m) => ({ ...m, [b.id]: unit }));
    const r = b.recovery;
    if (!r || isTime(unit) === (r.durationS != null && r.distanceM == null)) return;
    this.setRecMeasure(b, isTime(unit) ? 'duration' : 'distance');
  }

  // --- Séries : le bloc entier doublé, triplé… ------------------------------
  // « 2 × (6 × 400 m) » n'avait aucune écriture : il fallait saisir deux blocs identiques, donc
  // les retoucher deux fois à chaque ajustement — et les récupérations entre séries n'étaient
  // comptées nulle part.

  /** Séries effectives : 1 quand rien n'est saisi (toute séance écrite avant les séries). */
  setCount(b: CourseBlock): number {
    return b.sets != null && b.sets > 1 ? Math.floor(b.sets) : 1;
  }

  /**
   * Répétitions à envoyer au calculateur, séries comprises. `null` reste `null` quand il n'y a
   * ni série ni répétition : un bloc simple ne doit pas se mettre à parler de « 1 × ».
   */
  private effectiveReps(b: CourseBlock): number | null | undefined {
    const sets = this.setCount(b);
    return sets > 1 ? (b.reps ?? 1) * sets : b.reps;
  }

  setBlockSets(b: CourseBlock, value: number | null): void {
    const sets = value == null || value < 2 ? null : Math.min(20, Math.round(value));
    b.sets = sets;
    // Une seule série ne laisse pas de récupération entre séries derrière elle : elle ne serait
    // plus visible nulle part et repartirait à la prochaine série ajoutée.
    if (sets == null) b.setRecovery = null;
    this.onBlockEdited(b);
  }

  addSeriesRecovery(b: CourseBlock): void {
    b.setRecovery = {
      type: 'jog', durationS: 180, distanceM: null,
      prescription: { zoneId: this.zoneIdByName('Récupération') },
    };
    this.touch();
  }

  removeSeriesRecovery(b: CourseBlock): void {
    b.setRecovery = null;
    this.touch();
  }

  /** Récup entre séries : toujours un temps, écrit en minutes ou en min:sec. */
  seriesRecText(b: CourseBlock): string {
    return volumeText(b.setRecovery?.durationS ?? null, null, 'min');
  }

  setSeriesRecText(b: CourseBlock, input: HTMLInputElement): void {
    const r = b.setRecovery;
    if (!r) return;
    const seconds = parseMinSec(input.value);
    if (seconds != null) {
      applyUnit(r, 's', seconds);
      this.touch();
    } else {
      const value = parseNumber(input.value);
      if (value !== undefined) {
        applyUnit(r, 'min', value);
        this.touch();
      }
    }
    input.value = this.seriesRecText(b);
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
    const p = r?.prescription;
    if (!a || !p) return;
    // Même ordre que pour un bloc : une fourchette écrite par le coach prime sur la zone.
    let body: Parameters<CourseService['sessionCalc']>[1] | null = null;
    if (p.custom && p.ref && p.minPct != null && p.maxPct != null) {
      body = { ref: p.ref, minPct: p.minPct, maxPct: p.maxPct, distanceM: r!.distanceM, durationS: r!.durationS };
    } else if (p.zoneId) {
      body = { zoneId: p.zoneId, distanceM: r!.distanceM, durationS: r!.durationS };
    }
    if (!body) return;
    this.course.sessionCalc(a, body)
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
    // Séries comprises : pour le volume, « 2 × (6 × 400 m) » vaut 12 × 400 m — même règle que
    // le serveur, sinon l'aperçu de l'éditeur et le total de la séance se contrediraient.
    const reps = this.effectiveReps(b);
    // Ordre calqué sur le serveur : une fourchette voulue par le coach prime sur la zone.
    if (p.custom && p.ref && p.minPct != null && p.maxPct != null) {
      body = { ref: p.ref, minPct: p.minPct, maxPct: p.maxPct, reps, distanceM: b.distanceM, durationS: b.durationS };
    } else if (p.zoneId) {
      body = {
        zoneId: p.zoneId, hrZoneId: p.hrZoneId ?? null,
        reps, distanceM: b.distanceM, durationS: b.durationS,
      };
    } else if (p.ref && p.minPct != null && p.maxPct != null) {
      body = { ref: p.ref, minPct: p.minPct, maxPct: p.maxPct, reps, distanceM: b.distanceM, durationS: b.durationS };
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

  /** RPE global. Zéro vaut « pas d'annonce » — c'est aussi ce que fait le bouton « Effacer ». */
  setTargetRpe(value: number | string): void {
    const n = Number(value);
    this.targetRpe.set(Number.isFinite(n) && n >= 1 && n <= 10 ? n : null);
    this.touch();
  }

  /** « 7/10 », ou un tiret tant que rien n'est annoncé. */
  targetRpeLabel(): string {
    const v = this.targetRpe();
    return v === null ? '—' : `${v}/10`;
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
  /**
   * Dernier titre effectivement enregistré. L'auto-sauvegarde tourne en boucle : sans ce repère,
   * chaque frappe dans un autre champ renverrait le même libellé au serveur.
   */
  private persistedTitle = '';

  private persist() {
    if (this.isWorkout()) {
      const structure$ = this.workoutService.updateStructure(
        this.athleteId(), this.workoutId(), this.structure());
      const title = this.title().trim();
      // Un titre vidé n'efface pas celui de la séance : le serveur refuse un libellé vide, et
      // une séance sans nom ne se lit ni au calendrier ni côté athlète.
      if (!title || title === this.persistedTitle) {
        return structure$;
      }
      return structure$.pipe(
        switchMap(() => this.workoutService.rename(this.athleteId(), this.workoutId(), title)),
        tap(() => { this.persistedTitle = title; }),
      );
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
      // Zéro efface côté serveur ; `null` signifierait « champ non transmis ».
      targetRpe: this.targetRpe() ?? 0,
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

  /**
   * Ouvre le versement en bibliothèque, pré-rempli avec le titre de la séance adaptée.
   *
   * <p>Il proposait « Séance du 20/08/2026 ». Or on verse en bibliothèque précisément parce qu'on
   * vient d'adapter quelque chose qui mérite d'être gardé : le coach avait donc à retaper un nom
   * qu'il venait d'écrire deux champs plus haut, et une date ne dit rien de ce que la séance
   * contient — c'est le pire nom possible pour une entrée de bibliothèque qu'on cherchera par son
   * contenu.</p>
   */
  openSaveAs(): void {
    this.saveAsTitle = this.saveAsTitle
      || this.title().trim()
      || 'Séance du ' + new Date().toLocaleDateString('fr-FR');
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
   * Enregistrement explicite : ne fait qu'anticiper le debounce, mais vaut aussi « j'ai fini ».
   * Il renvoie donc à l'écran d'où l'on vient — la séance de l'athlète en mode adaptation, la
   * bibliothèque sinon. Rester sur l'éditeur après avoir cliqué « Enregistrer » laissait le coach
   * sans issue évidente alors qu'il venait de dire qu'il avait terminé.
   */
  save(): void {
    this.autosave.flush().subscribe((ok) => {
      if (!ok) { this.toast.error('Enregistrement impossible.'); return; }
      if (this.isWorkout()) {
        this.toast.success('Séance adaptée pour l’athlète');
        this.router.navigate(['/app/athletes', this.athleteId(), 'workouts', this.workoutId()]);
      } else {
        this.toast.success('Séance enregistrée');
        this.router.navigate(['/app/library/course'], { queryParams: this.libraryQueryParams() });
      }
    });
  }

  /**
   * Retour à la bibliothèque sur la catégorie de la séance : un coach qui travaille sa catégorie
   * « Seuil » y enchaîne plusieurs séances, et retomber sur la vue globale à chaque enregistrement
   * l'oblige à re-filtrer à chaque fois.
   */
  libraryQueryParams(): { cat: string | null } {
    return { cat: this.categoryId() || null };
  }
}
