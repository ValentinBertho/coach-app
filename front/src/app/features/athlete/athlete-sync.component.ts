import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AthletePortalService } from '../../core/services/athlete-portal.service';
import { ToastService } from '../../core/services/toast.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { StravaStatus } from '../../core/models/strava.model';
import { ActivityExclusion } from '../../core/models/activity.model';

/**
 * Synchronisation des montres (athlète) — CDC §12 : l'intégration est d'abord côté athlète.
 * Je connecte MA montre, l'import est ensuite automatique. Garmin/COROS à venir.
 */
@Component({
  selector: 'app-athlete-sync',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, RouterLink, IconComponent],
  template: `
    <div class="sync">
      <header class="sync-top">
        <a routerLink="/athlete/profile" class="btn btn-ghost btn-sm">← Profil</a>
        <h1 class="display-sm">Synchronisation</h1>
        <p class="subtitle">Connecte ta montre : tes activités s'importent toutes seules, chaque heure.</p>
      </header>

      @if (loading()) {
        <div class="card"><div class="skeleton" style="height: 80px;"></div></div>
      } @else if (status()) {
        @if (status(); as st) {
        <article class="card prov">
          <span class="prov-logo"><app-icon name="watch" [size]="22" /></span>
          <div class="prov-info">
            <strong>Strava</strong>
            @if (!st.configured) {
              <span class="field-hint">Indisponible sur ce serveur.</span>
            } @else if (st.connected) {
              <span class="field-hint">Synchro automatique toutes les heures@if (st.lastImportEpoch) { · dernier import {{ (st.lastImportEpoch * 1000) | date: 'd MMM HH:mm' }} }@if (st.providerAthleteId) { · #{{ st.providerAthleteId }} }</span>
            } @else {
              <span class="field-hint">Non connecté</span>
            }
          </div>
          @if (st.configured && st.connected) {
            <span class="badge badge-success">Connecté</span>
          }
        </article>

        @if (st.configured) {
          <div class="sync-actions">
            @if (st.connected) {
              <button type="button" class="btn btn-primary btn-sm" (click)="importNow()" [disabled]="busy()">
                {{ busy() ? 'Import…' : 'Importer maintenant' }}
              </button>
              <button type="button" class="btn btn-ghost btn-sm" (click)="disconnect()">Déconnecter</button>
            } @else {
              <button type="button" class="btn btn-accent btn-sm" (click)="connect()">
                <app-icon name="watch" [size]="15" /> Connecter Strava
              </button>
            }
          </div>
        }

        <!-- Écrire dans le compte Strava de l'athlète est d'une autre nature que lire : son fil
             est visible de ses abonnés, et nous ne gardons pas le nom d'origine. D'où un
             interrupteur, décoché par défaut, et un texte qui dit ce qui va se passer chez lui
             plutôt que ce que fait la fonctionnalité. -->
        @if (st.configured && st.connected) {
          <section class="card">
            <h2>Nom des sorties</h2>
            <p class="field-hint">
              Strava nomme lui-même les sorties que tu ne nommes pas : « Morning Run », « Sortie à
              vélo l'après-midi ». Dans Darilab, ces titres-là sont remplacés par le nom de ta
              séance — ou par la distance quand il n'y en a pas. Tes propres titres ne sont jamais
              touchés.
            </p>
            <div class="chan">
              <label class="chan__row">
                <span class="chan__txt">
                  <strong>Renommer aussi sur Strava</strong>
                  <span class="field-hint">
                    Le nouveau nom est écrit sur ton compte Strava, où tes abonnés le voient. Nous
                    ne gardons pas le nom d'origine : c'est sans retour de notre côté.
                  </span>
                </span>
                <span class="switch">
                  <input type="checkbox" [checked]="st.renameOnStrava" [disabled]="busy()"
                         (change)="setRenameOnStrava($event)"
                         aria-label="Renommer aussi mes sorties sur Strava" />
                  <span class="switch__track" aria-hidden="true"></span>
                </span>
              </label>
            </div>
            @if (st.renameOnStrava && !st.canRenameOnStrava) {
              <p class="field-hint warn">
                Strava ne nous a pas donné l'autorisation d'écrire sur ton compte — c'est le cas si
                tu l'as connecté avant cette option. Reconnecte-le et accepte la permission
                « modifier tes activités » : rien ne sera renommé chez toi d'ici là.
                <button type="button" class="btn btn-ghost btn-sm" (click)="connect()">Reconnecter Strava</button>
              </p>
            }
            @if (st.renameOnStrava && st.canRenameOnStrava) {
              <p class="field-hint">
                Actif pour les prochaines sorties. Désactiver arrête les suivantes, mais ne remet
                pas les anciens noms.
              </p>
            }
          </section>
        }
        }
      }

      <div class="card prov disabled">
        <span class="prov-logo"><app-icon name="watch" [size]="22" /></span>
        <div class="prov-info"><strong>Garmin Connect</strong><span class="field-hint">Bientôt</span></div>
        <span class="badge badge-neutral">Bientôt</span>
      </div>
      <div class="card prov disabled">
        <span class="prov-logo"><app-icon name="watch" [size]="22" /></span>
        <div class="prov-info"><strong>COROS</strong><span class="field-hint">Bientôt</span></div>
        <span class="badge badge-neutral">Bientôt</span>
      </div>

      <!-- Les sorties écartées pour de bon. Sans cet écran, une case cochée par erreur serait
           sans recours : la sortie ne reviendrait plus et rien ne dirait pourquoi. Elle n'apparaît
           que si l'athlète en a masqué au moins une — sinon la section n'aurait rien à dire. -->
      @if (excluded().length) {
        <section class="card masked">
          <div class="masked-hd">
            <h2>Sorties masquées</h2>
            <span class="badge badge-neutral">{{ excluded().length }}</span>
          </div>
          <p class="field-hint">
            Tu as demandé à ne plus jamais importer ces sorties. La synchro les ignore.
          </p>
          @for (m of excluded(); track m.id) {
            <div class="mrow">
              <div class="mrow-id">
                <strong>{{ m.title || 'Sortie sans titre' }}</strong>
                <span class="field-hint">
                  @if (m.activityDate) { {{ m.activityDate | date: 'd MMM yyyy' }} · }{{ m.source }}
                </span>
              </div>
              <button type="button" class="btn btn-ghost btn-sm" [disabled]="busy()" (click)="unmask(m)">
                Réautoriser
              </button>
            </div>
          }
          <p class="field-hint">
            Réautoriser ne la fait pas revenir tout de suite : c'est la prochaine synchronisation
            qui la rapportera, si elle est encore dans la fenêtre relue.
          </p>
        </section>
      }

      <p class="field-hint note">
        Tu peux aussi <a routerLink="/athlete/activities">ajouter une sortie manuellement ou importer un fichier de montre (FIT, GPX, TCX)</a>.
      </p>
    </div>
  `,
  styles: [`
    /* padding-top : safe-area de la coquille athlète (PWA) — sinon le titre passe sous l'heure. */
    .sync { max-width: 560px; margin-inline: auto; padding: var(--sp-4); padding-top: max(var(--sp-4), var(--safe-top, 0px)); display: flex; flex-direction: column; gap: var(--sp-3); }
    .sync-top { display: flex; flex-direction: column; gap: var(--sp-1); align-items: flex-start; }
    .sync-top h1 { margin: 0; }
    .subtitle { color: var(--ink-3); margin: 0; }
    .prov { display: flex; align-items: center; gap: var(--sp-3); }
    .prov.disabled { opacity: .6; }
    .prov-logo { display: inline-flex; }
    .prov-info { display: flex; flex-direction: column; flex: 1; min-width: 0; }
    .prov-info strong { color: var(--ink); }
    .sync-actions { display: flex; gap: var(--sp-2); flex-wrap: wrap; }
    .note { margin-top: var(--sp-2); }
    .masked { display: flex; flex-direction: column; gap: var(--sp-2); }
    .masked-hd { display: flex; align-items: center; gap: var(--sp-2); }
    .masked-hd h2 { margin: 0; font-size: var(--text-lg); }
    .mrow { display: flex; align-items: center; gap: var(--sp-3); padding-top: var(--sp-2); border-top: 1px solid var(--hairline); }
    .mrow-id { display: flex; flex-direction: column; flex: 1; min-width: 0; }
    .mrow-id strong { color: var(--ink); overflow-wrap: anywhere; }
    .mrow .btn { min-height: 44px; flex: none; }
    .card h2 { margin: 0 0 var(--sp-2); font-size: var(--text-lg); }
    .chan { display: flex; flex-direction: column; gap: var(--sp-2); margin-top: var(--sp-3); }
    .chan__row { display: flex; align-items: flex-start; gap: var(--sp-3); cursor: pointer; }
    .chan__txt { display: flex; flex-direction: column; gap: 2px; flex: 1; min-width: 0; }
    .chan__txt strong { color: var(--ink); }
    .switch { position: relative; flex-shrink: 0; width: 52px; height: 44px; }
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
    .switch input:disabled + .switch__track { opacity: .5; }
    .warn { color: var(--warning-text, var(--ink-2)); display: flex; flex-direction: column; align-items: flex-start; gap: var(--sp-2); margin-top: var(--sp-2); }
  `],
})
export class AthleteSyncComponent implements OnInit {
  private readonly portal = inject(AthletePortalService);
  private readonly toast = inject(ToastService);
  private readonly confirm = inject(ConfirmService);

  readonly loading = signal(true);
  readonly status = signal<StravaStatus | null>(null);
  readonly busy = signal(false);
  /** Les sorties que j'ai écartées pour de bon. */
  readonly excluded = signal<ActivityExclusion[]>([]);

  ngOnInit(): void { this.load(); }

  /** L'athlète revient sur sa décision : la sortie redevient importable. */
  async unmask(m: ActivityExclusion): Promise<void> {
    const ok = await this.confirm.ask({
      title: 'Réautoriser cette sortie ?',
      message: 'Elle pourra de nouveau être importée à la prochaine synchronisation.',
      confirmLabel: 'Réautoriser',
    });
    if (!ok) { return; }
    this.busy.set(true);
    this.portal.unmaskActivity(m.id).subscribe({
      next: () => {
        this.excluded.update((list) => list.filter((x) => x.id !== m.id));
        this.busy.set(false);
        this.toast.success('Sortie réautorisée');
      },
      error: () => { this.busy.set(false); this.toast.error('Action impossible.'); },
    });
  }

  private load(): void {
    this.loading.set(true);
    this.portal.stravaStatus().subscribe({
      next: (s) => { this.status.set(s); this.loading.set(false); },
      error: () => { this.status.set(null); this.loading.set(false); },
    });
    // Indépendant du statut Strava : des sorties peuvent rester masquées après une déconnexion,
    // et c'est justement là qu'on vient chercher pourquoi elles ne reviennent pas.
    this.portal.excludedActivities().subscribe({
      next: (e) => this.excluded.set(e),
      error: () => this.excluded.set([]),
    });
  }

  /**
   * L'athlète accepte — ou retire — le renommage sur son compte Strava.
   *
   * <p>En cas d'échec, on remet l'interrupteur dans l'état que le serveur connaît plutôt que dans
   * celui que le clic a affiché : sur une préférence qui autorise une écriture chez quelqu'un,
   * un interrupteur qui ment est pire que pas d'interrupteur du tout.</p>
   */
  setRenameOnStrava(event: Event): void {
    const enabled = (event.target as HTMLInputElement).checked;
    this.busy.set(true);
    this.portal.stravaSetRenameOnStrava(enabled).subscribe({
      next: (s) => {
        this.status.set(s);
        this.busy.set(false);
        this.toast.success(enabled
          ? 'Tes sorties seront aussi renommées sur Strava.'
          : 'Darilab ne renommera plus rien sur Strava.');
      },
      error: () => { this.busy.set(false); this.toast.error('Action impossible.'); this.load(); },
    });
  }

  connect(): void {
    this.portal.stravaAuthorizeUrl().subscribe({
      next: (r) => { window.location.href = r.url; },
      error: () => this.toast.error('Strava indisponible sur ce serveur.'),
    });
  }

  importNow(): void {
    this.busy.set(true);
    this.portal.stravaImport().subscribe({
      next: (r) => { this.busy.set(false); this.toast.success(`${r.imported} activité(s) importée(s)`); this.load(); },
      error: () => { this.busy.set(false); this.toast.error('Import impossible.'); },
    });
  }

  async disconnect(): Promise<void> {
    const ok = await this.confirm.ask({
      title: 'Déconnecter Strava ?', message: 'Tes activités déjà importées sont conservées.', confirmLabel: 'Déconnecter', danger: true,
    });
    if (!ok) { return; }
    this.portal.stravaDisconnect().subscribe({
      next: () => { this.toast.info('Strava déconnecté.'); this.load(); },
      error: () => this.toast.error('Déconnexion impossible.'),
    });
  }
}
