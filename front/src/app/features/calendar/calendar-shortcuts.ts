import { ShortcutGroup } from '../../shared/components/shortcuts/shortcuts';

/**
 * Raccourcis du calendrier, source unique pour l'aide-mémoire `?` **et** pour les libellés
 * affichés dans les menus contextuels. Un accélérateur documenté à deux endroits finit
 * toujours par diverger, et c'est le menu (le seul que l'on voit) qui devient faux.
 */
export const CALENDAR_SHORTCUTS: readonly ShortcutGroup[] = [
  {
    title: 'Navigation',
    items: [
      { keys: ['←'], label: 'Période précédente' },
      { keys: ['→'], label: 'Période suivante' },
      { keys: ['T'], label: 'Revenir à aujourd’hui' },
      { keys: ['W'], label: 'Vue semaine' },
      { keys: ['M'], label: 'Vue mois' },
      { keys: ['B'], label: 'Afficher / masquer la bibliothèque' },
      { keys: ['?'], label: 'Cette liste' },
    ],
  },
  {
    title: 'Sélection',
    items: [
      { keys: ['clic'], label: 'Sélectionner une séance' },
      { keys: ['mod', 'clic'], label: 'Ajouter ou retirer de la sélection' },
      { keys: ['Maj', 'clic'], label: 'Étendre la sélection' },
      { keys: ['glisser'], label: 'Rectangle de sélection (sur les jours vides)' },
      { keys: ['mod', 'A'], label: 'Tout sélectionner' },
      { keys: ['Échap'], label: 'Vider la sélection' },
    ],
  },
  {
    title: 'Édition',
    items: [
      { keys: ['mod', 'C'], label: 'Copier la sélection' },
      { keys: ['mod', 'V'], label: 'Coller sur le jour survolé' },
      // Le clic droit sur un jour porte le même collage : le raccourci clavier était le seul
      // moyen de coller, donc la moitié du copier-coller ne se découvrait jamais.
      { keys: ['clic droit'], label: 'Menu d’un jour : coller, planifier, noter' },
      { keys: ['mod', 'D'], label: 'Dupliquer sur place' },
      { keys: ['Suppr'], label: 'Supprimer la sélection' },
      { keys: ['Alt', 'glisser'], label: 'Copier au lieu de déplacer' },
      { keys: ['N'], label: 'Planifier sur le jour survolé' },
    ],
  },
  {
    // Le copier-coller n'existait pas en vue groupe — c'est le manque remonté en bêta par un
    // coach de club. Il y a désormais les mêmes gestes, avec la case survolée (athlète × jour)
    // pour curseur, plus celui que seule cette vue peut offrir : donner la séance à tout le monde.
    title: 'Vue groupe',
    items: [
      { keys: ['mod', 'C'], label: 'Copier la journée de l’athlète survolé' },
      { keys: ['mod', 'V'], label: 'Coller sur la case survolée' },
      { keys: ['clic droit'], label: 'Menu d’une séance ou d’une case : copier, coller' },
      { keys: ['clic droit'], label: '« Tout le groupe » : coller pour chaque athlète' },
      { keys: ['Alt', 'glisser'], label: 'Copier au lieu de déplacer' },
    ],
  },
  {
    title: 'Annulation',
    items: [
      { keys: ['mod', 'Z'], label: 'Annuler la dernière action' },
      { keys: ['mod', 'Maj', 'Z'], label: 'Rétablir' },
    ],
  },
];
