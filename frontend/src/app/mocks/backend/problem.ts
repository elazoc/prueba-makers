/** Error del simulador con la misma forma que el Problem Details del backend. */
export class MockProblem extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    readonly detail: string,
    readonly errors?: { field: string; message: string }[],
  ) {
    super(detail);
  }
}

const TITLES: Record<number, string> = {
  400: 'Solicitud inválida',
  401: 'No autenticado',
  403: 'Sin permiso',
  404: 'No encontrado',
  409: 'Conflicto',
  422: 'Regla de negocio incumplida',
};

export function problemJson(p: MockProblem): Record<string, unknown> {
  return {
    type: `/problems/${p.code.toLowerCase().replace(/_/g, '-')}`,
    title: TITLES[p.status] ?? 'Error',
    status: p.status,
    detail: p.detail,
    code: p.code,
    timestamp: new Date().toISOString(),
    ...(p.errors ? { errors: p.errors } : {}),
  };
}

/** Lanza 400 VALIDATION_ERROR si hay errores de campo. */
export function assertValid(errors: { field: string; message: string }[]): void {
  if (errors.length) {
    throw new MockProblem(400, 'VALIDATION_ERROR', 'Revisa los datos marcados.', errors);
  }
}

export const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
