import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { PwaInstallService } from '../../../core/services/pwa-install.service';

/** Bouton « Installer l'app » affiché uniquement si l'installation PWA est possible. */
@Component({
  selector: 'app-install-button',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (pwa.canInstall()) {
      <button type="button" class="btn btn-sm" [class.btn-primary]="!ghost()" [class.btn-ghost]="ghost()"
              (click)="pwa.promptInstall()">
        ⬇ Installer l'app
      </button>
    }
  `,
})
export class InstallButtonComponent {
  readonly ghost = input(false);
  readonly pwa = inject(PwaInstallService);
}
