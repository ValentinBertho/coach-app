import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { Load } from '../../../core/models/lactate.model';
import { IconComponent } from '../icon/icon.component';

/** Une notion expliquée : ce que c'est, comment ça se lit, et ce que dit *ton* chiffre. */
interface Entry {
  key: string;
  title: string;
  /** Ce que la valeur mesure, en une phrase, sans jargon. */
  what: string;
  /** Comment la lire : les repères chiffrés. */
  how: string;
  /** Lecture du chiffre de l'athlète, ou null tant qu'il n'existe pas. */
  yours: string | null;
  tone: 'neutral' | 'good' | 'watch' | 'alert';
}

/**
 * « Comprendre ces chiffres » — l'explication des indicateurs de charge, écrite pour l'athlète.
 *
 * <p><b>Pourquoi.</b> L'écran « Progrès » affiche ACWR, charge aiguë, charge chronique et
 * monotonie : quatre termes de littérature scientifique posés tels quels devant quelqu'un qui
 * vient voir s'il progresse. Un athlète qui lit « 1543 UA » et « monotonie 1.01 » ne sait ni ce
 * que ça vaut, ni ce qu'il devrait en faire — et les deux seuls réflexes possibles sont mauvais :
 * ignorer l'écran, ou s'inquiéter d'un chiffre qui n'a rien d'inquiétant.</p>
 *
 * <p><b>Le parti pris.</b> Chaque notion est dite trois fois : ce que c'est, comment ça se lit, et
 * ce que <em>ton</em> chiffre dit aujourd'hui. La troisième ligne est la seule qui compte
 * vraiment — c'est elle qui transforme une définition en information. Elle disparaît tant que le
 * chiffre n'existe pas, plutôt que d'inventer une lecture.</p>
 *
 * <p>Replié par défaut : l'athlète qui connaît déjà ces notions ne doit pas les relire chaque
 * semaine, et celui qui les découvre trouve le bouton là où la question se pose.</p>
 */
@Component({
  selector: 'app-load-explainer',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    <div class="lx card">
      <button type="button" class="lx-toggle" (click)="open.set(!open())" [attr.aria-expanded]="open()">
        <app-icon name="circle-help" [size]="16" />
        <span>Comprendre ces chiffres</span>
        <app-icon class="lx-caret" [name]="open() ? 'chevron-up' : 'chevron-down'" [size]="18" />
      </button>

      @if (open()) {
        <p class="lx-intro">
          Ces quatre repères servent à une seule chose : <strong>progresser sans te blesser</strong>.
          Ils ne jugent pas ta forme du jour — c'est ton ressenti qui la porte — mais la façon dont
          ta charge d'entraînement évolue d'une semaine à l'autre.
        </p>

        <dl class="lx-list">
          @for (e of entries(); track e.key) {
            <div class="lx-item" [class]="'lx-item--' + e.tone">
              <dt class="lx-t">{{ e.title }}</dt>
              <dd class="lx-d">
                <p class="lx-what">{{ e.what }}</p>
                <p class="lx-how">{{ e.how }}</p>
                @if (e.yours) { <p class="lx-yours">{{ e.yours }}</p> }
              </dd>
            </div>
          }
        </dl>

        <p class="lx-foot field-hint">
          « UA » veut dire <em>unités arbitraires</em> : la charge d'une séance, c'est son effort
          perçu multiplié par sa durée en minutes. Un chiffre qui n'a de sens que comparé à tes
          propres semaines — pas à celles de quelqu'un d'autre.
        </p>
      }
    </div>
  `,
  styles: [`
    .lx { padding: 0; overflow: hidden; }
    .lx-toggle {
      display: flex; align-items: center; gap: var(--sp-2); width: 100%;
      padding: var(--sp-3) var(--sp-4); min-height: 48px;
      background: transparent; border: none; cursor: pointer; text-align: left;
      color: var(--ink); font-weight: 700; font-size: var(--text-sm);
    }
    .lx-caret { margin-left: auto; color: var(--ink-4); }

    .lx-intro { margin: 0; padding: 0 var(--sp-4) var(--sp-3); color: var(--ink-2); font-size: var(--text-sm); }

    .lx-list { margin: 0; padding: 0 var(--sp-4) var(--sp-3); display: flex; flex-direction: column; gap: var(--sp-3); }
    .lx-item { border-left: 3px solid var(--hairline); padding-left: var(--sp-3); }
    .lx-item--good { border-left-color: var(--form-green); }
    .lx-item--watch { border-left-color: var(--form-orange); }
    .lx-item--alert { border-left-color: var(--form-red); }
    .lx-t { margin: 0 0 2px; font-weight: 800; color: var(--ink); font-size: var(--text-sm); }
    .lx-d { margin: 0; display: flex; flex-direction: column; gap: 2px; }
    .lx-what { margin: 0; color: var(--ink); font-size: var(--text-sm); }
    .lx-how { margin: 0; color: var(--ink-3); font-size: var(--text-sm); }
    /* La lecture de SON chiffre : c'est la ligne qui transforme une définition en information. */
    .lx-yours { margin: 2px 0 0; color: var(--ink); font-size: var(--text-sm); font-weight: 700; }

    .lx-foot { margin: 0; padding: 0 var(--sp-4) var(--sp-4); }
  `],
})
export class LoadExplainerComponent {
  readonly load = input.required<Load | null>();

  readonly open = signal(false);

  readonly entries = computed<Entry[]>(() => {
    const l = this.load();
    return [
      this.acwr(l),
      this.acute(l),
      this.chronic(l),
      this.monotony(l),
    ];
  });

  /** Le rapport qui dit si la semaine est en rupture avec le mois qui précède. */
  private acwr(l: Load | null): Entry {
    const ratio = l?.ratio ?? null;
    let yours: string | null = null;
    let tone: Entry['tone'] = 'neutral';

    if (ratio == null) {
      // Un ratio calculé sur trop peu d'historique dirait n'importe quoi : le produit le retient
      // volontairement, et l'athlète a le droit de savoir que ce n'est pas une panne.
      yours = l
        ? `Pas encore affiché : il faut environ un mois d'entraînement enregistré pour qu'il veuille dire quelque chose (tu en es à ${l.historyDays} jours sur ${l.chronicWindowDays}).`
        : null;
    } else if (ratio < 0.8) {
      yours = `Le tien est à ${fmt(ratio)} : tu t'entraînes moins que d'habitude. Après une coupure ou une maladie c'est normal ; sur la durée, c'est de la forme qui se perd.`;
      tone = 'watch';
    } else if (ratio <= 1.3) {
      yours = `Le tien est à ${fmt(ratio)} : tu es dans la zone où la charge progresse sans à-coup. C'est exactement là qu'on veut être.`;
      tone = 'good';
    } else if (ratio <= 1.5) {
      yours = `Le tien est à ${fmt(ratio)} : ta semaine est nettement plus lourde que le mois écoulé. Ça arrive dans un bloc de travail, mais ça ne se répète pas trois semaines de suite.`;
      tone = 'watch';
    } else {
      yours = `Le tien est à ${fmt(ratio)} : c'est une hausse brutale, la situation où les blessures arrivent. Parles-en à ton coach avant la prochaine séance dure.`;
      tone = 'alert';
    }

    return {
      key: 'acwr',
      title: 'ACWR — le rapport entre ta semaine et ton mois',
      what: 'Il compare ce que tu as fait ces 7 derniers jours à ta moyenne des 28 derniers. Autrement dit : est-ce que tu en fais soudainement beaucoup plus que d\'habitude ?',
      how: 'Entre 0,8 et 1,3, la progression est régulière. En dessous, tu es en sous-charge. Au-dessus de 1,5, c\'est le terrain sur lequel les blessures se produisent.',
      yours,
      tone,
    };
  }

  private acute(l: Load | null): Entry {
    return {
      key: 'acute',
      title: 'Charge aiguë (7 jours) — ta semaine',
      what: 'Le total de ce que t\'a coûté ton entraînement sur les 7 derniers jours : chaque séance compte pour son effort perçu multiplié par sa durée.',
      how: 'Ce chiffre seul ne se juge pas : il n\'a de sens que comparé à ta charge chronique — c\'est précisément ce que fait l\'ACWR juste au-dessus.',
      yours: l ? `Tu es à ${Math.round(l.acuteLoad7d)} UA sur ${l.sessions7d} séance${l.sessions7d > 1 ? 's' : ''} cette semaine.` : null,
      tone: 'neutral',
    };
  }

  private chronic(l: Load | null): Entry {
    return {
      key: 'chronic',
      title: 'Charge chronique (28 jours) — ton fond de forme',
      what: 'La même chose, mais lissée sur quatre semaines : c\'est le niveau d\'entraînement que ton corps a appris à encaisser.',
      how: 'Elle monte lentement, sur des mois. C\'est elle qu\'on cherche à faire progresser — et c\'est aussi elle qui protège : plus elle est haute, plus une grosse semaine passe bien.',
      yours: l ? `La tienne est à ${Math.round(l.chronicLoad28d)} UA par semaine en moyenne, sur ${l.sessions28d} séances.` : null,
      tone: 'neutral',
    };
  }

  /** L'indicateur qu'on oublie : tout faire pareil tous les jours use autant qu'en faire trop. */
  private monotony(l: Load | null): Entry {
    const m = l?.monotony ?? null;
    let yours: string | null = null;
    let tone: Entry['tone'] = 'neutral';

    if (m == null) {
      yours = null;
    } else if (m >= 2) {
      yours = `La tienne est à ${fmt(m)} : tes séances se ressemblent trop. Ajouter une vraie journée facile — ou un jour de repos — fait plus de bien qu'une séance de plus.`;
      tone = 'alert';
    } else if (m >= 1.5) {
      yours = `La tienne est à ${fmt(m)} : ça reste correct, mais tes semaines gagneraient à mieux alterner le dur et le facile.`;
      tone = 'watch';
    } else {
      yours = `La tienne est à ${fmt(m)} : tes semaines alternent bien les jours durs et les jours faciles. C'est ce qu'on veut.`;
      tone = 'good';
    }

    return {
      key: 'monotony',
      title: 'Monotonie — est-ce que tes semaines se ressemblent trop ?',
      what: 'Elle regarde si tes journées d\'entraînement se ressemblent toutes. Courir la même chose tous les jours fatigue plus qu\'alterner des jours durs et des jours faciles, à volume égal.',
      how: 'En dessous de 1,5, la semaine est bien contrastée. À partir de 2, la routine devient un facteur de fatigue en soi.',
      yours,
      tone,
    };
  }
}

/** Deux décimales, virgule française — le format des chiffres de l'écran. */
function fmt(value: number): string {
  return value.toFixed(2).replace('.', ',');
}
