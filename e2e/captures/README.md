# Captures d'écran de présentation

Un script, pas des tests : il parcourt l'application avec les comptes de démonstration et
photographie les écrans clés. On le relance après chaque évolution visible, et toutes les images
de la documentation et de la page d'accueil sont à jour d'un coup.

## Lancer

Sur une base **fraîche** en profil `dev` (le jeu de démo et sa couche « présentation », cf.
README racine, « Comptes de démonstration »), back et front démarrés :

```bash
cd e2e
npx playwright test -c playwright.captures.config.ts
```

## Ce qui sort

`docs/captures/ordinateur/` — 1440 × 900, en clair, résolution ×2 :

| Fichier | Écran |
|---|---|
| `01-accueil` · `02-annuaire` · `03-fiche-publique` | Ce que voit un visiteur, sans compte |
| `10-tableau-de-bord` · `11-ma-journee` | Le cockpit de la coach et sa boîte de tri |
| `12-athletes` · `13-fiche-athlete` · `14-programme-athlete` · `15-charge-athlete` · `16-zones-athlete` | Le suivi d'un athlète |
| `17-calendrier` · `19-bibliotheque` | La planification |
| `18-messages` · `20-demandes` · `21-ma-fiche-publique` | Échanger, recevoir des demandes, se présenter |

`docs/captures/telephone/` — Pixel 7, en sombre : la journée, le calendrier, les messages et les
progrès de l'athlète.

Un écran qui change de place ou de libellé fait échouer le script à l'étape concernée : c'est voulu,
une capture silencieusement fausse est pire qu'une capture manquante.
