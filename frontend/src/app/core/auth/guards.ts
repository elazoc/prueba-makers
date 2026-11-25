import { inject } from '@angular/core';
import { toObservable } from '@angular/core/rxjs-interop';
import { CanMatchFn, Router, UrlSegment } from '@angular/router';
import { filter, map, take } from 'rxjs';
import { Role } from '../api/dto';
import { ToastService } from '../notify/toast.service';
import { AuthStore } from './auth.store';

/**
 * Los guards ordenan la experiencia; la seguridad la pone el backend (403/404 igual).
 * Se usan como `canMatch` para que el código de una sección ni se descargue si no aplica.
 * Todos esperan a que termine la restauración de la sesión.
 */
function whenSessionKnown() {
  const auth = inject(AuthStore);
  return toObservable(auth.status).pipe(
    filter((s) => s !== 'restoring'),
    take(1),
    map(() => auth),
  );
}

const urlOf = (segments: UrlSegment[]) => `/${segments.map((s) => s.path).join('/')}`;

const ROLE_NOTICE: Record<Role, string> = {
  ADMIN: 'Esa sección es solo para administradores.',
  USER: 'Esa sección es para quienes solicitan préstamos.',
};

export const authGuard: CanMatchFn = (_route, segments) => {
  const router = inject(Router);
  return whenSessionKnown().pipe(
    map((auth) =>
      auth.isAuthenticated()
        ? true
        : router.createUrlTree(['/ingresar'], { queryParams: { volver: urlOf(segments) } }),
    ),
  );
};

export function roleGuard(role: Role): CanMatchFn {
  return () => {
    const router = inject(Router);
    const toasts = inject(ToastService);
    return whenSessionKnown().pipe(
      map((auth) => {
        if (auth.role() === role) return true;
        if (auth.isAuthenticated()) toasts.show({ tone: 'info', message: ROLE_NOTICE[role] });
        return router.parseUrl(auth.homeUrl());
      }),
    );
  };
}

export const guestGuard: CanMatchFn = () => {
  const router = inject(Router);
  return whenSessionKnown().pipe(
    map((auth) => (auth.isAuthenticated() ? router.parseUrl(auth.homeUrl()) : true)),
  );
};

/** La raíz lleva al inicio de cada rol (o a ingresar). */
export const homeGuard: CanMatchFn = () => {
  const router = inject(Router);
  return whenSessionKnown().pipe(map((auth) => router.parseUrl(auth.homeUrl())));
};
