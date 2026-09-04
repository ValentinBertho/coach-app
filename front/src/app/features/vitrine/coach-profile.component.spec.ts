import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { importProvidersFrom } from '@angular/core';
import { LucideAngularModule } from 'lucide-angular';
import { ICONS } from '../../app.config';
import { CoachProfile } from '../../core/services/coach-profile.service';
import { CoachProfileComponent } from './coach-profile.component';

/**
 * L'éditeur de la vitrine.
 *
 * <p>Ce que ces tests interdisent : qu'un coach reste devant un bouton « Publier » sans savoir
 * pourquoi il ne marche pas, et qu'il croie pouvoir modifier une fiche que le serveur a gelée.
 * Les deux se règlent en affichant ce que le serveur dit, plutôt qu'en recopiant ses règles ici —
 * une règle recopiée finit par diverger de celle qui décide vraiment.</p>
 */
describe('vitrine — l’éditeur montre l’état réel de la fiche', () => {
  let fixture: ComponentFixture<CoachProfileComponent>;
  let http: HttpTestingController;

  const base: CoachProfile = {
    id: 'p1',
    slug: 'marie-dupont',
    status: 'DRAFT',
    statusLabel: 'Brouillon',
    coachName: 'Marie Dupont',
    headline: null,
    bio: null,
    disciplines: [],
    specialties: [],
    levels: [],
    languages: [],
    city: null,
    country: null,
    remote: true,
    inPerson: false,
    experienceYears: null,
    capacityMax: null,
    submittedAt: null,
    publishedAt: null,
    reviewedAt: null,
    reviewNote: null,
    medianResponseHours: null,
    certifications: [],
    offers: [],
    missing: [],
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [CoachProfileComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        // Le jeu complet plutôt que les icônes de ce gabarit : Lucide lève dès qu'une icône
        // rendue n'a pas de fournisseur, et une liste tenue à la main casserait au premier
        // ajout, pour une raison étrangère à ce que ce test vérifie.
        importProvidersFrom(LucideAngularModule.pick(ICONS)),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(CoachProfileComponent);
  });

  afterEach(() => http.verify());

  /** Démarre l'écran sur la fiche donnée. */
  function start(profile: Partial<CoachProfile>): HTMLElement {
    fixture.detectChanges();
    http.expectOne((r) => r.url.endsWith('/vocabulary'))
      .flush({ disciplines: [], specialties: [], levels: [], periodicities: [] });
    http.expectOne((r) => r.url.endsWith('/me/coach-profile') && r.method === 'GET')
      .flush({ ...base, ...profile });
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  /**
   * La liste des manques vient du serveur — celui-là même qui refuserait la soumission. La
   * recopier ici produirait tôt ou tard deux règles différentes, dont une fausse.
   */
  it('affiche ce qui manque, et retient le bouton tant qu’il manque quelque chose', () => {
    const host = start({ missing: ['une accroche', 'au moins une formule tarifaire'] });

    expect(host.textContent).toContain('une accroche');
    expect(host.textContent).toContain('au moins une formule tarifaire');

    const submit = Array.from(host.querySelectorAll('button'))
      .find((b) => b.textContent?.includes('Envoyer à la validation')) as HTMLButtonElement;
    expect(submit.disabled).withContext('rien ne sert d’envoyer une fiche incomplète').toBeTrue();
  });

  it('libère le bouton quand la fiche est prête', () => {
    const host = start({ missing: [] });
    const submit = Array.from(host.querySelectorAll('button'))
      .find((b) => b.textContent?.includes('Envoyer à la validation')) as HTMLButtonElement;
    expect(submit.disabled).toBeFalse();
  });

  /**
   * Une fiche en validation est gelée côté serveur. L'écran doit le montrer plutôt que de laisser
   * le coach saisir un texte qui sera refusé à l'enregistrement.
   */
  it('gèle la saisie pendant la validation, et dit pourquoi', () => {
    const host = start({ status: 'PENDING', statusLabel: 'En validation' });

    expect(host.textContent).toContain('en cours de validation');
    expect((host.querySelector('fieldset') as HTMLFieldSetElement).disabled).toBeTrue();
  });

  /** Le motif d'un renvoi est la seule chose qui dise au coach quoi corriger. */
  it('met le motif du renvoi en tête', () => {
    const host = start({ status: 'DRAFT', reviewNote: 'Merci de préciser vos tarifs.' });
    expect(host.textContent).toContain('Merci de préciser vos tarifs.');
  });

  /** Publiée, la fiche n'est plus à soumettre : elle s'ouvre ou se ferme aux demandes. */
  it('propose de fermer aux demandes quand la fiche est publiée', () => {
    const host = start({ status: 'PUBLISHED', statusLabel: 'Publiée' });

    expect(host.textContent).toContain("Elle apparaît dans l'annuaire");
    expect(Array.from(host.querySelectorAll('button')).some((b) => b.textContent?.includes("Ne plus prendre d'athlètes")))
      .toBeTrue();
    expect(Array.from(host.querySelectorAll('button')).some((b) => b.textContent?.includes('Envoyer à la validation')))
      .withContext('une fiche publiée n’est plus à soumettre')
      .toBeFalse();
  });
});
