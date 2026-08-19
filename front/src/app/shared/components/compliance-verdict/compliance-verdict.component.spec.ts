import { importProvidersFrom } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { LucideAngularModule } from 'lucide-angular';
import { ComplianceVerdictComponent } from './compliance-verdict.component';
import { ComplianceDrift } from '../../../core/models/decision.model';
import { ICONS } from '../../../app.config';

/**
 * La phrase de dérive cardiaque.
 *
 * <p>Trois nombres seraient trois nombres. Ce que le test verrouille, c'est la <b>lecture</b> :
 * une dérive à allure tenue (le cœur monte, le chrono ne bouge pas) et une dérive compensée
 * (le cœur tient parce que l'allure a lâché) portent les mêmes chiffres de FC et ne veulent pas
 * dire la même chose. Confondre les deux, c'est féliciter un athlète qui a lâché.</p>
 */
describe('ComplianceVerdictComponent — dérive cardiaque', () => {
  let component: ComplianceVerdictComponent;

  beforeEach(() => {
    // `input()` exige un contexte d'injection : le composant se construit par TestBed, jamais
    // avec `new`.
    TestBed.configureTestingModule({
      imports: [ComplianceVerdictComponent],
      providers: [importProvidersFrom(LucideAngularModule.pick(ICONS))],
    });
    component = TestBed.createComponent(ComplianceVerdictComponent).componentInstance;
  });

  function drift(over: Partial<ComplianceDrift> = {}): ComplianceDrift {
    return {
      firstLabel: '1000 m (1/4)',
      lastLabel: '1000 m (4/4)',
      hrDeltaBpm: 12,
      hrDeltaPct: 8,
      paceDeltaSecPerKm: 0,
      ...over,
    };
  }

  it('nomme la dérive subie : le cœur monte, l’allure ne bouge pas', () => {
    const phrase = component.driftSentence(drift());
    expect(phrase).toContain('+12 bpm');
    expect(phrase).toContain('+8,0 %');
    expect(phrase).toContain('à allure tenue');
    expect(phrase).toContain('« 1000 m (1/4) »');
    expect(phrase).toContain('« 1000 m (4/4) »');
  });

  it('nomme la dérive compensée : l’allure a lâché', () => {
    const phrase = component.driftSentence(drift({ hrDeltaBpm: 0, hrDeltaPct: 0, paceDeltaSecPerKm: 11 }));
    expect(phrase).toContain('+11 s/km');
    expect(phrase).toContain('compensée');
    expect(phrase).not.toContain('à allure tenue');
  });

  /** Deux ou trois secondes au kilomètre, c'est du bruit de mesure, pas une décision de coureur. */
  it('traite un écart d’allure négligeable comme une allure tenue', () => {
    expect(component.driftSentence(drift({ paceDeltaSecPerKm: 2 }))).toContain('à allure tenue');
    expect(component.driftSentence(drift({ paceDeltaSecPerKm: -3 }))).toContain('à allure tenue');
  });

  it('sait dire une fin plus rapide plutôt que de la présenter comme une compensation', () => {
    const phrase = component.driftSentence(drift({ paceDeltaSecPerKm: -9 }));
    expect(phrase).toContain('−9 s/km');
    expect(phrase).toContain('plus rapide');
    expect(phrase).not.toContain('compensée');
  });

  it('sait se passer de l’allure quand elle manque', () => {
    const phrase = component.driftSentence(drift({ paceDeltaSecPerKm: null }));
    expect(phrase).toContain('+12 bpm');
    expect(phrase).not.toContain('s/km');
  });

  /** Une FC qui redescend se lit avec un vrai signe moins, pas un tiret de césure. */
  it('affiche une dérive négative avec son signe', () => {
    const phrase = component.driftSentence(drift({ hrDeltaBpm: -5, hrDeltaPct: -3.4 }));
    expect(phrase).toContain('−5 bpm');
    expect(phrase).toContain('−3,4 %');
  });
});
