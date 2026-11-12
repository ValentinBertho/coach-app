# Vidéo de présentation et images de la page d'accueil

Tout part des captures du jeu de démonstration (`docs/captures`, script `e2e/captures`). Après une
évolution visible de l'interface : refaire les captures, puis ces trois étapes.

## 1. La vidéo « en action » (1920 × 1080, ~1 min 37) — celle de la page d'accueil

L'application filmée pour de vrai par Playwright : curseur, clics, glisser-déposer, saisie, et
le téléphone de l'athlète. Il faut la pile de dev lancée (back en profil dev, front sur :4200) sur
une base **fraîche** : le tournage modifie les données (demande acceptée, message envoyé…).

```bash
cd front/branding/video/film
# 1. tourner les plans (tous, ou seulement ceux qu'on cite)
NODE_PATH=../../../../e2e/node_modules node shoot.mjs /tmp/film
NODE_PATH=../../../../e2e/node_modules node shoot.mjs /tmp/film coach-seance athlete-retour
# 2. monter : cartes d'ouverture et de fin, titres, cadre du téléphone, fondus
NODE_PATH=../../../../e2e/node_modules node montage.mjs /tmp/film /tmp/presentation-coachs.mp4
# avec une musique libre de droits :
NODE_PATH=../../../../e2e/node_modules node montage.mjs /tmp/film /tmp/presentation-coachs.mp4 --musique ../musique/darilab-theme.m4a
```

- `recorder.mjs` : les outils de tournage (curseur visible, clic, glisser, saisie, défilement,
  enregistrement image par image à 30 i/s).
- `shoot.mjs` : un plan par geste montré (`PLANS`), chacun dans un nouvel onglet connecté.
- `montage.mjs` : l'ordre des plans et leurs titres (`STORY`).
- `court.mjs` : la version courte et verticale (1080 × 1920, ~33 s) pour les réseaux sociaux,
  tirée des mêmes plans (écrans d'ordinateur recadrés sur la zone utile) :
  `NODE_PATH=… node court.mjs /tmp/film /tmp/presentation-courte.mp4 --musique ../musique/darilab-theme.m4a`

### La musique

`musique/darilab-theme.m4a` est composée par `musique/compose.py` (numpy seul) : tout est
synthétisé, sans échantillon ni boucle d'un tiers, donc libre de droits. Pour la refaire :

```bash
python3 musique/compose.py /tmp/theme.wav 98
ffmpeg -i /tmp/theme.wav -af "aecho=0.8:0.6:180|360:0.25|0.12,loudnorm=I=-18:TP=-1.5:LRA=11" \
  -c:a aac -b:a 160k musique/darilab-theme.m4a
```

Toute autre piste libre de droits se passe de la même façon avec `--musique`.

## 1 bis. L'ancienne vidéo animée à partir des captures (~73 s)

`presentation.html` décrit les scènes (texte, écran, zoom). Chaque image est calculée par
`render(t)` puis assemblée par ffmpeg : le rendu est exact et identique à chaque fois.

```bash
cd front
NODE_PATH=../e2e/node_modules node branding/video/render-video.mjs /tmp/presentation-coachs.mp4
# avec une musique libre de droits (fondu de sortie automatique) :
NODE_PATH=../e2e/node_modules node branding/video/render-video.mjs /tmp/presentation-coachs.mp4 --musique ../musique/darilab-theme.m4a
```

Pour vérifier une scène sans tout rendre : ouvrir `presentation.html` dans un navigateur et appeler
`render(12.5)` dans la console.

## 2. La version web, servie par la page d'accueil

Servie depuis `src/video/` (publié en `/video/`), **hors de `/assets`** : le service worker ne
met pas en cache ce dossier, et ne s'interpose donc pas dans la lecture d'une vidéo (requêtes
partielles). `vercel.json` exclut `mp4` de la réécriture vers `index.html`.

```bash
ffmpeg -i /tmp/presentation-coachs.mp4 -vf scale=1280:720 -c:v libx264 -preset slow -crf 27 \
  -pix_fmt yuv420p -c:a aac -b:a 96k -movflags +faststart src/video/presentation-coachs.mp4
ffmpeg -ss 2.2 -i /tmp/presentation-coachs.mp4 -frames:v 1 -vf scale=1280:720 /tmp/poster.png
# puis /tmp/poster.png → src/video/presentation-coachs-poster.webp (qualité ~80)
```

## 3. Les écrans de la page d'accueil

`src/assets/landing/*.webp` : les captures `ordinateur/` réduites à 1440 × 900 et les captures
`telephone/` à 420 px de large, en WebP qualité ~80 (voir la liste dans
`features/home/home.component.ts`).
