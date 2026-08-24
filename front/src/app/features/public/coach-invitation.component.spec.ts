import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { CoachInvitationComponent } from './coach-invitation.component';

/**
 * Le tout premier écran d'un coach : il y arrive depuis sa boîte mail, sans compte et sans repère.
 * Ces tests interdisent les deux culs-de-sac qu'on y trouvait.
 */
describe('invitation coach', () => {
  let fixture: ComponentFixture<CoachInvitationComponent>;
  let host: HTMLElement;
  let http: HttpTestingController;
  let router: Router;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      imports: [CoachInvitationComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    spyOn(router, 'navigateByUrl').and.resolveTo(true);

    fixture = TestBed.createComponent(CoachInvitationComponent);
    fixture.componentRef.setInput('token', 'jeton-de-test');
    fixture.detectChanges();
  });

  afterEach(() => localStorage.clear());

  /** Liens de l'écran, tels qu'un coach les verrait. */
  function links(): string[] {
    return Array.from(host.querySelectorAll('a')).map((a) => a.getAttribute('href') ?? '');
  }

  /**
   * Un lien d'invitation ne sert qu'une fois : recharger la page après activation suffit à tomber
   * ici. L'écran disait « Invitation expirée » et n'offrait aucune issue — pas même « se
   * connecter », alors que le compte venait d'être créé.
   */
  it('offre une issue quand le lien a déjà servi', () => {
    http.expectOne((r) => r.url.includes('/public/coach-invitations/')).flush(null, {
      status: 404, statusText: 'Not Found',
    });
    fixture.detectChanges();
    host = fixture.nativeElement as HTMLElement;

    expect(links()).withContext('un cul-de-sac, sans même un lien de connexion').toContain('/login');
    expect(links()).toContain('/forgot-password');
  });

  it("emmène le coach chez lui une fois le compte activé", async () => {
    http.expectOne((r) => r.url.includes('/public/coach-invitations/')).flush({
      email: 'coach@club.io', fullName: 'Coach Test', clubName: 'AC Test',
    });
    fixture.detectChanges();

    const component = fixture.componentInstance;
    component.password = 'password123';
    component.termsAccepted = true;
    component.accept();

    http.expectOne((r) => r.url.endsWith('/accept')).flush({
      accessToken: 'a', refreshToken: 'r',
      user: { id: 'u1', email: 'coach@club.io', fullName: 'Coach Test', role: 'COACH' },
    });
    fixture.detectChanges();
    host = fixture.nativeElement as HTMLElement;

    expect(router.navigateByUrl).toHaveBeenCalledWith('/app');
    // Et si la redirection n'aboutissait pas, l'écran ne laisse pas le coach devant son formulaire.
    expect(host.textContent).toContain('Compte activé');
    expect(links()).toContain('/app');
  });
});
