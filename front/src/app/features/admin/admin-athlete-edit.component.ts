import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AthleteRequest } from '../../core/models/athlete.model';
import { ATHLETE_STATUS_LABELS } from '../../core/models/admin.model';
import { AdminService } from '../../core/services/admin.service';
import { ToastService } from '../../core/services/toast.service';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { SkeletonComponent } from '../../shared/components/skeleton/skeleton.component';

/**
 * Édition complète d'un athlète côté admin (charge tout le profil pour ne rien écraser).
 *
 * <p><b>Deux défauts corrigés ici.</b> La date de naissance n'était pas dans le formulaire alors
 * que le serveur l'écrase avec ce qu'il reçoit : <b>tout enregistrement depuis cet écran effaçait
 * la date de naissance de l'athlète</b>, silencieusement. Et le sélecteur de statut n'était relié
 * à rien — {@code AthleteRequest} ne portait pas le champ, si bien qu'archiver un athlète depuis
 * l'administration semblait fonctionner sans jamais rien changer.</p>
 */
@Component({
  selector: 'app-admin-athlete-edit',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [SkeletonComponent, ReactiveFormsModule, RouterLink, IconComponent],
  templateUrl: './admin-athlete-edit.component.html',
  styleUrl: './admin-athlete-edit.component.scss',
})
export class AdminAthleteEditComponent implements OnInit {
  readonly id = input.required<string>();

  private readonly fb = inject(FormBuilder);
  private readonly admin = inject(AdminService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  readonly loading = signal(true);
  readonly submitting = signal(false);
  readonly failed = signal(false);
  readonly athleteName = signal('');
  readonly clubName = signal<string | null>(null);

  readonly statusLabels = ATHLETE_STATUS_LABELS;
  readonly statuses: (keyof typeof ATHLETE_STATUS_LABELS)[] = ['ACTIVE', 'PAUSED', 'ARCHIVED'];

  readonly form = this.fb.group({
    firstName: ['', [Validators.required]],
    lastName: ['', [Validators.required]],
    email: ['', [Validators.email]],
    // Absente du formulaire, elle partait à null à chaque enregistrement : le serveur écrit ce
    // qu'il reçoit, et une date de naissance perdue ne se retrouve pas.
    birthDate: [''],
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
        this.athleteName.set(`${a.firstName} ${a.lastName}`);
        // Le profil rend ses clubs (principal + additionnels) ; le premier suffit à situer
        // l'athlète en haut de l'écran.
        this.clubName.set(a.clubs?.[0]?.name ?? null);
        this.loading.set(false);
      },
      error: () => {
        this.failed.set(true);
        this.loading.set(false);
      },
    });
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.toast.warning('Corrige les champs en rouge avant d’enregistrer.');
      return;
    }
    this.submitting.set(true);
    const v = this.form.getRawValue();
    const blank = (s: string | null) => (s && s.trim() ? s.trim() : null);
    const payload: AthleteRequest = {
      firstName: v.firstName!,
      lastName: v.lastName!,
      email: blank(v.email),
      birthDate: blank(v.birthDate),
      sex: (blank(v.sex) as AthleteRequest['sex']) ?? null,
      level: (blank(v.level) as AthleteRequest['level']) ?? null,
      status: (blank(v.status) as AthleteRequest['status']) ?? null,
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
