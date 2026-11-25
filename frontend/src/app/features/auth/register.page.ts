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
import { Router, RouterLink } from '@angular/router';
import { isAppError } from '../../core/api/app-error';
import { AuthStore } from '../../core/auth/auth.store';
import { applyServerErrors, focusFirstInvalid } from '../../shared/forms/server-errors';
import { matchValidator } from '../../shared/forms/validators';
import { PrButton } from '../../shared/ui/button';
import { PrField } from '../../shared/ui/field';
import { PrIcon } from '../../shared/ui/icon';
import { PrInput } from '../../shared/ui/input.directive';
import { AuthLayout } from './auth-layout';

@Component({
  selector: 'app-register-page',
  imports: [ReactiveFormsModule, RouterLink, AuthLayout, PrField, PrInput, PrButton, PrIcon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-auth-layout>
      <h1 class="pr-auth__title">Crea tu cuenta</h1>
      <p class="pr-auth__lead">Con tu cuenta solicitas préstamos y sigues su estado.</p>

      <form class="pr-form" [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <pr-field label="Nombre completo" [control]="form.controls.fullName">
          <input prInput type="text" autocomplete="name" placeholder="Laura Pérez" formControlName="fullName" />
        </pr-field>
        <pr-field label="Correo" [control]="form.controls.email">
          <input prInput type="email" autocomplete="email" placeholder="nombre@correo.com" formControlName="email" />
        </pr-field>
        <pr-field label="Contraseña" hint="Usa al menos 8 caracteres." [control]="form.controls.password">
          <div class="pr-input-group">
            <input prInput [type]="showPassword() ? 'text' : 'password'" autocomplete="new-password"
              formControlName="password" />
            <button type="button" class="pr-input-group__action"
              [attr.aria-label]="showPassword() ? 'Ocultar contraseña' : 'Mostrar contraseña'"
              [attr.aria-pressed]="showPassword()" (click)="showPassword.set(!showPassword())">
              <pr-icon [name]="showPassword() ? 'eye-off' : 'eye'" />
            </button>
          </div>
        </pr-field>
        <pr-field label="Confirma la contraseña" [control]="form.controls.confirm">
          <input prInput [type]="showPassword() ? 'text' : 'password'" autocomplete="new-password"
            formControlName="confirm" />
        </pr-field>

        @if (formError()) {
          <p class="pr-form__error" role="alert"><pr-icon name="circle-alert" size="sm" />{{ formError() }}</p>
        }

        <button prButton variant="primary" size="lg" type="submit" class="pr-form__submit"
          [loading]="submitting()" loadingText="Creando tu cuenta…">Crear cuenta</button>
      </form>

      <p class="pr-auth__switch">¿Ya tienes cuenta? <a routerLink="/ingresar">Ingresa</a></p>
    </app-auth-layout>
  `,
})
export class RegisterPage {
  private readonly auth = inject(AuthStore);
  private readonly router = inject(Router);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  private readonly injector = inject(Injector);

  protected readonly form = inject(FormBuilder).nonNullable.group(
    {
      fullName: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(100)]],
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72)]],
      confirm: ['', [Validators.required]],
    },
    { validators: matchValidator('password', 'confirm') },
  );
  protected readonly submitting = signal(false);
  protected readonly formError = signal<string | null>(null);
  protected readonly showPassword = signal(false);

  protected async submit(): Promise<void> {
    if (this.submitting()) return;
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      afterNextRender(() => focusFirstInvalid(this.host), { injector: this.injector });
      return;
    }
    this.submitting.set(true);
    this.formError.set(null);
    try {
      const { fullName, email, password } = this.form.getRawValue();
      await this.auth.register({ fullName: fullName.trim(), email: email.trim(), password });
      await this.router.navigateByUrl(this.auth.homeUrl());
    } catch (e) {
      if (!isAppError(e)) {
        this.formError.set('Algo salió mal. Intenta de nuevo en un momento.');
      } else {
        this.formError.set(
          applyServerErrors(this.form, e, {
            EMAIL_ALREADY_REGISTERED: {
              field: 'email',
              message: 'Ya hay una cuenta con este correo. Ingresa o usa otro.',
            },
          }),
        );
        afterNextRender(() => focusFirstInvalid(this.host), { injector: this.injector });
      }
    } finally {
      this.submitting.set(false);
    }
  }
}
