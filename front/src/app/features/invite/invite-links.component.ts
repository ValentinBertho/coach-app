import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { InviteLink, InviteLinkService } from '../../core/services/invite-link.service';
import { TrainingGroupService } from '../../core/services/training-group.service';
import { ToastService } from '../../core/services/toast.service';
import { TrainingGroup } from '../../core/models/training-group.model';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { ShareKitComponent } from '../../shared/components/share-kit/share-kit.component';

type Preset = 'personal' | 'public';

/**
 * Les liens d'invitation du coach : en créer un, l'envoyer (SMS, WhatsApp, e-mail, QR code), voir
 * combien de fois il a servi, le désactiver.
 *
 * <p>Deux préréglages couvrent l'essentiel : le lien <b>personnel</b>, pour une personne précise,
 * qui la fait entrer directement ; le lien <b>public</b>, pour un groupe WhatsApp ou une affiche,
 * qui envoie une demande à valider. Le reste (club, groupe, limite, durée) se règle à côté.</p>
 */
@Component({
  selector: 'app-invite-links',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink, DatePipe, IconComponent, ShareKitComponent],
  template: `
    <section class="page-header">
      <div>
        <h1 class="display-sm">Inviter des athlètes</h1>
        <p class="subtitle">Un lien à envoyer par SMS, WhatsApp ou e-mail : l'athlète crée son compte et te rejoint en un geste.</p>
      </div>
      <div class="page-header__actions">
        <a routerLink="/app/athletes" class="btn btn-ghost">Mes athlètes</a>
      </div>
    </section>

    <section class="card il-new">
      <h2>Nouveau lien</h2>
      <div class="il-presets" role="radiogroup" aria-label="Type de lien">
        <button type="button" role="radio" [attr.aria-checked]="preset() === 'personal'"
                [class.on]="preset() === 'personal'" (click)="choose('personal')">
          <strong>Pour une personne</strong>
          <small>Entrée directe, un seul usage, valable 30 jours.</small>
        </button>
        <button type="button" role="radio" [attr.aria-checked]="preset() === 'public'"
                [class.on]="preset() === 'public'" (click)="choose('public')">
          <strong>À partager largement</strong>
          <small>Groupe WhatsApp, affiche, réseaux : chaque arrivée est une demande que tu valides.</small>
        </button>
      </div>

      <div class="il-form">
        <label class="form-label" for="il-label">Nom du lien (pour toi)</label>
        <input id="il-label" class="form-control" maxlength="120" [(ngModel)]="label"
               [placeholder]="preset() === 'personal' ? 'Pour Léa' : 'Groupe WhatsApp du club'" />

        @if (!solo()) {
          <label class="il-check">
            <input type="checkbox" [(ngModel)]="clubMembership" />
            <span>L'athlète rejoint le club <small class="field-hint">(sinon, il est ton athlète privé)</small></span>
          </label>
        }
        @if (groups().length > 0) {
          <label class="form-label" for="il-group">Groupe d'arrivée</label>
          <select id="il-group" class="form-control" [(ngModel)]="groupId">
            <option [ngValue]="null">Aucun</option>
            @for (g of groups(); track g.id) { <option [ngValue]="g.id">{{ g.name }}</option> }
          </select>
        }
        <label class="il-check">
          <input type="checkbox" [(ngModel)]="directEntry" />
          <span>Entrée directe <small class="field-hint">(sans validation de ta part)</small></span>
        </label>
        <div class="il-row">
          <div>
            <label class="form-label" for="il-uses">Utilisations max.</label>
            <input id="il-uses" class="form-control" type="number" min="1" max="1000" [(ngModel)]="maxUses" placeholder="Illimité" />
          </div>
          <div>
            <label class="form-label" for="il-days">Valable (jours)</label>
            <input id="il-days" class="form-control" type="number" min="1" max="365" [(ngModel)]="validDays" placeholder="Sans fin" />
          </div>
        </div>
        @if (directEntry && !maxUses) {
          <p class="il-hint"><app-icon name="info" [size]="14" /> Un lien à entrée directe sans limite fait entrer quiconque le reçoit. Pour un lien partagé largement, préfère la validation.</p>
        }
        <button type="button" class="btn btn-primary" [disabled]="busy()" (click)="create()">
          <app-icon name="link" [size]="16" /> {{ busy() ? 'Création…' : 'Créer le lien' }}
        </button>
      </div>
    </section>

    @if (fresh(); as l) {
      <section class="card il-fresh">
        <h2>Ton lien est prêt</h2>
        <app-share-kit [url]="l.url" [message]="shareMessage(l)" [title]="'Rejoins-moi sur DARI Lab'"
                       [qrName]="'invitation-' + l.token + '.png'" />
      </section>
    }

    <section class="il-list">
      <h2>Mes liens</h2>
      @if (links().length === 0) {
        <p class="field-hint">Aucun lien pour l'instant.</p>
      }
      @for (l of links(); track l.id) {
        <article class="card il-item" [class.off]="l.inactiveReason">
          <div class="il-item__main">
            <strong>{{ l.label || (l.directEntry ? 'Lien personnel' : 'Lien public') }}</strong>
            <span class="il-tags">
              <span class="badge">{{ l.directEntry ? 'Entrée directe' : 'Avec validation' }}</span>
              @if (l.clubMembership) { <span class="badge">Club</span> }
              @if (l.groupName) { <span class="badge">{{ l.groupName }}</span> }
            </span>
            <small class="field-hint">
              Utilisé {{ l.uses }}{{ l.maxUses ? ' / ' + l.maxUses : '' }} fois
              @if (l.expiresAt) { · jusqu'au {{ l.expiresAt | date: 'd MMM y' : '' : 'fr' }} }
              @if (l.inactiveReason) { · {{ l.inactiveReason }} }
            </small>
          </div>
          @if (!l.inactiveReason) {
            <div class="il-item__actions">
              <button type="button" class="btn btn-ghost btn-sm" (click)="fresh.set(l)">
                <app-icon name="share" [size]="14" /> Envoyer
              </button>
              <button type="button" class="btn btn-ghost btn-sm" (click)="revoke(l)">
                <app-icon name="x" [size]="14" /> Désactiver
              </button>
            </div>
          }
        </article>
      }
    </section>
  `,
  styles: [`
    .il-new, .il-fresh { padding: var(--sp-5); margin-bottom: var(--sp-5); display: flex; flex-direction: column; gap: var(--sp-3); }
    h2 { margin: 0 0 var(--sp-2); font-size: var(--text-lg); }
    .il-presets { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: var(--sp-3); }
    .il-presets button { text-align: left; display: flex; flex-direction: column; gap: 4px; padding: var(--sp-3);
      border: 1.5px solid var(--hairline); border-radius: var(--radius-md, 12px); background: var(--surface); color: var(--ink); }
    .il-presets button.on { border-color: var(--primary); box-shadow: 0 0 0 3px color-mix(in srgb, var(--primary) 18%, transparent); }
    .il-presets small { color: var(--ink-3); }
    .il-form { display: flex; flex-direction: column; gap: var(--sp-2); max-width: 520px; }
    .il-check { display: flex; gap: var(--sp-2); align-items: flex-start; }
    .il-row { display: grid; grid-template-columns: 1fr 1fr; gap: var(--sp-3); }
    .il-hint { display: flex; gap: var(--sp-2); color: var(--warning-text); background: var(--warning-bg);
      padding: var(--sp-2) var(--sp-3); border-radius: var(--radius-md, 12px); margin: 0; }
    .il-list { display: flex; flex-direction: column; gap: var(--sp-2); }
    .il-item { padding: var(--sp-3) var(--sp-4); display: flex; justify-content: space-between; align-items: center;
      gap: var(--sp-3); flex-wrap: wrap; }
    .il-item.off { opacity: .6; }
    .il-item__main { display: flex; flex-direction: column; gap: 4px; min-width: 0; }
    .il-tags { display: flex; gap: var(--sp-1); flex-wrap: wrap; }
    .il-item__actions { display: flex; gap: var(--sp-1); }
  `],
})
export class InviteLinksComponent implements OnInit {
  private readonly service = inject(InviteLinkService);
  private readonly groupService = inject(TrainingGroupService);
  private readonly toast = inject(ToastService);
  private readonly auth = inject(AuthService);

  readonly links = signal<InviteLink[]>([]);
  readonly groups = signal<TrainingGroup[]>([]);
  readonly fresh = signal<InviteLink | null>(null);
  readonly busy = signal(false);
  readonly preset = signal<Preset>('personal');
  readonly solo = computed(() => this.auth.soloPractice());

  label = '';
  clubMembership = false;
  directEntry = true;
  groupId: string | null = null;
  maxUses: number | null = 1;
  validDays: number | null = 30;

  ngOnInit(): void {
    this.service.mine().subscribe({ next: (l) => this.links.set(l), error: () => this.links.set([]) });
    this.groupService.list().subscribe({ next: (g) => this.groups.set(g), error: () => this.groups.set([]) });
    this.clubMembership = !this.solo();
  }

  choose(p: Preset): void {
    this.preset.set(p);
    if (p === 'personal') {
      this.directEntry = true;
      this.maxUses = 1;
      this.validDays = 30;
    } else {
      this.directEntry = false;
      this.maxUses = null;
      this.validDays = null;
    }
  }

  create(): void {
    this.busy.set(true);
    this.service.create({
      label: this.label.trim() || null,
      clubMembership: this.clubMembership,
      directEntry: this.directEntry,
      groupId: this.groupId,
      maxUses: this.maxUses || null,
      validDays: this.validDays || null,
    }).subscribe({
      next: (l) => {
        this.busy.set(false);
        this.links.update((list) => [l, ...list]);
        this.fresh.set(l);
        this.label = '';
        this.toast.success('Lien créé : envoie-le maintenant.');
      },
      error: (err) => {
        this.busy.set(false);
        this.toast.error(err?.error?.message ?? 'Lien non créé.');
      },
    });
  }

  revoke(l: InviteLink): void {
    this.service.revoke(l.id).subscribe({
      next: (updated) => {
        this.links.update((list) => list.map((x) => (x.id === updated.id ? updated : x)));
        if (this.fresh()?.id === l.id) this.fresh.set(null);
        this.toast.info('Lien désactivé : il ne fait plus entrer personne.');
      },
      error: () => this.toast.error('Lien non désactivé.'),
    });
  }

  shareMessage(l: InviteLink): string {
    const who = this.auth.currentUser()?.fullName ?? 'ton coach';
    return l.directEntry
      ? 'Voici ton lien pour me rejoindre sur DARI Lab (calendrier, séances, suivi) :'
      : `${who} t'invite sur DARI Lab : crée ton compte et demande à rejoindre ici`;
  }
}
