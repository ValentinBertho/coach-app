import { Injectable, signal } from '@angular/core';

/** Un cran du fil d'Ariane. Sans `link`, le cran est le lieu courant (non cliquable). */
export interface Crumb {
  label: string;
  link?: unknown[];
}

/**
 * Fil d'Ariane de la barre supérieure. Le menu latéral ne sait dire que la rubrique ; dès qu'on
 * entre dans un contexte (un athlète, un groupe), il faut un repère qui dise « où suis-je » et
 * offre la sortie. Les écrans qui ouvrent un contexte publient leur trail ici ; la coquille le
 * remet à zéro en se détruisant, pour qu'un trail ne survive pas à l'écran qui l'a posé.
 */
@Injectable({ providedIn: 'root' })
export class BreadcrumbService {
  readonly trail = signal<Crumb[]>([]);

  set(trail: Crumb[]): void {
    this.trail.set(trail);
  }

  clear(): void {
    this.trail.set([]);
  }
}
