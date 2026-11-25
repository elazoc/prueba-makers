import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';
import { toCents } from '../money/money';
import { LOAN_POLICY } from './loan-policy';

const isEmpty = (value: unknown) => value === null || value === undefined || value === '';

/** Valida un monto ya normalizado ("1500000.00") contra la política. */
export const amountValidator: ValidatorFn = (control): ValidationErrors | null => {
  const value = control.value as string | null;
  if (isEmpty(value)) return { required: true };

  const cents = toCents(value as string);
  if (cents < toCents(LOAN_POLICY.minAmount)) return { amountMin: { min: LOAN_POLICY.minAmount } };
  if (cents > toCents(LOAN_POLICY.maxAmount)) return { amountMax: { max: LOAN_POLICY.maxAmount } };
  return null;
};

/** Valida el plazo en meses enteros contra la política. */
export const termValidator: ValidatorFn = (control): ValidationErrors | null => {
  const value = control.value as number | null;
  if (isEmpty(value)) return { required: true };
  if (!Number.isInteger(value)) return { termInteger: true };
  if ((value as number) < LOAN_POLICY.minTermMonths) {
    return { termMin: { min: LOAN_POLICY.minTermMonths } };
  }
  if ((value as number) > LOAN_POLICY.maxTermMonths) {
    return { termMax: { max: LOAN_POLICY.maxTermMonths } };
  }
  return null;
};

/** Validador de grupo: marca `second` con `{ mismatch }` si no coincide con `first`. */
export function matchValidator(first: string, second: string): ValidatorFn {
  return (group: AbstractControl): ValidationErrors | null => {
    const a = group.get(first);
    const b = group.get(second);
    if (!a || !b) return null;

    const others = { ...(b.errors ?? {}) };
    delete others['mismatch'];
    const rest = Object.keys(others).length ? others : null;

    if (!isEmpty(b.value) && a.value !== b.value) {
      b.setErrors({ ...(rest ?? {}), mismatch: true });
    } else if (b.hasError('mismatch')) {
      b.setErrors(rest);
    }
    return null;
  };
}
