/**
 * Reconnaissance des liens de démonstration vidéo d'un exercice.
 *
 * <h2>Pourquoi une liste blanche, et pas un simple `<iframe [src]>`</h2>
 *
 * <p>Le champ « lien vidéo » est libre : un coach y colle ce qu'il veut, et ce qu'il colle finit
 * dans le navigateur d'un athlète. Encadrer une URL arbitraire dans une iframe, c'est exécuter du
 * tiers non choisi dans l'application — le contraire de ce que garantit la CSP du produit.</p>
 *
 * <p>On ne reconnaît donc que deux hébergeurs, et on ne recopie <b>jamais</b> l'URL fournie : on
 * en extrait l'identifiant, on le valide caractère par caractère, et on <b>reconstruit</b> une URL
 * d'intégration canonique. Une URL non reconnue n'est pas une erreur — elle reste un lien externe,
 * comme avant.</p>
 *
 * <p>YouTube est appelé sur son domaine <b>sans cookie</b> : l'athlète regarde une démonstration
 * de fente bulgare, il n'a pas à être suivi pour autant.</p>
 */
export type VideoProvider = 'youtube' | 'vimeo';

export interface VideoEmbed {
  readonly provider: VideoProvider;
  /** URL d'intégration reconstruite — la seule qui entre dans une iframe. */
  readonly embedUrl: string;
  /** Page d'origine, pour l'ouverture en plein écran hors de l'application. */
  readonly watchUrl: string;
  readonly label: string;
}

/** Identifiant YouTube : 11 caractères de l’alphabet base64url, jamais autre chose. */
const YOUTUBE_ID = /^[A-Za-z0-9_-]{11}$/;
/** Identifiant Vimeo : uniquement des chiffres. */
const VIMEO_ID = /^\d{6,12}$/;

const PROVIDER_LABEL: Record<VideoProvider, string> = {
  youtube: 'YouTube',
  vimeo: 'Vimeo',
};

/**
 * Traduit un lien saisi par le coach en vidéo intégrable, ou `null` s'il n'est pas reconnu.
 *
 * <p>Accepte les formes que l'on colle réellement : `youtu.be/ID`, `youtube.com/watch?v=ID`,
 * `/embed/ID`, `/shorts/ID`, `vimeo.com/123456789` et `player.vimeo.com/video/123456789`.</p>
 */
export function parseVideoUrl(raw: string | null | undefined): VideoEmbed | null {
  if (!raw) return null;
  let url: URL;
  try {
    url = new URL(raw.trim());
  } catch {
    return null;
  }
  // Le lien doit être chiffré : une démo en clair dégraderait la page entière de l'athlète.
  if (url.protocol !== 'https:') return null;

  const host = url.hostname.toLowerCase().replace(/^www\./, '');
  const id = host.endsWith('youtube.com') || host === 'youtu.be'
    ? youtubeId(host, url)
    : host === 'vimeo.com' || host === 'player.vimeo.com'
      ? vimeoId(url)
      : null;
  if (!id) return null;

  return host.includes('vimeo')
    ? {
      provider: 'vimeo',
      embedUrl: `https://player.vimeo.com/video/${id}?dnt=1`,
      watchUrl: `https://vimeo.com/${id}`,
      label: PROVIDER_LABEL.vimeo,
    }
    : {
      provider: 'youtube',
      // `-nocookie` : aucun cookie de suivi n'est posé tant que la vidéo n'est pas lancée,
      // et aucun profil publicitaire n'est alimenté par une séance de renforcement.
      embedUrl: `https://www.youtube-nocookie.com/embed/${id}?rel=0`,
      watchUrl: `https://www.youtube.com/watch?v=${id}`,
      label: PROVIDER_LABEL.youtube,
    };
}

function youtubeId(host: string, url: URL): string | null {
  const path = url.pathname.replace(/^\/+/, '');
  const candidate = host === 'youtu.be'
    ? path
    : path.startsWith('embed/') || path.startsWith('shorts/') || path.startsWith('v/')
      ? path.slice(path.indexOf('/') + 1)
      : (url.searchParams.get('v') ?? '');
  // Un chemin peut porter une suite (`/embed/ID/autre`) : on ne garde que le premier segment.
  const id = candidate.split('/')[0].trim();
  return YOUTUBE_ID.test(id) ? id : null;
}

function vimeoId(url: URL): string | null {
  // `vimeo.com/123456789`, `player.vimeo.com/video/123456789`, éventuellement suivis d'un hash.
  const segments = url.pathname.split('/').filter(Boolean);
  const id = (segments[0] === 'video' ? segments[1] : segments[0]) ?? '';
  return VIMEO_ID.test(id) ? id : null;
}
