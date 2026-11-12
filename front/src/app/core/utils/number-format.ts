/**
 * Nombre écrit à la française, avec un nombre fixe de décimales : « 53,9 », « 1,38 », « 12,0 ».
 *
 * <p>`toFixed` écrit le séparateur anglais (« 0.0 km », « VDOT 53.98 »), et c'est ce que
 * l'audit relevait sur la fiche athlète et le calendrier. Les gabarits passent par le pipe
 * `number` (la locale de l'application est fr-FR) ; le code TypeScript qui fabrique un libellé
 * passe par ici.</p>
 */
export function frNumber(value: number, decimals = 1): string {
  return new Intl.NumberFormat('fr-FR', {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  }).format(value);
}
