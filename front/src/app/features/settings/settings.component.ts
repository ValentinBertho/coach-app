import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { AuthService } from '../../core/services/auth.service';
import { ThemeService, type ThemePref } from '../../core/services/theme.service';
import { SegmentedControlComponent, type SegmentOption } from '../../shared/components/ui';

/** Écran Paramètres (s-settings) : profil coach, club et réglages physiologiques par défaut. */
@Component({
  selector: 'app-settings',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [SegmentedControlComponent],
  template: `
    <section class="page-header">
      <div>
        <h1 class="display-sm">Paramètres</h1>
        <p class="subtitle">Profil, club et réglages par défaut.</p>
      </div>
    </section>

    <div class="rel-grid">
      <div class="card">
        <h2>Apparence</h2>
        <p class="field-hint">Thème de l'interface. « Système » suit les réglages de votre appareil.</p>
        <app-segmented-control [options]="themeOptions" [value]="theme.preference()"
          (valueChange)="setTheme($any($event))" />
      </div>

      <div class="card">
        <h2>Profil coach</h2>
        @if (user(); as u) {
          <dl class="kv">
            <dt>Nom</dt><dd>{{ u.fullName }}</dd>
            <dt>Email</dt><dd class="metric">{{ u.email }}</dd>
            <dt>Rôle</dt><dd>{{ u.role }}</dd>
            <dt>Club</dt><dd>{{ u.clubName || '—' }}</dd>
          </dl>
        }
      </div>

      <div class="card">
        <h2>Domaines d'intensité (défauts)</h2>
        <p class="field-hint">Bornes par défaut, ajustables par athlète sur sa fiche physio.</p>
        <ul class="dom-list">
          <li><span class="dot dom1"></span> Domaine 1 — sous LT1 (&lt; 90 % VC / &lt; 80 % FCmax)</li>
          <li><span class="dot dom2"></span> Domaine 2 — entre LT1 et LT2 (90–100 % VC)</li>
          <li><span class="dot dom3"></span> Domaine 3 — au-dessus de LT2 (&gt; 100 % VC)</li>
        </ul>
      </div>

      <div class="card">
        <h2>Prescription</h2>
        <p class="field-hint">Règles Darilab appliquées partout :</p>
        <ul class="rules">
          <li> Prescription en <strong>fourchettes</strong> (min–max), jamais de valeur sèche</li>
          <li> État de forme = <strong>fatigue + douleur</strong> (jamais le RPE)</li>
          <li> Charge unifiée <strong>course + force</strong> (sRPE Foster)</li>
          <li> 1RM par défaut : <strong>Nuzzo</strong> (Pr. Lacourpaille)</li>
        </ul>
      </div>

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
    </div>
  `,
  styles: [`
    .billing { display: flex; flex-wrap: wrap; gap: var(--sp-3) var(--sp-6); margin: 0 0 var(--sp-3); }
    .billing div { display: flex; flex-direction: column; gap: 2px; }
    .billing dt { color: var(--ink-3); font-size: var(--text-xs); font-weight: 700; text-transform: uppercase; letter-spacing: 0.03em; }
    .billing dd { margin: 0; font-weight: 600; color: var(--ink); }
    .rel-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: var(--sp-4); }
    .kv { display: grid; grid-template-columns: 100px 1fr; gap: var(--sp-2) var(--sp-3); margin: 0; }
    .kv dt { color: var(--ink-3); font-size: var(--text-sm); }
    .kv dd { margin: 0; }
    .dom-list, .rules { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: var(--sp-2); }
    .dom-list li { display: flex; align-items: center; gap: var(--sp-2); }
    .dot { width: 12px; height: 12px; border-radius: 50%; }
    .dom1 { background: var(--domain-1); }
    .dom2 { background: var(--domain-2); }
    .dom3 { background: var(--domain-3); }
  `],
})
export class SettingsComponent {
  private readonly auth = inject(AuthService);
  readonly theme = inject(ThemeService);
  readonly user = this.auth.currentUser;

  readonly themeOptions: SegmentOption[] = [
    { value: 'light', label: 'Clair' },
    { value: 'dark', label: 'Sombre' },
    { value: 'system', label: 'Système' },
  ];

  setTheme(pref: ThemePref): void {
    this.theme.set(pref);
  }
}
