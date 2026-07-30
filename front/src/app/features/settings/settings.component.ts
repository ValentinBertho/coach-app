import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { ClubDefaults, ClubService } from '../../core/services/club.service';
import { ThemeService, type ThemePref } from '../../core/services/theme.service';
import { ToastService } from '../../core/services/toast.service';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { SegmentedControlComponent, type SegmentOption } from '../../shared/components/ui';

/**
 * Écran Paramètres — profil coach, préférences d'affichage et défauts physiologiques du club.
 *
 * L'écran était une vitrine : profil non éditable, domaines d'intensité en texte statique. Tout
 * ce qui est affiché ici est désormais réglable, ou renvoie vers l'endroit qui le règle.
 */
@Component({
  selector: 'app-settings',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink, IconComponent, SegmentedControlComponent],
  template: `
    <section class="page-header">
      <div>
        <h1 class="display-sm">Paramètres</h1>
        <p class="subtitle">Profil, préférences d'affichage et réglages par défaut.</p>
      </div>
    </section>

    <div class="rel-grid">
      <!-- ===== Profil coach : éditable ===== -->
      <div class="card">
        <h2>Profil coach</h2>
        @if (user(); as u) {
          <div class="form-group">
            <label for="fullName">Nom</label>
            <input id="fullName" class="form-control" [(ngModel)]="profile.fullName" maxlength="255" />
          </div>
          <div class="form-group">
            <label for="email">Adresse e-mail</label>
            <input id="email" type="email" class="form-control" [(ngModel)]="profile.email" maxlength="255" />
            @if (emailChanged()) {
              <p class="field-hint">
                <app-icon name="alert-triangle" [size]="14" />
                Changer d'adresse repasse le compte en « non confirmé » : un lien de confirmation
                sera envoyé à la nouvelle adresse.
              </p>
            }
          </div>
          <dl class="kv">
            <dt>Rôle</dt><dd>{{ u.role }}</dd>
            <dt>Club</dt><dd>{{ u.clubName || '—' }}</dd>
          </dl>
          <button type="button" class="btn btn-primary" (click)="saveProfile()" [disabled]="savingProfile()">
            <app-icon name="check" [size]="15" /> Enregistrer le profil
          </button>
        }
      </div>

      <!-- ===== Mot de passe ===== -->
      <div class="card">
        <h2>Mot de passe</h2>
        <p class="field-hint">Le mot de passe actuel est demandé pour valider le changement.</p>
        <div class="form-group">
          <label for="curPwd">Mot de passe actuel</label>
          <input id="curPwd" type="password" class="form-control" autocomplete="current-password"
                 [(ngModel)]="password.current" />
        </div>
        <div class="form-group">
          <label for="newPwd">Nouveau mot de passe</label>
          <input id="newPwd" type="password" class="form-control" autocomplete="new-password"
                 [(ngModel)]="password.next" />
          <p class="field-hint">8 caractères minimum.</p>
        </div>
        <button type="button" class="btn btn-ghost" (click)="savePassword()" [disabled]="savingPassword()">
          Changer le mot de passe
        </button>
      </div>

      <!-- ===== Préférences d'affichage ===== -->
      <div class="card">
        <h2>Affichage</h2>
        <p class="field-hint">Thème de l'interface. « Système » suit les réglages de votre appareil.</p>
        <app-segmented-control [options]="themeOptions" [value]="theme.preference()"
          (valueChange)="setTheme($any($event))" />

        <h3 class="sub-h">Unité d'allure</h3>
        <p class="field-hint">
          Utilisée partout où une allure est affichée (cibles de séance, allures dérivées, tests).
        </p>
        <app-segmented-control [options]="paceOptions" [value]="profile.paceUnit"
          (valueChange)="setPaceUnit($event)" />
      </div>

      <!-- ===== Domaines d'intensité par défaut : réellement éditables ===== -->
      <div class="card">
        <h2>Domaines d'intensité (défauts)</h2>
        <p class="field-hint">
          Bornes appliquées aux <strong>nouveaux</strong> athlètes. Les athlètes existants gardent
          les leurs — un défaut ne réécrit jamais des valeurs déjà personnalisées.
        </p>
        @if (defaults(); as d) {
          <div class="dom-grid">
            <label><span class="dot dom1"></span> Fin du domaine 1 (% VC)
              <input type="number" step="0.5" min="50" max="150" class="form-control" [(ngModel)]="d.vcDomain1Pct" />
            </label>
            <label><span class="dot dom2"></span> Fin du domaine 2 (% VC)
              <input type="number" step="0.5" min="50" max="150" class="form-control" [(ngModel)]="d.vcDomain2Pct" />
            </label>
            <label><span class="dot dom1"></span> Fin du domaine 1 (% FC max)
              <input type="number" step="0.5" min="50" max="100" class="form-control" [(ngModel)]="d.fcDomain1Pct" />
            </label>
            <label><span class="dot dom2"></span> Fin du domaine 2 (% FC max)
              <input type="number" step="0.5" min="50" max="100" class="form-control" [(ngModel)]="d.fcDomain2Pct" />
            </label>
          </div>
          <button type="button" class="btn btn-primary" (click)="saveDefaults()" [disabled]="savingDefaults()">
            <app-icon name="check" [size]="15" /> Enregistrer les défauts
          </button>
        } @else {
          <div class="skeleton" style="height: 90px;"></div>
        }
      </div>

      <!-- ===== Facturation ===== -->
      <div class="card">
        <h2>Facturation</h2>
        @if (user(); as u) {
          <dl class="billing">
            <div><dt>Club</dt><dd>{{ u.clubName || '—' }}</dd></div>
            <div><dt>Offre</dt><dd><span class="badge badge-info">Bêta — gratuite</span></dd></div>
            <div><dt>Statut</dt><dd><span class="badge badge-success">Active</span></dd></div>
          </dl>
        }
        <p class="field-hint">La facturation par abonnement (paiement en ligne) sera disponible à la sortie de bêta. Aucune carte requise pour l'instant.</p>
      </div>

      <!-- Les « règles Darilab » étaient listées ici ; leur place est dans l'aide. -->
      <div class="card">
        <h2>Règles de prescription</h2>
        <p class="field-hint">
          Les principes appliqués partout dans l'app (fourchettes, forme = fatigue + douleur,
          charge unifiée, 1RM Nuzzo) sont détaillés dans le centre d'aide.
        </p>
        <a routerLink="/app/aide" class="btn btn-ghost">
          <app-icon name="life-buoy" [size]="15" /> Lire les règles Darilab
        </a>
      </div>
    </div>
  `,
  styles: [`
    .billing { display: flex; flex-wrap: wrap; gap: var(--sp-3) var(--sp-6); margin: 0 0 var(--sp-3); }
    .billing div { display: flex; flex-direction: column; gap: 2px; }
    .billing dt { color: var(--ink-3); font-size: var(--text-xs); font-weight: 700; text-transform: uppercase; letter-spacing: 0.03em; }
    .billing dd { margin: 0; font-weight: 600; color: var(--ink); }
    .rel-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: var(--sp-4); }
    .kv { display: grid; grid-template-columns: 100px 1fr; gap: var(--sp-2) var(--sp-3); margin: 0 0 var(--sp-3); }
    .kv dt { color: var(--ink-3); font-size: var(--text-sm); }
    .kv dd { margin: 0; }
    .sub-h { margin: var(--sp-4) 0 var(--sp-1); font-size: var(--text-md); }
    .dom-grid { display: grid; grid-template-columns: 1fr 1fr; gap: var(--sp-3); margin-bottom: var(--sp-3); }
    .dom-grid label { display: flex; flex-direction: column; gap: var(--sp-1); font-size: var(--text-sm); color: var(--ink-2); }
    .dot { width: 12px; height: 12px; border-radius: 50%; display: inline-block; margin-right: var(--sp-1); }
    .dom1 { background: var(--domain-1); }
    .dom2 { background: var(--domain-2); }
    @media (max-width: 480px) { .dom-grid { grid-template-columns: 1fr; } }
  `],
})
export class SettingsComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly clubService = inject(ClubService);
  private readonly toast = inject(ToastService);
  readonly theme = inject(ThemeService);
  readonly user = this.auth.currentUser;

  profile = { fullName: '', email: '', paceUnit: 'PACE' as 'PACE' | 'SPEED' };
  password = { current: '', next: '' };

  readonly savingProfile = signal(false);
  readonly savingPassword = signal(false);
  readonly savingDefaults = signal(false);
  readonly defaults = signal<ClubDefaults | null>(null);

  readonly themeOptions: SegmentOption[] = [
    { value: 'light', label: 'Clair' },
    { value: 'dark', label: 'Sombre' },
    { value: 'system', label: 'Système' },
  ];

  readonly paceOptions: SegmentOption[] = [
    { value: 'PACE', label: 'min/km' },
    { value: 'SPEED', label: 'km/h' },
  ];

  ngOnInit(): void {
    const u = this.user();
    if (u) {
      this.profile = { fullName: u.fullName, email: u.email, paceUnit: u.paceUnit ?? 'PACE' };
    }
    this.clubService.defaults().subscribe({
      next: (d) => this.defaults.set(d),
      error: () => this.defaults.set(null),
    });
  }

  /** Signale à l'utilisateur qu'il s'apprête à devoir reconfirmer son adresse. */
  emailChanged(): boolean {
    const u = this.user();
    return !!u && this.profile.email.trim().toLowerCase() !== u.email.toLowerCase();
  }

  setTheme(pref: ThemePref): void { this.theme.set(pref); }

  setPaceUnit(value: string): void {
    this.profile.paceUnit = value as 'PACE' | 'SPEED';
    // Réglage d'affichage : il s'applique tout de suite, sans bouton « Enregistrer », puisque
    // son effet est immédiatement visible sur les autres écrans.
    this.auth.updateProfile({
      fullName: this.profile.fullName.trim() || (this.user()?.fullName ?? ''),
      email: this.profile.email.trim() || (this.user()?.email ?? ''),
      paceUnit: this.profile.paceUnit,
    }).subscribe({
      next: () => this.toast.success('Unité d’allure mise à jour'),
      error: () => this.toast.error('Enregistrement impossible.'),
    });
  }

  saveProfile(): void {
    if (this.savingProfile()) return;
    if (!this.profile.fullName.trim() || !this.profile.email.trim()) {
      this.toast.warning('Nom et adresse e-mail sont requis.');
      return;
    }
    const willReverify = this.emailChanged();
    this.savingProfile.set(true);
    this.auth.updateProfile({
      fullName: this.profile.fullName.trim(),
      email: this.profile.email.trim(),
      paceUnit: this.profile.paceUnit,
    }).subscribe({
      next: () => {
        this.savingProfile.set(false);
        this.toast.success(willReverify
          ? 'Profil enregistré — confirmez votre nouvelle adresse depuis l’e-mail reçu.'
          : 'Profil enregistré');
      },
      error: () => { this.savingProfile.set(false); this.toast.error('Enregistrement impossible.'); },
    });
  }

  savePassword(): void {
    if (this.savingPassword()) return;
    if (!this.password.current || this.password.next.length < 8) {
      this.toast.warning('Mot de passe actuel requis, et nouveau d’au moins 8 caractères.');
      return;
    }
    this.savingPassword.set(true);
    this.auth.changePassword(this.password.current, this.password.next).subscribe({
      next: () => {
        this.savingPassword.set(false);
        this.password = { current: '', next: '' };
        this.toast.success('Mot de passe changé');
      },
      error: () => {
        this.savingPassword.set(false);
        this.toast.error('Changement impossible — mot de passe actuel incorrect ?');
      },
    });
  }

  saveDefaults(): void {
    const d = this.defaults();
    if (!d || this.savingDefaults()) return;
    this.savingDefaults.set(true);
    this.clubService.updateDefaults(d).subscribe({
      next: (saved) => {
        this.defaults.set(saved);
        this.savingDefaults.set(false);
        this.toast.success('Défauts enregistrés — appliqués aux prochains athlètes créés.');
      },
      error: () => { this.savingDefaults.set(false); this.toast.error('Enregistrement impossible.'); },
    });
  }
}
