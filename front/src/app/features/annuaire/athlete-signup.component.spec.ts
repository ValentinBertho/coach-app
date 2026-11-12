import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { AthleteSignupComponent } from './athlete-signup.component';

/**
 * L'inscription athlète, arrivée depuis la fiche d'un coach.
 *
 * <p>Ce que ces tests protègent : un visiteur qui a choisi son coach écrit sa demande <b>dans le
 * même formulaire</b> que son compte, au lieu de devoir s'inscrire, confirmer, puis tout
 * recommencer depuis l'annuaire.</p>
 */
describe('inscription athlète — la demande s’écrit avec le compte', () => {
  let http: HttpTestingController;

  function create(coach: string | null) {
    TestBed.configureTestingModule({
      imports: [AthleteSignupComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap(coach ? { coach } : {}) } },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(AthleteSignupComponent);
    fixture.detectChanges();
    return fixture;
  }

  function fill(fixture: ReturnType<typeof create>, message?: string) {
    fixture.componentInstance.form.patchValue({
      firstName: 'Nina', lastName: 'Roy', email: 'nina@exemple.fr', password: 'password1234',
      birthDate: '1995-04-02', termsAccepted: true, healthDataConsent: true,
      ...(message !== undefined ? { requestMessage: message } : {}),
    });
  }

  afterEach(() => http.verify());

  it('sans coach choisi, reste une inscription simple', () => {
    const fixture = create(null);
    const host = fixture.nativeElement as HTMLElement;
    expect(host.textContent).toContain('Créer mon compte athlète');
    expect(host.querySelector('#requestMessage')).toBeNull();

    fill(fixture);
    fixture.componentInstance.submit();
    const req = http.expectOne((r) => r.url.endsWith('/public/athlete-registration'));
    expect(req.request.body.coachSlug).toBeNull();
    expect(req.request.body.requestMessage).toBeNull();
  });

  it('venu d’une fiche, demande un message et l’envoie avec l’inscription', () => {
    const fixture = create('sarah-lemoine');
    http.expectOne((r) => r.url.endsWith('/public/coaches/sarah-lemoine'))
      .flush({ slug: 'sarah-lemoine', name: 'Sarah Lemoine', acceptingAthletes: true });
    fixture.detectChanges();
    const host = fixture.nativeElement as HTMLElement;
    expect(host.textContent).toContain('Demander à Sarah Lemoine de vous coacher');
    expect(host.querySelector('#requestMessage')).not.toBeNull();

    fill(fixture, 'Bonjour');
    fixture.componentInstance.submit();
    http.expectNone((r) => r.url.endsWith('/public/athlete-registration'));

    fill(fixture, '  Je prépare mon premier marathon pour avril.  ');
    fixture.componentInstance.submit();
    const req = http.expectOne((r) => r.url.endsWith('/public/athlete-registration'));
    expect(req.request.body.coachSlug).toBe('sarah-lemoine');
    expect(req.request.body.requestMessage).toBe('Je prépare mon premier marathon pour avril.');
  });

  it('retombe sur l’inscription simple quand le coach ne prend plus personne', () => {
    const fixture = create('malik-fournier');
    http.expectOne((r) => r.url.endsWith('/public/coaches/malik-fournier'))
      .flush({ slug: 'malik-fournier', name: 'Malik Fournier', acceptingAthletes: false });
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('#requestMessage')).toBeNull();
  });
});
