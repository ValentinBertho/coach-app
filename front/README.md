# Front — DARI Lab (Angular 17, standalone)

Ce fichier ne double pas la documentation du dépôt. Tout est ailleurs :

- **Démarrer, variables d'environnement, architecture** : [`../README.md`](../README.md)
- **Design system** (tokens, composants, états) : [`../docs/Design.md`](../docs/Design.md)
- **Conventions de code** : [`../docs/Claude.md`](../docs/Claude.md)
- **Débogage** : [`../docs/DEBUG.md`](../docs/DEBUG.md)

## Les commandes, sans détour

| Commande | Ce qu'elle fait |
|---|---|
| `npm start` | Serveur de dev sur `http://localhost:4200`, proxy API via `proxy.conf.json` |
| `npm run start:pwa` | Build `pwa` servi en statique — **le seul moyen de tester le service worker**, l'installation et le push |
| `npm run build` | Build de production dans `dist/` |
| `npm test` | Tests unitaires, une passe, Chrome headless |

Il n'y a **pas** de tests de bout en bout (`ng e2e` n'est pas configuré) ; c'est un manque assumé,
tracé dans [`../docs/PLAN-CONFORMITE-BETA-2026-08.md`](../docs/PLAN-CONFORMITE-BETA-2026-08.md)
(V3-16).
