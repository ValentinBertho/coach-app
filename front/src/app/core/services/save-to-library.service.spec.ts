import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { SessionCategory } from '../models/session-category.model';
import { AuthService } from './auth.service';
import { ConfirmService } from './confirm.service';
import { SaveToLibraryService } from './save-to-library.service';
import { ToastService } from './toast.service';

/**
 * Ce que ces cas protègent : le <b>rangement</b> du modèle versé depuis une séance.
 *
 * <p>Le geste existait sans catégorie — un coach qui verse une adaptation en bibliothèque la
 * retrouvait au milieu de tout, et la classer ensuite supposait de l'avoir d'abord retrouvée. Le
 * choix se demande donc dans la modale du nom. Restait à s'assurer que la catégorie retenue part
 * bien dans la requête : un `<select>` qui ne décide de rien ne casse aucun écran, il range
 * simplement ailleurs que là où on a demandé.</p>
 */
describe('SaveToLibraryService', () => {
  let service: SaveToLibraryService;
  let confirm: ConfirmService;
  let http: HttpTestingController;
  const club = `${environment.apiUrl}/clubs/club-1`;
  const workout = { id: 'w-1', athleteId: 'a-1', title: '6x1000 m' };

  const categories: SessionCategory[] = [
    { id: 'cat-vma', name: 'VMA', domain: 'COURSE', parentId: null, discipline: null, sortOrder: 0 },
    { id: 'cat-court', name: 'Courte', domain: 'COURSE', parentId: 'cat-vma', discipline: null, sortOrder: 0 },
  ];

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(), provideHttpClientTesting(),
        { provide: AuthService, useValue: { clubId: () => 'club-1' } },
        { provide: ToastService, useValue: { success: () => undefined, error: () => undefined } },
        SaveToLibraryService,
      ],
    });
    service = TestBed.inject(SaveToLibraryService);
    confirm = TestBed.inject(ConfirmService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('propose les catégories du club et transmet celle qui est retenue', fakeAsync(() => {
    void service.promptAndSave(workout);
    tick();

    http.expectOne(`${club}/session-categories?domain=COURSE`).flush(categories);
    tick();

    const pending = confirm.pending();
    expect(pending?.selectOptions?.map((o) => o.value)).toEqual(['cat-vma', 'cat-court']);
    // Une sous-catégorie reste lisible dans une liste plate : elle est indentée, pas aplatie.
    expect(pending?.selectOptions?.[1].label).toContain('↳ Courte');
    expect(pending?.initialValue).toBe('6x1000 m');

    confirm.answer(true, 'VMA 6x1000', false, 'cat-court');
    tick();

    const req = http.expectOne(`${club}/athletes/a-1/workouts/w-1/save-as-template`);
    expect(req.request.body).toEqual({ title: 'VMA 6x1000', categoryId: 'cat-court' });
    req.flush({});
  }));

  it('verse sans catégorie quand le coach n\'en choisit pas', fakeAsync(() => {
    void service.promptAndSave(workout);
    tick();
    http.expectOne(`${club}/session-categories?domain=COURSE`).flush(categories);
    tick();

    confirm.answer(true, 'VMA 6x1000', false, null);
    tick();

    const req = http.expectOne(`${club}/athletes/a-1/workouts/w-1/save-as-template`);
    expect(req.request.body.categoryId).toBeNull();
    req.flush({});
  }));

  it('enregistre quand même si les catégories ne se chargent pas', fakeAsync(() => {
    void service.promptAndSave(workout);
    tick();
    http.expectOne(`${club}/session-categories?domain=COURSE`)
      .flush('boom', { status: 500, statusText: 'Server Error' });
    tick();

    // Pas de liste à proposer : l'invite reste celle d'avant, et le versement aboutit.
    expect(confirm.pending()?.selectOptions).toBeUndefined();
    confirm.answer(true, 'VMA 6x1000');
    tick();

    http.expectOne(`${club}/athletes/a-1/workouts/w-1/save-as-template`).flush({});
  }));

  it('ne verse rien si le coach renonce', fakeAsync(() => {
    void service.promptAndSave(workout);
    tick();
    http.expectOne(`${club}/session-categories?domain=COURSE`).flush(categories);
    tick();

    confirm.answer(false);
    tick();

    http.expectNone(`${club}/athletes/a-1/workouts/w-1/save-as-template`);
  }));
});
