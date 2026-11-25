import { Routes } from '@angular/router';
import { authGuard, guestGuard, homeGuard, roleGuard } from './core/auth/guards';
import { Shell } from './core/layout/shell';

export const routes: Routes = [
  { path: '', pathMatch: 'full', canMatch: [homeGuard], children: [] },
  {
    path: 'ingresar',
    title: 'Ingresar · Prestemos',
    canMatch: [guestGuard],
    loadComponent: () => import('./features/auth/login.page').then((m) => m.LoginPage),
  },
  {
    path: 'registro',
    title: 'Crear cuenta · Prestemos',
    canMatch: [guestGuard],
    loadComponent: () => import('./features/auth/register.page').then((m) => m.RegisterPage),
  },
  {
    path: '',
    component: Shell,
    children: [
      {
        path: 'prestamos',
        title: 'Mis préstamos · Prestemos',
        data: { title: 'Mis préstamos' },
        canMatch: [authGuard, roleGuard('USER')],
        loadChildren: () => import('./features/my-loans/routes').then((m) => m.MY_LOANS_ROUTES),
      },
      {
        path: 'admin/solicitudes',
        title: 'Solicitudes · Prestemos',
        data: { title: 'Solicitudes' },
        canMatch: [authGuard, roleGuard('ADMIN')],
        loadChildren: () =>
          import('./features/admin-loans/routes').then((m) => m.ADMIN_LOANS_ROUTES),
      },
    ],
  },
  {
    path: '**',
    title: 'Página no encontrada · Prestemos',
    loadComponent: () => import('./features/not-found/not-found.page').then((m) => m.NotFoundPage),
  },
];
