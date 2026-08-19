import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import {
  Compliance, ComplianceDrift, ComplianceEffort, EffortVerdict,
} from '../../../core/models/decision.model';
import { formatPace } from '../../../core/utils/pace';
import { IconComponent } from '../icon/icon.component';

/**
 * En deçà, l'allure est considérée comme tenue : une montre et un GPS ne mesurent pas au dixième
 * de seconde, et deux secondes au kilomètre entre la première et la dernière répétition ne sont
 * pas une décision de coureur — c'est du bruit de mesure.
 */
const PACE_STABLE_S_PER_KM = 3;

const VERDICT_CLASS: Record<EffortVerdict, string> = {
  ON_TARGET: 'ok',
  TOO_FAST: 'fast',
  TOO_SLOW: 'slow',
  NOT_ASSESSED: 'na',
};

/**
 * « Séance tenue ? » — le verdict d'une séance, et le détail de ses répétitions.
 *
 * <p><b>Le manque qu'il comble.</b> Une séance était validée sur son volume total : un 10 × 400
 * couru à l'allure du dimanche comptait pour 100 %. Les fourchettes prescrites et les tours de la
 * montre dormaient pourtant côte à côte en base, sans jamais se rencontrer.</p>
 *
 * <p><b>Le parti pris d'affichage.</b> Une phrase d'abord — « Séance tenue à 92 %, les deux
 * dernières ont dérivé de 8 s/km » — et le détail seulement si on le demande. C'est la différence
 * entre lire un verdict et déchiffrer une courbe : le coach qui parcourt sa file du matin ne doit
 * avoir besoin que de la première ligne.</p>
 *
 * <p>Muet quand rien n'était jugeable. Une séance sans sortie rattachée n'est pas une séance ratée,
 * et afficher « 0 % » sur une absence de données est le meilleur moyen qu'on cesse de croire le
 * chiffre.</p>
 */
@Component({
  selector: 'app-compliance-verdict',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    @if (data(); as c) {
      @if (c.scorePct !== null) {
        <div class="cv" [class]="'cv--' + tone()">
          <button type="button" class="cv__hd" (click)="open.set(!open())"
                  [attr.aria-expanded]="open()">
            <span class="cv__score metric">{{ c.scorePct }}<small>%</small></span>
            <span class="cv__text">
              <strong>{{ c.verdict }}</strong>
              @if (c.detail) { <span class="cv__detail">{{ c.detail }}</span> }
            </span>
            <app-icon [name]="open() ? 'chevron-up' : 'chevron-down'" [size]="18" />
          </button>

          @if (open()) {
            <ul class="cv__list">
              @for (e of assessed(); track e.label) {
                <li class="cv__row" [class]="'cv__row--' + cls(e.verdict)">
                  <span class="cv__dot" aria-hidden="true"></span>
                  <span class="cv__lb">{{ e.label }}</span>
                  <span class="cv__target metric">{{ range(e) }}</span>
                  <span class="cv__actual metric">{{ pace(e.actualPaceSecPerKm) }}</span>
                  <!-- La FC du bloc, à côté de son allure : c'est le couple qui dit ce que le
                       bloc a coûté. Absente quand la montre n'a rien enregistré — un tour sans
                       ceinture n'est pas un tour à zéro pulsation. -->
                  <span class="cv__hr metric">
                    @if (e.actualAvgHrBpm !== null) {
                      <app-icon name="heart-pulse" [size]="11" />{{ e.actualAvgHrBpm }}
                    }
                  </span>
                  <span class="cv__delta metric">{{ delta(e) }}</span>
                </li>
              }
            </ul>

            <!-- La dérive ferme la liste, comme une ligne de total : elle ne se lit qu'après
                 avoir vu les blocs qu'elle compare. -->
            @if (data()?.drift; as d) {
              <p class="cv__drift">
                <app-icon name="trending-up" [size]="13" />
                <span>
                  <strong>Dérive cardiaque</strong> — {{ driftSentence(d) }}
                </span>
              </p>
            }
          }
        </div>
      }
    }
  `,
  styles: [`
    .cv {
      border: 1px solid var(--hairline); border-radius: var(--radius);
      border-left: 4px solid var(--tc, var(--ink-4)); background: var(--paper);
      overflow: hidden;
    }
    .cv--good  { --tc: var(--success); }
    .cv--mixed { --tc: var(--warning); }
    .cv--poor  { --tc: var(--danger); }

    .cv__hd {
      display: flex; align-items: center; gap: var(--sp-3); width: 100%;
      padding: var(--sp-3) var(--sp-4); min-height: 44px;
      background: transparent; border: none; cursor: pointer; text-align: left; color: var(--ink);
    }
    .cv__score {
      font-family: var(--font-display); font-weight: 800; font-size: var(--text-2xl);
      color: var(--tc); line-height: 1; flex-shrink: 0; font-variant-numeric: tabular-nums;
    }
    .cv__score small { font-size: var(--text-sm); font-weight: 700; }
    .cv__text { display: flex; flex-direction: column; gap: 2px; flex: 1; min-width: 0; }
    .cv__detail { font-size: var(--text-sm); color: var(--ink-2); }

    .cv__list { list-style: none; margin: 0; padding: 0 var(--sp-4) var(--sp-3); }
    .cv__row {
      display: grid; grid-template-columns: 10px 1fr auto auto auto;
      align-items: center; gap: var(--sp-2);
      padding: var(--sp-2) 0; border-top: 1px solid var(--hairline);
      font-size: var(--text-sm);
    }
    .cv__dot { width: 8px; height: 8px; border-radius: 50%; background: var(--rc, var(--ink-4)); }
    .cv__row--ok   { --rc: var(--success); }
    .cv__row--fast { --rc: var(--info, var(--primary)); }
    .cv__row--slow { --rc: var(--warning); }
    .cv__lb { color: var(--ink-2); min-width: 0; overflow: hidden; text-overflow: ellipsis; }
    .cv__target { color: var(--ink-4); font-variant-numeric: tabular-nums; }
    .cv__actual { color: var(--ink); font-weight: 600; font-variant-numeric: tabular-nums; }
    .cv__delta { color: var(--rc, var(--ink-3)); font-weight: 600; font-variant-numeric: tabular-nums; }
    .cv__hr {
      color: var(--ink-3); font-variant-numeric: tabular-nums;
      display: inline-flex; align-items: center; gap: 2px;
    }

    .cv__drift {
      display: flex; align-items: flex-start; gap: var(--sp-2);
      margin: 0; padding: var(--sp-3) var(--sp-4);
      border-top: 1px solid var(--hairline); background: var(--paper-sunk);
      font-size: var(--text-sm); color: var(--ink-2);
    }
    .cv__drift strong { color: var(--ink); }

    @media (max-width: 480px) {
      /* La fourchette prescrite saute avant la FC : elle se retrouve dans la ligne de verdict,
         alors que la FC du bloc ne se lit nulle part ailleurs. */
      .cv__row { grid-template-columns: 10px 1fr auto auto; }
      .cv__target { display: none; }
    }
  `],
})
export class ComplianceVerdictComponent {
  readonly data = input<Compliance | null>(null);
  readonly open = signal(false);

  /** Les efforts qu'on a su juger : afficher les autres serait remplir la liste de tirets. */
  readonly assessed = computed(
    () => (this.data()?.efforts ?? []).filter((e) => e.verdict !== 'NOT_ASSESSED'));

  readonly tone = computed(() => {
    const score = this.data()?.scorePct ?? 0;
    return score >= 85 ? 'good' : score >= 60 ? 'mixed' : 'poor';
  });

  cls(v: EffortVerdict): string {
    return VERDICT_CLASS[v];
  }

  range(e: ComplianceEffort): string {
    if (e.targetFastSecPerKm === null || e.targetSlowSecPerKm === null) return '';
    return `${formatPace(e.targetFastSecPerKm) ?? '—'}–${formatPace(e.targetSlowSecPerKm) ?? '—'}`;
  }

  pace(secPerKm: number | null): string {
    return formatPace(secPerKm) ?? '—';
  }

  /**
   * La dérive, en une phrase.
   *
   * <p>Trois nombres seraient trois nombres ; la phrase les relie et dit lequel compte. Les
   * battements donnent l'amplitude, le pourcentage la rend comparable d'un athlète à l'autre —
   * dix pulsations ne pèsent pas pareil à 140 qu'à 180 — et l'allure tranche entre une dérive
   * <b>subie</b> (même allure, cœur plus haut) et une dérive <b>compensée</b> (cœur tenu, allure
   * lâchée). C'est cette distinction qui change la lecture de la séance, pas les chiffres.</p>
   */
  driftSentence(d: ComplianceDrift): string {
    const sign = d.hrDeltaBpm > 0 ? '+' : d.hrDeltaBpm < 0 ? '−' : '';
    const hr = `${sign}${Math.abs(d.hrDeltaBpm)} bpm (${sign}${Math.abs(d.hrDeltaPct).toFixed(1).replace('.', ',')} %)`;
    const between = `de « ${d.firstLabel} » à « ${d.lastLabel} »`;

    const pace = d.paceDeltaSecPerKm;
    if (pace === null) {
      return `${hr} ${between}.`;
    }
    // Le cas qui mérite d'être nommé : l'allure n'a pas bougé, le cœur a monté. C'est la dérive
    // subie, celle qui dit ce que la séance a réellement coûté.
    if (Math.abs(pace) <= PACE_STABLE_S_PER_KM) {
      return `${hr} ${between}, à allure tenue.`;
    }
    const paceSign = pace > 0 ? '+' : '−';
    const paceTxt = `${paceSign}${Math.abs(pace)} s/km`;
    return pace > 0
      ? `${hr} ${between}, avec ${paceTxt} d'allure : la dérive est en partie compensée.`
      : `${hr} ${between}, et ${paceTxt} d'allure — la fin a été plus rapide.`;
  }

  /** « +8 s » ou « −15 s » : l'écart signé, dans le sens où un coureur le lit. */
  delta(e: ComplianceEffort): string {
    if (e.deltaSecPerKm === null || e.deltaSecPerKm === 0) return '';
    const sign = e.deltaSecPerKm > 0 ? '+' : '−';
    return `${sign}${Math.abs(e.deltaSecPerKm)} s`;
  }
}
