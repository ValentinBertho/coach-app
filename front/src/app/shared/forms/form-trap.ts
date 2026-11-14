import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { HttpHeaders } from '@angular/common/http';

/**
 * Piège à robots d'un formulaire public : un champ invisible et un chronomètre.
 *
 * <p>Un humain ne voit pas le champ et met plus de deux secondes à remplir un formulaire ; un
 * robot générique remplit tout ce qu'il trouve et envoie aussitôt. Les deux indices partent en
 * en-têtes et le serveur tranche (`PublicFormGuardFilter`). Rien n'est demandé à l'humain,
 * aucun service tiers n'est appelé, aucun cookie n'est posé.</p>
 *
 * <p>Le chronomètre part à la création du formulaire, pas à chaque envoi : un humain refusé
 * parce que son navigateur a tout rempli d'un coup n'a qu'à renvoyer.</p>
 */
export class FormTrap {
  private readonly startedAt = Date.now();
  /** Contenu du champ invisible : vide pour un humain. */
  value = '';

  headers(): HttpHeaders {
    return new HttpHeaders({
      'X-Form-Trap': this.value,
      'X-Form-Elapsed': String(Date.now() - this.startedAt),
    });
  }
}

/**
 * Le champ invisible, à placer dans le formulaire. Hors écran plutôt que `display: none` — que
 * certains robots savent ignorer — et sorti de la navigation au clavier et des lecteurs d'écran.
 */
@Component({
  selector: 'app-form-trap',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="form-trap" aria-hidden="true">
      <label>
        Site web (laisser vide)
        <input type="text" name="website" tabindex="-1" autocomplete="off"
               (input)="trap().value = $any($event.target).value" />
      </label>
    </div>
  `,
  styles: [`
    .form-trap {
      position: absolute;
      left: -10000px;
      top: auto;
      width: 1px;
      height: 1px;
      overflow: hidden;
    }
  `],
})
export class FormTrapComponent {
  readonly trap = input.required<FormTrap>();
}
