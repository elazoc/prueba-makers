import { DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, Injector, OnDestroy, OnInit, inject, input } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { LoanDto } from '../../core/api/dto';
import { LoansApi } from '../../core/api/loans.api';
import { ToastService } from '../../core/notify/toast.service';
import { DrawerService } from '../../shared/ui/drawer.service';
import { LoanDetailAction, LoanDetailDrawer } from './loan-detail.drawer';
import { MyLoansActions } from './my-loans.actions';
import { MyLoansStore } from './my-loans.store';

/** `/prestamos/:id`: abre el detalle como panel sobre la lista. El enlace directo funciona. */
@Component({
  selector: 'app-loan-detail-route',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: '',
})
export class LoanDetailRoute implements OnInit, OnDestroy {
  readonly id = input.required<string>();

  private readonly store = inject(MyLoansStore);
  private readonly api = inject(LoansApi);
  private readonly drawer = inject(DrawerService);
  private readonly actions = inject(MyLoansActions);
  private readonly toasts = inject(ToastService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly injector = inject(Injector);
  private ref: DialogRef<LoanDetailAction, LoanDetailDrawer> | null = null;

  ngOnInit(): void {
    void this.open();
  }

  ngOnDestroy(): void {
    this.ref?.close();
  }

  private async open(): Promise<void> {
    let loan: LoanDto | undefined = this.store.page()?.items.find((l) => l.id === this.id());
    if (!loan) {
      try {
        loan = await firstValueFrom(this.api.get(this.id()));
      } catch {
        this.toasts.show({ tone: 'info', message: 'No encontramos este préstamo.' });
        this.back();
        return;
      }
    }
    this.ref = this.drawer.open<LoanDetailDrawer, { loan: LoanDto }, LoanDetailAction>(
      LoanDetailDrawer,
      { loan },
      this.injector,
    );
    const action = await firstValueFrom(this.ref.closed);
    this.ref = null;
    this.back();
    if (action === 'edit') this.actions.openForm(loan);
    if (action === 'cancel') void this.actions.cancel(loan);
  }

  private back(): void {
    void this.router.navigate(['..'], { relativeTo: this.route, queryParamsHandling: 'preserve' });
  }
}
