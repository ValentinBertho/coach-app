import { environment } from '../../../environments/environment';
import { LEGAL_OWNER } from '../../features/public/legal.component';

/**
 * Lien de contact du support.
 *
 * <p>L'aide affichait « Contactez le support de la plateforme. » — sans adresse ni lien. Un coach
 * bloqué n'avait donc aucun moyen de joindre qui que ce soit : la phrase désignait un support
 * introuvable. L'adresse est celle publiée sur les pages légales, seule adresse de contact du
 * Service : elles ne peuvent pas diverger.</p>
 *
 * <p>Le sujet et le corps sont pré-remplis avec le contexte technique (version, page, navigateur)
 * qu'on redemande sinon systématiquement au premier échange.</p>
 */
export function supportMailto(context: string, page: string = location?.pathname ?? ''): string {
  const subject = `DARI Lab — ${context}`;
  const body = [
    'Décris ici ce que tu faisais et ce qui s’est passé :',
    '',
    '',
    '---',
    `Version : ${environment.appVersion}`,
    `Page : ${page}`,
    `Navigateur : ${typeof navigator === 'undefined' ? '—' : navigator.userAgent}`,
  ].join('\n');
  return `mailto:${LEGAL_OWNER.email}?subject=${encodeURIComponent(subject)}&body=${encodeURIComponent(body)}`;
}

/** Adresse de support affichée en clair (pour les lecteurs d'écran et le copier-coller). */
export const SUPPORT_EMAIL = LEGAL_OWNER.email;
