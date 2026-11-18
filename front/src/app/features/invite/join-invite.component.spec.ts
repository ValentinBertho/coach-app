import { importProvidersFrom } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { ICONS } from '../../app.config';
import { AuthService } from '../../core/services/auth.service';
import { User } from '../../core/models/user.model';
import { JoinInviteComponent } from './join-invite.component';

/** La page qu'ouvre un lien d'invitation : qui invite, et le seul geste utile. */
describe('rejoindre par un lien d’invitation', () => {
  let fixture: ComponentFixture<JoinInviteComponent>;
  let http: HttpTestingController;

  const preview = {
    valid: true, reason: null, coachName: 'Sarah Lemoine', coachSlug: null, coachPhotoUrl: null,
    clubName: 'Athlé Vannes', independentCoach: false, clubMembership: true, groupName: 'Mardi',
    directEntry: true,
  };

  function setUp(user: Partial<User> | null): void {
    localStorage.clear();
    TestBed.configureTestingModule({
      imports: [JoinInviteComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        importProvidersFrom(LucideAngularModule.pick(ICONS)),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    const auth = TestBed.inject(AuthService);
    if (user) {
      auth.currentUser.set(user as User);
      spyOn(auth, 'isAuthenticated').and.returnValue(true);
    }
    fixture = TestBed.createComponent(JoinInviteComponent);
    fixture.componentRef.setInput('token', 'Abc123');
    fixture.detectChanges();
    http.expectOne((r) => r.url.endsWith('/public/invites/Abc123')).flush(preview);
    fixture.detectChanges();
  }

  it('sans compte : propose de le créer en gardant le lien', () => {
    setUp(null);
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Athlé Vannes vous invite');
    expect(text).toContain('Créer mon compte');
    const signup = fixture.nativeElement.querySelector('a[href*="inscription-athlete"]') as HTMLAnchorElement;
    expect(signup.getAttribute('href')).toContain('invite=Abc123');
  });

  it('athlète connecté : rejoindre en un geste', () => {
    setUp({ role: 'ATHLETE', fullName: 'Léa Martin' });
    const button = fixture.nativeElement.querySelector('button.btn-primary') as HTMLButtonElement;
    expect(button.textContent).toContain('Rejoindre Athlé Vannes');
    button.click();
    http.expectOne((r) => r.url.endsWith('/me/invites/Abc123/join'))
      .flush({ outcome: 'JOINED', coachName: 'Sarah Lemoine' });
    http.match((r) => r.url.endsWith('/refresh')).forEach((r) => r.flush({}));
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Sarah Lemoine vous suit désormais');
  });

  it('athlète déjà suivi : rejoindre, c’est changer de coach', () => {
    setUp({ role: 'ATHLETE', fullName: 'Léa Martin', athleteId: 'a1', personalSpace: false } as Partial<User>);
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Vous êtes déjà suivi par un coach');
    const button = fixture.nativeElement.querySelector('button.btn-primary') as HTMLButtonElement;
    expect(button.textContent).toContain('Changer de coach et rejoindre Athlé Vannes');
    button.click();
    const req = http.expectOne((r) => r.url.endsWith('/me/invites/Abc123/join'));
    expect(req.request.body.switchCoach).toBeTrue();
    expect(req.request.body.shareHistory).toBeTrue();
    req.flush({ outcome: 'JOINED', coachName: 'Sarah Lemoine' });
    http.match((r) => r.url.endsWith('/refresh')).forEach((r) => r.flush({}));
  });
});
