import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AthleteService } from '../../core/services/athlete.service';
import { CourseService } from '../../core/services/course.service';
import { ToastService } from '../../core/services/toast.service';
import { AthleteSummary } from '../../core/models/athlete.model';
import { CalculatedBlock, CourseBlock, PrescriptionRef, SessionStructure } from '../../core/models/course.model';

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
  private readonly toast = inject(ToastService);

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
  }

  blocks(key: keyof SessionStructure): CourseBlock[] {
    return this.structure()[key];
  }

  addBlock(key: keyof SessionStructure): void {
    const block: CourseBlock = {
      id: 'b-' + Math.random().toString(36).slice(2, 9),
      type: key === 'main' ? 'intervals' : 'easy',
      reps: key === 'main' ? 6 : null,
      distanceM: key === 'main' ? 1000 : null,
      durationS: key === 'main' ? null : 600,
      prescription: { ref: key === 'main' ? 'PCT_PACE_5KM' : 'PCT_LT1', minPct: 95, maxPct: 100 },
    };
    const s = this.structure();
    this.structure.set({ ...s, [key]: [...s[key], block] });
    this.recalc(block);
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
