import {
  ChangeDetectionStrategy, Component, ElementRef, HostListener, OnInit, inject, signal,
} from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { IconComponent } from '../icon/icon.component';
import { NotificationService, NotificationPreferences } from '../../../core/services/notification.service';
import { AppNotification } from '../../../core/models/notification.model';
import { SkeletonComponent } from '../skeleton/skeleton.component';

/**
 * Cloche du centre de notifications : badge de non-lues + panneau déroulant.
 * Réutilisable côté coach et athlète. Lecture seule, scopé par l'utilisateur du token.
 */
@Component({
  selector: 'app-notification-bell',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [SkeletonComponent, DatePipe, FormsModule, IconComponent],
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
        <div class="bell-panel" role="dialog" aria-label="Notifications"
             [style.--bell-top.px]="anchorTop()" [style.--bell-right.px]="anchorRight()">
          <header class="bell-hd">
            <strong>Notifications</strong>
            <span class="bell-hd-actions">
              @if (notif.unread() > 0) {
                <button type="button" class="btn btn-ghost btn-sm" (click)="markAll()">Tout marquer lu</button>
              }
              <button type="button" class="bell-gear" (click)="togglePrefs()" aria-label="Préférences">
                <app-icon name="settings" [size]="16" />
              </button>
            </span>
          </header>

          @if (showPrefs()) {
            <div class="bell-prefs">
              <span class="field-hint">Canaux (l'app reste toujours active)</span>
              <label class="pref"><input type="checkbox" [ngModel]="prefs().emailEnabled" (ngModelChange)="setPref('email', $event)" /> E-mail</label>
              <label class="pref"><input type="checkbox" [ngModel]="prefs().pushEnabled" (ngModelChange)="setPref('push', $event)" /> Push</label>
            </div>
          }

          @if (loading()) {
            <div class="bell-loading"><app-skeleton shape="text" [rows]="3" /></div>
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
      border-radius: var(--radius-full); background: var(--form-red); color: #fff; font-size: var(--text-2xs);
      font-weight: 800; display: inline-flex; align-items: center; justify-content: center; }

    .bell-backdrop { position: fixed; inset: 0; z-index: 300; }

    /* Positionné par rapport à l'ÉCRAN, pas à la cloche.
       En position absolue ancrée à droite, le panneau partait du bord droit du bouton — or la cloche
       n'est pas au bord de l'écran (l'avatar est à sa droite). Sur un téléphone, ses 360 px
       débordaient donc de l'écran par la GAUCHE : le titre et le début de chaque notification
       étaient tronqués, hors d'atteinte du doigt comme du défilement (cf. capture bêta). On ancre
       maintenant sur les coordonnées réelles du bouton, mesurées à l'ouverture, et la largeur est
       bornée par la fenêtre. */
    .bell-panel { position: fixed; top: var(--bell-top, 64px); right: var(--bell-right, 12px);
      z-index: 301; width: min(360px, calc(100vw - 2 * var(--sp-3)));
      max-height: min(70vh, calc(100dvh - var(--bell-top, 64px) - var(--sp-4)));
      overflow-y: auto; overscroll-behavior: contain;
      background: var(--paper); border: 1px solid var(--hairline);
      border-radius: var(--radius-lg); box-shadow: var(--shadow-lg, 0 12px 32px rgba(0,0,0,.18)); }

    /* Sous 640 px, aucun ancrage ne tient : un panneau lisible occupe presque toute la largeur,
       donc il s'étale entre les deux marges plutôt que de suivre le bouton. */
    @media (max-width: 640px) {
      .bell-panel { left: var(--sp-3); right: var(--sp-3); width: auto; }
    }

    .bell-hd { display: flex; align-items: center; justify-content: space-between; gap: var(--sp-2);
      padding: var(--sp-3) var(--sp-3); border-bottom: 1px solid var(--hairline); position: sticky; top: 0;
      background: var(--paper); z-index: 1; }
    /* Le titre cède la place aux actions plutôt que de les pousser hors du panneau. */
    .bell-hd > strong { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .bell-hd-actions { display: inline-flex; align-items: center; gap: var(--sp-1); flex-shrink: 0; }
    .bell-hd-actions .btn { white-space: nowrap; }
    .bell-gear { display: inline-flex; padding: 4px; border: none; background: transparent; color: var(--ink-3); cursor: pointer; border-radius: var(--radius); }
    .bell-gear:hover { background: var(--paper-sunk); color: var(--ink); }
    .bell-prefs { display: flex; flex-direction: column; gap: 4px; padding: var(--sp-3); border-bottom: 1px solid var(--hairline); background: var(--paper-sunk); }
    .bell-prefs .pref { display: flex; align-items: center; gap: var(--sp-2); font-weight: 600; color: var(--ink-2); }
    .bell-prefs input { width: 16px; height: 16px; }
    .bell-empty { padding: var(--sp-5); text-align: center; }
    .bell-loading { padding: var(--sp-4) var(--sp-5); }

    .bell-list { list-style: none; margin: 0; padding: 0; }
    .bell-row { display: flex; align-items: flex-start; gap: var(--sp-2); width: 100%; text-align: left;
      padding: var(--sp-3); background: transparent; border: none; border-top: 1px solid var(--hairline);
      cursor: pointer; }
    .bell-row:first-child { border-top: none; }
    .bell-row:hover { background: var(--paper-sunk); }
    .bell-row.unread { background: var(--primary-wash, var(--paper-sunk)); }
    .dot { width: 8px; height: 8px; border-radius: 50%; background: var(--primary); margin-top: 6px; flex-shrink: 0; }
    /* flex: 1 avec min-width: 0 — sans le premier, le corps gardait sa largeur naturelle et un
       titre long poussait la colonne au-delà du panneau au lieu de revenir à la ligne. */
    .bell-row-body { display: flex; flex-direction: column; gap: 2px; flex: 1; min-width: 0; }
    .bell-row-title { font-weight: 700; color: var(--ink); overflow-wrap: anywhere; }
    .bell-row-text { font-size: var(--text-sm); color: var(--ink-2); overflow-wrap: anywhere; }
    .bell-row-date { font-size: var(--text-xs); color: var(--ink-4); }
  `],
})
export class NotificationBellComponent implements OnInit {
  protected readonly notif = inject(NotificationService);
  private readonly router = inject(Router);
  private readonly host = inject(ElementRef<HTMLElement>);

  readonly open = signal(false);
  /** Coordonnées du panneau, mesurées sur le bouton à l'ouverture (cf. styles). */
  readonly anchorTop = signal(64);
  readonly anchorRight = signal(12);
  readonly loading = signal(false);
  readonly items = signal<AppNotification[]>([]);
  readonly showPrefs = signal(false);
  /**
   * Valeurs de départ avant la première réponse du serveur. Le panneau de la cloche ne règle que
   * les deux canaux ; les familles et les heures de silence se règlent sur l'écran dédié, où il y
   * a la place de les expliquer.
   */
  readonly prefs = signal<NotificationPreferences>({
    emailEnabled: true, pushEnabled: true, usualSessionTime: null,
    mutedCategories: [], quietStart: null, quietEnd: null, timezone: null,
  });

  ngOnInit(): void {
    this.notif.refreshUnread().subscribe({ error: () => {} });
  }

  toggle(): void {
    if (this.open()) { this.close(); return; }
    this.measure();
    this.open.set(true);
    this.loading.set(true);
    this.notif.list().subscribe({
      next: (n) => { this.items.set(n); this.loading.set(false); },
      error: () => { this.items.set([]); this.loading.set(false); },
    });
  }

  close(): void { this.open.set(false); this.showPrefs.set(false); }

  /**
   * Place le panneau sous le bouton, en coordonnées d'écran.
   *
   * <p>Mesuré à l'ouverture plutôt que déduit du flux : la cloche vit dans trois barres
   * différentes (menu latéral coach, barre mobile coach, barre du portail athlète), à des
   * distances du bord qu'aucune règle CSS ne connaît d'avance. Le décalage droit est plafonné pour
   * qu'un panneau de 360 px reste entier à l'écran — c'est exactement ce qui manquait quand il
   * débordait par la gauche.</p>
   */
  private measure(): void {
    const button = (this.host.nativeElement as HTMLElement).querySelector('.bell-btn');
    if (!button) { return; }
    const rect = button.getBoundingClientRect();
    // Marge et largeur maximale du panneau : les mêmes que la feuille de style (--sp-3 = 12px),
    // sinon le plafonnement calculé ici et le rendu réel ne parleraient pas de la même boîte.
    const margin = 12;
    const width = Math.min(360, window.innerWidth - 2 * margin);
    const fromRight = window.innerWidth - rect.right;
    this.anchorTop.set(Math.round(rect.bottom + 6));
    this.anchorRight.set(Math.round(
      Math.min(Math.max(fromRight, margin), Math.max(window.innerWidth - width - margin, margin))
    ));
  }

  /** La rotation d'un téléphone laisserait sinon le panneau à côté de sa cloche. */
  @HostListener('window:resize')
  onResize(): void {
    if (this.open()) { this.measure(); }
  }

  togglePrefs(): void {
    const next = !this.showPrefs();
    this.showPrefs.set(next);
    if (next) {
      this.notif.preferences().subscribe({ next: (p) => this.prefs.set(p), error: () => {} });
    }
  }

  setPref(channel: 'email' | 'push', enabled: boolean): void {
    const patch = channel === 'email' ? { emailEnabled: enabled } : { pushEnabled: enabled };
    this.prefs.update((p) => ({ ...p, ...patch }));
    this.notif.savePreferences(patch).subscribe({
      next: (p) => this.prefs.set(p),
      error: () => {},
    });
  }

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
