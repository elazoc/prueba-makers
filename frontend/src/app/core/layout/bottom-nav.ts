import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { PrIcon } from '../../shared/ui/icon';
import { AuthStore } from '../auth/auth.store';
import { NAV_ITEMS } from './nav-items';

/** Navegación inferior del celular (menos de 768 px). */
@Component({
  selector: 'pr-bottom-nav',
  imports: [RouterLink, RouterLinkActive, PrIcon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'pr-bottom-nav' },
  template: `
    <nav aria-label="Secciones">
      @for (item of items(); track item.link) {
        <a class="pr-bottom-nav__item" [routerLink]="item.link" routerLinkActive="is-active"
          ariaCurrentWhenActive="page">
          <pr-icon [name]="item.icon" />
          <span>{{ item.label }}</span>
        </a>
      }
    </nav>
  `,
})
export class PrBottomNav {
  private readonly auth = inject(AuthStore);
  protected readonly items = computed(() => {
    const role = this.auth.role();
    return role ? NAV_ITEMS[role] : [];
  });
}
