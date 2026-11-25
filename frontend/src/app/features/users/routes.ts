import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Routes } from '@angular/router';

/** Provisional: la pantalla real llega en la Tarea 14. */
@Component({
  selector: 'app-placeholder',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: '<p>Usuarios</p>',
})
class Placeholder {}

export const USERS_ROUTES: Routes = [{ path: '', component: Placeholder }];
