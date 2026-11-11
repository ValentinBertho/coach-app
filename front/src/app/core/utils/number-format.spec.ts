import { frNumber } from './number-format';

describe('frNumber', () => {
  it('écrit la virgule décimale et le nombre de décimales demandé', () => {
    expect(frNumber(53.98, 1)).toBe('54,0');
    expect(frNumber(1.3833, 2)).toBe('1,38');
    expect(frNumber(0, 1)).toBe('0,0');
    expect(frNumber(12, 0)).toBe('12');
  });
});
