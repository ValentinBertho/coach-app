import { importProvidersFrom } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { ICONS } from '../../../app.config';
import { SessionLibraryPanelComponent } from './session-library-panel.component';
import { WorkoutTemplate } from '../../../core/models/workout-template.model';

/**
 * Le panneau bibliothèque est une <b>source</b> de glisser-déposer : c'est le geste par lequel un
 * coach remplit son calendrier.
 *
 * <p><b>Le défaut que ces tests verrouillent.</b> Les listes « Favoris » et « Fréquentes »
 * portaient des {@code cdkDrag} sans {@code cdkDropList} parent. Dans le CDK, un
 * {@code cdkDrag} orphelin est un élément à déplacement libre : il n'entre dans aucun
 * {@code cdkDropListGroup}, donc le {@code cdkDropListDropped} du calendrier ne se déclenche
 * jamais. La séance suivait le curseur, revenait à sa place, et rien n'était planifié.</p>
 *
 * <p>C'est ce qui donnait « ne fonctionne pas <i>toujours</i> » : le geste marchait depuis les
 * accordéons de catégorie — qui, eux, avaient leur {@code cdkDropList} — et échouait depuis les
 * deux listes du haut. C'est-à-dire précisément sur les séances qu'un coach utilise le plus,
 * puisque ce sont ses favorites et ses plus fréquentes.</p>
 *
 * <p>Le test raisonne sur le DOM rendu plutôt que sur le gabarit : le CDK marque ses éléments de
 * {@code .cdk-drag} et {@code .cdk-drop-list}, si bien qu'un {@code cdkDrag} sans ancêtre
 * {@code .cdk-drop-list} est exactement le défaut, quelle que soit la façon dont le gabarit est
 * écrit ou réorganisé plus tard.</p>
 */
describe('SessionLibraryPanelComponent — glisser-déposer', () => {
  let fixture: ComponentFixture<SessionLibraryPanelComponent>;
  /** `nativeElement` est typé `any` : on le fixe une fois pour garder les requêtes DOM typées. */
  let host: HTMLElement;

  function template(id: string, name: string, favorite: boolean, useCount = 0): WorkoutTemplate {
    return { id, name, favorite, useCount } as unknown as WorkoutTemplate;
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SessionLibraryPanelComponent],
      providers: [
        provideRouter([]),
        // Le panneau rend des icônes : sans fournisseur, Lucide lève au premier cycle de
        // détection et masque ce que le test cherche réellement à vérifier.
        importProvidersFrom(LucideAngularModule.pick(ICONS)),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(SessionLibraryPanelComponent);
    host = fixture.nativeElement as HTMLElement;
  });

  /** Tout item déplaçable doit vivre dans une liste de dépôt, sans exception. */
  it('rattache chaque séance déplaçable à une liste de dépôt', () => {
    fixture.componentRef.setInput('courseTemplates', [
      template('t1', 'Sortie longue', true, 12),
      template('t2', '10 × 400', false, 9),
      template('t3', 'Footing', false, 0),
    ]);
    fixture.detectChanges();

    const drags = Array.from(host.querySelectorAll<HTMLElement>('.cdk-drag'));
    expect(drags.length).withContext('des items déplaçables sont rendus').toBeGreaterThan(0);

    const orphans = drags.filter((el) => !el.closest('.cdk-drop-list'));
    const names = orphans.map((el) => el.textContent?.trim() ?? '?');
    expect(orphans.length)
      .withContext(`items déplaçables hors de toute liste de dépôt : ${names.join(', ')}`)
      .toBe(0);
  });

  /**
   * Le cas précis du défaut : une séance favorite apparaît deux fois — dans « Favoris » et dans
   * sa catégorie. Seule la seconde était réellement déposable, ce qui rendait le symptôme
   * intermittent aux yeux du coach.
   */
  it('rend la séance favorite déposable depuis la liste des favoris', () => {
    fixture.componentRef.setInput('courseTemplates', [template('t1', 'Sortie longue', true)]);
    fixture.detectChanges();

    const favoritesList = host.querySelector<HTMLElement>('.slp-quick .lib-list');
    expect(favoritesList).withContext('la liste des favoris est rendue').not.toBeNull();
    expect(favoritesList!.classList.contains('cdk-drop-list'))
      .withContext('la liste des favoris est une liste de dépôt')
      .toBe(true);
  });
});
