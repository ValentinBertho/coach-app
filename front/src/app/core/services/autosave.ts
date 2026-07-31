import { DestroyRef, signal } from '@angular/core';
import { Observable, ReplaySubject, of } from 'rxjs';

/** État de sauvegarde affiché par la pastille (`app-autosave-badge`). */
export type AutosaveState = 'clean' | 'dirty' | 'saving' | 'saved' | 'error';

/**
 * Auto-sauvegarde avec debounce, pour les éditeurs longs (structure de séance course et force).
 *
 * Sans ça, un clic sur « Calendrier » et dix minutes de construction disparaissent sans un mot :
 * les éditeurs n'avaient ni auto-save ni garde-fou de sortie. Le debounce évite d'envoyer une
 * requête à chaque frappe tout en gardant la perte maximale sous la seconde et demie.
 *
 * Le suivi se fait par numéro de révision, pas par un simple booléen : une modification arrivée
 * pendant qu'une sauvegarde est en vol ne doit pas être considérée comme enregistrée quand
 * celle-ci répond. `pendingChanges()` reste donc vrai tant que le serveur n'a pas reçu la
 * dernière version — c'est ce que lit le `canDeactivate` en filet, pour le cas où la sauvegarde
 * a échoué (réseau coupé).
 *
 * @example
 * private readonly autosave = new Autosave(() => this.persist(), inject(DestroyRef));
 * // à chaque mutation : this.autosave.markDirty();
 */
export class Autosave {
  private static readonly DEBOUNCE_MS = 1500;

  readonly state = signal<AutosaveState>('clean');
  /** Horodatage de la dernière sauvegarde réussie (pour « il y a 3 s »). */
  readonly savedAt = signal<number | null>(null);

  private timer?: ReturnType<typeof setTimeout>;
  private revision = 0;
  private savedRevision = 0;

  constructor(
    private readonly persist: () => Observable<unknown>,
    destroyRef?: DestroyRef,
    private readonly debounceMs = Autosave.DEBOUNCE_MS,
  ) {
    destroyRef?.onDestroy(() => clearTimeout(this.timer));
  }

  /** Des modifications ne sont pas encore parties au serveur. */
  pendingChanges(): boolean { return this.revision !== this.savedRevision; }

  /** À appeler après toute mutation du contenu édité. */
  markDirty(): void {
    this.revision++;
    this.state.set('dirty');
    clearTimeout(this.timer);
    this.timer = setTimeout(() => this.run(), this.debounceMs);
  }

  /**
   * Sauvegarde tout de suite (bouton « Enregistrer », sortie d'écran) sans attendre le debounce.
   * Émet `true` si tout est à jour côté serveur.
   */
  flush(): Observable<boolean> {
    clearTimeout(this.timer);
    return this.pendingChanges() ? this.run() : of(true);
  }

  private run(): Observable<boolean> {
    const revision = this.revision;
    this.state.set('saving');
    // ReplaySubject : la sauvegarde part immédiatement, qu'on s'abonne ou non au résultat
    // (l'auto-save par debounce n'a personne pour l'écouter).
    const done = new ReplaySubject<boolean>(1);
    this.persist().subscribe({
      next: () => {
        this.savedRevision = revision;
        this.savedAt.set(Date.now());
        // Une frappe arrivée pendant l'envoi laisse l'écran « non enregistré » : elle attend
        // son propre tour de debounce.
        this.state.set(this.pendingChanges() ? 'dirty' : 'saved');
        done.next(!this.pendingChanges());
        done.complete();
      },
      error: () => {
        // On ne marque rien comme sauvegardé : le filet canDeactivate doit prévenir à la sortie.
        this.state.set('error');
        done.next(false);
        done.complete();
      },
    });
    return done.asObservable();
  }
}
