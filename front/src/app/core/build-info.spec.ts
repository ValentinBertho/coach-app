import { environment } from '../../environments/environment';
import { buildStamp, resetBuildStampCache } from './build-info';

/**
 * Le tampon de version, lu dans la page.
 *
 * <p>C'est lui qui rattache une erreur remontée à un commit précis : la `release` Sentry, le
 * corps du mail de support et le contexte d'un retour de bêta en dépendent. Il valait auparavant
 * une chaîne recopiée à la main dans `environment.ts` — donc la même pour tous les déploiements,
 * donc sans valeur de diagnostic (OPS-09).</p>
 */
describe('tampon de version', () => {
  let meta: HTMLMetaElement | null = null;

  function stampPageWith(value: string): void {
    meta = document.createElement('meta');
    meta.setAttribute('name', 'dari-build');
    meta.setAttribute('content', value);
    document.head.appendChild(meta);
    resetBuildStampCache();
  }

  afterEach(() => {
    meta?.remove();
    meta = null;
    resetBuildStampCache();
  });

  it('lit la version gravée au build', () => {
    stampPageWith('0.4.1+9f3c1ab');
    expect(buildStamp()).toBe('0.4.1+9f3c1ab');
  });

  /** Karma sert son propre `index.html` : la balise y est absente, comme sur un build non tamponné. */
  it('retombe sur la version du bundle quand la page n’est pas tamponnée', () => {
    resetBuildStampCache();
    expect(buildStamp()).toBe(environment.appVersion);
  });

  /** « dev » est la valeur du gabarit : elle ne désigne aucun déploiement. */
  it('ignore la valeur du gabarit', () => {
    stampPageWith('dev');
    expect(buildStamp()).toBe(environment.appVersion);
  });

  /** Lue une fois : le tampon ne change pas en cours de session. */
  it('ne relit pas la page à chaque appel', () => {
    stampPageWith('0.4.1+9f3c1ab');
    buildStamp();
    meta!.setAttribute('content', '0.0.0+aaaaaaa');
    expect(buildStamp()).toBe('0.4.1+9f3c1ab');
  });
});
