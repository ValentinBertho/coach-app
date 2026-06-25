import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Router } from '@angular/router';
import { IconComponent } from '../icon/icon.component';
import { NotificationService } from '../../../core/services/notification.service';
import { AppNotification } from '../../../core/models/notification.model';

/**
 * Cloche du centre de notifications : badge de non-lues + panneau déroulant.
 * Réutilisable côté coach et athlète. Lecture seule, scopé par l'utilisateur du token.
 */
@Component({
  selector: 'app-notification-bell',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, IconComponent],
  template: `
    <div class="bell">
      <button type="button" class="bell-btn" (click)="toggle()" [attr.aria-label]="'Notifications'"
              [attr.aria-expanded]="open()">
        <app-icon name="bell" [size]="20" />
        @if (notif.unread() > 0) {
          <span class="bell-badge">{{ notif.unread() > 9 ? '9+' : notif.unread() }}</span>
        }
      </button>

      @if (open()) {
        <div class="bell-backdrop" (click)="close()"></div>
        <div class="bell-panel" role="dialog" aria-label="Notifications">
          <header class="bell-hd">
            <strong>Notifications</strong>
            @if (notif.unread() > 0) {
              <button type="button" class="btn btn-ghost btn-sm" (click)="markAll()">Tout marquer lu</button>
            }
          </header>

          @if (loading()) {
            <div class="bell-empty field-hint">Chargement…</div>
          } @else if (items().length === 0) {
            <div class="bell-empty field-hint">Aucune notification.</div>
          } @else {
            <ul class="bell-list">
              @for (n of items(); track n.id) {
                <li>
                  <button type="button" class="bell-row" [class.unread]="!n.read" (click)="onClick(n)">
                    @if (!n.read) { <span class="dot" aria-hidden="true"></span> }
                    <span class="bell-row-body">
                      <span class="bell-row-title">{{ n.title }}</span>
                      @if (n.body) { <span class="bell-row-text">{{ n.body }}</span> }
                      <span class="bell-row-date">{{ n.createdAt | date: 'dd/MM HH:mm' }}</span>
                    </span>
                  </button>
                </li>
              }
            </ul>
          }
        </div>
      }
    </div>
  `,
  styles: [`
    .bell { position: relative; display: inline-flex; }
    .bell-btn { position: relative; display: inline-flex; align-items: center; justify-content: center;
      width: 40px; height: 40px; border-radius: var(--radius-full); border: none; background: transparent;
      color: var(--ink-2); cursor: pointer; }
    .bell-btn:hover { background: var(--paper-sunk); color: var(--ink); }
    .bell-badge { position: absolute; top: 4px; right: 4px; min-width: 16px; height: 16px; padding: 0 4px;
      border-radius: var(--radius-full); background: var(--form-red); color: #fff; font-size: 10px;
      font-weight: 800; display: inline-flex; align-items: center; justify-content: center; }

    .bell-backdrop { position: fixed; inset: 0; z-index: 300; }
    .bell-panel { position: absolute; right: 0; top: calc(100% + 6px); z-index: 301; width: min(360px, 92vw);
      max-height: 70vh; overflow: auto; background: var(--paper); border: 1px solid var(--hairline);
      border-radius: var(--radius-lg); box-shadow: var(--shadow-lg, 0 12px 32px rgba(0,0,0,.18)); }
    .bell-hd { display: flex; align-items: center; justify-content: space-between; gap: var(--sp-2);
      padding: var(--sp-3) var(--sp-3); border-bottom: 1px solid var(--hairline); position: sticky; top: 0;
      background: var(--paper); }
    .bell-empty { padding: var(--sp-5); text-align: center; }

    .bell-list { list-style: none; margin: 0; padding: 0; }
    .bell-row { display: flex; align-items: flex-start; gap: var(--sp-2); width: 100%; text-align: left;
      padding: var(--sp-3); background: transparent; border: none; border-top: 1px solid var(--hairline);
      cursor: pointer; }
    .bell-row:first-child { border-top: none; }
    .bell-row:hover { background: var(--paper-sunk); }
    .bell-row.unread { background: var(--primary-wash, var(--paper-sunk)); }
    .dot { width: 8px; height: 8px; border-radius: 50%; background: var(--primary); margin-top: 6px; flex-shrink: 0; }
    .bell-row-body { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
    .bell-row-title { font-weight: 700; color: var(--ink); }
    .bell-row-text { font-size: var(--text-sm); color: var(--ink-2); }
    .bell-row-date { font-size: var(--text-xs); color: var(--ink-4); }
  `],
})
export class NotificationBellComponent implements OnInit {
  protected readonly notif = inject(NotificationService);
  private readonly router = inject(Router);

  readonly open = signal(false);
  readonly loading = signal(false);
  readonly items = signal<AppNotification[]>([]);

  ngOnInit(): void {
    this.notif.refreshUnread().subscribe({ error: () => {} });
  }

  toggle(): void {
    if (this.open()) { this.close(); return; }
    this.open.set(true);
    this.loading.set(true);
    this.notif.list().subscribe({
      next: (n) => { this.items.set(n); this.loading.set(false); },
      error: () => { this.items.set([]); this.loading.set(false); },
    });
  }

  close(): void { this.open.set(false); }

  onClick(n: AppNotification): void {
    if (!n.read) {
      this.notif.markRead(n.id).subscribe({ error: () => {} });
      this.items.update((list) => list.map((x) => (x.id === n.id ? { ...x, read: true } : x)));
    }
    this.close();
    if (n.link) { this.router.navigateByUrl(n.link); }
  }

  markAll(): void {
    this.notif.markAllRead().subscribe({ error: () => {} });
    this.items.update((list) => list.map((x) => ({ ...x, read: true })));
  }
}
