import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Ping } from '../../core/models/ping.model';
import { PingService } from '../../core/services/ping.service';
import { IconComponent } from '../../shared/components/icon/icon.component';

type PingState = 'loading' | 'ok' | 'error';

/**
 * État de l'API (GET /api/public/ping) — banc de vérification interne, route `/dev/api`.
 *
 * Cet indicateur vivait dans le pied de page public : un prospect n'a pas à savoir qu'une
 * API existe, et « API injoignable » sur la page d'accueil tue la crédibilité au moment
 * précis où on essaie de l'établir. La sonde reste utile, elle change juste de public.
 */
@Component({
  selector: 'app-api-status',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, IconComponent],
  template: `
    <div class="container-narrow" style="padding-block: var(--sp-8);">
      <header class="page-header">
        <div>
          <h1 class="display-sm">État de l'API</h1>
          <p class="subtitle">Vérification de bout en bout que le front atteint le back.</p>
        </div>
      </header>

      <div class="card api-card">
        @switch (state()) {
          @case ('loading') {
            <p class="api-line"><span class="dot dot-load"></span> Connexion à l'API…</p>
          }
          @case ('ok') {
            <p class="api-line api-line--ok">
              <app-icon name="check" [size]="16" /> API en ligne · v{{ ping()?.version }}
            </p>
          }
          @case ('error') {
            <p class="api-line api-line--err">
              <app-icon name="alert-triangle" [size]="16" /> API injoignable
            </p>
          }
        }
        <button type="button" class="btn btn-ghost btn-sm" (click)="checkApi()">
          <app-icon name="refresh-cw" [size]="15" /> Revérifier
        </button>
      </div>

      <p class="field-hint"><a routerLink="/dev/ui-kit">← UI Kit</a></p>
    </div>
  `,
  styles: [`
    .api-card { display: flex; align-items: center; justify-content: space-between; gap: var(--sp-4); flex-wrap: wrap; }
    .api-line { display: inline-flex; align-items: center; gap: var(--sp-2); margin: 0; font-weight: 700; color: var(--ink-2); }
    .api-line--ok { color: var(--success); }
    .api-line--err { color: var(--danger); }
    .dot { width: 8px; height: 8px; border-radius: var(--radius-full); background: var(--ink-4); }
  `],
})
export class ApiStatusComponent implements OnInit {
  private readonly pingService = inject(PingService);

  readonly state = signal<PingState>('loading');
  readonly ping = signal<Ping | null>(null);

  ngOnInit(): void { this.checkApi(); }

  checkApi(): void {
    this.state.set('loading');
    this.pingService.ping().subscribe({
      next: (ping) => { this.ping.set(ping); this.state.set('ok'); },
      // Le toast d'erreur est déjà émis par l'intercepteur global.
      error: () => this.state.set('error'),
    });
  }
}
