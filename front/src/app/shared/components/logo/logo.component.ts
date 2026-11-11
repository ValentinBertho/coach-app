import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Compteur d'instances : chaque logo porte son propre identifiant de dégradé. */
let nextId = 0;

/**
 * Logo Darilab : mark (squircle dégradé + stride) + wordmark optionnel.
 * Cohérent avec branding/icon-master.svg et les icônes PWA, dans la couleur de la marque
 * (pétrole) : le mark était resté bleu-indigo après le passage du design system au pétrole.
 *
 * <p>L'identifiant du dégradé est propre à chaque instance. Il était codé en dur (`crbg`) : avec
 * deux logos dans la page (menu latéral de bureau, masqué, et barre mobile), le second
 * `url(#crbg)` résolvait vers le premier, dont le SVG était dans un conteneur `display: none` :
 * sur téléphone, le carré disparaissait et il ne restait que le point vert.</p>
 */
@Component({
  selector: 'app-logo',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span class="logo-wrap" [style.--logo-size.px]="size()">
      <svg class="mark" [attr.width]="size()" [attr.height]="size()" viewBox="0 0 1024 1024"
           fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
        <defs>
          <linearGradient [attr.id]="gradientId" x1="0" y1="0" x2="1024" y2="1024" gradientUnits="userSpaceOnUse">
            <stop offset="0" stop-color="#12808B"/><stop offset="1" stop-color="#0A4F58"/>
          </linearGradient>
        </defs>
        <rect width="1024" height="1024" rx="232" [attr.fill]="'url(#' + gradientId + ')'"/>
        <path d="M250 720 C 420 700, 540 580, 770 360" stroke="#fff" stroke-width="76" stroke-linecap="round" fill="none"/>
        <path d="M250 560 L 360 560" stroke="#fff" stroke-width="40" stroke-linecap="round" opacity="0.55"/>
        <path d="M250 640 L 320 640" stroke="#fff" stroke-width="40" stroke-linecap="round" opacity="0.35"/>
        <circle cx="788" cy="338" r="86" fill="#16C47F"/>
        <circle cx="788" cy="338" r="86" stroke="#fff" stroke-width="22" fill="none"/>
      </svg>
      @if (showText()) {
        <span class="wordmark">Dari<span class="accent">lab</span></span>
      }
    </span>
  `,
  styles: [`
    .logo-wrap { display: inline-flex; align-items: center; gap: var(--sp-2); }
    .mark { border-radius: 24%; display: block; flex-shrink: 0; }
    .wordmark {
      font-family: var(--font-display);
      font-weight: 700;
      font-size: calc(var(--logo-size, 32px) * 0.62);
      letter-spacing: -0.02em;
      color: var(--ink);
    }
    .wordmark .accent { color: var(--primary); }
  `],
})
export class LogoComponent {
  readonly size = input(32);
  readonly showText = input(true);
  protected readonly gradientId = `darilab-logo-${nextId++}`;
}
