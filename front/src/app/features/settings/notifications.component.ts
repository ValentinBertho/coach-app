import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AppNotification } from '../../core/models/notification.model';
import {
  NotificationCategory, NotificationPreferences, NotificationService,
} from '../../core/services/notification.service';
import { PushDevice, PushService } from '../../core/services/push.service';
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

    @if (prefs(); as p) {
      <div class="card">
        <h2>Ce qui me fait sonner</h2>
        <p class="field-hint">
          Chaque famille peut être coupée séparément. Couper une famille ne fait taire que la
          notification système : tout continue d'arriver dans l'historique ci-dessous.
        </p>
        <ul class="chan">
          @for (c of categories; track c.key) {
            <li class="chan__row">
              <span class="chan__ic"><app-icon [name]="c.icon" [size]="18" /></span>
              <div class="chan__txt">
                <strong>{{ c.label }}</strong>
                <span class="field-hint">{{ c.hint }}</span>
              </div>
              <label class="switch">
                <input type="checkbox" [checked]="!isMuted(c.key)"
                       (change)="setCategory(c.key, $event)"
                       [attr.aria-label]="'Recevoir : ' + c.label" />
                <span class="switch__track" aria-hidden="true"></span>
              </label>
            </li>
          }
        </ul>
      </div>

      <div class="card">
        <h2>Heures de silence</h2>
        <p class="field-hint">
          Aucune notification système pendant cette plage. Elles restent dans l'historique et
          s'y retrouvent au réveil. Deux heures identiques désactivent le silence.
        </p>
        <div class="quiet">
          <label class="quiet__f">
            <span class="field-hint">De</span>
            <input type="time" [value]="p.quietStart ?? ''" (change)="setQuiet('start', $event)"
                   aria-label="Début des heures de silence" />
          </label>
          <label class="quiet__f">
            <span class="field-hint">À</span>
            <input type="time" [value]="p.quietEnd ?? ''" (change)="setQuiet('end', $event)"
                   aria-label="Fin des heures de silence" />
          </label>
        </div>
      </div>
    }

    <div class="card">
      <h2>Mes appareils</h2>
      <p class="field-hint">
        Les appareils qui reçoivent tes notifications. Retire ceux que tu n'utilises plus —
        un téléphone revendu reste abonné tant que personne ne l'a retiré.
      </p>
      @if (devices() === null) {
        <app-skeleton shape="text" [rows]="2" />
      } @else if (devices()!.length === 0) {
        <p class="field-hint">Aucun appareil abonné pour l'instant.</p>
      } @else {
        <ul class="dlist">
          @for (d of devices(); track d.id) {
            <li class="drow">
              <span class="chan__ic"><app-icon name="smartphone" [size]="18" /></span>
              <div class="chan__txt">
                <strong>{{ d.label }}</strong>
                <span class="field-hint">
                  Ajouté le {{ d.createdAt | date: 'd MMM y' }}@if (d.lastSuccessAt) {
                    · vu {{ d.lastSuccessAt | date: 'd MMM, HH:mm' }}
                  }
                </span>
              </div>
              <button type="button" class="btn btn-ghost btn-sm" (click)="removeDevice(d)">Retirer</button>
            </li>
          }
        </ul>
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

    .dlist { list-style: none; margin: var(--sp-3) 0 0; padding: 0; display: flex; flex-direction: column; gap: var(--sp-3); }
    .drow { display: flex; align-items: flex-start; gap: var(--sp-3); }

    .quiet { display: flex; gap: var(--sp-4); margin-top: var(--sp-3); flex-wrap: wrap; }
    .quiet__f { display: flex; flex-direction: column; gap: 2px; }
    .quiet__f input {
      min-height: 44px; padding: 0 var(--sp-2); border-radius: var(--radius-sm);
      border: 1px solid var(--hairline); background: var(--paper); color: inherit;
      font-size: var(--text-sm);
    }

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
  private readonly push = inject(PushService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  readonly items = signal<AppNotification[]>([]);
  readonly prefs = signal<NotificationPreferences | null>(null);
  /** `null` tant que la liste n'est pas revenue — à distinguer de « aucun appareil ». */
  readonly devices = signal<PushDevice[] | null>(null);
  readonly loading = signal(true);
  readonly unread = this.notif.unread;

  /**
   * Les quatre familles, dans l'ordre où elles comptent pour celui qui lit l'écran. Les libellés
   * décrivent ce qu'on reçoit, pas le code émis : personne n'a à savoir qu'il existe un type
   * `WORKOUT_UPDATED`.
   */
  readonly categories: ReadonlyArray<{
    key: NotificationCategory; label: string; hint: string; icon: string;
  }> = [
    {
      key: 'PROGRAMME', label: 'Mon programme', icon: 'calendar',
      hint: 'Nouvelle séance, plan attribué, séance déplacée ou annulée.',
    },
    {
      key: 'RAPPELS', label: 'Rappels', icon: 'timer',
      hint: 'La séance de demain, et « ta séance est finie ? » après l\'effort.',
    },
    {
      key: 'MESSAGES', label: 'Messages', icon: 'message-square',
      hint: 'Messages du fil et retours de séance.',
    },
    {
      key: 'SUIVI', label: 'Suivi des athlètes', icon: 'activity',
      hint: 'Retours, alertes de charge, douleurs signalées, indisponibilités.',
    },
  ];

  ngOnInit(): void {
    this.notif.list().subscribe({
      next: (list) => { this.items.set(list); this.loading.set(false); },
      error: () => { this.loading.set(false); this.toast.error('Chargement des notifications impossible.'); },
    });
    this.notif.preferences().subscribe({ next: (p) => this.prefs.set(p), error: () => {} });
    this.push.devices().subscribe({ next: (d) => this.devices.set(d), error: () => this.devices.set([]) });
  }

  /**
   * Retire un appareil. Si c'est celui qu'on utilise, on défait aussi l'abonnement du navigateur :
   * sans ça il se croirait encore abonné, et le serveur ne saurait plus où pousser — l'appareil
   * cesserait d'être notifié sans que rien ne le signale.
   */
  removeDevice(device: PushDevice): void {
    this.push.removeDevice(device.id).subscribe({
      next: async () => {
        this.devices.update((list) => (list ?? []).filter((d) => d.id !== device.id));
        this.toast.success('Appareil retiré');
        if (await this.push.currentFingerprint() === device.fingerprint) {
          await this.push.forgetLocalSubscription();
        }
      },
      error: () => this.toast.error('Retrait impossible.'),
    });
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

  isMuted(key: NotificationCategory): boolean {
    return (this.prefs()?.mutedCategories ?? []).includes(key);
  }

  /**
   * La case cochée veut dire « je veux les recevoir » ; le serveur, lui, stocke l'inverse — la
   * liste de ce qui est coupé. On lui renvoie la liste complète, pas un delta.
   */
  setCategory(key: NotificationCategory, event: Event): void {
    const wanted = (event.target as HTMLInputElement).checked;
    const current = new Set(this.prefs()?.mutedCategories ?? []);
    if (wanted) { current.delete(key); } else { current.add(key); }
    this.save({ mutedCategories: [...current] }, wanted ? 'Notifications réactivées' : 'Notifications coupées');
  }

  setQuiet(bound: 'start' | 'end', event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.save(bound === 'start' ? { quietStart: value } : { quietEnd: value }, 'Heures de silence mises à jour');
  }

  private save(patch: Partial<NotificationPreferences>, message: string): void {
    this.notif.savePreferences(patch).subscribe({
      next: (p) => { this.prefs.set(p); this.toast.success(message); },
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
