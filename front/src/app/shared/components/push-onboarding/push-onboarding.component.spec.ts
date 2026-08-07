import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed, fakeAsync, flush, tick } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Bell, LucideAngularModule } from 'lucide-angular';
import { importProvidersFrom } from '@angular/core';
import { AuthService } from '../../../core/services/auth.service';
import { PushService } from '../../../core/services/push.service';
import { PushOnboardingComponent } from './push-onboarding.component';

/**
 * Invitation aux notifications au premier lancement de l'application installée.
 *
 * <p>Une fenêtre qui déclenche une demande d'autorisation système ne se trompe pas impunément :
 * un refus navigateur est <b>définitif</b> et ne se rattrape que dans les réglages du téléphone.
 * Les conditions d'apparition sont donc la partie sensible, et c'est ce que ces règles fixent.</p>
 */
describe('invitation aux notifications au premier lancement', () => {
  let fixture: ComponentFixture<PushOnboardingComponent>;
  let component: PushOnboardingComponent;
  let push: jasmine.SpyObj<Pick<PushService, 'enable'>> & Partial<PushService>;
  let auth: AuthService;
  let installed: boolean;

  /** État du navigateur simulé : installé ou non, capable ou non, déjà abonné ou non. */
  function setUp(options: { installed?: boolean; available?: boolean; subscribed?: boolean } = {}) {
    installed = options.installed ?? true;
    spyOn(window, 'matchMedia').and.callFake(
      (query: string) => ({ matches: installed && query.includes('standalone') } as MediaQueryList),
    );

    const pushStub = {
      available: options.available ?? true,
      subscribed: jasmine.createSpy('subscribed').and.returnValue(options.subscribed ?? false),
      enable: jasmine.createSpy('enable').and.resolveTo(true),
      init: () => {},
    };

    TestBed.configureTestingModule({
      providers: [
        provideRouter([]), provideHttpClient(), provideHttpClientTesting(),
        importProvidersFrom(LucideAngularModule.pick({ Bell })),
        { provide: PushService, useValue: pushStub },
      ],
    });
    push = TestBed.inject(PushService) as never;
    auth = TestBed.inject(AuthService);
    auth.token.set('un-jeton');

    fixture = TestBed.createComponent(PushOnboardingComponent);
    component = fixture.componentInstance;
  }

  beforeEach(() => localStorage.clear());
  afterEach(() => localStorage.clear());

  /** Fait s'écouler le délai d'apparition et rend le résultat. */
  function launch(): void {
    fixture.detectChanges();
    tick(2_000);
    fixture.detectChanges();
  }

  it('se propose au premier lancement de l’application installée', fakeAsync(() => {
    setUp();

    launch();

    expect(component.visible()).toBeTrue();
    expect(fixture.nativeElement.textContent).toContain('Activer les notifications');
  }));

  /**
   * Sur le web ouvert, une demande d'autorisation à la première visite est le geste que les
   * navigateurs sanctionnent et que les visiteurs refusent par réflexe. Et sur iOS, le push
   * n'existe tout simplement pas hors application installée.
   */
  it('ne se propose pas dans un simple onglet de navigateur', fakeAsync(() => {
    setUp({ installed: false });

    launch();

    expect(component.visible()).toBeFalse();
  }));

  it('ne se propose pas sans session ouverte : il n’y aurait personne à abonner', fakeAsync(() => {
    setUp();
    auth.token.set(null);

    launch();

    expect(component.visible()).toBeFalse();
  }));

  it('ne se propose pas à un appareil déjà abonné', fakeAsync(() => {
    setUp({ subscribed: true });

    launch();

    expect(component.visible()).toBeFalse();
  }));

  it('ne se propose pas quand l’appareil ne sait pas recevoir de push', fakeAsync(() => {
    setUp({ available: false });

    launch();

    expect(component.visible()).toBeFalse();
  }));

  /** Rien ne s'affiche avant le délai : un appareil déjà abonné ne doit pas voir un clignotement. */
  it('n’apparaît pas instantanément au démarrage', () => {
    setUp();

    fixture.detectChanges();

    expect(component.visible()).toBeFalse();
  });

  it('appelle l’activation et se referme', fakeAsync(() => {
    setUp();
    launch();

    component.enable();
    tick();
    fixture.detectChanges();

    expect(push.enable).toHaveBeenCalled();
    expect(component.visible()).toBeFalse();
    flush(); // le toast de confirmation programme sa propre disparition
  }));

  /**
   * « Plus tard » vaut réponse : une invitation qui revient à chaque lancement est une nuisance,
   * et le réglage reste accessible dans les écrans dédiés.
   */
  it('ne repose jamais la question, quelle qu’ait été la réponse', fakeAsync(() => {
    setUp();
    launch();
    component.later();
    fixture.detectChanges();
    expect(component.visible()).toBeFalse();

    // Nouveau lancement, même appareil.
    const second = TestBed.createComponent(PushOnboardingComponent);
    second.detectChanges();
    tick(2_000);
    second.detectChanges();

    expect(second.componentInstance.visible()).toBeFalse();
  }));

  /** Un échec d'activation ne doit pas piéger la fenêtre ouverte indéfiniment. */
  it('se referme même si l’activation échoue', fakeAsync(() => {
    setUp();
    (push.enable as jasmine.Spy).and.rejectWith(new Error('refus'));
    launch();

    component.enable();
    tick();
    fixture.detectChanges();

    expect(component.visible()).toBeFalse();
    flush(); // idem : le toast d'erreur a son minuteur
  }));
});
