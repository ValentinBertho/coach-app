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

  /** Champ de saisie, tel que le gabarit le passe au composant à la validation. */
  function field(value: string): HTMLInputElement {
    const input = document.createElement('input');
    input.value = value;
    return input;
  }

  it('respecte les bornes acceptées par le serveur', () => {
    const b = block('intervals');
    component.switchToPct(b);
    component.setPctBoundText(b, 'max', field('400'));
    expect(b.prescription!.maxPct).toBe(150);
    component.setPctBoundText(b, 'min', field('5'));
    expect(b.prescription!.minPct).toBe(30);
  });

  /**
   * Le défaut remonté en bêta (« des fois ça déconne quand je rentre les % »). La borne était
   * repliée dans [30, 150] à <b>chaque frappe</b> : taper « 102 » écrivait 30 dès le « 1 », la
   * suite de la saisie se collait derrière, et la borne haute suivait le mouvement. Une saisie
   * intermédiaire ne doit rien écrire du tout — seule la valeur validée compte.
   */
  it('n’écrit qu’à la validation du champ, pas pendant la frappe', () => {
    const b = block('intervals');
    component.switchToPct(b);

    component.setPctBoundText(b, 'min', field('102'));

    expect(b.prescription!.minPct).toBe(102);
    expect(b.prescription!.maxPct).toBe(108);
  });

  /** Un champ vidé ou illisible ne remplace pas la prescription par une valeur inventée. */
  it('ignore une saisie vide ou illisible et réaffiche la valeur en place', () => {
    const b = block('intervals');
    component.switchToPct(b);

    const empty = field('');
    component.setPctBoundText(b, 'min', empty);
    expect(b.prescription!.minPct).toBe(102);
    expect(empty.value).toBe('102');

    component.setPctBoundText(b, 'min', field('abc'));
    expect(b.prescription!.minPct).toBe(102);
  });

  /** Une borne basse au-dessus de la haute fait refuser le calcul : la cible disparaîtrait sans un mot. */
  it('pousse l’autre borne plutôt que de laisser une fourchette inversée', () => {
    const b = block('intervals');
    component.switchToPct(b);
    component.setPctBoundText(b, 'min', field('120'));
    expect(b.prescription!.maxPct).toBe(120);

    component.setPctBoundText(b, 'max', field('90'));
    expect(b.prescription!.minPct).toBe(90);
  });

  /**
   * Récupération prescrite à la main.
   *
   * <p>Elle n'acceptait qu'une zone du club, quand le bloc juste au-dessus acceptait déjà une
   * fourchette écrite pour la séance. « Récup à 55–70 % de LT1 » n'avait donc nulle part où
   * s'écrire, alors que le serveur sait calculer ce couple depuis toujours.</p>
   */
  describe('récupération en % d’un référentiel', () => {
    function withRecovery(): CourseBlock {
      const b = block('intervals');
      b.recovery = { type: 'jog', durationS: 90, distanceM: null, prescription: { zoneId: 'z-recup' } };
      return b;
    }

    it('passe la récup en % et retrouve sa zone au retour', () => {
      const b = withRecovery();
      component.switchRecToPct(b);

      expect(component.recUsesPct(b)).toBeTrue();
      expect(b.recovery!.prescription!.zoneId).toBeNull();
      expect(b.recovery!.prescription!.ref).toBe('PCT_LT1');

      component.switchRecToZone(b);
      expect(component.recUsesPct(b)).toBeFalse();
      expect(b.recovery!.prescription!.zoneId).toBe('z-recup');
    });

    it('borne et pousse la fourchette de récup comme celle d’un bloc', () => {
      const b = withRecovery();
      component.switchRecToPct(b);

      component.setRecPctBoundText(b, 'min', field('400'));
      expect(b.recovery!.prescription!.minPct).toBe(150);
      expect(b.recovery!.prescription!.maxPct).toBe(150);
    });

    it('change le référentiel de la récup', () => {
      const b = withRecovery();
      component.switchRecToPct(b);
      component.setRecPctRef(b, 'PCT_VC');
      expect(component.recPctRefShort(b)).toBe('VC');
    });
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
