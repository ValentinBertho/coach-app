import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { importProvidersFrom } from '@angular/core';
import { LucideAngularModule, Watch, RefreshCw } from 'lucide-angular';
import { AthleteSyncComponent } from './athlete-sync.component';
import { AthletePortalService } from '../../core/services/athlete-portal.service';
import { StravaStatus } from '../../core/models/strava.model';

/**
 * Le consentement à écrire dans le compte Strava de l'athlète.
 *
 * <p>Ce que ces tests tiennent : que la case dise la vérité. Elle autorise une écriture sur un
 * compte qui ne nous appartient pas, dans un fil que des tiers lisent, et sans nom d'origine
 * conservé — un interrupteur qui afficherait un état qu'il n'a pas obtenu du serveur serait donc
 * pire que pas d'interrupteur du tout.</p>
 */
describe('renommage sur Strava (côté athlète)', () => {
  let http: HttpTestingController;

  const status = (over: Partial<StravaStatus> = {}): StravaStatus => ({
    configured: true, connected: true, providerAthleteId: '99', lastImportEpoch: null,
    renameOnStrava: false, canRenameOnStrava: true, ...over,
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

  /** L'intention doit atteindre le serveur : une case cochée dans le vide autoriserait une écriture qui n'a jamais été enregistrée. */
  it('porte le consentement jusqu’à la requête', () => {
    const portal = TestBed.inject(AthletePortalService);
    portal.stravaSetRenameOnStrava(true).subscribe();
    const req = http.expectOne((r) => r.url.endsWith('/me/strava/rename-on-strava'));
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ enabled: true });
    req.flush(status({ renameOnStrava: true }));
  });

  /** La section n'a d'objet que sur un compte connecté : sans connexion, il n'y a rien à renommer. */
  it('ne montre la section que si Strava est connecté', () => {
    const fixture = TestBed.createComponent(AthleteSyncComponent);
    fixture.detectChanges();
    flushLoad(status({ connected: false }));
    fixture.detectChanges();

    expect(text(fixture)).not.toContain('Renommer aussi sur Strava');
  });

  /**
   * Le cas qui compte : l'athlète a dit oui, mais Strava ne nous a rien accordé. Décocher à sa
   * place effacerait sa décision ; ne rien dire lui laisserait croire que ses sorties sont
   * renommées là-bas. L'écran doit donc afficher les deux, et dire quoi faire.
   */
  it('dit que l’autorisation Strava manque, sans effacer le consentement', () => {
    const fixture = TestBed.createComponent(AthleteSyncComponent);
    fixture.detectChanges();
    flushLoad(status({ renameOnStrava: true, canRenameOnStrava: false }));
    fixture.detectChanges();

    const checkbox: HTMLInputElement = fixture.nativeElement
      .querySelector('input[aria-label="Renommer aussi mes sorties sur Strava"]');
    expect(checkbox.checked).withContext('le consentement de l’athlète reste affiché').toBeTrue();
    expect(text(fixture)).toContain("Strava ne nous a pas donné l'autorisation d'écrire");
  });

  /** L'état affiché est celui que le serveur renvoie, jamais celui que le clic a supposé. */
  it('affiche l’état renvoyé par le serveur après le clic', () => {
    const fixture = TestBed.createComponent(AthleteSyncComponent);
    fixture.detectChanges();
    flushLoad(status());
    fixture.detectChanges();

    const checkbox: HTMLInputElement = fixture.nativeElement
      .querySelector('input[aria-label="Renommer aussi mes sorties sur Strava"]');
    checkbox.click();

    const req = http.expectOne((r) => r.url.endsWith('/me/strava/rename-on-strava'));
    req.flush(status({ renameOnStrava: true }));
    fixture.detectChanges();

    expect(text(fixture)).toContain('ne remet pas les anciens noms');
  });

  function flushLoad(s: StravaStatus): void {
    http.expectOne((r) => r.url.endsWith('/me/strava')).flush(s);
    http.expectOne((r) => r.url.endsWith('/me/activity-exclusions')).flush([]);
  }

  function text(fixture: { nativeElement: HTMLElement }): string {
    return fixture.nativeElement.textContent ?? '';
  }
});
