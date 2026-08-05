import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, UrlTree, provideRouter } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { User } from '../models/user.model';
import { installedAppGuard } from './installed-app.guard';

/**
 * L'application installée ne montre pas de page de vente.
 *
 * <p>Le `start_url` du manifeste est la racine, donc la landing publique : un athlète qui lançait
 * Darilab depuis son écran d'accueil tombait sur l'argumentaire produit et devait y chercher le
 * bouton « Se connecter ». Toute autre application ouvre sur l'écran de connexion.</p>
 */
describe('installedAppGuard', () => {
  let auth: AuthService;
  let router: Router;
  /** Réponses de `matchMedia` par requête, pour simuler une application installée ou non. */
  let displayModes: Record<string, boolean>;

  beforeEach(() => {
    localStorage.clear();
    displayModes = {};
    spyOn(window, 'matchMedia').and.callFake(
      (query: string) => ({ matches: !!displayModes[query] } as MediaQueryList),
    );

    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    auth = TestBed.inject(AuthService);
    router = TestBed.inject(Router);
  });

  afterEach(() => localStorage.clear());

  function run(): unknown {
    return TestBed.runInInjectionContext(() => installedAppGuard(null!, null!));
  }

  function installed(): void {
    displayModes['(display-mode: standalone)'] = true;
  }

  function signIn(): void {
    auth.token.set('jeton-de-test');
    auth.currentUser.set({ id: 'u1', email: 'x@y.z', fullName: 'Test', role: 'ATHLETE' } as User);
  }

  it('envoie vers la connexion en application installée sans session', () => {
    installed();
    const result = run();
    expect(result instanceof UrlTree).toBeTrue();
    expect(router.serializeUrl(result as UrlTree)).toBe('/login');
  });

  /** Sur le web ouvert, la racine reste la page publique : c'est le seul point d'entrée. */
  it('laisse la landing au visiteur du site', () => {
    expect(run()).toBeTrue();
  });

  /** Connecté, la landing renvoie déjà chez soi : le garde n'a pas à s'en mêler. */
  it('laisse passer une session ouverte, installée ou non', () => {
    signIn();
    expect(run()).toBeTrue();
    installed();
    expect(run()).toBeTrue();
  });

  /** iOS n'a longtemps pas répondu à la media query : `navigator.standalone` fait foi. */
  it('reconnaît le mode application d’iOS', () => {
    const nav = navigator as { standalone?: boolean };
    const previous = nav.standalone;
    nav.standalone = true;
    try {
      expect(run() instanceof UrlTree).toBeTrue();
    } finally {
      if (previous === undefined) delete nav.standalone; else nav.standalone = previous;
    }
  });
});
