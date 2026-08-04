import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AthletePortalService, HealthConsent } from '../../core/services/athlete-portal.service';
import { AuthService } from '../../core/services/auth.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { FeedbackService } from '../../core/services/feedback.service';
import { NotificationPreferences, NotificationService } from '../../core/services/notification.service';
import { ToastService } from '../../core/services/toast.service';
import { LogoComponent } from '../../shared/components/logo/logo.component';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { InstallButtonComponent } from '../../shared/components/install-button/install-button.component';
import { PushButtonComponent } from '../../shared/components/push-button/push-button.component';
import { DataOriginTagComponent } from '../../shared/components/physiology';
import { PhysioProfile, Performance, Vdot } from '../../core/models/physio.model';
import { LactateTest } from '../../core/models/lactate.model';
import { RaceObjective } from '../../core/models/race.model';
import { supportMailto as supportLink } from '../../shared/components/support-link';
import {
  Unavailability, UnavailabilityReason, UnavailabilityRequest,
} from '../../core/models/unavailability.model';

interface TrendPoint { date: string; value: number; }
interface LtPoint { date: string; lt1: number | null; lt2: number | null; }

/**
 * Profil & confidentialité athlète (mobile-first). Phase 1 « Me connaître » :
 * mon profil physio (lecture), mes allures d'entraînement (VDOT) et mes objectifs,
 * câblés sur /me/physio, /me/vdot, /me/races — puis export RGPD + suppression de compte.
 */
@Component({
  selector: 'app-athlete-profile',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, FormsModule, LogoComponent, IconComponent, DataOriginTagComponent, DatePipe, DecimalPipe,
    InstallButtonComponent, PushButtonComponent],
  template: `
    <div class="shell">
      <header class="top">
        <app-logo [size]="28" [showText]="true" />
        <a routerLink="/athlete/today" class="btn btn-ghost btn-sm">← Aujourd'hui</a>
      </header>
      <main class="wrap">
        <h1 class="display-sm">Profil & confidentialité</h1>
        <p class="subtitle">{{ user()?.fullName }}</p>

        <a routerLink="/athlete/help" class="card help-link">
          <span class="help-link__ic"><app-icon name="life-buoy" [size]="20" /></span>
          <span class="help-link__txt">
            <strong>Aide & guide</strong>
            <span class="field-hint">Comment utiliser l'app, pas à pas.</span>
          </span>
          <app-icon name="chevron-right" [size]="18" />
        </a>

        <!-- Canal de support : l'aide renvoyait l'athlète vers son coach, ce qui ne sert à rien
             quand c'est l'application elle-même qui dysfonctionne. -->
        <button type="button" (click)="openFeedback()" class="card help-link">
          <span class="help-link__ic"><app-icon name="inbox" [size]="20" /></span>
          <span class="help-link__txt">
            <strong>Signaler un problème</strong>
            <span class="field-hint">Bug, idée ou question — version et page déjà renseignées.</span>
          </span>
          <app-icon name="chevron-right" [size]="18" />
        </button>

        <!-- Installation, notifications et déconnexion encombraient la barre supérieure de
             « Aujourd'hui », un écran qui devrait ne porter que la séance. Ce sont des
             réglages de compte : leur place est ici. -->
        <!-- Compte : changer son mot de passe, son nom ou son adresse. Les endpoints existaient
             et acceptaient le rôle athlète, mais aucun écran ne les appelait — le seul recours
             pour un mot de passe compromis était « mot de passe oublié ». -->
        <section class="card acct">
          <h2>Mon compte</h2>

          <label class="field">
            <span class="field-label">Nom</span>
            <input class="form-control" [ngModel]="acctName()" (ngModelChange)="acctName.set($event)" />
          </label>
          <label class="field">
            <span class="field-label">Adresse e-mail</span>
            <input class="form-control" type="email" [ngModel]="acctEmail()"
                   (ngModelChange)="acctEmail.set($event)" />
            <span class="field-hint">Changer d'adresse demande une nouvelle vérification.</span>
          </label>
          <button type="button" class="btn btn-outline" [disabled]="acctBusy()" (click)="saveAccount()">
            Enregistrer
          </button>

          <hr class="acct__sep" />

          @if (pwdOpen()) {
            <label class="field">
              <span class="field-label">Mot de passe actuel</span>
              <input class="form-control" type="password" autocomplete="current-password"
                     [ngModel]="pwdCurrent()" (ngModelChange)="pwdCurrent.set($event)" />
            </label>
            <label class="field">
              <span class="field-label">Nouveau mot de passe</span>
              <input class="form-control" type="password" autocomplete="new-password"
                     [ngModel]="pwdNew()" (ngModelChange)="pwdNew.set($event)" />
              <span class="field-hint">8 caractères minimum.</span>
            </label>
            <div class="acct__actions">
              <button type="button" class="btn btn-accent" [disabled]="pwdBusy()" (click)="savePassword()">
                Changer le mot de passe
              </button>
              <button type="button" class="btn btn-ghost" (click)="pwdOpen.set(false)">Annuler</button>
            </div>
          } @else {
            <button type="button" class="btn btn-outline" (click)="pwdOpen.set(true)">
              Changer mon mot de passe
            </button>
          }
        </section>

        <section class="card app-settings">
          <h2>L'application</h2>
          <p class="field-hint">Installe Darilab sur ton téléphone et reçois un rappel quand une séance t'attend.</p>
          <div class="app-settings__row">
            <app-install-button />
            <app-push-button />
          </div>

          <!-- Heure habituelle : elle ancre le rappel « Ta séance est finie ? », envoyé 2 h
               après. Un rappel de club à heure fixe tombe forcément à côté pour la moitié des
               athlètes ; celui-ci suit le rythme de chacun. -->
          <div class="debrief-time">
            <label for="usual-time">
              <strong>Mon heure d'entraînement habituelle</strong>
              <span class="field-hint">On te demandera ton ressenti 2 h après. Vide = pas de rappel.</span>
            </label>
            <input id="usual-time" type="time" class="form-control debrief-time__in"
                   [value]="usualSessionTime()" (change)="onUsualTimeChange($event)" />
          </div>

          <!-- Les e-mails renvoient ici pour se désabonner (lien de pied de page et en-tête
               List-Unsubscribe) : les deux canaux doivent donc s'y régler, pas seulement dans
               la cloche. -->
          @if (prefs(); as pr) {
            <div class="chan">
              <label class="chan__row">
                <span class="chan__txt">
                  <strong>Notifications push</strong>
                  <span class="field-hint">Séance planifiée, retour de ton coach, rappel de séance.</span>
                </span>
                <span class="switch">
                  <input type="checkbox" [checked]="pr.pushEnabled" (change)="setChannel('push', $event)"
                         aria-label="Recevoir les notifications push" />
                  <span class="switch__track" aria-hidden="true"></span>
                </span>
              </label>
              <label class="chan__row">
                <span class="chan__txt">
                  <strong>E-mails</strong>
                  <span class="field-hint">
                    Repli quand le push n'est pas actif. Les e-mails de compte (mot de passe,
                    invitation) partent toujours.
                  </span>
                </span>
                <span class="switch">
                  <input type="checkbox" [checked]="pr.emailEnabled" (change)="setChannel('email', $event)"
                         aria-label="Recevoir les e-mails de notification" />
                  <span class="switch__track" aria-hidden="true"></span>
                </span>
              </label>
            </div>
          }
        </section>

        <!-- Mon profil physio (lecture seule) -->
        @if (physio(); as p) {
          <section class="card">
            <div class="card-hd">
              <h2>Mon profil physio</h2>
              <app-data-origin-tag origin="calcule" label="Calculé" />
            </div>
            @if (hasPhysio(p)) {
              <div class="grid">
                @if (p.vdot != null) {
                  <div class="kpi"><span class="kpi-l">VDOT</span><span class="kpi-v">{{ p.vdot | number: '1.0-1' }}</span></div>
                }
                @if (p.lt1Kmh != null) {
                  <div class="kpi"><span class="kpi-l">LT1 (seuil aéro)</span><span class="kpi-v">{{ p.lt1Kmh | number: '1.1-1' }}<small> km/h</small></span></div>
                }
                @if (p.lt2Kmh != null) {
                  <div class="kpi"><span class="kpi-l">LT2 (seuil anaéro)</span><span class="kpi-v">{{ p.lt2Kmh | number: '1.1-1' }}<small> km/h</small></span></div>
                }
                @if (p.vcKmh != null) {
                  <div class="kpi"><span class="kpi-l">Vitesse critique</span><span class="kpi-v">{{ p.vcKmh | number: '1.1-1' }}<small> km/h</small></span></div>
                }
                @if (p.fcMax != null) {
                  <div class="kpi"><span class="kpi-l">FC max</span><span class="kpi-v">{{ p.fcMax }}<small> bpm</small></span></div>
                }
                @if (p.fcLt1 != null) {
                  <div class="kpi"><span class="kpi-l">FC LT1</span><span class="kpi-v">{{ p.fcLt1 }}<small> bpm</small></span></div>
                }
                @if (p.fcLt2 != null) {
                  <div class="kpi"><span class="kpi-l">FC LT2</span><span class="kpi-v">{{ p.fcLt2 }}<small> bpm</small></span></div>
                }
              </div>
            } @else {
              <p class="field-hint">Ton profil sera renseigné par ton coach après tes premiers tests.</p>
            }
          </section>
        }

        <!-- Ma progression physio (tendance VDOT + seuils) -->
        @if (vdotPoints().length >= 2 || ltPoints().length >= 2) {
          <section class="card">
            <div class="card-hd">
              <h2>Ma progression</h2>
              <app-data-origin-tag origin="mesure" label="Mesuré" />
            </div>

            @if (vdotPoints().length >= 2) {
              <div class="trend">
                <div class="trend-hd">
                  <span class="field-hint">VDOT</span>
                  <span class="trend-delta" [class.up]="vdotDelta() >= 0" [class.down]="vdotDelta() < 0">
                    {{ vdotDelta() >= 0 ? '▲' : '▼' }} {{ absVdot() | number: '1.0-1' }}
                  </span>
                </div>
                <svg viewBox="0 0 300 90" class="trend-svg" preserveAspectRatio="none" role="img" aria-label="Évolution du VDOT">
                  <polyline [attr.points]="vdotLine()" fill="none" stroke="var(--dari-violet)" stroke-width="2.5" />
                </svg>
                <div class="trend-x field-hint">
                  <span>{{ vdotPoints()[0].date | date: 'MM/yy' }}</span>
                  <span>{{ vdotPoints()[vdotPoints().length - 1].date | date: 'MM/yy' }}</span>
                </div>
              </div>
            }

            @if (ltPoints().length >= 2) {
              <div class="trend">
                <div class="trend-hd">
                  <span class="field-hint">Seuils (km/h)</span>
                  <span class="legend"><i class="sw sw-1"></i>LT1 <i class="sw sw-2"></i>LT2</span>
                </div>
                <svg viewBox="0 0 300 90" class="trend-svg" preserveAspectRatio="none" role="img" aria-label="Évolution des seuils">
                  @if (lt1Line()) { <polyline [attr.points]="lt1Line()" fill="none" stroke="var(--form-green, #11c08b)" stroke-width="2.5" /> }
                  @if (lt2Line()) { <polyline [attr.points]="lt2Line()" fill="none" stroke="var(--form-orange, #ff8a3c)" stroke-width="2.5" /> }
                </svg>
                <div class="trend-x field-hint">
                  <span>{{ ltPoints()[0].date | date: 'MM/yy' }}</span>
                  <span>{{ ltPoints()[ltPoints().length - 1].date | date: 'MM/yy' }}</span>
                </div>
              </div>
            }
          </section>
        }

        <!-- Mes allures d'entraînement (VDOT) -->
        @if (vdot(); as v) {
          @if (v.paces.length > 0) {
            <section class="card">
              <div class="card-hd">
                <h2>Mes allures d'entraînement</h2>
                <app-data-origin-tag origin="calcule" label="Calculé" />
              </div>
              <ul class="paces">
                @for (pace of v.paces; track pace.distance) {
                  <li>
                    <span class="pace-d">{{ pace.distance }}</span>
                    <span class="pace-p metric">{{ pace.paceLabel }}<small> /km</small></span>
                    @if (pace.speedKmh != null) {
                      <span class="pace-s field-hint">{{ pace.speedKmh | number: '1.1-1' }} km/h</span>
                    }
                  </li>
                }
              </ul>
            </section>
          }
        }

        <!-- Mes objectifs -->
        @if (races(); as rs) {
          @if (rs.length > 0) {
            <section class="card">
              <h2>Mes objectifs</h2>
              <ul class="races">
                @for (r of rs; track r.id) {
                  <li>
                    <span class="prio prio-{{ r.priority }}">{{ r.priority }}</span>
                    <div class="race-id">
                      <strong>{{ r.name }}</strong>
                      <span class="field-hint">{{ r.raceDate | date: 'EEE d MMM yyyy' }}</span>
                    </div>
                    <span class="race-j" [class.past]="r.daysUntil < 0">
                      {{ r.daysUntil > 0 ? 'J−' + r.daysUntil : (r.daysUntil === 0 ? "Jour J" : 'Passé') }}
                    </span>
                  </li>
                }
              </ul>
            </section>
          }
        }

        <!--
          Déclarer une indisponibilité : jusqu'ici l'athlète pouvait la voir dans son calendrier
          mais pas la créer — il devait prévenir son coach par un autre canal, qui la saisissait
          à sa place. Le coach référent est notifié à la déclaration.
        -->
        <section class="card unavail">
          <h2>Mes indisponibilités</h2>
          <p class="field-hint">Blessure, maladie, vacances : préviens ton coach, il adaptera ta semaine.</p>

          @if (unavailabilities().length > 0) {
            <ul class="unavail-list">
              @for (u of unavailabilities(); track u.id) {
                <li>
                  <span class="badge badge-warning">{{ reasonLabel(u.reason) }}</span>
                  <div class="unavail-id">
                    <strong>{{ u.startDate | date: 'd MMM' }} → {{ u.endDate | date: 'd MMM yyyy' }}</strong>
                    @if (u.notes) { <span class="field-hint">{{ u.notes }}</span> }
                  </div>
                  <button type="button" class="btn btn-ghost btn-sm" (click)="removeUnavailability(u)"
                          aria-label="Retirer cette indisponibilité">
                    <app-icon name="trash-2" [size]="15" />
                  </button>
                </li>
              }
            </ul>
          }

          @if (showUnavailForm()) {
            <form class="unavail-form" (ngSubmit)="submitUnavailability()">
              <label>
                Motif
                <select class="form-control" [(ngModel)]="unavailDraft.reason" name="reason" required>
                  @for (r of reasonOptions; track r.value) {
                    <option [value]="r.value">{{ r.label }}</option>
                  }
                </select>
              </label>
              <div class="unavail-dates">
                <label>Du<input type="date" class="form-control" [(ngModel)]="unavailDraft.startDate" name="start" required /></label>
                <label>Au<input type="date" class="form-control" [(ngModel)]="unavailDraft.endDate" name="end" required /></label>
              </div>
              <label>
                Commentaire <span class="field-hint">(facultatif)</span>
                <input class="form-control" [(ngModel)]="unavailDraft.notes" name="notes"
                       placeholder="Ex. reprise progressive prévue" />
              </label>
              <div class="unavail-actions">
                <button type="submit" class="btn btn-primary btn-sm" [disabled]="unavailBusy()">
                  {{ unavailBusy() ? 'Envoi…' : 'Déclarer' }}
                </button>
                <button type="button" class="btn btn-ghost btn-sm" (click)="showUnavailForm.set(false)">Annuler</button>
              </div>
            </form>
          } @else {
            <button type="button" class="btn btn-ghost btn-sm" (click)="openUnavailForm()">
              <app-icon name="plus" [size]="15" /> Déclarer une indisponibilité
            </button>
          }
        </section>

        <div class="card">
          <h2>Synchronisation</h2>
          <p class="field-hint">Connecte ta montre (Strava) pour importer tes activités automatiquement.</p>
          <a routerLink="/athlete/sync" class="btn btn-ghost">Gérer mes connexions</a>
        </div>

        <div class="card">
          <h2>Mes données (RGPD)</h2>
          <p class="field-hint">Télécharge l'ensemble de tes données personnelles (portabilité).</p>
          <button type="button" class="btn btn-ghost" (click)="exportData()">Exporter mes données (JSON)</button>
        </div>

        <!--
          Consentement au traitement des données de santé. La politique de confidentialité
          promettait le retrait « à tout moment » sans qu'aucun écran ne le permette ; l'article
          7-3 du RGPD exige qu'il soit aussi simple à retirer qu'à donner.
        -->
        @if (consent(); as c) {
          <div class="card">
            <h2>Mes données de santé</h2>
            @if (c.granted) {
              <p class="field-hint">
                Tu autorises ton coach à suivre ta <strong>douleur</strong>, ta
                <strong>fatigue</strong>, tes <strong>tests de lactate</strong> et tes
                indisponibilités médicales.
                @if (c.grantedAt) { <span>Accepté le {{ c.grantedAt | date: 'dd/MM/yyyy' }}.</span> }
              </p>
              <button type="button" class="btn btn-ghost" (click)="withdrawConsent()">
                Retirer mon consentement
              </button>
            } @else {
              <p class="field-hint">
                Tu ne partages actuellement <strong>aucune donnée de santé</strong>. Ton coach ne
                peut plus enregistrer de douleur, de test de lactate ni de motif médical.
                @if (c.withdrawnAt) { <span>Retiré le {{ c.withdrawnAt | date: 'dd/MM/yyyy' }}.</span> }
              </p>
              <button type="button" class="btn btn-ghost" (click)="grantConsent()">
                Réactiver le partage
              </button>
            }
          </div>
        }

        <div class="card danger-zone">
          <div>
            <strong>Supprimer mon compte</strong>
            <p class="field-hint">Efface définitivement ton profil, tes séances et activités.</p>
          </div>
          <button type="button" class="btn btn-danger" (click)="deleteAccount()">Supprimer</button>
        </div>

        <button type="button" class="btn btn-ghost logout-btn" (click)="logout()">
          <app-icon name="lock" [size]="16" /> Me déconnecter
        </button>
      </main>
    </div>
  `,
  styles: [`
    .shell { min-height: 100dvh; background: var(--canvas); }
    .top { display: flex; align-items: center; justify-content: space-between; padding: var(--sp-3) var(--sp-4); padding-top: max(var(--sp-3), env(safe-area-inset-top)); background: var(--paper); border-bottom: 1px solid var(--hairline); position: sticky; top: 0; }
    .wrap { max-width: 560px; margin-inline: auto; padding: var(--sp-5) var(--sp-4) var(--sp-12); display: flex; flex-direction: column; gap: var(--sp-4); }
    .subtitle { color: var(--ink-3); margin: 0; }
    /* « Signaler un problème » ouvre un panneau : c'est un bouton, rendu comme la carte-lien voisine. */
    .help-link { display: flex; align-items: center; gap: var(--sp-3); text-decoration: none; color: var(--ink); padding: var(--sp-3); width: 100%; text-align: left; font: inherit; cursor: pointer; }
    button.help-link { border: 1px solid var(--line); }
    .app-settings__row { display: flex; flex-wrap: wrap; gap: var(--sp-2); margin-top: var(--sp-3); }
    .debrief-time { display: flex; align-items: center; justify-content: space-between; gap: var(--sp-3); margin-top: var(--sp-4); }
    .debrief-time label { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
    .debrief-time__in { width: auto; min-height: 44px; flex-shrink: 0; font-variant-numeric: tabular-nums; }
    .logout-btn { align-self: center; }
    .unavail-list { list-style: none; margin: var(--sp-3) 0 0; padding: 0; display: flex; flex-direction: column; gap: var(--sp-2); }
    .unavail-list li { display: flex; align-items: center; gap: var(--sp-2); }
    .unavail-id { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px; }
    .unavail-form { display: flex; flex-direction: column; gap: var(--sp-3); margin-top: var(--sp-3); }
    .unavail-form label { display: flex; flex-direction: column; gap: 4px; font-size: var(--text-sm); font-weight: 600; color: var(--ink-2); }
    .unavail-dates { display: flex; gap: var(--sp-2); }
    .unavail-dates label { flex: 1; min-width: 0; }
    .unavail-actions { display: flex; gap: var(--sp-2); }
    .help-link__ic { display: inline-flex; align-items: center; justify-content: center; width: 40px; height: 40px; flex-shrink: 0; border-radius: var(--radius-lg); background: var(--gradient-brand, var(--primary)); color: #fff; }
    .help-link__txt { display: flex; flex-direction: column; gap: 2px; flex: 1; min-width: 0; }
    .help-link__txt strong { color: var(--ink); }
    .chan { display: flex; flex-direction: column; gap: var(--sp-2); margin-top: var(--sp-3); }
    .chan__row { display: flex; align-items: flex-start; gap: var(--sp-3); cursor: pointer; }
    .chan__txt { display: flex; flex-direction: column; gap: 2px; flex: 1; min-width: 0; }
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

    .card h2 { margin: 0 0 var(--sp-2); font-size: var(--text-lg); }
    .card-hd { display: flex; align-items: center; justify-content: space-between; gap: var(--sp-2); margin-bottom: var(--sp-3); }
    .card-hd h2 { margin: 0; }

    .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); gap: var(--sp-3); }
    .kpi { display: flex; flex-direction: column; gap: 2px; padding: var(--sp-2) var(--sp-3); background: var(--canvas); border-radius: var(--radius-md); }
    .kpi-l { font-size: var(--text-sm); color: var(--ink-3); }
    .kpi-v { font-size: var(--text-xl); font-weight: 800; color: var(--ink); font-variant-numeric: tabular-nums; }
    .kpi-v small { font-size: var(--text-sm); font-weight: 600; color: var(--ink-3); }

    .paces { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; }
    .paces li { display: flex; align-items: baseline; gap: var(--sp-3); padding: var(--sp-2) 0; border-top: 1px solid var(--hairline); }
    .paces li:first-child { border-top: none; }
    .pace-d { flex: 1; color: var(--ink); font-weight: 600; }
    .pace-p { font-weight: 800; color: var(--ink); font-variant-numeric: tabular-nums; }
    .pace-p small { font-weight: 600; color: var(--ink-3); }
    .pace-s { width: 88px; text-align: right; }

    .races { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; }
    .races li { display: flex; align-items: center; gap: var(--sp-3); padding: var(--sp-2) 0; border-top: 1px solid var(--hairline); }
    .races li:first-child { border-top: none; }
    .race-id { display: flex; flex-direction: column; gap: 2px; min-width: 0; flex: 1; }
    .race-id strong { color: var(--ink); }
    .prio { display: inline-flex; align-items: center; justify-content: center; width: 26px; height: 26px; border-radius: 50%; font-size: var(--text-sm); font-weight: 800; flex-shrink: 0; }
    .prio-A { background: var(--danger-bg); color: var(--danger-text); }
    .prio-B { background: var(--warn-bg, var(--canvas)); color: var(--warn-text, var(--ink)); }
    .prio-C { background: var(--canvas); color: var(--ink-3); }
    .race-j { font-weight: 800; color: var(--dari-violet); font-variant-numeric: tabular-nums; white-space: nowrap; }
    .race-j.past { color: var(--ink-4); }

    .trend { margin-top: var(--sp-3); }
    .trend:first-of-type { margin-top: var(--sp-2); }
    .trend-hd { display: flex; align-items: center; justify-content: space-between; gap: var(--sp-2); }
    .trend-svg { width: 100%; height: 90px; }
    .trend-delta { font-weight: 800; font-size: var(--text-sm); font-variant-numeric: tabular-nums; }
    .trend-delta.up { color: var(--success-text); }
    .trend-delta.down { color: var(--danger-text); }
    .trend-x { display: flex; justify-content: space-between; }
    .legend { display: inline-flex; align-items: center; gap: 4px; font-size: var(--text-xs); color: var(--ink-3); }
    .legend .sw { width: 12px; height: 0; border-top: 2px solid; display: inline-block; vertical-align: middle; }
    .legend .sw-1 { border-color: var(--form-green, #11c08b); }
    .legend .sw-2 { border-color: var(--form-orange, #ff8a3c); }
  `],
})
export class AthleteProfileComponent implements OnInit {

  /**
   * « Signaler un problème » : ouvre le panneau de retour, enregistré en base avec son contexte.
   * Le mailto reste disponible en repli (cf. {@link #supportMailto}) — un athlète hors ligne ou
   * dont la session a expiré doit garder une adresse à qui écrire.
   */
  openFeedback(): void {
    this.feedbackPanel.open();
  }

  /** Repli par e-mail : mailto au support, contexte technique pré-rempli. */
  supportMailto(): string {
    return supportLink('Signalement depuis l’espace athlète');
  }

  // --- Mon compte -----------------------------------------------------------
  readonly acctName = signal('');
  readonly acctEmail = signal('');
  readonly acctBusy = signal(false);
  readonly pwdOpen = signal(false);
  readonly pwdCurrent = signal('');
  readonly pwdNew = signal('');
  readonly pwdBusy = signal(false);

  private readonly portal = inject(AthletePortalService);
  private readonly auth = inject(AuthService);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);
  private readonly notifications = inject(NotificationService);
  private readonly feedbackPanel = inject(FeedbackService);
  readonly user = this.auth.currentUser;

  /** Heure habituelle de séance, format « HH:mm » ; vide = rappel de ressenti désactivé. */
  readonly usualSessionTime = signal('');

  /** Canaux de notification (push / e-mail) — c'est ici que les e-mails renvoient. */
  readonly prefs = signal<NotificationPreferences | null>(null);

  /** Consentement au traitement des données de santé (RGPD art. 9), et son retrait (art. 7-3). */
  readonly consent = signal<HealthConsent | null>(null);

  readonly physio = signal<PhysioProfile | null>(null);
  readonly vdot = signal<Vdot | null>(null);
  readonly races = signal<RaceObjective[] | null>(null);
  readonly performances = signal<Performance[]>([]);
  readonly unavailabilities = signal<Unavailability[]>([]);
  readonly showUnavailForm = signal(false);
  readonly unavailBusy = signal(false);

  /** Motifs proposés — la même liste fermée que côté coach (UnavailabilityReason). */
  readonly reasonOptions: { value: UnavailabilityReason; label: string }[] = [
    { value: 'INJURY', label: 'Blessure' },
    { value: 'ILLNESS', label: 'Maladie' },
    { value: 'VACATION', label: 'Vacances' },
    { value: 'PERSONAL', label: 'Personnel' },
  ];

  unavailDraft: UnavailabilityRequest = this.emptyUnavailDraft();
  readonly lactateTests = signal<LactateTest[]>([]);

  /** Points VDOT datés (depuis les performances), ordonnés dans le temps. */
  readonly vdotPoints = computed<TrendPoint[]>(() =>
    this.performances()
      .filter((p) => p.vdot != null && p.dateSet)
      .map((p) => ({ date: p.dateSet as string, value: p.vdot as number }))
      .sort((a, b) => a.date.localeCompare(b.date)));

  /** Points seuils datés (depuis les tests lactate), en km/h, ordonnés dans le temps. */
  readonly ltPoints = computed<LtPoint[]>(() =>
    this.lactateTests()
      .filter((t) => t.testDate && (t.lt1Ms != null || t.lt2Ms != null))
      .map((t) => ({
        date: t.testDate,
        lt1: t.lt1Ms != null ? Math.round(t.lt1Ms * 3.6 * 10) / 10 : null,
        lt2: t.lt2Ms != null ? Math.round(t.lt2Ms * 3.6 * 10) / 10 : null,
      }))
      .sort((a, b) => a.date.localeCompare(b.date)));

  readonly vdotDelta = computed(() => {
    const p = this.vdotPoints();
    return p.length >= 2 ? p[p.length - 1].value - p[0].value : 0;
  });
  absVdot(): number { return Math.abs(this.vdotDelta()); }

  readonly vdotLine = computed(() => {
    const vals = this.vdotPoints().map((p) => p.value);
    return this.poly(vals, Math.min(...vals), Math.max(...vals));
  });

  readonly lt1Line = computed(() => this.ltLine((p) => p.lt1));
  readonly lt2Line = computed(() => this.ltLine((p) => p.lt2));

  private ltLine(pick: (p: LtPoint) => number | null): string {
    const pts = this.ltPoints();
    const all = pts.flatMap((p) => [p.lt1, p.lt2]).filter((v): v is number => v != null);
    if (all.length === 0) return '';
    const min = Math.min(...all);
    const max = Math.max(...all);
    return this.poly(pts.map(pick), min, max);
  }

  /** Polyligne SVG (viewBox 300×90) d'une série, valeurs nulles omises. */
  private poly(vals: (number | null)[], min: number, max: number): string {
    const W = 300, H = 90, PT = 8, PB = 8;
    const span = max - min || 1;
    const n = vals.length;
    return vals
      .map((v, i) => {
        if (v == null) return null;
        const x = n === 1 ? W / 2 : (i / (n - 1)) * W;
        const y = (H - PB) - ((v - min) / span) * (H - PT - PB);
        return `${x.toFixed(1)},${y.toFixed(1)}`;
      })
      .filter((s): s is string => s != null)
      .join(' ');
  }

  /** Enregistre nom et adresse. Un changement d'adresse repasse le compte en « non vérifié ». */
  saveAccount(): void {
    const name = this.acctName().trim();
    const email = this.acctEmail().trim();
    if (!name || !email) { this.toast.warning('Nom et adresse sont requis.'); return; }
    this.acctBusy.set(true);
    this.auth.updateProfile({ fullName: name, email }).subscribe({
      next: () => { this.acctBusy.set(false); this.toast.success('Profil mis à jour'); },
      error: (err: { error?: { message?: string } }) => {
        this.acctBusy.set(false);
        this.toast.error(err.error?.message ?? 'Mise à jour impossible.');
      },
    });
  }

  /** Change le mot de passe. Le serveur exige l'actuel et révoque les sessions antérieures. */
  savePassword(): void {
    const current = this.pwdCurrent();
    const next = this.pwdNew();
    if (!current || next.length < 8) {
      this.toast.warning('Mot de passe actuel requis, nouveau d\'au moins 8 caractères.');
      return;
    }
    this.pwdBusy.set(true);
    this.auth.changePassword(current, next).subscribe({
      next: () => {
        this.pwdBusy.set(false);
        this.pwdOpen.set(false);
        this.pwdCurrent.set('');
        this.pwdNew.set('');
        // Le changement périme les jetons émis avant : la session courante comprise.
        this.toast.success('Mot de passe changé — reconnecte-toi.');
        this.auth.logout();
        this.router.navigateByUrl('/login');
      },
      error: (err: { error?: { message?: string } }) => {
        this.pwdBusy.set(false);
        this.toast.error(err.error?.message ?? 'Changement impossible — vérifie ton mot de passe actuel.');
      },
    });
  }

  ngOnInit(): void {
    const me = this.auth.currentUser();
    this.acctName.set(me?.fullName ?? '');
    this.acctEmail.set(me?.email ?? '');
    this.portal.physio().subscribe({ next: (p) => this.physio.set(p), error: () => this.physio.set(null) });
    this.portal.vdot().subscribe({ next: (v) => this.vdot.set(v), error: () => this.vdot.set(null) });
    this.portal.races().subscribe({ next: (r) => this.races.set(r), error: () => this.races.set(null) });
    this.portal.performances().subscribe({ next: (p) => this.performances.set(p), error: () => this.performances.set([]) });
    this.portal.lactateTests().subscribe({ next: (t) => this.lactateTests.set(t), error: () => this.lactateTests.set([]) });
    this.notifications.preferences().subscribe({
      next: (p) => { this.prefs.set(p); this.usualSessionTime.set(p.usualSessionTime ?? ''); },
      error: () => this.usualSessionTime.set(''),
    });
    this.loadConsent();
    this.loadUnavailabilities();
  }

  // --- Consentement aux données de santé (RGPD art. 9 / 7-3) --------------------

  private loadConsent(): void {
    this.portal.healthConsent().subscribe({
      next: (c) => this.consent.set(c),
      error: () => this.consent.set(null),
    });
  }

  /**
   * Retrait du consentement. La confirmation nomme ce qui sera effacé : le retrait n'est pas
   * qu'un interrupteur, il détruit les données de santé déjà collectées — l'athlète doit le
   * savoir avant, pas le découvrir après.
   */
  async withdrawConsent(): Promise<void> {
    const ok = await this.confirm.ask({
      title: 'Retirer mon consentement',
      message:
        'Tes tests de lactate, tes douleurs et fatigues déclarées et les motifs médicaux de tes '
        + 'indisponibilités seront effacés — définitivement. Ton coach en sera informé et ne '
        + 'pourra plus en enregistrer. Ton compte et tes séances restent intacts.',
      confirmLabel: 'Retirer et effacer',
      danger: true,
    });
    if (!ok) return;
    this.portal.withdrawHealthConsent().subscribe({
      next: () => {
        this.toast.success('Consentement retiré. Tes données de santé ont été effacées.');
        this.loadConsent();
        // La fiche physio et les tests affichés à l'écran viennent d'être vidés côté serveur.
        this.portal.lactateTests().subscribe({ next: (t) => this.lactateTests.set(t) });
        this.loadUnavailabilities();
      },
    });
  }

  /** Réactivation : la collecte reprend, mais ce qui a été effacé ne revient pas. */
  grantConsent(): void {
    this.portal.grantHealthConsent().subscribe({
      next: () => {
        this.toast.success('Partage réactivé.');
        this.loadConsent();
      },
    });
  }

  // --- Indisponibilités déclarées par l'athlète ---------------------------------

  private loadUnavailabilities(): void {
    this.portal.unavailabilities().subscribe({
      next: (u) => this.unavailabilities.set(u),
      error: () => this.unavailabilities.set([]),
    });
  }

  private emptyUnavailDraft(): UnavailabilityRequest {
    const today = new Date().toISOString().slice(0, 10);
    return { startDate: today, endDate: today, reason: 'INJURY', notes: '' };
  }

  openUnavailForm(): void {
    this.unavailDraft = this.emptyUnavailDraft();
    this.showUnavailForm.set(true);
  }

  reasonLabel(reason: UnavailabilityReason): string {
    return this.reasonOptions.find((o) => o.value === reason)?.label ?? 'Autre';
  }

  submitUnavailability(): void {
    const draft = this.unavailDraft;
    if (!draft.startDate || !draft.endDate) {
      this.toast.error('Renseigne les deux dates.');
      return;
    }
    if (draft.endDate < draft.startDate) {
      this.toast.error('La date de fin précède la date de début.');
      return;
    }
    this.unavailBusy.set(true);
    this.portal.declareUnavailability({ ...draft, notes: draft.notes?.trim() || null }).subscribe({
      next: () => {
        this.toast.success('Indisponibilité déclarée, ton coach est prévenu.');
        this.showUnavailForm.set(false);
        this.unavailBusy.set(false);
        this.loadUnavailabilities();
      },
      error: () => this.unavailBusy.set(false),
    });
  }

  async removeUnavailability(u: Unavailability): Promise<void> {
    const ok = await this.confirm.ask({
      title: 'Retirer cette indisponibilité ?',
      message: 'Ton coach pourra de nouveau planifier des séances sur cette période.',
      confirmLabel: 'Retirer',
      danger: true,
    });
    if (!ok) {
      return;
    }
    this.portal.removeUnavailability(u.id).subscribe({
      next: () => {
        this.toast.success('Indisponibilité retirée.');
        this.loadUnavailabilities();
      },
    });
  }

  /**
   * Enregistre l'heure habituelle de séance (ancre du rappel de débriefing). Vide = pas de
   * rappel : refuser une relance doit être aussi simple que la régler.
   */
  onUsualTimeChange(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.usualSessionTime.set(value);
    this.notifications.savePreferences({ usualSessionTime: value }).subscribe({
      next: () => this.toast.success(value ? `Rappel de ressenti à ${this.debriefLabel(value)}` : 'Rappel de ressenti désactivé'),
      error: () => this.toast.error('Préférence non enregistrée.'),
    });
  }

  /** Active ou coupe un canal de notification (push / e-mail). */
  setChannel(channel: 'email' | 'push', event: Event): void {
    const enabled = (event.target as HTMLInputElement).checked;
    const patch = channel === 'email' ? { emailEnabled: enabled } : { pushEnabled: enabled };
    this.notifications.savePreferences(patch).subscribe({
      next: (p) => { this.prefs.set(p); this.toast.success(enabled ? 'Canal activé' : 'Canal désactivé'); },
      error: () => this.toast.error('Préférence non enregistrée.'),
    });
  }

  /** « 18:00 » → « 20:00 » : on annonce l'heure du rappel, pas celle de la séance. */
  private debriefLabel(time: string): string {
    const [h, m] = time.split(':').map(Number);
    return `${String((h + 2) % 24).padStart(2, '0')}:${String(m).padStart(2, '0')}`;
  }

  /** Au moins une donnée physio renseignée (sinon on affiche un message d'attente). */
  hasPhysio(p: PhysioProfile): boolean {
    return p.vdot != null || p.lt1Kmh != null || p.lt2Kmh != null || p.vcKmh != null
      || p.fcMax != null || p.fcLt1 != null || p.fcLt2 != null;
  }

  exportData(): void {
    this.portal.export().subscribe((data) => {
      const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'darilab-mes-donnees.json';
      a.click();
      URL.revokeObjectURL(url);
      this.toast.success('Export téléchargé.');
    });
  }

  /** Déconnexion — déplacée depuis la barre supérieure de « Aujourd'hui ». */
  logout(): void {
    this.auth.logout();
    this.router.navigate(['/']);
  }

  /**
   * Suppression de compte, en deux temps.
   *
   * <p>Une seule boîte de dialogue séparait un tap d'une suppression en cascade — profil,
   * séances, activités, check-ins, tests, messages, compte — sans ressaisie, sans délai, sans
   * confirmation par e-mail. Le seul recours était la restauration sélective d'une sauvegarde,
   * une procédure lourde. C'est trop court pour une action irréversible sur un écran consulté
   * au téléphone.</p>
   *
   * <p>On ajoute donc une étape volontaire — écrire SUPPRIMER — et on propose d'abord l'export,
   * puisque c'est presque toujours ce que l'on veut avant de partir. La mention du coach est là
   * parce que l'historique effacé est aussi le sien : il ne sera pas prévenu autrement.</p>
   */
  async deleteAccount(): Promise<void> {
    const confirmed = await this.confirm.askForText({
      title: 'Supprimer mon compte',
      message:
        'Cette action est irréversible : ton profil, tes séances, tes activités, tes ressentis '
        + 'et tes messages seront effacés. Ton coach perdra aussi tout ton historique. '
        + 'Pense à exporter tes données avant, si tu veux les garder.',
      requiredText: 'SUPPRIMER',
      confirmLabel: 'Supprimer définitivement',
      danger: true,
    });
    if (!confirmed) return;
    this.portal.deleteAccount().subscribe(() => {
      this.auth.logout();
      this.toast.info('Ton compte a été supprimé.');
      this.router.navigate(['/']);
    });
  }
}
