import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { importProvidersFrom } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { LucideAngularModule } from 'lucide-angular';
import { ICONS } from '../../../app.config';
import { Router, provideRouter } from '@angular/router';
import { AuthService, isTrialEmail } from '../../../core/services/auth.service';
import { TrialBannerComponent } from './trial-banner.component';

const ACCESS_KEY = 'darilab.accessToken';
const USER_KEY = 'darilab.user';

function session(email: string, role: string): void {
  localStorage.setItem(ACCESS_KEY, 'jeton');
  localStorage.setItem(USER_KEY, JSON.stringify({ id: 'u', email, role, clubId: 'c' }));
}

describe('TrialBannerComponent', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      imports: [TrialBannerComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
        importProvidersFrom(LucideAngularModule.pick(ICONS))],
    });
  });

  afterEach(() => localStorage.clear());

  it('reconnaît une adresse d’essai', () => {
    expect(isTrialEmail('demo@a1b2c3.essai.invalid')).toBeTrue();
    expect(isTrialEmail('coach@club.fr')).toBeFalse();
    expect(isTrialEmail(null)).toBeFalse();
  });

  it('reste invisible pour un vrai compte', () => {
    session('coach@club.fr', 'HEAD_COACH');
    const fixture = TestBed.createComponent(TrialBannerComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.trial')).toBeNull();
  });

  it('s’affiche dans un essai et passe côté athlète', () => {
    session('demo@a1b2c3.essai.invalid', 'HEAD_COACH');
    const fixture = TestBed.createComponent(TrialBannerComponent);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('Espace d\'essai');
    const navigate = spyOn(TestBed.inject(Router), 'navigateByUrl').and.resolveTo(true);

    const switchBtn = Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.includes('côté athlète'))!;
    switchBtn.click();
    TestBed.inject(HttpTestingController).expectOne((r) => r.url.endsWith('/trial/switch')).flush({
      accessToken: 'jeton-athlete', refreshToken: null, expiresIn: 900,
      user: { id: 'a', email: 'athlete@a1b2c3.essai.invalid', role: 'ATHLETE', clubId: 'c', athleteId: 'x' },
    });

    expect(TestBed.inject(AuthService).currentUser()?.role).toBe('ATHLETE');
    expect(navigate).toHaveBeenCalledWith('/athlete/calendar');
  });
});
