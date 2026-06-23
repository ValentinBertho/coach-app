import { ChangeDetectionStrategy, Component } from '@angular/core';

/** Écran Synchronisations (s-sync) : connexion des montres/plateformes (cf. DARI Lab §12). */
@Component({
  selector: 'app-sync',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="page-header">
      <div>
        <h1 class="display-sm">Synchronisations</h1>
        <p class="subtitle">Connecte une plateforme pour importer les activités des athlètes.</p>
      </div>
    </section>

    <div class="sync-grid">
      @for (p of providers; track p.name) {
        <div class="card sync-card">
          <span class="sync-logo">{{ p.icon }}</span>
          <div class="sync-info">
            <strong>{{ p.name }}</strong>
            <span class="field-hint">{{ p.desc }}</span>
          </div>
          <span class="badge" [class.badge-success]="p.status === 'Disponible'" [class.badge-neutral]="p.status !== 'Disponible'">{{ p.status }}</span>
          <button type="button" class="btn btn-ghost btn-sm" disabled>Connecter</button>
        </div>
      }
    </div>

    <div class="card info-card">
      <h2>Comment ça marche</h2>
      <p class="field-hint">
        Après connexion OAuth, les activités (distance, durée, allure, FC, GPS) sont importées
        automatiquement et rapprochées des séances prescrites. L'intégration Strava/Garmin est
        prévue ; le rapprochement activité ↔ séance s'appuie sur le calendrier de l'athlète.
      </p>
    </div>
  `,
  styles: [`
    .sync-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: var(--sp-4); margin-bottom: var(--sp-4); }
    .sync-card { display: flex; align-items: center; gap: var(--sp-3); }
    .sync-logo { font-size: 28px; }
    .sync-info { display: flex; flex-direction: column; flex: 1; }
    .info-card { max-width: 720px; }
  `],
})
export class SyncComponent {
  readonly providers = [
    { name: 'Strava', icon: '🟠', desc: 'Import automatique des activités', status: 'Bientôt' },
    { name: 'Garmin Connect', icon: '⌚', desc: 'Pull activités + push séances', status: 'Bientôt' },
    { name: 'COROS', icon: '🔵', desc: 'Import des activités', status: 'Bientôt' },
    { name: 'Polar', icon: '🔴', desc: 'Import des activités', status: 'Bientôt' },
  ];
}
