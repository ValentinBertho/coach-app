import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { categoryOptions } from '../models/session-category.model';
import { ConfirmChoice, ConfirmService } from './confirm.service';
import { CourseService } from './course.service';
import { SessionCategoryService } from './session-category.service';
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
 * <p>L'éditeur de structure garde son propre panneau : il enregistre la structure en cours avant
 * de la verser. C'est un geste plus riche, pas le même.</p>
 */
@Injectable({ providedIn: 'root' })
export class SaveToLibraryService {
  private readonly confirm = inject(ConfirmService);
  private readonly courseService = inject(CourseService);
  private readonly categories = inject(SessionCategoryService);
  private readonly toast = inject(ToastService);

  /**
   * Demande un nom — et, si le club en a défini, une catégorie de rangement — puis verse la séance
   * comme nouveau modèle.
   *
   * <p>La catégorie reste facultative : le choix vide est présélectionné. Une bibliothèque se
   * range quand elle grossit, pas au moment où l'on veut seulement ne pas perdre son travail ;
   * mais la reclasser plus tard suppose de la retrouver, alors on offre le rangement tout de
   * suite, sans l'exiger.</p>
   *
   * @returns le nom retenu si le modèle a été créé, `null` si l'utilisateur a renoncé.
   */
  async promptAndSave(workout: { id: string; athleteId: string; title: string | null }): Promise<string | null> {
    const choices = await this.loadChoices();
    const common = {
      title: 'Enregistrer dans la bibliothèque',
      message: "La structure est recopiée comme nouveau modèle ; la séance de l'athlète n'est pas "
        + 'modifiée. Les consignes écrites pour lui restent sur sa séance — un modèle resservira à '
        + "d'autres.",
      promptLabel: 'Nom du modèle',
      initialValue: workout.title ?? '',
      confirmLabel: 'Ajouter à la bibliothèque',
    };

    // Sans catégorie définie dans le club, la liste n'aurait qu'un choix vide : on la tait plutôt
    // que d'ajouter un champ qui ne décide de rien.
    const answer = choices.length
      ? await this.confirm.promptWithChoice({
        ...common,
        selectLabel: 'Catégorie (facultatif)',
        selectOptions: choices,
        selectEmptyLabel: 'Sans catégorie',
      })
      : await this.confirm.prompt(common).then((text) => (text ? { text, choice: null } : null));
    if (!answer?.text) return null;

    const { text: title, choice: categoryId } = answer;
    return new Promise<string | null>((resolve) => {
      this.courseService.saveWorkoutAsTemplate(workout.athleteId, workout.id, { title, categoryId }).subscribe({
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

  /**
   * Catégories du domaine course, aplaties pour un menu déroulant.
   *
   * <p>Un échec de chargement ne bloque pas l'enregistrement : on retombe sur l'invite simple.
   * Perdre le rangement est un moindre mal ; perdre la séance qu'on voulait garder, non.</p>
   */
  private async loadChoices(): Promise<ConfirmChoice[]> {
    try {
      const list = await firstValueFrom(this.categories.list('COURSE'));
      return categoryOptions(list).map((o) => ({ value: o.category.id, label: o.label }));
    } catch {
      return [];
    }
  }
}
