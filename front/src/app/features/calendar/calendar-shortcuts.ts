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
      { keys: ['mod', 'D'], label: 'Dupliquer sur place' },
      { keys: ['Suppr'], label: 'Supprimer la sélection' },
      { keys: ['Alt', 'glisser'], label: 'Copier au lieu de déplacer' },
      { keys: ['N'], label: 'Planifier sur le jour survolé' },
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
