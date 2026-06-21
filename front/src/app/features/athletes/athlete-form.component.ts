import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AthleteRequest } from '../../core/models/athlete.model';
import { AthleteService } from '../../core/services/athlete.service';
import { ToastService } from '../../core/services/toast.service';

@Component({
  selector: 'app-athlete-form',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './athlete-form.component.html',
  styleUrl: './athletes.scss',
})
export class AthleteFormComponent implements OnInit {
  /** Lié au paramètre de route :id (édition) ; absent en création. */
  readonly id = input<string>();

  private readonly fb = inject(FormBuilder);
  private readonly athleteService = inject(AthleteService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  readonly submitting = signal(false);
  readonly loading = signal(false);

  get isEdit(): boolean {
    return !!this.id();
  }

  readonly form = this.fb.group({
    firstName: ['', [Validators.required, Validators.maxLength(120)]],
    lastName: ['', [Validators.required, Validators.maxLength(120)]],
    email: ['', [Validators.email]],
    birthDate: [''],
    sex: [''],
    level: [''],
    hrMax: [null as number | null, [Validators.min(100), Validators.max(230)]],
    hrRest: [null as number | null, [Validators.min(25), Validators.max(120)]],
    vma: [null as number | null, [Validators.min(5)]],
    weightKg: [null as number | null, [Validators.min(20)]],
    medicalNotes: ['', [Validators.maxLength(2048)]],
  });

  ngOnInit(): void {
    const id = this.id();
    if (id) {
      this.loading.set(true);
      this.athleteService.get(id).subscribe({
        next: (a) => {
          this.form.patchValue(a);
          this.loading.set(false);
        },
        error: () => {
          this.loading.set(false);
          this.router.navigate(['/app/athletes']);
        },
      });
    }
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    const payload = this.toRequest();
    const id = this.id();
    const call = id ? this.athleteService.update(id, payload) : this.athleteService.create(payload);

    call.subscribe({
      next: (athlete) => {
        this.toast.success(id ? 'Athlète mis à jour ✅' : 'Athlète créé 🎉');
        this.router.navigate(['/app/athletes', athlete.id]);
      },
      error: () => this.submitting.set(false),
    });
  }

  private toRequest(): AthleteRequest {
    const v = this.form.getRawValue();
    const blankToNull = (s: string | null) => (s && s.trim() ? s.trim() : null);
    return {
      firstName: v.firstName!,
      lastName: v.lastName!,
      email: blankToNull(v.email),
      birthDate: blankToNull(v.birthDate),
      sex: (blankToNull(v.sex) as AthleteRequest['sex']) ?? null,
      level: (blankToNull(v.level) as AthleteRequest['level']) ?? null,
      hrMax: v.hrMax ?? null,
      hrRest: v.hrRest ?? null,
      vma: v.vma ?? null,
      weightKg: v.weightKg ?? null,
      medicalNotes: blankToNull(v.medicalNotes),
    };
  }
}
