import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { environment } from '../../../environments/environment';
import { ToastService } from '../../core/services/toast.service';

export interface ReceivedTestimonial {
  id: string;
  authorName: string;
  body: string;
  published: boolean;
  updatedAt: string;
}

/**
 * Les témoignages reçus par le coach : il choisit lesquels paraissent sur sa page publique.
 * Un texte, pas une note : rien à moyenner, rien à classer.
 */
@Component({
  selector: 'app-coach-testimonials',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe],
  template: `
    <section class="card ct">
      <h2>Témoignages de vos athlètes</h2>
      <p class="field-hint">
        Vos athlètes peuvent écrire quelques mots depuis leur profil. Rien ne paraît sur votre page
        sans votre accord ; un texte repris par son auteur repasse en attente.
      </p>
      @if (items().length === 0) {
        <p class="field-hint">Aucun témoignage pour l'instant.</p>
      }
      @for (t of items(); track t.id) {
        <article class="ct__item">
          <blockquote>« {{ t.body }} »</blockquote>
          <div class="ct__meta">
            <span class="field-hint">{{ t.authorName }} · {{ t.updatedAt | date: 'd MMM y' : '' : 'fr' }}</span>
            <span class="badge" [class.badge-success]="t.published">{{ t.published ? 'Publié' : 'En attente' }}</span>
            <button type="button" class="btn btn-ghost btn-sm" (click)="toggle(t)">
              {{ t.published ? 'Retirer de ma page' : 'Publier sur ma page' }}
            </button>
          </div>
        </article>
      }
    </section>
  `,
  styles: [`
    .ct { padding: var(--sp-5); margin-top: var(--sp-5); display: flex; flex-direction: column; gap: var(--sp-3); }
    .ct h2 { margin: 0; font-size: var(--text-lg); }
    .ct__item { border-top: 1px solid var(--hairline); padding-top: var(--sp-3); }
    .ct__item blockquote { margin: 0 0 var(--sp-2); font-style: italic; color: var(--ink-2); }
    .ct__meta { display: flex; gap: var(--sp-2); align-items: center; flex-wrap: wrap; }
  `],
})
export class CoachTestimonialsComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly toast = inject(ToastService);
  private readonly base = environment.apiUrl;

  readonly items = signal<ReceivedTestimonial[]>([]);

  ngOnInit(): void {
    this.http.get<ReceivedTestimonial[]>(`${this.base}/me/testimonials`)
      .subscribe({ next: (l) => this.items.set(l), error: () => this.items.set([]) });
  }

  toggle(t: ReceivedTestimonial): void {
    this.http.post<ReceivedTestimonial>(`${this.base}/me/testimonials/${t.id}/publish`, {},
      { params: { published: String(!t.published) } }).subscribe({
      next: (u) => {
        this.items.update((l) => l.map((x) => (x.id === u.id ? u : x)));
        this.toast.success(u.published ? 'Publié sur votre page.' : 'Retiré de votre page.');
      },
      error: () => this.toast.error('Modification impossible pour le moment.'),
    });
  }
}
