import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import {
  AdminUserDetail,
  ClubAdmin,
  ROLE_LABELS,
  USER_STATUS_LABELS,
  UserStatus,
  userStatusBadge,
} from '../../core/models/admin.model';
import { UserRole } from '../../core/models/user.model';
import { AdminService } from '../../core/services/admin.service';
import { AuthService } from '../../core/services/auth.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { ToastService } from '../../core/services/toast.service';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { SkeletonComponent } from '../../shared/components/skeleton/skeleton.component';

/**
 * Fiche d'un compte : tout ce qu'un ticket de support demande, et les gestes qui le résolvent.
 *
 * <p><b>Ce qu'elle remplace.</b> Rien — il n'y avait aucune fiche. On modifiait un compte depuis
 * une ligne de tableau, sans voir si son adresse était vérifiée, quand il s'était connecté, à
 * quels clubs il était rattaché ni s'il avait un appareil abonné. Les gestes courants du support
 * — renvoyer un lien de vérification, réinitialiser un mot de passe, fermer les sessions,
 * rattacher un second club — n'existaient pas dans le produit : ils passaient par la base.</p>
 *
 * <p><b>Les actions sensibles se confirment, et laissent une trace.</b> Suspension, suppression et
 * impersonation passent par {@code ConfirmService} ; le serveur les consigne toutes au journal
 * d'audit, y compris quand elles échouent sur un garde-fou.</p>
 */
@Component({
  selector: 'app-admin-user-detail',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink, IconComponent, SkeletonComponent, DatePipe],
  templateUrl: './admin-user-detail.component.html',
  styleUrl: './admin-user-detail.component.scss',
})
export class AdminUserDetailComponent implements OnInit {
  private readonly admin = inject(AdminService);
  private readonly auth = inject(AuthService);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);

  /** Lié depuis la route (`withComponentInputBinding`). */
  readonly id = input.required<string>();

  readonly user = signal<AdminUserDetail | null>(null);
  readonly clubs = signal<ClubAdmin[]>([]);
  readonly loading = signal(true);
  readonly failed = signal(false);
  readonly busy = signal(false);

  readonly roleLabels = ROLE_LABELS;
  readonly statusLabels = USER_STATUS_LABELS;
  readonly statusBadge = userStatusBadge;
  readonly roles: UserRole[] = ['PLATFORM_ADMIN', 'HEAD_COACH', 'COACH', 'ATHLETE'];

  /** Brouillon d'édition, séparé de l'affichage : on n'enregistre que ce qui a été touché. */
  draft = { fullName: '', role: 'COACH' as UserRole, clubId: '' };
  clubToAdd = '';

  /** Le compte de l'administrateur connecté : la plupart des actions ne s'y appliquent pas. */
  readonly isSelf = computed(() => this.auth.currentUser()?.id === this.user()?.id);

  /**
   * Un compte d'administration ne s'emprunte pas : l'impersonation sert à voir l'application
   * comme un coach ou un athlète, pas à agir sous l'identité d'un pair. Le serveur le refuse
   * aussi — ce test n'est là que pour ne pas proposer un bouton qui échouera.
   */
  readonly canImpersonate = computed(() => {
    const u = this.user();
    return !!u && u.role !== 'PLATFORM_ADMIN' && !this.isSelf();
  });

  readonly clubsAvailable = computed(() => {
    const u = this.user();
    if (!u) return [];
    const taken = new Set([u.clubId, ...u.additionalClubs.map((c) => c.id)].filter(Boolean));
    return this.clubs().filter((c) => !taken.has(c.id));
  });

  ngOnInit(): void {
    this.load();
    this.admin.clubs(undefined, 0, undefined, 200).subscribe({
      next: (p) => this.clubs.set(p.content),
      error: () => this.clubs.set([]),
    });
  }

  load(): void {
    this.loading.set(true);
    this.failed.set(false);
    this.admin.userDetail(this.id()).subscribe({
      next: (u) => {
        this.user.set(u);
        this.draft = { fullName: u.fullName, role: u.role, clubId: u.clubId ?? '' };
        this.loading.set(false);
      },
      error: () => {
        this.failed.set(true);
        this.loading.set(false);
      },
    });
  }

  needsClub(role: UserRole): boolean {
    return role === 'HEAD_COACH' || role === 'COACH';
  }

  save(): void {
    const u = this.user();
    if (!u) return;
    this.busy.set(true);
    this.admin
      .updateUser(u.id, {
        fullName: this.draft.fullName,
        role: this.draft.role,
        clubId: this.draft.clubId || null,
      })
      .subscribe({
        next: () => {
          this.toast.success('Compte mis à jour.');
          this.busy.set(false);
          this.load();
        },
        error: () => this.busy.set(false),
      });
  }

  async suspend(): Promise<void> {
    const u = this.user();
    if (!u) return;
    const reason = await this.confirm.prompt({
      title: `Suspendre ${u.fullName || u.email} ?`,
      message:
        'Le compte ne pourra plus se connecter, et ses sessions ouvertes sont fermées '
        + 'immédiatement. Ses données restent intactes et la réactivation est possible à tout '
        + 'moment.',
      confirmLabel: 'Suspendre',
      danger: true,
      promptLabel: 'Motif (facultatif, consigné au journal)',
    });
    if (reason === null) return;

    this.run(this.admin.suspendUser(u.id, reason || undefined), 'Compte suspendu, sessions fermées.');
  }

  reactivate(): void {
    const u = this.user();
    if (!u) return;
    this.run(this.admin.reactivateUser(u.id), 'Compte réactivé.');
  }

  async revokeSessions(): Promise<void> {
    const u = this.user();
    if (!u) return;
    const ok = await this.confirm.ask({
      title: 'Fermer toutes les sessions',
      message:
        `${u.fullName || u.email} sera déconnecté de tous ses appareils et devra se reconnecter. `
        + 'Le compte reste actif.',
      confirmLabel: 'Fermer les sessions',
    });
    if (!ok) return;
    this.run(this.admin.revokeSessions(u.id), 'Sessions fermées.');
  }

  async sendPasswordReset(): Promise<void> {
    const u = this.user();
    if (!u) return;
    const ok = await this.confirm.ask({
      title: 'Envoyer un lien de réinitialisation',
      message:
        `Un lien valable 2 heures partira à ${u.email}. Tu ne verras pas le nouveau mot de `
        + "passe : seul son titulaire peut s'en servir.",
      confirmLabel: 'Envoyer',
    });
    if (!ok) return;
    this.busy.set(true);
    this.admin.sendPasswordReset(u.id).subscribe({
      next: () => {
        this.toast.success('Lien de réinitialisation envoyé.');
        this.busy.set(false);
        this.load();
      },
      error: () => this.busy.set(false),
    });
  }

  resendVerification(): void {
    const u = this.user();
    if (!u) return;
    this.busy.set(true);
    this.admin.resendVerification(u.id).subscribe({
      next: () => {
        this.toast.success('E-mail de vérification renvoyé.');
        this.busy.set(false);
        this.load();
      },
      error: () => this.busy.set(false),
    });
  }

  addClub(): void {
    const u = this.user();
    if (!u || !this.clubToAdd) return;
    this.busy.set(true);
    this.admin.addUserClub(u.id, this.clubToAdd).subscribe({
      next: () => {
        this.toast.success('Club rattaché.');
        this.clubToAdd = '';
        this.busy.set(false);
        this.load();
      },
      error: () => this.busy.set(false),
    });
  }

  async removeClub(clubId: string, name: string): Promise<void> {
    const u = this.user();
    if (!u) return;
    const ok = await this.confirm.ask({
      title: 'Détacher le club',
      message: `${u.fullName || u.email} n'aura plus accès aux athlètes de « ${name} ».`,
      confirmLabel: 'Détacher',
      danger: true,
    });
    if (!ok) return;
    this.run(this.admin.removeUserClub(u.id, clubId), 'Club détaché.');
  }

  async impersonate(): Promise<void> {
    const u = this.user();
    if (!u) return;
    const ok = await this.confirm.ask({
      title: `Voir l'application en tant que ${u.fullName || u.email} ?`,
      message:
        'Ta session reste ouverte et te sera rendue. Tout ce que tu feras pendant ce temps sera '
        + "enregistré au nom de cette personne, sans distinction : c'est un outil de lecture. "
        + "L'ouverture est consignée au journal d'audit.",
      confirmLabel: 'Voir en tant que',
    });
    if (!ok || this.busy()) return;

    this.busy.set(true);
    this.admin.impersonate(u.id).subscribe({
      next: (res) => {
        this.busy.set(false);
        this.auth.startImpersonation(res);
        void this.router.navigateByUrl(this.auth.homeRoute());
      },
      // Le message du serveur est déjà affiché par l'intercepteur : il nomme la raison du refus.
      error: () => this.busy.set(false),
    });
  }

  async remove(): Promise<void> {
    const u = this.user();
    if (!u) return;
    const ok = await this.confirm.askForText({
      title: 'Supprimer le compte',
      message:
        `Supprimer ${u.email} efface en cascade son profil, ses séances, ses sorties importées, `
        + "ses ressentis et ses messages. Cet historique n'a pas de sauvegarde côté utilisateur "
        + "et ne se reconstitue pas. Une suspension ferme l'accès sans rien détruire.",
      confirmLabel: 'Supprimer définitivement',
      danger: true,
      requiredText: 'SUPPRIMER',
    });
    if (!ok) return;

    this.busy.set(true);
    this.admin.deleteUser(u.id).subscribe({
      next: () => {
        this.toast.success('Compte supprimé.');
        void this.router.navigate(['/admin/users']);
      },
      error: () => this.busy.set(false),
    });
  }

  /** Enchaînement commun : appel, toast, rechargement de la fiche. */
  private run(call: import('rxjs').Observable<unknown>, message: string): void {
    this.busy.set(true);
    call.subscribe({
      next: () => {
        this.toast.success(message);
        this.busy.set(false);
        this.load();
      },
      error: () => this.busy.set(false),
    });
  }
}
