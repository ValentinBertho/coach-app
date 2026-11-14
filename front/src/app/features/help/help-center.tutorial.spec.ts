import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { importProvidersFrom } from '@angular/core';
import { LucideAngularModule } from 'lucide-angular';
import { ICONS } from '../../app.config';
import { HelpCenterComponent } from './help-center.component';
import { HelpAudience } from './help-content';

/** Le tutoriel vidéo en tête de l'aide : celui du profil, rien pour l'administration. */
describe('aide : tutoriel vidéo', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
        importProvidersFrom(LucideAngularModule.pick(ICONS)),
      ],
    });
  });

  function render(audience: HelpAudience): HTMLElement {
    const fixture = TestBed.createComponent(HelpCenterComponent);
    fixture.componentRef.setInput('audience', audience);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('montre le tutoriel coach, en 16:9', () => {
    const el = render('coach');
    const video = el.querySelector('.help-video video') as HTMLVideoElement;
    expect(video.querySelector('source')?.getAttribute('src')).toBe('/video/tutoriel-coach.mp4');
    expect(video.getAttribute('preload')).toBe('none');
    expect(el.querySelector('.help-video--vertical')).toBeNull();
  });

  it('montre le tutoriel athlète, vertical', () => {
    const el = render('athlete');
    expect(el.querySelector('.help-video source')?.getAttribute('src')).toBe('/video/tutoriel-athlete.mp4');
    expect(el.querySelector('.help-video--vertical')).not.toBeNull();
  });

  it('ne montre rien à l’administration', () => {
    expect(render('admin').querySelector('.help-video')).toBeNull();
  });
});
