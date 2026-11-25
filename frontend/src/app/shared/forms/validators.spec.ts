import { FormControl, FormGroup } from '@angular/forms';
import { errorMessage } from './error-messages';
import { LOAN_POLICY } from './loan-policy';
import { amountValidator, matchValidator, termValidator } from './validators';

const ctl = (value: unknown) => new FormControl(value);

describe('amountValidator', () => {
  it('requires a value', () => {
    expect(amountValidator(ctl(null))).toEqual({ required: true });
    expect(amountValidator(ctl(''))).toEqual({ required: true });
  });

  it('enforces the policy bounds', () => {
    expect(amountValidator(ctl('99.99'))).toEqual({ amountMin: { min: '100.00' } });
    expect(amountValidator(ctl('50000000.01'))).toEqual({ amountMax: { max: '50000000.00' } });
    expect(amountValidator(ctl('100.00'))).toBeNull();
    expect(amountValidator(ctl('50000000.00'))).toBeNull();
  });
});

describe('termValidator', () => {
  it('validates whole months within the policy', () => {
    expect(termValidator(ctl(null))).toEqual({ required: true });
    expect(termValidator(ctl(0))).toEqual({ termMin: { min: 1 } });
    expect(termValidator(ctl(121))).toEqual({ termMax: { max: 120 } });
    expect(termValidator(ctl(1.5))).toEqual({ termInteger: true });
    expect(termValidator(ctl(12))).toBeNull();
  });
});

describe('matchValidator', () => {
  it('marks the second control when values differ and clears it when they match', () => {
    const group = new FormGroup(
      { password: ctl('secreto123'), confirm: ctl('otro') },
      { validators: matchValidator('password', 'confirm') },
    );
    expect(group.controls.confirm.errors).toEqual({ mismatch: true });

    group.controls.confirm.setValue('secreto123');
    expect(group.controls.confirm.errors).toBeNull();
  });
});

describe('LOAN_POLICY', () => {
  it('mirrors the backend defaults', () => {
    expect(LOAN_POLICY).toEqual({
      minAmount: '100.00',
      maxAmount: '50000000.00',
      minTermMonths: 1,
      maxTermMonths: 120,
      maxPending: 3,
    });
  });
});

describe('errorMessage', () => {
  it.each([
    [{ required: true }, 'Completa este campo.'],
    [{ amountMin: { min: '100.00' } }, 'El monto mínimo es $\u00a0100.'],
    [{ amountMax: { max: '50000000.00' } }, 'El monto máximo es $\u00a050.000.000.'],
    [{ termInteger: true }, 'Escribe el plazo en meses enteros.'],
    [{ termMin: { min: 1 } }, 'El plazo va de 1 a 120 meses.'],
    [{ termMax: { max: 120 } }, 'El plazo va de 1 a 120 meses.'],
    [{ email: true }, 'Escribe un correo válido, por ejemplo nombre@correo.com.'],
    [{ minlength: { requiredLength: 8, actualLength: 3 } }, 'Usa al menos 8 caracteres.'],
    [{ maxlength: { requiredLength: 500, actualLength: 501 } }, 'Usa máximo 500 caracteres.'],
    [{ mismatch: true }, 'Las contraseñas no coinciden.'],
    [{ server: 'Ya hay una cuenta con este correo.' }, 'Ya hay una cuenta con este correo.'],
  ])('describes %o', (errors, message) => {
    expect(errorMessage(errors)).toBe(message);
  });

  it('returns null without errors', () => {
    expect(errorMessage(null)).toBeNull();
  });
});
