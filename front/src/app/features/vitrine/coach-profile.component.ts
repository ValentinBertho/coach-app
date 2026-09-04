import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import {
  CoachOffer,
  CoachProfile,
  CoachProfileService,
  CoachProfileStatus,
  OfferPeriodicity,
  Vocabulary,
} from '../../core/services/coach-profile.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { ToastService } from '../../core/services/toast.service';
import { IconComponent } from '../../shared/components/icon/icon.component';

/**
 * L'éditeur de la vitrine du coach.
 *
 * <p><b>Ce que cet écran doit réussir.</b> Une fiche est ce qu'un athlète lit avant de choisir
 * quelqu'un à qui confier son entraînement — et, pour beaucoup de coachs, le premier texte de
 * présentation qu'ils écrivent. L'écran doit donc dire en permanence deux choses : où en est la
 * fiche, et ce qu'il reste à faire. La liste des manques vient du serveur, celui-là même qui
 * refusera la soumission : elle ne peut donc pas mentir, là où une règle recopiée côté client
 * finirait par diverger.</p>
 *
 * <p>L'enregistrement n'exige rien : une fiche s'écrit en plusieurs fois, et un formulaire qui
 * refuse de garder un brouillon incomplet pousse à le remplir n'importe comment pour passer. Ce
 * sont la <b>soumission</b>, et elle seule, qui vérifie la complétude.</p>
 */
@Component({
  selector: 'app-coach-profile',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, IconComponent],
  template: `
    <header class="page-head">
      <div>
        <h1 class="display-sm">Ma fiche publique</h1>
        <p class="field-hint">
          C'est ce qu'un athlète lit avant de vous demander de le coacher.
        </p>
      </div>
      @if (profile(); as p) {
        <span class="badge" [class]="statusBadge()">{{ p.statusLabel }}</span>
      }
    </header>

    @if (profile(); as p) {

      <!-- Le motif d'un refus, en tête : c'est la première chose à lire, et la seule qui dise
           quoi corriger. -->
      @if (p.status === 'DRAFT' && p.reviewNote) {
        <div class="card cp-note" role="alert">
          <app-icon name="info" [size]="18" />
          <div>
            <strong>Votre fiche a été renvoyée pour correction.</strong>
            <p>{{ p.reviewNote }}</p>
          </div>
        </div>
      }

      @if (p.status === 'PENDING') {
        <div class="card cp-note">
          <app-icon name="info" [size]="18" />
          <div>
            <strong>Votre fiche est en cours de validation.</strong>
            <p>
              Elle est relue à la main — comptez 48 h. Elle ne peut pas être modifiée d'ici là :
              sans quoi nous validerions un texte que vous auriez changé entre-temps.
            </p>
          </div>
        </div>
      }

      @if (p.status === 'SUSPENDED') {
        <div class="card cp-note cp-note--danger" role="alert">
          <app-icon name="alert-triangle" [size]="18" />
          <div>
            <strong>Votre fiche a été retirée de l'annuaire.</strong>
            <p>{{ p.reviewNote || "Répondez à l'e-mail reçu pour en discuter avec l'équipe." }}</p>
          </div>
        </div>
      }

      <section class="card cp-block cp-photo">
        <div class="cp-photo__preview">
          @if (photoSrc(); as src) {
            <img [src]="src" alt="Votre photo de profil" />
          } @else {
            <span class="cp-photo__empty" aria-hidden="true">
              <app-icon name="user" [size]="32" />
            </span>
          }
        </div>
        <div class="cp-photo__side">
          <h2 class="cp-block__title">Votre photo</h2>
          <p class="field-hint">
            C'est la première chose qu'un athlète voit. JPEG ou PNG, 5 Mo au plus — elle sera
            réduite et ré-encodée : les données de votre appareil, y compris le lieu de la prise
            de vue, ne sont pas conservées.
          </p>
          @if (!frozen()) {
            <div class="cp-photo__actions">
              <label class="btn btn-secondary btn-sm">
                {{ uploading() ? 'Envoi…' : (profile()?.photoUrl ? 'Changer' : 'Ajouter une photo') }}
                <input type="file" accept="image/jpeg,image/png,image/webp"
                       (change)="onPhotoPicked($event)" [disabled]="uploading()" hidden />
              </label>
              @if (profile()?.photoUrl) {
                <button type="button" class="btn btn-ghost btn-sm" [disabled]="uploading()"
                        (click)="removePhoto()">Retirer</button>
              }
            </div>
          }
        </div>
      </section>

      <form class="card cp-form" [formGroup]="form" (ngSubmit)="save()">
        <fieldset [disabled]="frozen()">
          <div class="form-group">
            <label for="headline">Votre accroche</label>
            <input id="headline" type="text" class="form-control" formControlName="headline"
                   maxlength="140" placeholder="Coach route et trail, du 10 km à l'ultra" />
            <span class="field-hint">Une ligne. C'est ce qu'on lit dans la liste avant de cliquer.</span>
          </div>

          <div class="form-group">
            <label for="bio">Votre présentation</label>
            <textarea id="bio" class="form-control" formControlName="bio" rows="7"
                      maxlength="4000"
                      placeholder="Votre parcours, votre façon de travailler, les athlètes que vous accompagnez…"></textarea>
            <span class="field-hint">{{ bioLength() }} / 4000 — 120 caractères minimum pour publier.</span>
          </div>

          <div class="form-group">
            <span class="cp-legend">Disciplines</span>
            <div class="cp-chips">
              @for (d of vocabulary()?.disciplines ?? []; track d.value) {
                <button type="button" class="cp-chip" [class.selected]="has('disciplines', d.value)"
                        [attr.aria-pressed]="has('disciplines', d.value)"
                        (click)="toggle('disciplines', d.value)">{{ d.label }}</button>
              }
            </div>
          </div>

          <div class="form-group">
            <span class="cp-legend">Spécialités</span>
            <div class="cp-chips">
              @for (s of vocabulary()?.specialties ?? []; track s.value) {
                <button type="button" class="cp-chip" [class.selected]="has('specialties', s.value)"
                        [attr.aria-pressed]="has('specialties', s.value)"
                        (click)="toggle('specialties', s.value)">{{ s.label }}</button>
              }
            </div>
            <span class="field-hint">
              Ce sont les filtres de l'annuaire : n'en cochez que ce sur quoi vous voulez être trouvé.
            </span>
          </div>

          <div class="form-group">
            <span class="cp-legend">Niveaux accompagnés</span>
            <div class="cp-chips">
              @for (l of vocabulary()?.levels ?? []; track l.value) {
                <button type="button" class="cp-chip" [class.selected]="has('levels', l.value)"
                        [attr.aria-pressed]="has('levels', l.value)"
                        (click)="toggle('levels', l.value)">{{ l.label }}</button>
              }
            </div>
            <span class="field-hint">Aucun coché = tous les niveaux.</span>
          </div>

          <div class="form-group">
            <span class="cp-legend">Langues</span>
            <div class="cp-chips">
              @for (l of languageOptions; track l.value) {
                <button type="button" class="cp-chip" [class.selected]="has('languages', l.value)"
                        [attr.aria-pressed]="has('languages', l.value)"
                        (click)="toggle('languages', l.value)">{{ l.label }}</button>
              }
            </div>
          </div>

          <div class="cp-row">
            <div class="form-group">
              <label class="consent-check">
                <input type="checkbox" formControlName="remote" />
                <span>Je coache à distance</span>
              </label>
              <label class="consent-check">
                <input type="checkbox" formControlName="inPerson" />
                <span>Je coache en présentiel</span>
              </label>
            </div>

            <div class="form-group">
              <label for="city">Ville</label>
              <input id="city" type="text" class="form-control" formControlName="city"
                     placeholder="Lyon" />
              <span class="field-hint">Requise si vous proposez du présentiel.</span>
            </div>
          </div>

          <div class="cp-row">
            <div class="form-group">
              <label for="experienceYears">Années d'expérience</label>
              <input id="experienceYears" type="number" class="form-control" min="0" max="70"
                     formControlName="experienceYears" />
            </div>
            <div class="form-group">
              <label for="capacityMax">Athlètes que vous pouvez suivre</label>
              <input id="capacityMax" type="number" class="form-control" min="1" max="500"
                     formControlName="capacityMax" />
              <span class="field-hint">
                Indicatif : c'est vous qui acceptez ou refusez chaque demande. Cela évite qu'on
                vous sollicite quand vous êtes complet.
              </span>
            </div>
          </div>

          <button type="submit" class="btn btn-secondary" [disabled]="saving() || frozen()">
            {{ saving() ? 'Enregistrement…' : 'Enregistrer' }}
          </button>
        </fieldset>
      </form>

      <!-- Formules : exigées pour publier, parce que le tarif est un filtre de l'annuaire et
           qu'une fiche sans tarif y serait invisible au premier clic. -->
      <section class="card cp-block">
        <h2 class="cp-block__title">Mes formules</h2>
        <p class="field-hint">
          Aucun paiement ne passe par la plateforme : ces montants s'affichent, et l'athlète
          les voit avant de vous contacter.
        </p>

        @if (p.offers.length) {
          <ul class="cp-list">
            @for (o of p.offers; track o.id) {
              <li class="cp-item" [class.cp-item--off]="!o.active">
                <div>
                  <strong>{{ o.name }}</strong>
                  @if (!o.active) { <span class="badge badge-neutral">Retirée</span> }
                  @if (o.description) { <p class="cp-item__desc">{{ o.description }}</p> }
                </div>
                <span class="cp-price">{{ price(o) }}</span>
                @if (o.active && !frozen()) {
                  <button type="button" class="btn btn-ghost btn-sm" (click)="removeOffer(o)">Retirer</button>
                }
              </li>
            }
          </ul>
        }

        @if (!frozen()) {
          <form class="cp-add" [formGroup]="offerForm" (ngSubmit)="addOffer()">
            <input type="text" class="form-control" formControlName="name" placeholder="Suivi mensuel" />
            <input type="number" class="form-control cp-amount" formControlName="amountEuros"
                   min="0" step="1" placeholder="90" aria-label="Montant en euros" />
            <select class="form-control" formControlName="periodicity" aria-label="Périodicité">
              @for (per of vocabulary()?.periodicities ?? []; track per.value) {
                <option [value]="per.value">{{ per.label }}</option>
              }
            </select>
            <button type="submit" class="btn btn-secondary btn-sm" [disabled]="offerForm.invalid">
              Ajouter
            </button>
          </form>
        }
      </section>

      <!-- Diplômes : déclaratifs, et l'écran le dit. La plateforme ne se porte pas garante de ce
           qu'elle n'a pas vérifié auprès de l'organisme émetteur. -->
      <section class="card cp-block">
        <h2 class="cp-block__title">Mes diplômes et certifications</h2>
        <p class="field-hint">
          Ils s'affichent comme <strong>déclarés par vous</strong> : la plateforme ne les certifie
          pas. Tenez vos justificatifs à disposition, l'équipe peut vous les demander.
        </p>

        @if (p.certifications.length) {
          <ul class="cp-list">
            @for (c of p.certifications; track c.id) {
              <li class="cp-item">
                <div>
                  <strong>{{ c.label }}</strong>
                  <p class="cp-item__desc">
                    @if (c.organisation) { {{ c.organisation }} }
                    @if (c.obtainedYear) { · {{ c.obtainedYear }} }
                  </p>
                </div>
                @if (!frozen()) {
                  <button type="button" class="btn btn-ghost btn-sm" (click)="removeCertification(c.id)">
                    Retirer
                  </button>
                }
              </li>
            }
          </ul>
        }

        @if (!frozen()) {
          <form class="cp-add" [formGroup]="certForm" (ngSubmit)="addCertification()">
            <input type="text" class="form-control" formControlName="label" placeholder="BPJEPS Athlétisme" />
            <input type="text" class="form-control" formControlName="organisation" placeholder="FFA" />
            <input type="number" class="form-control cp-year" formControlName="obtainedYear"
                   min="1950" max="2100" placeholder="2018" aria-label="Année d'obtention" />
            <button type="submit" class="btn btn-secondary btn-sm" [disabled]="certForm.invalid">
              Ajouter
            </button>
          </form>
        }
      </section>

      <!-- La publication. La liste des manques vient du serveur : c'est lui qui refuserait. -->
      <section class="card cp-publish">
        @if (p.status === 'PUBLISHED' || p.status === 'CLOSED') {
          <div>
            <strong>Votre fiche est publiée.</strong>
            <p class="field-hint">{{ visibilityHint(p.status) }}</p>
          </div>
          <button type="button" class="btn btn-secondary"
                  [disabled]="busy()" (click)="toggleAccepting(p.status !== 'PUBLISHED')">
            {{ acceptingLabel(p.status) }}
          </button>
        } @else if (p.status !== 'PENDING' && p.status !== 'SUSPENDED') {
          <div>
            @if (p.missing.length) {
              <strong>Il reste à compléter :</strong>
              <ul class="cp-missing">
                @for (m of p.missing; track m) { <li>{{ m }}</li> }
              </ul>
            } @else {
              <strong>Votre fiche est prête.</strong>
              <p class="field-hint">Elle sera relue à la main avant d'apparaître dans l'annuaire.</p>
            }
          </div>
          <button type="button" class="btn btn-primary"
                  [disabled]="busy() || p.missing.length > 0" (click)="submit()">
            Envoyer à la validation
          </button>
        }
      </section>
    } @else if (loading()) {
      <p class="cp-loading">Chargement…</p>
    }
  `,
  styles: [`
    .page-head { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--sp-4); flex-wrap: wrap; margin-bottom: var(--sp-5); }
    .cp-loading { color: var(--ink-3); padding: var(--sp-6) 0; }
    .cp-note { display: flex; gap: var(--sp-3); padding: var(--sp-4); margin-bottom: var(--sp-4); align-items: flex-start; }
    .cp-note p { margin: var(--sp-1) 0 0; color: var(--ink-2); font-size: var(--text-sm); }
    .cp-note--danger { border-color: var(--danger-bg); }
    .cp-form { padding: var(--sp-5); margin-bottom: var(--sp-4); }
    .cp-form fieldset { border: 0; padding: 0; margin: 0; }
    .cp-form fieldset[disabled] { opacity: 0.6; }
    .cp-legend { display: block; font-size: var(--text-sm); font-weight: 600; color: var(--ink-2); margin-bottom: var(--sp-2); }
    .cp-chips { display: flex; flex-wrap: wrap; gap: var(--sp-2); }
    .cp-chip { padding: var(--sp-2) var(--sp-3); border: 1px solid var(--hairline); border-radius: var(--radius-full); background: var(--paper); color: var(--ink-2); font-size: var(--text-sm); cursor: pointer; min-height: 36px; }
    .cp-chip:hover { border-color: var(--primary-light); }
    .cp-chip.selected { border-color: var(--primary); background: var(--primary-wash); color: var(--primary); font-weight: 600; }
    .cp-chip:focus-visible { outline: 2px solid var(--primary); outline-offset: 2px; }
    .cp-row { display: grid; grid-template-columns: 1fr 1fr; gap: var(--sp-4); }
    @media (max-width: 640px) { .cp-row { grid-template-columns: 1fr; } }
    .cp-block { padding: var(--sp-5); margin-bottom: var(--sp-4); }
    .cp-photo { display: flex; gap: var(--sp-5); align-items: flex-start; }
    @media (max-width: 560px) { .cp-photo { flex-direction: column; } }
    .cp-photo__preview { flex: 0 0 auto; width: 128px; height: 128px; border-radius: var(--radius-lg); overflow: hidden; background: var(--paper-sunk); display: flex; align-items: center; justify-content: center; }
    .cp-photo__preview img { width: 100%; height: 100%; object-fit: cover; }
    .cp-photo__empty { color: var(--ink-4); }
    .cp-photo__side { flex: 1; min-width: 0; }
    .cp-photo__actions { display: flex; gap: var(--sp-2); margin-top: var(--sp-3); flex-wrap: wrap; }
    .cp-photo__actions label { cursor: pointer; }
    .cp-block__title { font-size: var(--text-lg); margin: 0 0 var(--sp-1); }
    .cp-list { list-style: none; margin: var(--sp-4) 0; padding: 0; display: flex; flex-direction: column; gap: var(--sp-2); }
    .cp-item { display: flex; align-items: center; gap: var(--sp-3); padding: var(--sp-3); border: 1px solid var(--hairline); border-radius: var(--radius); }
    .cp-item--off { opacity: 0.55; }
    .cp-item > div { flex: 1; min-width: 0; }
    .cp-item__desc { margin: 2px 0 0; font-size: var(--text-sm); color: var(--ink-3); }
    .cp-price { font-family: var(--font-mono); font-size: var(--text-sm); white-space: nowrap; }
    .cp-add { display: flex; gap: var(--sp-2); flex-wrap: wrap; align-items: center; }
    .cp-add .form-control { flex: 1; min-width: 120px; }
    .cp-amount, .cp-year { max-width: 110px; flex: 0 0 auto; }
    .cp-publish { display: flex; align-items: center; justify-content: space-between; gap: var(--sp-4); padding: var(--sp-5); flex-wrap: wrap; }
    .cp-missing { margin: var(--sp-2) 0 0; padding-left: var(--sp-5); color: var(--ink-2); font-size: var(--text-sm); }
  `],
})
export class CoachProfileComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(CoachProfileService);
  private readonly toast = inject(ToastService);
  private readonly confirm = inject(ConfirmService);

  readonly profile = signal<CoachProfile | null>(null);
  readonly vocabulary = signal<Vocabulary | null>(null);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly busy = signal(false);
  readonly uploading = signal(false);

  /** L'adresse de la photo courante, composée par le service à partir du chemin de l'API. */
  readonly photoSrc = computed(() => this.service.photoSrc(this.profile()?.photoUrl ?? null));

  /** Une fiche en validation est gelée côté serveur : l'écran le montre plutôt que d'échouer. */
  readonly frozen = computed(() => {
    const s = this.profile()?.status;
    return s === 'PENDING' || s === 'SUSPENDED';
  });

  readonly statusBadge = computed(() => {
    switch (this.profile()?.status) {
      case 'PUBLISHED': return 'badge-success';
      case 'PENDING': return 'badge-warning';
      case 'SUSPENDED': return 'badge-danger';
      default: return 'badge-neutral';
    }
  });

  readonly bioLength = computed(() => (this.form.controls.bio.value ?? '').length);

  /**
   * Les langues sont volontairement une courte liste plutôt qu'un champ libre : ce sont des
   * facettes de l'annuaire, et « anglais » saisi à la main ne rencontrerait jamais « en ».
   */
  readonly languageOptions = [
    { value: 'fr', label: 'Français' },
    { value: 'en', label: 'Anglais' },
    { value: 'es', label: 'Espagnol' },
    { value: 'de', label: 'Allemand' },
    { value: 'it', label: 'Italien' },
    { value: 'pt', label: 'Portugais' },
  ];

  readonly form = this.fb.nonNullable.group({
    headline: [''],
    bio: [''],
    city: [''],
    remote: [true],
    inPerson: [false],
    experienceYears: [null as number | null],
    capacityMax: [null as number | null],
  });

  readonly offerForm = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(120)]],
    amountEuros: [null as number | null, [Validators.required, Validators.min(0)]],
    periodicity: ['MONTHLY' as OfferPeriodicity, Validators.required],
  });

  readonly certForm = this.fb.nonNullable.group({
    label: ['', [Validators.required, Validators.maxLength(200)]],
    organisation: [''],
    obtainedYear: [null as number | null],
  });

  /** Les sélections multiples vivent hors du formulaire réactif : ce sont des ensembles. */
  private readonly sets = signal<Record<string, Set<string>>>({
    disciplines: new Set(),
    specialties: new Set(),
    levels: new Set(),
    languages: new Set(),
  });

  ngOnInit(): void {
    this.service.vocabulary().subscribe({ next: (v) => this.vocabulary.set(v) });
    this.reload(true);
  }

  has(group: string, value: string): boolean {
    return this.sets()[group]?.has(value) ?? false;
  }

  toggle(group: string, value: string): void {
    if (this.frozen()) return;
    const next = { ...this.sets() };
    const set = new Set(next[group]);
    if (set.has(value)) {
      set.delete(value);
    } else {
      set.add(value);
    }
    next[group] = set;
    this.sets.set(next);
  }

  save(): void {
    this.saving.set(true);
    const v = this.form.getRawValue();
    const s = this.sets();
    this.service.save({
      headline: v.headline || null,
      bio: v.bio || null,
      city: v.city || null,
      country: v.city ? 'FR' : null,
      remote: v.remote,
      inPerson: v.inPerson,
      experienceYears: v.experienceYears,
      capacityMax: v.capacityMax,
      disciplines: [...s['disciplines']],
      specialties: [...s['specialties']],
      levels: [...s['levels']],
      languages: [...s['languages']],
    }).subscribe({
      next: (p) => {
        this.apply(p);
        this.saving.set(false);
        this.toast.success('Fiche enregistrée');
      },
      error: () => {
        this.saving.set(false);
        this.toast.error("La fiche n'a pas pu être enregistrée");
      },
    });
  }

  submit(): void {
    this.busy.set(true);
    this.service.submit().subscribe({
      next: (p) => {
        this.apply(p);
        this.busy.set(false);
        this.toast.success('Fiche envoyée à la validation');
      },
      error: (err) => {
        this.busy.set(false);
        this.toast.error(err?.error?.message ?? "La fiche n'a pas pu être envoyée");
      },
    });
  }

  toggleAccepting(accepting: boolean): void {
    this.busy.set(true);
    this.service.setAccepting(accepting).subscribe({
      next: (p) => {
        this.apply(p);
        this.busy.set(false);
      },
      error: () => {
        this.busy.set(false);
        this.toast.error("Le changement n'a pas pu être enregistré");
      },
    });
  }

  addOffer(): void {
    const v = this.offerForm.getRawValue();
    this.service.addOffer({
      name: v.name,
      description: null,
      // Saisi en euros, stocké en centimes : un flottant sur de la monnaie finit par afficher
      // 89,99999 € à quelqu'un.
      amountCents: Math.round((v.amountEuros ?? 0) * 100),
      periodicity: v.periodicity,
      active: true,
      position: this.profile()?.offers.length ?? 0,
    }).subscribe({
      next: () => {
        this.offerForm.reset({ name: '', amountEuros: null, periodicity: 'MONTHLY' });
        this.reload(false);
      },
      error: () => this.toast.error("La formule n'a pas pu être ajoutée"),
    });
  }

  async removeOffer(offer: CoachOffer): Promise<void> {
    const ok = await this.confirm.ask({
      title: 'Retirer cette formule ?',
      message: `« ${offer.name} » disparaîtra de votre fiche. Elle reste lisible dans les accords `
        + `déjà passés sur cette base.`,
      confirmLabel: 'Retirer',
    });
    if (!ok) {
      return;
    }
    this.service.deactivateOffer(offer.id).subscribe({
      next: () => this.reload(false),
      error: () => this.toast.error("La formule n'a pas pu être retirée"),
    });
  }

  addCertification(): void {
    const v = this.certForm.getRawValue();
    this.service.addCertification({
      label: v.label,
      organisation: v.organisation || null,
      obtainedYear: v.obtainedYear,
    }).subscribe({
      next: () => {
        this.certForm.reset({ label: '', organisation: '', obtainedYear: null });
        this.reload(false);
      },
      error: () => this.toast.error("Le diplôme n'a pas pu être ajouté"),
    });
  }

  removeCertification(id: string): void {
    this.service.deleteCertification(id).subscribe({
      next: () => this.reload(false),
      error: () => this.toast.error("Le diplôme n'a pas pu être retiré"),
    });
  }

  /**
   * Envoie la photo choisie.
   *
   * <p>Le champ est vidé après coup : sans cela, rechoisir le même fichier — après un recadrage,
   * par exemple — ne déclencherait aucun événement, le navigateur considérant que la valeur n'a
   * pas changé.</p>
   */
  onPhotoPicked(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) {
      return;
    }
    this.uploading.set(true);
    this.service.uploadPhoto(file).subscribe({
      next: (p) => {
        this.apply(p);
        this.uploading.set(false);
        this.toast.success('Photo enregistrée');
      },
      error: (err) => {
        this.uploading.set(false);
        // Le serveur explique pourquoi il refuse (format, poids, dimensions) : c'est ce message
        // qui dit quoi faire, là où « échec de l'envoi » laisserait chercher.
        this.toast.error(err?.error?.message ?? "La photo n'a pas pu être envoyée");
      },
    });
  }

  async removePhoto(): Promise<void> {
    const ok = await this.confirm.ask({
      title: 'Retirer votre photo ?',
      message: 'Votre fiche affichera un portrait vide tant que vous n\'en aurez pas remis une.',
      confirmLabel: 'Retirer',
    });
    if (!ok) {
      return;
    }
    this.uploading.set(true);
    this.service.deletePhoto().subscribe({
      next: (p) => {
        this.apply(p);
        this.uploading.set(false);
      },
      error: () => {
        this.uploading.set(false);
        this.toast.error("La photo n'a pas pu être retirée");
      },
    });
  }

  /** Ce que la publication signifie, dit en une phrase plutôt qu'en un statut. */
  visibilityHint(status: CoachProfileStatus): string {
    return status === 'PUBLISHED'
      ? "Elle apparaît dans l'annuaire et accepte les demandes."
      : "Elle reste consultable, mais n'accepte plus de nouvelle demande.";
  }

  acceptingLabel(status: CoachProfileStatus): string {
    return status === 'PUBLISHED' ? "Ne plus prendre d'athlètes" : 'Reprendre des athlètes';
  }

  price(o: CoachOffer): string {
    return `${(o.amountCents / 100).toLocaleString('fr-FR')} € ${o.suffix}`;
  }

  private reload(first: boolean): void {
    this.service.get().subscribe({
      next: (p) => {
        this.apply(p);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        if (first) this.toast.error("La fiche n'a pas pu être chargée");
      },
    });
  }

  private apply(p: CoachProfile): void {
    this.profile.set(p);
    this.form.patchValue({
      headline: p.headline ?? '',
      bio: p.bio ?? '',
      city: p.city ?? '',
      remote: p.remote,
      inPerson: p.inPerson,
      experienceYears: p.experienceYears,
      capacityMax: p.capacityMax,
    });
    this.sets.set({
      disciplines: new Set(p.disciplines),
      specialties: new Set(p.specialties),
      levels: new Set(p.levels),
      languages: new Set(p.languages),
    });
  }
}
