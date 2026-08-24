import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component, OnInit, input } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Route, Routes, RouterOutlet, provideRouter, withComponentInputBinding, withRouterConfig } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { importProvidersFrom } from '@angular/core';
import { LucideAngularModule } from 'lucide-angular';
import { ICONS } from '../../app.config';
import { routes } from '../../app.routes';
import { AthleteFormComponent } from './athlete-form.component';
import { provideAthleteContextReuse } from '../../core/routing/athlete-context-reuse.strategy';

/** Sections d'onglet ouvertes, dans l'ordre : la trace que laisse une (re)création. */
const opened: string[] = [];

/** Section d'onglet réduite à ce qui compte : elle charge ses données une fois, à l'ouverture. */
@Component({ standalone: true, template: 'section' })
class TabStub implements OnInit {
  readonly athleteId = input.required<string>();
  ngOnInit(): void { opened.push(this.athleteId()); }
}

/** Coquille de l'athlète : elle persiste d'un athlète à l'autre, c'est tout son intérêt. */
@Component({ standalone: true, imports: [RouterOutlet], template: '<router-outlet />' })
class ShellStub {
  readonly athleteId = input.required<string>();
}

/**
 * Le routage de la fiche athlète — deux pannes rencontrées le même jour par un coach pilote.
 *
 * <p><b>« Modifier » ouvrait « Nouvel athlète ».</b> La route déclare {@code :athleteId}, le
 * formulaire attendait une entrée nommée {@code id} : la liaison d'entrées de route n'avait rien
 * à poser, le formulaire se croyait en création — et enregistrer aurait créé un doublon au lieu
 * de modifier la fiche ouverte.</p>
 *
 * <p><b>Changer d'athlète ne changeait pas l'onglet.</b> Chaque section charge ses données dans
 * {@code ngOnInit}, et le routeur réutilise le composant quand seul un paramètre change : le
 * sélecteur du bandeau renommait l'en-tête et laissait dessous le programme, la charge ou les
 * zones de l'athlète précédent.</p>
 */
describe('routage de la fiche athlète', () => {

  describe('formulaire d’édition', () => {
    let http: HttpTestingController;

    /** Chemin réel déclaré dans l'application : le test suit la route, il ne la réinvente pas. */
    function editPath(): string {
      const app = routes.find((r) => r.path === 'app');
      const found = (app?.children ?? []).find((r: Route) => (r.path ?? '').endsWith('/edit'));
      expect(found).withContext('route d’édition absente de app.routes.ts').toBeDefined();
      return `app/${found!.path}`;
    }

    beforeEach(() => {
      TestBed.configureTestingModule({
        providers: [
          provideRouter([{ path: editPath(), component: AthleteFormComponent }],
            withComponentInputBinding()),
          provideHttpClient(),
          provideHttpClientTesting(),
          importProvidersFrom(LucideAngularModule.pick(ICONS)),
        ],
      });
      http = TestBed.inject(HttpTestingController);
    });

    it('se sait en édition quand la route porte un athlète', async () => {
      const harness = await RouterTestingHarness.create();
      const form = await harness.navigateByUrl('/app/athletes/ath-42/edit', AthleteFormComponent);

      expect(form.isEdit)
        .withContext('sinon l’écran s’intitule « Nouvel athlète » et crée un doublon')
        .toBeTrue();
      // Et il va chercher CET athlète-là.
      http.expectOne((r) => r.url.endsWith('/athletes/ath-42'));
    });
  });

  describe('changement d’athlète sous la coquille', () => {
    const shellRoutes: Routes = [{
      path: 'app/athletes/:athleteId',
      component: ShellStub,
      children: [{ path: 'programme', component: TabStub }],
    }];

    beforeEach(() => {
      opened.length = 0;
      TestBed.configureTestingModule({
        providers: [
          provideRouter(shellRoutes, withComponentInputBinding(),
            withRouterConfig({ paramsInheritanceStrategy: 'always' })),
          provideAthleteContextReuse(),
        ],
      });
    });

    it('rouvre la section sur le nouvel athlète', async () => {
      const harness = await RouterTestingHarness.create();
      await harness.navigateByUrl('/app/athletes/ath-1/programme');
      await harness.navigateByUrl('/app/athletes/ath-2/programme');

      expect(opened)
        .withContext('la section restait chargée sur le premier athlète')
        .toEqual(['ath-1', 'ath-2']);
    });

    it('ne rouvre rien quand on ne fait que changer d’onglet', async () => {
      const harness = await RouterTestingHarness.create();
      await harness.navigateByUrl('/app/athletes/ath-1/programme');
      await harness.navigateByUrl('/app/athletes/ath-1/programme');

      expect(opened).toEqual(['ath-1']);
    });
  });
});
