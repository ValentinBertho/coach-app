import { bootstrapApplication } from '@angular/platform-browser';
import { AppComponent } from './app/app.component';
import { config } from './app/app.config.server';

/**
 * Point d'entrée du pré-rendu.
 *
 * <h2>Le bouchon de `localStorage`, et pourquoi il est ici</h2>
 *
 * <p>{@link AuthService} lit `localStorage` dans l'initialisation de ses champs — la toute
 * première ligne exécutée à sa création. Node n'a pas de `localStorage` : sans ce bouchon, le
 * pré-rendu s'arrête sur un `ReferenceError` avant d'avoir rendu quoi que ce soit.</p>
 *
 * <p>Deux façons de le régler. Gainer les trente accès de {@code AuthService} avec
 * `isPlatformBrowser` touche le code le plus sensible du produit — celui qui décide qui est
 * connecté — pour un besoin qui ne concerne que la compilation. Fournir un stockage vide coûte
 * dix lignes et laisse le chemin d'authentification du navigateur <b>strictement inchangé</b>.</p>
 *
 * <p>Un stockage vide n'est d'ailleurs pas un pis-aller : au pré-rendu, il n'y a pas
 * d'utilisateur. La page doit se rendre exactement comme pour un visiteur non connecté, et c'est
 * ce que ce bouchon produit. Ce qui y serait écrit pendant le rendu est jeté avec le processus,
 * et c'est très bien : rien de ce qui appartient à une session n'a sa place dans un fichier
 * statique servi à tout le monde.</p>
 */
if (typeof globalThis.localStorage === 'undefined') {
  const store = new Map<string, string>();
  const memoryStorage: Storage = {
    get length() {
      return store.size;
    },
    clear: () => store.clear(),
    getItem: (key) => store.get(key) ?? null,
    key: (index) => Array.from(store.keys())[index] ?? null,
    removeItem: (key) => void store.delete(key),
    setItem: (key, value) => void store.set(key, String(value)),
  };
  Object.defineProperty(globalThis, 'localStorage', { value: memoryStorage, configurable: true });
  Object.defineProperty(globalThis, 'sessionStorage', { value: memoryStorage, configurable: true });
}

const bootstrap = () => bootstrapApplication(AppComponent, config);

export default bootstrap;
