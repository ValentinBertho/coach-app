import { Provider } from '@angular/core';
import {
  ActivatedRouteSnapshot,
  BaseRouteReuseStrategy,
  RouteReuseStrategy,
} from '@angular/router';

/**
 * Recrée une route enfant quand un paramètre <b>hérité</b> de son parent change.
 *
 * <p><b>Le défaut.</b> La fiche d'un athlète est une coquille (`/app/athletes/:athleteId`) qui
 * persiste, et des sections montées dessous en routes enfants : Résumé, Programme, Charge, Zones,
 * Tests, Objectifs, Messages. Chacune reçoit `athleteId` par héritage de paramètres et charge ses
 * données à l'ouverture — dans `ngOnInit`, comme n'importe quel écran.</p>
 *
 * <p>Or le routeur réutilise un composant tant que sa configuration de route ne change pas. Passer
 * d'un athlète à l'autre par le sélecteur du bandeau ne change que le paramètre : la coquille
 * réagissait — elle observe `athleteId` — mais la section, elle, n'était jamais recréée et
 * `ngOnInit` ne repassait pas. Le coach voyait le nom changer en haut et le programme de
 * l'athlète précédent rester dessous. Silencieux, et faux d'une manière qui se remarque tard.</p>
 *
 * <p><b>La règle.</b> Une route qui <b>déclare</b> le paramètre dans son propre chemin le pilote
 * elle-même (la coquille sait recharger son athlète, et on veut qu'elle survive : bandeau,
 * onglets, position de défilement). Une route qui ne fait qu'en <b>hériter</b> n'a, elle, aucun
 * moyen de le voir bouger : c'est celle-là qu'on recrée. Une seule règle, valable pour les sept
 * onglets d'aujourd'hui comme pour ceux qu'on ajoutera.</p>
 *
 * <p>Le reste de l'application garde le comportement d'origine : un changement de paramètre sur
 * une route qui le déclare (`/app/templates/:id`, une séance, une activité) réutilise le
 * composant exactement comme avant.</p>
 */
export class AthleteContextReuseStrategy extends BaseRouteReuseStrategy {

  override shouldReuseRoute(future: ActivatedRouteSnapshot, curr: ActivatedRouteSnapshot): boolean {
    if (!super.shouldReuseRoute(future, curr)) {
      return false;
    }
    return !changedInheritedParam(future, curr);
  }
}

/** Un paramètre a-t-il changé sans que cette route le déclare dans son chemin ? */
function changedInheritedParam(future: ActivatedRouteSnapshot, curr: ActivatedRouteSnapshot): boolean {
  const path = future.routeConfig?.path ?? '';
  const declared = new Set(
    path.split('/').filter((s) => s.startsWith(':')).map((s) => s.slice(1)),
  );
  return Object.keys({ ...curr.params, ...future.params })
    .some((key) => !declared.has(key) && curr.params[key] !== future.params[key]);
}

/** À placer dans les providers de l'application, à côté de `provideRouter`. */
export function provideAthleteContextReuse(): Provider {
  return { provide: RouteReuseStrategy, useClass: AthleteContextReuseStrategy };
}
