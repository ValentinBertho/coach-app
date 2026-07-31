import { ToastService } from './toast.service';

describe('ToastService', () => {
  let service: ToastService;

  beforeEach(() => {
    service = new ToastService();
  });

  it('ajoute un toast', () => {
    service.success('ok');
    expect(service.toasts().length).toBe(1);
    expect(service.toasts()[0].level).toBe('success');
  });

  it('retire un toast par id', () => {
    service.error('boom');
    const id = service.toasts()[0].id;
    service.dismiss(id);
    expect(service.toasts().length).toBe(0);
  });

  it('génère des ids uniques', () => {
    service.info('a');
    service.info('b');
    const [a, b] = service.toasts();
    expect(a.id).not.toBe(b.id);
  });

  it('fusionne les messages identiques au lieu de les empiler', () => {
    service.error('Accès refusé.');
    service.error('Accès refusé.');
    service.error('Accès refusé.');
    expect(service.toasts().length).toBe(1);
    expect(service.toasts()[0].count).toBe(3);
  });

  it('ne fusionne pas deux niveaux différents pour le même texte', () => {
    service.error('Terminé');
    service.success('Terminé');
    expect(service.toasts().length).toBe(2);
  });

  it('ne fusionne pas un toast porteur d’action', () => {
    service.withAction('Séance déplacée', 'Annuler', () => {});
    service.withAction('Séance déplacée', 'Annuler', () => {});
    expect(service.toasts().length).toBe(2);
  });

  it('plafonne la pile à trois toasts visibles', () => {
    service.info('a');
    service.info('b');
    service.info('c');
    service.info('d');
    expect(service.toasts().length).toBe(3);
    expect(service.toasts().map((t) => t.message)).toEqual(['b', 'c', 'd']);
  });
});
