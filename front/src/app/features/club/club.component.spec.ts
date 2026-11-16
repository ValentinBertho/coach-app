import { importProvidersFrom } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { ICONS } from '../../app.config';
import { AuthService } from '../../core/services/auth.service';
import { User } from '../../core/models/user.model';
import { ClubComponent } from './club.component';

/** Le départ d'un coach : on voit ce qu'il produit avant de le confirmer. */
describe('départ d’un coach du club', () => {
  let fixture: ComponentFixture<ClubComponent>;
  let http: HttpTestingController;

  const members = [
    { coachId: 'owner', name: 'Claire Owner', clubRole: 'OWNER', pending: false },
    { coachId: 'c2', name: 'Paul Coach', clubRole: 'COACH', pending: false },
  ];
  const impact = {
    coachName: 'Paul Coach',
    handedOver: [{ athleteId: 'a1', name: 'Léa Club' }],
    followers: [{ athleteId: 'a2', name: 'Tom Privé' }],
    successors: [{ coachId: 'owner', name: 'Claire Owner', owner: true }],
    opensSoloSpace: true,
  };

  function setUp(me: string): void {
    TestBed.configureTestingModule({
      imports: [ClubComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        importProvidersFrom(LucideAngularModule.pick(ICONS)),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    const auth = TestBed.inject(AuthService);
    auth.currentUser.set({ id: me, role: 'HEAD_COACH', clubId: 'club1', fullName: 'X' } as unknown as User);
    spyOn(auth, 'clubId').and.returnValue('club1');
    fixture = TestBed.createComponent(ClubComponent);
    fixture.detectChanges();
    http.expectOne((r) => r.url.endsWith('/clubs/club1/members') && r.method === 'GET').flush(members);
    http.match(() => true).forEach((r) =>
      r.flush({ content: [], totalElements: 0, totalPages: 1, number: 0, size: 50, last: true, first: true }));
    fixture.detectChanges();
  }

  it('le propriétaire voit qui reste au club et qui suit le coach, puis confirme', () => {
    setUp('owner');
    const remove = Array.from(fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>)
      .find((b) => b.textContent?.includes('Retirer'))!;
    remove.click();
    http.expectOne((r) => r.url.endsWith('/members/c2/departure')).flush(impact);
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('1 athlète(s) du club restent au club');
    expect(text).toContain('Tom Privé');
    const confirm = fixture.nativeElement.querySelector('.departure .btn-danger') as HTMLButtonElement;
    confirm.click();
    const del = http.expectOne((r) => r.url.endsWith('/members/c2') && r.method === 'DELETE');
    expect(del.request.params.get('successorId')).toBe('owner');
    del.flush(impact);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain('Paul Coach');
  });

  it('un coach du club peut le quitter lui-même', () => {
    setUp('c2');
    const leave = Array.from(fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>)
      .find((b) => b.textContent?.includes('Quitter le club'));
    expect(leave).toBeTruthy();
    expect(Array.from(fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>)
      .some((b) => b.textContent?.includes('Retirer'))).toBeFalse();
  });
});
