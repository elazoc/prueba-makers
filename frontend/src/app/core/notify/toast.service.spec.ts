import { TestBed } from '@angular/core/testing';
import { PrToastRegion } from './toast-region';
import { ToastService } from './toast.service';

describe('ToastService', () => {
  afterEach(() => vi.useRealTimers());

  it('dismisses toasts after 4 seconds except errors and persistent ones', () => {
    vi.useFakeTimers();
    const toasts = TestBed.inject(ToastService);
    toasts.show({ tone: 'success', message: 'Se envió tu solicitud.' });
    toasts.show({ tone: 'danger', message: 'No se pudo conectar.' });
    toasts.show({ tone: 'warning', message: 'Tu sesión vence pronto.', persistent: true });

    vi.advanceTimersByTime(3999);
    expect(toasts.toasts()).toHaveLength(3);
    vi.advanceTimersByTime(1);
    expect(toasts.toasts().map((t) => t.tone)).toEqual(['danger', 'warning']);
  });

  it('dismisses by id', () => {
    const toasts = TestBed.inject(ToastService);
    const id = toasts.show({ tone: 'info', message: 'Hola' });
    toasts.dismiss(id);
    expect(toasts.toasts()).toEqual([]);
  });
});

describe('PrToastRegion', () => {
  it('announces politely, marks errors as alerts and closes with a labelled button', async () => {
    const fixture = TestBed.createComponent(PrToastRegion);
    const toasts = TestBed.inject(ToastService);
    toasts.show({ tone: 'success', message: 'Se aprobó el préstamo.' });
    toasts.show({ tone: 'danger', message: 'No se pudo conectar.' });
    await fixture.whenStable();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[aria-live="polite"]')).not.toBeNull();
    expect(el.querySelector('[role="alert"]')?.textContent).toContain('No se pudo conectar.');

    const close = el.querySelectorAll<HTMLButtonElement>('button[aria-label="Cerrar aviso"]');
    expect(close).toHaveLength(2);
    close[0].click();
    await fixture.whenStable();
    expect(el.textContent).not.toContain('Se aprobó el préstamo.');
  });
});
