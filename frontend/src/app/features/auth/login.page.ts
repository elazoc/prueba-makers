import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { isAppError } from '../../core/api/app-error';
import { AuthStore } from '../../core/auth/auth.store';
import { focusFirstInvalid } from '../../shared/forms/server-errors';
import { PrButton } from '../../shared/ui/button';
import { PrField } from '../../shared/ui/field';
import { PrIcon } from '../../shared/ui/icon';
import { PrInput } from '../../shared/ui/input.directive';
import { AuthLayout } from './auth-layout';

const REASONS: Record<string, string> = {
  expired: 'Tu sesión venció. Ingresa de nuevo.',
  disabled: 'Tu cuenta está desactivada. Habla con un administrador.',
  elsewhere: 'Cerraste sesión en otra pestaña.',
  switched: 'Ingresaste con otra cuenta en otra pestaña. Ingresa de nuevo para seguir con esta.',
};

const DEMO = [
  { label: 'Usuario', email: 'usuario@test.com', password: '123' },
  { label: 'Administrador', email: 'admin@test.com', password: '123' },
];

/** Solo rutas internas: evita redirecciones abiertas con `?volver=//otro-sitio`. */
export function safeReturnUrl(url: string | undefined): string | null {
  return url && url.startsWith('/') && !url.startsWith('//') ? url : null;
}

@Component({
  selector: 'app-login-page',
  imports: [ReactiveFormsModule, RouterLink, AuthLayout, PrField, PrInput, PrButton, PrIcon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-auth-layout>
      <h1 class="pr-auth__title">Ingresa a Prestemos</h1>
      <p class="pr-auth__lead">Usa tu correo y contraseña.</p>

      @if (notice()) {
        <p class="pr-notice" role="status"><pr-icon name="info" size="sm" />{{ notice() }}</p>
      }

      <form class="pr-form" [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <pr-field label="Correo" [control]="form.controls.email">
          <input prInput type="email" autocomplete="username" placeholder="nombre@correo.com"
            formControlName="email" />
        </pr-field>
        <pr-field label="Contraseña" [control]="form.controls.password">
          <div class="pr-input-group">
            <input prInput [type]="showPassword() ? 'text' : 'password'" autocomplete="current-password"
              formControlName="password" />
            <button type="button" class="pr-input-group__action"
              [attr.aria-label]="showPassword() ? 'Ocultar contraseña' : 'Mostrar contraseña'"
              [attr.aria-pressed]="showPassword()" (click)="showPassword.set(!showPassword())">
              <pr-icon [name]="showPassword() ? 'eye-off' : 'eye'" />
            </button>
          </div>
        </pr-field>

        @if (formError()) {
          <p class="pr-form__error" role="alert"><pr-icon name="circle-alert" size="sm" />{{ formError() }}</p>
        }

        <button #submitButton prButton variant="primary" size="lg" type="submit" class="pr-form__submit"
          [loading]="submitting()" loadingText="Ingresando…">Ingresar</button>
      </form>

      <p class="pr-auth__switch">¿No tienes cuenta? <a routerLink="/registro">Regístrate</a></p>

      <section class="pr-demo" aria-labelledby="demo-title">
        <h2 id="demo-title" class="pr-demo__title">Usuarios de prueba</h2>
        @for (account of demo; track account.email) {
          <div class="pr-demo__row">
            <div>
              <span class="pr-demo__role">{{ account.label }}</span>
              <span class="pr-demo__credentials">{{ account.email }} · {{ account.password }}</span>
            </div>
            <button prButton size="sm" type="button" [attr.aria-label]="'Usar la cuenta de ' + account.label"
              (click)="useDemo(account)">Usar</button>
          </div>
        }
      </section>
    </app-auth-layout>
  `,
})
export class LoginPage {
  /** De la URL: `?volver=/ruta` y `?motivo=expired|disabled|elsewhere|switched`. */
  readonly volver = input<string>();
  readonly motivo = input<string>();

  private readonly auth = inject(AuthStore);
  private readonly router = inject(Router);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  private readonly injector = inject(Injector);
  private readonly submitButton = viewChild.required('submitButton', { read: ElementRef });

  protected readonly demo = DEMO;
  protected readonly form = inject(FormBuilder).nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]],
  });
  protected readonly submitting = signal(false);
  protected readonly formError = signal<string | null>(null);
  protected readonly showPassword = signal(false);
  protected readonly notice = computed(() => REASONS[this.motivo() ?? ''] ?? null);

  protected useDemo(account: { email: string; password: string }): void {
    this.form.setValue({ email: account.email, password: account.password });
    this.formError.set(null);
    (this.submitButton().nativeElement as HTMLButtonElement).focus();
  }

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
      const { email, password } = this.form.getRawValue();
      await this.auth.login(email.trim(), password);
      await this.router.navigateByUrl(safeReturnUrl(this.volver()) ?? this.auth.homeUrl());
    } catch (e) {
      this.formError.set(this.describe(e));
    } finally {
      this.submitting.set(false);
    }
  }

  private describe(e: unknown): string {
    if (!isAppError(e)) return 'Algo salió mal. Intenta de nuevo en un momento.';
    if (e.code === 'INVALID_CREDENTIALS') {
      return 'El correo o la contraseña no coinciden. Revísalos e intenta de nuevo.';
    }
    if (e.code === 'ACCOUNT_DISABLED') return REASONS['disabled'];
    return e.detail;
  }
}
