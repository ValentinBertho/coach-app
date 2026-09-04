import { importProvidersFrom } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { ICONS } from '../../app.config';
import { CoachDetail } from '../../core/services/coach-directory.service';
import { CoachDetailComponent } from './coach-detail.component';

/**
 * La fiche publique d'un coach.
 *
 * <p>Deux choses s'y jouent qui n'ont pas la même nature, et ces tests fixent les deux : ce que la
 * page <b>affirme</b> — des signaux mesurés, jamais un chiffre inventé sur un échantillon d'un —
 * et ce qu'elle <b>permet de contester</b>, puisque les diplômes y sont publiés sans avoir été
 * vérifiés.</p>
 */
describe('fiche publique d’un coach', () => {
  let fixture: ComponentFixture<CoachDetailComponent>;
  let http: HttpTestingController;

  const base: CoachDetail = {
    slug: 'marie-dupont',
    name: 'Marie Dupont',
    headline: 'Coach route et trail',
    disciplines: ['ROUTE'],
    specialties: ['MARATHON'],
    specialtyLabels: ['Marathon'],
    languages: ['fr'],
    city: 'Lyon',
    remote: true,
    inPerson: false,
    experienceYears: 12,
    fromMonthlyCents: 9000,
    photoUrl: null,
    medianResponseHours: null,
    acceptingAthletes: true,
    bio: 'Une présentation.',
    responseRatePercent: null,
    levels: [],
    country: 'FR',
    capacityMax: null,
    certifications: [],
    certificationsDeclared: true,
    offers: [],
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [CoachDetailComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        importProvidersFrom(LucideAngularModule.pick(ICONS)),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ slug: 'marie-dupont' }) } },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(CoachDetailComponent);
  });

  afterEach(() => http.verify());

  function start(coach: Partial<CoachDetail> = {}): HTMLElement {
    fixture.detectChanges();
    http.expectOne((r) => r.url.includes('/public/coaches/marie-dupont'))
      .flush({ ...base, ...coach });
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  /**
   * Le signal se tait plutôt que de mentir.
   *
   * <p>Le serveur renvoie {@code null} tant que l'échantillon est trop mince, et la page doit s'en
   * accommoder sans afficher « répond en 0 h » ni un pourcentage vide. Un chiffre bâti sur une
   * seule demande serait une promesse que rien ne fonde.</p>
   */
  it('n’affiche aucun délai de réponse tant que le serveur n’en donne pas', () => {
    const host = start();
    expect(host.querySelector('.cd__signals')).withContext('rien à dire, donc rien').toBeNull();
  });

  /** Une médiane observée se dit prudemment, et se lit comme on la dirait à voix haute. */
  it('formule le délai de réponse en langage courant', () => {
    const host = start({ medianResponseHours: 36, responseRatePercent: 80 });
    const signals = host.querySelector('.cd__signals')!.textContent!;
    expect(signals).withContext('36 h se comprend moins vite que « deux jours »')
      .toContain('moins de 2 jours');
    expect(signals).toContain('80 %');
  });

  /** Une réponse en dix minutes ne s'annonce pas « en 0 h ». */
  it('dit « moins d’une heure » plutôt qu’un zéro', () => {
    const host = start({ medianResponseHours: 1 });
    expect(host.querySelector('.cd__signals')!.textContent).toContain("moins d'une heure");
  });

  /**
   * Le recours qui rend tenable la publication de diplômes non vérifiés.
   *
   * <p>Sans connexion : celui qui reconnaît un diplôme faux n'a aucune raison d'avoir un compte,
   * et l'exiger reviendrait à n'écouter que les clients.</p>
   */
  it('laisse un visiteur non connecté signaler la fiche', () => {
    const host = start();

    const details = host.querySelector('#report-details') as HTMLTextAreaElement;
    details.value = 'Ce coach affiche un diplôme qu’il n’a pas, je suis formateur dans ce cursus.';
    details.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    (host.querySelector('.cd__report button') as HTMLButtonElement).click();

    const sent = http.expectOne((r) => r.url.endsWith('/public/coaches/marie-dupont/report'));
    expect(sent.request.method).toBe('POST');
    expect(sent.request.body.reason)
      .withContext('le premier motif de la liste est le motif par défaut')
      .toBe('FALSE_CREDENTIALS');
    expect(sent.request.headers.has('Authorization'))
      .withContext('aucun jeton n’est exigé')
      .toBeFalse();
    sent.flush(null);
    fixture.detectChanges();

    expect(host.querySelector('.cd__reported')).withContext('le formulaire cède la place').toBeTruthy();
  });

  /**
   * Une case cochée sans explication ne se traite pas.
   *
   * <p>Le serveur refuse déjà un texte trop court ; le bouton le refuse aussi, pour que le
   * visiteur l'apprenne avant d'envoyer plutôt qu'après.</p>
   */
  it('refuse d’envoyer un signalement sans explication', () => {
    const host = start();
    const button = host.querySelector('.cd__report button') as HTMLButtonElement;
    expect(button.disabled).withContext('un texte vide ne dit rien').toBeTrue();

    const details = host.querySelector('#report-details') as HTMLTextAreaElement;
    details.value = 'pas sérieux';
    details.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(button.disabled).withContext('onze caractères non plus').toBeTrue();
  });

  /** Le titre et l'Open Graph sont posés pour le jour où un pré-rendu existera. */
  it('renseigne le titre de la page avec le nom du coach', () => {
    start();
    expect(document.title).toContain('Marie Dupont');
  });
});
