import { importProvidersFrom } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LucideAngularModule } from 'lucide-angular';
import { ICONS } from '../../../app.config';
import { VideoEmbedComponent } from './video-embed.component';

/**
 * La démonstration d'un exercice, sans faire payer l'athlète pour celles qu'il ne regarde pas.
 *
 * <p><b>Ce que ces tests verrouillent.</b> Une séance affiche cinq à huit exercices. Si chacun
 * posait son iframe au rendu, ouvrir sa séance déclencherait huit connexions à l'hébergeur — avec
 * l'adresse IP de l'athlète — pour des vidéos que personne n'a demandées. L'iframe n'apparaît donc
 * qu'au clic, et jamais pour un hébergeur non reconnu.</p>
 */
describe('VideoEmbedComponent', () => {
  let fixture: ComponentFixture<VideoEmbedComponent>;
  let host: HTMLElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [VideoEmbedComponent],
      providers: [importProvidersFrom(LucideAngularModule.pick(ICONS))],
    }).compileComponents();

    fixture = TestBed.createComponent(VideoEmbedComponent);
    host = fixture.nativeElement as HTMLElement;
  });

  function render(url: string | null): void {
    fixture.componentRef.setInput('url', url);
    fixture.componentRef.setInput('title', 'Fente bulgare');
    fixture.detectChanges();
  }

  /** Le point entier de la vignette : aucune requête tierce tant que personne n'a cliqué. */
  it('ne charge aucune iframe avant le clic', () => {
    render('https://youtu.be/dQw4w9WgXcQ');

    expect(host.querySelector('iframe')).withContext('aucune iframe au rendu').toBeNull();
    expect(host.querySelector('.ve__poster')).withContext('la vignette est proposée').not.toBeNull();
  });

  it('charge le lecteur au clic, sur le domaine sans cookie', () => {
    render('https://youtu.be/dQw4w9WgXcQ');

    host.querySelector<HTMLButtonElement>('.ve__poster')!.click();
    fixture.detectChanges();

    const iframe = host.querySelector('iframe');
    expect(iframe).not.toBeNull();
    expect(iframe!.getAttribute('src')).toContain('https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ');
  });

  /** Un hébergeur non reconnu garde son lien : on ne perd rien, on n'encadre rien. */
  it('laisse un lien externe pour un hébergeur non reconnu', () => {
    render('https://drive.google.com/file/d/abc/view');

    expect(host.querySelector('.ve__poster')).withContext('pas de vignette').toBeNull();
    const link = host.querySelector<HTMLAnchorElement>('.ve__out--alone');
    expect(link).withContext('le lien reste accessible').not.toBeNull();
    expect(link!.href).toContain('drive.google.com');
    expect(link!.rel).toContain('noopener');
  });

  /** Sans lien, le composant ne prend aucune place. */
  it('ne rend rien sans vidéo', () => {
    render(null);

    expect(host.textContent?.trim()).toBe('');
  });
});
