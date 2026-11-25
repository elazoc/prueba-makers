import { HttpErrorResponse } from '@angular/common/http';
import { isAppError, toAppError } from './app-error';

describe('toAppError', () => {
  it('maps problem details with field errors', () => {
    const e = toAppError(
      new HttpErrorResponse({
        status: 400,
        error: {
          status: 400,
          code: 'VALIDATION_ERROR',
          detail: 'Revisa los campos.',
          errors: [{ field: 'amount', message: 'Debe ser positivo.' }],
        },
      }),
    );
    expect(e).toEqual({
      kind: 'app-error',
      status: 400,
      code: 'VALIDATION_ERROR',
      detail: 'Revisa los campos.',
      fieldErrors: { amount: 'Debe ser positivo.' },
    });
  });

  it('explains a network failure', () => {
    const e = toAppError(new HttpErrorResponse({ status: 0 }));
    expect(e.code).toBe('NETWORK_ERROR');
    expect(e.detail).toBe('No se pudo conectar con el servidor. Revisa tu conexión e intenta de nuevo.');
  });

  it('shows the error id of a server failure', () => {
    const e = toAppError(
      new HttpErrorResponse({
        status: 500,
        error: { status: 500, code: 'INTERNAL_ERROR', detail: 'Error interno.', errorId: 'abc123' },
      }),
    );
    expect(e.errorId).toBe('abc123');
    expect(e.detail).toBe(
      'Algo salió mal de nuestro lado. Intenta de nuevo; si sigue pasando, comparte este código: abc123.',
    );
  });

  it('handles a body that is not problem details', () => {
    const e = toAppError(new HttpErrorResponse({ status: 502, error: '<html>Bad gateway</html>' }));
    expect(e).toMatchObject({ status: 502, code: 'UNEXPECTED', detail: 'Algo salió mal. Intenta de nuevo en un momento.' });
  });

  it('passes an existing app error through', () => {
    const original = toAppError(new HttpErrorResponse({ status: 0 }));
    expect(toAppError(original)).toBe(original);
    expect(isAppError(original)).toBe(true);
    expect(isAppError(new Error('x'))).toBe(false);
  });
});
