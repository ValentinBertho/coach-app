import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { PwaInstallService } from '../../../core/services/pwa-install.service';
import { IconComponent } from '../icon/icon.component';

/** Bouton « Installer l'app » affiché uniquement si l'installation PWA est possible. */
@Component({
  selector: 'app-install-button',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    @if (pwa.canInstall()) {
      <button type="button" class="btn btn-sm" [class.btn-primary]="!ghost()" [class.btn-ghost]="ghost()"
              (click)="pwa.promptInstall()">
        <app-icon name="download" [size]="15" /> Installer l'app
      </button>
    }
  `,
})
export class InstallButtonComponent {
  readonly ghost = input(false);
  readonly pwa = inject(PwaInstallService);
}
