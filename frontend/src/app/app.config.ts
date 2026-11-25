import { registerLocaleData } from '@angular/common';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import localeEsCO from '@angular/common/locales/es-CO';
import {
  ApplicationConfig,
  LOCALE_ID,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { environment } from '../environments/environment';
import { routes } from './app.routes';
import { API_BASE_URL } from './core/api/api-config';
import { problemInterceptor } from './core/api/problem.interceptor';
import { authInterceptor } from './core/auth/auth.interceptor';
import { AuthStore } from './core/auth/auth.store';

registerLocaleData(localeEsCO);

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withFetch(), withInterceptors([authInterceptor, problemInterceptor])),
    { provide: API_BASE_URL, useValue: environment.apiBaseUrl },
    { provide: LOCALE_ID, useValue: 'es-CO' },
    // Restaura la sesión con la cookie del refresh token. No bloquea el arranque: la app muestra
    // el cargador mientras `status` es 'restoring' y los guards esperan a que termine.
    provideAppInitializer(() => {
      void inject(AuthStore).restore();
    }),
  ],
};
