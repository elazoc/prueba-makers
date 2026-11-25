import { Injectable, Injector, inject } from '@angular/core';
import { isAppError } from '../../core/api/app-error';
import { UserDto } from '../../core/api/dto';
import { AuthStore } from '../../core/auth/auth.store';
import { ToastService } from '../../core/notify/toast.service';
import { ConfirmService } from '../../shared/ui/confirm.service';
import { DrawerService } from '../../shared/ui/drawer.service';
import { UserFormData, UserFormDrawer } from './user-form.drawer';
import { UsersStore } from './users.store';

/** Crear, editar y desactivar usuarios. Se provee en la ruta, junto al store. */
@Injectable()
export class UsersActions {
  private readonly drawer = inject(DrawerService);
  private readonly confirm = inject(ConfirmService);
  private readonly toasts = inject(ToastService);
  private readonly store = inject(UsersStore);
  private readonly auth = inject(AuthStore);
  private readonly injector = inject(Injector);

  openForm(user?: UserDto): void {
    this.drawer.open<UserFormDrawer, UserFormData, UserDto>(
      UserFormDrawer,
      { user, selfId: this.auth.user()?.id ?? '' },
      this.injector,
    );
  }

  async deactivate(user: UserDto): Promise<void> {
    const ok = await this.confirm.confirm({
      title: 'Desactivar cuenta',
      message: `${user.fullName} no podrá ingresar. Sus préstamos se conservan.`,
      confirmLabel: 'Desactivar',
      tone: 'danger',
    });
    if (!ok) return;
    try {
      await this.store.deactivate(user.id);
      this.toasts.show({ tone: 'success', message: `Se desactivó la cuenta de ${user.fullName}.` });
    } catch (e) {
      this.toasts.show({
        tone: 'danger',
        message: isAppError(e) ? e.detail : 'No se pudo desactivar. Intenta de nuevo.',
      });
    }
  }
}
