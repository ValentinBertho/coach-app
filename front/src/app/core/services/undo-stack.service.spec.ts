import { Observable, of, throwError } from 'rxjs';
import { UndoCommand, UndoStackService } from './undo-stack.service';

/** Commande factice qui trace ce qu'on lui demande. */
function cmd(label: string, trace: string[], failUndo = false): UndoCommand {
  return {
    label,
    undo: (): Observable<unknown> => {
      if (failUndo) return throwError(() => new Error('boom'));
      trace.push(`undo:${label}`);
      return of(null);
    },
    redo: (): Observable<unknown> => { trace.push(`redo:${label}`); return of(null); },
  };
}

describe('UndoStackService', () => {
  let stack: UndoStackService;
  let trace: string[];

  beforeEach(() => { stack = new UndoStackService(); trace = []; });

  it('défait en ordre inverse et rétablit en ordre direct', () => {
    stack.push(cmd('A', trace));
    stack.push(cmd('B', trace));

    stack.undo().subscribe();
    stack.undo().subscribe();
    expect(trace).toEqual(['undo:B', 'undo:A']);

    stack.redo().subscribe();
    expect(trace.at(-1)).toBe('redo:A');
    expect(stack.canRedo()).toBeTrue();
  });

  it('expose le libellé de ce qui sera défait', () => {
    expect(stack.undoLabel()).toBeNull();
    stack.push(cmd('le déplacement', trace));
    expect(stack.undoLabel()).toBe('le déplacement');
  });

  it('toute nouvelle action coupe la branche de rétablissement', () => {
    stack.push(cmd('A', trace));
    stack.undo().subscribe();
    expect(stack.canRedo()).toBeTrue();

    stack.push(cmd('B', trace));
    expect(stack.canRedo()).toBeFalse();
  });

  it('remet la commande en pile quand l’annulation échoue', () => {
    stack.push(cmd('A', trace, true));
    stack.undo().subscribe({ error: () => { /* attendu */ } });
    // L'état réel n'a pas changé : on doit pouvoir réessayer.
    expect(stack.canUndo()).toBeTrue();
    expect(stack.canRedo()).toBeFalse();
  });

  it('ne défait rien sur une pile vide', () => {
    let emitted: string | null | undefined;
    stack.undo().subscribe((l) => { emitted = l; });
    expect(emitted).toBeNull();
  });

  it('reset vide les deux branches', () => {
    stack.push(cmd('A', trace));
    stack.undo().subscribe();
    stack.reset();
    expect(stack.canUndo()).toBeFalse();
    expect(stack.canRedo()).toBeFalse();
  });
});
