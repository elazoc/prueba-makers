import { ValidationErrors } from '@angular/forms';
import { formatMoney } from '../money/money';

type Describe = (detail: unknown) => string;

const RANGE_TERM = 'El plazo va de 1 a 120 meses.';

/** El orden importa: se muestra el primer error que aplique. */
const MESSAGES: [string, Describe][] = [
  ['server', (detail) => String(detail)],
  ['required', () => 'Completa este campo.'],
  ['email', () => 'Escribe un correo válido, por ejemplo nombre@correo.com.'],
  ['minlength', (d) => `Usa al menos ${(d as { requiredLength: number }).requiredLength} caracteres.`],
  ['maxlength', (d) => `Usa máximo ${(d as { requiredLength: number }).requiredLength} caracteres.`],
  ['amountMin', (d) => `El monto mínimo es ${formatMoney((d as { min: string }).min)}.`],
  ['amountMax', (d) => `El monto máximo es ${formatMoney((d as { max: string }).max)}.`],
  ['termInteger', () => 'Escribe el plazo en meses enteros.'],
  ['termMin', () => RANGE_TERM],
  ['termMax', () => RANGE_TERM],
  ['mismatch', () => 'Las contraseñas no coinciden.'],
];

/** Texto en español para el primer error de un control, o `null` si no hay. */
export function errorMessage(errors: ValidationErrors | null): string | null {
  if (!errors) return null;
  for (const [key, describe] of MESSAGES) {
    if (key in errors) return describe(errors[key]);
  }
  return null;
}
