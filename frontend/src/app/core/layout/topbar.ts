import { CdkMenu, CdkMenuItem, CdkMenuItemRadio, CdkMenuTrigger } from '@angular/cdk/menu';
import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { Router } from '@angular/router';
import { environment } from '../../../environments/environment';
import { PrBadge } from '../../shared/ui/badge';
import { PrIcon } from '../../shared/ui/icon';
import { PrLogo } from '../../shared/ui/logo';
import { AuthStore } from '../auth/auth.store';
import { ThemeMode, ThemeService } from './theme.service';

const ROLE_LABEL = { USER: 'Usuario', ADMIN: 'Administrador' } as const;

/** Borra los datos del simulador (base y «cookie») y recarga. Solo existe en modo simulador. */
function resetMockData(): void {
  Object.keys(localStorage)
    .filter((k) => k.startsWith('prestemos-mock-'))
    .forEach((k) => localStorage.removeItem(k));
  location.assign('/');
}

@Component({
  selector: 'pr-topbar',
  imports: [CdkMenuTrigger, CdkMenu, CdkMenuItem, CdkMenuItemRadio, PrIcon, PrLogo, PrBadge],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'pr-topbar' },
  template: `
    <pr-logo class="pr-topbar__logo" variant="mark" [size]="28" />
    <h1 class="pr-topbar__title">{{ title() }}</h1>
    <div class="pr-topbar__tools">
      <ng-content />
      @if (useMocks) {
        <pr-badge tone="info" icon="info" class="pr-topbar__mock">Datos de prueba</pr-badge>
      }
      <button type="button" class="pr-user" [cdkMenuTriggerFor]="menu" aria-label="Abrir el menú de tu cuenta">
        <span class="pr-user__avatar" aria-hidden="true">{{ initials() }}</span>
        <span class="pr-user__text">
          <span class="pr-user__name">{{ auth.displayName() }}</span>
          <span class="pr-user__role">{{ roleLabel() }}</span>
        </span>
      </button>
    </div>

    <ng-template #menu>
      <div class="pr-menu" cdkMenu>
        <div class="pr-menu__header">
          <span class="pr-menu__name">{{ auth.displayName() }}</span>
          <span class="pr-menu__detail">{{ auth.user()?.email }} · {{ roleLabel() }}</span>
        </div>
        <div class="pr-menu__group" role="group" aria-label="Tema">
          <span class="pr-menu__caption" aria-hidden="true">Tema</span>
          @for (option of themes; track option.mode) {
            <button type="button" class="pr-menu__item" cdkMenuItemRadio
              [cdkMenuItemChecked]="theme.mode() === option.mode" (cdkMenuItemTriggered)="theme.setMode(option.mode)">
              <pr-icon [name]="option.icon" size="sm" />{{ option.label }}
              @if (theme.mode() === option.mode) {
                <pr-icon class="pr-menu__check" name="check" size="sm" />
              }
            </button>
          }
        </div>
        @if (useMocks) {
          <button type="button" class="pr-menu__item" cdkMenuItem (cdkMenuItemTriggered)="resetMock()">
            <pr-icon name="refresh-cw" size="sm" />Reiniciar datos de prueba
          </button>
        }
        <button type="button" class="pr-menu__item" cdkMenuItem (cdkMenuItemTriggered)="logout()">
          <pr-icon name="log-out" size="sm" />Cerrar sesión
        </button>
      </div>
    </ng-template>
  `,
})
export class PrTopbar {
  readonly title = input('');

  protected readonly auth = inject(AuthStore);
  protected readonly theme = inject(ThemeService);
  private readonly router = inject(Router);
  protected readonly useMocks = environment.useMocks;

  protected readonly themes: { mode: ThemeMode; label: string; icon: 'monitor' | 'sun' | 'moon' }[] = [
    { mode: 'system', label: 'Como el sistema', icon: 'monitor' },
    { mode: 'light', label: 'Claro', icon: 'sun' },
    { mode: 'dark', label: 'Oscuro', icon: 'moon' },
  ];

  protected roleLabel(): string {
    const role = this.auth.role();
    return role ? ROLE_LABEL[role] : '';
  }

  protected initials(): string {
    return this.auth
      .displayName()
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((w) => w[0]?.toUpperCase())
      .join('');
  }

  protected async logout(): Promise<void> {
    await this.auth.logout();
    await this.router.navigate(['/ingresar']);
  }

  protected resetMock(): void {
    resetMockData();
  }
}
