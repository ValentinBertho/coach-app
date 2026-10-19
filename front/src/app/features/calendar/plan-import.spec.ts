import { parsePlanFile } from './plan-import';

describe('parsePlanFile — lire un plan converti avant de l’importer', () => {
  const ok = {
    title: 'CIMA',
    workouts: [
      { scheduledDate: '2026-07-15', type: 'ENDURANCE', title: 'Footing', targetDurationS: 3000 },
      {
        scheduledDate: '2026-07-14', type: 'INTERVALS', title: ' Côtes ',
        steps: [{ stepType: 'WARMUP', repetitions: 1, zone: 'Z1', durationS: 1800 }],
      },
    ],
  };

  it('accepte un plan valide, trié par date, titres nettoyés', () => {
    const r = parsePlanFile(JSON.stringify(ok));
    expect(r.ok).toBeTrue();
    if (!r.ok) return;
    expect(r.plan.title).toBe('CIMA');
    expect(r.plan.workouts.map((w) => w.scheduledDate)).toEqual(['2026-07-14', '2026-07-15']);
    expect(r.plan.workouts[0].title).toBe('Côtes');
    expect(r.plan.workouts[1].steps).toEqual([]);
  });

  it('accepte aussi une simple liste de séances', () => {
    const r = parsePlanFile(JSON.stringify(ok.workouts));
    expect(r.ok).toBeTrue();
  });

  it('rapporte toutes les erreurs ensemble, séance par séance', () => {
    const r = parsePlanFile(JSON.stringify({
      workouts: [
        { scheduledDate: '14/07/2026', type: 'INTERVALS', title: 'Côtes' },
        { scheduledDate: '2026-07-15', type: 'FOOTING', title: '', steps: [{ stepType: 'RUN', zone: 'Z6' }] },
      ],
    }));
    expect(r.ok).toBeFalse();
    if (r.ok) return;
    expect(r.errors.length).toBe(5);
    expect(r.errors[0]).toContain('Séance 1');
  });

  it('refuse un fichier qui n’est pas du JSON, ou vide', () => {
    expect(parsePlanFile('pas du json').ok).toBeFalse();
    expect(parsePlanFile('{"workouts":[]}').ok).toBeFalse();
  });
});
