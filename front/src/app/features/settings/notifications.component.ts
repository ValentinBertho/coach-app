import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AppNotification } from '../../core/models/notification.model';
import { NotificationPreferences, NotificationService } from '../../core/services/notification.service';
import { ToastService } from '../../core/services/toast.service';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { SkeletonComponent } from '../../shared/components/skeleton/skeleton.component';

/**
 * Centre de notifications du coach : historique réel + réglage des canaux.
 *
 * <p>Cet écran était une maquette figée — une liste de « types d'alertes » en dur qui ne
 * correspondait à aucun code réellement émis (elle mentionnait Garmin, qui n'existe pas), et un
 * état vide « Aucune notification pour le moment » écrit dans le gabarit, donc affiché même à un
 * coach qui en avait des dizaines. Aucun service n'était injecté.</p>
 *
 * <p>C'est pourtant la destination du lien « Gérer mes notifications » et de l'en-tête
 * {@code List-Unsubscribe} de chaque e-mail : le destinataire arrivait sur une page qui lui
 * affirmait qu'il n'avait rien et ne lui proposait aucun réglage. Les vraies cases existaient,
 * mais cachées derrière l'engrenage de la cloche.</p>
 */
@Component({
  selector: 'app-notifications',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, SkeletonComponent, DatePipe],
  template: `
    <section class="page-header">
      <div>
        <h1 class="display-sm">Notifications</h1>
        <p class="subtitle">Ton historique, et les canaux par lesquels Darilab te joint.</p>
      </div>
      @if (unread() > 0) {
        <button type="button" class="btn btn-ghost btn-sm" (click)="markAllRead()">Tout marquer lu</button>
      }
    </section>

    <div class="card">
      <h2>Comment je suis prévenu</h2>
      <p class="field-hint">
        Le centre de notifications ci-dessous reste toujours actif. Ces deux réglages ne portent
        que sur les rappels envoyés en dehors de l'app.
      </p>

      @if (prefs(); as p) {
        <ul class="chan">
          <li class="chan__row">
            <span class="chan__ic"><app-icon name="bell" [size]="18" /></span>
            <div class="chan__txt">
              <strong>Notifications push</strong>
              <span class="field-hint">
                Séance planifiée, retour d'un athlète, rappel de séance. C'est le canal du
                quotidien — instantané, et sans encombrer ta boîte mail.
              </span>
            </div>
            <label class="switch">
              <input type="checkbox" [checked]="p.pushEnabled" (change)="setChannel('push', $event)"
                     aria-label="Recevoir les notifications push" />
              <span class="switch__track" aria-hidden="true"></span>
            </label>
          </li>
          <li class="chan__row">
            <span class="chan__ic"><app-icon name="mail" [size]="18" /></span>
            <div class="chan__txt">
              <strong>E-mails</strong>
              <span class="field-hint">
                Uniquement le récapitulatif quotidien d'alertes et les indisponibilités déclarées.
                Les e-mails de compte (mot de passe, invitations) partent toujours : ils ne sont
                pas des notifications.
              </span>
            </div>
            <label class="switch">
              <input type="checkbox" [checked]="p.emailEnabled" (change)="setChannel('email', $event)"
                     aria-label="Recevoir les e-mails de notification" />
              <span class="switch__track" aria-hidden="true"></span>
            </label>
          </li>
        </ul>
      } @else {
        <app-skeleton shape="text" [rows]="2" />
      }
    </div>

    <div class="card">
      <h2>Historique</h2>
      @if (loading()) {
        <app-skeleton shape="text" [rows]="4" />
      } @else if (items().length === 0) {
        <div class="empty-state">
          <app-icon name="bell" [size]="32" />
          <p class="field-hint">
            Rien pour l'instant. Les alertes de forme et de charge, les retours de séance et les
            messages de tes athlètes apparaîtront ici.
          </p>
        </div>
      } @else {
        <ul class="nlist">
          @for (n of items(); track n.id) {
            <li>
              <button type="button" class="nrow" [class.unread]="!n.read" (click)="open(n)">
                @if (!n.read) { <span class="dot" aria-hidden="true"></span> }
                <span class="nrow__body">
                  <span class="nrow__title">{{ n.title }}</span>
                  @if (n.body) { <span class="nrow__text">{{ n.body }}</span> }
                  <span class="nrow__date">{{ n.createdAt | date: 'EEE d MMM, HH:mm' }}</span>
                </span>
                @if (n.link) { <app-icon name="chevron-right" [size]="16" /> }
              </button>
            </li>
          }
        </ul>
      }
    </div>
  `,
  styles: [`
    .chan { list-style: none; margin: var(--sp-3) 0 0; padding: 0; display: flex; flex-direction: column; gap: var(--sp-4); }
    .chan__row { display: flex; align-items: flex-start; gap: var(--sp-3); }
    .chan__ic {
      width: 36px; height: 36px; flex-shrink: 0; border-radius: var(--radius-sm);
      display: flex; align-items: center; justify-content: center;
      background: var(--primary-wash); color: var(--primary);
    }
    .chan__txt { display: flex; flex-direction: column; gap: 2px; flex: 1; min-width: 0; }

    /* Interrupteur : cible ≥44px, état lisible sans la couleur seule (le pouce se déplace). */
    .switch { position: relative; flex-shrink: 0; width: 52px; height: 44px; cursor: pointer; }
    .switch input { position: absolute; inset: 0; opacity: 0; width: 100%; height: 100%; margin: 0; cursor: pointer; }
    .switch__track {
      position: absolute; top: 11px; left: 0; width: 52px; height: 30px;
      border-radius: var(--radius-full); background: var(--paper-sunk);
      border: 1px solid var(--hairline); transition: background var(--duration) var(--ease);
    }
    .switch__track::after {
      content: ''; position: absolute; top: 3px; left: 3px; width: 22px; height: 22px;
      border-radius: var(--radius-full); background: var(--paper);
      box-shadow: var(--shadow-sm); transition: transform var(--duration) var(--ease);
    }
    .switch input:checked + .switch__track { background: var(--primary); border-color: var(--primary); }
    .switch input:checked + .switch__track::after { transform: translateX(22px); }
    .switch input:focus-visible + .switch__track { outline: 2px solid var(--primary); outline-offset: 2px; }

    .nlist { list-style: none; margin: var(--sp-2) 0 0; padding: 0; }
    .nlist li + li { border-top: 1px solid var(--hairline); }
    .nrow {
      display: flex; align-items: center; gap: var(--sp-2); width: 100%;
      min-height: 44px; padding: var(--sp-3) 0; text-align: left;
      background: none; border: none; cursor: pointer; color: inherit;
    }
    .nrow__body { display: flex; flex-direction: column; gap: 2px; flex: 1; min-width: 0; }
    .nrow__title { font-weight: 600; font-size: var(--text-sm); }
    .nrow__text { font-size: var(--text-sm); color: var(--ink-2); }
    .nrow__date { font-size: var(--text-xs); color: var(--ink-3); }
    .nrow.unread .nrow__title { font-weight: 800; }
    .dot { width: 8px; height: 8px; border-radius: var(--radius-full); background: var(--primary); flex-shrink: 0; }
  `],
})
export class NotificationsComponent implements OnInit {
  private readonly notif = inject(NotificationService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  readonly items = signal<AppNotification[]>([]);
  readonly prefs = signal<NotificationPreferences | null>(null);
  readonly loading = signal(true);
  readonly unread = this.notif.unread;

  ngOnInit(): void {
    this.notif.list().subscribe({
      next: (list) => { this.items.set(list); this.loading.set(false); },
      error: () => { this.loading.set(false); this.toast.error('Chargement des notifications impossible.'); },
    });
    this.notif.preferences().subscribe({ next: (p) => this.prefs.set(p), error: () => {} });
  }

  setChannel(channel: 'email' | 'push', event: Event): void {
    const enabled = (event.target as HTMLInputElement).checked;
    const patch = channel === 'email' ? { emailEnabled: enabled } : { pushEnabled: enabled };
    this.notif.savePreferences(patch).subscribe({
      next: (p) => {
        this.prefs.set(p);
        this.toast.success(enabled ? 'Canal activé' : 'Canal désactivé');
      },
      error: () => this.toast.error('Enregistrement impossible.'),
    });
  }

  markAllRead(): void {
    this.notif.markAllRead().subscribe({
      next: () => this.items.update((l) => l.map((n) => ({ ...n, read: true }))),
      error: () => this.toast.error('Action impossible.'),
    });
  }

  open(n: AppNotification): void {
    if (!n.read) {
      this.notif.markRead(n.id).subscribe({ error: () => {} });
      this.items.update((l) => l.map((x) => (x.id === n.id ? { ...x, read: true } : x)));
    }
    if (n.link) {
      this.router.navigateByUrl(n.link);
    }
  }
}
