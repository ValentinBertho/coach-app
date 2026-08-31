import { Injectable, inject } from '@angular/core';
import { ConfirmService } from './confirm.service';
import { CourseService } from './course.service';
import { ToastService } from './toast.service';

/**
 * Verser une séance du calendrier dans la bibliothèque.
 *
 * <p>Le même geste est proposé depuis deux endroits — le menu contextuel d'une séance sur le
 * calendrier, et la fiche de la séance — parce qu'on décide de garder une séance aux deux moments :
 * en la posant, et en la relisant. Deux copies du geste auraient divergé au premier ajustement de
 * formulation ou de gestion d'erreur, et l'un des deux chemins aurait fini par mentir sur ce qu'il
 * fait.</p>
 *
 * <p>L'éditeur de structure garde son propre panneau : il propose en plus une catégorie de
 * rangement et enregistre la structure en cours avant de la verser. C'est un geste plus riche,
 * pas le même.</p>
 */
@Injectable({ providedIn: 'root' })
export class SaveToLibraryService {
  private readonly confirm = inject(ConfirmService);
  private readonly courseService = inject(CourseService);
  private readonly toast = inject(ToastService);

  /**
   * Demande un nom, puis verse la séance comme nouveau modèle.
   *
   * <p>Sans catégorie : la ranger demanderait un second choix au moment où l'on veut seulement ne
   * pas perdre son travail. La bibliothèque permet de la classer ensuite.</p>
   *
   * @returns le nom retenu si le modèle a été créé, `null` si l'utilisateur a renoncé.
   */
  async promptAndSave(workout: { id: string; athleteId: string; title: string | null }): Promise<string | null> {
    const title = await this.confirm.prompt({
      title: 'Enregistrer dans la bibliothèque',
      message: "La structure est recopiée comme nouveau modèle ; la séance de l'athlète n'est pas "
        + 'modifiée. Les consignes écrites pour lui restent sur sa séance — un modèle resservira à '
        + "d'autres.",
      promptLabel: 'Nom du modèle',
      initialValue: workout.title ?? '',
      confirmLabel: 'Ajouter à la bibliothèque',
    });
    if (!title) return null;

    return new Promise<string | null>((resolve) => {
      this.courseService.saveWorkoutAsTemplate(workout.athleteId, workout.id, { title }).subscribe({
        next: () => {
          this.toast.success('« ' + title + ' » ajoutée à ta bibliothèque');
          resolve(title);
        },
        error: () => {
          this.toast.error('Enregistrement impossible.');
          resolve(null);
        },
      });
    });
  }
}
