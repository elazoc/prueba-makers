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
import { ActivatedRoute, Router } from '@angular/router';
import { Role } from '../../core/api/dto';
import { AuthStore } from '../../core/auth/auth.store';
import { PrBadge } from '../../shared/ui/badge';
import { PrButton } from '../../shared/ui/button';
import { PrEmptyState } from '../../shared/ui/empty-state';
import { FilterOption, PrFilterTabs } from '../../shared/ui/filter-tabs';
import { PrIcon } from '../../shared/ui/icon';
import { PrPagination } from '../../shared/ui/pagination';
import { PrSkeleton } from '../../shared/ui/skeleton';
import { parsePage } from '../my-loans/my-loans.page';
import { UsersActions } from './users.actions';
import { UsersStore } from './users.store';

type RoleFilter = Role | 'ALL';
type ActiveFilter = 'ALL' | 'ACTIVE' | 'INACTIVE';

const ROLE_FILTERS: FilterOption<RoleFilter>[] = [
  { value: 'ALL', label: 'Todos los roles' },
  { value: 'USER', label: 'Usuarios' },
  { value: 'ADMIN', label: 'Administradores' },
];
const ACTIVE_FILTERS: FilterOption<ActiveFilter>[] = [
  { value: 'ALL', label: 'Todos' },
  { value: 'ACTIVE', label: 'Activos' },
  { value: 'INACTIVE', label: 'Inactivos' },
];

const parseRole = (v: string | undefined): Role | null => (v === 'USER' || v === 'ADMIN' ? v : null);
const parseActive = (v: string | undefined): boolean | null => (v === 'true' ? true : v === 'false' ? false : null);

@Component({
  selector: 'app-users-page',
  imports: [DatePipe, PrBadge, PrButton, PrIcon, PrFilterTabs, PrPagination, PrEmptyState, PrSkeleton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="pr-toolbar">
      <div class="pr-toolbar__filters">
        <pr-filter-tabs [options]="roleFilters" [value]="roleFilter()" (valueChange)="setRole($event)"
          label="Filtrar por rol" />
        <pr-filter-tabs [options]="activeFilters" [value]="activeFilter()" (valueChange)="setActive($event)"
          label="Filtrar por estado de la cuenta" />
      </div>
      <div class="pr-toolbar__end">
        <button prButton variant="primary" type="button" (click)="actions.openForm()">
          <pr-icon name="plus" />Nuevo usuario
        </button>
      </div>
    </div>
    <p class="pr-toolbar__hint">Desactivar una cuenta no borra a la persona ni sus préstamos: solo le impide ingresar.</p>

    @if (store.error() && !store.page()) {
      <pr-empty-state icon="circle-alert" title="No se pudieron cargar los usuarios." [description]="store.error()!.detail">
        <button prButton type="button" (click)="store.reload()">Reintentar</button>
      </pr-empty-state>
    } @else if (!store.page()) {
      <pr-skeleton [rows]="5" />
    } @else if (store.page()!.items.length === 0) {
      <pr-empty-state icon="users" title="No hay usuarios con estos filtros." description="Cambia los filtros para ver los demás." />
    } @else {
      <div class="pr-table" [attr.aria-busy]="store.loading()">
        <table>
          <caption class="sr-only">Usuarios</caption>
          <thead>
            <tr>
              <th scope="col">Nombre</th>
              <th scope="col">Rol</th>
              <th scope="col">Estado</th>
              <th scope="col">Creado</th>
              <th scope="col"><span class="sr-only">Acciones</span></th>
            </tr>
          </thead>
          <tbody>
            @for (user of store.page()!.items; track user.id) {
              <tr>
                <td data-label="Nombre">
                  <div class="pr-table__person">
                    <span class="pr-table__primary">
                      {{ user.fullName }}
                      @if (user.id === selfId()) { <span class="pr-muted">(tú)</span> }
                    </span>
                    <span class="pr-table__secondary">{{ user.email }}</span>
                  </div>
                </td>
                <td data-label="Rol">
                  <pr-badge [tone]="user.role === 'ADMIN' ? 'info' : 'neutral'">
                    {{ user.role === 'ADMIN' ? 'Administrador' : 'Usuario' }}
                  </pr-badge>
                </td>
                <td data-label="Estado">
                  <pr-badge [tone]="user.active ? 'success' : 'neutral'" [icon]="user.active ? 'check' : 'ban'">
                    {{ user.active ? 'Activo' : 'Inactivo' }}
                  </pr-badge>
                </td>
                <td data-label="Creado">{{ user.createdAt | date: 'd MMM y' }}</td>
                <td class="actions">
                  <button prButton size="sm" type="button" (click)="actions.openForm(user)"
                    [attr.aria-label]="'Editar a ' + user.fullName">Editar</button>
                  @if (user.active) {
                    <button prButton variant="danger" size="sm" type="button"
                      [disabled]="user.id === selfId()"
                      [attr.title]="user.id === selfId() ? 'No puedes desactivar tu propia cuenta.' : null"
                      (click)="actions.deactivate(user)"
                      [attr.aria-label]="'Desactivar la cuenta de ' + user.fullName">Desactivar</button>
                  }
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
      <pr-pagination [page]="store.page()!" (pageChange)="goToPage($event)" />
    }
  `,
})
export class UsersPage {
  /** De la URL: `?rol=ADMIN&activo=true&pagina=1`. */
  readonly rol = input<string>();
  readonly activo = input<string>();
  readonly pagina = input<string>();

  protected readonly store = inject(UsersStore);
  protected readonly actions = inject(UsersActions);
  private readonly auth = inject(AuthStore);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly roleFilters = ROLE_FILTERS;
  protected readonly activeFilters = ACTIVE_FILTERS;
  protected readonly selfId = computed(() => this.auth.user()?.id ?? null);
  protected readonly roleFilter = computed<RoleFilter>(() => this.store.role() ?? 'ALL');
  protected readonly activeFilter = computed<ActiveFilter>(() => {
    const active = this.store.active();
    return active === null ? 'ALL' : active ? 'ACTIVE' : 'INACTIVE';
  });

  constructor() {
    effect(() => {
      const query = {
        role: parseRole(this.rol()),
        active: parseActive(this.activo()),
        page: parsePage(this.pagina()),
      };
      untracked(() => this.store.setQuery(query));
    });
  }

  protected setRole(value: RoleFilter): void {
    this.navigate({ rol: value === 'ALL' ? null : value, pagina: null });
  }

  protected setActive(value: ActiveFilter): void {
    this.navigate({ activo: value === 'ALL' ? null : String(value === 'ACTIVE'), pagina: null });
  }

  protected goToPage(page: number): void {
    this.navigate({ pagina: page || null });
  }

  private navigate(queryParams: Record<string, string | number | null>): void {
    void this.router.navigate([], { relativeTo: this.route, queryParams, queryParamsHandling: 'merge' });
  }
}
