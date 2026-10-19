# Importer un plan d'entraînement reçu en PDF

Un athlète dont le coach écrit ses plans hors de l'application (PDF, tableur) peut quand même
les retrouver dans son calendrier. Le PDF est converti en fichier JSON (par Claude, ou à la main),
puis importé depuis le calendrier coach.

## Le workflow

1. **Conversion.** Donner le PDF à Claude avec la consigne :
   > Convertis ce plan en fichier d'import pour l'appli (format `docs/IMPORT-PLAN.md`).
   > Mon objectif : _<course>_. Le plan commence la semaine du _<date>_.

   Préciser l'objectif quand le plan propose plusieurs variantes (marathon / semi…), et l'année
   ou le mois si le PDF ne les donne pas (« 13 au 19 » ne dit pas de quel mois il s'agit).
2. **Import.** Calendrier → choisir l'athlète → **Actions → Importer un plan…** → choisir le
   fichier → relire l'aperçu → **Importer**.
3. **Plan corrigé par le coach.** Reconvertir la nouvelle version et la réimporter :
   - une séance encore à faire, déjà importée (même date **et** même titre), est mise à jour ;
   - une séance déjà réalisée ou déclarée n'est jamais modifiée ;
   - une séance identique ne bouge pas : réimporter le même fichier ne fait rien ;
   - rien n'est supprimé : une séance retirée du plan reste au calendrier, à effacer à la main.

   D'où la règle de conversion : **le titre est le nom de la séance donné par le coach**
   (« Côtes », « SV2 », « SL Vallonné »), pas son contenu. Le contenu va dans `notes` et
   `steps` ; un titre qui changerait avec le contenu produirait un doublon au lieu d'une mise à jour.

L'athlète reçoit une seule notification pour tout l'import.

## Le format

```json
{
  "title": "CIMA La Baule 2026 — Valence",
  "workouts": [
    {
      "scheduledDate": "2026-07-14",
      "type": "INTERVALS",
      "title": "Côtes",
      "notes": "Côtes à 7–8 %. 30' Z1, gammes, 10×20'' placé, récup trot en descente, 10' Z1.",
      "targetDurationS": 3600,
      "steps": [
        { "stepType": "WARMUP", "repetitions": 1, "zone": "Z1", "durationS": 1800 },
        { "stepType": "REPETITION", "repetitions": 10, "durationS": 20, "notes": "90/95 % vit. max" },
        { "stepType": "COOLDOWN", "repetitions": 1, "zone": "Z1", "durationS": 600 }
      ]
    }
  ]
}
```

Chaque séance est une `WorkoutRequest` ordinaire (celle de `POST …/workouts`), envoyée en lot à
`POST /clubs/{clubId}/athletes/{athleteId}/workouts/import` (500 séances au plus).

| Champ | Valeurs |
|---|---|
| `scheduledDate` | `AAAA-MM-JJ` |
| `type` | `ENDURANCE`, `RECOVERY`, `TEMPO`, `THRESHOLD`, `INTERVALS`, `LONG_RUN`, `RACE`, `STRENGTH`, `CROSS_TRAINING`, `REST` |
| `title` | ≤ 255 caractères — le nom de la séance (voir la règle plus haut) |
| `notes` | ≤ 2048 caractères — la consigne du coach, recopiée fidèlement |
| `targetDistanceM` / `targetDurationS` | facultatifs ; seulement quand le plan les donne |
| `steps[].stepType` | `WARMUP`, `STEADY`, `REPETITION`, `RECOVERY`, `COOLDOWN` |
| `steps[].zone` | `Z1` à `Z5`, ou absent |
| `steps[].notes` | ≤ 512 caractères |

Les zones au-delà de Z5 (Z6, Z7 des plans de coachs extérieurs) n'existent pas dans
l'application : l'étape est laissée sans zone et la zone d'origine est écrite dans sa note. Une
allure exprimée pour un athlète-type (« Ex : AS42 à 4' ») est recopiée telle quelle, avec la
mention qu'elle est à adapter.
