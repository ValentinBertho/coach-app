import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AthleteRequest } from '../../core/models/athlete.model';
import { AdminService } from '../../core/services/admin.service';
import { ToastService } from '../../core/services/toast.service';

/** Édition complète d'un athlète côté admin (charge tout le profil pour ne rien écraser). */
@Component({
  selector: 'app-admin-athlete-edit',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './admin-athlete-edit.component.html',
})
export class AdminAthleteEditComponent implements OnInit {
  readonly id = input.required<string>();

  private readonly fb = inject(FormBuilder);
  private readonly admin = inject(AdminService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  readonly loading = signal(true);
  readonly submitting = signal(false);

  readonly form = this.fb.group({
    firstName: ['', [Validators.required]],
    lastName: ['', [Validators.required]],
    email: ['', [Validators.email]],
    sex: [''],
    level: [''],
    status: [''],
    hrMax: [null as number | null],
    hrRest: [null as number | null],
    vma: [null as number | null],
    weightKg: [null as number | null],
    medicalNotes: [''],
  });

  ngOnInit(): void {
    this.admin.athlete(this.id()).subscribe({
      next: (a) => {
        this.form.patchValue(a as never);
        this.loading.set(false);
      },
      error: () => this.router.navigate(['/admin/athletes']),
    });
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    const v = this.form.getRawValue();
    const blank = (s: string | null) => (s && s.trim() ? s.trim() : null);
    const payload: AthleteRequest = {
      firstName: v.firstName!,
      lastName: v.lastName!,
      email: blank(v.email),
      sex: (blank(v.sex) as AthleteRequest['sex']) ?? null,
      level: (blank(v.level) as AthleteRequest['level']) ?? null,
      hrMax: v.hrMax ?? null,
      hrRest: v.hrRest ?? null,
      vma: v.vma ?? null,
      weightKg: v.weightKg ?? null,
      medicalNotes: blank(v.medicalNotes),
    };
    this.admin.updateAthlete(this.id(), payload).subscribe({
      next: () => {
        this.toast.success('Athlète mis à jour.');
        this.router.navigate(['/admin/athletes']);
      },
      error: () => this.submitting.set(false),
    });
  }
}
