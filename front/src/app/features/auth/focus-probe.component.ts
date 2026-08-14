import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { isInstalledApp } from '../../core/utils/installed-app';

/**
 * Sonde de diagnostic du focus, activée par <code>?debug=1</code> sur un écran d'authentification.
 *
 * <p><b>Pourquoi elle existe.</b> Un champ qui refuse le focus sur iPhone <b>en mode installé
 * seulement</b> ne se reproduit sur aucun navigateur de bureau : il n'y a ni clavier virtuel, ni
 * barre d'état système, ni fenêtre autonome. Toute correction écrite sans mesure serait une
 * supposition déployée en production. Cette sonde remplace la supposition par trois lignes lues
 * sur l'appareil qui a le défaut.</p>
 *
 * <p><b>Ce qu'elle dit.</b> Quels évènements le champ reçoit réellement au toucher
 * (<code>touchstart</code> → <code>pointerdown</code> → <code>mousedown</code> →
 * <code>click</code> → <code>focus</code>), quel élément détient le focus après coup, et si la
 * hauteur du <i>visual viewport</i> change — c'est ce qui trahit l'ouverture du clavier. La
 * chaîne s'interrompt exactement là où se situe le défaut :</p>
 * <ul>
 *   <li>rien du tout → le toucher n'atteint pas le champ ;</li>
 *   <li>tout sauf <code>focus</code> → le focus est refusé ou repris aussitôt ;</li>
 *   <li><code>focus</code> mais viewport inchangé → le focus est acquis et c'est le clavier qui
 *       ne s'ouvre pas, ce qui n'est plus un défaut de l'application.</li>
 * </ul>
 *
 * <p><b>Comment on l'ouvre.</b> Par <code>?debug=1</code> dans un navigateur, ou — puisqu'une
 * application installée n'a pas de barre d'adresse — par <b>cinq appuis rapides sur l'en-tête</b>
 * de la carte de connexion. Invisible autrement : rien qui puisse apparaître devant un
 * utilisateur, et rien à retirer une fois le diagnostic fait.</p>
 */
@Component({
  selector: 'app-focus-probe',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (on()) {
      <section class="fp" role="status" aria-label="Sonde de diagnostic">
        <header class="fp__hd">
          <strong>Sonde focus</strong>
          <button type="button" (click)="lines.set([])">vider</button>
        </header>
        <p class="fp__env">{{ env() }}</p>
        <p class="fp__hint">Touche le champ e-mail, puis lis les lignes ci-dessous.</p>
        <ol class="fp__log">
          @for (l of lines(); track $index) { <li>{{ l }}</li> }
          @empty { <li class="fp__empty">— aucun évènement reçu —</li> }
        </ol>
      </section>
    }
  `,
  styles: [`
    .fp {
      position: fixed; left: 0; right: 0; bottom: 0; z-index: 2000;
      max-height: 45dvh; overflow-y: auto;
      padding: var(--sp-3);
      padding-bottom: calc(var(--sp-3) + env(safe-area-inset-bottom, 0px));
      background: #0b1416; color: #eaf2f0;
      border-top: 2px solid #2fb3c0;
      font-family: var(--font-mono, monospace); font-size: 12px; line-height: 1.5;
    }
    .fp__hd { display: flex; align-items: center; justify-content: space-between; gap: var(--sp-2); }
    .fp__hd button {
      background: #2fb3c0; color: #04191c; border: 0; border-radius: 4px;
      min-height: 32px; padding: 0 10px; font: inherit; font-weight: 700;
    }
    .fp__env { margin: 6px 0; color: #8fdbe3; word-break: break-word; }
    .fp__hint { margin: 0 0 6px; color: #9bb0ad; }
    .fp__log { margin: 0; padding-left: 18px; }
    .fp__empty { list-style: none; margin-left: -18px; color: #647a77; }
  `],
})
export class FocusProbeComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);

  readonly on = signal(false);
  readonly lines = signal<string[]>([]);
  readonly env = signal('');

  ngOnInit(): void {
    if (this.route.snapshot.queryParamMap.get('debug') === '1') {
      this.start();
      return;
    }
    // Une application installée n'a pas de barre d'adresse : le paramètre d'URL y est
    // inatteignable. Cinq appuis rapides sur le titre ouvrent donc la sonde à la main, là où le
    // défaut se produit.
    setTimeout(() => this.armSecretGesture(), 300);
  }

  private start(): void {
    if (this.on()) return;
    this.on.set(true);
    this.describeEnvironment();
    // Le champ appartient à l'écran qui nous héberge : on attend qu'il soit peint.
    setTimeout(() => this.watch(), 300);
  }

  /** Cinq appuis en moins de trois secondes sur l'en-tête de la carte. */
  private armSecretGesture(): void {
    const head = document.querySelector('.auth-head');
    if (!head) return;
    let count = 0;
    let first = 0;
    head.addEventListener('click', () => {
      const now = Date.now();
      if (now - first > 3000) { count = 0; first = now; }
      if (++count >= 5) this.start();
    });
  }

  /** Le contexte d'exécution : c'est lui qui distingue le navigateur de l'application installée. */
  private describeEnvironment(): void {
    const nav = navigator as Navigator & { standalone?: boolean };
    const vv = window.visualViewport;
    this.env.set([
      `installée=${isInstalledApp()}`,
      `navigator.standalone=${nav.standalone ?? '—'}`,
      `display-mode:standalone=${matchMedia('(display-mode: standalone)').matches}`,
      `écran=${window.innerWidth}×${window.innerHeight}`,
      `visualViewport=${vv ? Math.round(vv.height) : '—'}`,
    ].join(' · '));
  }

  /**
   * Écoute la chaîne complète d'un toucher sur le champ e-mail. En capture, pour voir l'évènement
   * même si quelqu'un l'arrête en route — c'est précisément l'hypothèse à écarter.
   */
  private watch(): void {
    const input = document.querySelector<HTMLInputElement>('#email');
    if (!input) {
      this.push('champ #email introuvable');
      return;
    }
    const vv = window.visualViewport;
    const baseline = vv ? Math.round(vv.height) : 0;

    for (const type of ['touchstart', 'pointerdown', 'mousedown', 'click', 'focus', 'blur'] as const) {
      input.addEventListener(type, () => {
        const active = document.activeElement;
        const who = active ? active.tagName.toLowerCase() + (active.id ? '#' + active.id : '') : 'aucun';
        this.push(`${type} → focus sur ${who}`);
      }, { capture: true });
    }

    // Le clavier ne s'annonce pas : il se déduit du rétrécissement du viewport visuel.
    vv?.addEventListener('resize', () => {
      const now = Math.round(vv.height);
      this.push(`viewport ${baseline} → ${now} (${now < baseline - 80 ? 'clavier ouvert' : 'inchangé'})`);
    });

    // Ce que le doigt touche vraiment, indépendamment de ce qui est peint à cet endroit.
    document.addEventListener('touchstart', (ev) => {
      const t = ev.touches[0];
      if (!t) return;
      const el = document.elementFromPoint(t.clientX, t.clientY);
      const tag = el ? el.tagName.toLowerCase() + (el.id ? '#' + el.id : '') : 'rien';
      this.push(`doigt en ${Math.round(t.clientX)},${Math.round(t.clientY)} → ${tag}`);
    }, { capture: true, passive: true });

    this.push('sonde active');
  }

  private push(line: string): void {
    const at = new Date().toLocaleTimeString('fr-FR', { minute: '2-digit', second: '2-digit' });
    this.lines.update((l) => [...l.slice(-24), `${at} ${line}`]);
  }
}
