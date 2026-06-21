import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ConfirmDialogComponent } from './shared/components/confirm-dialog/confirm-dialog.component';
import { ToastComponent } from './shared/components/toast/toast.component';

/**
 * Shell applicatif : router-outlet + conteneur global de toasts + modale de confirmation.
 */
@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, ToastComponent, ConfirmDialogComponent],
  template: `
    <router-outlet />
    <app-toast />
    <app-confirm-dialog />
  `,
})
export class AppComponent {}
