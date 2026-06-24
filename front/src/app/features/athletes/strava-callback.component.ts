import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AthleteService } from '../../core/services/athlete.service';
import { ToastService } from '../../core/services/toast.service';

/**
 * Callback OAuth Strava : reçoit ?code & ?state (= athleteId) après autorisation,
 * finalise la connexion puis renvoie vers la fiche athlète.
 */
@Component({
  selector: 'app-strava-callback',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="card" style="max-width:520px;margin:48px auto;text-align:center">
      <h2>Connexion Strava</h2>
      <p class="field-hint">{{ message() }}</p>
    </div>
  `,
})
export class StravaCallbackComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly athletes = inject(AthleteService);
  private readonly toast = inject(ToastService);

  readonly message = signal('Finalisation de la connexion…');

  ngOnInit(): void {
    const code = this.route.snapshot.queryParamMap.get('code');
    const athleteId = this.route.snapshot.queryParamMap.get('state');
    const error = this.route.snapshot.queryParamMap.get('error');

    if (error || !code || !athleteId) {
      this.message.set('Connexion annulée ou invalide.');
      this.toast.error('Connexion Strava annulée.');
      return;
    }
    this.athletes.stravaConnect(athleteId, code).subscribe({
      next: () => {
        this.toast.success('Compte Strava connecté ✅');
        this.router.navigate(['/app/athletes', athleteId]);
      },
      error: () => {
        this.message.set('Échec de la connexion Strava.');
        this.toast.error('Échec de la connexion Strava.');
      },
    });
  }
}
