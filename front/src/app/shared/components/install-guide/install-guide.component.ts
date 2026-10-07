import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { PwaInstallService } from '../../../core/services/pwa-install.service';
import { isInstalledApp } from '../../../core/utils/installed-app';
import { BottomSheetComponent } from '../bottom-sheet/bottom-sheet.component';
import { IconComponent } from '../icon/icon.component';

/** Date (epoch ms) du dernier « Plus tard » : la fenêtre se tait ensuite pendant {@link SNOOZE_MS}. */
const SNOOZED_AT_KEY = 'darilab.install-guide.snoozedAt';

/** Deux semaines : assez pour ne pas harceler, assez court pour qu'un nouveau venu la recroise. */
export const SNOOZE_MS = 14 * 24 * 60 * 60 * 1000;

/** Laisser voir la page avant d'y poser quoi que ce soit — et laisser `beforeinstallprompt` arriver. */
const APPEAR_DELAY_MS = 2_000;

/** Plateforme vue du point de vue de l'installation : chacune a son geste. */
export type InstallPlatform = 'ios' | 'android' | null;

/**
 * Plateforme mobile capable d'installer l'application, ou {@code null} (bureau, navigateur intégré
 * d'une application tierce). Exportée pour être testée sans dépendre du navigateur qui exécute
 * les tests.
 *
 * <p>iPadOS se présente comme un Mac : seul l'écran tactile le trahit. Les navigateurs intégrés
 * (Facebook, Instagram, LinkedIn…) n'offrent pas l'ajout à l'écran d'accueil : décrire un geste
 * impossible ferait plus de mal que de bien.</p>
 */
export function detectInstallPlatform(userAgent: string, maxTouchPoints: number): InstallPlatform {
  if (/FBAN|FBAV|Instagram|LinkedInApp|Line\/|; wv\)/.test(userAgent)) return null;
  if (/iPhone|iPad|iPod/.test(userAgent)) return 'ios';
  if (/Macintosh/.test(userAgent) && maxTouchPoints > 1) return 'ios';
  if (/Android/.test(userAgent)) return 'android';
  return null;
}

/**
 * Fenêtre d'aide à l'installation, affichée aux visiteurs <b>mobiles</b> qui utilisent
 * l'application dans le navigateur.
 *
 * <h2>Pourquoi une fenêtre, en plus de la carte</h2>
 *
 * <p>La carte d'installation ({@code app-install-prompt}) ne vit que dans « Ma journée » du coach :
 * un athlète qui arrive par le lien d'invitation, depuis un mail, ne la croise jamais. Or c'est
 * lui, le plus souvent, qui ouvre l'application sur son téléphone — et sur iPhone, sans
 * installation, ni plein écran ni notifications. La fenêtre est donc montée à la racine, comme
 * l'invitation aux notifications, et apparaît sur n'importe quel écran, connexion comprise.</p>
 *
 * <h2>Les deux chemins</h2>
 *
 * <p>Android propose une invite native ({@code beforeinstallprompt}) : un bouton « Installer »
 * suffit. Quand elle manque (Firefox, Samsung Internet, invite déjà refusée), on décrit le geste
 * du menu. iOS n'a pas d'invite du tout : il faut décrire Partager → « Sur l'écran d'accueil ».</p>
 *
 * <p>Rien ne s'affiche dans l'application installée, sur ordinateur, ni pendant
 * {@link SNOOZE_MS} après un « Plus tard ».</p>
 */
@Component({
  selector: 'app-install-guide',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [BottomSheetComponent, IconComponent],
  template: `
    <app-bottom-sheet [open]="visible()" (openChange)="!$event && later()" title="Ajouter à l'écran d'accueil">
      <div class="ig">
        <p class="ig__lead">
          Cette application s'utilise comme une app. Ajoute-la à ton écran d'accueil pour
          l'ouvrir d'un tap, en plein écran{{ platform() === 'ios' ? ', et recevoir les notifications' : '' }}.
        </p>

        @if (platform() === 'android' && pwa.canInstall()) {
          <button type="button" class="btn btn-primary ig__cta" (click)="install()">
            <app-icon name="download" [size]="16" /> Installer l'application
          </button>
        } @else {
          <ol class="ig__steps">
            @if (platform() === 'ios') {
              <li>
                <span class="ig__ic"><app-icon name="share" [size]="20" /></span>
                <span>Touche le bouton <strong>Partager</strong> de ton navigateur</span>
              </li>
              <li>
                <span class="ig__ic"><app-icon name="square-plus" [size]="20" /></span>
                <span>Choisis <strong>« Sur l'écran d'accueil »</strong></span>
              </li>
            } @else {
              <li>
                <span class="ig__ic"><app-icon name="ellipsis-vertical" [size]="20" /></span>
                <span>Ouvre le <strong>menu</strong> de ton navigateur</span>
              </li>
              <li>
                <span class="ig__ic"><app-icon name="square-plus" [size]="20" /></span>
                <span>Choisis <strong>« Ajouter à l'écran d'accueil »</strong> ou <strong>« Installer l'application »</strong></span>
              </li>
            }
          </ol>
        }

        <button type="button" class="btn btn-ghost ig__later" (click)="later()">Plus tard</button>
      </div>
    </app-bottom-sheet>
  `,
  styles: [`
    .ig { display: flex; flex-direction: column; gap: var(--sp-4); }
    .ig__lead { margin: 0; color: var(--ink-2); }
    .ig__steps {
      list-style: none; margin: 0; padding: 0;
      display: flex; flex-direction: column; gap: var(--sp-3);
      border-top: 1px solid var(--hairline); padding-top: var(--sp-4);
    }
    .ig__steps li { display: flex; align-items: center; gap: var(--sp-3); }
    .ig__ic {
      width: 40px; height: 40px; flex-shrink: 0; border-radius: var(--radius-sm);
      display: flex; align-items: center; justify-content: center;
      background: var(--primary-wash); color: var(--primary);
    }
    .ig__cta, .ig__later { width: 100%; min-height: 46px; }
  `],
})
export class InstallGuideComponent implements OnInit {
  readonly pwa = inject(PwaInstallService);
  private readonly destroyRef = inject(DestroyRef);

  readonly platform = signal<InstallPlatform>(
    detectInstallPlatform(navigator.userAgent, navigator.maxTouchPoints ?? 0));
  private readonly snoozed = signal(isSnoozed(Date.now()));
  /** Passe à vrai après le délai d'apparition : rien ne s'affiche avant. */
  private readonly ready = signal(false);

  ngOnInit(): void {
    if (!this.platform() || this.snoozed() || isInstalledApp()) return;
    const timer = setTimeout(() => this.ready.set(true), APPEAR_DELAY_MS);
    this.destroyRef.onDestroy(() => clearTimeout(timer));
  }

  readonly visible = computed(() => this.ready() && !this.snoozed() && !!this.platform());

  async install(): Promise<void> {
    await this.pwa.promptInstall();
    // Acceptée ou refusée, l'invite native a eu lieu : la fenêtre n'a plus rien à dire.
    this.later();
  }

  later(): void {
    if (this.snoozed()) return;
    try { localStorage.setItem(SNOOZED_AT_KEY, String(Date.now())); } catch { /* stockage indisponible */ }
    this.snoozed.set(true);
  }
}

function isSnoozed(now: number): boolean {
  try {
    const at = Number(localStorage.getItem(SNOOZED_AT_KEY));
    return at > 0 && now - at < SNOOZE_MS;
  } catch {
    return false;
  }
}
