import { CalendarSelection, ChipPosition } from './calendar-selection';

/** Grille de test : deux jours, trois chips, dans l'ordre de parcours du calendrier. */
const ORDER: ChipPosition[] = [
  { kind: 'course', id: 'a', date: '2026-07-27', index: 0 },
  { kind: 'strength', id: 'b', date: '2026-07-27', index: 1 },
  { kind: 'course', id: 'c', date: '2026-07-28', index: 0 },
];

describe('CalendarSelection', () => {
  let sel: CalendarSelection;
  beforeEach(() => { sel = new CalendarSelection(); });

  it('le clic simple ne garde qu’une chip', () => {
    sel.select({ kind: 'course', id: 'a' });
    sel.select({ kind: 'course', id: 'c' });
    expect(sel.count()).toBe(1);
    expect(sel.has({ kind: 'course', id: 'c' })).toBeTrue();
  });

  it('Cmd+clic ajoute puis retire', () => {
    sel.toggle({ kind: 'course', id: 'a' });
    sel.toggle({ kind: 'course', id: 'c' });
    expect(sel.count()).toBe(2);
    sel.toggle({ kind: 'course', id: 'a' });
    expect(sel.count()).toBe(1);
  });

  it('Maj+clic étend dans l’ordre de la grille, dans les deux sens', () => {
    sel.select({ kind: 'course', id: 'a' });
    sel.extendTo({ kind: 'course', id: 'c' }, ORDER);
    expect(sel.count()).toBe(3);

    // L'ancre ne bouge pas : une seconde extension doit pouvoir rétrécir la plage.
    sel.extendTo({ kind: 'strength', id: 'b' }, ORDER);
    expect(sel.count()).toBe(2);
    expect(sel.has({ kind: 'course', id: 'c' })).toBeFalse();
  });

  it('Maj+clic sans ancre se comporte comme un clic simple', () => {
    sel.extendTo({ kind: 'course', id: 'c' }, ORDER);
    expect(sel.count()).toBe(1);
  });

  it('sépare les identifiants par nature', () => {
    sel.setAll([{ kind: 'course', id: 'a' }, { kind: 'strength', id: 'b' }]);
    expect(sel.ids('course')).toEqual(['a']);
    expect(sel.ids('strength')).toEqual(['b']);
  });

  it('oublie ce qui a disparu de la grille', () => {
    sel.setAll([{ kind: 'course', id: 'a' }, { kind: 'course', id: 'c' }]);
    sel.prune([{ kind: 'course', id: 'a' }]);
    expect(sel.count()).toBe(1);
    expect(sel.has({ kind: 'course', id: 'c' })).toBeFalse();
  });

  it('résout la sélection dans l’ordre de la grille', () => {
    sel.setAll([{ kind: 'course', id: 'c' }, { kind: 'course', id: 'a' }]);
    expect(sel.resolve(ORDER).map((p) => p.id)).toEqual(['a', 'c']);
  });
});
