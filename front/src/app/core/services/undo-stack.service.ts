import { Injectable, computed, signal } from '@angular/core';
import { Observable, of } from 'rxjs';

/**
 * Une action réversible. `undo` et `redo` doivent être **idempotentes en effet** : rejouer un
 * redo après un undo doit reproduire exactement le même état visible.
 *
 * Quand l'action crée une entité (planification, copie, restauration après suppression), le
 * serveur renvoie un nouvel identifiant à chaque rejeu. La commande doit alors refermer sur une
 * boîte mutable et y écrire l'id courant, sinon le deuxième undo viserait un fantôme.
 */
export interface UndoCommand {
  /** Libellé à la première personne du geste défait : « le déplacement », « la suppression ». */
  readonly label: string;
  undo(): Observable<unknown>;
  redo(): Observable<unknown>;
}

/**
 * Pile d'annulation réelle (Cmd+Z / Cmd+Shift+Z), par opposition au toast « Annuler » qui ne
 * rattrape que la dernière action et seulement pendant quelques secondes.
 *
 * La pile est **volontairement locale à un écran** : `reset()` à l'entrée, et tout changement de
 * contexte (athlète, période) la vide. Annuler un déplacement chez un athlète qu'on ne regarde
 * plus produirait un effet invisible — pire qu'une annulation impossible.
 */
@Injectable({ providedIn: 'root' })
export class UndoStackService {
  /** Au-delà, on oublie : une pile infinie garde en mémoire des ids d'entités disparues. */
  private static readonly MAX_DEPTH = 40;

  private readonly done = signal<UndoCommand[]>([]);
  private readonly undone = signal<UndoCommand[]>([]);
  /** Une opération est en vol : on refuse d'en empiler une seconde (ordre non garanti). */
  private readonly busy = signal(false);

  readonly canUndo = computed(() => this.done().length > 0 && !this.busy());
  readonly canRedo = computed(() => this.undone().length > 0 && !this.busy());
  readonly undoLabel = computed(() => this.done().at(-1)?.label ?? null);
  readonly redoLabel = computed(() => this.undone().at(-1)?.label ?? null);
  readonly depth = computed(() => this.done().length);

  /** Empile une action déjà exécutée. Toute nouvelle action invalide la branche de rétablissement. */
  push(command: UndoCommand): void {
    this.done.update((l) => [...l, command].slice(-UndoStackService.MAX_DEPTH));
    this.undone.set([]);
  }

  /** Vide la pile (changement d'écran ou de contexte). */
  reset(): void {
    this.done.set([]);
    this.undone.set([]);
    this.busy.set(false);
  }

  /**
   * Défait la dernière action. Émet le libellé défait, ou `null` si rien à défaire.
   * En cas d'échec serveur, la commande est **remise** sur la pile : l'état réel n'a pas changé.
   */
  undo(): Observable<string | null> {
    return this.run(this.done, this.undone, (c) => c.undo());
  }

  /** Rejoue la dernière action annulée. */
  redo(): Observable<string | null> {
    return this.run(this.undone, this.done, (c) => c.redo());
  }

  private run(
    from: ReturnType<typeof signal<UndoCommand[]>>,
    to: ReturnType<typeof signal<UndoCommand[]>>,
    apply: (c: UndoCommand) => Observable<unknown>,
  ): Observable<string | null> {
    if (this.busy()) return of(null);
    const command = from().at(-1);
    if (!command) return of(null);

    this.busy.set(true);
    from.update((l) => l.slice(0, -1));

    return new Observable<string | null>((sub) => {
      apply(command).subscribe({
        next: () => { /* on attend la complétion pour basculer la pile */ },
        error: () => {
          // L'action n'a pas été défaite : la commande retourne d'où elle vient.
          from.update((l) => [...l, command]);
          this.busy.set(false);
          sub.error(new Error(command.label));
        },
        complete: () => {
          to.update((l) => [...l, command]);
          this.busy.set(false);
          sub.next(command.label);
          sub.complete();
        },
      });
    });
  }
}
