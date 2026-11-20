import { describeSetMethod, setMethodConfig } from './set-method';

describe('méthodes de série', () => {
  it('dit chaque méthode en une phrase, avec ses réglages', () => {
    expect(describeSetMethod('CLUSTER', { clusters: 3, repsPerCluster: 2, pauseSec: 15 }))
      .toBe('Cluster : 3 blocs de 2 reps, 15 s de pause entre les blocs');
    expect(describeSetMethod('MYO_REPS', null))
      .toBe("Myo-reps : 12 reps d'activation, puis 4 × 4 reps, 5 s de pause");
    expect(describeSetMethod('DROP_SET', { drops: 1 })).toBe('Drop set : 1 baisse de 20 %, sans pause');
    expect(describeSetMethod('STANDARD', null)).toBe('');
    expect(describeSetMethod(undefined, null)).toBe('');
  });

  it('complète un réglage partiel par les valeurs par défaut', () => {
    expect(setMethodConfig('CLUSTER', { pauseSec: 20 })).toEqual({ clusters: 3, repsPerCluster: 2, pauseSec: 20 });
    expect(setMethodConfig('CLUSTER', 'n’importe quoi')).toEqual({ clusters: 3, repsPerCluster: 2, pauseSec: 15 });
  });
});
