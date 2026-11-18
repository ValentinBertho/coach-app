import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  inject,
  output,
  signal,
} from '@angular/core';
import { concatMap } from 'rxjs';
import { AthletePortalService } from '../../core/services/athlete-portal.service';
import { ToastService } from '../../core/services/toast.service';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { GarminStatus } from '../../core/models/garmin.model';
import { CorosStatus } from '../../core/models/coros.model';

/**
 * Cartes Garmin et COROS de l'écran d'accueil athlète, sur le modèle de la carte Strava.
 *
 * <p>Affichées seulement quand la montre est reliée : l'invitation à connecter reste dans
 * Profil → Synchronisation. Le bouton fait ce que la passe automatique ferait plus tard :</p>
 * <ul>
 *   <li><b>Garmin</b> : renvoie les séances à venir dans le calendrier Garmin. Les sorties, elles,
 *       arrivent d'elles-mêmes (Garmin nous les pousse), il n'y a rien à aller chercher.</li>
 *   <li><b>COROS</b> : renvoie les séances, puis va chercher les nouvelles sorties (COROS ne
 *       prévient pas, la passe automatique a lieu toutes les heures).</li>
 * </ul>
 */
@Component({
  selector: 'app-watch-sync-cards',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    @if (garmin(); as g) {
      @if (g.configured && g.connected) {
        <section class="ws card" data-testid="today-garmin-card">
          <span class="ws-logo"><app-icon name="watch" [size]="18" /></span>
          <div class="ws-txt">
            <strong>Garmin connecté</strong>
            <span class="field-hint">
              Séances envoyées sur ta montre automatiquement ·
              @if (g.workoutsOnGarmin > 0) {
                {{ g.workoutsOnGarmin }}
                {{
                  g.workoutsOnGarmin > 1 ? 'séances à venir' : 'séance à venir'
                }}
                dans Garmin
              } @else {
                aucune séance à venir dans Garmin
              }
              @if (g.workoutsInError > 0) {
                · {{ g.workoutsInError }} en attente
              }
            </span>
          </div>
          <button
            type="button"
            class="btn btn-ghost btn-sm ws-sync"
            (click)="syncGarmin()"
            [disabled]="busy() === 'garmin'"
            aria-label="Envoyer mes séances sur Garmin"
          >
            <app-icon
              name="refresh-cw"
              [size]="15"
              [class.spin]="busy() === 'garmin'"
            />
            {{ busy() === 'garmin' ? 'Synchro…' : 'Synchroniser' }}
          </button>
        </section>
      }
    }
    @if (coros(); as c) {
      @if (c.configured && c.connected) {
        <section class="ws card" data-testid="today-coros-card">
          <span class="ws-logo"><app-icon name="watch" [size]="18" /></span>
          <div class="ws-txt">
            <strong>COROS connecté</strong>
            <span class="field-hint">
              Synchro automatique toutes les heures ·
              @if (c.workoutsOnCoros > 0) {
                {{ c.workoutsOnCoros }}
                {{
                  c.workoutsOnCoros > 1 ? 'séances à venir' : 'séance à venir'
                }}
                dans COROS
              } @else {
                aucune séance à venir dans COROS
              }
              @if (c.workoutsInError > 0) {
                · {{ c.workoutsInError }} en attente
              }
            </span>
          </div>
          <button
            type="button"
            class="btn btn-ghost btn-sm ws-sync"
            (click)="syncCoros()"
            [disabled]="busy() === 'coros'"
            aria-label="Synchroniser ma montre COROS"
          >
            <app-icon
              name="refresh-cw"
              [size]="15"
              [class.spin]="busy() === 'coros'"
            />
            {{ busy() === 'coros' ? 'Synchro…' : 'Synchroniser' }}
          </button>
        </section>
      }
    }
  `,
  styles: [
    `
      :host {
        display: contents;
      }
      .ws {
        display: flex;
        align-items: center;
        gap: var(--sp-3);
      }
      .ws-logo {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        flex-shrink: 0;
        width: 36px;
        height: 36px;
        border-radius: var(--radius-full);
        background: var(--success-bg, var(--primary-wash));
        color: var(--success-text, var(--primary));
      }
      .ws-txt {
        display: flex;
        flex-direction: column;
        gap: 2px;
        min-width: 0;
        flex: 1;
      }
      .ws-txt strong {
        color: var(--ink);
      }
      .ws-sync {
        flex-shrink: 0;
      }
      .spin {
        animation: ws-spin 900ms linear infinite;
      }
      @keyframes ws-spin {
        to {
          transform: rotate(360deg);
        }
      }
      @media (prefers-reduced-motion: reduce) {
        .spin {
          animation: none;
        }
      }
    `,
  ],
})
export class WatchSyncCardsComponent implements OnInit {
  private readonly portal = inject(AthletePortalService);
  private readonly toast = inject(ToastService);

  /** Émis quand une synchro COROS a ramené au moins une sortie : l'écran se rafraîchit. */
  readonly synced = output<number>();
  /** Émis au chargement : une montre Garmin ou COROS est-elle reliée ? */
  readonly connected = output<boolean>();

  readonly garmin = signal<GarminStatus | null>(null);
  readonly coros = signal<CorosStatus | null>(null);
  readonly busy = signal<'garmin' | 'coros' | null>(null);

  ngOnInit(): void {
    this.portal.garminStatus().subscribe({
      next: (g) => {
        this.garmin.set(g);
        this.emitConnected();
      },
      error: () => this.garmin.set(null),
    });
    this.portal.corosStatus().subscribe({
      next: (c) => {
        this.coros.set(c);
        this.emitConnected();
      },
      error: () => this.coros.set(null),
    });
  }

  private emitConnected(): void {
    const g = this.garmin();
    const c = this.coros();
    this.connected.emit(
      !!(g?.configured && g.connected) || !!(c?.configured && c.connected),
    );
  }

  syncGarmin(): void {
    this.busy.set('garmin');
    this.portal.garminSyncWorkouts().subscribe({
      next: (g) => {
        this.garmin.set(g);
        this.busy.set(null);
        this.toast.success(
          g.workoutsInError > 0
            ? 'Envoi terminé, avec des séances en attente.'
            : 'Tes séances sont à jour dans Garmin Connect.',
        );
      },
      error: () => {
        this.busy.set(null);
        this.toast.error('Synchronisation Garmin impossible pour le moment.');
      },
    });
  }

  syncCoros(): void {
    const before = this.coros()?.activitiesImported ?? 0;
    this.busy.set('coros');
    this.portal
      .corosSyncWorkouts()
      .pipe(concatMap(() => this.portal.corosSyncActivities()))
      .subscribe({
        next: (c) => {
          this.coros.set(c);
          this.busy.set(null);
          const added = Math.max(0, c.activitiesImported - before);
          this.toast.success(
            added > 0
              ? `${added} ${added > 1 ? 'sorties importées' : 'sortie importée'} depuis COROS`
              : 'Tu es à jour : séances envoyées, aucune nouvelle sortie.',
          );
          if (added > 0) this.synced.emit(added);
        },
        error: () => {
          this.busy.set(null);
          this.toast.error('Synchronisation COROS impossible pour le moment.');
        },
      });
  }
}
