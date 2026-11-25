import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  inject,
  signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { isAppError } from '../../core/api/app-error';
import { LoanDto } from '../../core/api/dto';
import { ToastService } from '../../core/notify/toast.service';
import { applyServerErrors, focusFirstInvalid } from '../../shared/forms/server-errors';
import { amountValidator, termValidator } from '../../shared/forms/validators';
import { formatMoney } from '../../shared/money/money';
import { PrButton } from '../../shared/ui/button';
import { PrDrawer } from '../../shared/ui/drawer.service';
import { PrField } from '../../shared/ui/field';
import { PrIcon } from '../../shared/ui/icon';
import { PrInput } from '../../shared/ui/input.directive';
import { PrMoneyInput } from '../../shared/ui/money-input.directive';
import { MyLoansStore } from './my-loans.store';
import { termLabel } from './term-label';

export interface LoanFormData {
  loan?: LoanDto;
}

const TERM_SHORTCUTS = [6, 12, 24, 36, 60];

@Component({
  selector: 'app-loan-form-drawer',
  imports: [ReactiveFormsModule, PrDrawer, PrField, PrInput, PrMoneyInput, PrButton, PrIcon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <pr-drawer [title]="isEdit ? 'Editar solicitud' : 'Solicitar préstamo'"
      [description]="isEdit ? 'Puedes cambiarla mientras esté pendiente.' : 'Te avisamos apenas haya una decisión.'">
      <form id="loan-form" class="pr-form pr-form--flush" [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <pr-field label="Monto" hint="Entre $ 100 y $ 50.000.000." [control]="form.controls.amount">
          <div class="pr-input-affix">
            <span class="pr-input-affix__prefix" aria-hidden="true">$</span>
            <input prInput prMoneyInput placeholder="1.500.000" formControlName="amount" />
          </div>
        </pr-field>

        <pr-field label="Plazo en meses" hint="Entre 1 y 120 meses." [control]="form.controls.termMonths">
          <input prInput type="number" inputmode="numeric" min="1" max="120" step="1" placeholder="12"
            formControlName="termMonths" />
        </pr-field>
        <div class="pr-chips" role="group" aria-label="Plazos frecuentes">
          @for (months of shortcuts; track months) {
            <button prButton variant="secondary" size="sm" type="button"
              [attr.aria-pressed]="term() === months" (click)="setTerm(months)">{{ months }} meses</button>
          }
        </div>

        @if (summary(); as text) {
          <p class="pr-summary" aria-live="polite">{{ text }}</p>
        }
        @if (formError()) {
          <p class="pr-form__error" role="alert"><pr-icon name="circle-alert" size="sm" />{{ formError() }}</p>
        }
      </form>

      <div drawer-footer>
        <button prButton type="button" (click)="ref.close()">Volver</button>
        <button prButton variant="primary" type="submit" form="loan-form" [loading]="saving()"
          [loadingText]="isEdit ? 'Guardando…' : 'Enviando…'">
          {{ isEdit ? 'Guardar cambios' : 'Enviar solicitud' }}
        </button>
      </div>
    </pr-drawer>
  `,
})
export class LoanFormDrawer {
  private readonly data = inject<LoanFormData>(DIALOG_DATA);
  protected readonly ref = inject<DialogRef<LoanDto>>(DialogRef);
  private readonly store = inject(MyLoansStore);
  private readonly toasts = inject(ToastService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  private readonly injector = inject(Injector);

  protected readonly isEdit = !!this.data.loan;
  protected readonly shortcuts = TERM_SHORTCUTS;
  protected readonly saving = signal(false);
  protected readonly formError = signal<string | null>(null);

  protected readonly form = inject(FormBuilder).group({
    amount: [this.data.loan?.amount ?? (null as string | null), [amountValidator]],
    termMonths: [this.data.loan?.termMonths ?? (null as number | null), [termValidator]],
  });

  private readonly value = toSignal(this.form.valueChanges, { initialValue: this.form.value });
  private readonly statusChanges = toSignal(this.form.statusChanges, { initialValue: this.form.status });
  protected readonly term = computed(() => this.value().termMonths ?? null);

  protected readonly summary = computed(() => {
    const { amount, termMonths } = this.value();
    if (this.statusChanges() !== 'VALID' || !amount || !termMonths) return null;
    return `Pides ${formatMoney(amount)} a ${termLabel(termMonths)}.`;
  });

  protected setTerm(months: number): void {
    this.form.controls.termMonths.setValue(months);
    this.form.controls.termMonths.markAsTouched();
  }

  protected async submit(): Promise<void> {
    if (this.saving()) return;
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      afterNextRender(() => focusFirstInvalid(this.host), { injector: this.injector });
      return;
    }
    const request = {
      amount: this.form.controls.amount.value as string,
      termMonths: this.form.controls.termMonths.value as number,
    };
    this.saving.set(true);
    this.formError.set(null);
    try {
      const saved = this.data.loan
        ? await this.store.update(this.data.loan.id, request)
        : await this.store.request(request);
      this.toasts.show({
        tone: 'success',
        message: this.isEdit ? 'Se guardaron los cambios.' : 'Se envió tu solicitud.',
      });
      this.ref.close(saved);
    } catch (e) {
      // El panel sigue abierto con los datos: el servidor manda (p. ej. un 422 de la política).
      this.formError.set(
        isAppError(e)
          ? applyServerErrors(this.form, e)
          : 'Algo salió mal. Intenta de nuevo en un momento.',
      );
    } finally {
      this.saving.set(false);
    }
  }
}
