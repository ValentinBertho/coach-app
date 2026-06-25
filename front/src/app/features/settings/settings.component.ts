import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { AuthService } from '../../core/services/auth.service';

/** Écran Paramètres (s-settings) : profil coach, club et réglages physiologiques par défaut. */
@Component({
  selector: 'app-settings',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="page-header">
      <div>
        <h1 class="display-sm">Paramètres</h1>
        <p class="subtitle">Profil, club et réglages par défaut.</p>
      </div>
    </section>

    <div class="rel-grid">
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
    </div>
  `,
  styles: [`
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
  readonly user = this.auth.currentUser;
}
