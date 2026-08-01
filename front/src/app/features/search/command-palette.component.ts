import {
  ChangeDetectionStrategy, Component, ElementRef, HostListener,
  computed, effect, inject, signal, viewChild,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AthleteService } from '../../core/services/athlete.service';
import { AuthService } from '../../core/services/auth.service';
import { CommandPaletteService } from '../../core/services/command-palette.service';
import { StrengthService } from '../../core/services/strength.service';
import { WorkoutTemplateService } from '../../core/services/workout-template.service';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { SkeletonComponent } from '../../shared/components/skeleton/skeleton.component';

type ResultKind = 'athlete' | 'course' | 'strength' | 'screen';

interface SearchResult {
  kind: ResultKind;
  id: string;
  label: string;
  hint: string;
  icon: string;
  link: unknown[];
}

const KIND_LABELS: Record<ResultKind, string> = {
  athlete: 'Athlète',
  course: 'Séance course',
  strength: 'Séance de force',
  screen: 'Écran',
};

/** Écrans atteignables directement — évite de traverser la nav pour un aller simple. */
const SCREENS: { label: string; link: unknown[]; icon: string }[] = [
  { label: 'Tableau de bord', link: ['/app'], icon: 'layout-dashboard' },
  { label: 'Athlètes', link: ['/app/athletes'], icon: 'users' },
  { label: 'Groupes', link: ['/app/groups'], icon: 'users-round' },
  { label: 'Calendrier', link: ['/app/calendar'], icon: 'calendar' },
  { label: 'Messages', link: ['/app/messages'], icon: 'message-square' },
  { label: 'Retours à traiter', link: ['/app/feedback'], icon: 'check' },
  { label: 'Bibliothèque', link: ['/app/library'], icon: 'folder-open' },
  { label: 'Mes zones', link: ['/app/training-zones'], icon: 'target' },
  { label: 'Club', link: ['/app/club'], icon: 'building-2' },
  { label: 'Paramètres', link: ['/app/settings'], icon: 'settings' },
  { label: 'Aide', link: ['/app/aide'], icon: 'life-buoy' },
];

/**
 * Recherche globale (Cmd/Ctrl+K) — athlètes, modèles de séance course et force, écrans.
 *
 * Prévue au blueprint §3B et absente jusqu'ici : atteindre un athlète imposait de passer par
 * la liste et sa pagination. Les données viennent des services de liste existants, chargées à
 * la première ouverture et gardées en mémoire pour que la frappe reste instantanée.
 */
@Component({
  selector: 'app-command-palette',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [SkeletonComponent, FormsModule, IconComponent],
  template: `
    @if (palette.open()) {
      <div class="ovl" (click)="palette.close()">
        <div class="ovl-panel" role="dialog" aria-modal="true" aria-label="Recherche globale"
             (click)="$event.stopPropagation()">
          <div class="ovl-search">
            <app-icon name="search" [size]="18" />
            <input #box type="search" class="ovl-input"
                   placeholder="Athlète, séance, écran…"
                   [ngModel]="query()" (ngModelChange)="onQuery($event)"
                   (keydown)="onKey($event)"
                   role="combobox" aria-expanded="true" aria-controls="cp-results"
                   [attr.aria-activedescendant]="results().length ? 'cp-opt-' + active() : null"
                   aria-label="Rechercher un athlète, une séance ou un écran" />
            <button type="button" class="ovl-close" (click)="palette.close()" aria-label="Fermer">
              <app-icon name="x" [size]="18" />
            </button>
          </div>

          <div class="ovl-results" id="cp-results" role="listbox">
            @for (r of results(); track r.kind + r.id; let i = $index) {
              <button type="button" class="ovl-row" role="option"
                      [id]="'cp-opt-' + i" [attr.aria-selected]="i === active()"
                      [class.active]="i === active()"
                      (mouseenter)="active.set(i)" (click)="go(r)">
                <span class="ovl-ic"><app-icon [name]="r.icon" [size]="18" /></span>
                <span class="ovl-txt">
                  <strong>{{ r.label }}</strong>
                  <span class="ovl-sum">{{ r.hint }}</span>
                </span>
                <span class="ovl-kind">{{ kindLabels[r.kind] }}</span>
              </button>
            } @empty {
              <p class="ovl-empty">
                @if (loading()) { <app-skeleton shape="text" [rows]="3" /> } @else { Aucun résultat pour « {{ query() }} ». }
              </p>
            }
          </div>

          <p class="ovl-foot">↑ ↓ pour naviguer · Entrée pour ouvrir · Échap pour fermer</p>
        </div>
      </div>
    }
  `,
  styles: [`
    .ovl { position: fixed; inset: 0; z-index: 1000; background: rgba(10, 12, 24, 0.55); backdrop-filter: blur(3px); display: flex; align-items: flex-start; justify-content: center; padding: clamp(var(--sp-4), 10vh, 120px) var(--sp-4) var(--sp-4); }
    .ovl-panel { width: 100%; max-width: 600px; background: var(--paper); border: 1px solid var(--hairline); border-radius: var(--radius-xl, 18px); box-shadow: var(--shadow-lg); overflow: hidden; display: flex; flex-direction: column; max-height: 80vh; }
    .ovl-search { display: flex; align-items: center; gap: var(--sp-2); padding: var(--sp-3) var(--sp-4); border-bottom: 1px solid var(--hairline); color: var(--ink-3); }
    .ovl-input { flex: 1; border: none; background: transparent; min-height: 40px; font-size: var(--text-md); color: var(--ink); }
    .ovl-input:focus { outline: none; box-shadow: none; }
    .ovl-close { display: inline-flex; align-items: center; justify-content: center; width: 32px; height: 32px; border: none; background: transparent; color: var(--ink-3); cursor: pointer; border-radius: var(--radius-md); }
    .ovl-close:hover { color: var(--ink); background: var(--canvas); }
    .ovl-results { overflow-y: auto; padding: var(--sp-2); display: flex; flex-direction: column; gap: 2px; }
    .ovl-row { display: flex; align-items: center; gap: var(--sp-3); width: 100%; text-align: left; background: transparent; border: none; cursor: pointer; padding: var(--sp-2) var(--sp-3); border-radius: var(--radius-md); color: var(--ink); }
    .ovl-row.active, .ovl-row:hover { background: var(--canvas); }
    .ovl-ic { display: inline-flex; align-items: center; justify-content: center; width: 34px; height: 34px; flex-shrink: 0; border-radius: var(--radius-md); background: var(--canvas); color: var(--primary); }
    .ovl-txt { display: flex; flex-direction: column; gap: 1px; flex: 1; min-width: 0; }
    .ovl-txt strong { color: var(--ink); font-size: var(--text-md); }
    .ovl-sum { color: var(--ink-3); font-size: var(--text-sm); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .ovl-kind { color: var(--ink-4); font-size: var(--text-xs); font-weight: 700; text-transform: uppercase; letter-spacing: 0.03em; flex-shrink: 0; }
    .ovl-empty { padding: var(--sp-5); text-align: center; color: var(--ink-3); margin: 0; }
    .ovl-foot { padding: var(--sp-2) var(--sp-4); margin: 0; border-top: 1px solid var(--hairline); color: var(--ink-4); font-size: var(--text-xs); text-align: center; }
  `],
})
export class CommandPaletteComponent {
  readonly palette = inject(CommandPaletteService);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);
  private readonly athleteService = inject(AthleteService);
  private readonly templateService = inject(WorkoutTemplateService);
  private readonly strengthService = inject(StrengthService);
  private readonly box = viewChild<ElementRef<HTMLInputElement>>('box');

  readonly kindLabels = KIND_LABELS;
  readonly query = signal('');
  readonly active = signal(0);
  readonly loading = signal(false);
  private loaded = false;

  private readonly athletes = signal<SearchResult[]>([]);
  private readonly templates = signal<SearchResult[]>([]);

  private readonly screens: SearchResult[] = SCREENS.map((s) => ({
    kind: 'screen' as const, id: s.label, label: s.label,
    hint: 'Aller à l’écran', icon: s.icon, link: s.link,
  }));

  /**
   * Résultats classés par pertinence de type : athlètes d'abord (l'usage le plus fréquent),
   * puis séances, puis écrans. Sans requête, on montre les écrans — une palette vide n'aide pas.
   */
  readonly results = computed<SearchResult[]>(() => {
    const q = this.normalize(this.query());
    if (!q) return this.screens;
    const match = (r: SearchResult) => this.normalize(`${r.label} ${r.hint}`).includes(q);
    return [
      ...this.athletes().filter(match),
      ...this.templates().filter(match),
      ...this.screens.filter(match),
    ].slice(0, 20);
  });

  constructor() {
    effect(() => {
      if (!this.palette.open()) return;
      this.query.set('');
      this.active.set(0);
      this.loadOnce();
      setTimeout(() => this.box()?.nativeElement.focus(), 30);
    }, { allowSignalWrites: true });
  }

  /** Chargement paresseux : une seule fois par session, à la première ouverture. */
  private loadOnce(): void {
    if (this.loaded || !this.auth.clubId()) return;
    this.loaded = true;
    this.loading.set(true);

    this.athleteService.list({ status: 'ACTIVE' }).subscribe({
      next: (page) => {
        this.athletes.set(page.content.map((a) => ({
          kind: 'athlete' as const,
          id: a.id,
          label: `${a.firstName} ${a.lastName}`,
          hint: a.groupName || 'Athlète',
          icon: 'user',
          link: ['/app/athletes', a.id],
        })));
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });

    this.templateService.listAll().subscribe({
      next: (all) => this.templates.update((list) => [
        ...list.filter((r) => r.kind !== 'course'),
        ...all.map((t) => ({
          kind: 'course' as const,
          id: t.id,
          label: t.name,
          hint: t.categoryName || 'Modèle de séance course',
          icon: 'library',
          link: ['/app/templates', t.id, 'structure'],
        })),
      ]),
      error: () => { /* la palette reste utilisable sans les modèles */ },
    });

    this.strengthService.listSessions().subscribe({
      next: (page) => this.templates.update((list) => [
        ...list.filter((r) => r.kind !== 'strength'),
        ...page.content.map((s) => ({
          kind: 'strength' as const,
          id: s.id,
          label: s.name,
          hint: 'Séance de préparation physique',
          icon: 'dumbbell',
          link: ['/app/strength/sessions', s.id, 'structure'],
        })),
      ]),
      error: () => { /* idem */ },
    });
  }

  onQuery(value: string): void {
    this.query.set(value);
    this.active.set(0);
  }

  /** Navigation clavier complète : ↑ ↓ pour parcourir, Entrée pour ouvrir. */
  onKey(event: KeyboardEvent): void {
    const count = this.results().length;
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      this.active.set(count ? (this.active() + 1) % count : 0);
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      this.active.set(count ? (this.active() - 1 + count) % count : 0);
    } else if (event.key === 'Enter') {
      event.preventDefault();
      const target = this.results()[this.active()];
      if (target) this.go(target);
    }
  }

  go(r: SearchResult): void {
    this.palette.close();
    this.router.navigate(r.link as unknown[]);
  }

  /** Insensible à la casse ET aux accents : « joel » doit trouver « Joël ». */
  private normalize(value: string): string {
    return value.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().trim();
  }

  @HostListener('document:keydown', ['$event'])
  onShortcut(event: KeyboardEvent): void {
    if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
      event.preventDefault();
      this.palette.toggle();
      return;
    }
    if (event.key === 'Escape' && this.palette.open()) {
      this.palette.close();
    }
  }
}
