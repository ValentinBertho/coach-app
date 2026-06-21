import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { Subject, takeUntil } from 'rxjs';
import { Ping } from '../../core/models/ping.model';
import { PingService } from '../../core/services/ping.service';
import { ToastService } from '../../core/services/toast.service';

type PingState = 'loading' | 'ok' | 'error';

/**
 * Page d'accueil minimale : appelle GET /api/public/ping et affiche le statut de l'API
 * (preuve de bout-en-bout que le front communique avec le back).
 */
@Component({
  selector: 'app-home',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss',
})
export class HomeComponent implements OnInit, OnDestroy {
  private readonly pingService = inject(PingService);
  private readonly toast = inject(ToastService);
  private readonly destroy$ = new Subject<void>();

  readonly state = signal<PingState>('loading');
  readonly ping = signal<Ping | null>(null);

  ngOnInit(): void {
    this.checkApi();
  }

  checkApi(): void {
    this.state.set('loading');
    this.pingService
      .ping()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (ping) => {
          this.ping.set(ping);
          this.state.set('ok');
          this.toast.success('API connectée ✅');
        },
        error: () => {
          this.state.set('error');
          // le toast d'erreur est déjà émis par l'intercepteur global
        },
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
