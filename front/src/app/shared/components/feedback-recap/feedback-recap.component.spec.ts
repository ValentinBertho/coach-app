import { importProvidersFrom } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LucideAngularModule } from 'lucide-angular';
import { FeedbackRecapComponent } from './feedback-recap.component';
import { ICONS } from '../../../app.config';

/**
 * L'écart entre l'effort annoncé par le coach et l'effort ressenti par l'athlète.
 *
 * <p>Ce que ces tests protègent, c'est le <b>silence</b> autant que l'affichage. Une séance
 * prévue à 7 et ressentie à 7 n'apprend rien : l'afficher occuperait la place d'une information.
 * Une séance sans annonce ne doit pas produire un écart calculé contre zéro — l'athlète verrait
 * « +9 » sur une séance dont le coach n'a simplement rien dit.</p>
 */
describe('FeedbackRecapComponent — écart prévu / ressenti', () => {
  let fixture: ComponentFixture<FeedbackRecapComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [FeedbackRecapComponent],
      providers: [importProvidersFrom(LucideAngularModule.pick(ICONS))],
    });
    fixture = TestBed.createComponent(FeedbackRecapComponent);
  });

  function set(rpe: number | null, targetRpe: number | null) {
    fixture.componentRef.setInput('rpe', rpe);
    fixture.componentRef.setInput('targetRpe', targetRpe);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('annonce un effort plus dur que prévu, et le marque comme tel', () => {
    const gap = set(9, 6).rpeGap()!;
    expect(gap.text).toBe('Prévu 6/10 · +3');
    expect(gap.over).withContext('au-dessus du prévu : c’est le cas qui alerte').toBeTrue();
  });

  it('annonce un effort plus facile que prévu sans le marquer', () => {
    const gap = set(4, 7).rpeGap()!;
    expect(gap.text).toBe('Prévu 7/10 · −3');
    expect(gap.over).toBeFalse();
  });

  it('se tait quand le ressenti tombe juste sur l’annonce', () => {
    expect(set(7, 7).rpeGap()).toBeNull();
  });

  it('se tait quand le coach n’a rien annoncé', () => {
    expect(set(9, null).rpeGap()).withContext('pas d’annonce, pas d’écart').toBeNull();
  });

  it('se tait quand l’athlète n’a pas répondu', () => {
    expect(set(null, 7).rpeGap()).toBeNull();
  });

  /**
   * Le RPE annoncé reste lisible même sans réponse de l'athlète : le coach doit pouvoir vérifier
   * ce qu'il avait demandé, y compris — et surtout — sur une séance sans retour.
   */
  it('montre tout de même l’annonce quand l’athlète n’a pas répondu', () => {
    set(null, 7);
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Prévu 7/10');
    expect(text).toContain('Pas de réponse');
  });
});
