import { HttpParams } from '@angular/common/http';
import { InjectionToken } from '@angular/core';

/** Prefijo de la API ("/api/v1"). Viene del entorno. */
export const API_BASE_URL = new InjectionToken<string>('API_BASE_URL');

/** Arma parámetros de consulta omitiendo los valores `undefined` o `null`. */
export function toParams(query: Record<string, string | number | boolean | null | undefined>) {
  let params = new HttpParams();
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined && value !== null && value !== '') params = params.set(key, String(value));
  }
  return params;
}
