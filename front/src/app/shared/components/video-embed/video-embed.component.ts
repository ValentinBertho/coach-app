import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { IconComponent } from '../icon/icon.component';
import { parseVideoUrl } from '../../../core/utils/video-embed';

/**
 * Démonstration vidéo d'un exercice, lisible sans quitter l'application.
 *
 * <h2>Pourquoi une vignette avant la vidéo</h2>
 *
 * <p>Le lecteur n'est pas chargé au rendu : tant que personne ne clique, <b>aucune requête ne
 * part vers l'hébergeur</b>. Une séance de renforcement affiche cinq à huit exercices ; poser huit
 * iframes YouTube sur l'écran d'un athlète, c'est huit connexions à Google — et son adresse IP
 * avec — pour des vidéos qu'il ne regardera pas. C'est aussi ce qui garde la fiche rapide à
 * ouvrir en salle, sur un réseau qui n'est jamais bon.</p>
 *
 * <p>Le clic remplace la vignette par l'iframe, sur le domaine sans cookie. Un lien « ouvrir »
 * reste disponible : le plein écran d'une application native reste meilleur que le nôtre.</p>
 *
 * <p>Une URL non reconnue (Drive, Instagram, fichier perso) n'est jamais encadrée : elle
 * redevient le lien externe qu'elle était. Voir {@link parseVideoUrl}.</p>
 */
@Component({
  selector: 'app-video-embed',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  templateUrl: './video-embed.component.html',
  styleUrl: './video-embed.component.scss',
})
export class VideoEmbedComponent {
  /** Lien saisi par le coach sur l'exercice. Vide ou non reconnu : rien à intégrer. */
  readonly url = input<string | null>(null);
  /** Nom de l'exercice — sert de titre accessible à l'iframe et de libellé de la vignette. */
  readonly title = input('');
  /** Rendu compact (listes, sélecteur d'exercice) : la vignette se réduit à une ligne. */
  readonly compact = input(false);

  private readonly sanitizer = inject(DomSanitizer);

  readonly video = computed(() => parseVideoUrl(this.url()));
  /** Lien non reconnu mais non vide : on ne perd pas l'information, on ne l'encadre pas. */
  readonly externalOnly = computed(() => !this.video() && !!this.url()?.trim());
  readonly playing = signal(false);

  /**
   * Source de l'iframe.
   *
   * <p>Le contournement du nettoyeur d'Angular ne porte pas sur l'URL du coach : il porte sur
   * celle que {@link parseVideoUrl} a <b>reconstruite</b> à partir d'un identifiant validé
   * caractère par caractère. Sans cette reconstruction, ce serait une faille.</p>
   */
  readonly safeSrc = computed<SafeResourceUrl | null>(() => {
    const v = this.video();
    return v ? this.sanitizer.bypassSecurityTrustResourceUrl(v.embedUrl) : null;
  });

  play(): void { this.playing.set(true); }
}
