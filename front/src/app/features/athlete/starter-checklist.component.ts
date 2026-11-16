import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { AthletePortalService } from '../../core/services/athlete-portal.service';
import { CoachingRequestService } from '../../core/services/coaching-request.service';
import { IconComponent } from '../../shared/components/icon/icon.component';

interface Step {
  key: string;
  label: string;
  hint: string;
  link: string;
  done: boolean;
}

/**
 * « Pour bien démarrer » : les quatre gestes qui rendent l'application utile à un athlète qui
 * arrive sans coach.
 *
 * <p>Chaque étape se coche d'elle-même, d'après ce que le serveur sait — une montre connectée, un
 * objectif de course, un profil rempli, une demande envoyée. Rien n'est stocké dans le
 * navigateur : la liste dit la même chose sur le téléphone et sur l'ordinateur, et disparaît
 * d'elle-même une fois tout fait.</p>
 */
@Component({
  selector: 'app-starter-checklist',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, IconComponent],
  template: `
    @if (steps(); as list) {
      @if (remaining() > 0) {
        <section class="card sc" aria-labelledby="sc-title">
          <div class="sc__head">
            <h2 id="sc-title">Pour bien démarrer</h2>
            <span class="sc__count">{{ list.length - remaining() }} / {{ list.length }}</span>
          </div>
          <ol class="sc__list">
            @for (s of list; track s.key) {
              <li [class.done]="s.done">
                <a [routerLink]="s.link">
                  <span class="sc__tick" aria-hidden="true">
                    @if (s.done) { <app-icon name="check" [size]="14" /> }
                  </span>
                  <span class="sc__txt">
                    <strong>{{ s.label }}</strong>
                    <small>{{ s.hint }}</small>
                  </span>
                  <app-icon name="chevron-right" [size]="16" />
                </a>
              </li>
            }
          </ol>
        </section>
      }
    }
  `,
  styles: [`
    .sc { padding: var(--sp-4); margin-bottom: var(--sp-4); }
    .sc__head { display: flex; justify-content: space-between; align-items: baseline; margin-bottom: var(--sp-2); }
    .sc__head h2 { margin: 0; font-size: var(--text-lg); }
    .sc__count { color: var(--ink-3); font-size: var(--text-sm); font-weight: 600; }
    .sc__list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; }
    .sc__list a {
      display: flex; align-items: center; gap: var(--sp-3); padding: var(--sp-3) 0;
      text-decoration: none; color: var(--ink); border-top: 1px solid var(--hairline);
    }
    .sc__tick {
      flex: 0 0 22px; height: 22px; border-radius: 50%; border: 2px solid var(--hairline);
      display: inline-flex; align-items: center; justify-content: center; color: #fff;
    }
    li.done .sc__tick { background: var(--primary); border-color: var(--primary); }
    li.done strong { color: var(--ink-3); text-decoration: line-through; }
    .sc__txt { flex: 1; display: flex; flex-direction: column; min-width: 0; }
    .sc__txt small { color: var(--ink-3); }
  `],
})
export class StarterChecklistComponent implements OnInit {
  private readonly portal = inject(AthletePortalService);
  private readonly coaching = inject(CoachingRequestService);

  readonly steps = signal<Step[] | null>(null);
  readonly remaining = computed(() => (this.steps() ?? []).filter((s) => !s.done).length);

  ngOnInit(): void {
    const connected = <T extends { connected: boolean }>(o: import('rxjs').Observable<T>) =>
      o.pipe(map((s) => s.connected), catchError(() => of(false)));
    forkJoin({
      strava: connected(this.portal.stravaStatus()),
      garmin: connected(this.portal.garminStatus()),
      coros: connected(this.portal.corosStatus()),
      races: this.portal.races().pipe(map((r) => r.length > 0), catchError(() => of(false))),
      account: this.coaching.account().pipe(catchError(() => of(null))),
      requests: this.coaching.mine().pipe(map((r) => r.length > 0), catchError(() => of(false))),
    }).subscribe((r) => {
      this.steps.set([
        {
          key: 'watch', label: 'Connecter ma montre', link: '/athlete/sync',
          hint: 'Strava, Garmin ou COROS : tes sorties arrivent seules dans le calendrier.',
          done: r.strava || r.garmin || r.coros,
        },
        {
          key: 'race', label: 'Fixer un objectif de course', link: '/athlete/races',
          hint: 'La course visée et sa date donnent un cap à chaque semaine.',
          done: r.races,
        },
        {
          key: 'profile', label: 'Compléter mon profil', link: '/athlete/profile',
          hint: 'Discipline et niveau : ce qu’un coach lit en premier.',
          done: !!r.account?.discipline && !!r.account?.level,
        },
        {
          key: 'coach', label: 'Trouver un coach', link: '/coachs',
          hint: 'Facultatif : tu peux aussi t’entraîner seul.',
          done: r.requests,
        },
      ]);
    });
  }
}
