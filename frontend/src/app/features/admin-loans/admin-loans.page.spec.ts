import { computed, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { AdminLoanDto, LoanStatus } from '../../core/api/dto';
import { Page } from '../../core/api/page';
import { loan, pageOf } from '../my-loans/testing';
import { AdminLoansActions } from './admin-loans.actions';
import { AdminLoansPage } from './admin-loans.page';
import { AdminLoansStore } from './admin-loans.store';

const adminLoan = (id: string, status: LoanStatus = 'PENDING'): AdminLoanDto => ({
  ...loan(id, status),
  applicantEmail: 'usuario@test.com',
  applicantName: 'Usuario Demo',
});

describe('AdminLoansPage', () => {
  function fakeStore(page: Page<AdminLoanDto> | null, arrivals = 0) {
    return {
      page: signal(page),
      status: signal<LoanStatus | null>('PENDING'),
      email: signal<string | null>(null),
      loading: signal(false),
      error: signal(null),
      deciding: signal<string[]>([]),
      highlighted: signal<string[]>([]),
      newArrivals: signal(arrivals),
      lastPageIndex: computed(() => 1),
      setQuery: vi.fn(),
      reload: vi.fn(),
      clearArrivals: vi.fn(),
    };
  }

  async function render(store: ReturnType<typeof fakeStore>, inputs: Record<string, string> = {}) {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    TestBed.overrideComponent(AdminLoansPage, {
      set: {
        providers: [
          { provide: AdminLoansStore, useValue: store },
          { provide: AdminLoansActions, useValue: { approve: vi.fn(), reject: vi.fn() } },
        ],
      },
    });
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(AdminLoansPage);
    Object.entries(inputs).forEach(([k, v]) => fixture.componentRef.setInput(k, v));
    await fixture.whenStable();
    return { fixture, el: fixture.nativeElement as HTMLElement, navigate };
  }

  it('opens on pending requests', async () => {
    const store = fakeStore(pageOf([]));
    await render(store);
    expect(store.setQuery).toHaveBeenLastCalledWith({ status: 'PENDING', email: null, page: 0 });
  });

  it('reads "todos", the email and the page from the url', async () => {
    const store = fakeStore(pageOf([]));
    await render(store, { estado: 'todos', correo: 'usuario@test.com', pagina: '2' });
    expect(store.setQuery).toHaveBeenLastCalledWith({ status: null, email: 'usuario@test.com', page: 2 });
  });

  it('shows who asked and offers approve and reject on pending rows only', async () => {
    const { el } = await render(fakeStore(pageOf([adminLoan('l1'), adminLoan('l2', 'APPROVED')])));
    const rows = el.querySelectorAll('tbody tr');
    expect(rows[0].textContent).toContain('Usuario Demo');
    expect(rows[0].textContent).toContain('usuario@test.com');
    expect(rows[0].textContent).toContain('Aprobar');
    expect(rows[0].textContent).toContain('Rechazar');
    expect(rows[1].textContent).not.toContain('Aprobar');
  });

  it('does not call the api with an invalid email', async () => {
    const store = fakeStore(pageOf([]));
    const { fixture, el, navigate } = await render(store);
    const input = el.querySelector<HTMLInputElement>('input[type="email"]')!;
    input.value = 'usuario@';
    input.dispatchEvent(new Event('input'));
    el.querySelector<HTMLFormElement>('form[role="search"]')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
    expect(navigate).not.toHaveBeenCalled();
    expect(el.textContent).toContain('Escribe un correo válido, por ejemplo nombre@correo.com.');
  });

  it('announces new arrivals with a way to see them', async () => {
    const { el } = await render(fakeStore(pageOf([adminLoan('l1')]), 2));
    const notice = el.querySelector('[role="status"].pr-arrivals')!;
    expect(notice.textContent).toContain('Llegaron 2 solicitudes nuevas');
    expect(notice.querySelector('button')?.textContent).toContain('Ver');
  });

  it('says there is nothing to decide', async () => {
    const { el } = await render(fakeStore(pageOf([])));
    expect(el.textContent).toContain('No hay solicitudes por decidir.');
  });
});
