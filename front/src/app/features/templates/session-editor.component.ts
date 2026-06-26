import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AthleteService } from '../../core/services/athlete.service';
import { CourseService } from '../../core/services/course.service';
import { ToastService } from '../../core/services/toast.service';
import { RunDrillService } from '../../core/services/run-drill.service';
import { AthleteSummary } from '../../core/models/athlete.model';
import { CalculatedBlock, CourseBlock, PrescriptionRef, SessionStructure } from '../../core/models/course.model';
import { RunDrill } from '../../core/models/run-drill.model';

interface Section { key: keyof SessionStructure; label: string; }

/** Éditeur de séance course (s-session-editor) : blocs prescrits en fourchettes + calculateur live. */
@Component({
  selector: 'app-session-editor',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink],
  templateUrl: './session-editor.component.html',
  styleUrl: './session-editor.component.scss',
})
export class SessionEditorComponent implements OnInit {
  readonly templateId = input.required<string>();

  private readonly course = inject(CourseService);
  private readonly athletes = inject(AthleteService);
  private readonly drillService = inject(RunDrillService);
  private readonly toast = inject(ToastService);

  readonly drills = signal<RunDrill[]>([]);

  readonly name = signal('');
  readonly loading = signal(true);
  readonly structure = signal<SessionStructure>({ warmup: [], main: [], cooldown: [] });
  readonly calc = signal<Record<string, CalculatedBlock>>({});
  readonly athleteList = signal<AthleteSummary[]>([]);
  readonly athleteId = signal('');

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
    this.course.getStructure(this.templateId()).subscribe({
      next: (s) => {
        this.name.set(s.name);
        this.structure.set(s.structure ?? { warmup: [], main: [], cooldown: [] });
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
    this.athletes.list({ page: 0 }).subscribe((p) => this.athleteList.set(p.content));
    this.drillService.list().subscribe((d) => this.drills.set(d));
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

  /** Types de bloc proposés (liste fermée plutôt qu'un champ libre, plus intuitif). */
  readonly blockTypes = ['easy', 'warmup', 'cooldown', 'intervals', 'tempo', 'threshold', 'recovery', 'long', 'run'];

  /** Blocs pré-remplis en un clic, par section (valeurs de départ raisonnables, en fourchettes). */
  presetsFor(key: keyof SessionStructure): { label: string; block: Partial<CourseBlock> }[] {
    if (key === 'warmup') {
      return [
        { label: 'Échauffement 15 min', block: { type: 'warmup', durationS: 900, prescription: { ref: 'PCT_LT1', minPct: 60, maxPct: 75 } } },
      ];
    }
    if (key === 'cooldown') {
      return [
        { label: 'Retour au calme 10 min', block: { type: 'cooldown', durationS: 600, prescription: { ref: 'PCT_LT1', minPct: 55, maxPct: 70 } } },
      ];
    }
    return [
      { label: 'Intervalles 6×400 m', block: { type: 'intervals', reps: 6, distanceM: 400, prescription: { ref: 'PCT_PACE_5KM', minPct: 98, maxPct: 105 } } },
      { label: 'Seuil 20 min', block: { type: 'threshold', durationS: 1200, prescription: { ref: 'PCT_LT2', minPct: 95, maxPct: 100 } } },
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
    this.athleteId.set(id);
    this.calc.set({});
    if (!id) return;
    for (const sec of this.sections) {
      for (const b of this.blocks(sec.key)) this.recalc(b);
    }
  }

  recalc(b: CourseBlock): void {
    const a = this.athleteId();
    const p = b.prescription;
    if (!a || !p?.ref || p.minPct == null || p.maxPct == null) return;
    this.course
      .sessionCalc(a, { ref: p.ref, minPct: p.minPct, maxPct: p.maxPct, reps: b.reps, distanceM: b.distanceM, durationS: b.durationS })
      .subscribe((c) => this.calc.update((map) => ({ ...map, [b.id]: c })));
  }

  save(): void {
    this.course.putStructure(this.templateId(), { structure: this.structure() }).subscribe(() => {
      this.toast.success('Structure enregistrée');
    });
  }
}
