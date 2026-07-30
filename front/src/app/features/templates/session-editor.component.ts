import { CdkDragDrop, DragDropModule, moveItemInArray } from '@angular/cdk/drag-drop';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { Router, RouterLink } from '@angular/router';
import { AthleteService } from '../../core/services/athlete.service';
import { CourseService } from '../../core/services/course.service';
import { WorkoutService } from '../../core/services/workout.service';
import { ToastService } from '../../core/services/toast.service';
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
import { AthleteZoneValue } from '../../core/models/athlete-zone-value.model';
import { AthleteZoneValueService } from '../../core/services/athlete-zone-value.service';
import { ZonePickerComponent } from '../../shared/components/zone-picker/zone-picker.component';

/** Statut de complétude du profil pour la prescription course. */
export type ProfileStatus = 'measured' | 'estimated' | 'incomplete';

interface Section { key: keyof SessionStructure; label: string; }

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
  imports: [FormsModule, RouterLink, DragDropModule, IconComponent, ZonePickerComponent],
  templateUrl: './session-editor.component.html',
  styleUrl: './session-editor.component.scss',
})
export class SessionEditorComponent implements OnInit {
  // Paramètres de route (component input binding). Un seul jeu est renseigné selon le mode.
  readonly templateId = input<string>('');
  readonly workoutId = input<string>('');
  readonly athleteId = input<string>('');

  private readonly course = inject(CourseService);
  private readonly zoneService = inject(TrainingZoneService);
  private readonly metricService = inject(MetricTypeService);
  private readonly zoneValueService = inject(AthleteZoneValueService);
  private readonly athletes = inject(AthleteService);
  private readonly workoutService = inject(WorkoutService);
  private readonly drillService = inject(RunDrillService);
  private readonly physio = inject(PhysioService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);

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
    intervals: 'VO2',
    tempo: 'Marathon',
    threshold: 'Seuil',
    easy: 'Endurance fondamentale',
    warmup: 'Endurance fondamentale',
    cooldown: 'Récupération',
    recovery: 'Récupération',
    long: 'Endurance fondamentale',
    run: 'Endurance fondamentale',
  };

  private zoneIdByName(name: string): string | null {
    const found = this.zones().find((z) => z.name === name);
    return found?.id ?? this.zones()[0]?.id ?? null;
  }

  private defaultZoneIdForType(type: string): string | null {
    return this.zoneIdByName(this.DEFAULT_ZONE_BY_TYPE[type] ?? 'Endurance fondamentale');
  }

  /** Choix de zone d'un bloc (via le sélecteur riche) → met à jour la prescription + recalcule. */
  onBlockZone(b: CourseBlock, zoneId: string): void {
    if (!b.prescription) b.prescription = { zoneId };
    else b.prescription.zoneId = zoneId;
    this.recalc(b);
  }

  ngOnInit(): void {
    this.drillService.list().subscribe((d) => this.drills.set(d));
    this.zoneService.list().subscribe((z) => this.zones.set(z));
    this.metricService.list().subscribe((m) => this.metrics.set(m));

    if (this.isWorkout()) {
      // Mode séance planifiée : athlète fixe, on charge le snapshot puis on recalcule.
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

    // Mode modèle de bibliothèque.
    this.course.getStructure(this.templateId()).subscribe({
      next: (s) => {
        this.name.set(s.name);
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
    const type = isMain ? 'intervals' : (key === 'warmup' ? 'warmup' : 'cooldown');
    const base: CourseBlock = {
      id: 'b-' + Math.random().toString(36).slice(2, 9),
      type,
      reps: isMain ? 6 : null,
      distanceM: isMain ? 1000 : null,
      durationS: isMain ? null : 600,
      prescription: { zoneId: this.defaultZoneIdForType(type) },
    };
    const block: CourseBlock = { ...base, ...preset, id: base.id };
    // Un bloc se mesure soit en distance, soit en durée : on garde un seul des deux.
    if (preset?.durationS != null) { block.distanceM = null; }
    else if (preset?.distanceM != null) { block.durationS = null; }
    const s = this.structure();
    this.structure.set({ ...s, [key]: [...s[key], block] });
    this.recalc(block);
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
    this.recalc(b);
  }

  /** Bascule un bloc entre mesure par distance et par durée (un seul geste). */
  toggleMeasure(b: CourseBlock): void {
    this.setMeasure(b, this.measureOf(b) === 'distance' ? 'duration' : 'distance');
  }

  /** Bascule la récupération entre durée et distance. */
  toggleRecMeasure(b: CourseBlock): void {
    this.setRecMeasure(b, this.recMeasureOf(b) === 'duration' ? 'distance' : 'duration');
  }

  /** Durée exposée en minutes (plus lisible que des secondes) ; stockée en secondes. */
  durMin(b: CourseBlock): number | null {
    return b.durationS != null ? Math.round((b.durationS / 60) * 10) / 10 : null;
  }

  setDurMin(b: CourseBlock, min: number | null): void {
    b.durationS = min != null ? Math.round(min * 60) : null;
    this.recalc(b);
  }

  // --- Récupération inter-répétitions (fractionnés) --------------------------

  hasRecovery(b: CourseBlock): boolean { return !!b.recovery; }

  /** Ajoute une récupération par défaut (trot 1', zone Récupération). */
  addRecovery(b: CourseBlock): void {
    b.recovery = { type: 'jog', durationS: 60, distanceM: null, prescription: { zoneId: this.zoneIdByName('Récupération') } };
    this.recalcRecovery(b);
  }

  removeRecovery(b: CourseBlock): void {
    b.recovery = null;
    this.recCalc.update((m) => { const c = { ...m }; delete c[b.id]; return c; });
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
  }

  /** Durée de récup en secondes (les récups sont courtes → secondes plus lisibles que minutes). */
  recDurS(b: CourseBlock): number | null { return b.recovery?.durationS ?? null; }
  setRecDurS(b: CourseBlock, s: number | null): void {
    if (b.recovery) { b.recovery.durationS = s != null ? Math.round(s) : null; this.recalcRecovery(b); }
  }
  recDistM(b: CourseBlock): number | null { return b.recovery?.distanceM ?? null; }
  setRecDistM(b: CourseBlock, m: number | null): void {
    if (b.recovery) { b.recovery.distanceM = m != null ? Math.round(m) : null; this.recalcRecovery(b); }
  }
  setRecZone(b: CourseBlock, zoneId: string): void {
    if (b.recovery?.prescription) { b.recovery.prescription.zoneId = zoneId; this.recalcRecovery(b); }
  }
  setRecType(b: CourseBlock, type: string): void {
    if (b.recovery) { b.recovery.type = type; }
  }

  /** Recalcule la cible de la récupération d'un bloc (lecture depuis la zone de l'athlète). */
  recalcRecovery(b: CourseBlock): void {
    const a = this.calcAthleteId();
    const r = b.recovery;
    if (!a || !r?.prescription?.zoneId) return;
    this.course.sessionCalc(a, { zoneId: r.prescription.zoneId, distanceM: r.distanceM, durationS: r.durationS })
      .subscribe((c) => this.recCalc.update((map) => ({ ...map, [b.id]: c })));
  }

  removeBlock(key: keyof SessionStructure, id: string): void {
    const s = this.structure();
    this.structure.set({ ...s, [key]: s[key].filter((b) => b.id !== id) });
  }

  /** Réordonne les blocs d'une section par glisser-déposer (cohérent avec l'éditeur de force). */
  dropBlock(key: keyof SessionStructure, event: CdkDragDrop<CourseBlock[]>): void {
    if (event.previousIndex === event.currentIndex) return;
    const s = this.structure();
    const arr = [...s[key]];
    moveItemInArray(arr, event.previousIndex, event.currentIndex);
    this.structure.set({ ...s, [key]: arr });
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
      body = { zoneId: p.zoneId, reps: b.reps, distanceM: b.distanceM, durationS: b.durationS };
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

  save(): void {
    if (this.isWorkout()) {
      this.workoutService.updateStructure(this.athleteId(), this.workoutId(), this.structure()).subscribe({
        next: () => {
          this.toast.success('Séance adaptée pour l’athlète');
          this.router.navigate(['/app/athletes', this.athleteId(), 'workouts', this.workoutId()]);
        },
        error: () => this.toast.error('Enregistrement impossible.'),
      });
      return;
    }
    this.course.putStructure(this.templateId(), {
      notes: this.notes().trim() || null,
      structure: this.structure(),
    }).subscribe(() => {
      this.toast.success('Séance enregistrée');
    });
  }
}
