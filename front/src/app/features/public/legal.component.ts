import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs';
import { LogoComponent } from '../../shared/components/logo/logo.component';

/**
 * Pages publiques hors application : confidentialité, mentions légales, CGU (bêta) et support.
 * La route `legal/:page` sert les trois premières ; la page support est servie en plus sous
 * `/support`, une URL qu'on donne à des tiers (partenaires API, annuaires) et qui n'a rien à
 * faire sous `/legal`.
 * Les coordonnées de l'éditeur sont centralisées dans LEGAL_OWNER (à compléter avant la bêta).
 */
export const LEGAL_OWNER = {
  /** Nom sous lequel le Service est édité (éditeur non professionnel, LCEN art. 6, III-2). */
  name: 'DARI Lab',
  /** Email de contact (RGPD + support). */
  email: 'contact@darilab.app',

  /**
   * Identité civile et adresse du **responsable de traitement** (RGPD art. 13-1-a).
   *
   * ⚠️ **À COMPLÉTER AVANT D'OUVRIR LA BÊTA.** Ces deux champs sont vides et les blocs
   * correspondants ne s'affichent pas tant qu'ils le restent.
   *
   * L'exemption de l'article 6, III-2 de la LCEN — invoquée plus bas, à raison — dispense
   * l'éditeur **non professionnel** de publier son identité dans les *mentions légales*, à
   * condition de l'avoir communiquée à son hébergeur. Elle ne dispense de rien au titre du
   * RGPD : l'article 13 impose de fournir « l'identité et les coordonnées du responsable du
   * traitement » aux personnes concernées, et le Service traite des données de santé
   * (article 9). Un nom commercial et une adresse e-mail n'y suffisent pas.
   *
   * Renseigner : `legalName` = prénom et nom (ou raison sociale + forme juridique et SIREN si
   * une structure est créée) ; `address` = adresse postale complète.
   */
  legalName: '',
  address: '',

  /** Date de dernière mise à jour des présentes pages. */
  updated: 'août 2026',
};

type LegalPage = 'confidentialite' | 'mentions-legales' | 'cgu' | 'support';

@Component({
  selector: 'app-legal',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, LogoComponent],
  template: `
    <main class="legal-page">
      <header class="legal-head">
        <a routerLink="/" class="legal-logo"><app-logo [size]="36" [showText]="true" /></a>
        <nav class="legal-nav">
          <a routerLink="/support" [class.active]="page() === 'support'">Support</a>
          <a routerLink="/legal/confidentialite" [class.active]="page() === 'confidentialite'">Confidentialité</a>
          <a routerLink="/legal/mentions-legales" [class.active]="page() === 'mentions-legales'">Mentions légales</a>
          <a routerLink="/legal/cgu" [class.active]="page() === 'cgu'">CGU</a>
        </nav>
      </header>

      <article class="card legal-body">
        @switch (page()) {
          @case ('support') {
            <h1>Support</h1>
            <p class="muted">Dernière mise à jour : {{ owner.updated }}</p>

            <h2>Nous contacter</h2>
            <p>
              Une question, un problème, une demande sur vos données : écrivez à
              <a href="mailto:{{ owner.email }}">{{ owner.email }}</a>. Nous répondons sous
              <strong>3 jours ouvrés</strong>. Vous n'avez pas besoin d'un compte pour nous écrire.
            </p>
            <p>
              Pour aller plus vite, indiquez : l'adresse e-mail de votre compte, l'écran concerné,
              ce que vous attendiez et ce qui s'est passé, et — s'il y en a une — la référence
              d'erreur affichée à l'écran.
            </p>

            <h2>Montres et applications connectées</h2>
            <p>
              {{ owner.name }} importe vos activités depuis votre montre pour que votre coach compare la
              séance prescrite à la séance réalisée. <strong>C'est vous qui connectez votre
              compte</strong>, depuis <em>Synchronisation</em> dans votre espace athlète : un coach
              ne peut jamais le faire à votre place.
            </p>
            <ul>
              <li>
                <strong>Strava</strong> — disponible. La connexion se fait par autorisation
                Strava (OAuth) ; vos activités sont ensuite importées automatiquement.
              </li>
              <li>
                <strong>Garmin et COROS</strong> — intégrations en préparation. En attendant, vous
                pouvez importer un fichier <strong>GPX ou TCX</strong> exporté depuis votre compte,
                ou saisir une sortie à la main, depuis <em>Mes activités</em>.
              </li>
            </ul>
            <p>
              <strong>Se déconnecter à tout moment.</strong> Sur la même page
              <em>Synchronisation</em>, le bouton <em>Déconnecter</em> coupe la liaison et supprime
              immédiatement les jetons d'accès conservés. Les activités déjà importées restent dans
              votre historique ; vous pouvez les supprimer une par une depuis
              <em>Mes activités</em>.
            </p>
            <p>
              <strong>Une activité n'est pas remontée ?</strong> Vérifiez d'abord qu'elle est bien
              synchronisée dans l'application de votre montre — nous ne voyons que ce qui s'y
              trouve. L'import automatique tourne toutes les heures. Si l'activité manque toujours
              au-delà, écrivez-nous en précisant la date, l'heure de départ et la montre utilisée.
            </p>
            <p class="muted">
              <em>Device integrations — support in English is available at
              <a href="mailto:{{ owner.email }}">{{ owner.email }}</a>.</em>
            </p>

            <h2>Questions fréquentes</h2>
            <p>
              <strong>Je n'arrive pas à me connecter.</strong> Utilisez « Mot de passe oublié » sur
              l'écran de connexion : un lien de réinitialisation vous est envoyé par e-mail.
              Pensez à regarder dans vos indésirables.<br />
              <strong>Comment obtenir un compte athlète ?</strong> Deux chemins : votre coach vous
              <strong>invite</strong> (demandez-lui de renvoyer l'invitation si elle s'est perdue),
              ou vous <strong>créez votre compte vous-même</strong> et cherchez un coach dans
              l'annuaire. L'inscription directe est réservée aux 16 ans et plus.<br />
              <strong>Comment récupérer ou supprimer mes données ?</strong> Depuis votre profil
              dans l'application : <em>exporter mes données</em> et <em>supprimer mon compte</em>.
              Pour toute autre demande, écrivez-nous.<br />
              <strong>Je ne reçois pas les notifications.</strong> Vérifiez qu'elles sont autorisées
              dans les réglages de votre navigateur ou de votre téléphone, puis réactivez-les dans
              <em>Réglages → Notifications</em>.
            </p>

            <h2>Signaler un problème depuis l'application</h2>
            <p>
              Une fois connecté·e, le bouton <strong>« Signaler un problème »</strong> transmet
              votre message avec le contexte technique nécessaire (écran, version, navigateur,
              référence d'erreur). C'est la voie la plus rapide pour un bug, parce qu'elle nous
              évite un aller-retour pour reconstituer le contexte.
            </p>

            <h2>Confidentialité et données personnelles</h2>
            <p>
              Le détail des données collectées, de leur durée de conservation et de vos droits est
              dans notre <a routerLink="/legal/confidentialite">politique de confidentialité</a>.
              Pour exercer un droit ou poser une question relative à la protection des données :
              <a href="mailto:{{ owner.email }}">{{ owner.email }}</a>.
            </p>
          }
          @case ('mentions-legales') {
            <h1>Mentions légales</h1>
            <p class="muted">Dernière mise à jour : {{ owner.updated }}</p>

            <h2>Éditeur</h2>
            <p>
              Le site et l'application DARI Lab (ci-après « le Service ») sont édités à titre
              non professionnel sous le nom <strong>{{ owner.name }}</strong>
              (article 6, III-2 de la loi n° 2004-575 du 21 juin 2004 — LCEN : l'identité de
              l'éditeur non professionnel est tenue à la disposition des hébergeurs ci-dessous).<br />
              @if (owner.legalName) { Éditeur : {{ owner.legalName }}.<br /> }
              @if (owner.address) { {{ owner.address }}.<br /> }
              Contact : <a href="mailto:{{ owner.email }}">{{ owner.email }}</a>.
            </p>

            <h2>Hébergement</h2>
            <p>
              Application et base de données hébergées par <strong>Railway Corp.</strong>
              (548 Market St, San Francisco, CA, États-Unis — railway.com).<br />
              Interface web servie par <strong>Vercel Inc.</strong>
              (440 N Barranca Ave, Covina, CA, États-Unis — vercel.com).
            </p>

            <h2>Propriété intellectuelle</h2>
            <p>
              L'ensemble du Service (structure, textes, marques, logos, code) est protégé par le
              droit de la propriété intellectuelle. Toute reproduction non autorisée est interdite.
            </p>
          }
          @case ('cgu') {
            <h1>Conditions générales d'utilisation — version bêta</h1>
            <p class="muted">Dernière mise à jour : {{ owner.updated }}</p>

            <h2>1. Objet</h2>
            <p>
              DARI Lab est une plateforme de coaching sportif (course à pied et préparation
              physique) mettant en relation des coachs et leurs athlètes. Les présentes CGU
              régissent l'utilisation du Service, édité par {{ owner.name }}.
            </p>
            <p class="muted">
              <em>Ces conditions sont en cours de relecture juridique : elles décrivent fidèlement
              le fonctionnement du Service, mais leur rédaction n'est pas définitive.</em>
            </p>

            <h2>2. Version bêta</h2>
            <p>
              Le Service est fourni en <strong>version bêta</strong>, à des fins de test et
              d'amélioration. En conséquence : la disponibilité du Service n'est pas garantie ;
              des fonctionnalités peuvent évoluer, être suspendues ou supprimées ; malgré des
              sauvegardes régulières, la conservation des données ne fait l'objet d'aucune
              garantie contractuelle. Le Service est fourni « en l'état », sans garantie d'aucune
              sorte.
            </p>

            <h2>3. Comptes</h2>
            <p>
              L'inscription requiert une adresse email valide. Vous êtes responsable de la
              confidentialité de vos identifiants et des actions réalisées depuis votre compte.
              Un compte athlète s'obtient soit sur invitation d'un coach, soit par inscription
              directe — celle-ci étant réservée aux personnes de <strong>16 ans et plus</strong>.
            </p>

            <h2>4. Rôle et responsabilité du coach</h2>
            <p>
              Le coach saisit et consulte des données relatives à ses athlètes dans le cadre de la
              relation d'accompagnement sportif que ceux-ci ont acceptée. Le coach s'engage à ne
              saisir que des données pertinentes pour l'entraînement et à respecter la
              confidentialité des données de ses athlètes.
            </p>

            <h2>4 bis. Mise en relation et annuaire</h2>
            <p>
              Le Service publie un <strong>annuaire de coachs</strong> et permet à un athlète de
              leur adresser une demande d'accompagnement. Ce que cela signifie, et ce que cela ne
              signifie pas :
            </p>
            <ul>
              <li>
                <strong>La relation d'accompagnement se noue entre l'athlète et le coach</strong>,
                qui exerce en professionnel indépendant. L'éditeur n'est pas partie à cette
                relation : il n'en fixe ni le contenu, ni le prix, ni la durée.
              </li>
              <li>
                <strong>Aucun paiement ne transite par le Service.</strong> Les tarifs affichés sont
                déclarés par le coach à titre indicatif ; leur règlement se fait directement entre
                l'athlète et lui, hors du Service.
              </li>
              <li>
                <strong>Les diplômes et certifications affichés sont déclarés par le coach.</strong>
                L'éditeur ne les vérifie pas auprès des organismes qui les délivrent et ne s'en
                porte pas garant.
              </li>
              <li>
                <strong>Le coach reste libre d'accepter ou de refuser</strong> toute demande, sans
                avoir à se justifier.
              </li>
              <li>
                <strong>Chacune des deux parties peut mettre fin à l'accompagnement</strong> à tout
                moment, sans préavis. L'athlète cesse alors d'être suivi et peut solliciter un autre
                coach ; le coach conserve, en lecture seule, le dossier d'entraînement qu'il a tenu.
              </li>
            </ul>

            <h2>5. Avertissement santé</h2>
            <p>
              DARI Lab est un outil d'aide à l'entraînement. Il ne fournit
              <strong>aucun avis médical</strong> : les zones, charges et alertes calculées ne se
              substituent ni à un diagnostic ni à un suivi médical. Consultez un professionnel de
              santé avant toute pratique sportive intensive.
            </p>

            <h2>6. Responsabilité</h2>
            <p>
              Dans les limites permises par la loi, la responsabilité de l'éditeur ne saurait être
              engagée pour les dommages indirects résultant de l'utilisation du Service, d'une
              indisponibilité, ou d'une perte de données en période de bêta.
            </p>

            <h2>7. Résiliation</h2>
            <p>
              Vous pouvez cesser d'utiliser le Service et demander la suppression de votre compte
              à tout moment (cf. <a routerLink="/legal/confidentialite">politique de
              confidentialité</a>). L'éditeur peut suspendre un compte en cas de violation des
              présentes CGU.
            </p>

            <h2>8. Droit applicable</h2>
            <p>Les présentes CGU sont soumises au droit français.</p>
          }
          @default {
            <h1>Politique de confidentialité</h1>
            <p class="muted">Dernière mise à jour : {{ owner.updated }}</p>

            <h2>1. Responsable de traitement</h2>
            <p>
              {{ owner.name }}@if (owner.legalName) { ({{ owner.legalName }}) } —
              @if (owner.address) { {{ owner.address }} — }
              contact : <a href="mailto:{{ owner.email }}">{{ owner.email }}</a>.
            </p>

            <h2>2. Données collectées</h2>
            <p>
              <strong>Données de compte</strong> : nom, adresse email, mot de passe (haché),
              club de rattachement.<br />
              <strong>Données d'entraînement</strong> : séances prescrites et réalisées,
              activités (durée, distance, allure, fréquence cardiaque), objectifs, performances de
              référence, retours de séance (RPE, commentaires).<br />
              <strong>Données de santé (article 9 RGPD)</strong> : mesures de lactate, niveaux de
              douleur et de fatigue, indisponibilités (blessure, maladie). Elles ne sont
              collectées qu'avec le <strong>consentement explicite de l'athlète</strong> —
              recueilli à l'acceptation de son invitation, ou lors de son inscription directe — et
              sont <strong>chiffrées au repos (AES-256)</strong>.<br />
              <strong>Profil public d'un coach</strong> : si vous êtes coach et que vous publiez une
              fiche dans l'annuaire, les informations que vous y placez (nom, présentation,
              spécialités, ville, tarifs, diplômes déclarés, photo) sont
              <strong>publiquement accessibles</strong>, y compris hors connexion. Votre adresse
              e-mail et votre téléphone n'en font jamais partie. Les photos sont ré-encodées à
              l'envoi, ce qui supprime leurs métadonnées — dont la localisation de la prise de vue.<br />
              <strong>Demande de coaching</strong> : si vous sollicitez un coach, il reçoit votre
              prénom, votre nom, votre âge en années, votre discipline, votre niveau, votre ville,
              votre objectif et votre message. <strong>Il ne reçoit aucune coordonnée</strong> tant
              qu'il n'a pas accepté votre demande.<br />
              <strong>Données d'appareils connectés</strong> : si vous connectez volontairement
              votre compte Strava, les activités sportives associées sont importées ; les jetons
              d'accès sont chiffrés au repos.<br />
              <strong>Retours envoyés depuis l'application</strong> : lorsque vous utilisez
              « Signaler un problème », votre message est enregistré avec le contexte technique
              nécessaire à son traitement — écran concerné, version de l'application, navigateur
              et référence de l'erreur affichée. Ces retours sont conservés jusqu'à
              <strong>12 mois</strong> après leur traitement, puis supprimés.
            </p>

            <h2>3. Finalités et bases légales</h2>
            <p>
              Fournir le service de coaching (exécution du contrat) ; traiter les données de santé
              à des fins de suivi d'entraînement (consentement explicite, révocable) ; envoyer les
              emails fonctionnels — invitations, rappels de séance, alertes coach (exécution du
              contrat) ; assurer la sécurité et corriger les erreurs (intérêt légitime). Aucune
              donnée n'est vendue ni utilisée à des fins publicitaires.
            </p>

            <h2>4. Destinataires et sous-traitants</h2>
            <p>
              Vos données sont accessibles à votre coach (pour un athlète) dans la limite de ses
              permissions, et aux sous-traitants techniques listés ci-dessous.
            </p>
            <p>
              <strong>Le coach que vous choisissez.</strong> Lorsque vous sollicitez un coach depuis
              l'annuaire et qu'il accepte, il devient destinataire de vos données d'entraînement et
              — avec votre consentement — de vos données de santé. Vous pouvez mettre fin à cet
              accompagnement à tout moment : il cesse alors de pouvoir écrire dans votre suivi.
              <strong>Il conserve en lecture le dossier d'entraînement qu'il a tenu</strong>, dont
              il est l'auteur ; pour en demander l'effacement, écrivez-nous.
            </p>
            <p class="muted">
              <em>Ce point, ainsi que la durée de conservation de ce dossier après la fin de
              l'accompagnement, font partie des éléments en cours de relecture juridique.</em>
            </p>
            <ul>
              <li><strong>Railway</strong> — hébergement de l'application et de la base de
                données (toutes les données du Service) ; durée : vie du compte.</li>
              <li><strong>GitHub</strong> — stockage des <strong>sauvegardes quotidiennes
                chiffrées de la base</strong>, hors de l'infrastructure d'hébergement. Ces
                sauvegardes contiennent donc l'ensemble des données, y compris les données de
                santé, sous forme chiffrée (AES-256, clé détenue par l'éditeur) ; durée de
                rétention : <strong>14 jours</strong>, purge automatique ensuite.</li>
              <li><strong>Vercel</strong> — diffusion de l'interface web ; aucune donnée de santé
                n'y transite en dehors des appels API relayés vers Railway.</li>
              <li><strong>Resend</strong> — envoi des e-mails fonctionnels ; aucune donnée de
                santé n'est incluse dans les e-mails.</li>
              <li><strong>Sentry</strong> — suivi des erreurs techniques ; les données
                personnelles y sont désactivées (<code>send-default-pii: false</code>).</li>
              <li><strong>Strava</strong> — uniquement si vous connectez votre compte, pour
                importer vos activités.</li>
              <li><strong>Service de notification de votre navigateur</strong> (Google, Mozilla
                ou Apple selon le navigateur) — si vous activez les notifications push. Le
                contenu des notifications est chiffré de bout en bout ; ces services acheminent
                le message sans pouvoir le lire, et ne reçoivent aucune donnée de santé.</li>
            </ul>
            <p>
              Certains sous-traitants sont situés aux États-Unis ; les transferts sont encadrés
              par des clauses contractuelles types et/ou le Data Privacy Framework.
            </p>

            <h2>5. Durées de conservation</h2>
            <p>
              Les données sont conservées tant que votre compte est actif. Un compte resté
              <strong>inactif pendant 24 mois</strong> (aucune connexion) est supprimé après un
              e-mail de préavis. À la suppression du compte, les données sont effacées sans délai
              (suppression en cascade). Les sauvegardes techniques sont purgées selon leur cycle
              de rotation (14 jours), et les retours envoyés depuis l'application sont conservés
              12 mois après traitement.
            </p>
            <p>
              Le <strong>retrait de votre consentement</strong> au traitement des données de
              santé entraîne l'effacement immédiat des données concernées déjà collectées (tests
              de lactate, douleurs et fatigues déclarées, motifs médicaux d'indisponibilité,
              notes médicales) et l'arrêt de leur collecte. Votre compte et votre historique
              d'entraînement, eux, sont conservés : ce sont deux droits distincts.
            </p>

            <h2>6. Vos droits</h2>
            <p>
              Vous disposez des droits d'accès, de rectification, d'effacement, de portabilité,
              de limitation et d'opposition. Un athlète peut <strong>exporter ses données</strong>
              et <strong>supprimer son compte</strong> directement depuis son profil dans
              l'application. Pour toute autre demande (y compris la suppression d'un compte
              coach) : <a href="mailto:{{ owner.email }}">{{ owner.email }}</a>. Vous pouvez
              <strong>retirer votre consentement</strong> au traitement des données de santé à
              tout moment, directement depuis votre profil dans l'application (« Mes données de
              santé »), et introduire une réclamation auprès de la CNIL (cnil.fr).
            </p>

            <h2>7. Cookies et stockage local</h2>
            <p>
              DARI Lab n'utilise <strong>aucun cookie publicitaire ni traceur tiers</strong>, et
              aucune bannière de consentement n'est donc requise. En revanche, le fonctionnement
              hors ligne de l'application suppose de conserver certaines données
              <strong>sur votre appareil</strong> :
            </p>
            <ul>
              <li><strong>Jeton de session et profil</strong> (stockage local) — vous garder
                connecté·e.</li>
              <li><strong>Préférences d'affichage</strong> (unité d'allure, panneaux repliés).</li>
              <li><strong>File de retours hors ligne</strong> — un retour de séance saisi sans
                réseau (effort perçu, fatigue, douleur) est conservé sur l'appareil jusqu'à son
                envoi : ce sont des <strong>données de santé</strong>.</li>
              <li><strong>Cache de l'application installée (PWA)</strong> — la séance du jour et
                sa prescription sont mises en cache une heure pour rester consultables sans
                réseau.</li>
            </ul>
            <p>
              Ces éléments sont <strong>effacés à la déconnexion</strong> (file de retours et
              cache compris). Sur un appareil partagé, déconnectez-vous pour qu'il ne reste rien.
            </p>

            <h2>8. Sécurité</h2>
            <p>
              Connexions chiffrées (HTTPS), données de santé et jetons d'accès chiffrés au repos
              (AES-256-GCM), cloisonnement strict des accès par club et par permission,
              journalisation des suppressions.
            </p>
          }
        }
      </article>

      <footer class="legal-foot">
        <a routerLink="/">← Retour à l'accueil</a>
      </footer>
    </main>
  `,
  styles: `
    .legal-page { max-width: 760px; margin: 0 auto; padding: var(--sp-6) var(--sp-4) var(--sp-8); }
    .legal-head { display: flex; align-items: center; justify-content: space-between; gap: var(--sp-4); flex-wrap: wrap; margin-bottom: var(--sp-5); }
    .legal-logo { text-decoration: none; }
    .legal-nav { display: flex; gap: var(--sp-3); font-size: var(--text-sm); }
    .legal-nav a { color: var(--ink-2); text-decoration: none; padding-bottom: 2px; }
    .legal-nav a.active { color: var(--ink-1); border-bottom: 2px solid var(--brand); }
    .legal-body { padding: var(--sp-6); }
    .legal-body h1 { margin-bottom: var(--sp-1); }
    .legal-body h2 { margin-top: var(--sp-5); margin-bottom: var(--sp-2); font-size: var(--text-lg); }
    .legal-body p { line-height: 1.65; color: var(--ink-2); }
    .legal-body .muted { color: var(--ink-3); font-size: var(--text-sm); }
    .legal-foot { margin-top: var(--sp-5); font-size: var(--text-sm); }
  `,
})
export class LegalComponent {
  private readonly route = inject(ActivatedRoute);
  readonly owner = LEGAL_OWNER;

  /** Rubrique figée par la route (`/support`), là où `legal/:page` n'a pas de paramètre. */
  private readonly staticPage = this.route.snapshot.data['page'] as string | undefined;

  private readonly pageParam = toSignal(
    this.route.paramMap.pipe(map((p) => p.get('page') ?? this.staticPage ?? null)),
    { initialValue: this.route.snapshot.paramMap.get('page') ?? this.staticPage ?? null },
  );

  readonly page = computed<LegalPage>(() => {
    const p = this.pageParam();
    return p === 'mentions-legales' || p === 'cgu' || p === 'support' ? p : 'confidentialite';
  });
}
