import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { TemplateListComponent } from './template-list.component';

/**
 * Catégorie de travail de la bibliothèque.
 *
 * <p>Le filtre ne vivait que dans un signal local : partir dans l'éditeur puis revenir le remettait
 * à « toutes catégories ». Un coach qui étoffe sa catégorie « Seuil » re-filtrait donc après chaque
 * séance créée — et la séance elle-même atterrissait « sans catégorie » alors que le contexte
 * disait où elle allait.</p>
 */
describe('template-list — catégorie de travail', () => {
  function create(cat: string) {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    const fixture = TestBed.createComponent(TemplateListComponent);
    fixture.componentRef.setInput('cat', cat);
    fixture.componentInstance.ngOnInit();
    return fixture.componentInstance;
  }

  afterEach(() => TestBed.resetTestingModule());

  it('reprend la catégorie portée par l’URL', () => {
    expect(create('cat-seuil').categoryFilter()).toBe('cat-seuil');
  });

  it('range la nouvelle séance dans la catégorie consultée', () => {
    const component = create('cat-seuil');
    component.toggleForm();
    expect(component.form.getRawValue().categoryId).toBe('cat-seuil');
  });

  /** « Sans catégorie » est un filtre, pas une catégorie : rien à pré-remplir. */
  it('ne pré-remplit rien depuis « sans catégorie »', () => {
    const component = create('none');
    component.toggleForm();
    expect(component.form.getRawValue().categoryId).toBe('');
  });

  it('ne pré-remplit rien depuis la vue globale', () => {
    const component = create('');
    component.toggleForm();
    expect(component.form.getRawValue().categoryId).toBe('');
  });
});
