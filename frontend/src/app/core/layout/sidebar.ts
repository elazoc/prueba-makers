import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { PrIcon } from '../../shared/ui/icon';
import { PrLogo } from '../../shared/ui/logo';
import { AuthStore } from '../auth/auth.store';
import { NAV_ITEMS } from './nav-items';

/** Barra lateral sobre el cielo. Abierta desde 1024 px, solo íconos entre 768 y 1023, oculta en el celular. */
@Component({
  selector: 'pr-sidebar',
  imports: [RouterLink, RouterLinkActive, PrIcon, PrLogo],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'pr-sidebar' },
  template: `
    <a class="pr-sidebar__brand" [routerLink]="auth.homeUrl()" aria-label="Prestemos, ir al inicio">
      <pr-logo class="pr-sidebar__logo-full" variant="full" tone="on-sky" />
      <pr-logo class="pr-sidebar__logo-mark" variant="mark" tone="on-sky" />
    </a>
    <nav class="pr-sidebar__nav" aria-label="Secciones">
      @for (item of items(); track item.link) {
        <a class="pr-sidebar__item" [routerLink]="item.link" routerLinkActive="is-active"
          ariaCurrentWhenActive="page">
          <pr-icon [name]="item.icon" />
          <span class="pr-sidebar__label">{{ item.label }}</span>
        </a>
      }
    </nav>
  `,
})
export class PrSidebar {
  protected readonly auth = inject(AuthStore);
  protected readonly items = computed(() => {
    const role = this.auth.role();
    return role ? NAV_ITEMS[role] : [];
  });
}
