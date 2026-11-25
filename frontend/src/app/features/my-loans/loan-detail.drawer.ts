import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { LoanDto } from '../../core/api/dto';
import { MoneyPipe } from '../../shared/money/money.pipe';
import { PrButton } from '../../shared/ui/button';
import { PrDrawer } from '../../shared/ui/drawer.service';
import { PrStatusBadge } from '../../shared/ui/status-badge';
import { termLabel } from './term-label';

export type LoanDetailAction = 'edit' | 'cancel';

/** Detalle de un préstamo del usuario. Si está pendiente, permite editarlo o cancelarlo. */
@Component({
  selector: 'app-loan-detail-drawer',
  imports: [DatePipe, MoneyPipe, PrDrawer, PrButton, PrStatusBadge],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <pr-drawer title="Detalle del préstamo">
      <p class="pr-figure tabular">{{ loan.amount | money }}</p>
      <dl class="pr-details">
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
          <button prButton variant="danger" type="button" (click)="ref.close('cancel')">Cancelar solicitud</button>
          <button prButton type="button" (click)="ref.close('edit')">Editar</button>
        </div>
      }
    </pr-drawer>
  `,
})
export class LoanDetailDrawer {
  protected readonly loan = inject<{ loan: LoanDto }>(DIALOG_DATA).loan;
  protected readonly ref = inject<DialogRef<LoanDetailAction>>(DialogRef);
  protected readonly term = termLabel(this.loan.termMonths);
}
