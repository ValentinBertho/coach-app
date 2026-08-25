import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AdminPlatform, PlatformSetting } from '../../core/models/admin.model';
import { AdminService } from '../../core/services/admin.service';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { SkeletonComponent } from '../../shared/components/skeleton/skeleton.component';

/**
 * Configuration de l'instance, en lecture seule.
 *
 * <p><b>La question à laquelle cet écran répond</b> est celle qui suit tout incident : « est-ce
 * que c'est configuré <i>ici</i>, sur cette instance ? ». La seule façon d'y répondre était
 * d'ouvrir la console d'hébergement — donc de sortir du produit, et d'avoir les droits pour.</p>
 *
 * <p><b>Aucune valeur de secret n'est affichée</b>, ni ici ni dans la réponse du serveur : on
 * montre qu'un réglage est posé, jamais ce qu'il contient. La variable d'environnement est
 * nommée pour savoir où agir ; sa valeur reste où elle doit être.</p>
 */
@Component({
  selector: 'app-admin-platform',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [SkeletonComponent, IconComponent, RouterLink, DecimalPipe],
  templateUrl: './admin-platform.component.html',
  styleUrl: './admin-platform.component.scss',
})
export class AdminPlatformComponent implements OnInit {
  private readonly admin = inject(AdminService);

  readonly platform = signal<AdminPlatform | null>(null);
  readonly loading = signal(true);
  readonly failed = signal(false);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.failed.set(false);
    this.admin.platform().subscribe({
      next: (p) => {
        this.platform.set(p);
        this.loading.set(false);
      },
      error: () => {
        this.failed.set(true);
        this.loading.set(false);
      },
    });
  }

  badge(s: PlatformSetting): string {
    return s.state === 'ON' ? 'badge-success' : s.state === 'PARTIAL' ? 'badge-warning' : 'badge-neutral';
  }

  /**
   * Le serveur nomme lui-même l'état de chaque réglage. Le repli couvre un front encore en
   * cache face à une réponse plus ancienne — le champ a été ajouté, pas substitué.
   */
  stateLabel(s: PlatformSetting): string {
    return s.stateLabel ?? (s.state === 'ON' ? 'Actif' : s.state === 'PARTIAL' ? 'Incomplet' : 'Inactif');
  }

  registrationLabel(mode: string): string {
    return mode?.toLowerCase() === 'invite' ? 'Sur code d’invitation' : 'Libre';
  }
}
