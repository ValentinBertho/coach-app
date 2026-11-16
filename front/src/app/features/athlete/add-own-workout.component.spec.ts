import { importProvidersFrom } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LucideAngularModule } from 'lucide-angular';
import { ICONS } from '../../app.config';
import { AddOwnWorkoutComponent } from './add-own-workout.component';

/** L'athlète ajoute lui-même une séance : à la main toujours, depuis son catalogue sans coach. */
describe('ajouter sa propre séance', () => {
  let fixture: ComponentFixture<AddOwnWorkoutComponent>;
  let http: HttpTestingController;

  function setUp(withCatalog: boolean): void {
    TestBed.configureTestingModule({
      imports: [AddOwnWorkoutComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        importProvidersFrom(LucideAngularModule.pick(ICONS)),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AddOwnWorkoutComponent);
    fixture.componentRef.setInput('date', '2026-12-01');
    fixture.componentRef.setInput('withCatalog', withCatalog);
    fixture.detectChanges();
  }

  afterEach(() => http.verify());

  it('suivi par un coach : pas de catalogue, la saisie à la main part avec la date du jour', () => {
    setUp(false);
    const c = fixture.componentInstance;
    c.start();
    http.expectNone((r) => r.url.endsWith('/me/catalog'));
    expect(c.tab()).toBe('manual');

    c.title = 'Sortie du club';
    c.minutes = 45;
    c.km = 8.5;
    let added = '';
    c.added.subscribe((w) => (added = w.id));
    c.save();

    const req = http.expectOne((r) => r.url.endsWith('/me/own-workouts'));
    expect(req.request.body).toEqual(jasmine.objectContaining({
      scheduledDate: '2026-12-01', title: 'Sortie du club', targetDurationS: 2700, targetDistanceM: 8500,
    }));
    req.flush({ id: 'w1', createdByAthlete: true });
    expect(added).toBe('w1');
    expect(c.open()).toBeFalse();
  });

  it('sans coach : le catalogue est proposé, et une séance s’y pose en un geste', () => {
    setUp(true);
    const c = fixture.componentInstance;
    c.start();
    http.expectOne((r) => r.url.endsWith('/me/catalog')).flush({
      content: [{ id: 't1', name: 'Seuil 3 × 10 min', title: 'Seuil', type: 'THRESHOLD', steps: [] }],
      page: 0, size: 200, totalElements: 1, totalPages: 1,
    });
    expect(c.filtered().length).toBe(1);

    c.pick(c.filtered()[0]);
    const req = http.expectOne((r) => r.url.endsWith('/me/catalog/t1/schedule'));
    expect(req.request.params.get('date')).toBe('2026-12-01');
    req.flush({ id: 'w2', createdByAthlete: true });
  });
});
