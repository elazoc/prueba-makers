import { TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { AdminLoanDto, LoanEventDto } from '../../core/api/dto';
import { LoansApi } from '../../core/api/loans.api';
import { appError } from '../../core/auth/testing';
import { LiveEventsService } from '../../core/live/live-events.service';
import { ToastService } from '../../core/notify/toast.service';
import { loan, loanEvent, pageOf } from '../my-loans/testing';
import { AdminLoansStore } from './admin-loans.store';

const settle = (ms = 0) => new Promise((r) => setTimeout(r, ms));
const adminLoan = (id: string): AdminLoanDto => ({
  ...loan(id),
  applicantEmail: 'usuario@test.com',
  applicantName: 'Usuario Demo',
});

describe('AdminLoansStore', () => {
  let api: Record<string, ReturnType<typeof vi.fn>>;
  let events: Subject<LoanEventDto>;
  let store: InstanceType<typeof AdminLoansStore>;
  const toastMessages = () => TestBed.inject(ToastService).toasts().map((t) => `${t.tone}:${t.message}`);

  beforeEach(() => {
    events = new Subject();
    api = {
      listAll: vi.fn(() => of(pageOf([adminLoan('l1')], 25))),
      approve: vi.fn(() => of(loan('l1', 'APPROVED'))),
      reject: vi.fn(() => of(loan('l1', 'REJECTED'))),
    };
    TestBed.configureTestingModule({
      providers: [
        AdminLoansStore,
        { provide: LoansApi, useValue: api },
        { provide: LiveEventsService, useValue: { events$: events, resync$: new Subject() } },
      ],
    });
    store = TestBed.inject(AdminLoansStore);
  });

  it('lists pending requests oldest first', async () => {
    store.setQuery({ status: 'PENDING', email: null, page: 0 });
    await settle();
    expect(api['listAll']).toHaveBeenCalledWith(
      expect.objectContaining({ status: 'PENDING', sort: 'requestedAt,asc', page: 0, size: 20 }),
    );
    expect(store.page()?.items.map((l) => l.id)).toEqual(['l1']);
  });

  it('sends one approval on a double click and says who it was for', async () => {
    store.setQuery({ status: 'PENDING', email: null, page: 0 });
    await settle();
    await Promise.all([store.approve('l1'), store.approve('l1')]);
    expect(api['approve']).toHaveBeenCalledTimes(1);
    expect(toastMessages()).toContain('success:Se aprobó el préstamo de Usuario Demo.');
  });

  it('marks a request as being decided while the server answers', async () => {
    const pending = new Subject<never>();
    api['approve'].mockReturnValue(pending);
    void store.approve('l1');
    expect(store.deciding()).toContain('l1');
    pending.error(appError(409, 'LOAN_NOT_PENDING'));
    await settle();
    expect(store.deciding()).not.toContain('l1');
  });

  it('warns with the server message and reloads on a conflict', async () => {
    store.setQuery({ status: 'PENDING', email: null, page: 0 });
    await settle();
    api['approve'].mockReturnValue(throwError(() => appError(409, 'LOAN_NOT_PENDING', 'Este préstamo ya fue decidido.')));
    const calls = api['listAll'].mock.calls.length;

    expect(await store.approve('l1')).toBe(false);
    await settle();
    expect(toastMessages()).toContain('warning:Este préstamo ya fue decidido.');
    expect(api['listAll'].mock.calls.length).toBeGreaterThan(calls);
  });

  it('rejects with the reason', async () => {
    store.setQuery({ status: 'PENDING', email: null, page: 0 });
    await settle();
    expect(await store.reject('l1', 'Ingresos insuficientes')).toBe(true);
    expect(api['reject']).toHaveBeenCalledWith('l1', { reason: 'Ingresos insuficientes' });
    expect(toastMessages()).toContain('success:Se rechazó el préstamo de Usuario Demo.');
  });

  it('counts a new request that lands on another page', async () => {
    store.setQuery({ status: 'PENDING', email: null, page: 0 });
    await settle();
    events.next(loanEvent('loan.requested', 'nuevo'));
    await settle();
    expect(store.newArrivals()).toBe(1);
    expect(store.lastPageIndex()).toBe(1);
  });

  it('highlights a new request that lands on the current page', async () => {
    store.setQuery({ status: 'PENDING', email: null, page: 0 });
    await settle();
    api['listAll'].mockReturnValue(of(pageOf([adminLoan('l1'), adminLoan('nuevo')], 2)));
    events.next(loanEvent('loan.requested', 'nuevo'));
    await settle();
    expect(store.highlighted()).toContain('nuevo');
    expect(store.newArrivals()).toBe(0);
  });

  it('forgets new arrivals when the query changes', async () => {
    store.setQuery({ status: 'PENDING', email: null, page: 0 });
    await settle();
    events.next(loanEvent('loan.requested', 'nuevo'));
    await settle();
    store.setQuery({ status: 'PENDING', email: null, page: 1 });
    expect(store.newArrivals()).toBe(0);
  });
});
