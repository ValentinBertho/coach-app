import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, UrlTree, provideRouter } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { User } from '../models/user.model';
import { adminGuard } from './admin.guard';
import { athleteGuard } from './athlete.guard';
import { coachGuard } from './coach.guard';

/**
 * Gardes de rôle — le correctif le plus grave des audits de bêta, livré sans test.
 *
 * <p>Rappel du défaut : `/app` n'était protégé que par un garde d'authentification. Un athlète
 * connecté — c'est-à-dire la moitié des utilisateurs, sur téléphone, sans formation — ouvrait
 * donc le cockpit coach, qui se remplissait de « Accès refusé » puisque le serveur refuse à
 * raison toute la surface club à son jeton. Aucune donnée ne fuitait ; c'était simplement
 * l'écran d'accueil de la PWA.</p>
 *
 * <p>Ces tests fixent les deux moitiés de la règle : qui entre, et <b>où</b> est renvoyé celui
 * qui n'entre pas. La seconde compte autant : renvoyer un athlète vers `/app` — l'ancien
 * comportement de `adminGuard` — le remettait exactement dans l'écran cassé.</p>
 */
describe('gardes de rôle', () => {
  let auth: AuthService;
  let router: Router;

  beforeEach(() => {
    localStorage.clear();
    // AuthService dépend de HttpClient (logout appelle le serveur) : les gardes ne s'en servent
    // pas, mais le service ne s'instancie pas sans.
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    auth = TestBed.inject(AuthService);
    router = TestBed.inject(Router);
  });

  afterEach(() => localStorage.clear());

  /** Pose une session sans passer par le réseau : les gardes ne lisent que ces deux signaux. */
  function signIn(role: User['role']): void {
    auth.token.set('jeton-de-test');
    auth.currentUser.set({ id: 'u1', email: 'x@y.z', fullName: 'Test', role } as User);
  }

  function redirectOf(result: unknown): string {
    expect(result instanceof UrlTree).withContext('un refus doit rediriger').toBe(true);
    return router.serializeUrl(result as UrlTree);
  }

  function run(guard: typeof coachGuard): unknown {
    return TestBed.runInInjectionContext(() => guard(null!, null!));
  }

  describe('coachGuard', () => {
    it('laisse entrer un coach', () => {
      signIn('COACH');
      expect(run(coachGuard)).toBe(true);
    });

    it('laisse entrer un head coach', () => {
      signIn('HEAD_COACH');
      expect(run(coachGuard)).toBe(true);
    });

    it('laisse entrer un admin plateforme, vers qui adminGuard renvoie', () => {
      signIn('PLATFORM_ADMIN');
      expect(run(coachGuard)).toBe(true);
    });

    /** « Chez lui » = son calendrier, l'écran d'ouverture du portail depuis la réorganisation. */
    it('renvoie un athlète chez lui, et non sur la landing publique', () => {
      signIn('ATHLETE');
      expect(redirectOf(run(coachGuard))).toBe('/athlete/calendar');
    });

    it('renvoie un visiteur non connecté vers la connexion', () => {
      expect(redirectOf(run(coachGuard))).toBe('/login');
    });
  });

  describe('athleteGuard', () => {
    it('laisse entrer un athlète', () => {
      signIn('ATHLETE');
      expect(run(athleteGuard)).toBe(true);
    });

    it('renvoie un coach vers son cockpit', () => {
      signIn('COACH');
      expect(redirectOf(run(athleteGuard))).toBe('/app');
    });
  });

  describe('adminGuard', () => {
    it('laisse entrer un admin plateforme', () => {
      signIn('PLATFORM_ADMIN');
      expect(run(adminGuard)).toBe(true);
    });

    /** C'était la redirection en dur vers `/app` : la mauvaise destination pour un athlète. */
    it('renvoie un athlète vers son espace, pas vers le cockpit coach', () => {
      signIn('ATHLETE');
      expect(redirectOf(run(adminGuard))).toBe('/athlete/calendar');
    });

    it('renvoie un coach vers son cockpit', () => {
      signIn('COACH');
      expect(redirectOf(run(adminGuard))).toBe('/app');
    });
  });
});
