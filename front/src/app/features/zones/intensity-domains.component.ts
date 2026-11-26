import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ClubDefaults, ClubService } from '../../core/services/club.service';
import { ToastService } from '../../core/services/toast.service';
import { IconComponent } from '../../shared/components/icon/icon.component';

/**
 * Domaines d'intensité par défaut du club, réglés depuis « Mes zones ».
 *
 * <p>Ils vivaient dans Paramètres, loin des zones : le coach voyait deux systèmes (zones en %
 * de LT1/LT2, domaines en % de VC/FC max) sans savoir lequel servait où. Ici, sous les zones,
 * avec le mot qui dit leur usage : la répartition de l'onglet Charge.</p>
 */
@Component({
  selector: 'app-intensity-domains',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, IconComponent],
  template: `
    <details class="card dom">
      <summary>
        <span class="dom__t">Domaines (pour l'analyse de charge)</span>
        <app-icon name="chevron-down" [size]="16" class="dom__chev" />
      </summary>
      <p class="field-hint">
        Trois domaines, plus grossiers que les zones, servent à répartir la charge de chaque athlète
        (onglet Charge de sa fiche). Ces bornes s'appliquent aux <strong>nouveaux</strong> athlètes ;
        les athlètes existants gardent les leurs.
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
        <button type="button" class="btn btn-primary" (click)="save()" [disabled]="saving()">
          <app-icon name="check" [size]="15" /> Enregistrer les domaines
        </button>
      } @else {
        <div class="skeleton" style="height: 90px;"></div>
      }
    </details>
  `,
  styles: [`
    .dom { margin-top: var(--sp-5); }
    .dom summary { display: flex; align-items: center; justify-content: space-between; cursor: pointer; list-style: none; }
    .dom summary::-webkit-details-marker { display: none; }
    .dom__t { font-weight: 700; font-size: var(--text-md); }
    .dom[open] .dom__chev { transform: rotate(180deg); }
    .dom[open] summary { margin-bottom: var(--sp-2); }
    .dom-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--sp-3); margin: var(--sp-3) 0; }
    .dom-grid label { display: flex; flex-direction: column; gap: var(--sp-1); font-size: var(--text-sm); color: var(--ink-2); }
    .dot { width: 12px; height: 12px; border-radius: 50%; display: inline-block; margin-right: var(--sp-1); }
    .dom1 { background: var(--domain-1); }
    .dom2 { background: var(--domain-2); }
    @media (max-width: 480px) { .dom-grid { grid-template-columns: 1fr; } }
  `],
})
export class IntensityDomainsComponent implements OnInit {
  private readonly clubService = inject(ClubService);
  private readonly toast = inject(ToastService);

  readonly defaults = signal<ClubDefaults | null>(null);
  readonly saving = signal(false);

  ngOnInit(): void {
    this.clubService.defaults().subscribe({
      next: (d) => this.defaults.set(d),
      error: () => this.defaults.set(null),
    });
  }

  save(): void {
    const d = this.defaults();
    if (!d || this.saving()) return;
    this.saving.set(true);
    this.clubService.updateDefaults(d).subscribe({
      next: (saved) => {
        this.defaults.set(saved);
        this.saving.set(false);
        this.toast.success('Domaines enregistrés — appliqués aux prochains athlètes créés.');
      },
      error: () => {
        this.saving.set(false);
        this.toast.error('Enregistrement impossible.');
      },
    });
  }
}
