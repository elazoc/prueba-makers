import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  input,
  signal,
} from '@angular/core';
import { AbstractControl } from '@angular/forms';
import { errorMessage } from '../forms/error-messages';
import { PrIcon } from './icon';

let nextId = 0;

/**
 * Campo con etiqueta arriba, ayuda debajo y error que reemplaza a la ayuda.
 * El `<input prInput>` proyectado toma de aquí su `id`, `aria-describedby` y `aria-invalid`.
 */
@Component({
  selector: 'pr-field',
  imports: [PrIcon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'pr-field', '[class.pr-field--error]': '!!error()' },
  template: `
    <label class="pr-field__label" [attr.for]="inputId">{{ label() }}</label>
    <ng-content />
    @if (error(); as message) {
      <p class="pr-field__message pr-field__message--error" [id]="messageId">
        <pr-icon name="circle-alert" size="sm" />{{ message }}
      </p>
    } @else if (hint()) {
      <p class="pr-field__message" [id]="messageId">{{ hint() }}</p>
    }
  `,
})
export class PrField {
  readonly label = input.required<string>();
  readonly hint = input<string>();
  readonly control = input<AbstractControl | null>(null);

  readonly inputId = `pr-field-${++nextId}`;
  readonly messageId = `${this.inputId}-message`;

  /** Cambia con cada evento del control (valor, estado, tocado) para recalcular el error. */
  private readonly version = signal(0);

  readonly error = computed(() => {
    this.version();
    const c = this.control();
    if (!c || !c.invalid || !c.touched) return null;
    return errorMessage(c.errors);
  });

  readonly describedBy = computed(() => (this.error() || this.hint() ? this.messageId : null));

  constructor() {
    effect((onCleanup) => {
      const c = this.control();
      if (!c) return;
      const sub = c.events.subscribe(() => this.version.update((v) => v + 1));
      onCleanup(() => sub.unsubscribe());
    });
  }
}
