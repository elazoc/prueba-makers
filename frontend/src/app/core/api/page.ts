import { PageDto } from './dto';

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  total: number;
  totalPages: number;
}

export interface PageQuery {
  page: number;
  size: number;
  sort?: string;
}

/** Único lugar que conoce la forma de la página del backend. */
export function toPage<T>(dto: PageDto<T>): Page<T> {
  return {
    items: dto.content,
    page: dto.page,
    size: dto.size,
    total: dto.totalElements,
    totalPages: dto.totalPages,
  };
}
