import { Directive, ElementRef, forwardRef, inject } from '@angular/core';
import {
  ControlValueAccessor,
  NG_VALIDATORS,
  NG_VALUE_ACCESSOR,
  ValidationErrors,
  Validator,
} from '@angular/forms';
import { formatMoney, formatMoneyDraft, parseMoneyInput } from '../money/money';

/**
 * Campo de monto: muestra «1.500.000» mientras se escribe y entrega al formulario el texto
 * de la API («1500000.00»). Si el texto no se puede leer sin adivinar (p. ej. «1,500,000.00»),
 * no lo reformatea y marca `{ moneyFormat }`.
 */
@Directive({
  selector: 'input[prMoneyInput]',
  host: {
    inputmode: 'decimal',
    autocomplete: 'off',
    '(input)': 'onInput()',
    '(blur)': 'onTouched()',
  },
  providers: [
    { provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => PrMoneyInput), multi: true },
    { provide: NG_VALIDATORS, useExisting: forwardRef(() => PrMoneyInput), multi: true },
  ],
})
export class PrMoneyInput implements ControlValueAccessor, Validator {
  private readonly input = inject<ElementRef<HTMLInputElement>>(ElementRef).nativeElement;
  private formatError: 'format' | 'decimals' | null = null;
  private onChange: (value: string | null) => void = () => undefined;
  protected onTouched: () => void = () => undefined;

  writeValue(value: string | null): void {
    this.formatError = null;
    // formatMoney da «$ 1.500.000»: aquí va sin el símbolo.
    this.input.value = value ? formatMoney(value).replace(/^\$\s/, '') : '';
  }

  registerOnChange(fn: (value: string | null) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(disabled: boolean): void {
    this.input.disabled = disabled;
  }

  validate(): ValidationErrors | null {
    return this.formatError ? { moneyFormat: this.formatError } : null;
  }

  protected onInput(): void {
    const raw = this.input.value;
    const parsed = parseMoneyInput(raw);

    if (parsed.ok) {
      this.formatError = null;
      this.reformat(raw);
      this.onChange(parsed.value);
    } else if (parsed.reason === 'empty') {
      this.formatError = null;
      this.onChange(null);
    } else {
      this.formatError = parsed.reason;
      this.onChange(null);
    }
  }

  /** Agrupa los miles y deja el cursor después del mismo número de dígitos. */
  private reformat(raw: string): void {
    const caret = this.input.selectionStart ?? raw.length;
    const digitsBefore = raw.slice(0, caret).replace(/[^\d,]/g, '').length;
    const formatted = formatMoneyDraft(raw);
    if (formatted === raw) return;

    this.input.value = formatted;
    let seen = 0;
    let position = formatted.length;
    for (let i = 0; i < formatted.length; i++) {
      if (seen === digitsBefore) {
        position = i;
        break;
      }
      if (/[\d,]/.test(formatted[i])) seen++;
    }
    this.input.setSelectionRange(position, position);
  }
}
