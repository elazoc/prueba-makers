import { inject } from '@angular/core';
import { patchState, signalStore, withMethods, withState } from '@ngrx/signals';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { EMPTY, catchError, firstValueFrom, pipe, switchMap, tap } from 'rxjs';
import { AppError, toAppError } from '../../core/api/app-error';
import { CreateUserRequest, Role, UpdateUserRequest, UserDto } from '../../core/api/dto';
import { Page } from '../../core/api/page';
import { UsersApi } from '../../core/api/users.api';

interface UsersState {
  page: Page<UserDto> | null;
  role: Role | null;
  active: boolean | null;
  pageIndex: number;
  loading: boolean;
  error: AppError | null;
}

export interface UsersQuery {
  role: Role | null;
  active: boolean | null;
  page: number;
}

const PAGE_SIZE = 20;

/** Usuarios para el administrador. Tras cada cambio se vuelve a pedir la página. */
export const UsersStore = signalStore(
  withState<UsersState>({
    page: null,
    role: null,
    active: null,
    pageIndex: 0,
    loading: false,
    error: null,
  }),
  withMethods((store) => {
    const api = inject(UsersApi);
    const inFlight = new Map<string, Promise<unknown>>();

    const once = <T>(key: string, run: () => Promise<T>): Promise<T> => {
      const running = inFlight.get(key) as Promise<T> | undefined;
      if (running) return running;
      const started = run().finally(() => inFlight.delete(key));
      inFlight.set(key, started);
      return started;
    };

    const load = rxMethod<void>(
      pipe(
        tap(() => patchState(store, { loading: true })),
        switchMap(() =>
          api
            .list({
              page: store.pageIndex(),
              size: PAGE_SIZE,
              sort: 'createdAt,desc',
              role: store.role() ?? undefined,
              active: store.active() ?? undefined,
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

    return {
      reload: () => load(),

      setQuery(q: UsersQuery): void {
        patchState(store, { role: q.role, active: q.active, pageIndex: q.page });
        load();
      },

      create(r: CreateUserRequest): Promise<UserDto> {
        return once('create', async () => {
          const user = await firstValueFrom(api.create(r));
          load();
          return user;
        });
      },

      update(id: string, r: UpdateUserRequest): Promise<UserDto> {
        return once(`update:${id}`, async () => {
          const user = await firstValueFrom(api.update(id, r));
          load();
          return user;
        });
      },

      deactivate(id: string): Promise<void> {
        return once(`deactivate:${id}`, async () => {
          await firstValueFrom(api.deactivate(id));
          load();
        });
      },
    };
  }),
);
