import { TestBed } from '@angular/core/testing';
import { Subject, delay, of } from 'rxjs';
import { LoanEventDto, LoanStatus } from '../../core/api/dto';
import { LoansApi } from '../../core/api/loans.api';
import { LiveEventsService } from '../../core/live/live-events.service';
import { ToastService } from '../../core/notify/toast.service';
import { MyLoansStore } from './my-loans.store';
import { loan, loanEvent, pageOf } from './testing';

const settle = (ms = 0) => new Promise((r) => setTimeout(r, ms));

describe('MyLoansStore', () => {
  let api: Record<string, ReturnType<typeof vi.fn>>;
  let events: Subject<LoanEventDto>;
  let resync: Subject<void>;
  let store: InstanceType<typeof MyLoansStore>;

  beforeEach(() => {
    events = new Subject();
    resync = new Subject();
    api = {
      listMine: vi.fn((q: { status?: LoanStatus; size: number }) =>
        q.size === 1 ? of(pageOf([], 2)) : of(pageOf([loan('l1')])),
      ),
      request: vi.fn(() => of(loan('nuevo'))),
      update: vi.fn(() => of(loan('l1'))),
      cancel: vi.fn(() => of(undefined)),
      get: vi.fn((id: string) => of(loan(id, 'APPROVED', '800000.00'))),
    };
    TestBed.configureTestingModule({
      providers: [
        MyLoansStore,
        { provide: LoansApi, useValue: api },
        { provide: LiveEventsService, useValue: { events$: events, resync$: resync } },
      ],
    });
    store = TestBed.inject(MyLoansStore);
  });

  it('loads a page and the pending count', async () => {
    store.setQuery({ status: null, page: 0 });
    await settle();
    expect(store.page()?.items.map((l) => l.id)).toEqual(['l1']);
    expect(store.pendingCount()).toBe(2);
    expect(store.canRequest()).toBe(true);
  });

  it('keeps only the latest filter when responses arrive out of order', async () => {
    api['listMine'].mockImplementation((q: { status?: LoanStatus; size: number }) => {
      if (q.size === 1) return of(pageOf([], 0));
      return q.status === 'PENDING'
        ? of(pageOf([loan('p', 'PENDING')])).pipe(delay(40))
        : of(pageOf([loan('a', 'APPROVED')]));
    });
    store.setQuery({ status: 'PENDING', page: 0 });
    store.setQuery({ status: 'APPROVED', page: 0 });
    await settle(80);

    expect(store.status()).toBe('APPROVED');
    expect(store.page()!.items.every((l) => l.status === 'APPROVED')).toBe(true);
  });

  it('sends one request on a double submit', async () => {
    await Promise.all([
      store.request({ amount: '1500000.00', termMonths: 12 }),
      store.request({ amount: '1500000.00', termMonths: 12 }),
    ]);
    expect(api['request']).toHaveBeenCalledTimes(1);
  });

  it('does not allow a fourth pending request', async () => {
    api['listMine'].mockImplementation((q: { size: number }) =>
      q.size === 1 ? of(pageOf([], 3)) : of(pageOf([])),
    );
    store.setQuery({ status: null, page: 0 });
    await settle();
    expect(store.canRequest()).toBe(false);
  });

  it('reloads, highlights and tells the owner when a decision arrives live', async () => {
    store.setQuery({ status: null, page: 0 });
    await settle();
    const before = api['listMine'].mock.calls.length;

    events.next(loanEvent('loan.approved', 'l1'));
    await settle();

    expect(api['listMine'].mock.calls.length).toBeGreaterThan(before);
    expect(store.highlighted()).toContain('l1');
    expect(TestBed.inject(ToastService).toasts().map((t) => t.message)).toContain(
      'Se aprobó tu préstamo de $\u00a0800.000.',
    );
  });

  it('reloads when the tab comes back', async () => {
    store.setQuery({ status: null, page: 0 });
    await settle();
    const before = api['listMine'].mock.calls.length;
    resync.next();
    await settle();
    expect(api['listMine'].mock.calls.length).toBeGreaterThan(before);
  });
});
