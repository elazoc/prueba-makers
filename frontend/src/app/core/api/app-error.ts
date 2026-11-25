import { HttpErrorResponse } from '@angular/common/http';
import { ProblemDto } from './dto';

/** Error de la app: siempre con estado, código y un texto que se le puede mostrar a la persona. */
export interface AppError {
  kind: 'app-error';
  status: number;
  code: string;
  detail: string;
  fieldErrors: Record<string, string>;
  errorId?: string;
}

const NETWORK =
  'No se pudo conectar con el servidor. Revisa tu conexión e intenta de nuevo.';
const UNEXPECTED = 'Algo salió mal. Intenta de nuevo en un momento.';

export function isAppError(e: unknown): e is AppError {
  return typeof e === 'object' && e !== null && (e as AppError).kind === 'app-error';
}

export function toAppError(e: unknown): AppError {
  if (isAppError(e)) return e;
  if (!(e instanceof HttpErrorResponse)) return build(0, 'UNEXPECTED', UNEXPECTED);
  if (e.status === 0) return build(0, 'NETWORK_ERROR', NETWORK);

  const body = e.error as ProblemDto | null;
  if (!isProblem(body)) return build(e.status, 'UNEXPECTED', UNEXPECTED);

  const fieldErrors = Object.fromEntries((body.errors ?? []).map((f) => [f.field, f.message]));
  if (e.status >= 500) {
    const detail = body.errorId
      ? `Algo salió mal de nuestro lado. Intenta de nuevo; si sigue pasando, comparte este código: ${body.errorId}.`
      : UNEXPECTED;
    return { ...build(e.status, body.code ?? 'INTERNAL_ERROR', detail), errorId: body.errorId };
  }
  return { ...build(e.status, body.code ?? 'UNEXPECTED', body.detail ?? UNEXPECTED), fieldErrors };
}

function isProblem(body: unknown): body is ProblemDto {
  return typeof body === 'object' && body !== null && typeof (body as ProblemDto).status === 'number';
}

function build(status: number, code: string, detail: string): AppError {
  return { kind: 'app-error', status, code, detail, fieldErrors: {} };
}
