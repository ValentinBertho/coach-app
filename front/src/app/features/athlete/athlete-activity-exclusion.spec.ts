import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AthletePortalService } from '../../core/services/athlete-portal.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { isSyncedSource } from '../../core/models/activity.model';

/**
 * « Supprimer cette sortie, et ne plus jamais l'importer. »
 *
 * <p>Ce que ces tests tiennent : la case n'est proposée que là où elle a un objet, et l'intention
 * arrive bien jusqu'à la requête. Une case cochée qui n'atteint pas le serveur laisserait
 * l'athlète croire la sortie écartée, et la synchro suivante la lui rapporterait.</p>
 */
describe('sortie écartée pour de bon', () => {
  let portal: AthletePortalService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    portal = TestBed.inject(AthletePortalService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('ne propose l’option que pour une source qui se synchronise', () => {
    expect(isSyncedSource('STRAVA')).toBeTrue();
    expect(isSyncedSource('GARMIN')).toBeTrue();
    expect(isSyncedSource('COROS')).toBeTrue();
    // Rien ne les rapporterait : promettre de l'empêcher serait une promesse vide.
    expect(isSyncedSource('MANUAL')).toBeFalse();
    expect(isSyncedSource('FILE')).toBeFalse();
    expect(isSyncedSource(null)).toBeFalse();
  });

  it('porte l’intention jusqu’à la requête', () => {
    portal.deleteActivity('a1', true).subscribe();
    const req = http.expectOne((r) => r.url.endsWith('/me/activities/a1'));
    expect(req.request.method).toBe('DELETE');
    expect(req.request.params.get('neverImportAgain')).toBe('true');
    req.flush(null);
  });

  it('reste une suppression ordinaire par défaut', () => {
    portal.deleteActivity('a1').subscribe();
    const req = http.expectOne((r) => r.url.endsWith('/me/activities/a1'));
    expect(req.request.params.get('neverImportAgain')).toBe('false');
    req.flush(null);
  });

  it('sait relire et lever un masquage', () => {
    portal.excludedActivities().subscribe();
    http.expectOne((r) => r.url.endsWith('/me/activity-exclusions')).flush([]);

    portal.unmaskActivity('x1').subscribe();
    const req = http.expectOne((r) => r.url.endsWith('/me/activity-exclusions/x1'));
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});

/**
 * La case de la modale de confirmation : elle accompagne l'action sans la conditionner, et ne
 * doit jamais survivre à la modale.
 */
describe('confirmation à option', () => {
  let confirm: ConfirmService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [] });
    confirm = TestBed.inject(ConfirmService);
  });

  it('rend l’accord et l’état de la case', async () => {
    const answer = confirm.askWithOption({
      title: 'Supprimer ?', message: '…', optionLabel: 'Ne plus jamais importer',
    });
    confirm.answer(true, undefined, true);
    expect(await answer).toEqual({ confirmed: true, option: true });
  });

  it('ignore la case quand on renonce', async () => {
    const answer = confirm.askWithOption({
      title: 'Supprimer ?', message: '…', optionLabel: 'Ne plus jamais importer',
    });
    // Cocher puis annuler ne veut rien dire : il n'y a pas eu de suppression à rendre définitive.
    confirm.answer(false, undefined, true);
    expect(await answer).toEqual({ confirmed: false, option: false });
  });

  it('laisse la confirmation ordinaire inchangée', async () => {
    const answer = confirm.ask({ title: 'Supprimer ?', message: '…' });
    confirm.answer(true);
    expect(await answer).toBeTrue();
  });
});
