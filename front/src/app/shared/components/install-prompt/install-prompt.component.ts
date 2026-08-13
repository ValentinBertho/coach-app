import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { PwaInstallService } from '../../../core/services/pwa-install.service';
import { isInstalledApp } from '../../../core/utils/installed-app';
import { IconComponent } from '../icon/icon.component';

/** Mémoire du refus, distincte de celle des notifications : ce sont deux gestes différents. */
const DISMISSED_KEY = 'darilab.install-prompt.dismissed';

/**
 * Invitation à installer l'application sur l'écran d'accueil.
 *
 * <p><b>Pourquoi c'est une carte et pas un bouton.</b> Le bouton d'installation existait déjà,
 * mais uniquement là où on ne le croise pas : le profil de l'athlète et la page publique. Or sur
 * <b>iPhone, le web push n'existe que dans une application installée</b> — un coach qui n'a pas
 * ajouté Darilab à son écran d'accueil ne peut pas être prévenu d'une blessure déclarée, et rien
 * ne le lui disait. L'installation n'est donc pas un confort : c'est le préalable des
 * notifications, et elle doit croiser celui qui en a besoin.</p>
 *
 * <p><b>Les deux chemins.</b> Android et les navigateurs de bureau proposent une invite native
 * (<code>beforeinstallprompt</code>) : un bouton suffit. iOS n'en a pas — il faut décrire le
 * geste, Partager puis « Sur l'écran d'accueil », sans quoi la moitié des coachs reste sur le
 * bord du chemin sans savoir pourquoi.</p>
 *
 * <p>Rien ne s'affiche si l'application tourne déjà installée.</p>
 */
@Component({
  selector: 'app-install-prompt',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    @if (visible()) {
      <section class="iprompt card">
        <span class="iprompt__ic"><app-icon name="download" [size]="18" /></span>
        <div class="iprompt__txt">
          <strong>{{ title() }}</strong>
          @if (iOS()) {
            <span class="field-hint">
              {{ lead() }} Touche <strong>Partager</strong> en bas de Safari, puis
              « <strong>Sur l'écran d'accueil</strong> ».
            </span>
          } @else {
            <span class="field-hint">{{ lead() }}</span>
          }
        </div>
        <div class="iprompt__act">
          @if (pwa.canInstall()) {
            <button type="button" class="btn btn-primary btn-sm" (click)="install()">Installer</button>
          }
          <button type="button" class="btn btn-ghost btn-sm" (click)="dismiss()">Plus tard</button>
        </div>
      </section>
    }
  `,
  styles: [`
    .iprompt { display: flex; align-items: flex-start; gap: var(--sp-3); }
    .iprompt__ic {
      width: 36px; height: 36px; flex-shrink: 0; border-radius: var(--radius-sm);
      display: flex; align-items: center; justify-content: center;
      background: var(--primary-wash); color: var(--primary);
    }
    .iprompt__txt { display: flex; flex-direction: column; gap: 2px; flex: 1; min-width: 0; }
    .iprompt__act { display: flex; flex-direction: column; gap: var(--sp-2); flex-shrink: 0; }
    @media (max-width: 480px) {
      .iprompt { flex-wrap: wrap; }
      .iprompt__act { flex-direction: row; width: 100%; }
    }
  `],
})
export class InstallPromptComponent {
  readonly title = input("Garder Darilab à portée de pouce");
  readonly lead = input(
    "Installée sur ton écran d'accueil, l'application s'ouvre d'un tap — et c'est la seule "
    + 'façon de recevoir les notifications sur iPhone.');

  readonly pwa = inject(PwaInstallService);
  private readonly dismissed = signal(read(DISMISSED_KEY));

  /** iOS ne connaît pas `beforeinstallprompt` : il faut décrire le geste au lieu de l'offrir. */
  iOS(): boolean {
    return /iP(hone|ad|od)/.test(navigator.userAgent);
  }

  /**
   * On propose quand il y a quelque chose à proposer : une invite native disponible, ou un iPhone
   * où l'installation est manuelle. Jamais dans une application déjà installée.
   */
  visible(): boolean {
    if (this.dismissed() || isInstalledApp()) return false;
    return this.pwa.canInstall() || this.iOS();
  }

  install(): void {
    void this.pwa.promptInstall();
  }

  dismiss(): void {
    try { localStorage.setItem(DISMISSED_KEY, '1'); } catch { /* stockage indisponible */ }
    this.dismissed.set(true);
  }
}

function read(key: string): boolean {
  try { return localStorage.getItem(key) === '1'; } catch { return false; }
}
