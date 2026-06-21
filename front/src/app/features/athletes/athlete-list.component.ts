import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AthleteSummary } from '../../core/models/athlete.model';
import { AthleteService } from '../../core/services/athlete.service';

type ListState = 'loading' | 'ready' | 'error';

@Component({
  selector: 'app-athlete-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, FormsModule],
  templateUrl: './athlete-list.component.html',
  styleUrl: './athletes.scss',
})
export class AthleteListComponent implements OnInit {
  private readonly athleteService = inject(AthleteService);

  readonly state = signal<ListState>('loading');
  readonly athletes = signal<AthleteSummary[]>([]);
  search = '';

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.state.set('loading');
    this.athleteService.list({ q: this.search || undefined }).subscribe({
      next: (page) => {
        this.athletes.set(page.content);
        this.state.set('ready');
      },
      error: () => this.state.set('error'),
    });
  }

  statusLabel(status: string): string {
    return { ACTIVE: 'Actif', PAUSED: 'En pause', ARCHIVED: 'Archivé' }[status] ?? status;
  }

  statusClass(status: string): string {
    return (
      { ACTIVE: 'badge-success', PAUSED: 'badge-warning', ARCHIVED: 'badge-neutral' }[status] ??
      'badge-neutral'
    );
  }
}
