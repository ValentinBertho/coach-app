import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { Athlete } from '../../core/models/athlete.model';
import { AthleteService } from '../../core/services/athlete.service';
import { ToastService } from '../../core/services/toast.service';

@Component({
  selector: 'app-athlete-detail',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  templateUrl: './athlete-detail.component.html',
  styleUrl: './athletes.scss',
})
export class AthleteDetailComponent implements OnInit {
  readonly id = input.required<string>();

  private readonly athleteService = inject(AthleteService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  readonly athlete = signal<Athlete | null>(null);
  readonly loading = signal(true);
  readonly inviteUrl = signal<string | null>(null);

  ngOnInit(): void {
    this.athleteService.get(this.id()).subscribe({
      next: (a) => {
        this.athlete.set(a);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.router.navigate(['/app/athletes']);
      },
    });
  }

  invite(): void {
    this.athleteService.invite(this.id()).subscribe({
      next: (res) => {
        this.inviteUrl.set(res.inviteUrl);
        this.toast.success("Lien d'invitation généré ✅");
      },
    });
  }

  copyInvite(): void {
    const url = this.inviteUrl();
    if (url) {
      navigator.clipboard?.writeText(url);
      this.toast.info('Lien copié dans le presse-papier.');
    }
  }

  archive(): void {
    this.athleteService.archive(this.id()).subscribe({
      next: () => {
        this.toast.success('Athlète archivé.');
        this.router.navigate(['/app/athletes']);
      },
    });
  }
}
