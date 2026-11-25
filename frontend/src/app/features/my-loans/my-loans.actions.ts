import { Injectable, Injector, inject } from '@angular/core';
import { isAppError } from '../../core/api/app-error';
import { LoanDto } from '../../core/api/dto';
import { ToastService } from '../../core/notify/toast.service';
import { formatMoney } from '../../shared/money/money';
import { ConfirmService } from '../../shared/ui/confirm.service';
import { DrawerService } from '../../shared/ui/drawer.service';
import { LoanFormDrawer, LoanFormData } from './loan-form.drawer';
import { MyLoansStore } from './my-loans.store';

/** Acciones de la pantalla (desde la tabla o desde el detalle). Se provee en la ruta, junto al store. */
@Injectable()
export class MyLoansActions {
  private readonly drawer = inject(DrawerService);
  private readonly confirm = inject(ConfirmService);
  private readonly toasts = inject(ToastService);
  private readonly store = inject(MyLoansStore);
  private readonly injector = inject(Injector);

  openForm(loan?: LoanDto): void {
    this.drawer.open<LoanFormDrawer, LoanFormData, LoanDto>(LoanFormDrawer, { loan }, this.injector);
  }

  async cancel(loan: LoanDto): Promise<void> {
    const ok = await this.confirm.confirm({
      title: 'Cancelar solicitud',
      message: `Cancelar la solicitud de ${formatMoney(loan.amount)} no se puede deshacer.`,
      confirmLabel: 'Cancelar solicitud',
      tone: 'danger',
    });
    if (!ok) return;
    try {
      await this.store.cancel(loan.id);
      this.toasts.show({ tone: 'success', message: 'Se canceló tu solicitud.' });
    } catch (e) {
      this.toasts.show({
        tone: 'danger',
        message: isAppError(e) ? e.detail : 'No se pudo cancelar. Intenta de nuevo.',
      });
      this.store.reload();
    }
  }
}
