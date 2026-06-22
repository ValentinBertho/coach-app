import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Subject, debounceTime } from 'rxjs';
import { AthleteSummary } from '../../core/models/athlete.model';
import { AthleteService } from '../../core/services/athlete.service';
import { PaginatorComponent } from '../../shared/components/paginator/paginator.component';

type ListState = 'loading' | 'ready' | 'error';

@Component({
  selector: 'app-athlete-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, FormsModule, PaginatorComponent],
  templateUrl: './athlete-list.component.html',
  styleUrl: './athletes.scss',
})
export class AthleteListComponent implements OnInit {
  private readonly athleteService = inject(AthleteService);
  private readonly searchInput$ = new Subject<void>();

  readonly state = signal<ListState>('loading');
  readonly athletes = signal<AthleteSummary[]>([]);
  readonly page = signal(0);
  readonly totalPages = signal(1);
  search = '';

  ngOnInit(): void {
    this.searchInput$.pipe(debounceTime(300)).subscribe(() => {
      this.page.set(0);
      this.load();
    });
    this.load();
  }

  onSearchChange(): void {
    this.searchInput$.next();
  }

  goToPage(p: number): void {
    this.page.set(p);
    this.load();
  }

  load(): void {
    this.state.set('loading');
    this.athleteService.list({ q: this.search || undefined, page: this.page() }).subscribe({
      next: (page) => {
        this.athletes.set(page.content);
        this.totalPages.set(page.totalPages);
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
