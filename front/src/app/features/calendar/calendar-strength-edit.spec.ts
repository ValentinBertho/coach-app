import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { importProvidersFrom } from '@angular/core';
import { LucideAngularModule } from 'lucide-angular';
import { ICONS } from '../../app.config';
import { ScheduledStrength } from '../../core/models/strength.model';
import { ConfirmService } from '../../core/services/confirm.service';
import { CalendarComponent } from './calendar.component';

/**
 * Travailler une séance de préparation physique depuis le calendrier.
 *
 * <p><b>Ce que ce fichier protège.</b> Un coach de la bêta : « tu penses que ça peut être plus
 * simple pour créer des séances de prépa-physique ? et de pouvoir les modifier plus facilement ?
 * copier-coller de séance sur le calendrier. Et de pouvoir modifier le contenu directement sans
 * créer une nouvelle séance ? ». Les trois gestes existaient côté course et manquaient côté
 * force.</p>
 *
 * <p>On vérifie surtout <b>où ils aboutissent</b> : copier doit repasser par la séance affichée
 * (son snapshot), jamais par le modèle de bibliothèque — sans quoi coller une séance adaptée
 * rendrait silencieusement sa version d'origine, et une séance construite au calendrier, qui n'a
 * pas de modèle, ne se collerait pas du tout.</p>
 */
describe('calendrier — séances de renforcement', () => {
  let fixture: ComponentFixture<CalendarComponent>;
  let component: CalendarComponent;
  let http: HttpTestingController;
  let navigate: jasmine.Spy;

  const MARC = 'ath-marc';
  const DAY = '2026-07-08';
  const OTHER_DAY = '2026-07-10';

  function session(id: string, sourceSessionId: string | null): ScheduledStrength {
    return {
      id, athleteId: MARC, sourceSessionId, title: 'Full body', scheduledDate: DAY,
      originalDate: null, movedByAthlete: false, completed: false,
      sessionFatigue: null, sessionPain: null,
    };
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]), provideHttpClient(), provideHttpClientTesting(),
        importProvidersFrom(LucideAngularModule.pick(ICONS)),
      ],
    });
    fixture = TestBed.createComponent(CalendarComponent);
    component = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);

    fixture.componentRef.setInput('athleteId', MARC);
    fixture.detectChanges();
  });

  // Pas de `http.verify()` : l'initialisation du calendrier émet une dizaine d'appels (athlètes,
  // bibliothèque, éducatifs, catégories) qui n'ont rien à voir avec les gestes testés ici.

  /** Modifier le contenu d'une séance déjà posée — sans en créer une autre. */
  it('ouvre l’éditeur de structure de la séance planifiée', () => {
    component.editStrength(session('ss-1', 'lib-1'));

    expect(navigate).toHaveBeenCalledWith(
      ['/app/athletes', MARC, 'pp', 'scheduled', 'ss-1', 'structure']);
  });

  /** La copie duplique la séance affichée, pas le modèle dont elle est issue. */
  it('duplique la séance par la route qui recopie son contenu', () => {
    component.duplicateStrength(session('ss-1', 'lib-1'), OTHER_DAY);

    const req = http.expectOne((r) => r.url.includes(`/athletes/${MARC}/pp/scheduled/ss-1/copy`));
    expect(req.request.method).toBe('POST');
    expect(req.request.body.scheduledDate).toBe(OTHER_DAY);
    req.flush({ id: 'ss-2', athleteId: MARC, scheduledDate: OTHER_DAY, title: 'Full body' });
  });

  /** Elle ne repasse jamais par la bibliothèque : ce serait rendre la version d'origine. */
  it('ne replanifie pas depuis le modèle de bibliothèque', () => {
    component.duplicateStrength(session('ss-1', 'lib-1'), OTHER_DAY);

    http.expectNone((r) => r.url.includes('/pp/sessions/lib-1/schedule'));
    http.expectOne((r) => r.url.includes('/pp/scheduled/ss-1/copy'))
      .flush({ id: 'ss-2', athleteId: MARC, scheduledDate: OTHER_DAY, title: 'Full body' });
  });

  /** Une séance construite au calendrier n'a pas de modèle — et se copie quand même. */
  it('copie une séance sans modèle de bibliothèque', () => {
    component.duplicateStrength(session('ss-adhoc', null), OTHER_DAY);

    http.expectOne((r) => r.url.includes('/pp/scheduled/ss-adhoc/copy'))
      .flush({ id: 'ss-3', athleteId: MARC, scheduledDate: OTHER_DAY, title: 'Full body' });
  });

  /** Le presse-papier retient la séance, et le collage la recopie au même endroit logique. */
  it('copie puis colle par le presse-papier du calendrier', () => {
    component.strength.set([session('ss-1', 'lib-1')]);
    component.openStrengthMenu(session('ss-1', 'lib-1'), new MouseEvent('contextmenu'));
    component.ctxStrengthCopy();

    expect(component.clipboard().entries[0].id).toBe('ss-1');

    component.pasteOn(OTHER_DAY);

    http.expectOne((r) => r.url.includes('/pp/scheduled/ss-1/copy'))
      .flush({ id: 'ss-4', athleteId: MARC, scheduledDate: OTHER_DAY, title: 'Full body' });
  });

  /** Poser une séance vierge sur un jour, puis la remplir : le chemin court de la prépa physique. */
  it('crée une séance de renforcement vierge sur le jour choisi, puis ouvre son éditeur', () => {
    component.addWorkout(DAY);

    component.createAdHocStrength();

    // Le GET du calendrier vise la même URL : c'est la méthode qui distingue les deux.
    const req = http.expectOne(
      (r) => r.method === 'POST' && r.url.endsWith(`/athletes/${MARC}/pp/scheduled`));
    expect(req.request.body.date).toBe(DAY);
    req.flush({ id: 'ss-new', athleteId: MARC, scheduledDate: DAY, title: 'Séance de renforcement' });

    expect(navigate).toHaveBeenCalledWith(
      ['/app/athletes', MARC, 'pp', 'scheduled', 'ss-new', 'structure']);
  });

  /** Verser la séance en bibliothèque : le geste qui manquait pour garder une séance improvisée. */
  it('verse la séance du calendrier dans la bibliothèque', async () => {
    const confirm = TestBed.inject(ConfirmService);
    const done = component.saveStrengthToLibrary(session('ss-1', null));

    // L'invite de nom est une modale du produit, pas un prompt natif : on répond à sa place.
    expect(confirm.pending()).withContext('le nom du modèle est demandé').not.toBeNull();
    confirm.answer(true, 'Full body bas du corps');
    await done;

    const req = http.expectOne((r) => r.url.includes('/pp/scheduled/ss-1/save-as-session'));
    expect(req.request.body.name).toBe('Full body bas du corps');
    req.flush({ id: 'lib-9', name: 'Full body bas du corps', structure: { blocks: [] } });
  });

  /** Renommer la séance de l'athlète : son modèle de bibliothèque n'y est pour rien. */
  it('renomme la séance planifiée sans toucher à son contenu', async () => {
    const confirm = TestBed.inject(ConfirmService);
    component.strength.set([session('ss-1', 'lib-1')]);

    const done = component.renameStrength(session('ss-1', 'lib-1'));
    confirm.answer(true, 'Full body - bas du corps');
    await done;

    const req = http.expectOne((r) => r.url.includes('/pp/scheduled/ss-1/title'));
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body.title).toBe('Full body - bas du corps');
    req.flush({ id: 'ss-1', athleteId: MARC, scheduledDate: DAY, title: 'Full body - bas du corps' });
    expect(component.strength()[0].title).toBe('Full body - bas du corps');
  });
});
