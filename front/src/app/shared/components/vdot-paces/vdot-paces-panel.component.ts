import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Performance, Vdot, VdotPaceItem } from '../../../core/models/physio.model';
import { DataOriginTagComponent } from '../data-origin-tag/data-origin-tag.component';
import { IconComponent } from '../icon/icon.component';

/**
 * Libellés des distances, tels que le serveur les code ({@code RunDistance.code()}).
 * Une distance inconnue est affichée telle quelle plutôt que masquée : mieux vaut un code brut
 * qu'une ligne manquante dans un tableau d'allures.
 */
const DISTANCE_LABELS: Record<string, string> = {
  '800m': '800 m',
  '1500m': '1500 m',
  '3000m': '3000 m',
  '5km': '5 km',
  '10km': '10 km',
  '15km': '15 km',
  semi: 'Semi',
  marathon: 'Marathon',
};

/** Allures d'entraînement dérivées du même VDOT. */
const TRAINING_LABELS: Record<string, string> = {
  EASY: 'Endurance fondamentale',
  THRESHOLD: 'Seuil',
};

/** Un chrono saisi, prêt à afficher. */
interface RecordRow {
  readonly distance: string;
  readonly label: string;
  readonly time: string;
  readonly dateSet: string | null;
}

/**
 * Records de l'athlète et allures qui en découlent.
 *
 * <h2>Ce que cet écran rend visible</h2>
 *
 * <p>Le VDOT et ses allures d'équivalence étaient calculés, stockés, et servaient déjà d'ancres
 * au calcul des zones — mais le coach ne les voyait nulle part : sa fiche n'affichait que le
 * VDOT, un nombre sans traduction. Il fallait connaître la table de Daniels de tête pour savoir
 * ce qu'un 10 km en 40:00 implique sur semi ou sur marathon.</p>
 *
 * <p>Le même panneau sert au coach et à l'athlète : deux lectures différentes de la même table
 * finiraient par diverger, et c'est le genre de divergence qu'on ne remarque qu'en réunion.</p>
 *
 * <p>Panneau en <b>lecture seule</b>. La saisie reste à un seul endroit — l'écran des tests —
 * pour qu'il n'y ait jamais de doute sur l'endroit où corriger un chrono. {@link #manageLink}
 * y conduit quand l'appelant en fournit un.</p>
 */
@Component({
  selector: 'app-vdot-paces-panel',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DecimalPipe, RouterLink, IconComponent, DataOriginTagComponent],
  template: `
    <section class="card vpp">
      <div class="card-hd">
        <h2>{{ heading() }}</h2>
        <div class="vpp-hd-right">
          @if (vdotValue(); as v) {
            <span class="vpp-vdot" [attr.aria-label]="'VDOT ' + v">
              VDOT <strong class="metric">{{ v | number: '1.1-1' }}</strong>
            </span>
          }
          <app-data-origin-tag origin="estime" label="Estimé" />
        </div>
      </div>

      @if (!hasVdot()) {
        <!-- Sans chrono, rien n'est calculable : on dit quoi faire plutôt que d'afficher un
             tableau vide, qui se lirait comme une panne. -->
        <p class="field-hint vpp-empty">
          Aucun chrono de référence. Ajoute un record (1500 m, 5 km, 10 km, semi, marathon…)
          pour obtenir le VDOT, les allures d'équivalence et les allures d'entraînement.
        </p>
        @if (manageLink(); as link) {
          <a class="btn btn-primary btn-sm" [routerLink]="link">
            <app-icon name="plus" [size]="15" /> Saisir un record
          </a>
        }
      } @else {
        @if (records().length) {
          <div class="vpp-block">
            <h3 class="vpp-h3">Records</h3>
            <ul class="vpp-records">
              @for (r of records(); track r.distance) {
                <li>
                  <span class="vpp-d">{{ r.label }}</span>
                  <span class="vpp-t metric">{{ r.time }}</span>
                  @if (r.dateSet) { <span class="field-hint vpp-when">{{ r.dateSet }}</span> }
                </li>
              }
            </ul>
          </div>
        }

        <div class="vpp-block">
          <h3 class="vpp-h3">Allures d'équivalence</h3>
          <p class="field-hint vpp-note">
            Ce que ces chronos impliquent sur les autres distances, à VDOT constant.
          </p>
          <ul class="vpp-paces">
            @for (p of paces(); track p.distance) {
              <li>
                <span class="vpp-d">{{ label(p.distance) }}</span>
                <span class="vpp-p metric">{{ p.paceLabel }}<small> /km</small></span>
                @if (p.speedKmh != null) {
                  <span class="field-hint vpp-kmh">{{ p.speedKmh | number: '1.1-1' }} km/h</span>
                }
              </li>
            }
          </ul>
        </div>

        @if (trainingPaces().length) {
          <div class="vpp-block">
            <h3 class="vpp-h3">Allures d'entraînement</h3>
            <ul class="vpp-paces">
              @for (p of trainingPaces(); track p.distance) {
                <li>
                  <span class="vpp-d">{{ trainingLabel(p.distance) }}</span>
                  <span class="vpp-p metric">{{ p.paceLabel }}<small> /km</small></span>
                  @if (p.speedKmh != null) {
                    <span class="field-hint vpp-kmh">{{ p.speedKmh | number: '1.1-1' }} km/h</span>
                  }
                </li>
              }
            </ul>
          </div>
        }

        @if (manageLink(); as link) {
          <a class="vpp-edit" [routerLink]="link">Modifier les records →</a>
        }
      }
    </section>
  `,
  styles: [`
    .vpp { padding: var(--sp-4); }
    .card-hd { display: flex; align-items: center; justify-content: space-between; gap: var(--sp-3); flex-wrap: wrap; }
    .card-hd h2 { margin: 0; }
    .vpp-hd-right { display: flex; align-items: center; gap: var(--sp-3); }
    .vpp-vdot { color: var(--ink-2); font-size: var(--text-sm); }
    .vpp-vdot strong { font-size: var(--text-lg); color: var(--ink); margin-left: var(--sp-1); }
    .vpp-empty { margin: var(--sp-3) 0; line-height: 1.5; }
    .vpp-block { margin-top: var(--sp-4); }
    .vpp-h3 { margin: 0 0 var(--sp-1); font-size: var(--text-sm); color: var(--ink-2); text-transform: uppercase; letter-spacing: 0.03em; }
    .vpp-note { margin: 0 0 var(--sp-2); }
    .vpp-records, .vpp-paces { list-style: none; margin: 0; padding: 0; }
    .vpp-records li, .vpp-paces li { display: flex; align-items: baseline; gap: var(--sp-3); padding: var(--sp-2) 0; border-top: 1px solid var(--hairline); }
    .vpp-records li:first-child, .vpp-paces li:first-child { border-top: none; }
    .vpp-d { min-width: 9.5rem; color: var(--ink-2); }
    .vpp-t, .vpp-p { color: var(--ink); font-variant-numeric: tabular-nums; }
    .vpp-p small { color: var(--ink-3); font-weight: 400; }
    .vpp-when, .vpp-kmh { margin-left: auto; }
    .vpp-edit { display: inline-block; margin-top: var(--sp-4); font-size: var(--text-sm); }
    @media (max-width: 480px) {
      .vpp-d { min-width: 7rem; }
      .vpp-when, .vpp-kmh { display: none; }
    }
  `],
})
export class VdotPacesPanelComponent {
  /** Le VDOT et ses allures, tels que rendus par {@code GET .../vdot}. */
  readonly vdot = input<Vdot | null>(null);
  /** Chronos saisis. Facultatifs : le panneau reste utile avec les seules allures. */
  readonly performances = input<Performance[] | null>(null);
  /** Où corriger les records. Absent, le panneau n'affiche aucun lien. */
  readonly manageLink = input<string[] | null>(null);
  /** Titre, pour tutoyer l'athlète sur son portail et rester neutre côté coach. */
  readonly heading = input('Records & allures');

  readonly vdotValue = computed(() => this.vdot()?.vdot ?? null);
  readonly hasVdot = computed(() => this.vdotValue() != null && this.paces().length > 0);
  readonly paces = computed<VdotPaceItem[]>(() => this.vdot()?.paces ?? []);
  readonly trainingPaces = computed<VdotPaceItem[]>(() => this.vdot()?.trainingPaces ?? []);

  /**
   * Un chrono par distance : le meilleur. Un athlète en saisit plusieurs sur la même distance au
   * fil des saisons, et c'est le record qui a sa place ici — l'historique complet se lit sur
   * l'écran de saisie.
   */
  readonly records = computed<RecordRow[]>(() => {
    const perfs = this.performances() ?? [];
    const best = new Map<string, Performance>();
    for (const p of perfs) {
      const code = p.distanceCode || p.distance;
      const kept = best.get(code);
      if (!kept || p.timeSeconds < kept.timeSeconds) {
        best.set(code, p);
      }
    }
    const order = Object.keys(DISTANCE_LABELS);
    return [...best.entries()]
      .sort((a, b) => order.indexOf(a[0]) - order.indexOf(b[0]))
      .map(([code, p]) => ({
        distance: code,
        label: this.label(code),
        time: formatTime(p.timeSeconds),
        dateSet: p.dateSet,
      }));
  });

  label(code: string): string {
    return DISTANCE_LABELS[code] ?? code;
  }

  trainingLabel(code: string): string {
    return TRAINING_LABELS[code] ?? code;
  }
}

/** `h:mm:ss` au-delà de l'heure, `m:ss` en deçà — comme le reste du produit. */
function formatTime(totalSeconds: number): string {
  const h = Math.floor(totalSeconds / 3600);
  const m = Math.floor((totalSeconds % 3600) / 60);
  const s = totalSeconds % 60;
  const two = (n: number) => String(n).padStart(2, '0');
  return h > 0 ? `${h}:${two(m)}:${two(s)}` : `${m}:${two(s)}`;
}
