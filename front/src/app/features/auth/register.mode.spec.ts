import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RegistrationMode } from '../../core/models/user.model';
import { RegisterComponent } from './register.component';

/**
 * La porte d'entrée de la plateforme, dans les trois régimes qu'elle peut avoir.
 *
 * <p>Ce que ces tests interdisent : que l'écran demande au candidat quelque chose que le serveur
 * n'acceptera pas. Il montrait toujours le même formulaire — mot de passe compris — quel que soit
 * le régime réel ; en bêta ouverte, où l'inscription directe est fermée, cela revenait à faire
 * choisir un mot de passe pour un compte qui n'allait pas exister.</p>
 */
describe('inscription — le formulaire suit le régime du serveur', () => {
  let fixture: ComponentFixture<RegisterComponent>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [RegisterComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(RegisterComponent);
  });

  afterEach(() => http.verify());

  /** Démarre l'écran avec le régime annoncé par le serveur. */
  function start(mode: RegistrationMode): HTMLElement {
    fixture.detectChanges();
    http.expectOne((r) => r.url.includes('/public/registration-mode'))
      .flush({ mode, label: mode });
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  function text(host: HTMLElement): string {
    return host.textContent ?? '';
  }

  it('demande un mot de passe quand l’inscription est libre', () => {
    const host = start('OPEN');
    expect(host.querySelector('#password')).withContext('inscription directe').not.toBeNull();
    expect(host.querySelector('#invitationCode'))
      .withContext('aucun code n’est exigé : le champ n’a rien à faire là')
      .toBeNull();
  });

  it('ne montre le champ « code » que là où le serveur l’exige', () => {
    const host = start('INVITE');
    expect(host.querySelector('#invitationCode')).not.toBeNull();
  });

  /**
   * Le régime de la bêta ouverte. Le formulaire ne crée rien : il ne doit donc demander ni mot de
   * passe, ni le laisser croire — le bouton parle d'une demande, pas d'une création.
   */
  it('remplace le mot de passe par une demande en régime « sur demande »', () => {
    const host = start('REQUEST');
    expect(host.querySelector('#password'))
      .withContext('rien n’est créé au dépôt : un mot de passe n’aurait aucun sens')
      .toBeNull();
    expect(host.querySelector('#req-clubName')).not.toBeNull();
    expect(text(host)).toContain('Envoyer ma demande');
  });

  /**
   * Un formulaire qui se vide sans un mot est le moment où l'on perd les gens : après le dépôt,
   * l'écran doit dire ce qui vient de se passer et ce qui va suivre.
   */
  it('accuse réception de la demande, adresse comprise', () => {
    const host = start('REQUEST');
    (host.querySelector('#req-fullName') as HTMLInputElement).value = 'Camille Roy';
    (host.querySelector('#req-fullName') as HTMLInputElement).dispatchEvent(new Event('input'));
    (host.querySelector('#req-clubName') as HTMLInputElement).value = 'Les Foulées';
    (host.querySelector('#req-clubName') as HTMLInputElement).dispatchEvent(new Event('input'));
    (host.querySelector('#req-email') as HTMLInputElement).value = 'camille@club.fr';
    (host.querySelector('#req-email') as HTMLInputElement).dispatchEvent(new Event('input'));
    const terms = host.querySelector('input[type="checkbox"]') as HTMLInputElement;
    terms.click();
    fixture.detectChanges();

    (host.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    http.expectOne((r) => r.url.includes('/public/club-requests')).flush(null, { status: 202, statusText: 'Accepted' });
    fixture.detectChanges();

    expect(text(host)).toContain('Demande envoyée');
    expect(text(host))
      .withContext('rappeler l’adresse est ce qui permet de repérer une faute de frappe')
      .toContain('camille@club.fr');
    expect(text(host)).toContain('Les Foulées');
  });

  /**
   * L'API injoignable ne doit pas laisser la page vide : le serveur reste de toute façon le seul
   * arbitre de ce qu'il accepte.
   */
  it('retombe sur l’inscription directe si le régime est illisible', () => {
    fixture.detectChanges();
    http.expectOne((r) => r.url.includes('/public/registration-mode'))
      .flush(null, { status: 503, statusText: 'Service Unavailable' });
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('#password')).not.toBeNull();
  });
});
