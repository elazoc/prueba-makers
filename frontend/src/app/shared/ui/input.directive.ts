import { Directive, inject } from '@angular/core';
import { PrField } from './field';

/** Control dentro de un `pr-field`: toma su id y sus atributos de accesibilidad. */
@Directive({
  selector: 'input[prInput], textarea[prInput], select[prInput]',
  host: {
    class: 'pr-input',
    '[id]': 'field.inputId',
    '[attr.aria-describedby]': 'field.describedBy()',
    '[attr.aria-invalid]': "field.error() ? 'true' : null",
  },
})
export class PrInput {
  protected readonly field = inject(PrField);
}
