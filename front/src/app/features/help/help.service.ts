import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { UserRole } from '../../core/models/user.model';
import { HelpAudience } from './help-content';

/**
 * Pilote du centre d'aide : centralise la correspondance rôle → public → route,
 * l'état de la recherche globale (overlay app-level) et la navigation contextuelle
 * vers une section précise (« ? » sur un écran).
 */
@Injectable({ providedIn: 'root' })
export class HelpService {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  /** Overlay de recherche globale ouvert ? (monté une fois dans AppComponent). */
  readonly searchOpen = signal(false);

  audienceFor(role?: UserRole | null): HelpAudience {
    if (role === 'PLATFORM_ADMIN') return 'admin';
    if (role === 'ATHLETE') return 'athlete';
    return 'coach';
  }

  /** Public cible de l'utilisateur connecté. */
  readonly audience = computed<HelpAudience>(() => this.audienceFor(this.auth.currentUser()?.role));

  /** Route du centre d'aide pour un public donné (défaut : l'utilisateur courant). */
  routeFor(audience: HelpAudience = this.audience()): string {
    switch (audience) {
      case 'athlete': return '/athlete/help';
      case 'admin': return '/admin/aide';
      default: return '/app/aide';
    }
  }

  openSearch(): void { this.searchOpen.set(true); }
  closeSearch(): void { this.searchOpen.set(false); }

  /** Ouvre le centre d'aide (sans cible de section). */
  open(audience: HelpAudience = this.audience()): void {
    this.router.navigate([this.routeFor(audience)]);
  }

  /** Ouvre le centre d'aide directement sur une section (lien contextuel / résultat de recherche). */
  goToSection(sectionId: string, audience: HelpAudience = this.audience()): void {
    this.searchOpen.set(false);
    this.router.navigate([this.routeFor(audience)], { queryParams: { section: sectionId } });
  }
}
