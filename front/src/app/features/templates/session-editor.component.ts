import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AthleteService } from '../../core/services/athlete.service';
import { CourseService } from '../../core/services/course.service';
import { WorkoutService } from '../../core/services/workout.service';
import { ToastService } from '../../core/services/toast.service';
import { RunDrillService } from '../../core/services/run-drill.service';
import { PhysioService } from '../../core/services/physio.service';
import { AthleteSummary } from '../../core/models/athlete.model';
import { CalculatedBlock, COURSE_BLOCK_TYPE_LABELS, CourseBlock, CourseBlockType, PrescriptionRef, SessionStructure } from '../../core/models/course.model';
import { PhysioProfile } from '../../core/models/physio.model';
import { RunDrill } from '../../core/models/run-drill.model';

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
  imports: [FormsModule, RouterLink],
  templateUrl: './session-editor.component.html',
  styleUrl: './session-editor.component.scss',
})
export class SessionEditorComponent implements OnInit {
  // Paramètres de route (component input binding). Un seul jeu est renseigné selon le mode.
  readonly templateId = input<string>('');
  readonly workoutId = input<string>('');
  readonly athleteId = input<string>('');

  private readonly course = inject(CourseService);
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
  readonly structure = signal<SessionStructure>({ warmup: [], main: [], cooldown: [] });
  readonly calc = signal<Record<string, CalculatedBlock>>({});
  readonly athleteList = signal<AthleteSummary[]>([]);
  /** Athlète du calculateur live (sélectionnable en mode modèle, fixe en mode séance planifiée). */
  readonly calcAthleteId = signal('');

  readonly sections: Section[] = [
    { key: 'warmup', label: 'Échauffement' },
    { key: 'main', label: 'Corps de séance' },
    { key: 'cooldown', label: 'Retour au calme' },
  ];

  readonly refs: { value: PrescriptionRef; label: string }[] = [
    { value: 'PCT_LT1', label: '% LT1' },
    { value: 'PCT_LT2', label: '% LT2' },
    { value: 'PCT_VC', label: '% VC' },
    { value: 'PCT_PACE_5KM', label: '% allure 5 km' },
    { value: 'PCT_PACE_10KM', label: '% allure 10 km' },
    { value: 'PCT_PACE_SEMI', label: '% allure semi' },
    { value: 'PCT_PACE_MARATHON', label: '% allure marathon' },
  ];

  ngOnInit(): void {
    this.drillService.list().subscribe((d) => this.drills.set(d));

    if (this.isWorkout()) {
      // Mode séance planifiée : athlète fixe, on charge le snapshot puis on recalcule.
      this.name.set('Adapter la séance');
      this.calcAthleteId.set(this.athleteId());
      this.loadProfile(this.athleteId());
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
        this.structure.set(s.structure ?? { warmup: [], main: [], cooldown: [] });
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
    this.athletes.list({ page: 0 }).subscribe((p) => this.athleteList.set(p.content));
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
    let durationS = 0;
    let distanceM = 0;
    let hasAny = false;
    for (const sec of this.sections) {
      for (const b of this.structure()[sec.key]) {
        const c = calc[b.id];
        if (!c?.computable) continue;
        if (c.estimatedDurationS) { durationS += c.estimatedDurationS; hasAny = true; }
        if (c.estimatedDistanceM) { distanceM += c.estimatedDistanceM; hasAny = true; }
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

  /**
   * Référentiel adapté au profil : sans seuil mesuré, on prescrit en allures de course plutôt
   * qu'en % LT1/LT2/VC. Le moteur applique le même repli (LT1≈marathon, LT2≈10 km, VC≈5 km),
   * donc les cibles calculées sont identiques — mais la référence affichée reste cohérente avec
   * ce que l'athlète possède réellement.
   */
  private adaptRef(ref: PrescriptionRef): PrescriptionRef {
    if (this.profileStatus() === 'measured') return ref;
    switch (ref) {
      case 'PCT_LT1': return 'PCT_PACE_MARATHON';
      case 'PCT_LT2': return 'PCT_PACE_10KM';
      case 'PCT_VC': return 'PCT_PACE_5KM';
      default: return ref;
    }
  }

  /** Blocs pré-remplis en un clic, par section (valeurs de départ raisonnables, en fourchettes). */
  presetsFor(key: keyof SessionStructure): { label: string; block: Partial<CourseBlock> }[] {
    const ref = (r: PrescriptionRef) => this.adaptRef(r);
    if (key === 'warmup') {
      return [
        { label: 'Échauffement 15 min', block: { type: 'warmup', durationS: 900, prescription: { ref: ref('PCT_LT1'), minPct: 60, maxPct: 75 } } },
      ];
    }
    if (key === 'cooldown') {
      return [
        { label: 'Retour au calme 10 min', block: { type: 'cooldown', durationS: 600, prescription: { ref: ref('PCT_LT1'), minPct: 55, maxPct: 70 } } },
      ];
    }
    return [
      { label: 'Intervalles 6×400 m', block: { type: 'intervals', reps: 6, distanceM: 400, prescription: { ref: 'PCT_PACE_5KM', minPct: 98, maxPct: 105 } } },
      { label: 'Seuil 20 min', block: { type: 'threshold', durationS: 1200, prescription: { ref: ref('PCT_LT2'), minPct: 95, maxPct: 100 } } },
      { label: 'Tempo 4 km', block: { type: 'tempo', distanceM: 4000, prescription: { ref: 'PCT_PACE_10KM', minPct: 95, maxPct: 100 } } },
    ];
  }

  addBlock(key: keyof SessionStructure, preset?: Partial<CourseBlock>): void {
    const isMain = key === 'main';
    const base: CourseBlock = {
      id: 'b-' + Math.random().toString(36).slice(2, 9),
      type: isMain ? 'intervals' : (key === 'warmup' ? 'warmup' : 'cooldown'),
      reps: isMain ? 6 : null,
      distanceM: isMain ? 1000 : null,
      durationS: isMain ? null : 600,
      prescription: { ref: isMain ? 'PCT_PACE_5KM' : 'PCT_LT1', minPct: 95, maxPct: 100 },
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

  /** Durée exposée en minutes (plus lisible que des secondes) ; stockée en secondes. */
  durMin(b: CourseBlock): number | null {
    return b.durationS != null ? Math.round((b.durationS / 60) * 10) / 10 : null;
  }

  setDurMin(b: CourseBlock, min: number | null): void {
    b.durationS = min != null ? Math.round(min * 60) : null;
    this.recalc(b);
  }

  removeBlock(key: keyof SessionStructure, id: string): void {
    const s = this.structure();
    this.structure.set({ ...s, [key]: s[key].filter((b) => b.id !== id) });
  }

  onAthleteChange(id: string): void {
    this.calcAthleteId.set(id);
    this.calc.set({});
    this.profile.set(null);
    if (id) this.loadProfile(id);
    this.recalcAll();
  }

  /** Charge le profil physio de l'athlète du calculateur (pour le statut + le bootstrap chrono). */
  private loadProfile(athleteId: string): void {
    this.physio.profile(athleteId).subscribe({
      next: (p) => this.profile.set(p),
      error: () => this.profile.set(null),
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
        this.recalcAll();
        this.bootstrapBusy.set(false);
      },
      error: () => { this.bootstrapBusy.set(false); this.toast.error('Enregistrement impossible.'); },
    });
  }

  recalc(b: CourseBlock): void {
    const a = this.calcAthleteId();
    const p = b.prescription;
    if (!a || !p?.ref || p.minPct == null || p.maxPct == null) return;
    this.course
      .sessionCalc(a, { ref: p.ref, minPct: p.minPct, maxPct: p.maxPct, reps: b.reps, distanceM: b.distanceM, durationS: b.durationS })
      .subscribe((c) => this.calc.update((map) => ({ ...map, [b.id]: c })));
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
    this.course.putStructure(this.templateId(), { structure: this.structure() }).subscribe(() => {
      this.toast.success('Structure enregistrée');
    });
  }
}
