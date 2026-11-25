import { HttpErrorResponse } from '@angular/common/http';
import { LoginResponse, Role } from '../api/dto';
import { AppError, toAppError } from '../api/app-error';

/** Utilidades de prueba de la sesión (solo para specs). */
export function loginResponseFor(id: string, token = `token-${id}`, role: Role = 'USER', expiresIn = 900): LoginResponse {
  return {
    accessToken: token,
    tokenType: 'Bearer',
    expiresIn,
    user: {
      id,
      email: `${id}@test.com`,
      fullName: `Persona ${id}`,
      role,
      active: true,
      createdAt: '2026-11-01T00:00:00Z',
    },
  };
}

export function appError(status: number, code: string, detail = 'Detalle'): AppError {
  return toAppError(new HttpErrorResponse({ status, error: { status, code, detail } }));
}
