import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { PrButton } from '../../shared/ui/button';
import { PrLogo } from '../../shared/ui/logo';

@Component({
  selector: 'app-not-found-page',
  imports: [RouterLink, PrButton, PrLogo],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'pr-standalone-page' },
  template: `
    <main class="pr-standalone-page__card">
      <pr-logo variant="full" />
      <h1 class="pr-standalone-page__title">No encontramos esta página</h1>
      <p class="pr-standalone-page__text">Puede que el enlace esté mal escrito o que la página ya no exista.</p>
      <a prButton routerLink="/">Volver al inicio</a>
    </main>
  `,
})
export class NotFoundPage {}
