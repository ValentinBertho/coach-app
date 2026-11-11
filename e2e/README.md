# Tests de bout en bout

Playwright parcourt l'application complète (back + jeu de démonstration + front) comme un
utilisateur. Cinq parcours clés, et un contrôle d'accessibilité automatique (axe, WCAG 2.1 AA) des
écrans principaux.

| Fichier | Parcours |
| --- | --- |
| `1-connexion` | mauvais mot de passe refusé ; connexion du coach puis déconnexion |
| `2-coach-planifie` | le coach planifie une séance de la bibliothèque dans le calendrier |
| `athlete-3-debrief` | l'athlète (sur mobile) note sa séance, le coach reçoit le retour |
| `athlete-4-messages` | l'athlète écrit à son coach, qui lit le message |
| `5-admin-double-auth` | enrôlement de l'administrateur en double authentification, puis connexion par code de secours |
| `6-accessibilite` | 8 écrans contrôlés par axe ; le contraste des couleurs est relevé sans bloquer |

En CI, le job « Bout en bout (Playwright) » les lance à chaque push. En cas d'échec, l'artefact
`e2e-rapport` contient le rapport HTML, les captures, les traces et les journaux du back et du front.

## En local

Les tests modifient les données de démonstration et supposent une base **neuve**, en particulier
pour l'enrôlement de l'administrateur.

```bash
# Base neuve
dropdb coachrun && createdb coachrun

# Back : profil dev (jeu de démonstration), plafonds de requêtes coupés
cd back && mvn -q package -DskipTests
RATE_LIMIT_ENABLED=false java -jar target/coachrun-back.jar --spring.profiles.active=dev

# Front
cd front && npx ng serve --proxy-config proxy.conf.json

# Tests
cd e2e && npm ci && npx playwright install chromium
npx playwright test            # tout
npx playwright test --ui       # mode interactif
```
