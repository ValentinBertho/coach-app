import { environment } from '../../../environments/environment';
import { LEGAL_OWNER } from '../../features/public/legal.component';
import { SUPPORT_EMAIL, supportMailto } from './support-link';

/**
 * Lien de support. L'aide affichait « Contactez le support de la plateforme. » sans adresse ni
 * lien : un coach bloqué n'avait aucun moyen de joindre qui que ce soit.
 */
describe('supportMailto', () => {
  it('pointe sur l’adresse publiée dans les pages légales', () => {
    // Les deux ne doivent pas pouvoir diverger : c'est la même adresse de contact du Service.
    expect(SUPPORT_EMAIL).toBe(LEGAL_OWNER.email);
    expect(supportMailto('Test')).toContain(`mailto:${LEGAL_OWNER.email}`);
  });

  it('pré-remplit le sujet avec le contexte', () => {
    const link = supportMailto('Signalement depuis l’espace coach');

    const subject = decodeURIComponent(link.split('subject=')[1].split('&')[0]);
    expect(subject).toBe('DARI Lab — Signalement depuis l’espace coach');
  });

  it('pré-remplit le corps avec la version et la page', () => {
    const link = supportMailto('Demande de support', '/app/athletes/42');

    const body = decodeURIComponent(link.split('body=')[1]);
    // Ce sont les trois informations qu'on redemande sinon systématiquement au premier échange.
    expect(body).toContain(`Version : ${environment.appVersion}`);
    expect(body).toContain('Page : /app/athletes/42');
    expect(body).toContain('Navigateur :');
  });

  it('produit une URL utilisable telle quelle', () => {
    const link = supportMailto('Sujet avec espaces & esperluette');

    // Les séparateurs ne doivent pas être cassés par le contenu.
    expect(link.startsWith('mailto:')).toBeTrue();
    expect(link.split('?').length).toBe(2);
    expect(link.split('subject=').length).toBe(2);
    expect(link.split('body=').length).toBe(2);
  });
});
