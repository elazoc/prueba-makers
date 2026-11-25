import { DestroyRef, computed, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { patchState, signalStore, withComputed, withHooks, withMethods, withState } from '@ngrx/signals';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { EMPTY, catchError, firstValueFrom, pipe, switchMap, tap } from 'rxjs';
import { AppError, toAppError } from '../../core/api/app-error';
import { LoanDto, LoanEventDto, LoanRequest, LoanStatus } from '../../core/api/dto';
import { LoansApi } from '../../core/api/loans.api';
import { Page } from '../../core/api/page';
import { LiveEventsService } from '../../core/live/live-events.service';
import { ToastService } from '../../core/notify/toast.service';
import { LOAN_POLICY } from '../../shared/forms/loan-policy';
import { formatMoney } from '../../shared/money/money';

interface MyLoansState {
  page: Page<LoanDto> | null;
  status: LoanStatus | null;
  pageIndex: number;
  loading: boolean;
  error: AppError | null;
  pendingCount: number;
  /** Filas que acaban de cambiar en vivo: se resaltan 1,2 s. */
  highlighted: string[];
}

export interface MyLoansQuery {
  status: LoanStatus | null;
  page: number;
}

const PAGE_SIZE = 20;
const HIGHLIGHT_MS = 1200;

/**
 * Préstamos del usuario. El servidor es la fuente de verdad: tras cada comando o aviso en vivo se
 * vuelve a pedir la página; nada se inventa en el cliente.
 */
export const MyLoansStore = signalStore(
  withState<MyLoansState>({
    page: null,
    status: null,
    pageIndex: 0,
    loading: false,
    error: null,
    pendingCount: 0,
    highlighted: [],
  }),
  withComputed(({ pendingCount }) => ({
    canRequest: computed(() => pendingCount() < LOAN_POLICY.maxPending),
  })),
  withMethods((store) => {
    const api = inject(LoansApi);
    const toasts = inject(ToastService);
    /** Comandos en curso por clave: un doble clic reutiliza la misma promesa. */
    const inFlight = new Map<string, Promise<unknown>>();

    const once = <T>(key: string, run: () => Promise<T>): Promise<T> => {
      const running = inFlight.get(key) as Promise<T> | undefined;
      if (running) return running;
      const started = run().finally(() => inFlight.delete(key));
      inFlight.set(key, started);
      return started;
    };

    // switchMap: si cambia el filtro, la petición anterior se cancela y nunca pisa a la nueva.
    const load = rxMethod<MyLoansQuery>(
      pipe(
        tap(() => patchState(store, { loading: true })),
        switchMap((q) =>
          api
            .listMine({
              page: q.page,
              size: PAGE_SIZE,
              sort: 'requestedAt,desc',
              status: q.status ?? undefined,
            })
            .pipe(
              tap((page) => patchState(store, { page, loading: false, error: null })),
              catchError((e: unknown) => {
                patchState(store, { loading: false, error: toAppError(e) });
                return EMPTY;
              }),
            ),
        ),
      ),
    );

    const refreshPending = rxMethod<void>(
      pipe(
        switchMap(() =>
          api.listMine({ page: 0, size: 1, status: 'PENDING' }).pipe(
            tap((p) => patchState(store, { pendingCount: p.total })),
            catchError(() => EMPTY),
          ),
        ),
      ),
    );

    const reload = () => {
      load({ status: store.status(), page: store.pageIndex() });
      refreshPending();
    };

    const highlight = (id: string) => {
      patchState(store, { highlighted: [...store.highlighted(), id] });
      setTimeout(
        () => patchState(store, { highlighted: store.highlighted().filter((h) => h !== id) }),
        HIGHLIGHT_MS,
      );
    };

    return {
      reload,

      setQuery(q: MyLoansQuery): void {
        patchState(store, { status: q.status, pageIndex: q.page });
        reload();
      },

      request(r: LoanRequest): Promise<LoanDto> {
        return once('request', async () => {
          const created = await firstValueFrom(api.request(r));
          reload();
          highlight(created.id);
          return created;
        });
      },

      update(id: string, r: LoanRequest): Promise<LoanDto> {
        return once(`update:${id}`, async () => {
          const updated = await firstValueFrom(api.update(id, r));
          reload();
          highlight(id);
          return updated;
        });
      },

      cancel(id: string): Promise<void> {
        return once(`cancel:${id}`, async () => {
          await firstValueFrom(api.cancel(id));
          reload();
        });
      },

      async applyLiveEvent(e: LoanEventDto): Promise<void> {
        reload();
        highlight(e.loanId);
        if (e.type !== 'loan.approved' && e.type !== 'loan.rejected') return;
        try {
          const loan = await firstValueFrom(api.get(e.loanId));
          const amount = formatMoney(loan.amount);
          toasts.show(
            e.type === 'loan.approved'
              ? { tone: 'success', message: `Se aprobó tu préstamo de ${amount}.` }
              : {
                  tone: 'warning',
                  message: `Se rechazó tu préstamo de ${amount}. Mira el motivo en el detalle.`,
                },
          );
        } catch {
          // La lista ya se recargó; el aviso es un extra.
        }
      },
    };
  }),
  withHooks((store) => ({
    onInit() {
      const live = inject(LiveEventsService);
      const destroyRef = inject(DestroyRef);
      live.events$.pipe(takeUntilDestroyed(destroyRef)).subscribe((e) => void store.applyLiveEvent(e));
      live.resync$.pipe(takeUntilDestroyed(destroyRef)).subscribe(() => store.reload());
    },
  })),
);
