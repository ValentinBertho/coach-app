import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { importProvidersFrom } from '@angular/core';
import { LucideAngularModule, Watch, RefreshCw } from 'lucide-angular';
import { WatchSyncCardsComponent } from './watch-sync-cards.component';
import { GarminStatus } from '../../core/models/garmin.model';
import { CorosStatus } from '../../core/models/coros.model';

/**
 * Cartes Garmin et COROS de l'écran d'accueil : visibles seulement quand la montre est reliée,
 * avec le même bouton « Synchroniser » que Strava.
 */
describe('cartes Garmin et COROS de l’accueil athlète', () => {
  let http: HttpTestingController;

  const garmin = (over: Partial<GarminStatus> = {}): GarminStatus => ({
    configured: true, connected: true, providerUserId: 'g-1',
    permissions: ['WORKOUT_IMPORT', 'ACTIVITY_EXPORT'],
    canSendWorkouts: true, canImportActivities: true, workoutsOnGarmin: 3, workoutsInError: 0, ...over,
  });
  const coros = (over: Partial<CorosStatus> = {}): CorosStatus => ({
    configured: true, connected: true, providerUserId: 'c-1', region: 'EU',
    workoutsOnCoros: 2, workoutsInError: 0, activitiesImported: 5, ...over,
  });

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(), provideHttpClientTesting(),
        importProvidersFrom(LucideAngularModule.pick({ Watch, RefreshCw })),
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function render(g: GarminStatus, c: CorosStatus) {
    const fixture = TestBed.createComponent(WatchSyncCardsComponent);
    const connected: boolean[] = [];
    fixture.componentInstance.connected.subscribe((v) => connected.push(v));
    fixture.detectChanges();
    http.expectOne((r) => r.url.endsWith('/me/garmin')).flush(g);
    http.expectOne((r) => r.url.endsWith('/me/coros')).flush(c);
    fixture.detectChanges();
    return { fixture, el: fixture.nativeElement as HTMLElement, connected };
  }

  it('n’affiche rien quand aucune montre n’est reliée', () => {
    const { el, connected } = render(garmin({ connected: false }), coros({ connected: false }));
    expect(el.querySelector('[data-testid="today-garmin-card"]')).toBeNull();
    expect(el.querySelector('[data-testid="today-coros-card"]')).toBeNull();
    expect(connected.at(-1)).toBeFalse();
  });

  it('affiche Garmin relié et renvoie les séances au clic', () => {
    const { fixture, el, connected } = render(garmin(), coros({ connected: false }));
    const card = el.querySelector('[data-testid="today-garmin-card"]')!;
    expect(card.textContent).toContain('Garmin connecté');
    expect(card.textContent).toContain('3 séances à venir');
    expect(connected.at(-1)).toBeTrue();

    (card.querySelector('button') as HTMLButtonElement).click();
    http.expectOne((r) => r.url.endsWith('/me/garmin/sync-workouts')).flush(garmin({ workoutsOnGarmin: 4 }));
    fixture.detectChanges();
    expect(card.textContent).toContain('4 séances à venir');
  });

  it('synchronise COROS : séances puis sorties, et signale les nouvelles sorties', () => {
    const { fixture, el } = render(garmin({ connected: false }), coros());
    const synced: number[] = [];
    fixture.componentInstance.synced.subscribe((n) => synced.push(n));

    (el.querySelector('[data-testid="today-coros-card"] button') as HTMLButtonElement).click();
    http.expectOne((r) => r.url.endsWith('/me/coros/sync-workouts')).flush(coros());
    http.expectOne((r) => r.url.endsWith('/me/coros/sync-activities')).flush(coros({ activitiesImported: 7 }));
    expect(synced).toEqual([2]);
  });
});
