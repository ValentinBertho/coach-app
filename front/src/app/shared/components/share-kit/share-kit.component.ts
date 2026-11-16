import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { IconComponent } from '../icon/icon.component';
import { ToastService } from '../../../core/services/toast.service';

/**
 * Le kit de partage d'un lien : le copier, l'envoyer par WhatsApp, SMS ou e-mail, le montrer en
 * QR code, ou passer par la feuille de partage du téléphone.
 *
 * <p>Pensé pour être réutilisé : la fiche publique du coach aujourd'hui, les liens d'invitation
 * demain. Aucun appel serveur — les liens « wa.me », « sms: » et « mailto: » ouvrent l'application
 * de la personne avec le message prêt, et c'est elle qui choisit à qui l'envoyer.</p>
 */
@Component({
  selector: 'app-share-kit',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    <div class="sk">
      <div class="sk__link">
        <app-icon name="link" [size]="16" />
        <span class="sk__url">{{ url() }}</span>
        <button type="button" class="btn btn-secondary btn-sm" (click)="copy()">
          <app-icon name="copy" [size]="15" /> {{ copied() ? 'Copié' : 'Copier' }}
        </button>
      </div>

      <div class="sk__actions">
        @if (canNativeShare) {
          <button type="button" class="btn btn-primary btn-sm" (click)="nativeShare()">
            <app-icon name="share" [size]="15" /> Partager
          </button>
        }
        <a class="btn btn-ghost btn-sm" [href]="whatsapp()" target="_blank" rel="noopener">
          <app-icon name="message-square" [size]="15" /> WhatsApp
        </a>
        <a class="btn btn-ghost btn-sm" [href]="sms()">
          <app-icon name="smartphone" [size]="15" /> SMS
        </a>
        <a class="btn btn-ghost btn-sm" [href]="mail()">
          <app-icon name="mail" [size]="15" /> E-mail
        </a>
        <button type="button" class="btn btn-ghost btn-sm" (click)="toggleQr()">
          <app-icon name="qr-code" [size]="15" /> QR code
        </button>
        @if (imageUrl()) {
          <a class="btn btn-ghost btn-sm" [href]="imageUrl()" [attr.download]="imageName()">
            <app-icon name="download" [size]="15" /> {{ imageLabel() }}
          </a>
        }
      </div>

      @if (qr(); as src) {
        <div class="sk__qr">
          <img [src]="src" width="220" height="220" [alt]="'QR code vers ' + url()" />
          <div>
            <p class="field-hint">
              À imprimer sur une affiche ou un flyer, ou à montrer depuis votre téléphone : on le
              scanne avec l'appareil photo.
            </p>
            <a class="btn btn-secondary btn-sm" [href]="src" [attr.download]="qrName()">
              <app-icon name="download" [size]="15" /> Télécharger le QR code
            </a>
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .sk { display: flex; flex-direction: column; gap: var(--sp-3); }
    .sk__link {
      display: flex; align-items: center; gap: var(--sp-2);
      padding: var(--sp-2) var(--sp-3); border: 1px solid var(--hairline);
      border-radius: var(--radius-md, 10px); background: var(--surface-2, var(--canvas));
      min-width: 0;
    }
    .sk__url {
      flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
      font-family: var(--font-mono, monospace); font-size: var(--text-sm);
    }
    .sk__actions { display: flex; flex-wrap: wrap; gap: var(--sp-2); }
    .sk__actions .btn { display: inline-flex; align-items: center; gap: 6px; }
    .sk__qr { display: flex; gap: var(--sp-4); align-items: center; flex-wrap: wrap; }
    .sk__qr img { background: #fff; padding: 8px; border-radius: 8px; }
  `],
})
export class ShareKitComponent {
  private readonly toast = inject(ToastService);

  /** Le lien à partager. */
  readonly url = input.required<string>();
  /** Le message qui accompagne le lien dans WhatsApp, un SMS ou un e-mail. */
  readonly message = input<string>('');
  /** Objet de l'e-mail et titre de la feuille de partage. */
  readonly title = input<string>('');
  /** Image téléchargeable (ex. format story), facultative. */
  readonly imageUrl = input<string | null>(null);
  readonly imageLabel = input<string>("Télécharger l'image");
  readonly imageName = input<string>('partage.png');
  readonly qrName = input<string>('qr-code.png');

  readonly copied = signal(false);
  readonly qr = signal<string | null>(null);
  readonly canNativeShare = typeof navigator !== 'undefined' && typeof navigator.share === 'function';

  private readonly text = computed(() => {
    const m = this.message().trim();
    return m ? `${m} ${this.url()}` : this.url();
  });
  readonly whatsapp = computed(() => `https://wa.me/?text=${encodeURIComponent(this.text())}`);
  /** « ?&body= » : la seule forme que lisent à la fois iOS et Android. */
  readonly sms = computed(() => `sms:?&body=${encodeURIComponent(this.text())}`);
  readonly mail = computed(() =>
    `mailto:?subject=${encodeURIComponent(this.title())}&body=${encodeURIComponent(this.text())}`);

  async copy(): Promise<void> {
    try {
      await navigator.clipboard.writeText(this.url());
      this.copied.set(true);
      setTimeout(() => this.copied.set(false), 2000);
    } catch {
      this.toast.error('Copie impossible : sélectionnez le lien et copiez-le à la main.');
    }
  }

  async nativeShare(): Promise<void> {
    try {
      await navigator.share({ title: this.title(), text: this.message(), url: this.url() });
    } catch {
      // Feuille fermée par la personne : rien à signaler.
    }
  }

  async toggleQr(): Promise<void> {
    if (this.qr()) {
      this.qr.set(null);
      return;
    }
    // `qrcode` est un module CommonJS : une fois empaqueté, ses fonctions sont sous `default`.
    const mod: any = await import('qrcode');
    const toDataURL = mod.toDataURL ?? mod.default?.toDataURL;
    this.qr.set(await toDataURL(this.url(), { margin: 1, width: 440 }));
  }
}
