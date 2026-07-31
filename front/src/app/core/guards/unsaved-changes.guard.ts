import { inject } from '@angular/core';
import { CanDeactivateFn } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { Autosave } from '../services/autosave';
import { ConfirmService } from '../services/confirm.service';

/** Écran d'édition qui expose son auto-sauvegarde au garde de sortie. */
export interface HasAutosave {
  readonly autosave: Autosave;
}

/**
 * Filet de sécurité à la sortie d'un éditeur : l'auto-save couvre le cas normal, ce garde ne
 * couvre que l'anormal — une sauvegarde qui a échoué (réseau coupé, session expirée).
 *
 * On tente d'abord d'enregistrer une dernière fois : dans l'immense majorité des cas la sortie
 * se fait sans que le coach voie quoi que ce soit. On ne l'interrompt que si ça échoue encore,
 * et alors le choix lui appartient (jamais confirm() natif, cf. Claude.md).
 */
export const unsavedChangesGuard: CanDeactivateFn<HasAutosave> = async (component) => {
  const autosave = component?.autosave;
  if (!autosave || !autosave.pendingChanges()) return true;

  const saved = await firstValueFrom(autosave.flush());
  if (saved) return true;

  return inject(ConfirmService).ask({
    title: 'Modifications non enregistrées',
    message: 'Ta dernière modification n’a pas pu être enregistrée. Si tu quittes maintenant, elle sera perdue.',
    confirmLabel: 'Quitter sans enregistrer',
    danger: true,
  });
};
