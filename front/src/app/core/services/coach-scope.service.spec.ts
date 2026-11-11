import { TestBed } from '@angular/core/testing';
import { CoachScopeService } from './coach-scope.service';

/**
 * Le périmètre du coach est un seul réglage pour tous ses écrans : c'est ce qui donne le même
 * chiffre de retours à la pastille du menu, à « Ma journée » et au tableau de bord.
 */
describe('CoachScopeService', () => {
  beforeEach(() => {
    localStorage.removeItem('coach-scope');
    localStorage.removeItem('coach-day-scope');
  });
  afterEach(() => {
    localStorage.removeItem('coach-scope');
    localStorage.removeItem('coach-day-scope');
  });

  function fresh(): CoachScopeService {
    TestBed.resetTestingModule();
    return TestBed.inject(CoachScopeService);
  }

  it('part de « mes athlètes » sans préférence enregistrée', () => {
    expect(fresh().scope()).toBe('mine');
    expect(fresh().label()).toBe('Mes athlètes');
  });

  it('retient le choix entre deux sessions', () => {
    fresh().set('all');
    expect(fresh().scope()).toBe('all');
  });

  it("reprend le périmètre que l'ancienne « Ma journée » avait retenu", () => {
    localStorage.setItem('coach-day-scope', 'private');
    expect(fresh().scope()).toBe('private');
  });

  it('ignore une valeur inconnue', () => {
    localStorage.setItem('coach-scope', 'tout');
    expect(fresh().scope()).toBe('mine');
  });
});
