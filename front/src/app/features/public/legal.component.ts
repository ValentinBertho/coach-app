import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs';
import { LogoComponent } from '../../shared/components/logo/logo.component';

/**
 * Pages légales publiques : politique de confidentialité, mentions légales, CGU (bêta).
 * Une seule route `legal/:page` sert les trois rubriques.
 * Les coordonnées de l'éditeur sont centralisées dans LEGAL_OWNER (à compléter avant la bêta).
 */
export const LEGAL_OWNER = {
  /** Nom sous lequel le Service est édité (éditeur non professionnel, LCEN art. 6, III-2). */
  name: 'DARI Lab',
  /** Email de contact (RGPD + support). */
  email: 'contact@darilab.app',
  /** Date de dernière mise à jour des présentes pages. */
  updated: 'juillet 2026',
};

type LegalPage = 'confidentialite' | 'mentions-legales' | 'cgu';

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
          <a routerLink="/legal/confidentialite" [class.active]="page() === 'confidentialite'">Confidentialité</a>
          <a routerLink="/legal/mentions-legales" [class.active]="page() === 'mentions-legales'">Mentions légales</a>
          <a routerLink="/legal/cgu" [class.active]="page() === 'cgu'">CGU</a>
        </nav>
      </header>

      <article class="card legal-body">
        @switch (page()) {
          @case ('mentions-legales') {
            <h1>Mentions légales</h1>
            <p class="muted">Dernière mise à jour : {{ owner.updated }}</p>

            <h2>Éditeur</h2>
            <p>
              Le site et l'application DARI Lab (ci-après « le Service ») sont édités à titre
              non professionnel sous le nom <strong>{{ owner.name }}</strong>
              (article 6, III-2 de la loi n° 2004-575 du 21 juin 2004 — LCEN : l'identité de
              l'éditeur non professionnel est tenue à la disposition des hébergeurs ci-dessous).<br />
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
              Les comptes athlètes sont créés sur invitation d'un coach.
            </p>

            <h2>4. Rôle et responsabilité du coach</h2>
            <p>
              Le coach saisit et consulte des données relatives à ses athlètes dans le cadre de la
              relation d'accompagnement sportif que ceux-ci ont acceptée. Le coach s'engage à ne
              saisir que des données pertinentes pour l'entraînement et à respecter la
              confidentialité des données de ses athlètes.
            </p>

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
              {{ owner.name }} —
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
              collectées qu'avec le <strong>consentement explicite de l'athlète</strong>,
              recueilli à l'acceptation de son invitation, et sont
              <strong>chiffrées au repos (AES-256)</strong>.<br />
              <strong>Données d'appareils connectés</strong> : si vous connectez volontairement
              votre compte Strava, les activités sportives associées sont importées ; les jetons
              d'accès sont chiffrés au repos.
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
              permissions, et aux sous-traitants techniques suivants :
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
            </ul>
            <p>
              Certains sous-traitants sont situés aux États-Unis ; les transferts sont encadrés
              par des clauses contractuelles types et/ou le Data Privacy Framework.
            </p>

            <h2>5. Durées de conservation</h2>
            <p>
              Les données sont conservées tant que votre compte est actif. À la suppression du
              compte, elles sont effacées sans délai (suppression en cascade). Les sauvegardes
              techniques sont purgées selon leur cycle de rotation (14 jours).
            </p>

            <h2>6. Vos droits</h2>
            <p>
              Vous disposez des droits d'accès, de rectification, d'effacement, de portabilité,
              de limitation et d'opposition. Un athlète peut <strong>exporter ses données</strong>
              et <strong>supprimer son compte</strong> directement depuis son profil dans
              l'application. Pour toute autre demande (y compris la suppression d'un compte
              coach) : <a href="mailto:{{ owner.email }}">{{ owner.email }}</a>. Vous pouvez
              retirer votre consentement au traitement des données de santé à tout moment, et
              introduire une réclamation auprès de la CNIL (cnil.fr).
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

  private readonly pageParam = toSignal(
    this.route.paramMap.pipe(map((p) => p.get('page'))),
    { initialValue: this.route.snapshot.paramMap.get('page') },
  );

  readonly page = computed<LegalPage>(() => {
    const p = this.pageParam();
    return p === 'mentions-legales' || p === 'cgu' ? p : 'confidentialite';
  });
}
