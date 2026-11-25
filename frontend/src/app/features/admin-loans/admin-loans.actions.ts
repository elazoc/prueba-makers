import { Dialog } from '@angular/cdk/dialog';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { AdminLoanDto } from '../../core/api/dto';
import { formatMoney } from '../../shared/money/money';
import { ConfirmService } from '../../shared/ui/confirm.service';
import { termLabel } from '../my-loans/term-label';
import { AdminLoansStore } from './admin-loans.store';
import { RejectDialog } from './reject.dialog';

/** Aprobar (con confirmación) y rechazar (con motivo), desde la tabla o desde el detalle. */
@Injectable()
export class AdminLoansActions {
  private readonly store = inject(AdminLoansStore);
  private readonly confirm = inject(ConfirmService);
  private readonly dialog = inject(Dialog);

  async approve(loan: AdminLoanDto): Promise<void> {
    const ok = await this.confirm.confirm({
      title: 'Aprobar préstamo',
      message: `Vas a aprobar ${formatMoney(loan.amount)} a ${termLabel(loan.termMonths)} para ${loan.applicantName}. Es definitivo.`,
      confirmLabel: 'Aprobar',
    });
    if (ok) await this.store.approve(loan.id);
  }

  async reject(loan: AdminLoanDto): Promise<void> {
    const ref = this.dialog.open<string, { loan: AdminLoanDto }, RejectDialog>(RejectDialog, {
      data: { loan },
      role: 'alertdialog',
      ariaLabel: 'Rechazar préstamo',
      panelClass: 'pr-dialog-panel',
      backdropClass: 'pr-scrim',
      autoFocus: 'textarea',
      restoreFocus: true,
    });
    const reason = await firstValueFrom(ref.closed);
    if (reason) await this.store.reject(loan.id, reason);
  }
}
