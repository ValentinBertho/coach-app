import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { PhysioService } from '../../core/services/physio.service';
import { PhysioProfile, Vdot } from '../../core/models/physio.model';

/**
 * Panneau « Profil physiologique » Darilab : VDOT, allures d'équivalence et seuils LT1/LT2/VC.
 * Affiché sur la fiche athlète.
 */
@Component({
  selector: 'app-physio-panel',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    <div class="card physio-card">
      <div class="physio-head">
        <h2>Profil physiologique</h2>
        @if (profile()?.discipline; as d) {
          <span class="discipline-chip" [class.discipline-chip--route]="d === 'ROUTE'"
                [class.discipline-chip--trail]="d === 'TRAIL'">
            <app-icon [name]="d === 'TRAIL' ? 'mountain' : 'footprints'" [size]="13" /> {{ d === 'TRAIL' ? 'Trail' : 'Route' }}
          </span>
        }
      </div>

      @if (loading()) {
        <p class="field-hint">Chargement…</p>
      } @else {
        <div class="physio-grid">
          <div class="vdot-tile">
            <span class="stat-label">VDOT</span>
            <span class="metric vdot-value">{{ profile()?.vdot ?? '—' }}</span>
          </div>

          <div class="seuils">
            <div class="seuil-row">
              <span class="seuil-name">LT1</span>
              <span class="metric">{{ kmh(profile()?.lt1Kmh) }}</span>
              <span class="seuil-fc metric">{{ fc(profile()?.fcLt1) }}</span>
            </div>
            <div class="seuil-row">
              <span class="seuil-name">LT2</span>
              <span class="metric">{{ kmh(profile()?.lt2Kmh) }}</span>
              <span class="seuil-fc metric">{{ fc(profile()?.fcLt2) }}</span>
            </div>
            <div class="seuil-row">
              <span class="seuil-name">VC</span>
              <span class="metric">{{ kmh(profile()?.vcKmh) }}</span>
              <span class="seuil-fc metric">{{ fc(profile()?.fcMax) }} max</span>
            </div>
          </div>
        </div>

        @if (vdot()?.paces?.length) {
          <h3 class="allures-title">Allures calculées (VDOT)</h3>
          <table class="allures">
            <tbody>
              @for (p of vdot()!.paces; track p.distance) {
                @if (p.paceSecPerKm) {
                  <tr>
                    <td class="allure-dist">{{ p.distance }}</td>
                    <td class="metric allure-pace">{{ p.paceLabel }}/km</td>
                    <td class="metric allure-speed">{{ p.speedKmh }} km/h</td>
                  </tr>
                }
              }
            </tbody>
          </table>
        } @else {
          <p class="empty-hint">Aucune allure : ajoute une performance pour calculer le VDOT.</p>
        }
      }
    </div>
  `,
  styles: [
    `
      .physio-card {
        background: var(--gradient-night);
        color: var(--night-text);
        border-color: var(--night-2);
      }
      .physio-head {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: var(--sp-3);
        margin-bottom: var(--sp-4);
      }
      .physio-head h2 {
        color: var(--night-text);
        margin: 0;
      }
      .physio-grid {
        display: grid;
        grid-template-columns: auto 1fr;
        gap: var(--sp-6);
        align-items: center;
      }
      .vdot-tile {
        display: flex;
        flex-direction: column;
        gap: var(--sp-1);
        padding-right: var(--sp-6);
        border-right: 1px solid var(--night-2);
      }
      .vdot-value {
        font-size: 44px;
        line-height: 1;
        color: var(--dari-teal);
      }
      .stat-label {
        color: var(--night-text-2);
        font-size: var(--text-sm);
      }
      .seuils {
        display: flex;
        flex-direction: column;
        gap: var(--sp-2);
      }
      .seuil-row {
        display: grid;
        grid-template-columns: 48px 1fr auto;
        gap: var(--sp-3);
        align-items: baseline;
      }
      .seuil-name {
        font-weight: 700;
        color: var(--dari-teal);
      }
      .seuil-fc {
        color: var(--night-text-2);
        font-size: var(--text-sm);
      }
      .allures-title {
        margin: var(--sp-5) 0 var(--sp-2);
        font-size: var(--text-md);
        color: var(--night-text);
      }
      .allures {
        width: 100%;
        border-collapse: collapse;
      }
      .allures td {
        padding: var(--sp-2) 0;
        border-bottom: 1px solid var(--night-2);
      }
      .allure-dist {
        color: var(--night-text-2);
      }
      .allure-pace {
        color: var(--dari-teal);
        font-weight: 600;
      }
      .allure-speed {
        text-align: right;
        color: var(--night-text-2);
      }
      .empty-hint {
        color: var(--night-text-2);
        margin-top: var(--sp-4);
      }
    `,
  ],
})
export class PhysioPanelComponent implements OnInit {
  readonly athleteId = input.required<string>();

  private readonly physio = inject(PhysioService);

  readonly profile = signal<PhysioProfile | null>(null);
  readonly vdot = signal<Vdot | null>(null);
  readonly loading = signal(true);

  ngOnInit(): void {
    this.physio.profile(this.athleteId()).subscribe({
      next: (p) => {
        this.profile.set(p);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
    this.physio.vdot(this.athleteId()).subscribe({ next: (v) => this.vdot.set(v) });
  }

  kmh(value: number | null | undefined): string {
    return value ? `${value} km/h` : '—';
  }

  fc(value: number | null | undefined): string {
    return value ? `${value} bpm` : '—';
  }
}
