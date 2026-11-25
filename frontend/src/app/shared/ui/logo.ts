import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * Logo de Prestemos (propuesta B, elegida por Ever): el trazo de la P, su anillo y un punto,
 * el mismo gesto del cargador. `full` suma el nombre.
 */
@Component({
  selector: 'pr-logo',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'pr-logo',
    '[class.pr-logo--on-sky]': "tone() === 'on-sky'",
    role: 'img',
    'aria-label': 'Prestemos',
  },
  template: `
    <svg class="pr-logo__mark" viewBox="0 0 32 32" [attr.width]="size()" [attr.height]="size()" aria-hidden="true" focusable="false">
      <g class="pr-logo__ink" fill="none" stroke-width="3.4" stroke-linecap="round">
        <path d="M10 26V8" />
        <circle cx="16" cy="13.5" r="6" />
      </g>
      <circle class="pr-logo__accent" cx="21.6" cy="7.9" r="2.4" />
    </svg>
    @if (variant() === 'full') {
      <span class="pr-logo__name" aria-hidden="true">Prestemos</span>
    }
  `,
})
export class PrLogo {
  readonly variant = input<'full' | 'mark'>('full');
  readonly tone = input<'on-light' | 'on-sky'>('on-light');
  readonly size = input(32);
}
