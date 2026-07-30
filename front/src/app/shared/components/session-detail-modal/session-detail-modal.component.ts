import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { IconComponent } from '../icon/icon.component';
import { SessionCategory } from '../../../core/models/session-category.model';
import { CourseBlock, CourseStructureResponse, courseBlockTypeLabel } from '../../../core/models/course.model';
import { TrainingZone } from '../../../core/models/training-zone.model';
import { CourseService } from '../../../core/services/course.service';
import { TrainingZoneService } from '../../../core/services/training-zone.service';

interface Section { key: 'warmup' | 'main' | 'cooldown'; label: string; }

/**
 * Modale de consultation d'une séance course (lecture seule) : structure échauffement / corps /
 * retour au calme, chaque bloc avec son volume, sa zone (cible lue sur la fiche athlète) et sa
 * récupération. Ouverte depuis la bibliothèque et la liste des séances course.
 */
@Component({
  selector: 'app-session-detail-modal',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, RouterLink, FormsModule],
  template: `
    <div class="overlay" (click)="closed.emit()">
      <div class="dialog card" (click)="$event.stopPropagation()" role="dialog" aria-modal="true">
        <header class="sd-head">
          <div>
            <h2>{{ name() || 'Séance' }}</h2>
            <!-- Catégorie modifiable ici (menu déroulant) : ranger une séance sans quitter la consultation. -->
            @if (categories().length) {
              <label class="sd-cat">
                <span class="sd-cat-lb">Catégorie</span>
                <select class="form-control" [ngModel]="categoryId()" (ngModelChange)="categoryChange.emit($event)">
                  <option value="">Sans catégorie</option>
                  @for (c of categories(); track c.id) { <option [value]="c.id">{{ c.name }}</option> }
                </select>
              </label>
            } @else if (data()?.categoryName) {
              <span class="badge badge-neutral">{{ data()!.categoryName }}</span>
            }
          </div>
          <button type="button" class="icon-btn" (click)="closed.emit()" aria-label="Fermer"><app-icon name="x" [size]="18" /></button>
        </header>

        @if (loading()) {
          <div class="skeleton" style="height:160px"></div>
        } @else {
          @for (sec of sections; track sec.key) {
            @if (blocks(sec.key).length) {
              <section class="sd-sec">
                <h3>{{ sec.label }}</h3>
                @for (b of blocks(sec.key); track b.id) {
                  <div class="sd-block">
                    <span class="sd-vol metric">{{ volume(b) }}</span>
                    <span class="sd-type">{{ typeLabel(b.type) }}</span>
                    <span class="sd-zone">
                      <span class="dot" [style.background]="zoneColor(b)"></span>{{ zoneLabel(b) }}
                    </span>
                    @if (b.rpe) { <span class="sd-rpe metric">RPE {{ b.rpe }}</span> }
                    @if (b.recovery; as r) {
                      <span class="sd-rec">récup {{ recoveryVol(r) }} · {{ zoneLabel(r) }}</span>
                    }
                  </div>
                  @if (b.note) { <p class="sd-note">« {{ b.note }} »</p> }
                }
              </section>
            }
          }
          @if (empty()) { <p class="field-hint">Structure vide — à construire dans l'éditeur.</p> }
          @if (data()?.notes) {
            <section class="sd-sec">
              <h3>Notes de séance</h3>
              <p class="sd-notes">{{ data()!.notes }}</p>
            </section>
          }
        }

        <footer class="sd-foot">
          <a class="btn btn-primary btn-sm" [routerLink]="['/app/templates', templateId(), 'structure']" (click)="closed.emit()">
            <app-icon name="pencil" [size]="15" /> Modifier la structure
          </a>
        </footer>
      </div>
    </div>
  `,
  styles: [`
    .overlay { position: fixed; inset: 0; z-index: 1100; background: var(--bg-overlay); display: flex; align-items: center; justify-content: center; padding: var(--sp-4); animation: fadeIn var(--duration) var(--ease); }
    .dialog { max-width: 620px; width: 100%; max-height: 86vh; overflow-y: auto; animation: slideInUp var(--duration) var(--ease); }
    .sd-head { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--sp-3); margin-bottom: var(--sp-4); }
    .sd-head h2 { margin: 0 0 var(--sp-1); }
    .sd-head > div { display: flex; flex-direction: column; align-items: flex-start; gap: var(--sp-2); }
    .sd-cat { display: inline-flex; align-items: center; gap: var(--sp-2); }
    .sd-cat-lb { font-size: var(--text-xs); text-transform: uppercase; letter-spacing: 0.04em; color: var(--ink-3); font-weight: 700; }
    .sd-cat .form-control { min-height: 34px; padding: 2px var(--sp-2); font-size: var(--text-sm); }
    .sd-sec { margin-bottom: var(--sp-4); }
    .sd-sec h3 { font-size: var(--text-xs); text-transform: uppercase; letter-spacing: 0.04em; color: var(--ink-3); margin: 0 0 var(--sp-2); }
    .sd-block { display: flex; align-items: center; gap: var(--sp-3); flex-wrap: wrap; padding: var(--sp-2) var(--sp-3); border: 1px solid var(--hairline); border-radius: var(--radius-md); margin-bottom: var(--sp-2); }
    .sd-vol { font-weight: 800; min-width: 78px; }
    .sd-type { font-weight: 600; }
    .sd-zone { display: inline-flex; align-items: center; gap: var(--sp-1); color: var(--ink-2); }
    .dot { width: 11px; height: 11px; border-radius: var(--radius-full); }
    .sd-rec { font-size: var(--text-sm); color: var(--ink-3); margin-left: auto; }
    .sd-rpe { font-size: var(--text-xs); font-weight: 800; color: var(--ink-2); background: var(--paper-sunk); border-radius: var(--radius-full); padding: 1px var(--sp-2); }
    .sd-notes { white-space: pre-wrap; color: var(--ink-2); font-size: var(--text-sm); margin: 0; }
    .sd-note { font-size: var(--text-sm); color: var(--ink-3); font-style: italic; margin: calc(var(--sp-2) * -1 + 2px) 0 var(--sp-2) var(--sp-3); }
    .sd-foot { display: flex; justify-content: flex-end; margin-top: var(--sp-2); }
  `],
})
export class SessionDetailModalComponent {
  readonly templateId = input.required<string>();
  readonly name = input<string>('');
  /** Catégories du club (menu déroulant de rangement) ; vide = pas de sélecteur. */
  readonly categories = input<SessionCategory[]>([]);
  /** Catégorie courante de la séance (id) ; '' = sans catégorie. */
  readonly categoryId = input<string>('');
  readonly closed = output<void>();
  /** Émis quand le coach change la catégorie (id ou '' pour aucune). */
  readonly categoryChange = output<string>();

  private readonly course = inject(CourseService);
  private readonly zoneService = inject(TrainingZoneService);

  readonly loading = signal(true);
  readonly data = signal<CourseStructureResponse | null>(null);
  private readonly zones = signal<TrainingZone[]>([]);

  readonly sections: Section[] = [
    { key: 'warmup', label: 'Échauffement' },
    { key: 'main', label: 'Corps de séance' },
    { key: 'cooldown', label: 'Retour au calme' },
  ];

  private readonly zoneById = computed(() => {
    const m = new Map<string, TrainingZone>();
    for (const z of this.zones()) m.set(z.id, z);
    return m;
  });

  readonly empty = computed(() => {
    const s = this.data()?.structure;
    return !!this.data() && !(s && (s.warmup.length || s.main.length || s.cooldown.length));
  });

  constructor() {
    this.zoneService.list().subscribe({ next: (z) => this.zones.set(z), error: () => this.zones.set([]) });
    effect(() => {
      const id = this.templateId();
      if (id) this.fetch(id);
    }, { allowSignalWrites: true });
  }

  private fetch(id: string): void {
    this.loading.set(true);
    this.course.getStructure(id).subscribe({
      next: (r) => { this.data.set(r); this.loading.set(false); },
      error: () => { this.data.set(null); this.loading.set(false); },
    });
  }

  blocks(key: Section['key']): CourseBlock[] {
    return this.data()?.structure?.[key] ?? [];
  }

  typeLabel(type: string): string { return courseBlockTypeLabel(type); }

  volume(b: CourseBlock): string {
    const unit = b.distanceM != null ? `${b.distanceM >= 1000 ? (b.distanceM / 1000) + ' km' : b.distanceM + ' m'}`
      : b.durationS != null ? this.dur(b.durationS) : '—';
    return (b.reps && b.reps > 1) ? `${b.reps} × ${unit}` : unit;
  }

  recoveryVol(r: { durationS?: number | null; distanceM?: number | null; type: string }): string {
    const v = r.distanceM != null ? `${r.distanceM} m` : r.durationS != null ? this.dur(r.durationS) : '';
    const t = r.type === 'walk' ? 'marche' : r.type === 'static' ? 'statique' : 'trot';
    return `${t} ${v}`.trim();
  }

  private dur(s: number): string {
    const m = Math.round(s / 60);
    return m >= 60 ? `${Math.floor(m / 60)}h${String(m % 60).padStart(2, '0')}` : `${m} min`;
  }

  zoneLabel(b: { prescription?: { zoneId?: string | null; ref?: string | null; minPct?: number | null; maxPct?: number | null } | null }): string {
    const p = b.prescription;
    if (p?.zoneId) return this.zoneById().get(p.zoneId)?.name ?? 'Zone';
    if (p?.ref && p.minPct != null && p.maxPct != null) return `${p.minPct}–${p.maxPct} % ${p.ref.replace('PCT_', '')}`;
    return '—';
  }

  zoneColor(b: { prescription?: { zoneId?: string | null } | null }): string {
    const id = b.prescription?.zoneId;
    return (id && this.zoneById().get(id)?.color) || 'var(--ink-3)';
  }
}
