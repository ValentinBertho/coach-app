import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { AuthService } from './auth.service';
import { NotificationService } from './notification.service';

/**
 * Le badge de la cloche suit l'état d'authentification.
 *
 * <p>La déconnexion doit remettre le compteur à zéro. Elle le fait depuis un effet — qu'Angular
 * empêche par défaut d'écrire dans un signal. Sans le drapeau `allowSignalWrites`, la
 * déconnexion levait NG0600 sur le `unread.set(0)` et le badge gardait les non-lues du compte
 * précédent, visibles par qui se connectait ensuite sur le même onglet.</p>
 */
describe('notifications — le badge suit la session', () => {
  const token = signal<string | null>(null);

  function serviceWithToken(): NotificationService {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { token } },
      ],
    });
    return TestBed.inject(NotificationService);
  }

  beforeEach(() => {
    TestBed.resetTestingModule();
    token.set(null);
  });

  it('remet le compteur à zéro à la déconnexion', () => {
    token.set('un-jeton');
    const service = serviceWithToken();
    TestBed.flushEffects();

    // Un compteur reçu pendant la session, comme le pousserait le flux temps réel.
    service.unread.set(4);

    token.set(null);
    TestBed.flushEffects();

    expect(service.unread()).toBe(0);
  });

  it('ne touche pas au compteur tant que la session dure', () => {
    token.set('un-jeton');
    const service = serviceWithToken();
    TestBed.flushEffects();

    service.unread.set(3);
    token.set('un-jeton-renouvele');   // rotation du jeton : le flux rouvre, le badge reste juste
    TestBed.flushEffects();

    expect(service.unread()).toBe(3);
  });
});
