import { ChangeDetectionStrategy, Component } from '@angular/core';

/** Provisional: la pantalla real llega en la Tarea 10. */
@Component({
  selector: 'app-register-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: '<h1>Crea tu cuenta</h1>',
})
export class RegisterPage {}
