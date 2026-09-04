import { importProvidersFrom } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { ICONS } from '../../app.config';
import { CoachFacets, CoachSummary } from '../../core/services/coach-directory.service';
import { CoachDirectoryComponent } from './coach-directory.component';

/**
 * L'annuaire public.
 *
 * <p>Ce que ces tests interdisent, et c'est tout l'enjeu du lancement à dix coachs : qu'un
 * visiteur arrive sur une liste vide et en conclue que la plateforme l'est. Deux mécanismes le
 * garantissent, et ils sont vérifiés ici — un filtre qui ne rendrait rien ne se propose pas, et
 * une combinaison sans résultat retombe sur des coachs disponibles <b>en disant pourquoi</b>.</p>
 */
describe('annuaire — un visiteur ne tombe jamais sur un cul-de-sac', () => {
  let fixture: ComponentFixture<CoachDirectoryComponent>;
  let http: HttpTestingController;

  const coach: CoachSummary = {
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
  };

  const facets: CoachFacets = {
    disciplines: [{ value: 'ROUTE', label: 'Route', count: 1 }],
    specialties: [
      { value: 'MARATHON', label: 'Marathon', count: 1 },
      { value: 'TRIATHLON', label: 'Triathlon', count: 0 },
    ],
    languages: [{ value: 'fr', label: 'Français', count: 1 }],
    cities: [{ value: 'Lyon', label: 'Lyon', count: 1 }],
    total: 1,
    accepting: 1,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [CoachDirectoryComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        importProvidersFrom(LucideAngularModule.pick(ICONS)),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(CoachDirectoryComponent);
  });

  afterEach(() => http.verify());

  /** Démarre l'écran sur une première recherche au résultat donné. */
  function start(results: CoachSummary[]): HTMLElement {
    fixture.detectChanges();
    http.expectOne((r) => r.url.endsWith('/public/coach-facets')).flush(facets);
    http.expectOne((r) => r.url.endsWith('/public/coaches')).flush(page(results));
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  function page(content: CoachSummary[]) {
    return { content, page: 0, size: 12, totalElements: content.length, totalPages: 1 };
  }

  function chip(host: HTMLElement, label: string): HTMLButtonElement {
    return Array.from(host.querySelectorAll('.dir__chip'))
      .find((b) => b.textContent?.includes(label)) as HTMLButtonElement;
  }

  /**
   * Le premier garde-fou. Grisé et non masqué : une facette qui disparaît laisse croire qu'elle
   * n'existe pas, alors qu'elle est seulement vide aujourd'hui.
   */
  it('grise un filtre qui ne rendrait aucun résultat, sans le masquer', () => {
    const host = start([coach]);

    expect(chip(host, 'Marathon').disabled).withContext('un coach le propose').toBeFalse();
    expect(chip(host, 'Triathlon')).withContext('la facette reste visible').toBeTruthy();
    expect(chip(host, 'Triathlon').disabled).withContext('personne ne le propose').toBeTrue();
  });

  /** Le compte est affiché : c'est ce qui rend le grisage compréhensible plutôt qu'arbitraire. */
  it('affiche le nombre de coachs derrière chaque filtre', () => {
    const host = start([coach]);
    expect(chip(host, 'Marathon').textContent).toContain('1');
    expect(chip(host, 'Triathlon').textContent).toContain('0');
  });

  /**
   * Le second garde-fou. Une combinaison peut ne rien rendre sans qu'aucun filtre ne soit vide :
   * l'écran doit alors dire que la recherche n'a rien donné, puis proposer autre chose.
   */
  it('retombe sur des coachs disponibles, en disant pourquoi, quand une recherche ne rend rien', () => {
    const host = start([coach]);

    chip(host, 'Marathon').click();
    fixture.detectChanges();
    http.expectOne((r) => r.url.endsWith('/public/coaches')).flush(page([]));
    fixture.detectChanges();
    http.expectOne((r) => r.url.endsWith('/public/coach-suggestions')).flush(page([coach]));
    fixture.detectChanges();

    expect(host.textContent)
      .withContext('dire que la recherche a échoué AVANT de proposer autre chose')
      .toContain('Aucun coach ne correspond');
    expect(host.textContent).toContain('ceux qui prennent des athlètes');
    expect(host.querySelectorAll('.dir__card').length)
      .withContext('le visiteur repart avec quelque chose')
      .toBe(1);
  });

  /** Sans filtre, un annuaire vide dit qu'il ouvre bientôt — il ne se justifie pas d'un filtre. */
  it('distingue « aucun coach publié » de « aucun résultat pour ce filtre »', () => {
    const host = start([]);
    expect(host.textContent).toContain("L'annuaire ouvre bientôt");
    expect(host.textContent).not.toContain('Aucun coach ne correspond');
  });

  /** Le tarif affiché est le même que celui qui filtre : « à partir de », ramené au mois. */
  it('affiche le tarif d’entrée du coach', () => {
    const host = start([coach]);
    expect(host.textContent).toContain('à partir de 90 € / mois');
  });
});
