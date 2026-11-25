import { AbstractControl, FormGroup } from '@angular/forms';
import { AppError } from '../../core/api/app-error';

/** Errores del servidor que van a un campo concreto según su código (p. ej. correo repetido). */
export type CodeToField = Record<string, { field: string; message?: string }>;

/**
 * Pone los errores del servidor en sus campos (`{ server: mensaje }`). Devuelve el detalle si
 * no quedó asignado a ningún campo, para mostrarlo arriba del botón.
 */
export function applyServerErrors(form: FormGroup, e: AppError, byCode: CodeToField = {}): string | null {
  let assigned = false;
  const put = (control: AbstractControl | null, message: string) => {
    if (!control) return;
    control.setErrors({ ...(control.errors ?? {}), server: message });
    control.markAsTouched();
    assigned = true;
  };

  for (const [field, message] of Object.entries(e.fieldErrors)) put(form.get(field), message);
  const mapped = byCode[e.code];
  if (mapped) put(form.get(mapped.field), mapped.message ?? e.detail);

  return assigned ? null : e.detail;
}

/** Enfoca el primer campo marcado como inválido dentro de `host`. */
export function focusFirstInvalid(host: HTMLElement): void {
  host.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus();
}
