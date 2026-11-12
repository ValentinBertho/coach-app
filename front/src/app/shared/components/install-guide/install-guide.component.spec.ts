import { importProvidersFrom } from '@angular/core';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { LucideAngularModule } from 'lucide-angular';
import { ICONS } from '../../../app.config';
import { PwaInstallService } from '../../../core/services/pwa-install.service';
import { InstallGuideComponent, SNOOZE_MS, detectInstallPlatform } from './install-guide.component';

const IPHONE = 'Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 '
  + '(KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1';
const ANDROID = 'Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) '
  + 'Chrome/129.0.0.0 Mobile Safari/537.36';
const DESKTOP = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) '
  + 'Chrome/129.0.0.0 Safari/537.36';
const MAC = 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) '
  + 'Version/18.0 Safari/605.1.15';
const INSTAGRAM = IPHONE + ' Instagram 300.0.0.0';

describe('détection de la plateforme d’installation', () => {
  it('reconnaît l’iPhone et l’Android', () => {
    expect(detectInstallPlatform(IPHONE, 5)).toBe('ios');
    expect(detectInstallPlatform(ANDROID, 5)).toBe('android');
  });

  /** iPadOS se présente comme un Mac : seul l'écran tactile le trahit. */
  it('reconnaît l’iPad qui se fait passer pour un Mac', () => {
    expect(detectInstallPlatform(MAC, 5)).toBe('ios');
    expect(detectInstallPlatform(MAC, 0)).toBeNull();
  });

  it('ignore le bureau et les navigateurs intégrés, qui ne savent pas installer', () => {
    expect(detectInstallPlatform(DESKTOP, 0)).toBeNull();
    expect(detectInstallPlatform(INSTAGRAM, 5)).toBeNull();
  });
});

describe('fenêtre d’aide à l’installation', () => {
  let fixture: ComponentFixture<InstallGuideComponent>;
  let component: InstallGuideComponent;
  let pwa: PwaInstallService;

  function setUp(options: { ua?: string; installed?: boolean } = {}) {
    const installed = options.installed ?? false;
    // Seules les requêtes display-mode sont simulées : le CDK interroge matchMedia pour le reste.
    const real = window.matchMedia.bind(window);
    spyOn(window, 'matchMedia').and.callFake((query: string) => query.includes('display-mode')
      ? ({ matches: installed && query.includes('standalone') } as MediaQueryList)
      : real(query));
    spyOnProperty(Object.getPrototypeOf(navigator), 'userAgent', 'get').and.returnValue(options.ua ?? IPHONE);
    spyOnProperty(Object.getPrototypeOf(navigator), 'maxTouchPoints', 'get').and.returnValue(5);

    TestBed.configureTestingModule({
      providers: [provideNoopAnimations(), importProvidersFrom(LucideAngularModule.pick(ICONS))],
    });
    pwa = TestBed.inject(PwaInstallService);
    fixture = TestBed.createComponent(InstallGuideComponent);
    component = fixture.componentInstance;
  }

  function launch(): void {
    fixture.detectChanges();
    tick(3_000);
    fixture.detectChanges();
  }

  beforeEach(() => localStorage.clear());
  afterEach(() => localStorage.clear());

  it('décrit le geste Partager → écran d’accueil sur iPhone', fakeAsync(() => {
    setUp();

    launch();

    expect(component.visible()).toBeTrue();
    expect(document.body.textContent).toContain('Partager');
    expect(document.body.textContent).toContain("Sur l'écran d'accueil");
  }));

  it('offre l’invite native sur Android quand elle est disponible', fakeAsync(() => {
    setUp({ ua: ANDROID });
    pwa.canInstall.set(true);
    const prompt = spyOn(pwa, 'promptInstall').and.resolveTo();

    launch();
    (document.querySelector('.ig__cta') as HTMLButtonElement).click();
    tick();
    fixture.detectChanges();

    expect(prompt).toHaveBeenCalled();
    expect(component.visible()).toBeFalse();
  }));

  it('décrit le menu sur Android quand l’invite native manque', fakeAsync(() => {
    setUp({ ua: ANDROID });

    launch();

    expect(document.querySelector('.ig__cta')).toBeNull();
    expect(document.body.textContent).toContain("Ajouter à l'écran d'accueil");
  }));

  it('ne se propose pas sur ordinateur', fakeAsync(() => {
    setUp({ ua: DESKTOP });
    launch();
    expect(component.visible()).toBeFalse();
  }));

  it('ne se propose pas dans l’application déjà installée', fakeAsync(() => {
    setUp({ installed: true });
    launch();
    expect(component.visible()).toBeFalse();
  }));

  it('n’apparaît pas instantanément au chargement', () => {
    setUp();
    fixture.detectChanges();
    expect(component.visible()).toBeFalse();
  });

  /** « Plus tard » vaut silence pendant deux semaines, puis la fenêtre revient. */
  it('se tait après « Plus tard », puis revient après la mise en sommeil', fakeAsync(() => {
    setUp();
    launch();
    component.later();
    fixture.detectChanges();
    expect(component.visible()).toBeFalse();

    const again = TestBed.createComponent(InstallGuideComponent);
    again.detectChanges();
    tick(3_000);
    expect(again.componentInstance.visible()).toBeFalse();
    again.destroy();

    localStorage.setItem('darilab.install-guide.snoozedAt', String(Date.now() - SNOOZE_MS - 1));
    const later = TestBed.createComponent(InstallGuideComponent);
    later.detectChanges();
    tick(3_000);
    expect(later.componentInstance.visible()).toBeTrue();
    later.destroy();
  }));
});
