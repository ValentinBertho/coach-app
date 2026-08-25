import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AthletePortalService } from '../../core/services/athlete-portal.service';
import { AthleteZoneMetric, AthleteZoneSheet } from '../../core/models/athlete-zone-sheet.model';
import { PhysioProfile } from '../../core/models/physio.model';
import { ZoneAnchor, ZONE_ANCHOR_LABELS } from '../../core/models/training-zone.model';
import { formatMetricRange } from '../../core/utils/metric-format';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { SkeletonComponent } from '../../shared/components/skeleton/skeleton.component';
import { DataOriginTagComponent } from '../../shared/components/physiology';

/**
 * Mes zones d'entraînement (athlète, lecture seule).
 *
 * <p>L'athlète lisait l'allure prescrite d'<b>une</b> séance et rien de plus : l'échelle dont
 * cette allure sort — à quelle vitesse et à quelle fréquence cardiaque chacune de ses zones se
 * court — n'existait que sur l'écran de son coach. C'est pourtant la donnée la plus quotidienne du
 * système : celle qu'on relit avant de partir, pas celle qu'on consulte une fois par saison.</p>
 *
 * <p>Une carte par zone plutôt que le tableau du coach. Le tableau croise zones × métriques et
 * suppose une largeur d'écran ; ici on lit une zone à la fois, sur un téléphone, et chaque
 * fourchette est accompagnée de la règle dont elle sort — « 88–92 % de mon seuil » — sans quoi un
 * chiffre d'allure ne se rattache à rien.</p>
 */
@Component({
  selector: 'app-athlete-zones',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, IconComponent, SkeletonComponent, DataOriginTagComponent],
  template: `
    <div class="az">
      <header class="az-top">
        <a routerLink="/athlete/progress" class="btn btn-ghost btn-sm">← Progrès</a>
        <h1 class="display-sm">Mes zones</h1>
        <p class="subtitle">
          Tes allures et fréquences cardiaques de travail, zone par zone. Ton coach les règle ;
          elles se recalculent à chaque test.
        </p>
      </header>

      <!-- Les ancres : les quelques valeurs dont toute l'échelle dérive. Les afficher ici évite
           l'aller-retour vers le profil pour comprendre d'où sortent les fourchettes. -->
      @if (anchors().length) {
        <section class="card refs">
          <div class="refs-hd">
            <span class="refs-lb">D'où viennent ces chiffres</span>
            <app-data-origin-tag origin="calcule" label="Calculé" />
          </div>
          <div class="refs-row">
            @for (r of anchors(); track r.label) {
              <span class="ref"><span class="ref-k">{{ r.label }}</span><span class="ref-v metric">{{ r.value }}</span></span>
            }
          </div>
        </section>
      }

      @if (loading()) {
        <app-skeleton shape="card" [rows]="3" />
      } @else if (zones().length === 0) {
        <div class="card empty">
          <h2>Aucune zone</h2>
          <p class="field-hint">
            Ton coach n'a pas encore défini d'échelle de zones. Elle apparaîtra ici dès qu'il l'aura
            réglée.
          </p>
        </div>
      } @else {
        @for (z of zones(); track z.zoneId) {
          <article class="card zone" [style.--zone-color]="z.color || 'var(--ink-4)'">
            <header class="zone-hd">
              <h2 class="zone-n">{{ z.name }}</h2>
              @if (z.description) { <p class="zone-d field-hint">{{ z.description }}</p> }
            </header>

            @if (valued(z).length) {
              <dl class="mlist">
                @for (m of valued(z); track m.metricTypeId) {
                  <div class="m">
                    <dt>
                      {{ m.name }}
                      @if (m.source === 'MANUAL') {
                        <span class="m-src" title="Valeur fixée par ton coach">
                          <app-icon name="pencil" [size]="12" /> réglée par ton coach
                        </span>
                      }
                    </dt>
                    <dd class="metric">{{ range(m) }}</dd>
                    @if (rule(m); as r) { <p class="m-rule field-hint">{{ r }}</p> }
                  </div>
                }
              </dl>
            } @else {
              <p class="field-hint zone-empty">
                Pas encore de valeur pour cette zone — il manque une mesure à ton profil.
              </p>
            }
          </article>
        }

        <p class="foot field-hint">
          Une valeur te semble fausse ? Elle vient de ton profil et de tes tests —
          <a routerLink="/athlete/performances">tes performances</a> et
          <a routerLink="/athlete/profile">ton profil</a> les portent. Parles-en à ton coach, lui
          seul règle l'échelle.
        </p>
      }
    </div>
  `,
  styles: [`
    /* padding-top : safe-area de la coquille athlète (PWA) — sinon le titre passe sous l'heure. */
    .az { max-width: 560px; margin-inline: auto; padding: var(--sp-4); padding-top: max(var(--sp-4), var(--safe-top, 0px)); display: flex; flex-direction: column; gap: var(--sp-3); }
    .az-top { display: flex; flex-direction: column; gap: var(--sp-1); align-items: flex-start; }
    .az-top h1 { margin: 0; }
    .subtitle { color: var(--ink-3); margin: 0; }
    .empty { text-align: center; }

    .refs { display: flex; flex-direction: column; gap: var(--sp-2); }
    .refs-hd { display: flex; align-items: center; justify-content: space-between; gap: var(--sp-2); }
    .refs-lb { font-weight: 700; color: var(--ink-2); font-size: var(--text-sm); }
    .refs-row { display: flex; flex-wrap: wrap; gap: var(--sp-2); }
    .ref { display: inline-flex; align-items: baseline; gap: 6px; padding: 4px 10px; border-radius: var(--radius-full); background: var(--paper-sunk); }
    .ref-k { font-size: var(--text-xs); color: var(--ink-3); }
    .ref-v { font-weight: 800; font-variant-numeric: tabular-nums; }

    /* Le liseré porte la couleur de la zone : c'est le seul repère qui relie cette carte à la
       bande de couleur vue sur la séance et sur le calendrier. */
    .zone { padding: 0; overflow: hidden; border-left: 4px solid var(--zone-color); }
    .zone-hd { padding: var(--sp-3) var(--sp-4) 0; }
    .zone-n { margin: 0; font-size: var(--text-lg); }
    .zone-d { margin: 2px 0 0; }
    .zone-empty { padding: var(--sp-2) var(--sp-4) var(--sp-3); margin: 0; }

    .mlist { margin: 0; padding: var(--sp-2) var(--sp-4) var(--sp-3); display: flex; flex-direction: column; gap: var(--sp-2); }
    .m { display: grid; grid-template-columns: 1fr auto; align-items: baseline; gap: var(--sp-2) var(--sp-3); padding-top: var(--sp-2); border-top: 1px solid var(--hairline); }
    .m:first-child { border-top: none; padding-top: 0; }
    .m dt { color: var(--ink-2); display: flex; flex-wrap: wrap; align-items: center; gap: 6px; }
    .m dd { margin: 0; font-weight: 800; font-size: var(--text-lg); font-variant-numeric: tabular-nums; text-align: right; white-space: nowrap; }
    .m-rule { grid-column: 1 / -1; margin: 0; }
    .m-src { display: inline-flex; align-items: center; gap: 3px; font-size: var(--text-2xs); color: var(--ink-3); }

    .foot { margin: 0; }
  `],
})
export class AthleteZonesComponent implements OnInit {
  private readonly portal = inject(AthletePortalService);

  readonly loading = signal(true);
  readonly zones = signal<AthleteZoneSheet[]>([]);
  readonly physio = signal<PhysioProfile | null>(null);

  /**
   * Les ancres réellement utilisées par l'échelle, dans l'ordre où elles se lisent. On n'affiche
   * que celles dont une zone se sert : lister toutes les valeurs du profil ferait un mur de
   * chiffres dont la plupart n'expliquent rien de ce qui est en dessous.
   */
  readonly anchors = computed<{ label: string; value: string }[]>(() => {
    const p = this.physio();
    if (!p) return [];
    const used = new Set<ZoneAnchor>();
    for (const z of this.zones()) {
      for (const m of z.metrics) {
        if (m.anchor) used.add(m.anchor);
        if (m.highAnchor) used.add(m.highAnchor);
      }
    }
    const out: { label: string; value: string }[] = [];
    const push = (a: ZoneAnchor, v: number | null | undefined, unit: string) => {
      if (used.has(a) && v != null) {
        out.push({ label: ZONE_ANCHOR_LABELS[a], value: `${this.oneDecimal(v)}${unit}` });
      }
    };
    push('LT1', p.lt1Kmh, ' km/h');
    push('LT2', p.lt2Kmh, ' km/h');
    push('VC', p.vcKmh, ' km/h');
    push('FCMAX', p.fcMax, ' bpm');
    push('LTHR', p.fcLt2, ' bpm');
    return out;
  });

  ngOnInit(): void {
    this.portal.zones().subscribe({
      next: (z) => { this.zones.set(z); this.loading.set(false); },
      error: () => { this.zones.set([]); this.loading.set(false); },
    });
    // Le profil n'est qu'un complément d'explication : son absence ne doit pas priver l'athlète
    // de son échelle, d'où deux appels indépendants plutôt qu'un forkJoin.
    this.portal.physio().subscribe({
      next: (p) => this.physio.set(p),
      error: () => this.physio.set(null),
    });
  }

  /**
   * Les métriques qui portent une valeur. Une zone déclarée sur une métrique jamais mesurée
   * (la puissance, par exemple) afficherait une ligne « — » qui n'apprend rien.
   */
  valued(z: AthleteZoneSheet): AthleteZoneMetric[] {
    return z.metrics.filter((m) => m.valueMin != null || m.valueMax != null);
  }

  range(m: AthleteZoneMetric): string {
    return formatMetricRange(m, m.valueMin, m.valueMax);
  }

  /**
   * La règle, en clair : « 88–92 % · Seuil lactique (LT2) ». Une valeur fixée à la main n'en a
   * plus — la dire quand même serait mentir sur son origine.
   */
  rule(m: AthleteZoneMetric): string | null {
    if (m.source === 'MANUAL') return null;
    if (m.anchor == null || m.lowPct == null || m.highPct == null) return null;
    const high = m.highAnchor ?? m.anchor;
    return high === m.anchor
      ? `${m.lowPct}–${m.highPct} % · ${ZONE_ANCHOR_LABELS[m.anchor]}`
      : `${m.lowPct} % ${this.shortAnchor(m.anchor)} → ${m.highPct} % ${this.shortAnchor(high)}`;
  }

  /** Libellé court d'une ancre : « LT1 » plutôt que « Seuil aérobie (LT1) ». */
  private shortAnchor(a: ZoneAnchor): string {
    const label = ZONE_ANCHOR_LABELS[a];
    const paren = /\(([^)]+)\)/.exec(label);
    return paren ? paren[1] : label;
  }

  private oneDecimal(v: number): string {
    return (Math.round(v * 10) / 10).toString().replace('.', ',');
  }
}
