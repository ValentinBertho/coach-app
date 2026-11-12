import { parseVideoUrl } from './video-embed';

/**
 * Ce que le coach colle, et ce qui a le droit d'entrer dans une iframe.
 *
 * <p><b>Ce que ce fichier protège.</b> Le champ « lien vidéo » est libre, et ce qu'on y colle
 * finit dans le navigateur d'un athlète. La règle tient en une phrase : l'URL fournie n'est jamais
 * recopiée — on en extrait un identifiant, on le valide, et on reconstruit l'URL d'intégration.
 * Les cas de refus comptent donc autant que les cas d'acceptation.</p>
 */
describe('parseVideoUrl', () => {
  /** Les formes qu'on colle réellement depuis la barre d'adresse ou le bouton « Partager ». */
  it('reconnaît les liens YouTube dans leurs formes courantes', () => {
    const forms = [
      'https://www.youtube.com/watch?v=dQw4w9WgXcQ',
      'https://youtube.com/watch?v=dQw4w9WgXcQ&t=42',
      'https://youtu.be/dQw4w9WgXcQ',
      'https://www.youtube.com/embed/dQw4w9WgXcQ',
      'https://www.youtube.com/shorts/dQw4w9WgXcQ',
    ];
    for (const url of forms) {
      const video = parseVideoUrl(url);
      expect(video).withContext(url).not.toBeNull();
      expect(video!.provider).toBe('youtube');
      expect(video!.embedUrl).toBe('https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ?rel=0');
    }
  });

  /** Le domaine sans cookie n'est pas un détail : c'est ce qui évite de tracer l'athlète. */
  it('intègre YouTube par son domaine sans cookie', () => {
    expect(parseVideoUrl('https://youtu.be/dQw4w9WgXcQ')!.embedUrl).toContain('youtube-nocookie.com');
    expect(parseVideoUrl('https://youtu.be/dQw4w9WgXcQ')!.watchUrl).toBe('https://www.youtube.com/watch?v=dQw4w9WgXcQ');
  });

  it('reconnaît les liens Vimeo', () => {
    expect(parseVideoUrl('https://vimeo.com/123456789')!.embedUrl)
      .toBe('https://player.vimeo.com/video/123456789?dnt=1');
    expect(parseVideoUrl('https://player.vimeo.com/video/123456789')!.provider).toBe('vimeo');
  });

  /** Tout le reste redevient un simple lien externe : c'est un refus, pas une panne. */
  it('refuse ce qui n’est pas un hébergeur reconnu', () => {
    const refused = [
      'https://drive.google.com/file/d/abc/view',
      'https://www.instagram.com/p/abc/',
      'https://example.com/ma-video.mp4',
      'https://youtube.com.attaquant.fr/watch?v=dQw4w9WgXcQ',
      'pas une url',
      '',
      null,
      undefined,
    ];
    for (const url of refused) {
      expect(parseVideoUrl(url)).withContext(String(url)).toBeNull();
    }
  });

  /** Un lien en clair dégraderait la page entière de l'athlète : on ne l'intègre pas. */
  it('refuse le HTTP non chiffré', () => {
    expect(parseVideoUrl('http://www.youtube.com/watch?v=dQw4w9WgXcQ')).toBeNull();
  });

  /**
   * Le cœur de la garde : un identifiant n'est accepté que s'il a exactement la forme attendue.
   * Sans cette validation, `?v=` porterait n'importe quoi jusque dans l'URL de l'iframe.
   */
  it('refuse un identifiant mal formé', () => {
    expect(parseVideoUrl('https://www.youtube.com/watch?v=trop-court')).toBeNull();
    expect(parseVideoUrl('https://www.youtube.com/watch?v=avec/slash/x')).toBeNull();
    expect(parseVideoUrl('https://www.youtube.com/watch?v="onerror=alert(1)')).toBeNull();
    expect(parseVideoUrl('https://vimeo.com/abcdefgh')).toBeNull();
  });

  /** Un paramètre de suivi collé avec le lien ne doit pas survivre à la reconstruction. */
  it('ne recopie jamais les paramètres du lien d’origine', () => {
    const video = parseVideoUrl('https://www.youtube.com/watch?v=dQw4w9WgXcQ&si=tracking&list=PL123');
    expect(video!.embedUrl).toBe('https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ?rel=0');
  });
});
