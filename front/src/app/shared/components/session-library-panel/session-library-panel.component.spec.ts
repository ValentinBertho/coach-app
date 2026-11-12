import { importProvidersFrom } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { ICONS } from '../../../app.config';
import { SessionLibraryPanelComponent } from './session-library-panel.component';
import { WorkoutTemplate } from '../../../core/models/workout-template.model';
import { StrengthSession } from '../../../core/models/strength.model';
import { SessionCategory } from '../../../core/models/session-category.model';

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
    return { id, name, favorite, useCount, categoryId: null } as unknown as WorkoutTemplate;
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

/**
 * Trier avant de lire.
 *
 * <p><b>Ce que ce fichier protège.</b> Un coach bêta : « quand la bibliothèque contient
 * énormément de séances il faut beaucoup scroller, on pourrait réfléchir à un système de filtre /
 * dropdown catégorie ». Le panneau n'avait qu'une recherche par nom — inutile quand on ne sait pas
 * encore quelle séance on veut, ce qui est précisément le moment où l'on parcourt.</p>
 */
describe('SessionLibraryPanelComponent — filtres', () => {
  let fixture: ComponentFixture<SessionLibraryPanelComponent>;
  let component: SessionLibraryPanelComponent;
  let host: HTMLElement;

  function template(id: string, name: string, categoryId: string | null = null): WorkoutTemplate {
    return { id, name, favorite: false, useCount: 0, categoryId } as unknown as WorkoutTemplate;
  }

  function strengthSession(id: string, name: string, categoryId: string | null = null): StrengthSession {
    return { id, name, categoryId, favorite: false, useCount: 0, structure: { blocks: [] } } as unknown as StrengthSession;
  }

  function category(id: string, name: string, domain: 'COURSE' | 'STRENGTH' | 'DRILL',
                    parentId: string | null = null): SessionCategory {
    return { id, name, domain, parentId, discipline: null, sortOrder: 0 };
  }

  function names(): string[] {
    return Array.from(host.querySelectorAll<HTMLElement>('.lib-item .li-name'))
      .map((el) => el.textContent?.trim() ?? '');
  }

  beforeEach(async () => {
    // Le repli des accordéons est mémorisé dans `localStorage` — donc partagé par les tests du
    // même navigateur. Sans ce ménage, un test qui replie une catégorie en cache le contenu au
    // suivant, et l'échec désigne le mauvais coupable.
    try { localStorage.removeItem('coach-lib-collapsed'); } catch { /* indisponible : rien à nettoyer */ }

    await TestBed.configureTestingModule({
      imports: [SessionLibraryPanelComponent],
      providers: [provideRouter([]), importProvidersFrom(LucideAngularModule.pick(ICONS))],
    }).compileComponents();

    fixture = TestBed.createComponent(SessionLibraryPanelComponent);
    component = fixture.componentInstance;
    host = fixture.nativeElement as HTMLElement;

    fixture.componentRef.setInput('categories', [
      category('c-seuil', 'Seuil', 'COURSE'),
      category('c-seuil-long', 'Seuil long', 'COURSE', 'c-seuil'),
      category('s-bas', 'Bas du corps', 'STRENGTH'),
    ]);
    fixture.componentRef.setInput('courseTemplates', [
      template('t1', 'Seuil 3x8', 'c-seuil'),
      template('t2', 'Seuil 2x20', 'c-seuil-long'),
      template('t3', 'Footing'),
    ]);
    fixture.componentRef.setInput('strengthSessions', [
      strengthSession('s1', 'Full body', 's-bas'),
      strengthSession('s2', 'Gainage'),
    ]);
    fixture.detectChanges();
  });

  /** Le premier tri : une famille, et la bibliothèque est divisée. */
  it('ne garde que la famille choisie', () => {
    component.setFamily('strength');
    fixture.detectChanges();

    expect(names().sort()).toEqual(['Full body', 'Gainage']);
  });

  /** Changer de famille ne doit pas garder une catégorie qui appartenait à l'autre arbre. */
  it('oublie la catégorie quand la famille change', () => {
    component.setFamily('course');
    component.categoryId.set('c-seuil');
    fixture.detectChanges();

    component.setFamily('strength');
    fixture.detectChanges();

    expect(component.categoryId()).toBe('');
    expect(names().sort()).toEqual(['Full body', 'Gainage']);
  });

  /** Une catégorie parente contient ses sous-catégories : « Seuil » rapporte « Seuil long ». */
  it('inclut les sous-catégories dans le filtre', () => {
    component.categoryId.set('c-seuil');
    fixture.detectChanges();

    expect(names().sort()).toEqual(['Seuil 2x20', 'Seuil 3x8']);
  });

  /** Les séances de force se rangent enfin : elles n'avaient aucune catégorie avant. */
  it('range les séances de force par catégorie', () => {
    component.setFamily('strength');
    component.categoryId.set('s-bas');
    fixture.detectChanges();

    expect(names()).toEqual(['Full body']);
  });

  /** « Sans catégorie » est un filtre comme un autre : c'est le tas qu'on veut ranger. */
  it('isole ce qui n’est pas encore rangé', () => {
    component.categoryId.set(component.NO_CATEGORY_ID);
    fixture.detectChanges();

    expect(names().sort()).toEqual(['Footing', 'Gainage']);
  });

  /** Le menu ne propose que des catégories qui rapporteraient quelque chose, avec leur compte. */
  it('ne propose que les catégories qui contiennent quelque chose', () => {
    fixture.componentRef.setInput('categories', [
      category('c-seuil', 'Seuil', 'COURSE'),
      category('c-vide', 'Côtes', 'COURSE'),
    ]);
    fixture.detectChanges();

    const ids = component.categoryChoices().map((c) => c.id);
    expect(ids).toContain('c-seuil');
    expect(ids).not.toContain('c-vide');
    expect(component.categoryChoices().find((c) => c.id === 'c-seuil')?.count).toBe(1);
  });

  /** La recherche ignore les accents : personne ne tape « Fractionné » avec son accent. */
  it('cherche sans tenir compte des accents', () => {
    fixture.componentRef.setInput('courseTemplates', [template('t9', 'Fractionné court')]);
    component.search.set('fractionne');
    fixture.detectChanges();

    expect(names()).toEqual(['Fractionné court']);
  });

  /**
   * Le pire des deux mondes, corrigé : la catégorie repliée affichait son compte de résultats
   * mais gardait la séance trouvée derrière le clic qu'on venait d'économiser.
   */
  it('ouvre les accordéons repliés dès qu’un filtre est posé', () => {
    component.toggleGroup('course:c-seuil');
    fixture.detectChanges();
    expect(names()).not.toContain('Seuil 3x8');

    component.search.set('seuil 3');
    fixture.detectChanges();

    expect(names()).toContain('Seuil 3x8');
  });

  /** Le filtre ne doit pas sortir un item de sa liste de dépôt : le glisser-déposer en dépend. */
  it('garde les items filtrés déposables', () => {
    component.setFamily('strength');
    fixture.detectChanges();

    const drags = Array.from(host.querySelectorAll<HTMLElement>('.cdk-drag'));
    expect(drags.length).toBeGreaterThan(0);
    expect(drags.filter((el) => !el.closest('.cdk-drop-list')).length).toBe(0);
  });

  /** Et l'on peut toujours tout ré-afficher d'un geste. */
  it('efface tous les filtres', () => {
    component.setFamily('course');
    component.categoryId.set('c-seuil');
    component.search.set('seuil');
    fixture.detectChanges();

    component.clearFilters();
    fixture.detectChanges();

    expect(component.filtersActive()).toBe(false);
    expect(names().length).toBe(5);
  });
});
