import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Injector,
  afterNextRender,
  inject,
  signal,
} from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { isAppError } from '../../core/api/app-error';
import { Role, UserDto } from '../../core/api/dto';
import { ToastService } from '../../core/notify/toast.service';
import { applyServerErrors, focusFirstInvalid } from '../../shared/forms/server-errors';
import { PrButton } from '../../shared/ui/button';
import { PrDrawer } from '../../shared/ui/drawer.service';
import { PrField } from '../../shared/ui/field';
import { PrIcon } from '../../shared/ui/icon';
import { PrInput } from '../../shared/ui/input.directive';
import { UsersStore } from './users.store';

export interface UserFormData {
  user?: UserDto;
  /** Id de quien administra: sobre su propia cuenta no puede quitarse el rol ni desactivarse. */
  selfId: string;
}

@Component({
  selector: 'app-user-form-drawer',
  imports: [ReactiveFormsModule, PrDrawer, PrField, PrInput, PrButton, PrIcon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <pr-drawer [title]="isEdit ? 'Editar usuario' : 'Nuevo usuario'"
      [description]="isEdit ? data.user!.email : 'Crea una cuenta con cualquier rol.'">
      <form id="user-form" class="pr-form pr-form--flush" [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <pr-field label="Nombre completo" [control]="form.controls.fullName">
          <input prInput type="text" autocomplete="off" placeholder="Laura Pérez" formControlName="fullName" />
        </pr-field>

        @if (!isEdit) {
          <pr-field label="Correo" [control]="form.controls.email">
            <input prInput type="email" autocomplete="off" placeholder="nombre@correo.com" formControlName="email" />
          </pr-field>
          <pr-field label="Contraseña" hint="Usa al menos 8 caracteres. Compártela por un canal seguro."
            [control]="form.controls.password">
            <input prInput type="password" autocomplete="new-password" formControlName="password" />
          </pr-field>
        }

        <pr-field label="Rol" [hint]="isSelf ? selfHint : undefined" [control]="form.controls.role">
          <select prInput formControlName="role">
            <option value="USER">Usuario</option>
            <option value="ADMIN">Administrador</option>
          </select>
        </pr-field>

        @if (isEdit) {
          <label class="pr-check" [class.pr-check--disabled]="isSelf">
            <input type="checkbox" formControlName="active" />
            <span>
              <span class="pr-check__label">Cuenta activa</span>
              <span class="pr-check__hint">Una cuenta inactiva no puede ingresar. Sus préstamos se conservan.</span>
            </span>
          </label>
        }

        @if (formError()) {
          <p class="pr-form__error" role="alert"><pr-icon name="circle-alert" size="sm" />{{ formError() }}</p>
        }
      </form>

      <div drawer-footer>
        <button prButton type="button" (click)="ref.close()">Volver</button>
        <button prButton variant="primary" type="submit" form="user-form" [loading]="saving()"
          [loadingText]="isEdit ? 'Guardando…' : 'Creando…'">
          {{ isEdit ? 'Guardar cambios' : 'Crear usuario' }}
        </button>
      </div>
    </pr-drawer>
  `,
})
export class UserFormDrawer {
  protected readonly data = inject<UserFormData>(DIALOG_DATA);
  protected readonly ref = inject<DialogRef<UserDto>>(DialogRef);
  private readonly store = inject(UsersStore);
  private readonly toasts = inject(ToastService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  private readonly injector = inject(Injector);

  protected readonly isEdit = !!this.data.user;
  protected readonly isSelf = this.data.user?.id === this.data.selfId;
  protected readonly selfHint = 'No puedes quitarte el rol ni desactivar tu cuenta.';
  protected readonly saving = signal(false);
  protected readonly formError = signal<string | null>(null);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    fullName: [
      this.data.user?.fullName ?? '',
      [Validators.required, Validators.minLength(2), Validators.maxLength(100)],
    ],
    email: ['', this.isEdit ? [] : [Validators.required, Validators.email]],
    password: [
      '',
      this.isEdit ? [] : [Validators.required, Validators.minLength(8), Validators.maxLength(72)],
    ],
    role: [{ value: (this.data.user?.role ?? 'USER') as Role, disabled: this.isSelf }],
    active: [{ value: this.data.user?.active ?? true, disabled: this.isSelf }],
  });

  protected async submit(): Promise<void> {
    if (this.saving()) return;
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      afterNextRender(() => focusFirstInvalid(this.host), { injector: this.injector });
      return;
    }
    const v = this.form.getRawValue();
    this.saving.set(true);
    this.formError.set(null);
    try {
      const user = this.data.user
        ? await this.store.update(this.data.user.id, {
            fullName: v.fullName.trim(),
            role: v.role,
            active: v.active,
          })
        : await this.store.create({
            fullName: v.fullName.trim(),
            email: v.email.trim().toLowerCase(),
            password: v.password,
            role: v.role,
          });
      this.toasts.show({
        tone: 'success',
        message: this.isEdit ? 'Se guardaron los cambios.' : `Se creó la cuenta de ${user.fullName}.`,
      });
      this.ref.close(user);
    } catch (e) {
      if (!isAppError(e)) {
        this.formError.set('Algo salió mal. Intenta de nuevo en un momento.');
        return;
      }
      this.formError.set(
        applyServerErrors(this.form, e, {
          EMAIL_ALREADY_REGISTERED: { field: 'email', message: 'Ya hay una cuenta con este correo.' },
        }),
      );
      afterNextRender(() => focusFirstInvalid(this.host), { injector: this.injector });
    } finally {
      this.saving.set(false);
    }
  }
}
