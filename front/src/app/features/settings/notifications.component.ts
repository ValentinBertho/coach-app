import { ChangeDetectionStrategy, Component } from '@angular/core';

/** Écran Notifications (s-notifications) : alertes que le système DARI Lab déclenche. */
@Component({
  selector: 'app-notifications',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="page-header">
      <div>
        <h1 class="display-sm">Notifications</h1>
        <p class="subtitle">Alertes automatiques sur l'état et la charge de tes athlètes.</p>
      </div>
    </section>

    <div class="card">
      <h2>Types d'alertes</h2>
      <ul class="notif-list">
        @for (n of types; track n.label) {
          <li class="notif">
            <span class="notif-ic" [style.background]="n.color">{{ n.icon }}</span>
            <div><strong>{{ n.label }}</strong><span class="field-hint">{{ n.desc }}</span></div>
          </li>
        }
      </ul>
    </div>

    <div class="card empty-state">
      <span style="font-size:32px">🔔</span>
      <h2>Aucune notification pour le moment</h2>
      <p class="field-hint">Les alertes apparaîtront ici dès qu'un athlète renseigne une fatigue/douleur élevée ou déplace une séance.</p>
    </div>
  `,
  styles: [`
    .notif-list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: var(--sp-3); }
    .notif { display: flex; align-items: center; gap: var(--sp-3); }
    .notif-ic { width: 36px; height: 36px; border-radius: var(--radius-sm); display: flex; align-items: center; justify-content: center; color: #fff; }
    .notif div { display: flex; flex-direction: column; }
  `],
})
export class NotificationsComponent {
  readonly types = [
    { icon: '🔴', color: 'var(--dari-red)', label: 'Fatigue élevée', desc: 'Un athlète a renseigné une fatigue ≥ 8' },
    { icon: '🩹', color: 'var(--dari-orange)', label: 'Douleur', desc: 'Douleur ≥ 5, ou ≥ 3 en réathlétisation' },
    { icon: '📅', color: 'var(--dari-teal)', label: 'Séance déplacée', desc: 'Un athlète a déplacé une séance' },
    { icon: '💬', color: 'var(--primary)', label: 'Nouveau retour', desc: 'Une séance a été renseignée' },
    { icon: '⚡', color: 'var(--block-classique)', label: 'Activité synchronisée', desc: 'Nouvelle activité importée (Strava/Garmin)' },
  ];
}
