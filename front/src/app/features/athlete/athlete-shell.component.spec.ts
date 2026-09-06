import { importProvidersFrom } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { ICONS } from '../../app.config';
import { AuthService } from '../../core/services/auth.service';
import { User } from '../../core/models/user.model';
import { AthleteShellComponent } from './athlete-shell.component';

/**
 * La navigation du portail athlète, et ce qu'elle laisse atteindre.
 *
 * <p>Un athlète venu du hub n'a pas de fiche tant qu'aucun coach ne l'a accepté : calendrier,
 * séance du jour et progrès n'auraient rien à lui montrer. Mais <b>après</b> une fin de coaching,
 * il n'a de nouveau plus de fiche — et ses conversations, elles, restent lisibles côté serveur.
 * Sans une entrée « Messages », cette règle serait vraie et inatteignable : il aurait perdu
 * l'accès à des messages qu'il a lui-même écrits.</p>
 */
describe('coquille athlète — ce que la navigation laisse atteindre', () => {
  let fixture: ComponentFixture<AthleteShellComponent>;
  let http: HttpTestingController;
  let auth: AuthService;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      imports: [AthleteShellComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        importProvidersFrom(LucideAngularModule.pick(ICONS)),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
    fixture = TestBed.createComponent(AthleteShellComponent);
  });

  afterEach(() => localStorage.clear());

  function signIn(athleteId: string | null): void {
    auth.token.set('jeton-de-test');
    auth.currentUser.set({
      id: 'u1', email: 'x@y.z', fullName: 'Lena', role: 'ATHLETE', athleteId,
    } as User);
  }

  function labels(): string[] {
    return Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('.ashell__nav a'))
      .map((a) => a.textContent?.trim() ?? '');
  }

  /** Suivi : la navigation complète, et aucune requête pour le deviner. */
  it('montre la navigation d’entraînement à un athlète suivi', () => {
    signIn('a1');
    fixture.detectChanges();

    expect(labels().join(' ')).toContain('Calendrier');
    expect(labels().join(' ')).toContain('Messages');
    http.expectNone((r) => r.url.includes('/me/conversations'));
  });

  /** Jamais suivi : rien à relire, donc pas d'entrée « Messages » qui ouvrirait sur du vide. */
  it('n’offre pas Messages à un athlète qui n’a jamais eu de coach', () => {
    signIn(null);
    fixture.detectChanges();
    http.expectOne((r) => r.url.endsWith('/me/conversations')).flush([]);
    fixture.detectChanges();

    expect(labels().join(' ')).toContain('Trouver un coach');
    expect(labels().join(' ')).not.toContain('Messages');
  });

  /**
   * Le cas qui motive tout : la relation est terminée, la fiche a disparu, et le fil reste.
   *
   * <p>C'est la seule porte vers des messages que l'athlète a lui-même écrits.</p>
   */
  it('garde Messages après une fin de coaching, pour les fils déjà tenus', () => {
    signIn(null);
    fixture.detectChanges();
    http.expectOne((r) => r.url.endsWith('/me/conversations'))
      .flush([{ id: 'c1', kind: 'ATHLETE_COACH', title: 'Coach Démo', unreadCount: 0 }]);
    fixture.detectChanges();

    expect(labels().join(' '))
      .withContext('sans cette entrée, la lecture seule serait inatteignable')
      .toContain('Messages');
  });

  /** Une boîte injoignable ne doit pas proposer une porte qui s'ouvrirait sur une erreur. */
  it('n’offre pas Messages si la boîte est injoignable', () => {
    signIn(null);
    fixture.detectChanges();
    http.expectOne((r) => r.url.endsWith('/me/conversations'))
      .error(new ProgressEvent('erreur réseau'));
    fixture.detectChanges();

    expect(labels().join(' ')).not.toContain('Messages');
  });
});
