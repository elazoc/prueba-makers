import { PageDto } from '../../core/api/dto';
import { MockProblem } from './problem';

export interface MockPageQuery {
  page?: number;
  size?: number;
  sort?: string;
}

export function paginate<T>(items: T[], q: MockPageQuery): PageDto<T> {
  const page = q.page ?? 0;
  const size = q.size ?? 20;
  if (!Number.isInteger(page) || page < 0) {
    throw new MockProblem(400, 'VALIDATION_ERROR', 'La página no puede ser negativa.');
  }
  if (!Number.isInteger(size) || size < 1 || size > 100) {
    throw new MockProblem(400, 'VALIDATION_ERROR', 'El tamaño de página va de 1 a 100.');
  }
  return {
    content: items.slice(page * size, page * size + size),
    page,
    size,
    totalElements: items.length,
    totalPages: Math.ceil(items.length / size),
  };
}

const DECIMAL = /^\d+\.\d{2}$/;

/** Ordena por `campo,asc|desc`, solo con campos de la lista blanca. */
export function sortBy<T>(items: T[], sort: string | undefined, allowed: string[], fallback: string): T[] {
  const [field, direction = 'asc'] = (sort || fallback).split(',');
  if (!allowed.includes(field) || !['asc', 'desc'].includes(direction)) {
    throw new MockProblem(400, 'VALIDATION_ERROR', `No se puede ordenar por «${sort}».`);
  }
  const sign = direction === 'desc' ? -1 : 1;
  return [...items].sort((a, b) => sign * compare(read(a, field), read(b, field)));
}

function read(item: unknown, field: string): unknown {
  return (item as Record<string, unknown>)[field];
}

function compare(a: unknown, b: unknown): number {
  if (typeof a === 'string' && typeof b === 'string' && DECIMAL.test(a) && DECIMAL.test(b)) {
    const x = BigInt(a.replace('.', ''));
    const y = BigInt(b.replace('.', ''));
    return x === y ? 0 : x < y ? -1 : 1;
  }
  return String(a).localeCompare(String(b));
}
