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
});
