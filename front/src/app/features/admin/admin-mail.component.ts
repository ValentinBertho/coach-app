import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { MailDay, MailKindVolume, MailLogEntry, MailStats } from '../../core/models/admin.model';
import { AdminService } from '../../core/services/admin.service';
import { ToastService } from '../../core/services/toast.service';
import { IconComponent } from '../../shared/components/icon/icon.component';

/** Une barre de l'histogramme, déjà projetée en coordonnées du dessin. */
interface Bar {
  day: MailDay;
  x: number;
  width: number;
  sentY: number;
  sentH: number;
  failedY: number;
  failedH: number;
}

/**
 * Consommation d'e-mails (administration plateforme).
 *
 * <p><b>Pourquoi cet écran.</b> Le produit vit sous un plafond d'envoi, et ce plafond a déjà
 * dicté des choix de conception majeurs — le basculement des notifications de routine vers le
 * push. Il n'était pourtant mesuré nulle part : connaître sa consommation demandait d'ouvrir la
 * console du fournisseur, et un dépassement ne se découvrait que le jour où quelqu'un signalait
 * n'avoir jamais reçu son lien de réinitialisation. C'est-à-dire trop tard, et sur l'envoi qu'on
 * peut le moins se permettre de perdre.</p>
 *
 * <p><b>Ce qu'il montre, dans cet ordre.</b> Où l'on en est des deux plafonds (le jour, le mois),
 * ce qui a échoué récemment, la forme de la consommation sur trente jours, ce qui la compose, et
 * enfin le détail des derniers envois — le seul niveau qui réponde à « untel a-t-il reçu ? ».</p>
 */
@Component({
  selector: 'app-admin-mail',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, DecimalPipe, IconComponent],
  templateUrl: './admin-mail.component.html',
  styleUrl: './admin-mail.component.scss',
})
export class AdminMailComponent implements OnInit {
  private readonly admin = inject(AdminService);
  private readonly toast = inject(ToastService);

  readonly stats = signal<MailStats | null>(null);
  readonly log = signal<MailLogEntry[]>([]);
  readonly loading = signal(true);

  /** Jour survolé dans l'histogramme : son détail s'affiche au-dessus, jamais sur les barres. */
  readonly hovered = signal<MailDay | null>(null);

  ngOnInit(): void {
    this.admin.mailStats().subscribe({
      next: (s) => { this.stats.set(s); this.loading.set(false); },
      error: () => { this.loading.set(false); this.toast.error('Statistiques indisponibles.'); },
    });
    this.admin.mailLog().subscribe({
      next: (p) => this.log.set(p.content),
      error: () => this.log.set([]),
    });
  }

  // --- Jauges de plafond ------------------------------------------------------

  /** Part consommée d'un plafond, bornée à 100 % : au-delà, c'est la couleur qui alerte. */
  fill(used: number, quota: number): number {
    return quota <= 0 ? 0 : Math.min(100, Math.round((used / quota) * 100));
  }

  /**
   * Trois états, et un seuil qui a une raison : à 80 % d'un plafond quotidien, il reste de quoi
   * absorber une invitation de groupe ; au-delà, un simple envoi en masse fait tomber le reste
   * de la journée — y compris les mots de passe oubliés.
   */
  level(used: number, quota: number): 'ok' | 'warn' | 'over' {
    const pct = quota <= 0 ? 0 : (used / quota) * 100;
    if (pct >= 100) return 'over';
    return pct >= 80 ? 'warn' : 'ok';
  }

  // --- Histogramme ------------------------------------------------------------

  readonly chartWidth = 720;
  readonly chartHeight = 180;

  /** Journée la plus chargée de la fenêtre — le pic que l'échelle doit au minimum contenir. */
  private readonly peak = computed(() => {
    const s = this.stats();
    if (!s) return 1;
    return Math.max(1, ...s.byDay.map((d) => d.sent + d.failed));
  });

  /**
   * Le plafond est-il assez proche du pic pour être tracé ?
   *
   * <p>Un plafond dix fois supérieur au pic ne se trace pas : il faudrait écraser les barres d'un
   * facteur dix pour le faire entrer, et l'information « il reste de la marge » serait payée par
   * la perte de tout le reste. Au-delà d'une fois et demie le pic, la ligne disparaît — les
   * jauges du haut continuent de dire la marge en chiffres.</p>
   */
  readonly quotaVisible = computed(() => {
    const s = this.stats();
    return !!s && s.dailyQuota > 0 && s.dailyQuota <= this.peak() * 1.5;
  });

  /**
   * Échelle verticale : <b>les données</b>, étendues au plafond seulement quand on le dessine.
   *
   * <p>Caler l'échelle sur le plafond en toutes circonstances paraissait logique — on verrait
   * d'un coup d'œil la marge restante — mais avec sept envois par jour pour un plafond de cent,
   * toutes les barres s'écrasent sur l'axe et l'histogramme ne raconte plus rien. Ce graphique
   * répond à l'autre question : quelle est la <i>forme</i> de la consommation. Quand le plafond
   * est en vue, en revanche, l'échelle doit l'englober — sinon la ligne se dessine au-dessus du
   * cadre, hors du dessin, et va barrer le texte qui le surmonte.</p>
   */
  readonly scaleMax = computed(() => {
    const s = this.stats();
    const peak = this.peak();
    return this.quotaVisible() && s ? Math.max(peak, s.dailyQuota) : peak;
  });

  readonly bars = computed<Bar[]>(() => {
    const s = this.stats();
    if (!s || !s.byDay.length) return [];
    const max = this.scaleMax();
    const slot = this.chartWidth / s.byDay.length;
    // 2 px de fond entre deux barres : sans cet interstice, une série de jours pleins devient
    // un aplat continu où l'on ne distingue plus les jours.
    const width = Math.max(3, slot - 2);
    const toH = (v: number) => (v / max) * this.chartHeight;

    return s.byDay.map((day, i) => {
      const sentH = toH(day.sent);
      const failedH = toH(day.failed);
      // 2 px de fond entre les deux segments d'une même barre : deux aplats jointifs se lisent
      // comme un seul bloc bicolore, l'interstice en refait deux quantités distinctes.
      const gap = sentH > 0 && failedH > 0 ? 2 : 0;
      return {
        day,
        x: i * slot,
        width,
        // Les échecs se posent au-dessus des envois : ils s'ajoutent au volume tenté, ils ne
        // s'en retranchent pas.
        sentY: this.chartHeight - sentH,
        sentH,
        failedY: this.chartHeight - sentH - failedH - gap,
        failedH,
      };
    });
  });

  /** Ordonnée de la ligne de plafond, dans une échelle qui la contient toujours. */
  readonly quotaY = computed(() => {
    const s = this.stats();
    if (!s) return 0;
    return this.chartHeight - (s.dailyQuota / this.scaleMax()) * this.chartHeight;
  });

  /** Résumé lu par un lecteur d'écran : un graphique n'est jamais la seule voie d'accès. */
  readonly chartSummary = computed(() => {
    const s = this.stats();
    if (!s) return '';
    const total = s.byDay.reduce((n, d) => n + d.sent, 0);
    const failed = s.byDay.reduce((n, d) => n + d.failed, 0);
    return `${total} e-mails envoyés sur ${s.byDay.length} jours, ${failed} en échec.`;
  });

  /** Deux dates repères sous l'axe : le début de la fenêtre et aujourd'hui. */
  readonly firstDay = computed(() => this.stats()?.byDay[0]?.date ?? null);
  readonly lastDay = computed(() => this.stats()?.byDay.at(-1)?.date ?? null);

  // --- Répartition ------------------------------------------------------------

  readonly kindTotal = computed(() =>
    Math.max(1, (this.stats()?.byKind ?? []).reduce((n, k) => n + k.count, 0)));

  share(kind: MailKindVolume): number {
    return Math.round((kind.count / this.kindTotal()) * 100);
  }
}
