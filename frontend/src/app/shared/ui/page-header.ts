import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Encabezado de pantallas que no son listas: título, una línea de para qué sirve y acciones a la derecha. */
@Component({
  selector: 'pr-page-header',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'pr-page-header' },
  template: `
    <div class="pr-page-header__text">
      <h1 class="pr-page-header__title">{{ title() }}</h1>
      @if (description()) {
        <p class="pr-page-header__description">{{ description() }}</p>
      }
    </div>
    <div class="pr-page-header__actions"><ng-content /></div>
  `,
})
export class PrPageHeader {
  readonly title = input.required<string>();
  readonly description = input<string>();
}
