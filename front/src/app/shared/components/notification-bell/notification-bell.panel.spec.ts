import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Component, importProvidersFrom, viewChild } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Bell, LucideAngularModule, Settings } from 'lucide-angular';
import { NotificationBellComponent } from './notification-bell.component';

/**
 * Placement du panneau de la cloche.
 *
 * <p>Le panneau était ancré au bord droit du <b>bouton</b> ({@code position: absolute; right: 0}).
 * Or la cloche n'est jamais au bord de l'écran — l'avatar du compte est à sa droite dans le
 * portail athlète, trois autres boutons dans la barre mobile du coach. Ses 360 px de large
 * partaient donc vers la gauche <b>hors de l'écran</b> sur un téléphone : le titre du panneau et
 * le début de chaque notification étaient tronqués, hors d'atteinte du doigt comme du défilement.
 * C'est le défaut remonté en bêta, capture à l'appui.</p>
 *
 * <p>Ces tests fixent l'invariant qui manquait : quelle que soit la position de la cloche dans sa
 * barre, le panneau tient entièrement dans la fenêtre.</p>
 */
describe('panneau de la cloche de notifications', () => {
  /** Reproduit une barre supérieure : la cloche à ~60 px du bord droit, l'avatar après elle. */
  @Component({
    standalone: true,
    imports: [NotificationBellComponent],
    template: `
      <header style="display:flex; justify-content:flex-end; padding: 12px 16px;">
        <app-notification-bell />
        <span style="width:44px; height:44px;"></span>
      </header>
    `,
  })
  class HostComponent {
    readonly bell = viewChild.required(NotificationBellComponent);
  }

  /** Largeur réelle du panneau, telle que la feuille de style la borne. */
  const MARGIN = 12;
  function panelWidth(): number {
    return Math.min(360, window.innerWidth - 2 * MARGIN);
  }

  async function openBell() {
    TestBed.configureTestingModule({
      imports: [HostComponent],
      providers: [
        provideRouter([]), provideHttpClient(), provideHttpClientTesting(),
        importProvidersFrom(LucideAngularModule.pick({ Bell, Settings })),
      ],
    });
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const bell = fixture.componentInstance.bell();
    bell.toggle();
    fixture.detectChanges();
    return { fixture, bell };
  }

  it('reste entièrement dans la fenêtre, sans déborder par la gauche', async () => {
    const { bell } = await openBell();

    const left = window.innerWidth - bell.anchorRight() - panelWidth();
    expect(left)
      .withContext('le bord gauche du panneau doit rester visible')
      .toBeGreaterThanOrEqual(0);
    expect(bell.anchorRight()).toBeGreaterThanOrEqual(MARGIN);
  });

  it('se place sous la cloche, jamais par-dessus', async () => {
    const { fixture, bell } = await openBell();

    const button = (fixture.nativeElement as HTMLElement).querySelector('.bell-btn')!;
    expect(bell.anchorTop()).toBeGreaterThanOrEqual(button.getBoundingClientRect().bottom);
  });

  it('se replace quand la fenêtre change de taille (rotation du téléphone)', async () => {
    const { bell } = await openBell();
    bell.anchorRight.set(9999);

    window.dispatchEvent(new Event('resize'));

    expect(bell.anchorRight()).toBeLessThan(window.innerWidth);
  });

  it('ne mesure plus rien une fois refermé', async () => {
    const { bell } = await openBell();
    bell.close();
    bell.anchorRight.set(9999);

    window.dispatchEvent(new Event('resize'));

    expect(bell.anchorRight()).toBe(9999);
  });
});
