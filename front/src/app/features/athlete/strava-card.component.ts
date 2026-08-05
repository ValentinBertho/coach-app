import { ChangeDetectionStrategy, Component, OnInit, computed, inject, output, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AthletePortalService } from '../../core/services/athlete-portal.service';
import { ToastService } from '../../core/services/toast.service';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { StravaStatus } from '../../core/models/strava.model';

/** Report de l'invitation à connecter Strava : deux semaines de silence, pas un refus définitif. */
const SNOOZE_KEY = 'darilab.strava.snoozedUntil';
const SNOOZE_MS = 14 * 24 * 3600 * 1000;

/**
 * Carte Strava de l'écran d'accueil athlète.
 *
 * <p>Deux états, un seul objectif — que les sorties réelles arrivent :</p>
 * <ul>
 *   <li><b>Non connecté</b> : l'invitation à connecter sa montre est ici, sur l'écran ouvert
 *       chaque matin, et non enterrée dans Profil → Synchronisation. Un athlète qui ne synchronise
 *       pas est un athlète que son coach suit à l'aveugle.</li>
 *   <li><b>Connecté</b> : l'heure du dernier import et un bouton pour en déclencher un tout de
 *       suite — la synchro automatique passe toutes les heures, ce qui est parfait au quotidien
 *       et trop lent juste après une sortie, quand on veut noter son ressenti.</li>
 * </ul>
 */
@Component({
  selector: 'app-strava-card',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    @if (visible()) {
      @if (status(); as st) {
        @if (st.connected) {
          <section class="sv sv--on card">
            <span class="sv-logo sv-logo--on"><app-icon name="watch" [size]="18" /></span>
            <div class="sv-txt">
              <strong>Strava connecté</strong>
              <span class="field-hint">{{ lastSyncLabel() }}</span>
            </div>
            <button type="button" class="btn btn-ghost btn-sm sv-sync" (click)="syncNow()" [disabled]="busy()"
                    [attr.aria-label]="'Synchroniser mes activités Strava'">
              <app-icon name="refresh-cw" [size]="15" [class.spin]="busy()" />
              {{ busy() ? 'Synchro…' : 'Synchroniser' }}
            </button>
          </section>
        } @else {
          <section class="sv sv--off card">
            <div class="sv-head">
              <span class="sv-logo"><app-icon name="watch" [size]="18" /></span>
              <div class="sv-txt">
                <strong>Connecte ta montre</strong>
                <span class="field-hint">Tes sorties Strava arrivent toutes seules, et ton coach voit ce que tu as vraiment fait.</span>
              </div>
            </div>
            <div class="sv-actions">
              <button type="button" class="btn btn-accent btn-sm" (click)="connect()" [disabled]="busy()">
                <app-icon name="watch" [size]="15" /> Connecter Strava
              </button>
              <button type="button" class="btn btn-ghost btn-sm" (click)="snooze()">Plus tard</button>
            </div>
          </section>
        }
      }
    }
  `,
  styles: [`
    .sv { display: flex; gap: var(--sp-3); }
    .sv--on { align-items: center; }
    .sv--off { flex-direction: column; }
    .sv-head { display: flex; align-items: flex-start; gap: var(--sp-3); }
    .sv-logo {
      display: inline-flex; align-items: center; justify-content: center; flex-shrink: 0;
      width: 36px; height: 36px; border-radius: var(--radius-full);
      background: var(--primary-wash); color: var(--primary);
    }
    .sv-logo--on { background: var(--success-bg, var(--primary-wash)); color: var(--success-text, var(--primary)); }
    .sv-txt { display: flex; flex-direction: column; gap: 2px; min-width: 0; flex: 1; }
    .sv-txt strong { color: var(--ink); }
    .sv-actions { display: flex; gap: var(--sp-2); flex-wrap: wrap; }
    .sv-sync { flex-shrink: 0; }

    .spin { animation: sv-spin 900ms linear infinite; }
    @keyframes sv-spin { to { transform: rotate(360deg); } }
    @media (prefers-reduced-motion: reduce) { .spin { animation: none; } }
  `],
})
export class StravaCardComponent implements OnInit {
  private readonly portal = inject(AthletePortalService);
  private readonly toast = inject(ToastService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  /** Émis après un import ayant ramené au moins une activité : l'écran appelant se rafraîchit. */
  readonly synced = output<number>();

  readonly status = signal<StravaStatus | null>(null);
  readonly busy = signal(false);
  private readonly snoozed = signal(this.isSnoozed());

  /**
   * Rien à montrer si l'intégration n'est pas configurée sur le serveur, et rien non plus si
   * l'athlète a repoussé l'invitation — mais une connexion active reste toujours visible.
   */
  readonly visible = computed(() => {
    const st = this.status();
    if (!st?.configured) return false;
    return st.connected || !this.snoozed();
  });

  ngOnInit(): void {
    // Retour d'autorisation Strava (`?strava=connected`) : on ramène les sorties tout de suite.
    // Attendre la synchro automatique, c'est un écran d'accueil vide pendant une heure juste
    // après avoir connecté sa montre — le moment précis où l'athlète vérifie que ça marche.
    const justConnected = this.route.snapshot.queryParamMap.get('strava') === 'connected';
    if (justConnected) {
      this.router.navigate([], {
        relativeTo: this.route, queryParams: { strava: null },
        queryParamsHandling: 'merge', replaceUrl: true,
      });
    }
    this.load(justConnected);
  }

  private load(thenSync = false): void {
    this.portal.stravaStatus().subscribe({
      next: (s) => {
        this.status.set(s);
        if (thenSync && s.configured && s.connected) this.syncNow();
      },
      error: () => this.status.set(null),
    });
  }

  connect(): void {
    this.busy.set(true);
    this.portal.stravaAuthorizeUrl().subscribe({
      next: (r) => { window.location.href = r.url; },
      error: () => { this.busy.set(false); this.toast.error('Strava indisponible pour le moment.'); },
    });
  }

  syncNow(): void {
    this.busy.set(true);
    this.portal.stravaImport().subscribe({
      next: (r) => {
        this.busy.set(false);
        this.toast.success(
          r.imported > 0
            ? `${r.imported} sortie${r.imported > 1 ? 's' : ''} importée${r.imported > 1 ? 's' : ''}`
            : 'Tu es à jour — aucune nouvelle sortie.',
        );
        this.load();
        if (r.imported > 0) this.synced.emit(r.imported);
      },
      error: () => { this.busy.set(false); this.toast.error('Synchronisation impossible pour le moment.'); },
    });
  }

  snooze(): void {
    try {
      localStorage.setItem(SNOOZE_KEY, String(Date.now() + SNOOZE_MS));
    } catch {
      // Stockage indisponible : la carte reviendra au prochain lancement, ce n'est pas grave.
    }
    this.snoozed.set(true);
  }

  /** « Synchro auto toutes les heures · dernier import il y a 2 h ». */
  lastSyncLabel(): string {
    const epoch = this.status()?.lastImportEpoch;
    if (!epoch) return 'Synchro automatique toutes les heures · aucun import pour l’instant';

    const minutes = Math.max(0, Math.round((Date.now() - epoch * 1000) / 60000));
    let ago: string;
    if (minutes < 2) ago = "à l'instant";
    else if (minutes < 60) ago = `il y a ${minutes} min`;
    else if (minutes < 60 * 24) ago = `il y a ${Math.round(minutes / 60)} h`;
    else ago = `il y a ${Math.round(minutes / (60 * 24))} j`;
    return `Synchro automatique toutes les heures · dernier import ${ago}`;
  }

  private isSnoozed(): boolean {
    try {
      const until = Number(localStorage.getItem(SNOOZE_KEY));
      return Number.isFinite(until) && until > Date.now();
    } catch {
      return false;
    }
  }
}
