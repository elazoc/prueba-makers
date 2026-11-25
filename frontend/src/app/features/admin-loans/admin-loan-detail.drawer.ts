import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { AdminLoanDto } from '../../core/api/dto';
import { MoneyPipe } from '../../shared/money/money.pipe';
import { PrButton } from '../../shared/ui/button';
import { PrDrawer } from '../../shared/ui/drawer.service';
import { PrStatusBadge } from '../../shared/ui/status-badge';
import { termLabel } from '../my-loans/term-label';

export type AdminLoanAction = 'approve' | 'reject';

/** Detalle para el administrador: el préstamo, quién lo pidió y, si está pendiente, la decisión. */
@Component({
  selector: 'app-admin-loan-detail-drawer',
  imports: [DatePipe, MoneyPipe, PrDrawer, PrButton, PrStatusBadge],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <pr-drawer title="Solicitud de préstamo">
      <p class="pr-figure tabular">{{ loan.amount | money }}</p>
      <dl class="pr-details">
        <div><dt>Solicitante</dt><dd>{{ loan.applicantName }}<br /><span class="pr-muted">{{ loan.applicantEmail }}</span></dd></div>
        <div><dt>Estado</dt><dd><pr-status-badge [status]="loan.status" /></dd></div>
        <div><dt>Plazo</dt><dd>{{ term }}</dd></div>
        <div><dt>Solicitado</dt><dd>{{ loan.requestedAt | date: 'd MMM y, h:mm a' }}</dd></div>
        @if (loan.decidedAt) {
          <div>
            <dt>{{ loan.status === 'APPROVED' ? 'Aprobado' : 'Rechazado' }}</dt>
            <dd>{{ loan.decidedAt | date: 'd MMM y, h:mm a' }}</dd>
          </div>
        }
      </dl>
      @if (loan.status === 'REJECTED' && loan.rejectionReason) {
        <section class="pr-reason">
          <h3 class="pr-reason__title">Motivo del rechazo</h3>
          <p>{{ loan.rejectionReason }}</p>
        </section>
      }
      @if (loan.status === 'PENDING') {
        <div drawer-footer>
          <button prButton variant="danger" type="button" (click)="ref.close('reject')">Rechazar</button>
          <button prButton variant="primary" type="button" (click)="ref.close('approve')">Aprobar</button>
        </div>
      }
    </pr-drawer>
  `,
})
export class AdminLoanDetailDrawer {
  protected readonly loan = inject<{ loan: AdminLoanDto }>(DIALOG_DATA).loan;
  protected readonly ref = inject<DialogRef<AdminLoanAction>>(DialogRef);
  protected readonly term = termLabel(this.loan.termMonths);
}
