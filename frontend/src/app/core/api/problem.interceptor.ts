import { HttpInterceptorFn } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';
import { toAppError } from './app-error';

/** Convierte cualquier error HTTP en un `AppError` tipado. */
export const problemInterceptor: HttpInterceptorFn = (req, next) =>
  next(req).pipe(catchError((e: unknown) => throwError(() => toAppError(e))));
