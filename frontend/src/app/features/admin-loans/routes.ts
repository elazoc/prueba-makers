import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Routes } from '@angular/router';

/** Provisional: la pantalla real llega en la Tarea 13. */
@Component({
  selector: 'app-placeholder',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: '<p>Solicitudes</p>',
})
class Placeholder {}

export const ADMIN_LOANS_ROUTES: Routes = [{ path: '', component: Placeholder }];
