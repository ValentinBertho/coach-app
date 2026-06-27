import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { AuthService } from '../../core/services/auth.service';
import { HELP_GUIDES, HelpAudience, HelpSection } from './help-content';

/**
 * Centre d'aide intégré, adaptatif selon le profil (athlète / coach / admin).
 *
 * Le public cible (`audience`) est passé par la route (`data.audience`) ; à défaut,
 * il est déduit du rôle de l'utilisateur connecté. Le rendu est un accordéon
 * recherchable, stylé via les tokens du thème → s'adapte au fond clair (coach/admin)
 * comme au fond sombre du portail athlète.
 */
@Component({
  selector: 'app-help-center',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink, IconComponent],
  template: `
    <div class="help">
      <header class="help-top">
        <a [routerLink]="backLink()" class="btn btn-ghost btn-sm">← Retour</a>
        <div class="help-title">
          <span class="help-ic"><app-icon name="life-buoy" [size]="22" /></span>
          <div>
            <h1 class="display-sm">{{ guide().title }}</h1>
            <p class="subtitle">{{ guide().subtitle }}</p>
          </div>
        </div>
      </header>

      <div class="help-search">
        <app-icon name="search" [size]="16" />
        <input type="search" class="form-control" placeholder="Rechercher dans l'aide…"
               [ngModel]="query()" (ngModelChange)="query.set($event)" aria-label="Rechercher dans l'aide" />
      </div>

      @if (sections().length === 0) {
        <div class="help-empty card">
          <p>Aucun résultat pour « {{ query() }} ».</p>
          <button type="button" class="btn btn-ghost btn-sm" (click)="query.set('')">Effacer la recherche</button>
        </div>
      }

      <div class="help-list">
        @for (s of sections(); track s.id) {
          <section class="help-section card" [class.open]="isOpen(s.id)">
            <button type="button" class="hs-head" (click)="toggle(s.id)" [attr.aria-expanded]="isOpen(s.id)">
              <span class="hs-ic"><app-icon [name]="s.icon" [size]="18" /></span>
              <span class="hs-txt">
                <strong>{{ s.title }}</strong>
                <span class="hs-sum">{{ s.summary }}</span>
              </span>
              <span class="hs-chev" [class.rot]="isOpen(s.id)"><app-icon name="chevron-right" [size]="18" /></span>
            </button>

            @if (isOpen(s.id)) {
              <div class="hs-body">
                @for (b of s.blocks; track $index) {
                  @switch (b.kind) {
                    @case ('text') { <p class="hb-text">{{ b.text }}</p> }
                    @case ('steps') {
                      <ol class="hb-steps">
                        @for (it of b.items ?? []; track $index) { <li>{{ it }}</li> }
                      </ol>
                    }
                    @case ('list') {
                      <ul class="hb-list">
                        @for (it of b.items ?? []; track $index) { <li>{{ it }}</li> }
                      </ul>
                    }
                    @case ('callout') {
                      <div class="hb-callout" [class]="'tone-' + (b.tone ?? 'info')">
                        <app-icon [name]="calloutIcon(b.tone)" [size]="16" />
                        <span>{{ b.text }}</span>
                      </div>
                    }
                  }
                }
              </div>
            }
          </section>
        }
      </div>

      <p class="help-foot field-hint">
        Une question sans réponse ici ? {{ contactHint() }}
      </p>
    </div>
  `,
  styles: [`
    .help { max-width: 760px; margin-inline: auto; padding: var(--sp-5) var(--sp-4) var(--sp-12); display: flex; flex-direction: column; gap: var(--sp-4); }

    .help-top { display: flex; flex-direction: column; gap: var(--sp-3); }
    .help-title { display: flex; align-items: center; gap: var(--sp-3); }
    .help-title h1 { margin: 0; }
    .help-title .subtitle { color: var(--ink-3); margin: 2px 0 0; font-size: var(--text-sm); }
    .help-ic { display: inline-flex; align-items: center; justify-content: center; width: 44px; height: 44px; flex-shrink: 0; border-radius: var(--radius-lg); background: var(--gradient-brand, var(--primary)); color: #fff; }

    .help-search { display: flex; align-items: center; gap: var(--sp-2); padding: 0 var(--sp-3); background: var(--paper); border: 1px solid var(--hairline); border-radius: var(--radius-pill, 999px); color: var(--ink-3); }
    .help-search input { border: none; background: transparent; padding: var(--sp-2) 0; flex: 1; min-height: 44px; }
    .help-search input:focus { outline: none; box-shadow: none; }

    .help-list { display: flex; flex-direction: column; gap: var(--sp-3); }

    .help-section { padding: 0; overflow: hidden; }
    .hs-head { display: flex; align-items: center; gap: var(--sp-3); width: 100%; text-align: left; background: transparent; border: none; cursor: pointer; padding: var(--sp-3) var(--sp-4); color: var(--ink); }
    .hs-ic { display: inline-flex; align-items: center; justify-content: center; width: 36px; height: 36px; flex-shrink: 0; border-radius: var(--radius-md); background: var(--canvas); color: var(--primary); }
    .hs-txt { display: flex; flex-direction: column; gap: 2px; flex: 1; min-width: 0; }
    .hs-txt strong { color: var(--ink); font-size: var(--text-md); }
    .hs-sum { color: var(--ink-3); font-size: var(--text-sm); }
    .hs-chev { color: var(--ink-3); transition: transform var(--duration-fast, 160ms) var(--ease, ease); flex-shrink: 0; }
    .hs-chev.rot { transform: rotate(90deg); color: var(--primary); }

    .hs-body { padding: 0 var(--sp-4) var(--sp-4); display: flex; flex-direction: column; gap: var(--sp-3); border-top: 1px solid var(--hairline); padding-top: var(--sp-3); }
    .hb-text { margin: 0; color: var(--ink-2); line-height: 1.55; }
    .hb-steps, .hb-list { margin: 0; padding-left: 1.25rem; display: flex; flex-direction: column; gap: var(--sp-2); color: var(--ink-2); line-height: 1.5; }
    .hb-steps li, .hb-list li { padding-left: var(--sp-1); }

    .hb-callout { display: flex; align-items: flex-start; gap: var(--sp-2); padding: var(--sp-3); border-radius: var(--radius-md); font-size: var(--text-sm); line-height: 1.5; border: 1px solid transparent; }
    .hb-callout app-icon { flex-shrink: 0; margin-top: 1px; }
    .tone-tip { background: var(--success-bg, rgba(17,192,139,0.12)); color: var(--success-text, #0a7a57); border-color: var(--success-border, rgba(17,192,139,0.3)); }
    .tone-info { background: var(--info-bg, rgba(61,90,254,0.10)); color: var(--info-text, var(--primary)); border-color: var(--info-border, rgba(61,90,254,0.25)); }
    .tone-warn { background: var(--warn-bg, rgba(255,138,60,0.12)); color: var(--warn-text, #b3560f); border-color: var(--warn-border, rgba(255,138,60,0.3)); }

    .help-empty { text-align: center; display: flex; flex-direction: column; align-items: center; gap: var(--sp-2); padding: var(--sp-5); }
    .help-foot { text-align: center; margin: var(--sp-2) 0 0; }
  `],
})
export class HelpCenterComponent {
  private readonly auth = inject(AuthService);

  /** Public cible, fourni par la route (`data.audience`). */
  readonly audience = input<HelpAudience | undefined>(undefined);

  readonly query = signal('');
  private readonly openIds = signal<Set<string>>(new Set());

  /** Public effectif : route en priorité, sinon déduit du rôle connecté. */
  readonly effectiveAudience = computed<HelpAudience>(() => this.audience() ?? this.fromRole());

  readonly guide = computed(() => HELP_GUIDES[this.effectiveAudience()]);

  /** Sections filtrées par la recherche (titre, résumé, contenu). */
  readonly sections = computed<HelpSection[]>(() => {
    const q = this.query().trim().toLowerCase();
    const all = this.guide().sections;
    if (!q) return all;
    return all.filter((s) => this.haystack(s).includes(q));
  });

  private fromRole(): HelpAudience {
    const role = this.auth.currentUser()?.role;
    if (role === 'PLATFORM_ADMIN') return 'admin';
    if (role === 'ATHLETE') return 'athlete';
    return 'coach';
  }

  private haystack(s: HelpSection): string {
    const blocks = s.blocks
      .map((b) => `${b.text ?? ''} ${(b.items ?? []).join(' ')}`)
      .join(' ');
    return `${s.title} ${s.summary} ${blocks}`.toLowerCase();
  }

  /** En recherche, tout est déplié pour montrer les correspondances. */
  isOpen(id: string): boolean {
    return this.query().trim().length > 0 || this.openIds().has(id);
  }

  toggle(id: string): void {
    const next = new Set(this.openIds());
    next.has(id) ? next.delete(id) : next.add(id);
    this.openIds.set(next);
  }

  calloutIcon(tone?: string): string {
    return tone === 'tip' ? 'lightbulb' : tone === 'warn' ? 'alert-triangle' : 'info';
  }

  backLink(): string {
    switch (this.effectiveAudience()) {
      case 'athlete': return '/athlete/today';
      case 'admin': return '/admin';
      default: return '/app';
    }
  }

  contactHint(): string {
    return this.effectiveAudience() === 'athlete'
      ? 'Écris à ton coach depuis l\'onglet Messages.'
      : 'Contactez le support de la plateforme.';
  }
}
