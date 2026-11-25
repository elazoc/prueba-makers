import { DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, Injector, OnDestroy, OnInit, inject, input } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AdminLoanDto } from '../../core/api/dto';
import { LoansApi } from '../../core/api/loans.api';
import { UsersApi } from '../../core/api/users.api';
import { ToastService } from '../../core/notify/toast.service';
import { DrawerService } from '../../shared/ui/drawer.service';
import { AdminLoanAction, AdminLoanDetailDrawer } from './admin-loan-detail.drawer';
import { AdminLoansActions } from './admin-loans.actions';
import { AdminLoansStore } from './admin-loans.store';

/**
 * `/admin/solicitudes/:id`. Desde la lista usa la fila (ya trae al solicitante). Con un enlace
 * directo pide el préstamo y luego al solicitante, porque `GET /loans/{id}` no trae su nombre.
 */
@Component({
  selector: 'app-admin-loan-detail-route',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: '',
})
export class AdminLoanDetailRoute implements OnInit, OnDestroy {
  readonly id = input.required<string>();

  private readonly store = inject(AdminLoansStore);
  private readonly loansApi = inject(LoansApi);
  private readonly usersApi = inject(UsersApi);
  private readonly drawer = inject(DrawerService);
  private readonly actions = inject(AdminLoansActions);
  private readonly toasts = inject(ToastService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly injector = inject(Injector);
  private ref: DialogRef<AdminLoanAction, AdminLoanDetailDrawer> | null = null;

  ngOnInit(): void {
    void this.open();
  }

  ngOnDestroy(): void {
    this.ref?.close();
  }

  private async open(): Promise<void> {
    let loan = this.store.page()?.items.find((l) => l.id === this.id());
    if (!loan) {
      try {
        const found = await firstValueFrom(this.loansApi.get(this.id()));
        const applicant = await firstValueFrom(this.usersApi.get(found.applicantId));
        loan = { ...found, applicantEmail: applicant.email, applicantName: applicant.fullName };
      } catch {
        this.toasts.show({ tone: 'info', message: 'No encontramos esta solicitud.' });
        this.back();
        return;
      }
    }
    this.ref = this.drawer.open<AdminLoanDetailDrawer, { loan: AdminLoanDto }, AdminLoanAction>(
      AdminLoanDetailDrawer,
      { loan },
      this.injector,
    );
    const action = await firstValueFrom(this.ref.closed);
    this.ref = null;
    this.back();
    if (action === 'approve') void this.actions.approve(loan);
    if (action === 'reject') void this.actions.reject(loan);
  }

  private back(): void {
    void this.router.navigate(['..'], { relativeTo: this.route, queryParamsHandling: 'preserve' });
  }
}
