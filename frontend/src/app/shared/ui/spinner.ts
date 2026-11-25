import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * Cargador de Prestemos: un punto que recorre un anillo, el mismo gesto del logo.
 * Es decorativo: lo que pasa lo dice la palabra que lo acompaña («Aprobando…»).
 */
@Component({
  selector: 'pr-spinner',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'pr-spinner',
    '[class.pr-spinner--on-action]': "tone() === 'on-action'",
    'aria-hidden': 'true',
  },
  template: `<svg viewBox="0 0 20 20" focusable="false">
    <circle class="pr-spinner__ring" cx="10" cy="10" r="7" />
    <g class="pr-spinner__orbit"><circle class="pr-spinner__dot" cx="10" cy="3" r="2.25" /></g>
  </svg>`,
})
export class PrSpinner {
  readonly tone = input<'default' | 'on-action'>('default');
}
