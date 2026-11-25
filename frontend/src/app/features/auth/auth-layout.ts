import { ChangeDetectionStrategy, Component } from '@angular/core';
import { PrLogo } from '../../shared/ui/logo';

/** Ingreso y registro: el cielo a la izquierda (desde 1024 px) y el formulario a la derecha. */
@Component({
  selector: 'app-auth-layout',
  imports: [PrLogo],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'pr-auth' },
  template: `
    <aside class="pr-auth__sky">
      <pr-logo variant="full" tone="on-sky" [size]="40" />
      <div class="pr-auth__pitch">
        <p class="pr-auth__headline">Solicita y sigue tus préstamos en un solo lugar.</p>
        <p class="pr-auth__sub">Pides el monto y el plazo, y te avisamos apenas haya una decisión.</p>
      </div>
    </aside>
    <main class="pr-auth__main">
      <pr-logo class="pr-auth__logo-mobile" variant="full" />
      <div class="pr-auth__panel"><ng-content /></div>
    </main>
  `,
})
export class AuthLayout {}
