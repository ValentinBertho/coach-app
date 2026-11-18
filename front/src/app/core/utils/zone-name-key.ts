/**
 * Clé de rapprochement des zones <b>entre modèles de zones</b> — miroir de
 * `ZoneNameKey.java` côté serveur : les deux tables doivent rester alignées.
 *
 * Une séance de bibliothèque est écrite avec les zones d'un modèle ; l'athlète lit ses cibles
 * dans le sien. Les homologues se retrouvent par leur nom, sauf la zone la plus basse, qui change
 * de nom d'un modèle à l'autre : « Footing facile » (seuils lactiques), « Récupération »
 * (vitesses critiques), « Régénération » (VMA).
 */
const ALIASES: Record<string, string> = {
  'footing facile': 'recuperation',
  footing: 'recuperation',
  recup: 'recuperation',
  regeneration: 'recuperation',
};

export function zoneNameKey(name: string | null | undefined): string {
  if (!name) return '';
  const flat = name
    .normalize('NFD')
    .replace(/\p{M}/gu, '')
    .toLowerCase()
    .trim()
    .replace(/\s+/g, ' ');
  return ALIASES[flat] ?? flat;
}
