import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Conversation, MessageService } from '../../core/services/message.service';
import { ToastService } from '../../core/services/toast.service';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { HelpHintComponent } from '../help/help-hint.component';

/**
 * Boîte de réception coach : toutes les conversations, tous athlètes confondus.
 *
 * La messagerie n'était accessible qu'athlète par athlète, sans aucun endroit qui dise
 * « 3 messages non lus ». Une ligne par fil (athlète, dernier message, horodatage, non-lus),
 * du plus récent au plus ancien ; le clic ouvre le fil existant.
 */
@Component({
  selector: 'app-message-inbox',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, DatePipe, IconComponent, HelpHintComponent],
  template: `
    <section class="page-header">
      <div>
        <h1 class="display-sm">Messages <app-help-hint section="communication" label="Aide : messagerie" /></h1>
        <p class="subtitle">
          @if (unread() > 0) {
            {{ unread() }} message{{ unread() > 1 ? 's' : '' }} non lu{{ unread() > 1 ? 's' : '' }}, tous athlètes confondus.
          } @else {
            Vos conversations avec vos athlètes, de la plus récente à la plus ancienne.
          }
        </p>
      </div>
    </section>

    @if (loading()) {
      <div class="card"><div class="skeleton" style="height: 140px;"></div></div>
    } @else if (conversations().length === 0) {
      <div class="card empty-state">
        <h2>Aucune conversation</h2>
        <p class="field-hint">
          Ouvrez la fiche d'un athlète puis son onglet Messages pour démarrer un échange.
        </p>
        <a routerLink="/app/athletes" class="btn btn-primary">Voir mes athlètes</a>
      </div>
    } @else {
      <div class="card">
        <ul class="inbox">
          @for (c of conversations(); track c.athleteId) {
            <li>
              <a class="inbox__row" [class.inbox__row--unread]="c.unreadCount > 0"
                 [routerLink]="['/app/athletes', c.athleteId, 'messages']">
                <span class="avatar avatar-sm">{{ c.firstName[0] }}{{ c.lastName[0] }}</span>
                <span class="inbox__body">
                  <span class="inbox__top">
                    <strong>{{ c.firstName }} {{ c.lastName }}</strong>
                    <span class="field-hint metric">{{ c.lastMessageAt | date: 'd MMM, HH:mm' }}</span>
                  </span>
                  <span class="inbox__preview">
                    @if (c.lastSenderRole === 'COACH') { <span class="inbox__me">Vous :</span> }
                    {{ excerpt(c.lastMessage) }}
                  </span>
                </span>
                @if (c.unreadCount > 0) {
                  <span class="inbox__badge">{{ c.unreadCount > 9 ? '9+' : c.unreadCount }}</span>
                } @else {
                  <app-icon class="inbox__go" name="chevron-right" [size]="18" />
                }
              </a>
            </li>
          }
        </ul>
      </div>
    }
  `,
  styles: [`
    .inbox { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; }
    .inbox__row {
      display: flex; align-items: center; gap: var(--sp-3);
      padding: var(--sp-3); text-decoration: none; color: var(--ink);
      border-bottom: 1px solid var(--hairline);
    }
    .inbox li:last-child .inbox__row { border-bottom: none; }
    .inbox__row:hover { background: var(--paper-sunk); }
    .inbox__body { display: flex; flex-direction: column; gap: 2px; flex: 1; min-width: 0; }
    .inbox__top { display: flex; align-items: baseline; justify-content: space-between; gap: var(--sp-2); }
    .inbox__preview {
      font-size: var(--text-sm); color: var(--ink-2);
      overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
    }
    .inbox__row--unread .inbox__preview { color: var(--ink); font-weight: 600; }
    .inbox__me { color: var(--ink-3); }
    .inbox__badge {
      min-width: 22px; height: 22px; padding: 0 6px; border-radius: var(--radius-full);
      display: inline-flex; align-items: center; justify-content: center;
      background: var(--primary); color: var(--on-primary, #fff);
      font-size: var(--text-xs); font-weight: 800; font-family: var(--font-data);
    }
    .inbox__go { color: var(--ink-4); }
  `],
})
export class MessageInboxComponent implements OnInit {
  private readonly messageService = inject(MessageService);
  private readonly toast = inject(ToastService);

  readonly conversations = signal<Conversation[]>([]);
  readonly loading = signal(true);
  readonly unread = computed(() => this.conversations().reduce((n, c) => n + c.unreadCount, 0));

  ngOnInit(): void {
    this.messageService.conversations().subscribe({
      next: (list) => { this.conversations.set(list); this.loading.set(false); },
      error: () => { this.conversations.set([]); this.loading.set(false); this.toast.error('Chargement impossible.'); },
    });
  }

  excerpt(body: string): string {
    return body.length > 120 ? body.slice(0, 120).trimEnd() + '…' : body;
  }
}
