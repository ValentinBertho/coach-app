import { Injectable, signal } from '@angular/core';

export interface ConfirmRequest {
  title: string;
  message: string;
  confirmLabel?: string;
  danger?: boolean;
  /**
   * Mot à recopier pour débloquer la confirmation (ex. `SUPPRIMER`). Absent = confirmation
   * simple, en un clic.
   *
   * <p>Réservé aux actions irréversibles et sans recours — la suppression d'un compte efface en
   * cascade profil, séances, activités, ressentis et messages, et la seule récupération possible
   * est la restauration sélective d'une sauvegarde. Un tap sur un écran mobile est trop court
   * pour ça ; recopier un mot rend le geste délibéré sans le rendre pénible.</p>
   */
  requiredText?: string;
  /**
   * Libellé d'un champ de <b>saisie libre</b>. Présent, la modale devient une invite : elle
   * résout la valeur saisie plutôt qu'un simple oui/non.
   *
   * <p>Distinct de {@link ConfirmRequest#requiredText}, qui exige la recopie d'un mot imposé
   * pour armer une action destructrice. Ici on demande une valeur, pas une preuve d'intention.</p>
   */
  promptLabel?: string;
  /** Valeur initiale du champ de saisie, présélectionnée pour être remplacée d'un coup. */
  initialValue?: string;
  /**
   * Libellé d'une <b>case à cocher</b> facultative, posée sous le message.
   *
   * <p>Pour une décision qui accompagne l'action sans la conditionner : « supprimer » reste
   * « supprimer », la case ajoute « et ne plus jamais l'importer ». En faire un second bouton
   * obligerait à choisir entre deux suppressions au moment où l'on veut juste supprimer.</p>
   */
  optionLabel?: string;
  /** Précision affichée sous la case, quand sa portée ne tient pas dans son libellé. */
  optionHint?: string;
}

/** Réponse d'une confirmation à option : l'accord, et l'état de la case. */
export interface ConfirmWithOption {
  confirmed: boolean;
  option: boolean;
}

interface PendingConfirm extends ConfirmRequest {
  resolve: (ok: boolean) => void;
  /** Présent pour une confirmation à option : reçoit l'accord ET l'état de la case. */
  resolveOption?: (answer: ConfirmWithOption) => void;
  /** Présent pour une saisie libre : reçoit la valeur, ou `null` si l'utilisateur renonce. */
  resolveText?: (value: string | null) => void;
}

/**
 * Confirmation via modale (remplace confirm() natif, cf. Claude.md). Le composant
 * app-confirm-dialog consomme `pending` et appelle resolve().
 */
@Injectable({ providedIn: 'root' })
export class ConfirmService {
  readonly pending = signal<PendingConfirm | null>(null);

  ask(request: ConfirmRequest): Promise<boolean> {
    return new Promise<boolean>((resolve) => {
      this.pending.set({ ...request, resolve });
    });
  }

  /** Confirmation à recopie : le bouton ne s'active qu'une fois `requiredText` saisi. */
  askForText(request: ConfirmRequest & { requiredText: string }): Promise<boolean> {
    return this.ask(request);
  }

  /**
   * Invite de saisie : résout la valeur saisie, ou `null` si l'utilisateur renonce.
   *
   * <p>Remplace le `prompt()` natif, banni au même titre que `confirm()` : il bloque le fil, ne
   * suit pas le thème, et sur mobile installé en PWA certains navigateurs ne l'affichent pas du
   * tout — l'action semblait alors sans effet.</p>
   */
  prompt(request: ConfirmRequest & { promptLabel: string }): Promise<string | null> {
    return new Promise<string | null>((resolve) => {
      this.pending.set({ ...request, resolve: () => undefined, resolveText: resolve });
    });
  }

  /**
   * Confirmation portant une case à cocher : résout l'accord et l'état de la case.
   *
   * <p>La case n'est lue que si l'action est confirmée — décocher en annulant ne veut rien dire.</p>
   */
  askWithOption(request: ConfirmRequest & { optionLabel: string }): Promise<ConfirmWithOption> {
    return new Promise<ConfirmWithOption>((resolve) => {
      this.pending.set({ ...request, resolve: () => undefined, resolveOption: resolve });
    });
  }

  answer(ok: boolean, value?: string, option = false): void {
    const p = this.pending();
    if (!p) {
      return;
    }
    this.pending.set(null);
    if (p.resolveOption) {
      p.resolveOption({ confirmed: ok, option: ok && option });
      return;
    }
    if (p.resolveText) {
      p.resolveText(ok ? (value ?? '').trim() : null);
      return;
    }
    p.resolve(ok);
  }
}
