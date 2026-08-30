import { TestBed } from '@angular/core/testing';
import { ComponentRef } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { importProvidersFrom } from '@angular/core';
import { LucideAngularModule } from 'lucide-angular';
import { ICONS } from '../../app.config';
import { TrainingZone } from '../../core/models/training-zone.model';
import { TrainingZonesComponent } from './training-zones.component';

/**
 * Arriver d'une fiche athlète pour régler UNE zone.
 *
 * <p>La règle appartient au club : on ne l'édite donc pas depuis la fiche, où tout le reste ne
 * concerne qu'un athlète et où un champ de pourcentage laisserait croire qu'on n'ajuste que lui.
 * On mène à l'écran des zones — mais sur la bonne ligne, sinon le lien perd en route ce qu'il
 * portait.</p>
 */
describe('zones — arrivée depuis une fiche athlète', () => {
  let component: TrainingZonesComponent;
  let ref: ComponentRef<TrainingZonesComponent>;

  function zone(id: string, name: string): TrainingZone {
    return {
      id, name, color: null, description: null, sortOrder: 0,
      scope: 'CLUB', discipline: null, builtin: false, metricTypeIds: [], rules: [],
    };
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]), provideHttpClient(), provideHttpClientTesting(),
        importProvidersFrom(LucideAngularModule.pick(ICONS)),
      ],
    });
    const fixture = TestBed.createComponent(TrainingZonesComponent);
    component = fixture.componentInstance;
    ref = fixture.componentRef;
  });

  it('signale la zone visée', () => {
    ref.setInput('zone', 'z2');
    component.zones.set([zone('z1', 'EF'), zone('z2', 'Tempo')]);
    component['revealRequestedZone']();
    expect(component.highlighted()).toBe('z2');
  });

  it('ne signale rien sans zone visée', () => {
    component.zones.set([zone('z1', 'EF')]);
    component['revealRequestedZone']();
    expect(component.highlighted()).toBeNull();
  });

  it('ne signale rien si la zone n’est pas dans le modèle affiché', () => {
    // L'athlète peut travailler sur un autre modèle : mieux vaut ne rien désigner que désigner
    // une ligne au hasard.
    ref.setInput('zone', 'absente');
    component.zones.set([zone('z1', 'EF')]);
    component['revealRequestedZone']();
    expect(component.highlighted()).toBeNull();
  });
});
