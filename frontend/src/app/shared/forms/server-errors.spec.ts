import { HttpErrorResponse } from '@angular/common/http';
import { FormControl, FormGroup } from '@angular/forms';
import { toAppError } from '../../core/api/app-error';
import { applyServerErrors } from './server-errors';

const error = (status: number, code: string, detail: string, errors: { field: string; message: string }[] = []) =>
  toAppError(new HttpErrorResponse({ status, error: { status, code, detail, errors } }));

describe('applyServerErrors', () => {
  const form = () => new FormGroup({ email: new FormControl(''), amount: new FormControl('') });

  it('puts field errors on their controls', () => {
    const f = form();
    const left = applyServerErrors(f, error(400, 'VALIDATION_ERROR', 'Revisa.', [{ field: 'amount', message: 'Debe ser positivo.' }]));
    expect(left).toBeNull();
    expect(f.controls.amount.errors).toEqual({ server: 'Debe ser positivo.' });
    expect(f.controls.amount.touched).toBe(true);
  });

  it('maps a code to a field with its own message', () => {
    const f = form();
    const left = applyServerErrors(f, error(409, 'EMAIL_ALREADY_REGISTERED', 'Ya existe.'), {
      EMAIL_ALREADY_REGISTERED: { field: 'email', message: 'Ya hay una cuenta con este correo.' },
    });
    expect(left).toBeNull();
    expect(f.controls.email.errors).toEqual({ server: 'Ya hay una cuenta con este correo.' });
  });

  it('returns the detail when nothing was assigned to a field', () => {
    expect(applyServerErrors(form(), error(422, 'LOAN_POLICY_VIOLATION', 'Detalle'))).toBe('Detalle');
  });
});
