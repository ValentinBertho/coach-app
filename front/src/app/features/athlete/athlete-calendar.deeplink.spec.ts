import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { AthleteCalendarComponent } from './athlete-calendar.component';

/**
 * Arrivée depuis une notification : le calendrier s'ouvre sur la date annoncée.
 *
 * <p>« Nouvelle séance — mercredi 19 août » renvoyait sur l'écran « Aujourd'hui », où la séance
 * du 19 n'est évidemment pas. L'athlète tapait la notification et ne trouvait rien de ce qu'elle
 * annonçait — le pire résultat possible pour un canal dont tout l'intérêt est de faire revenir
 * quelqu'un dans l'application.</p>
 */
describe("athlete-calendar — ouverture sur la date d'une notification", () => {
  function componentWith(date: string | null): AthleteCalendarComponent {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]), provideHttpClient(), provideHttpClientTesting(),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap(date ? { date } : {}) } },
        },
      ],
    });
    const component = TestBed.createComponent(AthleteCalendarComponent).componentInstance;
    component.ngOnInit();
    return component;
  }

  it("cale l'agenda sur la date passée en paramètre", () => {
    const component = componentWith('2026-08-19');

    const anchor = component.anchor();
    expect(anchor.getFullYear()).toBe(2026);
    expect(anchor.getMonth()).toBe(7); // août
    expect(anchor.getDate()).toBe(19);
  });

  /**
   * Construction locale et non `new Date('2026-08-19')`, qui est lu en UTC : à l'ouest de
   * Greenwich la séance annoncée basculerait sur la veille.
   */
  it('ne décale pas la date au fuseau du navigateur', () => {
    expect(componentWith('2026-01-01').anchor().getDate()).toBe(1);
    expect(componentWith('2026-12-31').anchor().getMonth()).toBe(11);
  });

  it('reste sur le mois courant sans paramètre', () => {
    const today = new Date();

    const anchor = componentWith(null).anchor();

    expect(anchor.getMonth()).toBe(today.getMonth());
  });

  /** Un paramètre abîmé ne doit jamais empêcher le calendrier de s'afficher. */
  it('ignore une date illisible', () => {
    const today = new Date();

    const anchor = componentWith('pas-une-date').anchor();

    expect(anchor.getMonth()).toBe(today.getMonth());
  });
});
