import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule, ValidatorFn } from '@angular/forms';
import { AdminLoanDto } from '../../core/api/dto';
import { MoneyPipe } from '../../shared/money/money.pipe';
import { PrButton } from '../../shared/ui/button';
import { PrField } from '../../shared/ui/field';
import { PrInput } from '../../shared/ui/input.directive';

const MIN = 5;
const MAX = 500;

/** Largo del motivo sin contar espacios al principio y al final (como lo valida el backend). */
const reasonLength: ValidatorFn = (control) => {
  const length = String(control.value ?? '').trim().length;
  if (length === 0) return { required: true };
  if (length < MIN) return { minlength: { requiredLength: MIN, actualLength: length } };
  if (length > MAX) return { maxlength: { requiredLength: MAX, actualLength: length } };
  return null;
};

let nextId = 0;

/** Rechazo con motivo obligatorio: el solicitante lo verá. Devuelve el motivo sin espacios sobrantes. */
@Component({
  selector: 'app-reject-dialog',
  imports: [ReactiveFormsModule, MoneyPipe, PrButton, PrField, PrInput],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'pr-dialog' },
  template: `
    <h2 class="pr-dialog__title" [id]="titleId">Rechazar préstamo</h2>
    <p class="pr-dialog__text">
      {{ data.loan.amount | money }} a {{ data.loan.termMonths }} meses para {{ data.loan.applicantName }}.
    </p>
    <form class="pr-form pr-form--flush" (submit)="$event.preventDefault(); submit()" novalidate>
      <pr-field label="Motivo" hint="El solicitante verá este motivo." [control]="reason">
        <textarea prInput rows="4" [formControl]="reason" [attr.maxlength]="max"
          placeholder="Por ejemplo: el monto supera tu capacidad de pago con los ingresos que registraste."></textarea>
      </pr-field>
      <p class="pr-counter tabular" [attr.aria-live]="length() > 450 ? 'polite' : null">{{ length() }}/{{ max }}</p>
      <div class="pr-dialog__actions">
        <button prButton type="button" (click)="ref.close()">Volver</button>
        <button prButton variant="danger" type="submit">Rechazar</button>
      </div>
    </form>
  `,
})
export class RejectDialog {
  protected readonly data = inject<{ loan: AdminLoanDto }>(DIALOG_DATA);
  protected readonly ref = inject<DialogRef<string>>(DialogRef);
  protected readonly max = MAX;
  readonly titleId = `pr-reject-title-${++nextId}`;

  protected readonly reason = new FormControl('', { nonNullable: true, validators: [reasonLength] });
  private readonly value = toSignal(this.reason.valueChanges, { initialValue: '' });
  protected readonly length = computed(() => this.value().trim().length);

  protected submit(): void {
    if (this.reason.invalid) {
      this.reason.markAsTouched();
      return;
    }
    this.ref.close(this.reason.value.trim());
  }
}
