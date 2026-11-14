import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { firstValueFrom, Observable } from 'rxjs';
import { SessionEditorComponent } from './session-editor.component';

/**
 * « Adapter » une séance posée : les notes et l'effort attendu s'enregistrent sur CETTE séance.
 *
 * <p>Les deux champs étaient affichés dans l'éditeur d'adaptation mais jamais envoyés : la
 * sauvegarde automatique ne transportait que la structure et le titre. Une consigne écrite pour
 * un athlète disparaissait sans un mot.</p>
 */
describe('session-editor — consignes d\'une séance planifiée', () => {
  let component: SessionEditorComponent;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    const fixture = TestBed.createComponent(SessionEditorComponent);
    fixture.componentRef.setInput('athleteId', 'a1');
    fixture.componentRef.setInput('workoutId', 'w1');
    component = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    // État après chargement de la séance : consignes et titre déjà connus du serveur.
    component.title.set('4 x 1000');
    component.notes.set('Consigne du modele');
    component.targetRpe.set(7);
    const c = component as unknown as { persistedTitle: string; persistedInstructions: string; instructionsKey(): string };
    c.persistedTitle = '4 x 1000';
    c.persistedInstructions = c.instructionsKey();
  });

  afterEach(() => http.verify());

  function persist(): Promise<unknown> {
    return firstValueFrom((component as unknown as { persist(): Observable<unknown> }).persist());
  }

  it('enregistre les notes et l\'effort sur la séance, pas sur le modèle', async () => {
    component.setNotes('Attention au depart');
    component.setTargetRpe(5);

    const done = persist();
    http.expectOne((r) => r.method === 'PUT' && r.url.endsWith('/athletes/a1/workouts/w1/structure')).flush({});
    const patch = http.expectOne((r) => r.url.endsWith('/athletes/a1/workouts/w1/instructions'));
    expect(patch.request.method).toBe('PATCH');
    expect(patch.request.body).toEqual({ notes: 'Attention au depart', targetRpe: 5 });
    patch.flush({});
    await done;

    http.expectNone((r) => r.url.includes('workout-templates'));
  });

  it('n\'envoie pas les consignes quand elles n\'ont pas changé', async () => {
    const done = persist();
    http.expectOne((r) => r.url.endsWith('/workouts/w1/structure')).flush({});
    await done;
    http.expectNone((r) => r.url.endsWith('/instructions'));
  });

  it('un effort effacé part à zéro, ce que le serveur lit comme « rien d\'annoncé »', async () => {
    component.setTargetRpe(0);

    const done = persist();
    http.expectOne((r) => r.url.endsWith('/workouts/w1/structure')).flush({});
    const patch = http.expectOne((r) => r.url.endsWith('/instructions'));
    expect(patch.request.body).toEqual({ notes: 'Consigne du modele', targetRpe: 0 });
    patch.flush({});
    await done;
  });
});
