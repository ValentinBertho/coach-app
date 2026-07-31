import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, input, signal } from '@angular/core';
import { Autosave } from '../../../core/services/autosave';
import { IconComponent } from '../icon/icon.component';

/**
 * Pastille d'état d'auto-sauvegarde, à poser près du bouton « Enregistrer ».
 *
 * Une auto-sauvegarde muette est angoissante : le coach ne sait pas s'il peut fermer l'onglet.
 * La pastille dit toujours où en est son travail — « Enregistrement… », « Enregistré · il y a
 * 3 s », « Non enregistré » — et le compteur se rafraîchit tout seul pour rester crédible.
 *
 * @example <app-autosave-badge [autosave]="autosave" />
 */
@Component({
  selector: 'app-autosave-badge',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    <span class="asb" [class]="'asb--' + autosave().state()" role="status" aria-live="polite">
      @switch (autosave().state()) {
        @case ('saving') { <app-icon name="refresh-cw" [size]="13" /> <span>Enregistrement…</span> }
        @case ('saved') { <app-icon name="check" [size]="13" /> <span>Enregistré · {{ ago() }}</span> }
        @case ('dirty') { <app-icon name="circle" [size]="9" /> <span>Non enregistré</span> }
        @case ('error') { <app-icon name="alert-triangle" [size]="13" /> <span>Échec de l'enregistrement</span> }
      }
    </span>
  `,
  styles: [`
    .asb {
      display: inline-flex; align-items: center; gap: var(--sp-1);
      font-size: var(--text-sm); font-weight: 600; color: var(--ink-3);
      white-space: nowrap;
    }
    .asb--saved { color: var(--success); }
    .asb--dirty { color: var(--warning); }
    .asb--error { color: var(--danger); }
  `],
})
export class AutosaveBadgeComponent {
  readonly autosave = input.required<Autosave>();

  /** Battement de seconde : sans lui, « il y a 3 s » resterait figé à « à l'instant ». */
  private readonly now = signal(Date.now());

  constructor() {
    const tick = setInterval(() => this.now.set(Date.now()), 1000);
    inject(DestroyRef).onDestroy(() => clearInterval(tick));
  }

  protected readonly ago = computed(() => {
    const at = this.autosave().savedAt();
    if (at == null) return 'à l’instant';
    const s = Math.max(0, Math.round((this.now() - at) / 1000));
    if (s < 5) return 'à l’instant';
    if (s < 60) return `il y a ${s} s`;
    const m = Math.round(s / 60);
    return m < 60 ? `il y a ${m} min` : `il y a ${Math.round(m / 60)} h`;
  });
}
