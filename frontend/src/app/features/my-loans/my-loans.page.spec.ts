import { computed, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { LoanDto } from '../../core/api/dto';
import { Page } from '../../core/api/page';
import { MyLoansActions } from './my-loans.actions';
import { MyLoansPage } from './my-loans.page';
import { MyLoansStore } from './my-loans.store';
import { loan, pageOf } from './testing';

describe('MyLoansPage', () => {
  function fakeStore(page: Page<LoanDto> | null, pending = 1) {
    const pendingCount = signal(pending);
    return {
      page: signal(page),
      status: signal(null),
      loading: signal(false),
      error: signal(null),
      pendingCount,
      highlighted: signal<string[]>([]),
      canRequest: computed(() => pendingCount() < 3),
      setQuery: vi.fn(),
      reload: vi.fn(),
      cancel: vi.fn(),
    };
  }

  async function render(store: ReturnType<typeof fakeStore>, inputs: Record<string, string> = {}) {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    TestBed.overrideComponent(MyLoansPage, {
      set: {
        providers: [
          { provide: MyLoansStore, useValue: store },
          { provide: MyLoansActions, useValue: { openForm: vi.fn(), cancel: vi.fn() } },
        ],
      },
    });
    const fixture = TestBed.createComponent(MyLoansPage);
    Object.entries(inputs).forEach(([k, v]) => fixture.componentRef.setInput(k, v));
    await fixture.whenStable();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  it('reads the filter and page from the url', async () => {
    const store = fakeStore(pageOf([]));
    await render(store, { estado: 'REJECTED', pagina: '1' });
    expect(store.setQuery).toHaveBeenLastCalledWith({ status: 'REJECTED', page: 1 });
  });

  it('ignores unknown filters', async () => {
    const store = fakeStore(pageOf([]));
    await render(store, { estado: 'HACKED' });
    expect(store.setQuery).toHaveBeenLastCalledWith({ status: null, page: 0 });
  });

  it('offers edit and cancel only on pending rows', async () => {
    const { el } = await render(fakeStore(pageOf([loan('l1', 'PENDING'), loan('l2', 'APPROVED')])));
    const rows = el.querySelectorAll('tbody tr');
    expect(rows[0].textContent).toContain('Editar');
    expect(rows[0].textContent).toContain('Cancelar');
    expect(rows[1].textContent).not.toContain('Editar');
    expect(rows[1].textContent).toContain('Aprobado');
    expect(rows[0].textContent).toContain('$\u00a01.500.000');
  });

  it('explains the limit and disables the request when there are 3 pending', async () => {
    const { el } = await render(fakeStore(pageOf([loan('l1')]), 3));
    const request = [...el.querySelectorAll('button')].find((b) => b.textContent?.includes('Solicitar préstamo'))!;
    expect(request.disabled).toBe(true);
    expect(el.textContent).toContain('Tienes 3 de 3 solicitudes pendientes.');
  });

  it('shows a helpful empty state', async () => {
    const { el } = await render(fakeStore(pageOf([]), 0));
    expect(el.textContent).toContain('Todavía no tienes préstamos.');
    expect(el.textContent).toContain('Cuando solicites el primero, lo verás aquí.');
  });
});
