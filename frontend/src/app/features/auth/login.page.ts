import { ChangeDetectionStrategy, Component } from '@angular/core';

/** Provisional: la pantalla real llega en la Tarea 10. */
@Component({
  selector: 'app-login-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: '<h1>Ingresa a Prestemos</h1>',
})
export class LoginPage {}
