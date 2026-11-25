import { Routes } from '@angular/router';
import { AdminLoanDetailRoute } from './admin-loan-detail.route';
import { AdminLoansActions } from './admin-loans.actions';
import { AdminLoansPage } from './admin-loans.page';
import { AdminLoansStore } from './admin-loans.store';

export const ADMIN_LOANS_ROUTES: Routes = [
  {
    path: '',
    providers: [AdminLoansStore, AdminLoansActions],
    component: AdminLoansPage,
    children: [{ path: ':id', component: AdminLoanDetailRoute }],
  },
];
