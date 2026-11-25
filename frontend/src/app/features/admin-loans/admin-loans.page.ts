import { DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
  untracked,
} from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink, RouterOutlet } from '@angular/router';
import { LoanStatus } from '../../core/api/dto';
import { errorMessage } from '../../shared/forms/error-messages';
import { formatMoney } from '../../shared/money/money';
import { MoneyPipe } from '../../shared/money/money.pipe';
import { PrButton } from '../../shared/ui/button';
import { PrEmptyState } from '../../shared/ui/empty-state';
import { FilterOption, PrFilterTabs } from '../../shared/ui/filter-tabs';
import { PrIcon } from '../../shared/ui/icon';
import { PrPagination } from '../../shared/ui/pagination';
import { PrSkeleton } from '../../shared/ui/skeleton';
import { PrStatusBadge } from '../../shared/ui/status-badge';
import { parsePage, parseStatus } from '../my-loans/my-loans.page';
import { termLabel } from '../my-loans/term-label';
import { AdminLoansActions } from './admin-loans.actions';
import { AdminLoansStore } from './admin-loans.store';

type Filter = LoanStatus | 'ALL';
const ALL = 'todos';

const FILTERS: FilterOption<Filter>[] = [
  { value: 'PENDING', label: 'Pendientes' },
  { value: 'APPROVED', label: 'Aprobados' },
  { value: 'REJECTED', label: 'Rechazados' },
  { value: 'CANCELLED', label: 'Cancelados' },
  { value: 'ALL', label: 'Todos' },
];

const EMPTY: Record<Filter, { title: string; description: string }> = {
  PENDING: { title: 'No hay solicitudes por decidir.', description: 'Las nuevas aparecen aquí apenas lleguen.' },
  APPROVED: { title: 'No hay préstamos aprobados.', description: 'Los que apruebes aparecen aquí.' },
  REJECTED: { title: 'No hay préstamos rechazados.', description: 'Los que rechaces aparecen aquí.' },
  CANCELLED: { title: 'No hay solicitudes canceladas.', description: 'Aquí aparecen las que cancelen los solicitantes.' },
  ALL: { title: 'Todavía no hay solicitudes.', description: 'Las nuevas aparecen aquí apenas lleguen.' },
};

@Component({
  selector: 'app-admin-loans-page',
  imports: [
    DatePipe,
    ReactiveFormsModule,
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
      <form class="pr-search" role="search" (submit)="$event.preventDefault(); search()" novalidate>
        <label class="sr-only" for="buscar-correo">Buscar por correo</label>
        <div class="pr-search__box" [class.pr-search__box--error]="searchError()">
          <input id="buscar-correo" class="pr-input" type="email" placeholder="Buscar por correo"
            autocomplete="off" [formControl]="emailSearch"
            [attr.aria-invalid]="searchError() ? 'true' : null"
            [attr.aria-describedby]="searchError() ? 'buscar-correo-error' : null" />
          <button prButton variant="ghost" size="sm" class="pr-btn--icon" type="submit" aria-label="Buscar">
            <pr-icon name="search" size="sm" />
          </button>
        </div>
        @if (store.email()) {
          <button prButton variant="ghost" size="sm" type="button" (click)="clearSearch()">Quitar filtro</button>
        }
        @if (searchError()) {
          <p id="buscar-correo-error" class="pr-search__error" role="alert">
            <pr-icon name="circle-alert" size="sm" />{{ searchError() }}
          </p>
        }
      </form>
    </div>

    @if (store.email()) {
      <p class="pr-toolbar__hint">Solicitudes de <strong>{{ store.email() }}</strong>.</p>
    } @else {
      <p class="pr-toolbar__hint">Ordenadas de la más antigua a la más reciente: la que más espera va primero.</p>
    }

    @if (store.newArrivals() > 0) {
      <div class="pr-arrivals" role="status">
        <pr-icon name="inbox" size="sm" />
        <span>{{ store.newArrivals() === 1 ? 'Llegó 1 solicitud nueva' : 'Llegaron ' + store.newArrivals() + ' solicitudes nuevas' }}</span>
        <button prButton size="sm" type="button" (click)="showArrivals()">Ver</button>
      </div>
    }

    @if (store.error() && !store.page()) {
      <pr-empty-state icon="circle-alert" title="No se pudieron cargar las solicitudes."
        [description]="store.error()!.detail">
        <button prButton type="button" (click)="store.reload()">Reintentar</button>
      </pr-empty-state>
    } @else if (!store.page()) {
      <pr-skeleton [rows]="5" />
    } @else if (store.page()!.items.length === 0) {
      <pr-empty-state icon="inbox" [title]="emptyCopy().title"
        [description]="store.email() ? 'Prueba con otro correo o quita el filtro.' : emptyCopy().description" />
    } @else {
      <div class="pr-table" [attr.aria-busy]="store.loading()">
        <table>
          <caption class="sr-only">Solicitudes de préstamo</caption>
          <thead>
            <tr>
              <th scope="col">Solicitante</th>
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
                <td data-label="Solicitante">
                  <div class="pr-table__person">
                    <a class="pr-table__link pr-table__primary" [routerLink]="[loan.id]" queryParamsHandling="preserve">
                      {{ loan.applicantName }}
                    </a>
                    <span class="pr-table__secondary">{{ loan.applicantEmail }}</span>
                  </div>
                </td>
                <td class="num" data-label="Monto">{{ loan.amount | money }}</td>
                <td data-label="Plazo">{{ term(loan.termMonths) }}</td>
                <td data-label="Solicitado">{{ loan.requestedAt | date: 'd MMM y' }}</td>
                <td data-label="Estado"><pr-status-badge [status]="loan.status" /></td>
                <td class="actions">
                  @if (loan.status === 'PENDING') {
                    <button prButton variant="danger" size="sm" type="button"
                      [disabled]="store.deciding().includes(loan.id)" (click)="actions.reject(loan)"
                      [attr.aria-label]="'Rechazar el préstamo de ' + money(loan.amount) + ' de ' + loan.applicantName">
                      Rechazar
                    </button>
                    <button prButton size="sm" type="button" [loading]="store.deciding().includes(loan.id)"
                      loadingText="Decidiendo…" (click)="actions.approve(loan)"
                      [attr.aria-label]="'Aprobar el préstamo de ' + money(loan.amount) + ' de ' + loan.applicantName">
                      Aprobar
                    </button>
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
export class AdminLoansPage {
  /** De la URL: `?estado=APPROVED|todos&correo=…&pagina=2`. Sin estado: pendientes. */
  readonly estado = input<string>();
  readonly correo = input<string>();
  readonly pagina = input<string>();

  protected readonly store = inject(AdminLoansStore);
  protected readonly actions = inject(AdminLoansActions);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly filters = FILTERS;
  protected readonly term = termLabel;
  protected readonly money = formatMoney;
  protected readonly emailSearch = new FormControl('', { nonNullable: true, validators: [Validators.email] });
  protected readonly searchError = signal<string | null>(null);

  protected readonly filter = computed<Filter>(() => this.store.status() ?? 'ALL');
  protected readonly emptyCopy = computed(() => EMPTY[this.filter()]);

  constructor() {
    effect(() => {
      const estado = this.estado();
      const correo = this.correo()?.trim() || null;
      const query = {
        status: estado === ALL ? null : (parseStatus(estado) ?? 'PENDING'),
        email: correo,
        page: parsePage(this.pagina()),
      };
      untracked(() => {
        this.emailSearch.setValue(correo ?? '', { emitEvent: false });
        this.store.setQuery(query);
      });
    });
    this.emailSearch.valueChanges.subscribe(() => this.searchError.set(null));
  }

  protected setFilter(value: Filter): void {
    this.navigate({ estado: value === 'PENDING' ? null : value === 'ALL' ? ALL : value, pagina: null });
  }

  protected search(): void {
    const value = this.emailSearch.value.trim();
    if (!value) return this.clearSearch();
    if (this.emailSearch.invalid) {
      this.searchError.set(errorMessage({ email: true }));
      return;
    }
    this.navigate({ correo: value.toLowerCase(), pagina: null });
  }

  protected clearSearch(): void {
    this.emailSearch.setValue('');
    this.navigate({ correo: null, pagina: null });
  }

  protected goToPage(page: number): void {
    this.navigate({ pagina: page || null });
  }

  protected showArrivals(): void {
    const last = this.store.lastPageIndex();
    this.store.clearArrivals();
    this.navigate({ pagina: last || null });
  }

  private navigate(queryParams: Record<string, string | number | null>): void {
    void this.router.navigate([], { relativeTo: this.route, queryParams, queryParamsHandling: 'merge' });
  }
}
