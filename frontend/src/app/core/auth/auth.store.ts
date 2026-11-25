import { computed, inject } from '@angular/core';
import { patchState, signalStore, withComputed, withHooks, withMethods, withState } from '@ngrx/signals';
import { firstValueFrom } from 'rxjs';
import { isAppError } from '../api/app-error';
import { LoginResponse, RegisterRequest, UserDto } from '../api/dto';
import { AuthApi } from './auth.api';
import { RefreshCoordinator } from './refresh-coordinator';
import { SessionChannel } from './session-channel';

export type SessionEndReason = 'logout' | 'expired' | 'disabled' | 'elsewhere' | 'switched';
export type SessionStatus = 'restoring' | 'anonymous' | 'authenticated';

interface AuthState {
  user: UserDto | null;
  /** Solo en memoria: nunca en localStorage, sessionStorage ni cookies. */
  accessToken: string | null;
  expiresAt: number | null;
  status: SessionStatus;
  endReason: SessionEndReason | null;
}

const REFRESH_AHEAD_MS = 60_000;
const NETWORK_RETRY_MS = 15_000;

const initialState: AuthState = {
  user: null,
  accessToken: null,
  expiresAt: null,
  status: 'restoring',
  endReason: null,
};

export const AuthStore = signalStore(
  { providedIn: 'root' },
  withState(initialState),
  withComputed(({ user, status }) => ({
    isAuthenticated: computed(() => status() === 'authenticated'),
    isAdmin: computed(() => user()?.role === 'ADMIN'),
    role: computed(() => user()?.role ?? null),
    displayName: computed(() => user()?.fullName ?? ''),
    homeUrl: computed(() => {
      const role = user()?.role;
      if (role === 'ADMIN') return '/admin/solicitudes';
      if (role === 'USER') return '/prestamos';
      return '/ingresar';
    }),
  })),
  withMethods((store) => {
    const api = inject(AuthApi);
    const coordinator = inject(RefreshCoordinator);
    const channel = inject(SessionChannel);
    let timer: ReturnType<typeof setTimeout> | undefined;

    const clearTimer = () => {
      if (timer !== undefined) clearTimeout(timer);
      timer = undefined;
    };

    const schedule = (delayMs: number) => {
      clearTimer();
      timer = setTimeout(() => void refresh(), Math.max(delayMs, 0));
    };

    const applySession = (res: LoginResponse) => {
      const expiresAt = Date.now() + res.expiresIn * 1000;
      patchState(store, {
        user: res.user,
        accessToken: res.accessToken,
        expiresAt,
        status: 'authenticated',
        endReason: null,
      });
      schedule(expiresAt - REFRESH_AHEAD_MS - Date.now());
    };

    const endSession = (reason: SessionEndReason) => {
      clearTimer();
      patchState(store, {
        user: null,
        accessToken: null,
        expiresAt: null,
        status: 'anonymous',
        endReason: reason,
      });
    };

    const callRefresh = () => coordinator.run(() => firstValueFrom(api.refresh()));

    /** Renueva el token de acceso. `false` si no se pudo; la sesión se cierra si el servidor la rechazó. */
    async function refresh(): Promise<boolean> {
      const current = store.user();
      try {
        const res = await callRefresh();
        if (current && res.user.id !== current.id) {
          // La cookie es una por navegador: alguien ingresó con otra cuenta en otra pestaña.
          endSession('switched');
          return false;
        }
        applySession(res);
        return true;
      } catch (e) {
        if (isAppError(e) && e.status === 401) {
          if (current) endSession(e.code === 'ACCOUNT_DISABLED' ? 'disabled' : 'expired');
          return false;
        }
        // Problema de red: se conserva la sesión y se reintenta más tarde.
        if (store.isAuthenticated()) schedule(NETWORK_RETRY_MS);
        return false;
      }
    }

    return {
      refresh,
      endSession,
      clearTimer,

      async restore(): Promise<void> {
        patchState(store, { status: 'restoring' });
        try {
          applySession(await callRefresh());
        } catch {
          patchState(store, { status: 'anonymous', endReason: null });
        }
      },

      async login(email: string, password: string): Promise<void> {
        applySession(await firstValueFrom(api.login(email, password)));
      },

      async register(r: RegisterRequest): Promise<void> {
        await firstValueFrom(api.register(r));
        applySession(await firstValueFrom(api.login(r.email, r.password)));
      },

      async logout(): Promise<void> {
        try {
          await firstValueFrom(api.logout());
        } catch {
          // Cerrar sesión en este navegador no depende de que el servidor responda.
        }
        endSession('logout');
        channel.announceLogout();
      },
    };
  }),
  withHooks((store) => {
    let unsubscribe: (() => void) | undefined;
    return {
      onInit() {
        unsubscribe = inject(SessionChannel).onLogout(() => {
          if (store.isAuthenticated()) store.endSession('elsewhere');
        });
      },
      onDestroy() {
        unsubscribe?.();
        store.clearTimer();
      },
    };
  }),
);
