import { Directive, ElementRef, OnDestroy, OnInit, booleanAttribute, inject, input, numberAttribute } from '@angular/core';

/**
 * Apparition « à l'entrée dans le champ » : l'élément monte légèrement et se révèle
 * la première fois qu'il devient visible (IntersectionObserver + Web Animations API,
 * donc sans CSS global ni conflit de styles). Un délai permet de cadencer une grille
 * (effet de relève d'instruments). Respecte `prefers-reduced-motion` (aucune animation).
 *
 * @example
 * <article appReveal></article>
 * <article *ngFor="…" [appReveal]="$index * 60"></article>
 */
@Directive({
  selector: '[appReveal]',
  standalone: true,
})
export class RevealDirective implements OnInit, OnDestroy {
  /** Délai avant l'apparition (ms) — utile pour cadencer une liste. */
  readonly appReveal = input(0, { transform: numberAttribute });
  readonly duration = input(520, { transform: numberAttribute });
  readonly disabled = input(false, { transform: booleanAttribute });

  private readonly host = inject(ElementRef<HTMLElement>);
  private observer?: IntersectionObserver;

  ngOnInit(): void {
    const el = this.host.nativeElement;
    const reduce = typeof matchMedia !== 'undefined'
      && matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (this.disabled() || reduce || typeof IntersectionObserver === 'undefined') {
      return;
    }
    // Masque immédiatement (avant le 1er paint) pour éviter un flash avant l'observer.
    el.style.opacity = '0';
    this.observer = new IntersectionObserver((entries, obs) => {
      for (const entry of entries) {
        if (entry.isIntersecting) {
          this.play(el);
          obs.unobserve(el);
        }
      }
    }, { threshold: 0.08 });
    this.observer.observe(el);
  }

  private play(el: HTMLElement): void {
    const anim = el.animate(
      [
        { opacity: 0, transform: 'translateY(12px)' },
        { opacity: 1, transform: 'translateY(0)' },
      ],
      {
        duration: this.duration(),
        delay: this.appReveal(),
        easing: 'cubic-bezier(0.32, 0.72, 0, 1)',
        fill: 'backwards',
      },
    );
    // Rend la main au flux normal une fois révélé (retire l'opacité inline posée à l'init).
    anim.onfinish = () => { el.style.opacity = ''; };
  }

  ngOnDestroy(): void {
    this.observer?.disconnect();
  }
}
