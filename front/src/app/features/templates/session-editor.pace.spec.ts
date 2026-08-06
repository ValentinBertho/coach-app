import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { CourseBlock } from '../../core/models/course.model';
import { SessionEditorComponent, parseNumber, volumeText } from './session-editor.component';

/**
 * Allure d'intervalle sur mesure dans l'éditeur de séance.
 *
 * <p>Un bloc ne pouvait porter qu'une <em>zone</em> du club. Or les zones sont une échelle de
 * référence commune : on ne les retouche pas pour un fractionné particulier. « 6 × 1000 à
 * 102–106 % de VC » n'avait donc nulle part où s'écrire, alors que le moteur calcule ce couple
 * référentiel + % depuis toujours.</p>
 */
describe('session-editor — allure en % d’un référentiel', () => {
  let component: SessionEditorComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    component = TestBed.createComponent(SessionEditorComponent).componentInstance;
  });

  function block(type: string, zoneId: string | null = 'z-vo2'): CourseBlock {
    return { id: 'b1', type, reps: 6, distanceM: 1000, prescription: { zoneId } };
  }

  it('part d’une fourchette plausible pour le type de bloc', () => {
    const b = block('intervals');
    component.switchToPct(b);
    expect(component.usesPct(b)).toBeTrue();
    expect(b.prescription!.ref).toBe('PCT_VC');
    expect(b.prescription!.minPct).toBe(102);
    expect(b.prescription!.maxPct).toBe(108);
  });

  /** La zone primerait sur le % côté serveur : la laisser en place viderait le geste de son sens. */
  it('efface la zone en passant en % et la retrouve au retour', () => {
    const b = block('threshold', 'z-seuil');
    component.switchToPct(b);
    expect(b.prescription!.zoneId).toBeNull();
    expect(b.prescription!.custom).toBeTrue();

    component.switchToZone(b);
    expect(b.prescription!.zoneId).toBe('z-seuil');
    expect(component.usesPct(b)).toBeFalse();
  });

  it('respecte les bornes acceptées par le serveur', () => {
    const b = block('intervals');
    component.switchToPct(b);
    component.setPctBound(b, 'max', 400);
    expect(b.prescription!.maxPct).toBe(150);
    component.setPctBound(b, 'min', 5);
    expect(b.prescription!.minPct).toBe(30);
  });

  /** Une borne basse au-dessus de la haute fait refuser le calcul : la cible disparaîtrait sans un mot. */
  it('pousse l’autre borne plutôt que de laisser une fourchette inversée', () => {
    const b = block('intervals');
    component.switchToPct(b);
    component.setPctBound(b, 'min', 120);
    expect(b.prescription!.maxPct).toBe(120);

    component.setPctBound(b, 'max', 90);
    expect(b.prescription!.minPct).toBe(90);
  });

  it('affiche le référentiel en abrégé à côté des bornes', () => {
    const b = block('tempo');
    component.switchToPct(b);
    expect(component.pctRefShort(b)).toBe('LT2');
    component.setPctRef(b, 'PCT_PACE_5KM');
    expect(component.pctRefShort(b)).toBe('5 km');
  });

  /** Retour à la bibliothèque après enregistrement : sur la catégorie de la séance, pas la vue globale. */
  it('renvoie à la bibliothèque sur la catégorie de la séance', () => {
    expect(component.libraryQueryParams()).toEqual({ cat: null });
    component.setCategory('cat-seuil');
    expect(component.libraryQueryParams()).toEqual({ cat: 'cat-seuil' });
  });
});

/**
 * Saisie d'un volume : « 1:30 » autant que « 90 ».
 *
 * <p>Le champ n'acceptait qu'un nombre dans l'unité choisie à côté. Écrire une récup de 1'30
 * supposait donc de convertir en 90 et de penser à basculer l'unité en secondes — et le premier
 * écran venu réaffichait « 2 min ». Ces règles fixent l'aller-retour saisie ↔ affichage.</p>
 */
describe('volume d’un bloc (saisie et affichage)', () => {
  it('écrit une durée à la minute pleine comme un nombre', () => {
    expect(volumeText(600, null, 'min')).toBe('10');
    expect(volumeText(90, null, 's')).toBe('90');
  });

  it('écrit une durée qui déborde de la minute en m:ss', () => {
    expect(volumeText(90, null, 'min')).toBe('1:30');
    expect(volumeText(3630, null, 'min')).toBe('60:30');
  });

  it('laisse les distances telles quelles', () => {
    expect(volumeText(null, 400, 'm')).toBe('400');
    expect(volumeText(null, 5000, 'km')).toBe('5');
  });

  it('rend une chaîne vide quand il n’y a rien à afficher', () => {
    expect(volumeText(null, null, 'min')).toBe('');
  });

  it('lit un nombre, virgule française comprise', () => {
    expect(parseNumber('12')).toBe(12);
    expect(parseNumber('1,5')).toBe(1.5);
  });

  it('distingue « effacer » (null) d’une saisie illisible (undefined)', () => {
    expect(parseNumber('')).toBeNull();
    expect(parseNumber('abc')).toBeUndefined();
    expect(parseNumber('-3')).toBeUndefined();
  });
});
