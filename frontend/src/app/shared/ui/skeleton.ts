import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/** Forma de lo que viene, en gris, para la primera carga de una pantalla. */
@Component({
  selector: 'pr-skeleton',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'pr-skeleton', 'aria-busy': 'true' },
  template: `
    <span class="sr-only" role="status">Cargando…</span>
    @for (row of list(); track row) {
      <div class="pr-skeleton__row" aria-hidden="true">
        <span class="pr-skeleton__bar pr-skeleton__bar--wide"></span>
        <span class="pr-skeleton__bar"></span>
        <span class="pr-skeleton__bar pr-skeleton__bar--short"></span>
      </div>
    }
  `,
})
export class PrSkeleton {
  readonly rows = input(5);
  protected readonly list = computed(() => Array.from({ length: this.rows() }, (_, i) => i));
}
