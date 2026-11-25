import { DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  untracked,
} from '@angular/core';
import { ActivatedRoute, Router, RouterLink, RouterOutlet } from '@angular/router';
import { LoanStatus } from '../../core/api/dto';
import { LOAN_POLICY } from '../../shared/forms/loan-policy';
import { formatMoney } from '../../shared/money/money';
import { MoneyPipe } from '../../shared/money/money.pipe';
import { PrButton } from '../../shared/ui/button';
import { PrEmptyState } from '../../shared/ui/empty-state';
import { FilterOption, PrFilterTabs } from '../../shared/ui/filter-tabs';
import { PrIcon } from '../../shared/ui/icon';
import { PrPagination } from '../../shared/ui/pagination';
import { PrSkeleton } from '../../shared/ui/skeleton';
import { PrStatusBadge } from '../../shared/ui/status-badge';
import { MyLoansActions } from './my-loans.actions';
import { MyLoansStore } from './my-loans.store';
import { termLabel } from './term-label';

type Filter = LoanStatus | 'ALL';

const STATUSES: LoanStatus[] = ['PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'];
const FILTERS: FilterOption<Filter>[] = [
  { value: 'ALL', label: 'Todos' },
  { value: 'PENDING', label: 'Pendientes' },
  { value: 'APPROVED', label: 'Aprobados' },
  { value: 'REJECTED', label: 'Rechazados' },
  { value: 'CANCELLED', label: 'Cancelados' },
];
const EMPTY_BY_STATUS: Record<LoanStatus, string> = {
  PENDING: 'No tienes préstamos pendientes.',
  APPROVED: 'No tienes préstamos aprobados.',
  REJECTED: 'No tienes préstamos rechazados.',
  CANCELLED: 'No tienes préstamos cancelados.',
};

export const parseStatus = (value: string | undefined): LoanStatus | null =>
  STATUSES.includes(value as LoanStatus) ? (value as LoanStatus) : null;
export const parsePage = (value: string | undefined): number => Math.max(0, Number.parseInt(value ?? '0', 10) || 0);

@Component({
  selector: 'app-my-loans-page',
  imports: [
    DatePipe,
    RouterLink,
    RouterOutlet,
    MoneyPipe,
    PrButton,
    PrIcon,
    PrFilterTabs,
    PrPagination,
    PrEmptyState,
    PrSkeleton,
    PrStatusBadge,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="pr-toolbar">
      <pr-filter-tabs [options]="filters" [value]="filter()" (valueChange)="setFilter($event)"
        label="Filtrar por estado" />
      <div class="pr-toolbar__end">
        <button prButton variant="primary" type="button" [disabled]="!store.canRequest()"
          [attr.title]="store.canRequest() ? null : limitReached" (click)="actions.openForm()">
          <pr-icon name="plus" />Solicitar préstamo
        </button>
      </div>
    </div>
    <p class="pr-toolbar__hint" [class.pr-toolbar__hint--limit]="!store.canRequest()">
      Tienes {{ store.pendingCount() }} de {{ maxPending }} solicitudes pendientes.
      @if (!store.canRequest()) {
        Espera una decisión o cancela una para pedir otra.
      }
    </p>

    @if (store.error() && !store.page()) {
      <pr-empty-state icon="circle-alert" title="No se pudieron cargar tus préstamos."
        [description]="store.error()!.detail">
        <button prButton type="button" (click)="store.reload()">Reintentar</button>
      </pr-empty-state>
    } @else if (!store.page()) {
      <pr-skeleton [rows]="4" />
    } @else if (store.page()!.items.length === 0) {
      @if (store.status(); as status) {
        <pr-empty-state icon="inbox" [title]="emptyByStatus[status]"
          description="Cambia el filtro para ver tus otros préstamos." />
      } @else {
        <pr-empty-state icon="wallet" title="Todavía no tienes préstamos."
          description="Cuando solicites el primero, lo verás aquí.">
          @if (store.canRequest()) {
            <button prButton type="button" (click)="actions.openForm()">Solicitar préstamo</button>
          }
        </pr-empty-state>
      }
    } @else {
      <div class="pr-table" [attr.aria-busy]="store.loading()">
        <table>
          <caption class="sr-only">Tus préstamos</caption>
          <thead>
            <tr>
              <th scope="col" class="num">Monto</th>
              <th scope="col">Plazo</th>
              <th scope="col">Solicitado</th>
              <th scope="col">Estado</th>
              <th scope="col"><span class="sr-only">Acciones</span></th>
            </tr>
          </thead>
          <tbody>
            @for (loan of store.page()!.items; track loan.id) {
              <tr [class.is-live]="store.highlighted().includes(loan.id)">
                <td class="num" data-label="Monto">
                  <a class="pr-table__link" [routerLink]="[loan.id]" queryParamsHandling="preserve">
                    {{ loan.amount | money }}
                  </a>
                </td>
                <td data-label="Plazo">{{ term(loan.termMonths) }}</td>
                <td data-label="Solicitado">{{ loan.requestedAt | date: 'd MMM y' }}</td>
                <td data-label="Estado"><pr-status-badge [status]="loan.status" /></td>
                <td class="actions">
                  @if (loan.status === 'PENDING') {
                    <button prButton size="sm" type="button" (click)="actions.openForm(loan)"
                      [attr.aria-label]="'Editar la solicitud de ' + money(loan.amount)">Editar</button>
                    <button prButton size="sm" type="button" (click)="actions.cancel(loan)"
                      [attr.aria-label]="'Cancelar la solicitud de ' + money(loan.amount)">Cancelar</button>
                  }
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
      <pr-pagination [page]="store.page()!" (pageChange)="goToPage($event)" />
    }
    <router-outlet />
  `,
})
export class MyLoansPage {
  /** De la URL: `?estado=PENDING&pagina=2`. */
  readonly estado = input<string>();
  readonly pagina = input<string>();

  protected readonly store = inject(MyLoansStore);
  protected readonly actions = inject(MyLoansActions);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly filters = FILTERS;
  protected readonly emptyByStatus = EMPTY_BY_STATUS;
  protected readonly maxPending = LOAN_POLICY.maxPending;
  protected readonly limitReached = 'Ya tienes 3 solicitudes pendientes. Espera una decisión o cancela una.';
  protected readonly filter = computed<Filter>(() => this.store.status() ?? 'ALL');
  protected readonly term = termLabel;
  protected readonly money = formatMoney;

  constructor() {
    effect(() => {
      const query = { status: parseStatus(this.estado()), page: parsePage(this.pagina()) };
      untracked(() => this.store.setQuery(query));
    });
  }

  protected setFilter(value: Filter): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { estado: value === 'ALL' ? null : value, pagina: null },
      queryParamsHandling: 'merge',
    });
  }

  protected goToPage(page: number): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { pagina: page || null },
      queryParamsHandling: 'merge',
    });
  }
}
