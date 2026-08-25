import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { importProvidersFrom } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { ICONS } from '../../app.config';
import { ConversationSummary } from '../../core/services/conversation.service';
import { ConversationsComponent } from './conversations.component';

/**
 * La messagerie vue de l'écran.
 *
 * <p>Deux invariants de cloisonnement s'y jouent — le fil du club est un canal d'annonces, et
 * « Nouveau message » ne propose que ce que le serveur accepte — plus un troisième, découvert au
 * téléphone : <b>la liste doit se montrer</b>. L'écran ouvrait d'office la conversation la plus
 * chaude, ce qui remplit utilement un second panneau sur grand écran mais escamote l'écran entier
 * sur un mobile : l'athlète arrivait à l'intérieur d'un fil sans avoir vu qu'il en avait d'autres,
 * et sans rien pour en sortir.</p>
 */
describe('messagerie', () => {
  let fixture: ComponentFixture<ConversationsComponent>;
  let host: HTMLElement;
  let http: HttpTestingController;

  const clubThread: ConversationSummary = {
    id: 'c-club', kind: 'CLUB', title: 'AC Test', subtitle: 'Annonces du club',
    athleteId: null, groupId: null, lastMessage: 'Sortie dimanche', lastSenderName: 'Coach',
    lastMessageAt: '2026-08-24T09:00:00Z', unreadCount: 0, canPost: false,
  };
  const coachThread: ConversationSummary = {
    ...clubThread, id: 'c-coach', kind: 'ATHLETE_COACH', title: 'Marie Coach',
    subtitle: 'Coach', lastMessage: 'Bien joué', unreadCount: 2, canPost: true,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ConversationsComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        importProvidersFrom(LucideAngularModule.pick(ICONS)),
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  /**
   * Monte l'écran à une largeur donnée. La largeur n'est pas un détail de rendu ici : c'est elle
   * qui décide si l'on ouvre une conversation d'office.
   */
  function render(width: 'narrow' | 'wide'): void {
    spyOn(window, 'matchMedia').and.returnValue(
      { matches: width === 'narrow' } as MediaQueryList);
    fixture = TestBed.createComponent(ConversationsComponent);
    fixture.detectChanges();
  }

  /** Sert la boîte de réception, puis le fil que le composant ouvre de lui-même. */
  function serveInbox(list: ConversationSummary[], openedId: string): void {
    http.expectOne((r) => r.url.endsWith('/me/conversations') && r.method === 'GET').flush(list);
    http.expectOne((r) => r.url.endsWith(`/${openedId}/messages`)).flush([]);
    http.expectOne((r) => r.url.endsWith(`/${openedId}/read`)).flush(null);
    http.match((r) => r.url.endsWith('/unread-count')).forEach((r) => r.flush({ count: 0 }));
    fixture.detectChanges();
    host = fixture.nativeElement as HTMLElement;
  }

  describe('au téléphone', () => {
    beforeEach(() => render('narrow'));

    it('montre la liste, et n’ouvre aucun fil de lui-même', () => {
      http.expectOne((r) => r.url.endsWith('/me/conversations') && r.method === 'GET')
        .flush([clubThread, coachThread]);
      fixture.detectChanges();
      host = fixture.nativeElement as HTMLElement;

      // Aucun appel de fil : c'est la preuve qu'on n'a rien ouvert dans le dos de l'athlète.
      http.expectNone((r) => r.url.includes('/messages'));
      expect(host.querySelectorAll('.row').length).toBe(2);
      expect(host.querySelector('.msg-thread')?.textContent)
        .withContext('le panneau de droite n’existe pas sur un écran étroit')
        .toContain('Choisis une conversation');
    });

    it('passe en plein écran sur le fil ouvert, et sait en revenir', () => {
      http.expectOne((r) => r.url.endsWith('/me/conversations') && r.method === 'GET')
        .flush([coachThread]);
      fixture.detectChanges();

      fixture.componentInstance.select('c-coach');
      http.expectOne((r) => r.url.endsWith('/c-coach/messages')).flush([]);
      http.expectOne((r) => r.url.endsWith('/c-coach/read')).flush(null);
      http.match((r) => r.url.endsWith('/unread-count')).forEach((r) => r.flush({ count: 0 }));
      fixture.detectChanges();
      host = fixture.nativeElement as HTMLElement;

      // C'est cette classe que la feuille de style attend pour sortir le fil du flux : sans
      // elle, la saisie reste sous la barre d'onglets et le fil pousse la page.
      expect((fixture.nativeElement as HTMLElement).classList).toContain('thread-open');
      expect(host.querySelector('.thread-head .back')).withContext('sinon on est prisonnier').not.toBeNull();

      fixture.componentInstance.closeThread();
      fixture.detectChanges();
      expect((fixture.nativeElement as HTMLElement).classList).not.toContain('thread-open');
    });
  });

  describe('sur deux colonnes', () => {
    beforeEach(() => render('wide'));

    it('ouvre le fil non lu en priorité et le marque lu', () => {
      http.expectOne((r) => r.url.endsWith('/me/conversations') && r.method === 'GET')
        .flush([clubThread, coachThread]);
      http.expectOne((r) => r.url.endsWith('/c-coach/messages')).flush([]);
      http.expectOne((r) => r.url.endsWith('/c-coach/read')).flush(null);
      http.match((r) => r.url.endsWith('/unread-count')).forEach((r) => r.flush({ count: 0 }));
      fixture.detectChanges();
      host = fixture.nativeElement as HTMLElement;

      expect(host.querySelector('.row--active')?.textContent).toContain('Marie Coach');
      expect(host.querySelector('.row__badge')).withContext('la pastille retombe à l’ouverture').toBeNull();
    });

    it("n'offre pas de champ de saisie sur un fil en lecture seule", () => {
      serveInbox([clubThread], 'c-club');

      expect(host.querySelector('.composer'))
        .withContext('un champ qui échouerait à l’envoi est une promesse fausse')
        .toBeNull();
      expect(host.textContent).toContain('écrites par les coachs');
    });

    it('propose les destinataires que le serveur autorise', () => {
      serveInbox([coachThread], 'c-coach');

      fixture.componentInstance.openPicker();
      http.expectOne((r) => r.url.endsWith('/recipients')).flush([
        { kind: 'COACH', id: 'u-1', name: 'Marie Coach', subtitle: 'Coach principal' },
      ]);
      fixture.detectChanges();

      expect(host.querySelector('.picker')?.textContent).toContain('Marie Coach');
    });
  });
});
