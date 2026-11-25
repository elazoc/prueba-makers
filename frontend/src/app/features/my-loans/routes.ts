import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Routes } from '@angular/router';

/** Provisional: la pantalla real llega en la Tarea 12. */
@Component({
  selector: 'app-placeholder',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: '<p>Mis préstamos</p>',
})
class Placeholder {}

export const MY_LOANS_ROUTES: Routes = [{ path: '', component: Placeholder }];
