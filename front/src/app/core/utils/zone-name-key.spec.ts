import { zoneNameKey } from './zone-name-key';

describe('zoneNameKey', () => {
  it('rapproche la zone la plus basse des trois modèles de référence', () => {
    const recup = zoneNameKey('Récupération');
    expect(zoneNameKey('Footing facile')).toBe(recup);
    expect(zoneNameKey('Régénération')).toBe(recup);
    expect(zoneNameKey('  RECUPERATION ')).toBe(recup);
  });

  it('ignore casse, accents et espaces superflus sans confondre les zones', () => {
    expect(zoneNameKey(' Seuil  2   bas ')).toBe(zoneNameKey('seuil 2 bas'));
    expect(zoneNameKey('EF')).not.toBe(zoneNameKey('Récupération'));
    expect(zoneNameKey(null)).toBe('');
  });
});
