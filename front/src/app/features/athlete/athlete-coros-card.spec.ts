import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { importProvidersFrom } from '@angular/core';
import { LucideAngularModule, Watch, RefreshCw } from 'lucide-angular';
import { AthleteSyncComponent } from './athlete-sync.component';
import { CorosStatus } from '../../core/models/coros.model';

/**
 * La carte COROS de Synchronisation : « Bientôt » tant que l'intégration n'est pas ouverte sur le
 * serveur, puis connexion et déconnexion du compte.
 */
describe('carte COROS (côté athlète)', () => {
  let http: HttpTestingController;

  const coros = (over: Partial<CorosStatus> = {}): CorosStatus => ({
    configured: true, connected: false, providerUserId: null, region: null, ...over,
  });

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
        importProvidersFrom(LucideAngularModule.pick({ Watch, RefreshCw })),
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function render(c: CorosStatus | 'error'): HTMLElement {
    const fixture = TestBed.createComponent(AthleteSyncComponent);
    fixture.detectChanges();
    http.expectOne((r) => r.url.endsWith('/me/strava')).flush({
      configured: true, connected: false, providerAthleteId: null, lastImportEpoch: null,
      renameOnStrava: false, canRenameOnStrava: false,
    });
    http.expectOne((r) => r.url.endsWith('/me/garmin')).flush({
      configured: false, connected: false, providerUserId: null, permissions: [],
      canSendWorkouts: false, canImportActivities: false, workoutsOnGarmin: 0, workoutsInError: 0,
    });
    const req = http.expectOne((r) => r.url.endsWith('/me/coros'));
    if (c === 'error') {
      req.flush('boom', { status: 500, statusText: 'Server Error' });
    } else {
      req.flush(c);
    }
    http.expectOne((r) => r.url.endsWith('/me/activity-exclusions')).flush([]);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('reste « Bientôt » tant que l’intégration n’est pas ouverte', () => {
    const el = render(coros({ configured: false }));
    expect(el.querySelector('[data-testid="coros-soon"]')).not.toBeNull();
    expect(el.textContent).not.toContain('Connecter COROS');
  });

  it('reste « Bientôt » si le serveur ne répond pas', () => {
    const el = render('error');
    expect(el.querySelector('[data-testid="coros-soon"]')).not.toBeNull();
  });

  it('propose la connexion quand le compte n’est pas relié', () => {
    const el = render(coros());
    expect(el.querySelector('[data-testid="coros-card"]')).not.toBeNull();
    expect(el.textContent).toContain('Connecter COROS');
  });

  it('affiche le compte relié et propose la déconnexion', () => {
    const el = render(coros({ connected: true, providerUserId: 'c-1', region: 'EU' }));
    expect(el.querySelector('[data-testid="coros-summary"]')?.textContent).toContain('Compte relié');
    expect(el.textContent).toContain('Déconnecter COROS');
  });
});
