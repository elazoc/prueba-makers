import { DestroyRef, computed, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { patchState, signalStore, withComputed, withHooks, withMethods, withState } from '@ngrx/signals';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { EMPTY, Observable, catchError, firstValueFrom, pipe, switchMap, tap } from 'rxjs';
import { AppError, isAppError, toAppError } from '../../core/api/app-error';
import { AdminLoanDto, LoanEventDto, LoanStatus } from '../../core/api/dto';
import { LoansApi } from '../../core/api/loans.api';
import { Page } from '../../core/api/page';
import { LiveEventsService } from '../../core/live/live-events.service';
import { ToastService } from '../../core/notify/toast.service';

interface AdminLoansState {
  page: Page<AdminLoanDto> | null;
  status: LoanStatus | null;
  email: string | null;
  pageIndex: number;
  loading: boolean;
  error: AppError | null;
  /** Préstamos con una decisión en curso (el botón muestra «Aprobando…»). */
  deciding: string[];
  /** Solicitudes nuevas que llegaron en vivo y quedaron en otra página. */
  newArrivals: number;
  highlighted: string[];
}

export interface AdminLoansQuery {
  status: LoanStatus | null;
  email: string | null;
  page: number;
}

const PAGE_SIZE = 20;
const HIGHLIGHT_MS = 1200;
/** El que más espera, primero. */
const SORT = 'requestedAt,asc';
const CONFLICTS = ['LOAN_NOT_PENDING', 'CONCURRENT_MODIFICATION'];

/**
 * Solicitudes para el administrador. Sin actualizaciones optimistas: una decisión solo se ve
 * cuando el servidor la confirma, y ante un conflicto (otro administrador decidió primero) se
 * muestra su mensaje y se recarga.
 */
export const AdminLoansStore = signalStore(
  withState<AdminLoansState>({
    page: null,
    status: 'PENDING',
    email: null,
    pageIndex: 0,
    loading: false,
    error: null,
    deciding: [],
    newArrivals: 0,
    highlighted: [],
  }),
  withComputed(({ page }) => ({
    lastPageIndex: computed(() => Math.max(0, (page()?.totalPages ?? 1) - 1)),
  })),
  withMethods((store) => {
    const api = inject(LoansApi);
    const toasts = inject(ToastService);
    const inFlight = new Map<string, Promise<boolean>>();

    const query = (): Observable<Page<AdminLoanDto>> =>
      api.listAll({
        page: store.pageIndex(),
        size: PAGE_SIZE,
        sort: SORT,
        status: store.status() ?? undefined,
        applicantEmail: store.email() ?? undefined,
      });

    const load = rxMethod<void>(
      pipe(
        tap(() => patchState(store, { loading: true })),
        switchMap(() =>
          query().pipe(
            tap((page) => patchState(store, { page, loading: false, error: null })),
            catchError((e: unknown) => {
              patchState(store, { loading: false, error: toAppError(e) });
              return EMPTY;
            }),
          ),
        ),
      ),
    );

    const highlight = (id: string) => {
      patchState(store, { highlighted: [...store.highlighted(), id] });
      setTimeout(
        () => patchState(store, { highlighted: store.highlighted().filter((h) => h !== id) }),
        HIGHLIGHT_MS,
      );
    };

    const nameOf = (id: string) =>
      store.page()?.items.find((l) => l.id === id)?.applicantName ?? 'la persona';

    /** Una decisión: un solo envío por préstamo, aunque se haga doble clic. */
    const decide = (id: string, send: () => Observable<unknown>, done: string): Promise<boolean> => {
      const running = inFlight.get(id);
      if (running) return running;
      const name = nameOf(id);
      patchState(store, { deciding: [...store.deciding(), id] });
      const started = firstValueFrom(send())
        .then(() => {
          toasts.show({ tone: 'success', message: `${done} ${name}.` });
          return true;
        })
        .catch((e: unknown) => {
          const error = isAppError(e) ? e : toAppError(e);
          const conflict = CONFLICTS.includes(error.code);
          toasts.show({ tone: conflict ? 'warning' : 'danger', message: error.detail });
          return false;
        })
        .finally(() => {
          inFlight.delete(id);
          patchState(store, { deciding: store.deciding().filter((d) => d !== id) });
          load();
        });
      inFlight.set(id, started);
      return started;
    };

    return {
      reload: () => load(),

      setQuery(q: AdminLoansQuery): void {
        patchState(store, { status: q.status, email: q.email, pageIndex: q.page, newArrivals: 0 });
        load();
      },

      clearArrivals(): void {
        patchState(store, { newArrivals: 0 });
      },

      approve(id: string): Promise<boolean> {
        return decide(id, () => api.approve(id), 'Se aprobó el préstamo de');
      },

      reject(id: string, reason: string): Promise<boolean> {
        return decide(id, () => api.reject(id, { reason }), 'Se rechazó el préstamo de');
      },

      /** Recarga la página actual; si la solicitud nueva no cae en ella, la cuenta como llegada. */
      async applyLiveEvent(e: LoanEventDto): Promise<void> {
        try {
          const page = await firstValueFrom(query());
          patchState(store, { page, error: null });
          if (page.items.some((l) => l.id === e.loanId)) highlight(e.loanId);
          else if (e.type === 'loan.requested') {
            patchState(store, { newArrivals: store.newArrivals() + 1 });
          }
        } catch {
          load();
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
