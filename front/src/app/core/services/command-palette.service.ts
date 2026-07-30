import { Injectable, signal } from '@angular/core';

/**
 * État d'ouverture de la recherche globale (Cmd/Ctrl+K). Séparé du composant pour que
 * n'importe quel en-tête puisse l'ouvrir sans le connaître (même pattern que HelpService).
 */
@Injectable({ providedIn: 'root' })
export class CommandPaletteService {
  readonly open = signal(false);

  toggle(): void { this.open.update((v) => !v); }
  show(): void { this.open.set(true); }
  close(): void { this.open.set(false); }
}
