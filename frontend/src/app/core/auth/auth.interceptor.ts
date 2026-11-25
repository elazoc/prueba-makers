import { HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, from, switchMap, throwError } from 'rxjs';
import { API_BASE_URL } from '../api/api-config';
import { isAppError } from '../api/app-error';
import { AuthStore } from './auth.store';

const withBearer = (req: HttpRequest<unknown>, token: string) =>
  req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });

/**
 * Agrega el token de acceso solo a la API (nunca a /auth/* ni a otros dominios). Ante un 401,
 * renueva una vez (compartiendo la renovación en curso) y reintenta; si no se puede, lleva a
 * ingresar con la ruta para volver.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthStore);
  const base = inject(API_BASE_URL);
  const router = inject(Router);
  const token = auth.accessToken();

  const isApi = req.url.startsWith(`${base}/`);
  const isAuthRoute = req.url.startsWith(`${base}/auth/`);
  if (!isApi || isAuthRoute || !token) return next(req);

  return next(withBearer(req, token)).pipe(
    catchError((e: unknown) => {
      if (!isAppError(e) || e.status !== 401) return throwError(() => e);
      return from(auth.refresh()).pipe(
        switchMap((renewed) => {
          const fresh = auth.accessToken();
          if (renewed && fresh) return next(withBearer(req, fresh));
          if (!auth.isAuthenticated()) {
            void router.navigate(['/ingresar'], {
              queryParams: { volver: router.url, motivo: auth.endReason() ?? 'expired' },
            });
          }
          return throwError(() => e);
        }),
      );
    }),
  );
};
