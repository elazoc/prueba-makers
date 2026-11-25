import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { Page } from '../../core/api/page';
import { PrButton } from './button';
import { PrIcon } from './icon';

/** «1–20 de 57» y Anterior / Siguiente. No se muestra si no hay filas. */
@Component({
  selector: 'pr-pagination',
  imports: [PrButton, PrIcon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (page().total > 0) {
      <nav class="pr-pagination" aria-label="Paginación">
        <span class="pr-pagination__range tabular">{{ range() }}</span>
        <button prButton size="sm" type="button" [disabled]="page().page === 0"
          (click)="pageChange.emit(page().page - 1)">
          <pr-icon name="chevron-left" size="sm" />Anterior
        </button>
        <button prButton size="sm" type="button" [disabled]="page().page >= page().totalPages - 1"
          (click)="pageChange.emit(page().page + 1)">
          Siguiente<pr-icon name="chevron-right" size="sm" />
        </button>
      </nav>
    }
  `,
})
export class PrPagination {
  readonly page = input.required<Page<unknown>>();
  readonly pageChange = output<number>();

  protected readonly range = computed(() => {
    const { page, size, total } = this.page();
    const from = page * size + 1;
    const to = Math.min((page + 1) * size, total);
    return `${from}–${to} de ${total}`;
  });
}
